import Foundation
import SwiftUI
import UIKit
import Combine
import UserNotifications

public enum TodaySubSection: String, CaseIterable, Identifiable, Sendable {
    case headlines = "Hacker News"
    case lumaEvents = "BLR Tech Events"
    case arxivPaper = "Trending arXiv AI"

    public var id: String { rawValue }
    public var icon: String {
        switch self {
        case .headlines: return "flame.fill"
        case .lumaEvents: return "mappin.and.ellipse"
        case .arxivPaper: return "doc.text.magnifyingglass"
        }
    }
}

@MainActor
public final class MainViewModel: ObservableObject {

    public static let headlineRefreshIntervalSeconds: TimeInterval = 60 * 60 // 1 hour
    public static let headlineStaleAfterSeconds: TimeInterval = 15 * 60 // 15 minutes

    public let prefs: AppPreferencesStore
    public let archiveStore: BriefArchiveStore
    private let hnClient: HackerNewsClient
    private let geminiClient: GeminiClient
    private let xClient: XClient
    private var cancellables = Set<AnyCancellable>()
    private var autoRefreshTask: Task<Void, Never>?

    // Navigation & Theme
    @Published public var selectedTab: LiteDestination = .today
    @Published public var fontScale: Double

    // Today — Sub-section Selector
    @Published public var todaySubSection: TodaySubSection = .headlines

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

    // Today — Bengaluru Lu.ma Tech & AI Events (2-3x daily refresh)
    @Published public var lumaEvents: [LumaBengaluruEvent] = []
    @Published public var isLoadingLumaEvents: Bool = false
    @Published public var lumaEventsError: String?
    @Published public var lumaEventsLastUpdated: String?
    private var lastLumaFetchDate: Date?

    // Today — Trending arXiv GenAI Paper (ELI5 / Simplified)
    @Published public var dailyArxivPaper: ArxivGenAIPaper?
    @Published public var isLoadingArxivPaper: Bool = false
    @Published public var arxivPaperError: String?
    @Published public var trendingArxivIndex: Int = 0
    @Published public var isPostingArxivToX: Bool = false
    @Published public var arxivPostStatus: String?
    private var cachedRawArxivEntries: [GeminiClient.ArxivRawEntry] = []

    // Hourly Lock Screen Feed
    @Published public var lockScreenHourlyEnabled: Bool

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

    // Conference Reporter State
    @Published public var confTitle: String = ""
    @Published public var confTopic: String = ""
    @Published public var confSpeaker: String = ""
    @Published public var confPhotos: [UIImage] = []
    @Published public var isGeneratingConferenceReport: Bool = false
    @Published public var conferenceProgress: String?
    @Published public var conferenceReportResult: ConferenceReportResult?
    @Published public var conferenceError: String?
    @Published public var conferenceSuccessMessage: String?
    public let audioManager: AudioSessionManager = AudioSessionManager()

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
        self.lockScreenHourlyEnabled = prefs.lockScreenTopStoryEnabled

        // Forward AudioSessionManager mutations to MainViewModel so SwiftUI observers immediately update
        audioManager.objectWillChange
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)

        // Load cached feeds or samples immediately
        if let cachedEvents = try? JSONDecoder().decode([LumaBengaluruEvent].self, from: Data(prefs.lumaEventsCache.utf8)), !cachedEvents.isEmpty {
            self.lumaEvents = cachedEvents
            let ts = prefs.lumaEventsCacheTimestamp
            if ts > 0 {
                let f = DateFormatter()
                f.dateFormat = "MMM d, h:mm a"
                self.lumaEventsLastUpdated = f.string(from: Date(timeIntervalSince1970: TimeInterval(ts)))
            }
        } else {
            self.lumaEvents = GeminiClient.sampleBengaluruEvents()
        }

        if let cachedPaper = try? JSONDecoder().decode(ArxivGenAIPaper.self, from: Data(prefs.arxivDailyPaperCache.utf8)) {
            self.dailyArxivPaper = cachedPaper
        } else {
            self.dailyArxivPaper = GeminiClient.sampleArxivPaper()
        }

        if ProcessInfo.processInfo.arguments.contains("-tab_luma") {
            self.todaySubSection = .lumaEvents
        } else if ProcessInfo.processInfo.arguments.contains("-tab_arxiv") {
            self.todaySubSection = .arxivPaper
        } else if ProcessInfo.processInfo.arguments.contains("-screen_drafter_arxiv") {
            if let paper = self.dailyArxivPaper {
                self.draftPostFromArxivPaper(paper)
            }
            self.selectedTab = .articleToX
        } else if ProcessInfo.processInfo.arguments.contains("-screen_drafter") {
            self.selectedTab = .articleToX
        } else if ProcessInfo.processInfo.arguments.contains("-screen_conference") {
            self.selectedTab = .conference
        } else if ProcessInfo.processInfo.arguments.contains("-screen_settings") {
            self.selectedTab = .settings
        }

        Task {
            await refreshHeadlines()
        }

        startHeadlineAutoRefresh()
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
        if let lastLuma = lastLumaFetchDate, Date().timeIntervalSince(lastLuma) >= 8 * 3600 {
            Task { await fetchLumaBengaluruEvents() }
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

            if lockScreenHourlyEnabled, let top = sortedHeadlines.first {
                postLockScreenNotification(for: top)
            }
        } catch {
            headlinesError = error.localizedDescription
        }
        isLoadingHeadlines = false
    }

    public func startHeadlineAutoRefresh() {
        autoRefreshTask?.cancel()
        autoRefreshTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: UInt64(Self.headlineRefreshIntervalSeconds * 1_000_000_000))
                guard !Task.isCancelled, let self = self else { break }
                await self.refreshHeadlines()
            }
        }
    }

    public func setLockScreenHourlyEnabled(_ enabled: Bool) {
        lockScreenHourlyEnabled = enabled
        prefs.lockScreenTopStoryEnabled = enabled
        if enabled {
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                DispatchQueue.main.async {
                    if !granted {
                        self.lockScreenHourlyEnabled = false
                        self.prefs.lockScreenTopStoryEnabled = false
                    } else if let top = self.sortedHeadlines.first {
                        self.postLockScreenNotification(for: top)
                    }
                }
            }
        }
    }

    public func postLockScreenNotification(for item: HeadlineItem) {
        let content = UNMutableNotificationContent()
        content.title = "🔥 YenGuru • #1 Signal on Hacker News"
        let domainStr = item.domain.isEmpty ? "" : " • \(item.domain)"
        content.subtitle = "\(item.points) pts\(domainStr)"
        content.body = item.title
        content.sound = .default
        content.userInfo = ["url": item.openUrl, "title": item.title]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        let request = UNNotificationRequest(identifier: "yenguru_hn_top_story", content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)
    }

    // MARK: - Lu.ma Bengaluru Tech & AI Events (2-3x Daily)

    public func fetchLumaBengaluruEvents(forceRefresh: Bool = false) async {
        guard !isLoadingLumaEvents else { return }

        // If not forcing refresh and feed was fetched within the last 8 hours, keep current
        if !forceRefresh, !lumaEvents.isEmpty, let last = lastLumaFetchDate, Date().timeIntervalSince(last) < 8 * 3600 {
            return
        }

        isLoadingLumaEvents = true
        lumaEventsError = nil

        do {
            let fetched = try await geminiClient.fetchLumaBengaluruEvents(
                apiKey: geminiApiKey,
                modelName: geminiModel
            )
            lumaEvents = fetched
            lastLumaFetchDate = Date()
            let f = DateFormatter()
            f.dateFormat = "MMM d, h:mm a"
            lumaEventsLastUpdated = f.string(from: Date())

            if let encoded = try? JSONEncoder().encode(fetched),
               let str = String(data: encoded, encoding: .utf8) {
                prefs.lumaEventsCache = str
                prefs.lumaEventsCacheTimestamp = Int64(Date().timeIntervalSince1970)
            }
        } catch {
            lumaEventsError = error.localizedDescription
            if lumaEvents.isEmpty {
                lumaEvents = GeminiClient.sampleBengaluruEvents()
            }
        }

        isLoadingLumaEvents = false
    }

    // MARK: - Daily Trending arXiv GenAI Paper

    public func loadDailyArxivPaper(forceRefresh: Bool = false) async {
        guard !isLoadingArxivPaper else { return }

        if !forceRefresh && dailyArxivPaper != nil {
            return
        }

        isLoadingArxivPaper = true
        arxivPaperError = nil

        do {
            if cachedRawArxivEntries.isEmpty || forceRefresh {
                cachedRawArxivEntries = try await geminiClient.fetchTrendingArxivPapers(count: 6)
            }

            guard !cachedRawArxivEntries.isEmpty else {
                dailyArxivPaper = GeminiClient.sampleArxivPaper()
                isLoadingArxivPaper = false
                return
            }

            let index = trendingArxivIndex % cachedRawArxivEntries.count
            let chosen = cachedRawArxivEntries[index]

            let simplified = try await geminiClient.simplifyArxivPaper(
                apiKey: geminiApiKey,
                modelName: geminiModel,
                rawEntry: chosen
            )

            dailyArxivPaper = simplified

            if let encoded = try? JSONEncoder().encode(simplified),
               let str = String(data: encoded, encoding: .utf8) {
                prefs.arxivDailyPaperCache = str
                prefs.arxivDailyPaperTimestamp = Int64(Date().timeIntervalSince1970)
            }
        } catch {
            arxivPaperError = error.localizedDescription
            if dailyArxivPaper == nil {
                dailyArxivPaper = GeminiClient.sampleArxivPaper()
            }
        }

        isLoadingArxivPaper = false
    }

    public func loadNextArxivPaper() async {
        trendingArxivIndex += 1
        await loadDailyArxivPaper(forceRefresh: true)
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

    public func draftPostFromArxivPaper(_ paper: ArxivGenAIPaper) {
        let text = paper.formatForX(isXBlue: prefs.isXBlue)
        let jpeg = NanoBananaCardRenderer.renderJpeg(for: paper)
        if let uiImg = NanoBananaCardRenderer.renderImage(for: paper) {
            articleImages = [uiImg]
        } else {
            articleImages = []
        }

        let draftItem = XPostDraftItem(
            label: "arXiv: \(paper.title)",
            imageJpegData: jpeg,
            text: text,
            source: "arXiv",
            headline: paper.title,
            mainTopic: paper.coreBreakthrough,
            fullSummary: paper.plainEnglishSummary
        )

        postDrafts = [draftItem]
        activePostDraft = text
        articleTextInput = text
        articleSource = "arXiv"
        sourceIsUserOverride = true
        articleError = nil
        selectedTab = .articleToX
    }

    public func postArxivPaperToX(_ paper: ArxivGenAIPaper) async {
        let charLimit = prefs.isXBlue ? AppPreferencesStore.xPremiumCharLimit : AppPreferencesStore.xStandardCharLimit
        let tweetText = paper.formatForX(isXBlue: prefs.isXBlue)

        let trimmedText = tweetText.count > charLimit
            ? String(tweetText.prefix(charLimit - 3)) + "..."
            : tweetText

        guard !prefs.xAccessToken.isEmpty || !prefs.xRefreshToken.isEmpty else {
            arxivPostStatus = "⚠️ X OAuth not configured in Settings. Tap 'Draft in X Drafter' to edit or post via X app."
            return
        }

        isPostingArxivToX = true
        arxivPostStatus = "Uploading Nano-Banana infographic & publishing to X..."

        let jpegData = NanoBananaCardRenderer.renderJpeg(for: paper)

        let outcome = await xClient.postTweet(
            accessToken: prefs.xAccessToken,
            text: trimmedText,
            clientId: prefs.xClientId,
            clientSecret: prefs.xClientSecret,
            refreshToken: prefs.xRefreshToken,
            accessTokenExpiresAtMillis: prefs.xTokenExpiresAtMillis,
            imageJpegData: jpegData,
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

        isPostingArxivToX = false
        switch outcome {
        case .success(let tweetId, _):
            arxivPostStatus = "✅ Published to X with Nano-Banana infographic! Tweet ID: \(tweetId)"
        case .failure(let error):
            arxivPostStatus = "❌ X post failed: \(error)"
        }
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

        struct EncodedImage: Sendable {
            let index: Int
            let jpeg: Data
        }

        var encodedList: [EncodedImage] = []
        for (index, img) in cappedImages.enumerated() {
            if let jpeg = Self.downscaledJpegData(from: img, maxDimension: 1600) {
                encodedList.append(EncodedImage(index: index, jpeg: jpeg))
            } else {
                failures.append("Image \(index + 1): Could not encode JPEG.")
            }
        }

        if !encodedList.isEmpty {
            batchProgress = "Analysing \(encodedList.count) images concurrently with \(prefs.geminiModel)..."
            let apiKey = prefs.geminiApiKey
            let model = prefs.geminiModel
            let specifiedSource = sourceIsUserOverride ? articleSource : nil
            let isXBlue = prefs.isXBlue
            let client = geminiClient

            let results = await withTaskGroup(of: (Int, Data, Result<ArticleAnalysisResult, Error>).self) { group in
                for item in encodedList {
                    group.addTask {
                        do {
                            let res = try await client.analyzeArticle(
                                apiKey: apiKey,
                                modelName: model,
                                textInput: "",
                                imageJpegBuffers: [item.jpeg],
                                specifiedSource: specifiedSource,
                                isXBlue: isXBlue
                            )
                            return (item.index, item.jpeg, .success(res))
                        } catch {
                            return (item.index, item.jpeg, .failure(error))
                        }
                    }
                }

                var collectedGroup: [(Int, Data, Result<ArticleAnalysisResult, Error>)] = []
                for await element in group {
                    collectedGroup.append(element)
                }
                return collectedGroup.sorted { $0.0 < $1.0 }
            }

            for (index, jpeg, result) in results {
                switch result {
                case .success(let res):
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
                case .failure(let error):
                    failures.append("Image \(index + 1): \(error.localizedDescription)")
                }
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

    // MARK: - Conference Reporter

    public func addConferencePhoto(_ image: UIImage) {
        confPhotos.append(image)
        conferenceError = nil
    }

    public func removeConferencePhoto(at index: Int) {
        guard confPhotos.indices.contains(index) else { return }
        confPhotos.remove(at: index)
    }

    public func clearConferencePhotos() {
        confPhotos.removeAll()
    }

    public func clearConferenceWorkspace() {
        confTitle = ""
        confTopic = ""
        confSpeaker = ""
        confPhotos.removeAll()
        conferenceReportResult = nil
        conferenceError = nil
        conferenceSuccessMessage = nil
        audioManager.discardRecording()
    }

    public func loadSampleConferenceSession() {
        confTitle = "Google I/O Global Summit"
        confTopic = "Gemini 3.5 & Next-Gen Multimodal Reasoning"
        confSpeaker = "Sundar Pichai & Demis Hassabis"
        conferenceError = nil
        conferenceReportResult = ConferenceReportResult(
            sessionTitle: "Google I/O: Next-Gen Multimodal Reasoning",
            speaker: "Sundar Pichai & Demis Hassabis",
            executiveSummary: "Comprehensive overview of 2M+ token high-context windows, real-time multimodal latency optimizations, and agentic workflows integrated into enterprise workflows.",
            keyTakeaways: [
                "2M token context enables entire codebases and hours of video analysis in a single prompt.",
                "Sub-second multimodal response loops drastically improve mobile on-device assistants.",
                "Sovereign data isolation models meet stringent healthcare and financial data compliance."
            ],
            slideInsights: [
                "Architecture slide: unified multimodal encoder shared across text, vision and audio.",
                "Benchmark slide: 40% latency reduction versus the previous generation."
            ],
            actionItems: [
                "Audit internal knowledge bases for long-context ingestion.",
                "Prototype multimodal visual document pipelines."
            ],
            fullReportMarkdown: """
            # Conference Executive Brief: Google I/O Global Summit
            **Session**: Gemini 3.5 & Next-Gen Multimodal Reasoning  
            **Speaker**: Sundar Pichai & Demis Hassabis  

            ## Executive Summary
            Google I/O spotlighted major breakthroughs in high-context multimodal reasoning, reducing latency by 40% while expanding document analysis capabilities.

            ## Key Takeaways
            - 2M token context enables deep codebase and financial filings analysis.
            - New enterprise guardrails for enterprise privacy.
            - Multimodal agents natively processing video streams in real-time.

            ## Slide Insights
            - Unified architecture across text, audio, and visual embeddings.
            - Direct edge compilation for mobile deployment.

            ## Strategic Action Items
            - Evaluate high-context models for corporate document analysis.
            - Pilot real-time voice and visual assistance interfaces.

            ---
            *Synthesized by YenGuru*
            """
        )
        conferenceSuccessMessage = "Loaded Google I/O keynote sample report."
    }

    public func loadBengaluruTechConferenceSample() {
        confTitle = "Bengaluru Tech Summit 2026"
        confTopic = "Autonomous Urban Transit & Sovereign AI Compute"
        confSpeaker = "Dr. C. N. Ashwath Narayan & Tech Leaders"
        conferenceError = nil
        conferenceReportResult = ConferenceReportResult(
            sessionTitle: "Bengaluru Tech Summit 2026: Sovereign AI & Autonomous Urban Infrastructure",
            speaker: "Dr. C. N. Ashwath Narayan & Tech Leaders",
            executiveSummary: "At BTS 2026, Karnataka inaugurated the 10,000-GPU 'Namma AI Grid' compute infrastructure and autonomous mobility algorithms deployed across Bengaluru's expanding Metro network. The session outlined how public-private partnerships in Karnataka are enabling localized Indic AI models while driving 100% renewable power data center development along the Devanahalli tech corridor.",
            keyTakeaways: [
                "10,000 GPUs dedicated for Bengaluru tech ecosystem startups and research institutions.",
                "Namma Metro automated passenger load prediction reducing urban gridlock along Outer Ring Road (ORR).",
                "Sovereign Kannada & Indic LLMs achieving benchmark parity on technical and legal reasoning.",
                "Devanahalli Green Tech Corridor providing 100% renewable power to next-gen AI supercomputing centers."
            ],
            slideInsights: [
                "Grid architecture: distributed micro-datacenters along Namma Metro Pink & Blue line stations.",
                "Energy metrics: solar-first power management with 99.999% uptime."
            ],
            actionItems: [
                "Participate in Namma AI Grid incubation cohort.",
                "Evaluate Indic language APIs for localized customer support applications."
            ],
            fullReportMarkdown: """
            # Conference Executive Brief: Bengaluru Tech Summit 2026
            **Session**: Autonomous Urban Transit & Sovereign AI Compute  
            **Speaker**: Dr. C. N. Ashwath Narayan & Tech Leaders  

            ## Executive Summary
            Karnataka unveiled sovereign compute infrastructure and AI urban mobility systems at BTS 2026, establishing Bengaluru as Asia's primary sovereign AI hub.

            ## Key Takeaways
            - 10,000 GPU cluster reserved for local startups and academia.
            - Real-time AI transit balancing deployed along Outer Ring Road and Metro corridors.
            - Open Indic language models tailored for Indian enterprise governance.

            ## Slide Insights
            - Real-time commuter load heatmaps optimizing bus and metro dispatch frequencies.
            - Renewable energy integration across Devanahalli server farms.

            ## Action Items
            - Apply for GPU compute grants via Karnataka Innovation Technology Society.
            - Test Kannada & Indic LLM endpoints on internal domain datasets.

            ---
            *Synthesized by YenGuru • Bengaluru*
            """
        )
        conferenceSuccessMessage = "Loaded Bengaluru Tech Summit sample report."
    }

    public func synthesizeConferenceReport() async {
        guard !isGeneratingConferenceReport else { return }

        let hasPhotos = !confPhotos.isEmpty
        let recordedURL = audioManager.stopRecording() ?? audioManager.recordedFileURL
        let hasAudio = recordedURL != nil && FileManager.default.fileExists(atPath: recordedURL!.path)
        let hasTopic = !confTopic.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty

        guard hasPhotos || hasAudio || hasTopic else {
            conferenceError = "Please add slide photos, record session audio, or enter a session topic."
            return
        }

        // Request background execution time so iOS will not suspend synthesis if phone sleeps or user switches apps
        var bgTaskId = UIBackgroundTaskIdentifier.invalid
        bgTaskId = UIApplication.shared.beginBackgroundTask(withName: "YenGuruConferenceSynthesis") {
            UIApplication.shared.endBackgroundTask(bgTaskId)
            bgTaskId = .invalid
        }
        defer {
            if bgTaskId != .invalid {
                UIApplication.shared.endBackgroundTask(bgTaskId)
                bgTaskId = .invalid
            }
        }

        isGeneratingConferenceReport = true
        conferenceProgress = "Preparing session slides & audio buffer..."
        conferenceError = nil
        conferenceSuccessMessage = nil

        let photoBuffers = confPhotos.compactMap { Self.downscaledJpegData(from: $0) }
        var audioBytes: Data? = nil
        if let audioURL = recordedURL {
            audioBytes = try? Data(contentsOf: audioURL)
        }

        if let audio = audioBytes, !audio.isEmpty {
            conferenceProgress = "Uploading audio (\(audioManager.fileSizeDescription)) & slides to Gemini..."
        } else {
            conferenceProgress = "Synthesizing slides & session notes with Gemini..."
        }

        do {
            let report = try await geminiClient.generateConferenceReport(
                apiKey: geminiApiKey,
                modelName: geminiModel,
                conferenceTitle: confTitle,
                sessionTopic: confTopic,
                speakerName: confSpeaker,
                photoJpegBuffers: photoBuffers,
                audioData: audioBytes,
                audioMimeType: "audio/m4a"
            )
            self.conferenceReportResult = report
            self.conferenceSuccessMessage = "Report synthesized successfully!"
            self.conferenceProgress = nil

            // Save to Archive
            let brief = BriefItem(
                type: "CONFERENCE_REPORT",
                title: report.sessionTitle,
                content: report.fullReportMarkdown,
                sourceOrSpeaker: "\(report.speaker) • \(confTitle.isEmpty ? "Conference" : confTitle)",
                imageCount: confPhotos.count,
                status: "Report"
            )
            _ = archiveStore.saveWithRollover(brief, maxLimit: 10)
            archivedBriefs = archiveStore.loadAll()
        } catch {
            self.conferenceError = error.localizedDescription
            self.conferenceProgress = nil
        }

        isGeneratingConferenceReport = false
    }
}
