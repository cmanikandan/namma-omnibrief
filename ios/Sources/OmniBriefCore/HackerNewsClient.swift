import Foundation

/// Fetches and ranks Hacker News stories from the public Algolia HN Search API (`https://hn.algolia.com/api/v1`).
///
/// Mirrors Android's `HackerNewsService.kt`:
/// - Keyless, public API so the Today screen works immediately on first launch.
/// - Ranking formula: `points + 600 * matchedInterests.count + (isFrontPage ? 250 : 0)`.
/// - Diversity cap: `maxPerInterest = 3` with score-based backfill up to `maxHeadlines = 10`.
public final class HackerNewsClient: Sendable {

    public struct Interest: Sendable {
        public let label: String
        public let terms: [String]
        public init(label: String, terms: [String]) {
            self.label = label
            self.terms = terms
        }
    }

    public static let baseURL = "https://hn.algolia.com/api/v1"
    public static let maxHeadlines = 10
    public static let scorePerInterest = 600
    public static let scoreFrontPage = 250
    public static let maxPerInterest = 3

    public static let interests: [Interest] = [
        Interest(label: "GenAI", terms: ["genai", "generative ai", "llm", "ai model", "diffusion"]),
        Interest(label: "OpenAI", terms: ["openai", "chatgpt", "gpt-4", "gpt-5", "sora", "sam altman"]),
        Interest(label: "Gemini", terms: ["gemini", "deepmind", "bard"]),
        Interest(label: "Google", terms: ["google", "alphabet", "android", "chrome"]),
        Interest(label: "Anthropic", terms: ["anthropic", "claude"]),
        Interest(label: "India Tech", terms: ["india", "bengaluru", "bangalore", "upi", "indian"]),
        Interest(label: "AI", terms: ["artificial intelligence", "machine learning", "neural", "agent"])
    ]

    public static let queryTerms: [String] = [
        "openai", "anthropic claude", "gemini deepmind", "generative ai", "india", "llm"
    ]

    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    /// Returns which interest labels match the given title (case-insensitive).
    public static func matchInterests(title: String) -> [String] {
        let lower = title.lowercased()
        return interests.compactMap { interest in
            interest.terms.contains(where: { lower.contains($0) }) ? interest.label : nil
        }
    }

    /// Computes the relevance score for a story.
    public static func score(item: HeadlineItem) -> Int {
        var total = item.points
        total += item.matchedInterests.count * scorePerInterest
        if item.isFrontPage {
            total += scoreFrontPage
        }
        return total
    }

    /// Ranks and selects up to `maxHeadlines` stories while capping each primary interest at `maxPerInterest`
    /// and backfilling by score if needed.
    public static func rankAndSelectDiverse(_ rawItems: [HeadlineItem]) -> [HeadlineItem] {
        let annotated = rawItems.map { item -> HeadlineItem in
            var copy = item
            copy.matchedInterests = matchInterests(title: item.title)
            return copy
        }
        let ranked = annotated.sorted { score(item: $0) > score(item: $1) }

        var picked: [HeadlineItem] = []
        var pickedIds = Set<String>()
        var perInterest: [String: Int] = [:]

        for item in ranked {
            if picked.count >= maxHeadlines { break }
            let primary = item.matchedInterests.first ?? "Burning"
            let used = perInterest[primary, default: 0]
            if used < maxPerInterest && !pickedIds.contains(item.id) {
                picked.append(item)
                pickedIds.insert(item.id)
                perInterest[primary] = used + 1
            }
        }

        if picked.count < maxHeadlines {
            for item in ranked {
                if picked.count >= maxHeadlines { break }
                if !pickedIds.contains(item.id) {
                    picked.append(item)
                    pickedIds.insert(item.id)
                }
            }
        }
        return picked
    }

    /// Fetches top Hacker News stories concurrently across front page + standing interest queries.
    public func fetchTopHeadlines() async throws -> [HeadlineItem] {
        let since = Int64(Date().timeIntervalSince1970) - (14 * 24 * 60 * 60)

        return try await withThrowingTaskGroup(of: (Bool, [HeadlineItem]).self) { group in
            group.addTask {
                let items = (try? await self.fetchEndpoint("\(Self.baseURL)/search?tags=front_page&hitsPerPage=30")) ?? []
                return (true, items)
            }

            for term in Self.queryTerms {
                group.addTask {
                    let encoded = term.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? term
                    let urlStr = "\(Self.baseURL)/search?query=\(encoded)&tags=story&numericFilters=created_at_i>\(since),points>20&hitsPerPage=15"
                    let items = (try? await self.fetchEndpoint(urlStr)) ?? []
                    return (false, items)
                }
            }

            var collectedById: [String: HeadlineItem] = [:]
            var orderedIds: [String] = []

            for try await (isFront, items) in group {
                for var item in items {
                    if let existing = collectedById[item.id] {
                        item.isFrontPage = existing.isFrontPage || isFront
                        collectedById[item.id] = item
                    } else {
                        item.isFrontPage = isFront
                        collectedById[item.id] = item
                        orderedIds.append(item.id)
                    }
                }
            }

            let merged = orderedIds.compactMap { collectedById[$0] }
            guard !merged.isEmpty else {
                throw NSError(
                    domain: "HackerNewsClient",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "Hacker News returned no stories. Check your internet connection."]
                )
            }

            return Self.rankAndSelectDiverse(merged)
        }
    }

    private func fetchEndpoint(_ urlString: String) async throws -> [HeadlineItem] {
        guard let url = URL(string: urlString) else { return [] }
        var req = URLRequest(url: url)
        req.timeoutInterval = 20
        req.setValue("NammaOmnibrief-iOS/1.0", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }

        return Self.parseHits(from: data)
    }

    public static func parseHits(from data: Data) -> [HeadlineItem] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let hits = root["hits"] as? [[String: Any]] else {
            return []
        }

        var items: [HeadlineItem] = []
        for hit in hits {
            let rawTitle = (hit["title"] as? String) ?? (hit["story_title"] as? String) ?? ""
            let title = rawTitle.trimmingCharacters(in: .whitespacesAndNewlines)
            let id = ((hit["objectID"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if title.isEmpty || id.isEmpty { continue }

            let rawUrl = (hit["url"] as? String) ?? ""
            let url = (rawUrl == "null") ? "" : rawUrl
            let points = (hit["points"] as? Int) ?? 0
            let commentCount = (hit["num_comments"] as? Int) ?? 0
            let author = (hit["author"] as? String) ?? ""
            let createdAt = (hit["created_at"] as? String) ?? ""
            let createdAtSeconds: Int64 = {
                if let val = hit["created_at_i"] as? Int64 { return val }
                if let val = hit["created_at_i"] as? Int { return Int64(val) }
                if let val = hit["created_at_i"] as? Double { return Int64(val) }
                return 0
            }()

            items.append(
                HeadlineItem(
                    id: id,
                    title: title,
                    url: url,
                    points: points,
                    commentCount: commentCount,
                    author: author,
                    createdAt: createdAt,
                    createdAtSeconds: createdAtSeconds
                )
            )
        }
        return items
    }
}
