import Foundation
import AVFoundation

public enum AudioRecordingState: String, Sendable {
    case idle = "IDLE"
    case recording = "RECORDING"
    case paused = "PAUSED"
    case stopped = "STOPPED"
}

@MainActor
public final class AudioSessionManager: NSObject, ObservableObject, AVAudioRecorderDelegate, AVAudioPlayerDelegate {
    @Published public var state: AudioRecordingState = .idle
    @Published public var durationSeconds: Int = 0
    @Published public var amplitude: Float = 0.0
    @Published public var isPlaying: Bool = false
    @Published public var playbackProgress: Double = 0.0
    @Published public var recordedFileURL: URL? = nil
    @Published public var fileSizeDescription: String = ""
    @Published public var fileSizeBytes: Int64 = 0
    @Published public var errorMessage: String? = nil

    private var audioRecorder: AVAudioRecorder?
    private var audioPlayer: AVAudioPlayer?
    private var meterTimer: Timer?
    private var playbackTimer: Timer?

    public override init() {
        super.init()
    }

    public func checkOrRequestPermission() async -> Bool {
        if #available(iOS 17.0, *) {
            let current = AVAudioApplication.shared.recordPermission
            if current == .granted {
                return true
            } else if current == .denied {
                return false
            }
            return await AVAudioApplication.requestRecordPermission()
        } else {
            let current = AVAudioSession.sharedInstance().recordPermission
            if current == .granted {
                return true
            } else if current == .denied {
                return false
            }
            return await withCheckedContinuation { continuation in
                AVAudioSession.sharedInstance().requestRecordPermission { granted in
                    continuation.resume(returning: granted)
                }
            }
        }
    }

    public func startRecording() {
        stopPlayback()
        discardRecording()

        let audioSession = AVAudioSession.sharedInstance()
        do {
            do {
                try audioSession.setCategory(
                    .playAndRecord,
                    mode: .spokenAudio,
                    options: [.defaultToSpeaker, .allowBluetooth, .allowBluetoothA2DP]
                )
            } catch {
                try audioSession.setCategory(.playAndRecord, options: [.defaultToSpeaker])
            }
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)

            let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let sessionsDir = docs.appendingPathComponent("ConferenceRecordings", isDirectory: true)
            try? FileManager.default.createDirectory(at: sessionsDir, withIntermediateDirectories: true)

            let fileName = "conf_session_\(Int(Date().timeIntervalSince1970)).m4a"
            let fileURL = sessionsDir.appendingPathComponent(fileName)

            // Voice-optimized speech settings: 22.05 kHz mono @ 28 kbps.
            // A full 1-hour recording is ~12.6 MB (under Gemini's 20 MB inlineData limit).
            let settings: [String: Any] = [
                AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
                AVSampleRateKey: 22050.0,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
                AVEncoderBitRateKey: 28000
            ]

            let recorder = try AVAudioRecorder(url: fileURL, settings: settings)
            recorder.delegate = self
            recorder.isMeteringEnabled = true
            let prepared = recorder.prepareToRecord()
            guard prepared else {
                errorMessage = "Failed to initialize microphone buffer."
                state = .idle
                return
            }
            guard recorder.record() else {
                errorMessage = "Could not activate microphone recording hardware."
                state = .idle
                return
            }

            self.audioRecorder = recorder
            self.recordedFileURL = fileURL
            self.state = .recording
            self.durationSeconds = 0
            self.fileSizeDescription = "0 KB"
            self.fileSizeBytes = 0
            self.errorMessage = nil

            startMeterTimer()
        } catch {
            errorMessage = "Microphone session error: \(error.localizedDescription)"
            state = .idle
        }
    }

    public func pauseRecording() {
        guard state == .recording, let recorder = audioRecorder else { return }
        recorder.pause()
        state = .paused
        meterTimer?.invalidate()
        meterTimer = nil
        amplitude = 0.0
    }

    public func resumeRecording() {
        guard state == .paused, let recorder = audioRecorder else { return }
        recorder.record()
        state = .recording
        startMeterTimer()
    }

    @discardableResult
    public func stopRecording() -> URL? {
        meterTimer?.invalidate()
        meterTimer = nil
        amplitude = 0.0

        if let recorder = audioRecorder, recorder.isRecording || state == .paused {
            recorder.stop()
        }
        audioRecorder = nil

        if let url = recordedFileURL, FileManager.default.fileExists(atPath: url.path) {
            state = .stopped
            updateFileSize()
        } else {
            state = .idle
        }
        return recordedFileURL
    }

    public func discardRecording() {
        meterTimer?.invalidate()
        meterTimer = nil
        stopPlayback()

        if let recorder = audioRecorder {
            recorder.stop()
            audioRecorder = nil
        }

        if let url = recordedFileURL {
            try? FileManager.default.removeItem(at: url)
            recordedFileURL = nil
        }

        durationSeconds = 0
        fileSizeDescription = ""
        fileSizeBytes = 0
        amplitude = 0.0
        state = .idle
    }

    public func togglePlayPause() {
        if isPlaying {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    public func startPlayback() {
        guard let url = recordedFileURL, FileManager.default.fileExists(atPath: url.path) else { return }
        stopPlayback()

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)

            let player = try AVAudioPlayer(contentsOf: url)
            player.delegate = self
            player.prepareToPlay()
            player.play()

            self.audioPlayer = player
            self.isPlaying = true

            playbackTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self = self, let p = self.audioPlayer, p.duration > 0 else { return }
                    self.playbackProgress = p.currentTime / p.duration
                }
            }
        } catch {
            errorMessage = "Playback error: \(error.localizedDescription)"
            isPlaying = false
        }
    }

    public func stopPlayback() {
        playbackTimer?.invalidate()
        playbackTimer = nil
        if let player = audioPlayer {
            player.stop()
            audioPlayer = nil
        }
        isPlaying = false
        playbackProgress = 0.0
    }

    public func updateFileSize() {
        guard let url = recordedFileURL,
              let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
              let size = attrs[.size] as? Int64 else { return }
        self.fileSizeBytes = size
        self.fileSizeDescription = ByteCountFormatter.string(fromByteCount: size, countStyle: .file)
    }

    // MARK: - Delegates
    public nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            self.stopPlayback()
        }
    }

    public nonisolated func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        Task { @MainActor in
            if !flag {
                self.errorMessage = "Audio recording completed with an error."
            }
            self.updateFileSize()
        }
    }

    private func startMeterTimer() {
        meterTimer?.invalidate()
        let timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self = self, let recorder = self.audioRecorder, self.state == .recording else { return }
                recorder.updateMeters()
                self.durationSeconds = Int(recorder.currentTime)
                let avg = recorder.averagePower(forChannel: 0)
                // Normalize from [-60 dB, 0 dB] to [0.0, 1.0]
                let linear = max(0.0, (avg + 60.0) / 60.0)
                self.amplitude = linear
                self.updateFileSize()
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        self.meterTimer = timer
    }
}
