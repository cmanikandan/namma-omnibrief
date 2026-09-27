# AGENTS.md — iOS (Namma Omnibrief Lite)

Operating notes for AI agents (and humans) working in `ios/`. Read the root [AGENTS.md](../AGENTS.md)
first — its secrets policy, prompt rules and git conventions apply here unchanged. This file covers
only what is different on iOS.

For the human, step-by-step build and install guide, see [README.md](README.md).

---

## 1. What this is

A native Swift / SwiftUI port of the Android app for iPhone, iOS 17+. Four tabs:

| Tab | Screen | Android counterpart |
|---|---|---|
| Today | `Screens/TodayHeadlinesView.swift` | `HeadlinesScreen.kt` |
| X Drafter | `Screens/ArticleToXView.swift` | `ArticleToXScreen.kt` |
| Archive | `Screens/ArchiveView.swift` | `HistoryScreen.kt` |
| Settings | `Screens/SettingsView.swift` | `SettingsScreen.kt` |

The **Conference Reporter is deliberately not ported.** It has not been exercised on Android either;
do not add it here until it has been verified there.

It lives on the `ios-lite` branch. Do not merge to `main` without the user asking.

---

## 2. Build and test

```bash
./ios/scripts/run_tests.sh          # 14 unit tests, ~2 s. Run before every commit.

xcodebuild -project ios/NammaOmniBriefLite.xcodeproj -scheme NammaOmniBriefLite \
  -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO \
  -derivedDataPath /tmp/omnibrief-ios-derived build

xcodebuild -project ios/NammaOmniBriefLite.xcodeproj -scheme NammaOmniBriefLite \
  -sdk iphoneos -configuration Debug CODE_SIGNING_ALLOWED=NO \
  -derivedDataPath /tmp/omnibrief-ios-derived build
```

If `xcodebuild` reports that the active developer directory is a Command Line Tools instance, set
`DEVELOPER_DIR` to the Xcode app (`/Applications/Xcode.app/Contents/Developer`, or
`Xcode-beta.app`). `run_tests.sh` picks either automatically.

**A change is done when** all three commands succeed **and** the builds have zero Swift warnings.
Check warnings explicitly — do not pipe through `tail` and assume:

```bash
xcodebuild … build 2>&1 | grep -E "\.swift.*warning:|error:|BUILD (SUCCEEDED|FAILED)"
```

The `appintentsmetadataprocessor … Metadata extraction skipped` line is benign.

Real-device install, signing and TestFlight need the user's Apple ID and are done by the user in
Xcode. Do not claim on-device behaviour you have not seen.

---

## 3. Layout and conventions

```
ios/
├── NammaOmniBriefLite.xcodeproj/   # The only build definition
├── Sources/
│   ├── OmniBriefCore/              # Pure logic. Foundation only — no SwiftUI, no UIKit
│   └── NammaOmniBriefLiteApp/      # SwiftUI app, MainViewModel, Theme, Screens/
├── Tests/
│   ├── RunUnitTests.swift          # What run_tests.sh executes
│   └── OmniBriefCoreTests/         # Same tests as XCTest; not yet wired to a target
└── scripts/run_tests.sh
```

Conventions — follow them rather than "improving" them, same philosophy as Android:

- **No third-party dependencies.** `URLSession` + `JSONSerialization`, mirroring OkHttp + `org.json`.
- **No `Package.swift`.** The Xcode project is the single build definition; a second one drifts.
- **One `MainViewModel`** (`@MainActor`, `@Published` properties), mirroring Android's single
  ViewModel. Screens take it as `@ObservedObject`. No navigation library, no DI, no coordinators.
- **`OmniBriefCore` must stay UI-free.** `run_tests.sh` concatenates every file in it and runs them
  with `xcrun swift` on macOS — an `import UIKit` or `import SwiftUI` there breaks the tests.
  Anything that needs `UIImage` (e.g. JPEG downscaling) belongs in `MainViewModel`.
- **Test files must not use `@main`.** `RunUnitTests.swift` is executed as a script; it calls
  `OmniBriefJITTestMain.main()` at top level.
- **Fonts go through `omniScaledFont(size:weight:scale:)`** so the Settings text-size control keeps
  working. Colours come from `OmniTheme`.
- **Bundle ID** in the repo is `com.aistudio.omnibrief.ioslite`, matching the Android scaffold's
  naming. Users change it locally for signing.

### Adding a Swift file

Every new app file must be registered in `project.pbxproj`, or it silently is not compiled. Prefer
asking the user to create it from Xcode. If editing by hand, add all four entries, following the
existing ID pattern (`A1…` build file, `A2…` file reference):

1. `PBXBuildFile` (`A1xxxxxx… /* Foo.swift in Sources */`)
2. `PBXFileReference` with the path relative to `ios/`
3. the child in the right `PBXGroup`
4. the entry in the `PBXSourcesBuildPhase`

Then build for both SDKs. Files in `Sources/OmniBriefCore/` are picked up by `run_tests.sh`
automatically; add their tests to **both** `RunUnitTests.swift` and `OmniBriefCoreTests.swift`.

### Do not commit Xcode's local changes to the project file

Opening the project in Xcode and choosing a signing team writes `DEVELOPMENT_TEAM = …` into
`project.pbxproj`, and Xcode may also rewrite object comments. The team ID is personal. Stage only
the hunks you intend (`git add -p`), or restore the file before committing
(`git restore ios/NammaOmniBriefLite.xcodeproj/project.pbxproj`) if your change did not touch it.

---

## 4. Parity with Android — change both sides together

These were copied deliberately from the Kotlin code. If one side changes, the other must too, and
the reasons in the root AGENTS.md §5 and §5A apply verbatim.

| Behaviour | iOS | Android |
|---|---|---|
| 8-rule grounded article prompt (incl. rule 4 incidental mentions, rule 7 ≤ 260 chars) | `GeminiClient.buildArticlePrompt` | `GeminiApiService.analyzeArticle` |
| Model fallback chain `3.8 → 3.5 → 2.5` | `GeminiClient.fallbackModels` | `GeminiApiService.FALLBACK_MODELS` |
| Selectable models | `AppPreferencesStore.availableModels` | `AppPreferences.AVAILABLE_MODELS` |
| HN interests, single-word query terms, weights 600 / 250, cap 3 | `HackerNewsClient` | `HackerNewsService` |
| For you / Newest (stable sort), persisted by `id` | `HeadlineSort` | `HeadlineSort` |
| Source retargeting (prose + `Source:` line, bare "The", skip `POSTED`) | `SourceRetargeter.retargetSource` + `MainViewModel.retargetDraftsToSource` | `MainViewModel.retargetSource` |
| Detection never overrules a user-chosen source | `sourceIsUserOverride` | `sourceIsUserOverride` |
| Archive batch format (`[Label] ` + `\n\n---\n\n`) | `SourceRetargeter.joinDraftsForArchive` / `splitArchivedDraft` | `saveDraftToRoom` / `splitArchivedDraft` |
| 10-item FIFO archive | `BriefArchiveStore.saveWithRollover` | `insertWithRollover(maxLimit = 10)` |
| Character-limit pre-flight before posting | `MainViewModel.approveAndPostToX` | `MainViewModel.approveAndPostToX` |
| Token refresh before expiry; save rotated refresh token | `XClient.needsRefresh`, `AppPreferencesStore.saveRefreshedTokens` | `XApiService.needsRefresh`, `AppPreferences.saveRefreshedTokens` |
| 403 on media upload translated to a `media.write` message | `XClient.uploadImage` | `XApiService.uploadImage` |
| Images downscaled to 1600 px, orientation normalised | `MainViewModel.downscaledJpegData` | `readUriAsBase64Jpeg` |

Known intentional differences: iOS has pull-to-refresh and no hourly auto-refresh timer; persistence
is a JSON file in `Documents/` plus `UserDefaults` instead of Room plus `SharedPreferences`.

---

## 5. Secrets

The root policy applies in full — the repository is public.

- Nothing hardcoded. `AppPreferencesStore` resolves: value saved in Settings (`UserDefaults`) →
  process environment variable (only set when launched from Xcode with a scheme env var) → empty.
- Test fixtures and UI placeholders use obviously fake values (`FAKE_CLIENT_ID_123`). Test 14 asserts
  every X credential starts empty.
- Keys are in `UserDefaults`, not the Keychain. Acceptable for a personal device; moving to Keychain
  is a known improvement, not a bug to fix silently.
- Run the root AGENTS.md secret grep on the staged diff before every commit, and keep documentation
  free of machine-specific or employer-specific details.

---

## 6. Status and known gaps

- **Verified:** 14/14 unit tests; unsigned Simulator and device builds with zero Swift warnings.
- **Not verified:** anything on a real iPhone — live Gemini and X calls, camera capture, layout at
  every text size. The user is running a multi-week on-device shakedown; the checklist is in
  README.md Part 9.
- No app icon (blocks TestFlight upload only).
- `OmniBriefCoreTests.swift` is not attached to an XCTest target, so ⌘U does nothing.
- No Keychain, no hourly Today refresh, no Conference Reporter.

When the user reports a bug from the phone, fix it, add a test in `OmniBriefCore` if the logic is
testable there, run all three commands in §2, and say plainly which parts could only be verified on
the device.
