import XCTest
@testable import OmniBriefCore

final class OmniBriefCoreTests: XCTestCase {

    // MARK: - 1. Source Retargeting Tests (Mirrors Android SourceRetargetTest.kt)

    private let sampleDraft = """
    Hiring mandates for AI adoption managers in India have risen 65% year-on-year.

    Key findings reported by The Times of India:
    - Mandates projected to rise to 2,600-2,700 in 2026.

    Source: The Times of India
    """

    func testRetargetSourceRewritesProseAndAttributionLine() {
        let result = SourceRetargeter.retargetSource(
            text: sampleDraft,
            oldSource: "The Times of India",
            newSource: "The Economic Times"
        )
        XCTAssertFalse(result.contains("Times of India"), "No mention of the old publication may survive")
        XCTAssertTrue(result.contains("Source: The Economic Times"), "Attribution line is rewritten")
        XCTAssertTrue(result.contains("reported by The Economic Times:"), "Mid-sentence mention is rewritten")
    }

    func testRetargetSourceLeavesRestOfDraftUntouched() {
        let result = SourceRetargeter.retargetSource(
            text: sampleDraft,
            oldSource: "The Times of India",
            newSource: "The Economic Times"
        )
        XCTAssertTrue(result.contains("risen 65% year-on-year"))
        XCTAssertTrue(result.contains("2,600-2,700 in 2026"))
        XCTAssertEqual(
            sampleDraft.components(separatedBy: .newlines).count,
            result.components(separatedBy: .newlines).count
        )
    }

    func testRetargetSourceHandlesModelDroppingLeadingThe() {
        let text = "Times of India reported the figure.\n\nSource: The Times of India"
        let result = SourceRetargeter.retargetSource(
            text: text,
            oldSource: "The Times of India",
            newSource: "Deccan Herald"
        )
        XCTAssertEqual(result, "Deccan Herald reported the figure.\n\nSource: Deccan Herald")
    }

    func testRetargetSourceRewritesAttributionEvenWhenOldSourceIsUnknown() {
        let text = "A summary.\n\nSource: Some Paper"
        let result = SourceRetargeter.retargetSource(
            text: text,
            oldSource: "",
            newSource: "Financial Times"
        )
        XCTAssertEqual(result, "A summary.\n\nSource: Financial Times")
    }

    func testRetargetSourceIsCaseInsensitive() {
        let text = "per the economic times.\n\nSource: The Economic Times"
        let result = SourceRetargeter.retargetSource(
            text: text,
            oldSource: "The Economic Times",
            newSource: "Mint"
        )
        XCTAssertEqual(result, "per Mint.\n\nSource: Mint")
    }

    func testRetargetSourceNormalisesWhenSourceUnchanged() {
        let text = "A summary.\n\nSource: the economic times"
        let result = SourceRetargeter.retargetSource(
            text: text,
            oldSource: "The Economic Times",
            newSource: "The Economic Times"
        )
        XCTAssertEqual(result, "A summary.\n\nSource: The Economic Times")
    }

    func testRetargetSourceDoesNotTouchMidSentenceSourceColon() {
        let text = "He said Source: is a word.\n\nSource: The Hindu"
        let result = SourceRetargeter.retargetSource(
            text: text,
            oldSource: "The Hindu",
            newSource: "Mint"
        )
        XCTAssertEqual(result, "He said Source: is a word.\n\nSource: Mint")
    }

    // MARK: - 2. HeadlineSort & Relative Age Tests (Mirrors HeadlineSortTest.kt)

    func testHeadlineSortForYouPreservesOriginalRelevanceOrder() {
        let items = [
            HeadlineItem(id: "1", title: "Older high relevance", url: "", points: 100, commentCount: 10, author: "a", createdAt: "", createdAtSeconds: 1000),
            HeadlineItem(id: "2", title: "Newer lower relevance", url: "", points: 50, commentCount: 5, author: "b", createdAt: "", createdAtSeconds: 2000)
        ]
        let sorted = HeadlineSort.forYou.apply(items)
        XCTAssertEqual(sorted.map(\.id), ["1", "2"])
    }

    func testHeadlineSortNewestOrdersDescendingWithStableTies() {
        let items = [
            HeadlineItem(id: "first-tie", title: "Tie A", url: "", points: 200, commentCount: 10, author: "a", createdAt: "", createdAtSeconds: 5000),
            HeadlineItem(id: "older", title: "Older", url: "", points: 400, commentCount: 10, author: "b", createdAt: "", createdAtSeconds: 1000),
            HeadlineItem(id: "second-tie", title: "Tie B", url: "", points: 100, commentCount: 10, author: "c", createdAt: "", createdAtSeconds: 5000),
            HeadlineItem(id: "newest", title: "Newest", url: "", points: 50, commentCount: 10, author: "d", createdAt: "", createdAtSeconds: 9000)
        ]
        let sorted = HeadlineSort.newest.apply(items)
        XCTAssertEqual(sorted.map(\.id), ["newest", "first-tie", "second-tie", "older"])
    }

    func testRelativeAgeFormatting() {
        let itemUnknown = HeadlineItem(id: "0", title: "T", url: "", points: 1, commentCount: 0, author: "a", createdAt: "", createdAtSeconds: 0)
        XCTAssertEqual(itemUnknown.relativeAge(nowSeconds: 10_000), "")

        let itemFresh = HeadlineItem(id: "1", title: "T", url: "", points: 1, commentCount: 0, author: "a", createdAt: "", createdAtSeconds: 9_980)
        XCTAssertEqual(itemFresh.relativeAge(nowSeconds: 10_000), "just now")

        let itemMins = HeadlineItem(id: "2", title: "T", url: "https://www.reuters.com/tech", points: 1, commentCount: 0, author: "a", createdAt: "", createdAtSeconds: 10_000 - (42 * 60))
        XCTAssertEqual(itemMins.relativeAge(nowSeconds: 10_000), "42m ago")
        XCTAssertEqual(itemMins.domain, "reuters.com")
    }

    // MARK: - 3. Hacker News Ranking & Diversity Cap Tests

    func testHackerNewsInterestScoringAndDiversityCap() {
        // Create 5 OpenAI stories and 2 India Tech stories
        var raw: [HeadlineItem] = []
        for i in 1...5 {
            raw.append(HeadlineItem(
                id: "oai-\(i)",
                title: "OpenAI launches GPT-5 feature \(i)",
                url: "https://openai.com/\(i)",
                points: 500 - (i * 10),
                commentCount: 50,
                author: "sam",
                createdAt: "",
                createdAtSeconds: 1000
            ))
        }
        for i in 1...2 {
            raw.append(HeadlineItem(
                id: "ind-\(i)",
                title: "Bengaluru startup builds sovereign UPI rail \(i)",
                url: "https://example.in/\(i)",
                points: 120 - (i * 10),
                commentCount: 20,
                author: "blru",
                createdAt: "",
                createdAtSeconds: 1000
            ))
        }

        let selected = HackerNewsClient.rankAndSelectDiverse(raw)
        // First pass should cap OpenAI at 3, include both India Tech stories (total 5), then backfill the remaining 2 OpenAI stories since total < 10
        XCTAssertEqual(selected.count, 7)
        let firstFiveIds = Array(selected.prefix(5)).map(\.id)
        XCTAssertTrue(firstFiveIds.contains("ind-1"), "Diversity cap must allow India Tech into top 5 before OpenAI monopolizes all slots")
        XCTAssertTrue(firstFiveIds.contains("ind-2"))
    }

    // MARK: - 4. Archived Draft Splitting Tests (Mirrors ArchivedDraftTest.kt)

    func testSplitArchivedDraftStripsImagePrefixOnlyAtStart() {
        let stored = "[Image 1] First post body with [legitimate bracket] inside.\n\n---\n\n[Pasted text] Second post body."
        let parts = SourceRetargeter.splitArchivedDraft(stored)
        XCTAssertEqual(parts.count, 2)
        XCTAssertEqual(parts[0], "First post body with [legitimate bracket] inside.")
        XCTAssertEqual(parts[1], "Second post body.")
    }

    // MARK: - 5. BriefArchiveStore 10-Item FIFO Rollover & Retargeting

    func testArchiveStoreEnforcesTenItemFIFORolloverAndRetargeting() {
        let tempURL = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("test_archive_\(UUID().uuidString).json")
        defer { try? FileManager.default.removeItem(at: tempURL) }

        let store = BriefArchiveStore(fileURL: tempURL)
        var lastId = UUID()
        for i in 1...12 {
            let item = BriefItem(
                title: "Headline \(i)",
                content: "Reported by The Times of India.\n\nSource: The Times of India",
                sourceOrSpeaker: "The Times of India"
            )
            lastId = store.saveWithRollover(item, maxLimit: 10)
        }

        let loaded = store.loadAll()
        XCTAssertEqual(loaded.count, 10, "FIFO rollover must keep strictly 10 items")
        XCTAssertEqual(loaded.first?.title, "Headline 12")
        XCTAssertEqual(loaded.last?.title, "Headline 3")

        // Retarget the newest archived brief in place
        let updated = store.retargetBrief(id: lastId, newSource: "The Economic Times")
        XCTAssertEqual(updated?.sourceOrSpeaker, "The Economic Times")
        XCTAssertTrue(updated?.content.contains("Source: The Economic Times") == true)
        XCTAssertFalse(updated?.content.contains("Times of India") == true)
    }

    // MARK: - 6. X Token Refresh & Zero Hardcoded Secrets Guard

    func testXClientNeedsRefreshLogicAndPreferencesTokenOrder() {
        // Blank token always needs refresh
        XCTAssertTrue(XClient.needsRefresh(accessToken: "", expiresAtMillis: 0, nowMillis: 1_000_000))
        // Hand-pasted token with unknown expiry (0) should NOT pre-refresh; tried directly
        XCTAssertFalse(XClient.needsRefresh(accessToken: "hand_pasted_token", expiresAtMillis: 0, nowMillis: 1_000_000))
        // Token expiring within 60s buffer should refresh
        XCTAssertTrue(XClient.needsRefresh(accessToken: "valid_tok", expiresAtMillis: 1_050_000, nowMillis: 1_000_000))
        // Token with plenty of lifetime left should not refresh
        XCTAssertFalse(XClient.needsRefresh(accessToken: "valid_tok", expiresAtMillis: 2_000_000, nowMillis: 1_000_000))

        let suiteName = "test_prefs_\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let prefs = AppPreferencesStore(defaults: defaults)
        // Verify zero baked-in secrets
        XCTAssertTrue(prefs.xClientId.isEmpty)
        XCTAssertTrue(prefs.xClientSecret.isEmpty)
        XCTAssertTrue(prefs.xAccessToken.isEmpty)
        XCTAssertTrue(prefs.xRefreshToken.isEmpty)

        // Verify saveRefreshedTokens preserves expiry
        prefs.saveRefreshedTokens(accessToken: "NEW_ACC", refreshToken: "NEW_REF", expiresInSeconds: 7200, nowMillis: 1_000_000)
        XCTAssertEqual(prefs.xAccessToken, "NEW_ACC")
        XCTAssertEqual(prefs.xRefreshToken, "NEW_REF")
        XCTAssertEqual(prefs.xTokenExpiresAtMillis, 1_000_000 + 7_200_000)

        // Verify bulk .env import
        let count = prefs.importEnvBlock("""
        # Comment line
        GEMINI_API_KEY=FAKE_GEMINI_KEY_FOR_TEST
        X_CLIENT_ID="FAKE_CLIENT_ID_123"
        """)
        XCTAssertEqual(count, 2)
        XCTAssertEqual(prefs.geminiApiKey, "FAKE_GEMINI_KEY_FOR_TEST")
        XCTAssertEqual(prefs.xClientId, "FAKE_CLIENT_ID_123")
    }
}
