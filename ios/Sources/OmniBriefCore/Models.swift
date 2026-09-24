import Foundation

/// Ordering applied to the Today feed after it has been fetched and ranked.
///
/// This is a *sort*, not a filter: both modes show the same ten stories, so switching to
/// `.newest` never hides something that `.forYou` surfaced. `id` is persisted to UserDefaults.
public enum HeadlineSort: String, CaseIterable, Identifiable, Sendable {
    /// The interest-weighted ranking the service produced. The default.
    case forYou = "for_you"
    /// Strict reverse-chronological by the story's Hacker News post time.
    case newest = "newest"

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .forYou: return "For you"
        case .newest: return "Newest"
        }
    }

    public static func fromId(_ id: String?) -> HeadlineSort {
        guard let id = id else { return .forYou }
        return HeadlineSort(rawValue: id) ?? .forYou
    }

    /// Returns `items` in this ordering.
    /// `.forYou` returns the list untouched; `.newest` performs a stable descending sort by `createdAtSeconds`.
    public func apply(_ items: [HeadlineItem]) -> [HeadlineItem] {
        switch self {
        case .forYou:
            return items
        case .newest:
            return items.enumerated()
                .sorted { lhs, rhs in
                    if lhs.element.createdAtSeconds != rhs.element.createdAtSeconds {
                        return lhs.element.createdAtSeconds > rhs.element.createdAtSeconds
                    }
                    return lhs.offset < rhs.offset
                }
                .map(\.element)
        }
    }
}

/// One ranked Hacker News story shown on the Today home screen.
public struct HeadlineItem: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let title: String
    public let url: String
    public let points: Int
    public let commentCount: Int
    public let author: String
    public let createdAt: String
    public let createdAtSeconds: Int64
    public var matchedInterests: [String]
    public var isFrontPage: Bool

    public init(
        id: String,
        title: String,
        url: String,
        points: Int,
        commentCount: Int,
        author: String,
        createdAt: String,
        createdAtSeconds: Int64 = 0,
        matchedInterests: [String] = [],
        isFrontPage: Bool = false
    ) {
        self.id = id
        self.title = title
        self.url = url
        self.points = points
        self.commentCount = commentCount
        self.author = author
        self.createdAt = createdAt
        self.createdAtSeconds = createdAtSeconds
        self.matchedInterests = matchedInterests
        self.isFrontPage = isFrontPage
    }

    /// Bare domain for display, e.g. "arstechnica.com". Empty for Ask/Show HN text posts.
    public var domain: String {
        guard !url.isEmpty, let parsed = URL(string: url), let host = parsed.host else {
            return ""
        }
        return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
    }

    /// Permalink to the HN discussion, used when a story has no external URL.
    public var discussionUrl: String {
        "https://news.ycombinator.com/item?id=\(id)"
    }

    /// The link to actually open: the article if there is one, otherwise the HN thread.
    public var openUrl: String {
        url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? discussionUrl : url
    }

    /// Compact age of the story, e.g. "just now", "42m ago", "6h ago", "2d ago".
    /// Returns an empty string when `createdAtSeconds` is unknown (<= 0).
    public func relativeAge(nowSeconds: Int64 = Int64(Date().timeIntervalSince1970)) -> String {
        if createdAtSeconds <= 0 { return "" }
        let delta = nowSeconds - createdAtSeconds
        if delta < 60 { return "just now" }
        let minutes = delta / 60
        if minutes < 60 { return "\(minutes)m ago" }
        let hours = minutes / 60
        if hours < 24 { return "\(hours)h ago" }
        let days = hours / 24
        if days < 7 { return "\(days)d ago" }
        return "\(days / 7)w ago"
    }
}

/// Result returned by Gemini 8-rule grounded article analysis.
public struct ArticleAnalysisResult: Equatable, Codable, Sendable {
    public let detectedSource: String
    public let sourceDetectedAutomatically: Bool
    public let headline: String
    public let postDraft: String
    public let hashtags: [String]
    public let fullSummary: String
    public let mainTopic: String

    public init(
        detectedSource: String,
        sourceDetectedAutomatically: Bool,
        headline: String,
        postDraft: String,
        hashtags: [String],
        fullSummary: String,
        mainTopic: String = ""
    ) {
        self.detectedSource = detectedSource
        self.sourceDetectedAutomatically = sourceDetectedAutomatically
        self.headline = headline
        self.postDraft = postDraft
        self.hashtags = hashtags
        self.fullSummary = fullSummary
        self.mainTopic = mainTopic
    }
}

/// Lifecycle of a single queued X post draft.
public enum DraftPostStatus: String, Codable, Sendable {
    case pending = "PENDING"
    case posting = "POSTING"
    case posted = "POSTED"
    case failed = "FAILED"
}

/// One reviewable X post in the Article -> X batch queue.
public struct XPostDraftItem: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let label: String
    public var imageJpegData: Data?
    public var text: String
    public var source: String
    public var headline: String
    public var mainTopic: String
    public var fullSummary: String
    public var status: DraftPostStatus
    public var tweetId: String?
    public var error: String?

    public init(
        id: String = UUID().uuidString,
        label: String,
        imageJpegData: Data? = nil,
        text: String,
        source: String = "",
        headline: String = "",
        mainTopic: String = "",
        fullSummary: String = "",
        status: DraftPostStatus = .pending,
        tweetId: String? = nil,
        error: String? = nil
    ) {
        self.id = id
        self.label = label
        self.imageJpegData = imageJpegData
        self.text = text
        self.source = source
        self.headline = headline
        self.mainTopic = mainTopic
        self.fullSummary = fullSummary
        self.status = status
        self.tweetId = tweetId
        self.error = error
    }
}

/// Local archive item stored with a 10-item FIFO rollover.
public struct BriefItem: Identifiable, Equatable, Codable, Sendable {
    public let id: UUID
    public let type: String
    public var title: String
    public var content: String
    public var sourceOrSpeaker: String
    public let timestamp: Date
    public let imageCount: Int
    public var status: String

    public init(
        id: UUID = UUID(),
        type: String = "ARTICLE_X",
        title: String,
        content: String,
        sourceOrSpeaker: String,
        timestamp: Date = Date(),
        imageCount: Int = 0,
        status: String = "Draft"
    ) {
        self.id = id
        self.type = type
        self.title = title
        self.content = content
        self.sourceOrSpeaker = sourceOrSpeaker
        self.timestamp = timestamp
        self.imageCount = imageCount
        self.status = status
    }
}
