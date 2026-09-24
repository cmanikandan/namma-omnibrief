import Foundation
import SwiftUI
import UIKit

@MainActor
public final class MainViewModel: ObservableObject {

    public static let headlineRefreshIntervalSeconds: TimeInterval = 60 * 60 // 1 hour
    public static let headlineStaleAfterSeconds: TimeInterval = 15 * 60 // 15 minutes

    public let prefs: AppPreferencesStore
    public let archiveStore: BriefArchiveStore
    private let hnClient: HackerNewsClient
    private let geminiClient: GeminiClient
    private let xClient: XClient

    // Navigation & Theme
    @Published public var selectedTab: LiteDestination = .today
    @Published public var fontScale: Double

    // Today — Hacker News Top 10
    @Published public var headlines: [HeadlineItem] = []
    @Published public var headlineSort: HeadlineSort
    @Published public var isLoadingHeadlines: Bool = false
    @Published public var headlinesError: String?
    @Published public var headlinesLastUpdated: String?
    private var lastHeadlineFetchDate: Date?

    public var sortedHeadlines: [HeadlineItem] {
        headlineSort.apply(headlines)
    }

    // Article -> X State
    @Published public var articleTextInput: String = ""
    @Published public var articleImages: [UIImage] = []
    @Published public var articleSource: String
    @Published public var isAnalyzingArticle: Bool = false
    @Published public var batchProgress: String?
    @Published public var articleAnalysisResult: ArticleAnalysisResult?
    @Published public var postDrafts: [XPostDraftItem] = []
    @Published public var activePostDraft: String = ""
    @Published public var isPostingToX: Bool = false
    @Published public var postToXStatus: String?
    @Published public var articleError: String?

    private var sourceIsUserOverride: Bool = false
    private var archivedArticleBriefId: UUID?

    // Archive / History (max 10 FIFO)
    @Published public var archivedBriefs: [BriefItem] = []

    // Settings observables
    @Published public var geminiApiKey: String
    @Published public var geminiModel: String
    @Published public var xClientId: String
    @Published public var xClientSecret: String
    @Published public var xAccessToken: String
    @Published public var xRefreshToken: String
    @Published public var isXBlue: Bool
    @Published public var attachImageToPost: Bool
    @Published public var defaultSource: String
    @Published public var settingsStatusMessage: String?

    public init(
        prefs: AppPreferencesStore = AppPreferencesStore(),
        archiveStore: BriefArchiveStore = BriefArchiveStore(),
        hnClient: HackerNewsClient = HackerNewsClient(),
        geminiClient: GeminiClient = GeminiClient(),
        xClient: XClient = XClient()
    ) {
        self.prefs = prefs
        self.archiveStore = archiveStore
        self.hnClient = hnClient
        self.geminiClient = geminiClient
        self.xClient = xClient

        self.fontScale = prefs.fontScale
        self.headlineSort = prefs.headlineSort
        self.articleSource = prefs.defaultSource
        self.geminiApiKey = prefs.geminiApiKey
        self.geminiModel = prefs.geminiModel
        self.xClientId = prefs.xClientId
        self.xClientSecret = prefs.xClientSecret
        self.xAccessToken = prefs.xAccessToken
        self.xRefreshToken = prefs.xRefreshToken
        self.isXBlue = prefs.isXBlue
        self.attachImageToPost = prefs.attachImageToPost
        self.defaultSource = prefs.defaultSource
        self.archivedBriefs = archiveStore.loadAll()

        Task {
            await refreshHeadlines()
        }
    }

    // MARK: - Today Feed Actions

    public func setHeadlineSort(_ sort: HeadlineSort) {
        headlineSort = sort
        prefs.headlineSort = sort
    }

    public func onAppResumed() {
        guard let last = lastHeadlineFetchDate else {
            Task { await refreshHeadlines() }
            return
        }
        if Date().timeIntervalSince(last) >= Self.headlineStaleAfterSeconds {
            Task { await refreshHeadlines() }
        }
    }

    public func refreshHeadlines() async {
        guard !isLoadingHeadlines else { return }
        isLoadingHeadlines = true
        headlinesError = nil
        do {
            let fetched = try await hnClient.fetchTopHeadlines()
            headlines = fetched
            lastHeadlineFetchDate = Date()
            let formatter = DateFormatter()
            formatter.dateFormat = "h:mm a"
            headlinesLastUpdated = formatter.string(from: Date())
        } catch {
            headlinesError = error.localizedDescription
        }
        isLoadingHeadlines = false
    }

    public func draftPostFromHeadline(_ item: HeadlineItem) {
        var text = item.title
        if !item.domain.isEmpty {
            text += "\n\nSource: \(item.domain)"
        }
        text += "\n\(item.openUrl)"
        articleTextInput = text
        articleError = nil
        selectedTab = .articleToX
    }

    // MARK: - Article -> X Actions

    public func addArticleImage(_ image: UIImage) {
        guard articleImages.count < AppPreferencesStore.maxArticleImages else {
            articleError = "Maximum \(AppPreferencesStore.maxArticleImages) images per batch. Remove one to add another."
            return
        }
        articleImages.append(image)
        articleError = nil
    }

    public func removeArticleImage(at index: Int) {
        guard articleImages.indices.contains(index) else { return }
        articleImages.remove(at: index)
    }

    public func setArticleSource(_ newSource: String) {
        let previous = articleSource
        articleSource = newSource
        sourceIsUserOverride = !newSource.isEmpty && newSource != AppPreferencesStore.autoDetectSource
        retargetDraftsToSource(newSource: newSource, fallbackOldSource: previous)
    }

    private func retargetDraftsToSource(newSource: String, fallbackOldSource: String) {
        guard !newSource.isEmpty, newSource != AppPreferencesStore.autoDetectSource, !postDrafts.isEmpty else {
            return
        }
        let anyPosted = postDrafts.contains { $0.status == .posted }

        postDrafts = postDrafts.map { draft in
            guard draft.status != .posted else { return draft }
            let oldSrc = draft.source.isEmpty ? fallbackOldSource : draft.source
            let retargeted = SourceRetargeter.retargetSource(text: draft.text, oldSource: oldSrc, newSource: newSource)
            var copy = draft
            copy.text = retargeted
            copy.source = newSource
            return copy
        }
        if let first = postDrafts.first {
            activePostDraft = first.text
        }

        if !anyPosted, let briefId = archivedArticleBriefId {
            _ = archiveStore.retargetBrief(id: briefId, newSource: newSource)
            archivedBriefs = archiveStore.loadAll()
        }
    }

    public func updateDraftText(id: String, newText: String) {
        postDrafts = postDrafts.map { item in
            guard item.id == id else { return item }
            var copy = item
            copy.text = newText
            copy.status = .pending
            copy.error = nil
            return copy
        }
        if postDrafts.first?.id == id {
            activePostDraft = newText
        }
    }

    public func removeDraft(id: String) {
        postDrafts.removeAll { $0.id == id }
        activePostDraft = postDrafts.first?.text ?? ""
    }

    public func clearArticleWorkspace() {
        articleImages = []
        articleTextInput = ""
        articleAnalysisResult = nil
        postDrafts = []
        activePostDraft = ""
        articleError = nil
        postToXStatus = nil
        batchProgress = nil
        articleSource = prefs.defaultSource
        sourceIsUserOverride = false
        archivedArticleBriefId = nil
    }

    /// Loads a realistic sample Financial Times / India Tech article so the user can test source
    /// retargeting, character counter, and X posting without needing a live newspaper page first.
    public func loadSampleArticle() {
        let detected = sourceIsUserOverride ? articleSource : "Financial Times"
        let sampleText = """
        India's sovereign AI compute cluster expands to 18,000 GPUs across Bengaluru and Hyderabad.

        Key findings reported by \(detected):
        - Public-private subsidies cut inference training costs by 42% for domestic foundation model startups.
        - Commercial rollout slated for Q4 2026.

        Source: \(detected)
        #IndiaTech #GenAI #CloudInfra
        """
        articleSource = detected
        let draft = XPostDraftItem(
            label: "Sample FT",
            imageJpegData: nil,
            text: sampleText,
            source: detected,
            headline: "India expands sovereign AI compute cluster to 18,000 GPUs",
            mainTopic: "India sovereign AI GPU expansion and startup compute subsidies",
            fullSummary: "• 18,000 GPUs deployed across Bengaluru and Hyderabad\n• 42% subsidy on foundation model training\n• Commercial access starts Q4 2026"
        )
        postDrafts = [draft]
        activePostDraft = draft.text
        articleAnalysisResult = ArticleAnalysisResult(
            detectedSource: detected,
            sourceDetectedAutomatically: !sourceIsUserOverride,
            headline: draft.headline,
            postDraft: draft.text,
            hashtags: ["#IndiaTech", "#GenAI", "#CloudInfra"],
            fullSummary: draft.fullSummary,
            mainTopic: draft.mainTopic
        )
        let savedId = archiveStore.saveWithRollover(
            BriefItem(
                title: draft.headline,
                content: draft.text,
                sourceOrSpeaker: detected,
                imageCount: 0
            )
        )
        archivedArticleBriefId = savedId
        archivedBriefs = archiveStore.loadAll()
        articleError = nil
    }

    public func analyzeArticle() async {
        let trimmedText = articleTextInput.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedText.isEmpty && articleImages.isEmpty {
            articleError = "Please attach a newspaper photo or paste an article excerpt first."
            return
        }

        isAnalyzingArticle = true
        articleError = nil
        postToXStatus = nil
        postDrafts = []

        var collected: [XPostDraftItem] = []
        var failures: [String] = []
        let cappedImages = Array(articleImages.prefix(AppPreferencesStore.maxArticleImages))

        for (index, img) in cappedImages.enumerated() {
            batchProgress = "Analysing image \(index + 1) of \(cappedImages.count)..."
            guard let jpeg = Self.downscaledJpegData(from: img, maxDimension: 1600) else {
                failures.append("Image \(index + 1): Could not encode JPEG.")
                continue
            }

            do {
                let res = try await geminiClient.analyzeArticle(
                    apiKey: prefs.geminiApiKey,
                    modelName: prefs.geminiModel,
                    textInput: "",
                    imageJpegBuffers: [jpeg],
                    specifiedSource: sourceIsUserOverride ? articleSource : nil,
                    isXBlue: prefs.isXBlue
                )
                let effectiveSource: String = {
                    if sourceIsUserOverride { return articleSource }
                    if !res.detectedSource.isEmpty && res.detectedSource != "Unknown" {
                        return res.detectedSource
                    }
                    return articleSource
                }()
                let finalDraftText = sourceIsUserOverride
                    ? SourceRetargeter.retargetSource(text: res.postDraft, oldSource: res.detectedSource, newSource: articleSource)
                    : res.postDraft

                if index == 0 && !sourceIsUserOverride && !effectiveSource.isEmpty {
                    articleSource = effectiveSource
                }
                if index == 0 {
                    articleAnalysisResult = res
                }
                collected.append(
                    XPostDraftItem(
                        label: "Image \(index + 1)",
                        imageJpegData: jpeg,
                        text: finalDraftText,
                        source: effectiveSource,
                        headline: res.headline,
                        mainTopic: res.mainTopic,
                        fullSummary: res.fullSummary
                    )
                )
            } catch {
                failures.append("Image \(index + 1): \(error.localizedDescription)")
            }
        }

        if !trimmedText.isEmpty {
            batchProgress = "Analysing pasted text..."
            do {
                let res = try await geminiClient.analyzeArticle(
                    apiKey: prefs.geminiApiKey,
                    modelName: prefs.geminiModel,
                    textInput: trimmedText,
                    imageJpegBuffers: [],
                    specifiedSource: sourceIsUserOverride ? articleSource : nil,
                    isXBlue: prefs.isXBlue
                )
                let effectiveSource: String = {
                    if sourceIsUserOverride { return articleSource }
                    if !res.detectedSource.isEmpty && res.detectedSource != "Unknown" {
                        return res.detectedSource
                    }
                    return articleSource
                }()
                let finalDraftText = sourceIsUserOverride
                    ? SourceRetargeter.retargetSource(text: res.postDraft, oldSource: res.detectedSource, newSource: articleSource)
                    : res.postDraft

                if collected.isEmpty && !sourceIsUserOverride && !effectiveSource.isEmpty {
                    articleSource = effectiveSource
                }
                if articleAnalysisResult == nil {
                    articleAnalysisResult = res
                }
                collected.append(
                    XPostDraftItem(
                        label: "Pasted text",
                        imageJpegData: nil,
                        text: finalDraftText,
                        source: effectiveSource,
                        headline: res.headline,
                        mainTopic: res.mainTopic,
                        fullSummary: res.fullSummary
                    )
                )
            } catch {
                failures.append("Pasted text: \(error.localizedDescription)")
            }
        }

        batchProgress = nil
        isAnalyzingArticle = false
        postDrafts = collected
        activePostDraft = collected.first?.text ?? ""

        if !collected.isEmpty {
            let joined = SourceRetargeter.joinDraftsForArchive(collected)
            let savedId = archiveStore.saveWithRollover(
                BriefItem(
                    title: collected.first?.headline ?? "Article Summary",
                    content: joined,
                    sourceOrSpeaker: articleSource,
                    imageCount: cappedImages.count
                )
            )
            archivedArticleBriefId = savedId
            archivedBriefs = archiveStore.loadAll()
        }

        if !failures.isEmpty {
            articleError = failures.joined(separator: "\n")
        }
    }

    public func approveAndPostToX() async {
        guard !postDrafts.isEmpty else { return }
        let charLimit = prefs.isXBlue ? AppPreferencesStore.xPremiumCharLimit : AppPreferencesStore.xStandardCharLimit

        // Pre-flight guard matching Android MainViewModel.approveAndPostToX
        for draft in postDrafts where draft.status != .posted {
            if draft.text.count > charLimit {
                articleError = "\(draft.label) is \(draft.text.count) characters (limit is \(charLimit)). Shorten it or enable X Premium in Settings."
                return
            }
        }

        isPostingToX = true
        articleError = nil

        for idx in postDrafts.indices {
            if postDrafts[idx].status == .posted { continue }
            postDrafts[idx].status = .posting
            postToXStatus = "Publishing \(idx + 1) of \(postDrafts.count)..."

            let draft = postDrafts[idx]
            let imageToAttach = prefs.attachImageToPost ? draft.imageJpegData : nil

            let outcome = await xClient.postTweet(
                accessToken: prefs.xAccessToken,
                text: draft.text,
                clientId: prefs.xClientId,
                clientSecret: prefs.xClientSecret,
                refreshToken: prefs.xRefreshToken,
                accessTokenExpiresAtMillis: prefs.xTokenExpiresAtMillis,
                imageJpegData: imageToAttach,
                onTokenRefreshed: { [weak self] newAccess, newRefresh, expiresIn in
                    Task { @MainActor in
                        self?.prefs.saveRefreshedTokens(
                            accessToken: newAccess,
                            refreshToken: newRefresh,
                            expiresInSeconds: expiresIn
                        )
                        self?.xAccessToken = newAccess
                        self?.xRefreshToken = newRefresh
                    }
                }
            )

            switch outcome {
            case .success(let tweetId, _):
                postDrafts[idx].status = .posted
                postDrafts[idx].tweetId = tweetId
                postDrafts[idx].error = nil
            case .failure(let msg):
                postDrafts[idx].status = .failed
                postDrafts[idx].error = msg
                articleError = msg
            }
        }

        isPostingToX = false
        let postedCount = postDrafts.filter { $0.status == .posted }.count
        if postedCount == postDrafts.count {
            postToXStatus = "Published \(postedCount) post(s) to X!"
        } else {
            postToXStatus = "Published \(postedCount) of \(postDrafts.count) post(s)."
        }
    }

    public func openInXComposer(text: String) {
        guard let url = XClient.directComposerURL(for: text) else { return }
        UIApplication.shared.open(url)
    }

    // MARK: - Archive Actions

    public func loadArchivedDraft(_ item: BriefItem) {
        let parts = SourceRetargeter.splitArchivedDraft(item.content)
        articleSource = item.sourceOrSpeaker.isEmpty ? prefs.defaultSource : item.sourceOrSpeaker
        articleImages = []
        articleError = nil
        postToXStatus = nil
        postDrafts = parts.enumerated().map { idx, text in
            XPostDraftItem(
                label: parts.count > 1 ? "Part \(idx + 1)" : "Archived",
                imageJpegData: nil,
                text: text,
                source: item.sourceOrSpeaker,
                headline: item.title
            )
        }
        activePostDraft = parts.first ?? ""
        selectedTab = .articleToX
    }

    public func deleteArchivedBrief(id: UUID) {
        archiveStore.delete(id: id)
        archivedBriefs = archiveStore.loadAll()
    }

    public func clearArchive() {
        archiveStore.clearAll()
        archivedBriefs = []
    }

    // MARK: - Settings Persistence

    public func saveSettings() {
        prefs.geminiApiKey = geminiApiKey
        prefs.geminiModel = geminiModel
        prefs.xClientId = xClientId
        prefs.xClientSecret = xClientSecret
        prefs.xAccessToken = xAccessToken
        prefs.xRefreshToken = xRefreshToken
        prefs.isXBlue = isXBlue
        prefs.attachImageToPost = attachImageToPost
        prefs.defaultSource = defaultSource
        prefs.fontScale = fontScale
        settingsStatusMessage = "Settings saved on-device."
    }

    public func setFontScale(_ scale: Double) {
        fontScale = scale
        prefs.fontScale = scale
    }

    public func importBulkEnv(_ raw: String) {
        let count = prefs.importEnvBlock(raw)
        geminiApiKey = prefs.geminiApiKey
        xClientId = prefs.xClientId
        xClientSecret = prefs.xClientSecret
        xAccessToken = prefs.xAccessToken
        xRefreshToken = prefs.xRefreshToken
        settingsStatusMessage = count > 0
            ? "Imported \(count) key(s) from .env snippet."
            : "No matching keys found in snippet."
    }

    /// Normalizes EXIF orientation by redrawing into a standard UIGraphicsImageRenderer context
    /// and downscales to `maxDimension` (1600 px) before JPEG encoding — preventing sideways uploads.
    public static func downscaledJpegData(from image: UIImage, maxDimension: CGFloat = 1600) -> Data? {
        let size = image.size
        guard size.width > 0, size.height > 0 else { return nil }
        let maxSide = max(size.width, size.height)
        let ratio = maxSide > maxDimension ? (maxDimension / maxSide) : 1.0
        let targetSize = CGSize(width: round(size.width * ratio), height: round(size.height * ratio))

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1.0
        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        let normalized = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
        return normalized.jpegData(compressionQuality: 0.85)
    }
}
