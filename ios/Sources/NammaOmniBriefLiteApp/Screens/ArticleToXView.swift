import PhotosUI
import SwiftUI

public struct ArticleToXView: View {
    @ObservedObject var viewModel: MainViewModel
    @State private var showCamera: Bool = false
    @State private var selectedPhotoItems: [PhotosPickerItem] = []

    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                captureCard
                sourceOverrideCard
                textInputCard
                analyseButton

                if let err = viewModel.articleError {
                    Text(err)
                        .omniScaledFont(size: 13, weight: .medium, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.errorCrimson)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(OmniTheme.cardSurface)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(OmniTheme.errorCrimson.opacity(0.4), lineWidth: 1)
                        )
                }

                if !viewModel.postDrafts.isEmpty {
                    draftQueueSection
                }
            }
            .padding(16)
        }
        .background(OmniTheme.paperBackground.ignoresSafeArea())
        .sheet(isPresented: $showCamera) {
            CameraPicker(sourceType: .camera) { image in
                viewModel.addArticleImage(image)
            }
        }
        .onChange(of: selectedPhotoItems) { _, newItems in
            Task {
                for item in newItems {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let uiImage = UIImage(data: data) {
                        viewModel.addArticleImage(uiImage)
                    }
                }
                selectedPhotoItems = []
            }
        }
    }

    private var captureCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("ARTICLE → X DRAFTER")
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.primaryTerracotta)
                    Text("Capture or Paste Newspaper Story")
                        .omniScaledFont(size: 17, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.deepInk)
                }
                Spacer()
                Button("Start Fresh") {
                    viewModel.clearArticleWorkspace()
                }
                .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.primaryTerracotta)
            }

            HStack(spacing: 10) {
                Button {
                    showCamera = true
                } label: {
                    Label("Camera", systemImage: "camera.fill")
                        .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(OmniTheme.primaryTerracotta)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }

                PhotosPicker(
                    selection: $selectedPhotoItems,
                    maxSelectionCount: max(1, AppPreferencesStore.maxArticleImages - viewModel.articleImages.count),
                    matching: .images
                ) {
                    Label("Photos (\(viewModel.articleImages.count)/10)", systemImage: "photo.on.rectangle")
                        .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(OmniTheme.subtleSurface)
                        .foregroundColor(OmniTheme.deepInk)
                        .cornerRadius(10)
                }

                Button {
                    viewModel.loadSampleArticle()
                } label: {
                    Text("Sample FT")
                        .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(OmniTheme.subtleSurface)
                        .foregroundColor(OmniTheme.primaryTerracotta)
                        .cornerRadius(10)
                }
            }

            if !viewModel.articleImages.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(Array(viewModel.articleImages.enumerated()), id: \.offset) { idx, img in
                            ZStack(alignment: .topTrailing) {
                                Image(uiImage: img)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 78, height: 96)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))

                                Button {
                                    viewModel.removeArticleImage(at: idx)
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.white)
                                        .background(Color.black.opacity(0.6).clipShape(Circle()))
                                }
                                .padding(4)
                            }
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

    private var sourceOverrideCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Publication Attribution")
                    .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
                Spacer()
                Menu {
                    ForEach(AppPreferencesStore.knownPublications, id: \.self) { pub in
                        Button(pub) {
                            viewModel.setArticleSource(pub)
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Text(viewModel.articleSource)
                            .lineLimit(1)
                        Image(systemName: "chevron.up.chevron.down")
                    }
                    .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(OmniTheme.subtleSurface)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                    .cornerRadius(8)
                }
            }
            Text("Changing this chip rewrites both the mid-sentence attribution and the Source: line in your drafts and Archive.")
                .omniScaledFont(size: 11, weight: .regular, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.mutedSlate)
        }
        .padding(14)
        .background(OmniTheme.cardSurface)
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private var textInputCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Or Paste Article Excerpt / URL")
                .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.deepInk)
            TextEditor(text: $viewModel.articleTextInput)
                .omniScaledFont(size: 14, weight: .regular, scale: viewModel.fontScale)
                .frame(minHeight: 95)
                .padding(8)
                .background(OmniTheme.paperBackground)
                .cornerRadius(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(OmniTheme.borderWarm, lineWidth: 1))
        }
        .padding(14)
        .background(OmniTheme.cardSurface)
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private var analyseButton: some View {
        Button {
            Task { await viewModel.analyzeArticle() }
        } label: {
            HStack {
                if viewModel.isAnalyzingArticle {
                    ProgressView()
                        .tint(.white)
                    Text(viewModel.batchProgress ?? "Analysing with Gemini...")
                } else {
                    Image(systemName: "sparkles")
                    Text("Analyse & Draft Grounded X Post")
                }
            }
            .omniScaledFont(size: 15, weight: .bold, scale: viewModel.fontScale)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(OmniTheme.primaryTerracotta)
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled(viewModel.isAnalyzingArticle)
    }

    private var draftQueueSection: some View {
        let limit = viewModel.isXBlue ? AppPreferencesStore.xPremiumCharLimit : AppPreferencesStore.xStandardCharLimit

        return VStack(alignment: .leading, spacing: 14) {
            Text("REVIEW & PUBLISH (\(viewModel.postDrafts.count) DRAFT\(viewModel.postDrafts.count == 1 ? "" : "S"))")
                .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.primaryTerracotta)

            ForEach(viewModel.postDrafts) { draft in
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text(draft.label)
                            .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(OmniTheme.primaryTerracotta)
                            .cornerRadius(6)

                        if !draft.source.isEmpty {
                            Text(draft.source)
                                .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                                .foregroundColor(OmniTheme.deepInk)
                                .lineLimit(1)
                        }

                        Spacer()

                        let overLimit = draft.text.count > limit
                        Text("\(draft.text.count)/\(limit)")
                            .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                            .foregroundColor(overLimit ? OmniTheme.errorCrimson : OmniTheme.forestGreen)
                    }

                    if !draft.mainTopic.isEmpty {
                        Text("Main topic analysed: \(draft.mainTopic)")
                            .omniScaledFont(size: 12, weight: .medium, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.mutedSlate)
                    }

                    TextEditor(
                        text: Binding(
                            get: { draft.text },
                            set: { viewModel.updateDraftText(id: draft.id, newText: $0) }
                        )
                    )
                    .omniScaledFont(size: 14, weight: .regular, scale: viewModel.fontScale)
                    .frame(minHeight: 130)
                    .padding(8)
                    .background(OmniTheme.paperBackground)
                    .cornerRadius(8)

                    HStack(spacing: 10) {
                        Button {
                            viewModel.openInXComposer(text: draft.text)
                        } label: {
                            Label("Open in X App", systemImage: "arrow.up.right.square")
                                .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(OmniTheme.subtleSurface)
                                .foregroundColor(OmniTheme.deepInk)
                                .cornerRadius(8)
                        }

                        Spacer()

                        Text(draft.status.rawValue)
                            .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                            .foregroundColor(draft.status == .posted ? OmniTheme.forestGreen : OmniTheme.mutedSlate)
                    }
                }
                .padding(14)
                .background(OmniTheme.cardSurface)
                .cornerRadius(12)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(OmniTheme.borderWarm, lineWidth: 1))
            }

            Button {
                Task { await viewModel.approveAndPostToX() }
            } label: {
                HStack {
                    if viewModel.isPostingToX {
                        ProgressView().tint(.white)
                    } else {
                        Image(systemName: "paperplane.fill")
                    }
                    Text(viewModel.isPostingToX ? "Publishing to X..." : "Approve & Post All via OAuth 2.0")
                }
                .omniScaledFont(size: 15, weight: .bold, scale: viewModel.fontScale)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(OmniTheme.forestGreen)
                .foregroundColor(.white)
                .cornerRadius(12)
            }
            .disabled(viewModel.isPostingToX)

            if let status = viewModel.postToXStatus {
                Text(status)
                    .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.forestGreen)
            }
        }
    }
}
