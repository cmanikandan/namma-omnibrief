import SwiftUI
import PhotosUI

public struct ConferenceReporterView: View {
    @ObservedObject var viewModel: MainViewModel

    @State private var showingCamera = false
    @State private var selectedPickerItems: [PhotosPickerItem] = []
    @State private var copiedToClipboard = false
    @State private var showingShareSheet = false

    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerSection
                messagesSection
                sessionMetadataCard
                slidePhotosCard
                audioRecordingCard
                synthesizeButton

                if let report = viewModel.conferenceReportResult {
                    reportResultCard(report: report)
                }
            }
            .padding(16)
        }
        .background(OmniTheme.paperBackground.ignoresSafeArea())
        .sheet(isPresented: $showingCamera) {
            CameraPicker { image in
                viewModel.addConferencePhoto(image)
            }
        }
        .sheet(isPresented: $showingShareSheet) {
            if let report = viewModel.conferenceReportResult {
                ActivityView(activityItems: [report.fullReportMarkdown])
            }
        }
        .onChange(of: selectedPickerItems) { _, newItems in
            Task {
                for item in newItems {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let uiImage = UIImage(data: data) {
                        viewModel.addConferencePhoto(uiImage)
                    }
                }
                selectedPickerItems = []
            }
        }
    }

    // MARK: - Sections

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("CONFERENCE INTELLIGENCE")
                .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.primaryTerracotta)
                .tracking(1.2)

            Text("Session Synthesis & Report Scribe")
                .omniScaledFont(size: 18, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.deepInk)

            Text("Capture slides, record speaker audio, and generate boardroom-ready executive field reports with Gemini.")
                .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.mutedSlate)
        }
    }

    @ViewBuilder
    private var messagesSection: some View {
        if let err = viewModel.conferenceError {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(OmniTheme.errorCrimson)
                Text(err)
                    .omniScaledFont(size: 12, weight: .medium, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.errorCrimson)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(OmniTheme.errorCrimson.opacity(0.1))
            .cornerRadius(10)
        }

        if let msg = viewModel.conferenceSuccessMessage {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(OmniTheme.forestGreen)
                Text(msg)
                    .omniScaledFont(size: 12, weight: .medium, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.forestGreen)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(OmniTheme.forestGreen.opacity(0.1))
            .cornerRadius(10)
        }
    }

    private var sessionMetadataCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("SESSION DETAILS")
                    .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                Spacer()

                Button {
                    viewModel.loadSampleConferenceSession()
                } label: {
                    Text("Sample I/O")
                        .omniScaledFont(size: 10, weight: .bold, scale: viewModel.fontScale)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(OmniTheme.subtleSurface)
                        .foregroundColor(OmniTheme.primaryTerracotta)
                        .cornerRadius(6)
                }

                Button {
                    viewModel.loadBengaluruTechConferenceSample()
                } label: {
                    HStack(spacing: 3) {
                        Image(systemName: "building.2.fill")
                            .font(.system(size: 9))
                        Text("Namma BLR")
                            .omniScaledFont(size: 10, weight: .bold, scale: viewModel.fontScale)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(OmniTheme.subtleSurface)
                    .foregroundColor(OmniTheme.forestGreen)
                    .cornerRadius(6)
                }
            }

            TextField("Session Topic / Presentation Title", text: $viewModel.confTopic)
                .textFieldStyle(.roundedBorder)
                .omniScaledFont(size: 13, scale: viewModel.fontScale)

            HStack(spacing: 10) {
                TextField("Speaker Name", text: $viewModel.confSpeaker)
                    .textFieldStyle(.roundedBorder)
                    .omniScaledFont(size: 13, scale: viewModel.fontScale)

                TextField("Conference Name", text: $viewModel.confTitle)
                    .textFieldStyle(.roundedBorder)
                    .omniScaledFont(size: 13, scale: viewModel.fontScale)
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private var slidePhotosCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("PRESENTATION SLIDES (\(viewModel.confPhotos.count))")
                    .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                Spacer()

                if !viewModel.confPhotos.isEmpty {
                    Button("Clear All") {
                        viewModel.clearConferencePhotos()
                    }
                    .omniScaledFont(size: 10, weight: .semibold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.mutedSlate)
                }
            }

            HStack(spacing: 10) {
                Button {
                    showingCamera = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "camera.fill")
                        Text("Take Slide Photo")
                    }
                    .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(OmniTheme.subtleSurface)
                    .foregroundColor(OmniTheme.deepInk)
                    .cornerRadius(8)
                }

                PhotosPicker(
                    selection: $selectedPickerItems,
                    maxSelectionCount: 30,
                    matching: .images
                ) {
                    HStack(spacing: 6) {
                        Image(systemName: "photo.on.rectangle.angled")
                        Text("Pick from Photos")
                    }
                    .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(OmniTheme.subtleSurface)
                    .foregroundColor(OmniTheme.deepInk)
                    .cornerRadius(8)
                }
            }

            if !viewModel.confPhotos.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(Array(viewModel.confPhotos.enumerated()), id: \.offset) { index, img in
                            ZStack(alignment: .topTrailing) {
                                Image(uiImage: img)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 80, height: 80)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))

                                Button {
                                    viewModel.removeConferencePhoto(at: index)
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.white)
                                        .background(Circle().fill(Color.black.opacity(0.6)))
                                }
                                .padding(4)
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private var audioRecordingCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("LIVE SESSION AUDIO RECORDING")
                    .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                Spacer()

                if viewModel.audioManager.state != .idle {
                    Button("Discard") {
                        viewModel.audioManager.discardRecording()
                    }
                    .omniScaledFont(size: 10, weight: .semibold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.errorCrimson)
                }
            }

            switch viewModel.audioManager.state {
            case .idle:
                Button {
                    Task {
                        let granted = await viewModel.audioManager.checkOrRequestPermission()
                        if granted {
                            viewModel.audioManager.startRecording()
                        } else {
                            viewModel.conferenceError = "Microphone access is required to record conference audio. Please enable it in iOS Settings."
                        }
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "mic.circle.fill")
                            .font(.system(size: 20))
                        Text("Record Session Audio")
                            .omniScaledFont(size: 13, weight: .bold, scale: viewModel.fontScale)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(OmniTheme.subtleSurface)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                    .cornerRadius(10)
                }

            case .recording, .paused:
                VStack(spacing: 12) {
                    HStack {
                        Circle()
                            .fill(viewModel.audioManager.state == .recording ? OmniTheme.errorCrimson : OmniTheme.mysoreGold)
                            .frame(width: 10, height: 10)

                        Text(viewModel.audioManager.state == .recording ? "RECORDING LIVE AUDIO" : "RECORDING PAUSED")
                            .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.deepInk)

                        Spacer()

                        let s = viewModel.audioManager.durationSeconds
                        Text(String(format: "%02d:%02d", s / 60, s % 60))
                            .font(.system(size: 15, weight: .bold, design: .monospaced))
                            .foregroundColor(OmniTheme.deepInk)
                    }

                    // Live waveform amplitude bars
                    HStack(spacing: 4) {
                        ForEach(0..<18, id: \.self) { i in
                            let amp = CGFloat(viewModel.audioManager.amplitude)
                            let height = viewModel.audioManager.state == .recording
                                ? max(6.0, CGFloat((i % 5 + 1) * 6) * amp + 4.0)
                                : 6.0
                            RoundedRectangle(cornerRadius: 2)
                                .fill(OmniTheme.primaryTerracotta.opacity(0.8))
                                .frame(width: 8, height: height)
                                .animation(.spring(response: 0.2), value: viewModel.audioManager.amplitude)
                        }
                    }
                    .frame(height: 36)

                    HStack(spacing: 12) {
                        if viewModel.audioManager.state == .recording {
                            Button {
                                viewModel.audioManager.pauseRecording()
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: "pause.fill")
                                    Text("Pause")
                                }
                                .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(OmniTheme.subtleSurface)
                                .foregroundColor(OmniTheme.deepInk)
                                .cornerRadius(8)
                            }
                        } else {
                            Button {
                                viewModel.audioManager.resumeRecording()
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: "play.fill")
                                    Text("Resume")
                                }
                                .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(OmniTheme.subtleSurface)
                                .foregroundColor(OmniTheme.deepInk)
                                .cornerRadius(8)
                            }
                        }

                        Button {
                            viewModel.audioManager.stopRecording()
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "stop.fill")
                                Text("Stop Recording")
                            }
                            .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(OmniTheme.errorCrimson)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                        }
                    }
                }

            case .stopped:
                VStack(spacing: 10) {
                    HStack {
                        Image(systemName: "waveform")
                            .foregroundColor(OmniTheme.forestGreen)
                        let s = viewModel.audioManager.durationSeconds
                        Text("Audio Captured (\(String(format: "%02d:%02d", s / 60, s % 60)))")
                            .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.deepInk)
                        Spacer()

                        Button {
                            viewModel.audioManager.togglePlayPause()
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: viewModel.audioManager.isPlaying ? "pause.fill" : "play.fill")
                                Text(viewModel.audioManager.isPlaying ? "Pause" : "Play")
                            }
                            .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(OmniTheme.subtleSurface)
                            .foregroundColor(OmniTheme.primaryTerracotta)
                            .cornerRadius(6)
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private var synthesizeButton: some View {
        Button {
            Task {
                await viewModel.synthesizeConferenceReport()
            }
        } label: {
            HStack(spacing: 8) {
                if viewModel.isGeneratingConferenceReport {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    Text("Synthesizing Multimodal Report…")
                        .omniScaledFont(size: 14, weight: .bold, scale: viewModel.fontScale)
                } else {
                    Image(systemName: "sparkles")
                    Text("Synthesize Executive Field Report")
                        .omniScaledFont(size: 14, weight: .bold, scale: viewModel.fontScale)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(viewModel.isGeneratingConferenceReport ? OmniTheme.mutedSlate : OmniTheme.primaryTerracotta)
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled(viewModel.isGeneratingConferenceReport)
    }

    private func reportResultCard(report: ConferenceReportResult) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(report.sessionTitle)
                        .omniScaledFont(size: 16, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.deepInk)
                    Text(report.speaker)
                        .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.mutedSlate)
                }
                Spacer()

                HStack(spacing: 8) {
                    Button {
                        UIPasteboard.general.string = report.fullReportMarkdown
                        copiedToClipboard = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                            copiedToClipboard = false
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: copiedToClipboard ? "checkmark" : "doc.on.doc")
                            Text(copiedToClipboard ? "Copied" : "Copy")
                        }
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(OmniTheme.subtleSurface)
                        .foregroundColor(OmniTheme.deepInk)
                        .cornerRadius(6)
                    }

                    Button {
                        showingShareSheet = true
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 13, weight: .bold))
                            .padding(8)
                            .background(OmniTheme.subtleSurface)
                            .foregroundColor(OmniTheme.primaryTerracotta)
                            .cornerRadius(6)
                    }
                }
            }

            Divider()

            // Executive Summary
            VStack(alignment: .leading, spacing: 6) {
                Text("EXECUTIVE SUMMARY")
                    .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                Text(report.executiveSummary)
                    .omniScaledFont(size: 13, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
            }

            // Key Takeaways
            if !report.keyTakeaways.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("KEY TAKEAWAYS")
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.primaryTerracotta)
                    ForEach(report.keyTakeaways, id: \.self) { takeaway in
                        HStack(alignment: .top, spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(OmniTheme.forestGreen)
                                .font(.system(size: 12))
                                .padding(.top, 2)
                            Text(takeaway)
                                .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                                .foregroundColor(OmniTheme.deepInk)
                        }
                    }
                }
            }

            // Slide Insights
            if !report.slideInsights.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("SLIDE & PRESENTATION INSIGHTS")
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.primaryTerracotta)
                    ForEach(report.slideInsights, id: \.self) { insight in
                        HStack(alignment: .top, spacing: 6) {
                            Image(systemName: "lightbulb.fill")
                                .foregroundColor(OmniTheme.mysoreGold)
                                .font(.system(size: 12))
                                .padding(.top, 2)
                            Text(insight)
                                .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                                .foregroundColor(OmniTheme.deepInk)
                        }
                    }
                }
            }

            // Action Items
            if !report.actionItems.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("STRATEGIC ACTION ITEMS")
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.primaryTerracotta)
                    ForEach(report.actionItems, id: \.self) { action in
                        HStack(alignment: .top, spacing: 6) {
                            Image(systemName: "arrow.right.circle.fill")
                                .foregroundColor(OmniTheme.primaryTerracotta)
                                .font(.system(size: 12))
                                .padding(.top, 2)
                            Text(action)
                                .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                                .foregroundColor(OmniTheme.deepInk)
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }
}

// Activity View wrapper for native UIActivityViewController
private struct ActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]
    let applicationActivities: [UIActivity]? = nil

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: applicationActivities)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
