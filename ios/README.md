# Namma Omnibrief Lite for iOS

A native Swift / SwiftUI port of the Android app, for iPhone (iOS 17 or newer).

This guide assumes **no prior iOS experience**. If you have built Android apps before, the
[Android → iOS cheat sheet](#android--ios-cheat-sheet) maps the concepts you already know.

> [!NOTE]
> Working on the code with an AI agent? [AGENTS.md §13](../AGENTS.md) holds the terse version of
> the build quirks on this machine. This file is the long, human version.

---

## Contents

- [What is in the Lite edition](#what-is-in-the-lite-edition)
- [Android → iOS cheat sheet](#android--ios-cheat-sheet)
- [The four ways to get the app onto your iPhone](#the-four-ways-to-get-the-app-onto-your-iphone)
- [Which one should you pick?](#which-one-should-you-pick)
- [Part 1 — One-time Mac setup](#part-1--one-time-mac-setup)
- [Part 2 — Run the unit tests](#part-2--run-the-unit-tests)
- [Part 3 — Run it in the iOS Simulator](#part-3--run-it-in-the-ios-simulator)
- [Part 4 — Install on your iPhone with a free Apple ID](#part-4--install-on-your-iphone-with-a-free-apple-id)
- [Part 5 — Wireless installs (no cable)](#part-5--wireless-installs-no-cable)
- [Part 6 — The paid Apple Developer Program](#part-6--the-paid-apple-developer-program)
- [Part 7 — TestFlight](#part-7--testflight)
- [Part 8 — Other distribution options](#part-8--other-distribution-options)
- [Part 9 — A few-weeks shakedown plan](#part-9--a-few-weeks-shakedown-plan)
- [Part 10 — Debugging on the phone](#part-10--debugging-on-the-phone)
- [Project layout](#project-layout)
- [Troubleshooting](#troubleshooting)
- [Known gaps](#known-gaps)

---

## What is in the Lite edition

Four tabs, mirroring the parts of the Android app used day to day:

| Tab | What it does |
|---|---|
| **Today** | Hacker News top 10 ranked by your interests (GenAI, OpenAI, Gemini, Google, Anthropic, India Tech). Public, keyless API — works on first launch. **For you / Newest** sort, story ages, pull-to-refresh, and **Draft** to send a headline to the drafter. |
| **X Drafter** | Camera or Photos (up to 10 images), paste text, or **Sample FT** for a no-key test. Gemini drafts a grounded post using the same 8 rules as Android. Change the **Publication Attribution** menu and the draft text, `Source:` line and Archive entry all follow. Publish via OAuth 2.0 (with the photo attached) or **Open in X App**. |
| **Archive** | The last 10 briefs, stored on the phone. Reopen in the drafter, share (Mail, Drive, Notes, AirDrop), delete. |
| **Settings** | Text size (5 steps), Gemini model, X credentials, and a **Bulk Import** box that accepts pasted `.env` lines. |

**Not included:** the Conference Reporter (audio recording + slides). It has not been exercised on
Android yet either, so porting it now would mean porting something unverified.

---

## Android → iOS cheat sheet

| Android | iOS equivalent | Notes |
|---|---|---|
| Android Studio | **Xcode** | Here it is installed as `/Applications/Xcode-beta.app` |
| Gradle (`build.gradle.kts`) | **Xcode project** (`NammaOmniBriefLite.xcodeproj`) | Build settings live in the project file; edit them in Xcode's UI, not by hand |
| `./gradlew assembleDebug` | `xcodebuild … build` | See Part 3 |
| Emulator (AVD) | **iOS Simulator** | Bundled with Xcode; runtimes downloaded separately |
| USB debugging / Developer options | **Developer Mode** | Settings → Privacy & Security → Developer Mode |
| `adb install` | Xcode **Run (⌘R)** | Xcode installs and launches in one step |
| Debug keystore | **Signing certificate + provisioning profile** | Xcode creates both automatically ("Automatically manage signing") |
| `applicationId` | **Bundle Identifier** | Must be globally unique per Apple account |
| `versionName` / `versionCode` | **Version** / **Build** | Target → General → Identity. Build must go up for every TestFlight upload |
| `AndroidManifest.xml` permissions | **`Info.plist`** usage strings | `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription` |
| Logcat | Xcode **debug console** / **Console.app** | See Part 10 |
| Play Console internal testing | **TestFlight** | Needs the paid program |
| Sideloading an APK | *Not really possible* | iOS only runs signed apps — hence Parts 4–8 |

The single biggest difference: **iOS will not run an app that is not signed by Apple-issued
credentials.** Every installation option below is really a choice of *which* signature and *how long
it lasts*.

---

## The four ways to get the app onto your iPhone

| Option | Cost | App keeps working for | Needs the Mac each time? | Best for |
|---|---|---|---|---|
| **A. Simulator** | Free | n/a (runs on the Mac) | Yes | Quick UI checks. No real camera |
| **B. Xcode + free Apple ID** ("Personal Team") | Free | **7 days**, then must be reinstalled from Xcode | Yes, weekly | Trying it out, first week or two |
| **C. Xcode + paid Developer Program** | US$99/year (or local equivalent) | **1 year** | Only to install updates | Living with the app without TestFlight |
| **D. TestFlight** | Same paid program | **90 days per build**, updates arrive over the air | No — upload once, phone pulls it | A multi-week shakedown, sharing with a few others |

> [!IMPORTANT]
> The 7-day limit on option B is the part people trip over. On day 8 the app icon is still there but
> tapping it does nothing (or shows *"no longer available"*). **Your data is not lost** — plug in,
> press ⌘R in Xcode, and it is back with the archive and settings intact. Deleting the app from the
> home screen, however, *does* delete its data.

Free Personal Team limits worth knowing: at most **3 sideloaded apps per device**, and at most
**10 new bundle IDs per 7 days** per Apple ID.

---

## Which one should you pick?

Your goal is to use it on your own iPhone for a few weeks and fix what breaks. Recommended path:

1. **Week 1 — Option B (free).** Costs nothing, and you will be rebuilding often anyway while fixing
   early bugs, so the 7-day expiry rarely bites. Follow [Part 4](#part-4--install-on-your-iphone-with-a-free-apple-id).
2. **If re-installing every week gets annoying, or you want to stop depending on the Mac**, enrol in
   the paid program. Then either keep using Xcode (option C, one-year installs) or move to
   **TestFlight** (option D), which is the nicest way to live with an app: upload a build from the
   Mac, and the TestFlight app on the phone offers the update, keeps previous builds, and lets you
   send screenshot feedback to yourself.

You do not need to decide now. Nothing in the code changes between the options — only the signing
team in Xcode.

---

## Part 1 — One-time Mac setup

### 1.1 Xcode

Xcode is already installed at `/Applications/Xcode-beta.app` (Xcode 27, iOS 27 SDK). Open it once
from Finder so it can finish installing its components, and accept the licence.

The command-line tools on this Mac point at a stripped-down toolchain, so terminal commands need to
be told where Xcode is. Either export this in every terminal session:

```bash
export DEVELOPER_DIR="/Applications/Xcode-beta.app/Contents/Developer"
```

or switch the system default once (needs your Mac password):

```bash
sudo xcode-select -s /Applications/Xcode-beta.app/Contents/Developer
```

Check it worked:

```bash
xcodebuild -version        # → Xcode 27.0
```

### 1.2 Simulator runtime (only needed for Part 3)

This Mac currently has **no iOS Simulator runtimes installed**, so the Simulator will show no
devices. Download one (several GB) either from **Xcode → Settings → Components → iOS** or:

```bash
xcodebuild -downloadPlatform iOS
xcrun simctl list devices available | grep iPhone    # should now list iPhones
```

### 1.3 Your Apple ID in Xcode

**Xcode → Settings… (⌘,) → Accounts → ＋ → Apple ID** and sign in. Any Apple ID works; the one on
your iPhone is simplest.

> [!WARNING]
> This is a Google-managed MacBook. Signing a personal Apple ID into Xcode for a personal project is
> common, but check it is acceptable under your device policy. Keychain items created for the
> signing certificate live in your login keychain.

### 1.4 Corporate MacBook: do not use `swift test` here

Santa (the binary allow-listing tool on corporate Macs) blocks the unsigned helper binary that Swift
Package Manager compiles from a `Package.swift`. You will see a popup naming `ios-manifest`. That is
why this project has **no `Package.swift`** and runs tests with a script instead (Part 2). If you ever
see that popup, click **Dismiss** — nothing is harmed, the build simply did not run.

---

## Part 2 — Run the unit tests

From the repository root:

```bash
./ios/scripts/run_tests.sh
```

Takes about two seconds and should end with `Result: 14 passed, 0 failed`. It checks the logic that
does not need a screen:

- source retargeting (the "change the publication and every mention follows" behaviour), 7 cases
  copied from the Android `SourceRetargetTest`;
- Today feed sorting, story ages and domain parsing;
- Hacker News ranking and the 3-per-interest diversity cap;
- splitting archived batches back into drafts (`[Image 1]` labels stripped);
- the 10-item archive rollover and in-place source rewrite;
- X token expiry logic, the `.env` import parser, and **no hardcoded secrets**.

How it works: the script feeds the source files into `xcrun swift`, Apple's signed interpreter, so no
unsigned binary is ever produced and Santa stays quiet.

Run it before every commit. It is the iOS counterpart of `./gradlew :app:testDebugUnitTest`.

---

## Part 3 — Run it in the iOS Simulator

### From Xcode (easiest)

```bash
open -a "/Applications/Xcode-beta.app" ios/NammaOmniBriefLite.xcodeproj
```

1. In the toolbar at the top, click the device name next to **NammaOmniBriefLite** and choose an
   iPhone under *iOS Simulators* (e.g. *iPhone 17*). If the list is empty, do step 1.2 above.
2. Press **⌘R** (or the ▶ button). The Simulator window opens and the app launches.
3. Stop with **⌘.** (or ■).

The simulator has **no camera**. Use **Photos** instead: drag any image file from Finder onto the
Simulator window to add it to its photo library, or just use **Sample FT**.

Paste your keys: copy a line on the Mac, then in the Simulator use **Edit → Paste** (or ⌘V) into the
Bulk Import box.

### From the terminal (build only — handy as a pre-commit check)

```bash
export DEVELOPER_DIR="/Applications/Xcode-beta.app/Contents/Developer"

# Simulator build, unsigned
xcodebuild -project ios/NammaOmniBriefLite.xcodeproj -scheme NammaOmniBriefLite \
  -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO \
  -derivedDataPath /tmp/omnibrief-ios-derived build

# Device (arm64) build, unsigned — proves it compiles for a real iPhone
xcodebuild -project ios/NammaOmniBriefLite.xcodeproj -scheme NammaOmniBriefLite \
  -sdk iphoneos -configuration Debug CODE_SIGNING_ALLOWED=NO \
  -derivedDataPath /tmp/omnibrief-ios-derived build
```

Both should end with `** BUILD SUCCEEDED **`. Tip: add `| tail -n 5` to hide the long log, or
`-quiet` to show only warnings and errors.

---

## Part 4 — Install on your iPhone with a free Apple ID

About 15 minutes the first time, one minute after that.

### 4.1 Set up signing (once)

1. Open the project in Xcode (command in Part 3).
2. In the left sidebar (Project navigator, ⌘1) click the blue **NammaOmniBriefLite** icon at the top.
3. Under **TARGETS** select **NammaOmniBriefLite**, then the **Signing & Capabilities** tab.
4. Tick **Automatically manage signing**.
5. **Team** → choose **`<Your Name> (Personal Team)`**.
6. **Bundle Identifier** → change `com.aistudio.omnibrief.ioslite` to something only you would use,
   e.g. `com.cmanikandan.omnibrief.ioslite`. The default is almost certainly taken, and Xcode will
   show *"Failed to register bundle identifier"* until you change it.

Xcode now shows a spinner then *"Xcode Managed Profile"* with no red errors. Done.

> [!TIP]
> Changing the Bundle Identifier edits `project.pbxproj`. That change is harmless to commit, but if
> you would rather keep your personal ID out of the public repo, don't stage that file:
> `git restore --staged ios/NammaOmniBriefLite.xcodeproj/project.pbxproj`.

### 4.2 Prepare the iPhone (once)

1. Connect the iPhone to the Mac with a cable. Unlock it.
2. On the phone tap **Trust** on *"Trust This Computer?"* and enter your passcode.
3. Back in Xcode, choose your iPhone in the device menu in the toolbar. Xcode may say *"Preparing
   iPhone for development"* for a few minutes the first time — wait for it.
4. On the phone: **Settings → Privacy & Security → Developer Mode** (at the very bottom) → **On** →
   **Restart**. After the restart, tap **Turn On** and enter the passcode.
   *The Developer Mode switch only appears after the phone has been connected to Xcode once — so do
   step 3 first.*

### 4.3 Build, install, trust

1. Press **⌘R**. Xcode builds, installs, and tries to launch.
2. The first time, launching fails with *"Untrusted Developer"* (or Xcode reports it could not
   launch). That is expected. On the phone:
   **Settings → General → VPN & Device Management → (your Apple ID under *Developer App*) → Trust**.
3. Press **⌘R** again. The app opens. You can unplug the cable — it stays installed.

The first time you tap **Camera**, iOS asks for camera permission; that prompt uses the text in
`Info.plist`.

### 4.4 Add your keys

Easiest, using Universal Clipboard (same Apple ID on Mac and iPhone, Bluetooth + Wi-Fi on):

```bash
pbcopy < .env        # on the Mac, from the repo root
```

On the phone: **Settings tab → Bulk Import → long-press the box → Paste → Import .env Keys**.
Recognised keys are `GEMINI_API_KEY`, `X_CLIENT_ID`, `X_CLIENT_SECRET`, `X_ACCESS_TOKEN`,
`X_REFRESH_TOKEN`. Keys are stored only on the phone.

> [!CAUTION]
> X refresh tokens are **single-use and rotate on every refresh** (see the main README). If the
> Android app and the iPhone share one refresh token, whichever refreshes first invalidates the
> other. For the shakedown, mint a separate token pair for the iPhone with
> `tools/x_oauth_setup.py`, or accept that you will re-paste tokens after switching devices.

### 4.5 Every 7 days

When the app stops opening: connect the phone (or use wireless, Part 5), open the project, **⌘R**.
Settings and archive survive.

---

## Part 5 — Wireless installs (no cable)

After one successful cable install:

1. **Window → Devices and Simulators (⇧⌘2)**, select the iPhone, tick **Connect via network**
   (on recent Xcode versions this is automatic and the option may be absent).
2. Unplug. As long as the Mac and iPhone are on the same Wi-Fi, the phone stays in Xcode's device
   menu with a globe icon, and ⌘R installs over the air.

Corporate and guest Wi-Fi often block device-to-device traffic. If the phone disappears, use a home
network, a phone hotspot, or the cable.

---

## Part 6 — The paid Apple Developer Program

Needed for one-year installs (option C) and for TestFlight (option D).

1. Enrol at [developer.apple.com/programs/enroll](https://developer.apple.com/programs/enroll/) as an
   **Individual**, with the same Apple ID. Enrolment is usually approved within a day or two; Apple
   may ask for identity verification.
2. Once approved, in Xcode **Settings → Accounts** the Apple ID shows a second team (your name,
   *without* "Personal Team"). In **Signing & Capabilities** switch **Team** to it.
3. Press ⌘R. Apps installed this way keep running for a year, and the 3-app limit goes away.

---

## Part 7 — TestFlight

TestFlight is Apple's beta-testing service. You upload a build from Xcode to **App Store Connect**;
the free **TestFlight** app on your iPhone installs it and offers every later build as an update.
For you testing your own app ("internal testing") there is **no App Review** and builds are usually
available 10–30 minutes after upload. Each build expires after **90 days**.

### 7.1 Before your first upload — add an app icon (required)

App Store Connect rejects uploads that have no 1024×1024 icon, and **this project does not have one
yet** (Xcode shows a blank icon locally, which is fine for options A–C).

1. Make a **1024×1024 PNG with no transparency** (square; iOS rounds the corners itself).
2. In Xcode open `Sources/NammaOmniBriefLiteApp/Assets.xcassets` → **AppIcon** and drag the PNG onto
   the single *Any Appearance* slot.

Quick way to strip transparency from an existing PNG on the Mac:

```bash
sips -s format jpeg icon.png --out /tmp/icon.jpg && sips -s format png /tmp/icon.jpg --out icon-1024.png
sips -z 1024 1024 icon-1024.png
```

### 7.2 Register the app (once)

1. [App Store Connect](https://appstoreconnect.apple.com) → **Apps → ＋ → New App**.
2. Platform **iOS**; Name e.g. *Namma Omnibrief Lite* (must be unique on the App Store — add a word
   if it's taken; this is never shown publicly for internal testing); Primary language English;
   **Bundle ID** → pick the one Xcode registered in Part 4/6 (it appears in the list once Xcode has
   signed with your paid team at least once); SKU → anything, e.g. `omnibrief-ios-lite`;
   User Access **Full Access**.
3. **Create**.

### 7.3 Upload a build

1. In Xcode, set the device menu to **Any iOS Device (arm64)** (not a simulator).
2. Target → **General → Identity**: set **Version** (e.g. `1.0`) and **Build** (e.g. `1`).
   **Build must be higher for every upload** — `2`, `3`, … Version can stay the same across
   builds.
3. **Product → Archive**. When it finishes, the **Organizer** window opens with the archive
   selected.
4. **Distribute App → TestFlight Internal Only** (or *App Store Connect* on older Xcode) →
   **Distribute**. Leave the defaults (automatic signing, upload symbols).
5. Wait for *"Upload Successful"*. App Store Connect then *processes* the build — you will get an
   email when it is ready.

The export-compliance question ("does your app use encryption?") is pre-answered: `Info.plist` sets
`ITSAppUsesNonExemptEncryption = NO`, which is correct because the app only uses standard HTTPS.

### 7.4 Install on the iPhone

1. App Store Connect → your app → **TestFlight** tab → **Internal Testing → ＋** create a group
   (e.g. *Me*), add yourself as a tester, add the build to the group.
2. On the iPhone install **TestFlight** from the App Store and sign in with the same Apple ID. Accept
   the email invitation if one arrives.
3. Tap **Install**. The app appears on the home screen with an orange dot next to its name.

To send yourself a bug report: take a screenshot while in the app → tap **Share Beta Feedback**. It
shows up under **TestFlight → Feedback** in App Store Connect, with device and OS details attached.
Crashes are collected there too.

To ship a fix: bump **Build**, Archive, Distribute. The phone offers the update in TestFlight
(turn on **Automatic Updates** in the TestFlight app to skip even that).

### 7.5 Uploading from the terminal (optional)

Once the GUI flow has worked once, the same thing can be scripted:

```bash
export DEVELOPER_DIR="/Applications/Xcode-beta.app/Contents/Developer"

xcodebuild -project ios/NammaOmniBriefLite.xcodeproj -scheme NammaOmniBriefLite \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath /tmp/NammaOmniBriefLite.xcarchive -allowProvisioningUpdates \
  CURRENT_PROJECT_VERSION=2 archive

cat > /tmp/ExportOptions.plist <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>method</key><string>app-store-connect</string>
  <key>destination</key><string>upload</string>
  <key>signingStyle</key><string>automatic</string>
</dict></plist>
EOF

xcodebuild -exportArchive -archivePath /tmp/NammaOmniBriefLite.xcarchive \
  -exportOptionsPlist /tmp/ExportOptions.plist -exportPath /tmp/omnibrief-export \
  -allowProvisioningUpdates
```

`CURRENT_PROJECT_VERSION=2` overrides the build number for that archive — increase it each time.

---

## Part 8 — Other distribution options

| Option | What it is | Verdict for this app |
|---|---|---|
| **Ad Hoc** | Paid program. Register the iPhone's UDID, export an `.ipa` signed for that device list (Organizer → *Distribute App → Release Testing*), install via Finder / Apple Configurator. Valid 1 year | Works, but TestFlight does the same with less effort |
| **External TestFlight** | Up to 10,000 testers by email or public link | Needs a one-off Beta App Review. Only if you want others to try it |
| **App Store** | Public release | Out of scope — needs privacy policy, screenshots, review, and your personal keys are per-user anyway |
| **Mac "Designed for iPad"** | Apple-silicon Macs can run iPhone apps. Pick **My Mac (Designed for iPad)** in Xcode's device menu | Handy for quick checks without the Simulator; no camera flow |
| **AltStore / SideStore** | Community tools that re-sign free Personal Team apps every 7 days automatically | Not recommended on a corporate Mac; same limits as option B |
| **Enterprise program** | In-house distribution for organisations | Not applicable to a personal app |

---

## Part 9 — A few-weeks shakedown plan

A suggested routine for "ironing out the chinks".

**Day 1 — smoke test** (tick each on the phone):

| # | Flow | Expected |
|---|---|---|
| 1 | Launch with no keys | Today shows 10 stories within a few seconds |
| 2 | Toggle **For you / Newest**, pull down to refresh | Order changes; "Updated" time moves |
| 3 | Tap a story; tap **Draft** on another | Safari opens; drafter pre-filled with the headline |
| 4 | Settings → Bulk Import your `.env` | "Imported N key(s)" |
| 5 | X Drafter → **Sample FT** | One draft, `Main topic analysed:` shown, entry in Archive |
| 6 | Change Publication Attribution to *The Economic Times* | Draft prose **and** `Source:` line change; Archive entry changes in place (still 1/10) |
| 7 | **Camera** → photograph a real article → Analyse | Draft names the dominant story, ignores side columns |
| 8 | Add 3 photos at once | 3 separate drafts |
| 9 | Standard (non-Premium) mode: turn off X Premium, analyse | Counter shows `/280`; over-limit draft blocks **Approve & Post** |
| 10 | **Open in X App** | X app / Safari opens with the text pre-filled |
| 11 | **Approve & Post All** (when ready to go live) | Tweet posted, with photo if *Attach source photo* is on |
| 12 | Settings → each **Text Size** step | Whole app rescales instantly |
| 13 | Rotate the phone; scroll to the bottom of every tab | Nothing clipped behind the tab bar |

**Daily use:** use the app for real. Whenever something is off, take a screenshot (TestFlight
feedback if on option D) and jot down *what you did, what you expected, what happened*.

**Weekly:** fix the batch of issues on the Mac, run `./ios/scripts/run_tests.sh`, reinstall (⌘R or a
new TestFlight build). On option B this also resets the 7-day clock.

Things most likely to need attention, from reading the code:

- no Keychain yet — keys are stored in the app's `UserDefaults` (fine for a personal device, but
  Keychain is the iOS-proper place);
- the ViewModel has no hourly auto-refresh timer (Android has one) — Today refreshes on launch, on
  return to the app after 15 minutes, and on pull-to-refresh;
- no automated UI tests; the checklist above is the UI test.

---

## Part 10 — Debugging on the phone

- **Xcode console.** When launched with ⌘R, anything the app prints appears in Xcode's bottom panel
  (⇧⌘Y to show it). Errors from Gemini and X are surfaced in the UI as red text as well.
- **Breakpoints.** Click a line number in Xcode to set one; the app pauses there on the phone and you
  can inspect variables. Same idea as Android Studio.
- **Console.app** on the Mac shows the phone's system log live, even when the app was not launched
  from Xcode. Select the iPhone in the sidebar and filter by `NammaOmniBriefLite`.
- **Crash logs.** Xcode → **Window → Devices and Simulators → View Device Logs**, or on the phone
  **Settings → Privacy & Security → Analytics & Improvements → Analytics Data**. TestFlight builds
  also report crashes to App Store Connect automatically.
- **View hierarchy.** While running, **Debug → View Debugging → Capture View Hierarchy** gives a 3D
  exploded view of the screen — the iOS equivalent of Layout Inspector.

---

## Project layout

```
ios/
├── NammaOmniBriefLite.xcodeproj      # Open this in Xcode
├── README.md                          # This file
├── scripts/run_tests.sh               # Santa-safe unit test runner
├── Sources/
│   ├── OmniBriefCore/                 # Pure logic, no UI — what the tests cover
│   │   ├── Models.swift               # HeadlineItem, HeadlineSort, drafts, BriefItem
│   │   ├── AppPreferencesStore.swift  # Settings + keys (UserDefaults), .env import
│   │   ├── SourceRetargeter.swift     # Publication rewrite + archive split
│   │   ├── BriefArchiveStore.swift    # 10-item FIFO archive (JSON in Documents/)
│   │   ├── HackerNewsClient.swift     # Algolia fetch + interest ranking
│   │   ├── GeminiClient.swift         # 8-rule prompt, model fallback chain
│   │   └── XClient.swift              # OAuth refresh, media upload, post, web intent
│   └── NammaOmniBriefLiteApp/         # SwiftUI app
│       ├── NammaOmniBriefLiteApp.swift  # @main, tab bar
│       ├── MainViewModel.swift        # All app state (one ViewModel, like Android)
│       ├── Theme.swift                # Warm Bengaluru palette, scaled fonts
│       ├── Screens/                   # Today, ArticleToX, Archive, Settings, CameraPicker
│       ├── Assets.xcassets            # App icon + accent colour
│       └── Info.plist                 # Camera/Photos permission text, versions
└── Tests/
    ├── RunUnitTests.swift             # The 14 tests run by run_tests.sh
    └── OmniBriefCoreTests/            # Same tests in XCTest form (see Known gaps)
```

**Adding a new Swift file:** create it from Xcode (**File → New → File from Template → Swift File**)
so it is added to the project and target automatically. Files created outside Xcode are not
compiled until you drag them into the Project navigator and tick the *NammaOmniBriefLite* target.
New files under `OmniBriefCore/` are picked up by `run_tests.sh` automatically.

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `xcode-select: error: tool 'xcodebuild' requires Xcode` | `export DEVELOPER_DIR="/Applications/Xcode-beta.app/Contents/Developer"` (Part 1.1) |
| Santa popup naming `ios-manifest` | Something ran `swift build`/`swift test`. Dismiss it and use `./ios/scripts/run_tests.sh` |
| Simulator list is empty | No runtime installed — Part 1.2 |
| *Failed to register bundle identifier* / *No profiles for …* | Change the Bundle Identifier to something unique (Part 4.1) |
| *Signing for "NammaOmniBriefLite" requires a development team* | Pick a Team in Signing & Capabilities |
| iPhone not in the device menu | Unlock it, re-plug, accept *Trust This Computer*. Check **Window → Devices and Simulators** |
| *Developer Mode disabled* | Settings → Privacy & Security → Developer Mode → On, restart (Part 4.2) |
| *Untrusted Developer* on launch | Settings → General → VPN & Device Management → Trust (Part 4.3) |
| App icon there but won't open after a week | Free-profile 7-day expiry. ⌘R again; data is kept |
| *Your maximum App ID limit has been reached* | Free Personal Team allows 10 new bundle IDs per 7 days. Reuse the same ID |
| Upload fails: *Missing required icon file* | Add a 1024×1024 PNG without transparency (Part 7.1) |
| Upload fails: *bundle version must be higher* | Increase **Build** (Part 7.3) |
| Build not in TestFlight yet | Still processing; wait for the email (usually under 30 min) |
| Gemini error in the drafter | Check the key in Settings; the red text shows the server's actual message |
| X upload says `media.write` is missing | Re-authorise with `tools/x_oauth_setup.py`, or turn off *Attach source photo* |

---

## Known gaps

Honest status as of the first commit on the `ios-lite` branch:

- **Verified:** 14/14 unit tests; unsigned Simulator and device builds succeed with no warnings.
- **Not yet verified:** running on a real iPhone, live Gemini and X calls from iOS, camera capture.
  That is what the shakedown is for.
- `Tests/OmniBriefCoreTests/OmniBriefCoreTests.swift` is an XCTest version of the same tests, kept
  for the day this runs on a machine without Santa. It is **not** wired into the Xcode project, so
  **⌘U does nothing yet**. Adding a Unit Test target in Xcode and including that file would enable it.
- No app icon (blocks TestFlight only — Part 7.1).
- Keys in `UserDefaults` rather than Keychain; no hourly Today refresh; no Conference Reporter.
