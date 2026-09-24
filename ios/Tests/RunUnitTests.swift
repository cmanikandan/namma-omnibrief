import Foundation

/// Santa-safe JIT Test Harness for Namma Omnibrief Lite (iOS).
///
/// Why this exists (documented in AGENTS.md):
/// On a Google corporate MacBook (gMac) with Santa enabled, `swift test` / SwiftPM compiles
/// `Package.swift` into an unsigned temporary binary (`ios-manifest` in `/var/folders/...`) and
/// executes it, which triggers a Santa block popup.
///
/// Running `OmniBriefCore` + this test suite inside Apple's signed `xcrun swift` JIT interpreter
/// executes entirely in-process inside the Apple-signed `swift-frontend` binary — zero unsigned
/// binaries are spawned, so Santa never blocks it, and all 14 domain assertions run in <1 second.

struct TestRunner {
    var passed = 0
    var failed = 0

    mutating func check(_ condition: @autoclosure () -> Bool, _ name: String, detail: String = "") {
        if condition() {
            passed += 1
            print("  ✅ PASS: \(name)")
        } else {
            failed += 1
            print("  ❌ FAIL: \(name) \(detail.isEmpty ? "" : "— \(detail)")")
        }
    }

    mutating func assertEqual<T: Equatable>(_ actual: T, _ expected: T, _ name: String) {
        if actual == expected {
            passed += 1
            print("  ✅ PASS: \(name)")
        } else {
            failed += 1
            print("  ❌ FAIL: \(name)\n     Expected: \(expected)\n     Actual:   \(actual)")
        }
    }
}

struct OmniBriefJITTestMain {
    static func main() {
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        print("🧪 Namma Omnibrief Lite (iOS) — Automated Unit Test Suite (Santa-Safe)")
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        var runner = TestRunner()

        let sampleDraft = """
        Hiring mandates for AI adoption managers in India have risen 65% year-on-year.

        Key findings reported by The Times of India:
        - Mandates projected to rise to 2,600-2,700 in 2026.

        Source: The Times of India
        """

        // 1. Source Retargeting Tests
        let r1 = SourceRetargeter.retargetSource(
            text: sampleDraft,
            oldSource: "The Times of India",
            newSource: "The Economic Times"
        )
        runner.check(
            !r1.contains("Times of India")
                && r1.contains("Source: The Economic Times")
                && r1.contains("reported by The Economic Times:"),
            "1. SourceRetargeter rewrites both mid-sentence prose and Source: line"
        )

        runner.check(
            r1.contains("risen 65% year-on-year")
                && r1.contains("2,600-2,700 in 2026")
                && sampleDraft.components(separatedBy: .newlines).count == r1.components(separatedBy: .newlines).count,
            "2. SourceRetargeter leaves surrounding figures and line count untouched"
        )

        let r3 = SourceRetargeter.retargetSource(
            text: "Times of India reported the figure.\n\nSource: The Times of India",
            oldSource: "The Times of India",
            newSource: "Deccan Herald"
        )
        runner.assertEqual(
            r3,
            "Deccan Herald reported the figure.\n\nSource: Deccan Herald",
            "3. SourceRetargeter handles model dropping leading 'The'"
        )

        let r4 = SourceRetargeter.retargetSource(
            text: "A summary.\n\nSource: Some Paper",
            oldSource: "",
            newSource: "Financial Times"
        )
        runner.assertEqual(
            r4,
            "A summary.\n\nSource: Financial Times",
            "4. SourceRetargeter rewrites attribution line even when oldSource is blank"
        )

        let r5 = SourceRetargeter.retargetSource(
            text: "per the economic times.\n\nSource: The Economic Times",
            oldSource: "The Economic Times",
            newSource: "Mint"
        )
        runner.assertEqual(
            r5,
            "per Mint.\n\nSource: Mint",
            "5. SourceRetargeter matches oldSource case-insensitively"
        )

        let r6 = SourceRetargeter.retargetSource(
            text: "A summary.\n\nSource: the economic times",
            oldSource: "The Economic Times",
            newSource: "The Economic Times"
        )
        runner.assertEqual(
            r6,
            "A summary.\n\nSource: The Economic Times",
            "6. SourceRetargeter normalises casing when source is unchanged"
        )

        let r7 = SourceRetargeter.retargetSource(
            text: "He said Source: is a word.\n\nSource: The Hindu",
            oldSource: "The Hindu",
            newSource: "Mint"
        )
        runner.assertEqual(
            r7,
            "He said Source: is a word.\n\nSource: Mint",
            "7. SourceRetargeter does not touch 'Source:' mid-sentence"
        )

        // 2. HeadlineSort & Relative Age Tests
        let items = [
            HeadlineItem(id: "first-tie", title: "Tie A", url: "", points: 200, commentCount: 10, author: "a", createdAt: "", createdAtSeconds: 5000),
            HeadlineItem(id: "older", title: "Older", url: "", points: 400, commentCount: 10, author: "b", createdAt: "", createdAtSeconds: 1000),
            HeadlineItem(id: "second-tie", title: "Tie B", url: "", points: 100, commentCount: 10, author: "c", createdAt: "", createdAtSeconds: 5000),
            HeadlineItem(id: "newest", title: "Newest", url: "https://www.reuters.com/tech", points: 50, commentCount: 10, author: "d", createdAt: "", createdAtSeconds: 9000)
        ]
        runner.assertEqual(
            HeadlineSort.forYou.apply(items).map(\.id),
            ["first-tie", "older", "second-tie", "newest"],
            "8. HeadlineSort.forYou preserves upstream interest relevance order"
        )
        runner.assertEqual(
            HeadlineSort.newest.apply(items).map(\.id),
            ["newest", "first-tie", "second-tie", "older"],
            "9. HeadlineSort.newest orders descending by epoch with stable tie-breaking"
        )
        runner.check(
            items[3].relativeAge(nowSeconds: 9000 + 42 * 60) == "42m ago" && items[3].domain == "reuters.com",
            "10. HeadlineItem computes relative age ('42m ago') and bare domain ('reuters.com')"
        )

        // 3. HackerNews Ranking & Diversity Cap Tests
        var rawStories: [HeadlineItem] = []
        for i in 1...5 {
            rawStories.append(HeadlineItem(id: "oai-\(i)", title: "OpenAI launches GPT-5 feature \(i)", url: "https://openai.com/\(i)", points: 500 - i * 10, commentCount: 50, author: "sam", createdAt: "", createdAtSeconds: 1000))
        }
        for i in 1...2 {
            rawStories.append(HeadlineItem(id: "ind-\(i)", title: "Bengaluru startup builds sovereign UPI rail \(i)", url: "https://example.in/\(i)", points: 120 - i * 10, commentCount: 20, author: "blru", createdAt: "", createdAtSeconds: 1000))
        }
        let rankedDiverse = HackerNewsClient.rankAndSelectDiverse(rawStories)
        let topFiveIds = Array(rankedDiverse.prefix(5)).map(\.id)
        runner.check(
            rankedDiverse.count == 7 && topFiveIds.contains("ind-1") && topFiveIds.contains("ind-2"),
            "11. HackerNewsClient enforces maxPerInterest=3 diversity cap with score backfill"
        )

        // 4. Archived Draft Splitting Tests
        let splitParts = SourceRetargeter.splitArchivedDraft(
            "[Image 1] First post body with [legitimate bracket] inside.\n\n---\n\n[Pasted text] Second post body."
        )
        runner.assertEqual(
            splitParts,
            ["First post body with [legitimate bracket] inside.", "Second post body."],
            "12. SourceRetargeter.splitArchivedDraft strips leading [Image N] tags while keeping inner brackets"
        )

        // 5. BriefArchiveStore 10-Item FIFO Rollover & In-Place Retargeting
        let tempURL = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("test_archive_\(UUID().uuidString).json")
        defer { try? FileManager.default.removeItem(at: tempURL) }
        let archiveStore = BriefArchiveStore(fileURL: tempURL)
        var newestBriefId = UUID()
        for i in 1...12 {
            newestBriefId = archiveStore.saveWithRollover(
                BriefItem(title: "Headline \(i)", content: "Reported by The Times of India.\n\nSource: The Times of India", sourceOrSpeaker: "The Times of India"),
                maxLimit: 10
            )
        }
        let retargetedBrief = archiveStore.retargetBrief(id: newestBriefId, newSource: "The Economic Times")
        let allArchived = archiveStore.loadAll()
        runner.check(
            allArchived.count == 10
                && allArchived.first?.title == "Headline 12"
                && allArchived.last?.title == "Headline 3"
                && retargetedBrief?.sourceOrSpeaker == "The Economic Times"
                && retargetedBrief?.content.contains("Source: The Economic Times") == true,
            "13. BriefArchiveStore enforces 10-item FIFO rollover and in-place source retargeting"
        )

        // 6. X Token Expiry & Zero Hardcoded Secrets Guard
        let suiteName = "test_prefs_\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let prefs = AppPreferencesStore(defaults: defaults)
        let noHardcodedSecrets = prefs.xClientId.isEmpty && prefs.xClientSecret.isEmpty && prefs.xAccessToken.isEmpty && prefs.xRefreshToken.isEmpty
        prefs.saveRefreshedTokens(accessToken: "NEW_ACC", refreshToken: "NEW_REF", expiresInSeconds: 7200, nowMillis: 1_000_000)
        let importedCount = prefs.importEnvBlock("GEMINI_API_KEY=FAKE_GEMINI_KEY_FOR_TEST\nX_CLIENT_ID=\"FAKE_CLIENT_ID_123\"")
        runner.check(
            noHardcodedSecrets
                && XClient.needsRefresh(accessToken: "", expiresAtMillis: 0, nowMillis: 1_000_000)
                && !XClient.needsRefresh(accessToken: "pasted", expiresAtMillis: 0, nowMillis: 1_000_000)
                && XClient.needsRefresh(accessToken: "expiring", expiresAtMillis: 1_050_000, nowMillis: 1_000_000)
                && prefs.xTokenExpiresAtMillis == 8_200_000
                && importedCount == 2
                && prefs.geminiApiKey == "FAKE_GEMINI_KEY_FOR_TEST",
            "14. XClient token expiry buffer, zero hardcoded secrets, and bulk .env import verified"
        )

        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        print("Result: \(runner.passed) passed, \(runner.failed) failed")
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        if runner.failed > 0 {
            Foundation.exit(1)
        }
    }
}

OmniBriefJITTestMain.main()

