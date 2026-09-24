# Namma Omnibrief Lite for iOS (`ios/`)

This directory contains **Namma Omnibrief Lite**, the native Swift / SwiftUI iOS counterpart to the Android app.

## Why "Lite"?

The iOS Lite edition focuses on the **four daily-driver workflows** used every day on phone hardware, omitting the Conference Scribe live audio/slide recording stack so the app remains zero-dependency, fast to compile, and simple to provision on a personal Apple ID:

1. **Today (`TodayHeadlinesView.swift`)** — Keyless Algolia Hacker News Top 10 ranked by your interests (`GenAI`, `OpenAI`, `Gemini`, `Google`, `Anthropic`, `India Tech`, `AI`), with **For you / Newest** sorting, live relative story ages (`42m ago`), and 1-tap **Draft** piping into the X Drafter.
2. **X Drafter (`ArticleToXView.swift`)** — Shoot up to 10 newspaper photos with the iPhone camera (`CameraPicker`), pick from the Photo Library (`PhotosPicker`), paste text, or load a 1-tap **Sample FT** article. Calls Gemini (`gemini-3.8-flash` → `3.5` → `2.5` fallback chain) using the **8 non-negotiable grounding rules**, displays `Main topic analysed`, supports **Source Override with full prose + `Source:` + Archive retargeting**, and publishes via **X OAuth 2.0 User Context** (with automatic token refresh and `media.write` photo upload) or **Open in X App** (`x.com/intent/tweet`).
3. **Archive (`ArchiveView.swift`)** — Local on-device JSON store with strict **10-item FIFO rollover**, 1-tap reopen into the drafter (stripping `[Image 1]` batch tags via `SourceRetargeter.splitArchivedDraft`), and native iOS `ShareLink` (Mail, Google Drive, Notes, AirDrop, Copy).
4. **Settings (`SettingsView.swift`)** — Live **5-step Text Size scaling** (`Compact 0.90×` to `Extra Large 1.50×`, default `Comfortable 1.15×`), Gemini model selector, X OAuth 2.0 fields, and **Bulk Import (`.env` paste)** via Universal Clipboard from your MacBook.

---

## 1. Building & Running Unit Tests from the Command Line

> [!IMPORTANT]
> **Google Corporate MacBook (Santa) Note:**
> Do **not** add a `Package.swift` or run `swift test`. Swift Package Manager compiles `Package.swift` into an unsigned temporary host executable (`ios-manifest` in `/private/var/folders/...`), which triggers a **Santa** block popup on corporate macOS machines.
>
> Instead, this project uses a native Xcode project (`NammaOmniBriefLite.xcodeproj`) and a **Santa-safe JIT unit test script** (`./ios/scripts/run_tests.sh`) that runs entirely inside Apple's signed `xcrun swift` binary.

### Point to Xcode

If `/Applications/Xcode-beta.app` is installed rather than `/Applications/Xcode.app`, export `DEVELOPER_DIR` first:

```bash
export DEVELOPER_DIR="/Applications/Xcode-beta.app/Contents/Developer"
# (Or "/Applications/Xcode.app/Contents/Developer" on standard Xcode)
```

### Run the 14 Automated Unit Tests (< 2 seconds, Santa-safe)

```bash
./ios/scripts/run_tests.sh
```

This verifies:
- **Source Retargeting (`SourceRetargeter`)**: Mid-sentence prose + `Source:` attribution line rewriting, bare form without leading `"The"`, unknown old source, case-insensitivity, and preserving mid-sentence `"Source:"` literals.
- **Today Feed Sorting & Ages (`HeadlineSort`, `HeadlineItem`)**: `For you` relevance preservation, `Newest` descending sort with stable tie-breaking, relative age formatting (`just now`, `42m ago`), and bare domain parsing.
- **Hacker News Ranking (`HackerNewsClient`)**: `points + 600 × interestMatches + 250 × isFrontPage` scoring and `maxPerInterest = 3` diversity cap with backfill.
- **Archive Batch Splitting (`splitArchivedDraft`)**: Stripping `[Image 1]` / `[Pasted text]` markers while keeping legitimate inner brackets intact.
- **FIFO Rollover & In-Place Retargeting (`BriefArchiveStore`)**: Enforcing the 10-item cap and updating archived rows when the user changes the publication source chip.
- **X Token Expiry & Secrets Policy (`XClient`, `AppPreferencesStore`)**: 60-second pre-refresh window, hand-pasted token handling, deterministic `saveRefreshedTokens` ordering, `.env` bulk import, and zero hardcoded secrets.

### Build the iOS App from Terminal (`xcodebuild`)

```bash
# Build for iOS Simulator (unsigned CI verification)
DEVELOPER_DIR="/Applications/Xcode-beta.app/Contents/Developer" \
  xcodebuild -project ios/NammaOmniBriefLite.xcodeproj \
  -scheme NammaOmniBriefLite \
  -sdk iphonesimulator \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO \
  -derivedDataPath /tmp/omnibrief-ios-derived \
  build

# Build for physical iPhone arm64 (unsigned compile verification)
DEVELOPER_DIR="/Applications/Xcode-beta.app/Contents/Developer" \
  xcodebuild -project ios/NammaOmniBriefLite.xcodeproj \
  -scheme NammaOmniBriefLite \
  -sdk iphoneos \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO \
  -derivedDataPath /tmp/omnibrief-ios-derived \
  build
```

---

## 2. How to Install & Test on a Real iPhone (Step-by-Step)

Because Namma Omnibrief uses the physical camera to photograph newspaper columns, running on a real iPhone gives the best experience. You do **not** need a paid $99/year Apple Developer account — any personal Apple ID provides free on-device signing.

### Step 1 — Open the Project in Xcode

From the repository root on your MacBook:

```bash
open -a "/Applications/Xcode-beta.app" ios/NammaOmniBriefLite.xcodeproj
# Or if using stable Xcode:
# open -a Xcode ios/NammaOmniBriefLite.xcodeproj
```

### Step 2 — Add Your Apple ID & Select a Personal Team

1. In Xcode, open **Xcode → Settings…** (`⌘,`) and click the **Accounts** tab.
2. Click **＋** at the bottom-left → **Apple ID** → sign in with your personal Apple ID.
3. In the left Project Navigator, click the top-level **`NammaOmniBriefLite`** project icon, select the **`NammaOmniBriefLite`** target, and open the **Signing & Capabilities** tab.
4. Ensure **Automatically manage signing** is checked.
5. Set **Team** to **`<Your Name> (Personal Team)`**.
6. Set **Bundle Identifier** to a unique string tied to you, for example:
   ```text
   com.cmanikandan.omnibrief.ioslite
   ```
   *(Free personal provisioning profiles require a globally unique Bundle Identifier so Apple's provisioning server can issue a device certificate for your iPhone).*

### Step 3 — Enable Developer Mode on Your iPhone (One-Time Setup on iOS 16 / 17 / 18+)

1. Connect your iPhone to your MacBook via USB-C / Lightning cable, and tap **Trust This Computer** on the iPhone when prompted (enter your iPhone passcode).
2. On your iPhone, open **Settings → Privacy & Security**, scroll to the very bottom, and tap **Developer Mode**.
3. Toggle **Developer Mode** ON, tap **Restart**, and after the phone reboots, tap **Turn On** in the modal and enter your passcode.

### Step 4 — Build & Run (`⌘R`) + Trust Your Developer Certificate

1. At the top of the Xcode window (the Run Destination bar next to `NammaOmniBriefLite`), select **your physical iPhone** from the device dropdown.
2. Press **Run (`⌘R`)** (or click the ▶ Play button).
3. On the very first install with a free Personal Team, iOS will display **"Untrusted Developer"** when Xcode tries to launch the app. Fix this in 10 seconds on the iPhone:
   - Open **Settings → General → VPN & Device Management**.
   - Under **Developer App**, tap your Apple ID email address.
   - Tap **Trust "<Your Apple ID>"** → **Trust**.
4. Press **Run (`⌘R`)** in Xcode once more — **Namma Omnibrief Lite** will launch on your iPhone!

### Step 5 — Transfer Your `.env` Keys via Universal Clipboard & Test Live

1. On your MacBook, copy your `.env` contents (`GEMINI_API_KEY=...`, `X_CLIENT_ID=...`, etc.) with `⌘C`:
   ```bash
   pbcopy < .env
   ```
2. On your iPhone (signed into the same iCloud account with Handoff / Universal Clipboard on), open **Namma Omnibrief → Settings**, scroll down to **Bulk Import (`.env` Paste)**, tap **Paste**, and tap **Import `.env` Keys**.
3. **End-to-End Verification Checklist on iPhone**:
   - **Today Tab**: Verify the 10 interest-ranked Hacker News stories load immediately (even before importing keys), toggle **For you / Newest**, and tap **Draft** on any story.
   - **X Drafter — Sample FT & Source Retargeting**: Tap **Sample FT**, then change the **Publication Attribution** menu from `Financial Times` to `The Economic Times`. Verify that both the mid-sentence prose (`reported by The Economic Times:`) and `Source: The Economic Times` update immediately in the draft AND in the **Archive** tab (`1/10`).
   - **X Drafter — Camera Grounding**: Tap **Camera**, photograph a real newspaper article, and tap **Analyse & Draft Grounded X Post**. Verify `Main topic analysed` names the dominant story and ignores peripheral columns.
   - **Publishing to X**: Tap **Approve & Post All via OAuth 2.0** (publishes with the photo attached) or **Open in X App** (opens the native iOS X app / Safari composer pre-filled with the draft).
