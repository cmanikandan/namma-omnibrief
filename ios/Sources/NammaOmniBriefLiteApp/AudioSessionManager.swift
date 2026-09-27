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
            return await AVAudioApplication.requestRecordPermission()
        } else {
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
            try audioSession.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)

            let tempDir = FileManager.default.temporaryDirectory
            let fileName = "conf_session_\(Int(Date().timeIntervalSince1970)).m4a"
            let fileURL = tempDir.appendingPathComponent(fileName)

            let settings: [String: Any] = [
                AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
                AVSampleRateKey: 44100.0,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue,
                AVEncoderBitRateKey: 64000
            ]

            let recorder = try AVAudioRecorder(url: fileURL, settings: settings)
            recorder.delegate = self
            recorder.isMeteringEnabled = true
            guard recorder.record() else {
                errorMessage = "Could not start audio recorder."
                state = .idle
                return
            }

            self.audioRecorder = recorder
            self.recordedFileURL = fileURL
            self.state = .recording
            self.durationSeconds = 0
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

        if recordedFileURL != nil {
            state = .stopped
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
            try session.setCategory(.playback, mode: .default)
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
        }
    }

    private func startMeterTimer() {
        meterTimer?.invalidate()
        meterTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self = self, let recorder = self.audioRecorder, self.state == .recording else { return }
                recorder.updateMeters()
                self.durationSeconds = Int(recorder.currentTime)
                let avg = recorder.averagePower(forChannel: 0)
                // Normalize from [-60 dB, 0 dB] to [0.0, 1.0]
                let linear = max(0.0, (avg + 60.0) / 60.0)
                self.amplitude = linear
            }
        }
    }
}
