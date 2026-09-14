# Namma Omnibrief

A personal Android app for turning what you read and what you attend into publishable output.

Point your camera at a newspaper article, a magazine spread, or a screenshot and Gemini drafts a
ready-to-post summary for X — grounded strictly in what is actually in the image, with the correct
source attribution. Or record a conference session and get a structured field report you can mail to
yourself in seconds.

Built with Jetpack Compose, a single-Activity architecture, and a light, high-contrast UI with a
Bengaluru identity.

> Working on this codebase (human or AI)? Read [AGENTS.md](AGENTS.md) first — it documents the build
> prerequisites, the secrets policy and several non-obvious constraints.

---

## Contents

**New here?** Read [Getting started with Android Studio](#getting-started-with-android-studio), then
[Adding your keys](#adding-your-keys). That is everything you need to run the app.

- [Features](#features)
- [Getting started with Android Studio](#getting-started-with-android-studio)
- [Building from the command line](#building-from-the-command-line)
- [Adding your keys](#adding-your-keys)
- [Posting to X: which credentials do you need?](#posting-to-x-which-credentials-do-you-need)
- [Model selection](#model-selection)
- [Architecture](#architecture)
- [Testing](#testing)
- [Deploying](#deploying)
- [Troubleshooting](#troubleshooting)
- [Where this app came from](#where-this-app-came-from)
- [Privacy](#privacy)

---

## Features

### 1. Article → X

- **Capture or import**: take a photo, pick from the gallery, or paste raw text.
- **Batch of up to 10 images**: queue several photos at once. Each image is analysed independently
  and produces **its own post draft** — a newspaper page with three articles you photographed
  separately becomes three separate posts, not one blended mush.
- **Focused analysis**: the prompt instructs the model to lock on to the single dominant story in
  the frame and ignore peripheral columns, adjacent headlines, sidebars and advertisements that
  crept in at the edge of the shot. Every draft reports the `Main topic analysed` so you can verify
  it picked the right story.
- **Strictly grounded**: no editorialising, no outside knowledge, no invented figures or quotes, no
  emojis. If a fact is not in the image, it does not appear in the post.
- **Source attribution**: the masthead is detected from the image (WSJ, FT, NYT, The Hindu, Times of
  India, Economic Times, Deccan Herald, ...) and credited in the post.
- **Review then publish**: nothing is posted automatically. Review and edit every draft in the
  queue, remove the ones you don't want, then **Approve & Post All** publishes them one by one with
  spacing between calls, showing per-draft status.
- **X Blue long-form** supported via a toggle in Settings.

### 2. Conference Reporter

Capture slides, record audio notes and session metadata, then synthesize a structured report:
executive summary, key takeaways, slide insights, notable quotes and action items.

### 3. History, Email and Drive

- The last **10** briefs are stored locally (Room, FIFO rollover). Nothing is uploaded to a server.
- Any brief can be emailed to yourself via Gmail or saved to Google Drive as Markdown, using
  standard Android share intents.

---

## Getting started with Android Studio

New to Android Studio? This section assumes no prior experience. If you already know the IDE, the
short version is: open the folder, let Gradle sync, press Run.

### Step 1 — Install Android Studio

Download it from [developer.android.com/studio](https://developer.android.com/studio) and run the
setup wizard, accepting the defaults. The wizard installs the Android SDK for you.

You do **not** need to install Java separately. Android Studio bundles its own JDK (this project was
built with the JDK 25 that ships inside Studio 2026.1). Java is only something you have to think
about if you build from the terminal — see [Building from the command line](#building-from-the-command-line).

### Step 2 — Open the project

1. Start Android Studio.
2. Choose **Open** (not *New Project*), and select the `namma-omnibrief` folder — the one containing
   `settings.gradle.kts`. Pick the folder itself, not a file inside it.
3. If you are asked whether you trust the project, choose **Trust Project**.

Android Studio now runs a **Gradle sync**: it reads the build files and downloads every dependency.
Watch the status bar at the bottom. The first sync downloads a few hundred MB and can take several
minutes. Wait for **"Gradle sync finished"** before doing anything else.

> [!NOTE]
> If Studio offers to upgrade the Android Gradle Plugin, **decline for now.** The project is pinned
> to AGP 9.1.1 and an unattended upgrade is a common way to break a working build. If Studio says
> your version is too *old* to open the project, update Android Studio itself instead.

### Step 3 — Pick something to run the app on

You need either an emulator or a real phone. A real phone is better here, because this app uses the
camera and microphone.

#### Option A — Create an emulator

A fresh SDK install has no virtual devices, so you create one:

1. **View → Tool Windows → Device Manager** (or the phone icon in the right sidebar).
2. Click **＋ → Create Virtual Device**.
3. Pick any modern phone, for example **Pixel 8**, then **Next**.
4. Choose a system image. Any API level **24 or higher** works; **API 36** matches what this project
   targets. If there is a **Download** link next to the image name, click it first and wait.
5. **Next → Finish**, then press ▶ in Device Manager to boot it.

> [!TIP]
> The emulator can fake a camera, but photographing a newspaper is the whole point of this app. Use a
> real phone if you have one.

#### Option B — Use your own Android phone

1. On the phone: **Settings → About phone**, then tap **Build number** seven times. You will see
   "You are now a developer".
2. Go to **Settings → System → Developer options** and turn on **USB debugging**.
3. Connect the phone to your computer with a USB cable.
4. On the phone, a dialog asks **"Allow USB debugging?"** — tick *Always allow* and accept. This
   dialog is easy to miss; if the phone never shows up in Studio, unplug and replug to trigger it.

To check the computer can see the phone, run:

```bash
~/Library/Android/sdk/platform-tools/adb devices
```

You should see your device listed. `unauthorized` means you have not accepted the dialog on the
phone yet.

Prefer no cable? Use **Device Manager → ＋ → Pair using Wi-Fi** and scan the QR code with
**Developer options → Wireless debugging** on the phone.

### Step 4 — Run it

1. In the toolbar at the top, the dropdown on the left should read **app**. Next to it, choose your
   emulator or phone.
2. Press the green **▶ Run** button (or `Ctrl+R` on macOS).

The first build takes a few minutes; later ones are much faster. The app installs and launches
automatically.

**Useful panels while it runs:**

| Panel | What it is for |
|---|---|
| **Run** (bottom) | Build progress and install errors |
| **Logcat** (bottom) | Live log from the device. Filter by `package:mine` to see only this app |
| **Build** (bottom) | Compile errors — click one to jump to the line |
| **Problems** | Warnings and lint findings |

If the app misbehaves, Logcat is where the answer is. The networking code logs under the tags
`GeminiApi` and `XApiService`.

### Step 5 — Add your keys

The app builds and launches with no keys at all, but it cannot call Gemini or X until you add them.
Open **Settings** in the running app and paste them in — see [Adding your keys](#adding-your-keys)
below.

---

## Building from the command line

Optional — everything above can be done from the IDE. But if you want to script it:

Gradle needs to know where Java is, and the JDK inside Android Studio is not on your `PATH` by
default. Without this you get `Unable to locate a Java Runtime`:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

| Command | What it does |
|---|---|
| `./gradlew :app:assembleDebug` | Build a debug APK |
| `./gradlew :app:installDebug` | Build and install onto the connected device |
| `./gradlew :app:testDebugUnitTest` | Run the unit tests |
| `./gradlew :app:lint` | Static analysis |
| `./gradlew clean` | Delete build outputs |

The APK lands in `app/build/outputs/apk/debug/app-debug.apk` (~23 MB).

---

## Adding your keys

There are two ways to supply credentials. **No key is ever committed to this repository.**

### Option A — in the app (recommended)

Open **Settings** in the app and paste your keys. Every field has a **Paste** button that reads
straight from the clipboard, so you never have to type a token by hand.

If you keep all your keys together (in a password manager note, or a `.env` file), copy the whole
block and use **Import All Keys From Clipboard** — it parses the lot in one tap. Both `=` and `:`
separators work, and `export ` prefixes, quotes, trailing commas and `#` comments are tolerated:

```
GEMINI_API_KEY=AIzaSy...
X_CLIENT_ID=cXo1...
X_CLIENT_SECRET=...
X_ACCESS_TOKEN=...
X_REFRESH_TOKEN=...
X_BEARER_TOKEN=AAAA...
```

Keys entered this way are stored in the app's private `SharedPreferences` on the device only, and
are sent solely to Google and X over HTTPS.

### Option B — a local `.env` for development (cannot enable posting)

> [!IMPORTANT]
> A `.env` can supply **only** the Gemini key and the app-only X **Bearer** token. Neither of those
> can publish a post. The app-only bearer is rejected by X with
> `403 Unsupported Authentication` on `POST /2/tweets`, and the four OAuth 2.0 fields have **no**
> build-time fallback — they are read from Settings only. **If you want to post to X, you must use
> Option A.**

Create a `.env` in the project root (it is git-ignored; see `.env.example`):

```
GEMINI_API_KEY=your_key_here
X_BEARER_TOKEN=your_token_here      # read-only endpoints; CANNOT post
```

These two are injected into `BuildConfig` at build time by the Secrets Gradle plugin. Use this route
when you just want article analysis working on a dev build without tapping through Settings.

What each route can configure:

| Credential | Settings (Option A) | `.env` (Option B) | Needed to post? |
|---|---|---|---|
| `GEMINI_API_KEY` | Yes | Yes | No (needed to analyse) |
| `X_BEARER_TOKEN` | Yes | Yes | **No — cannot post** |
| `X_CLIENT_ID` | Yes | **No** | **Yes** |
| `X_CLIENT_SECRET` | Yes | **No** | **Yes** |
| `X_ACCESS_TOKEN` | Yes | **No** | **Yes** |
| `X_REFRESH_TOKEN` | Yes | **No** | Yes (to auto-renew) |

Resolution order at runtime is: **Settings value → `BuildConfig` (`.env`) → empty**.

---

## Posting to X: which credentials do you need?

This matters, because the two token types are not interchangeable:

| Credential | Can read | Can post | Use it for |
|---|---|---|---|
| **App-only Bearer token** | Yes | **No** — `POST /2/tweets` returns `403 Unsupported Authentication` | Read-only endpoints |
| **OAuth 2.0 User Context** (`tweet.write` scope) | Yes | **Yes** | Actually publishing posts |

So to post from inside the app you must use **OAuth 2.0 User Context**. In Settings, select the
OAuth 2.0 protocol and fill in Client ID, Client Secret, Access Token and Refresh Token, then tap
**Test Connection & Refresh Token** to verify and mint a fresh access token. The app refreshes the
token automatically when a post attempt returns `401`.

> **Note:** refreshing rotates your refresh token. The new value is saved back into Settings
> automatically — if you also keep the token elsewhere, update your copy.

If you'd rather not wire up OAuth at all, every draft has an **Open in X app** action that hands the
composed text to the official X composer, where you tap Post yourself.

### Getting the keys

- **Gemini API key**: [Google AI Studio](https://aistudio.google.com/apikey).
- **X keys**: [X Developer Portal](https://developer.x.com/) → your project → *Keys and tokens*.
  Enable OAuth 2.0, set the app permissions to **Read and write**, and request the `tweet.write`
  scope.

---

## Model selection

Settings lets you pick the active Gemini model. The app is configured for
`gemini-3.8-flash` in high-context mode and automatically falls back down the chain
(`3.8 → 3.5 → 2.5 flash`) if a model is unavailable or rate-limited, so an analysis rarely fails
outright.

---

## Architecture

| Layer | Implementation |
|---|---|
| UI | Jetpack Compose, Material 3, single Activity, `when`-based destination switching |
| State | One `MainViewModel` exposing `StateFlow`s, collected with `collectAsStateWithLifecycle()` |
| Persistence | Room (`BriefItem`, 10-item FIFO rollover) + `SharedPreferences` for settings |
| Networking | OkHttp + `org.json`, no generated clients |
| Gemini | `generativelanguage.googleapis.com/v1beta`, images downscaled to 1600px and sent as base64 JPEG `inlineData` |
| X | `api.twitter.com/2/tweets`, with OAuth 2.0 refresh at `/2/oauth2/token` |
| Export | Android share intents to Gmail and Drive via a `FileProvider` |

```
app/src/main/java/com/example/
├── MainActivity.kt              # Scaffold, top bar, bottom nav
├── data/
│   ├── local/                   # Room database, entity, DAO
│   ├── preferences/             # AppPreferences (keys, model, toggles)
│   ├── remote/                  # GeminiApiService, XApiService
│   └── repository/              # BriefRepository
└── ui/
    ├── components/              # OmniTopBar, OmniNavBar, XPostPreviewCard, PhotoStripView
    ├── screens/                 # ArticleToX, ConferenceReporter, History, Settings
    ├── theme/                   # Light theme, colours
    └── viewmodel/               # MainViewModel
```

---

## Testing

### Automated tests

In Android Studio, right-click `app/src/test/java/com/example` in the Project panel and choose
**Run 'Tests in com.example'**. Results appear in the Run panel, green for pass.

From the terminal:

```bash
./gradlew :app:testDebugUnitTest
```

There are 8 tests. They run on your computer, not a device, so they take seconds and need no
emulator. They cover:

- the app name and preference round-tripping;
- the **no-hardcoded-secrets invariant** — every credential must resolve to empty until the user
  configures one;
- the bulk clipboard key parser;
- a Roborazzi screenshot snapshot of the top bar.

If the screenshot test fails after you intentionally change the top bar, re-record the baseline:

```bash
./gradlew recordRoborazziDebug
```

### Manual smoke test

The automated tests do not cover the UI flows, so after any significant change run through this by
hand on a device:

| # | Flow | Expected |
|---|---|---|
| 1 | Settings → paste Gemini key → Save | "All Settings saved successfully" |
| 2 | Article → X → camera → photograph an article → Analyse | A draft appears with "Main topic analysed: …" naming the right story |
| 3 | Add 2-3 photos at once | One separate draft **per image**, not one merged post |
| 4 | Try to add an 11th image | Blocked at the 10-image cap |
| 5 | Edit a draft, delete another | Queue updates; counts stay correct |
| 6 | Scroll to the bottom of **every** screen | Nothing hidden behind the bottom nav bar |
| 7 | Settings → Test Connection & Refresh Token | Clear success or a readable error |
| 8 | History | The batch appears as one entry; older entries roll off past 10 |

For step 2, check the draft against the photo before trusting it. The prompt is strict about
staying grounded, but verifying beats assuming.

> [!TIP]
> Watch **Logcat** while testing and filter by `package:mine`. API failures are logged under the
> `GeminiApi` and `XApiService` tags with the real error from the server.

### Instrumented tests

`./gradlew :app:connectedAndroidTest` runs on-device tests, but this project has only the default
placeholder, so there is nothing meaningful to run yet.

---

## Deploying

"Deploy" means different things depending on what you want. This is a personal app, so option 1 is
almost certainly what you want.

### Option 1 — Just put it on your own phone (recommended)

Pressing **▶ Run** in Android Studio already installs the app on the connected device, and it stays
there after you unplug it. That is the whole job.

To install on a phone that is not connected to your computer, build an APK and transfer it:

```bash
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Send that file to the phone (Drive, email, AirDrop to a Mac then a cable) and tap it. Android will
ask you to allow installing from unknown sources — that is expected for an app that did not come
from the Play Store.

> [!NOTE]
> A debug APK is signed with an automatically generated debug key. That is fine for personal use.
> It cannot be uploaded to the Play Store, and if you later install a release build you must
> uninstall the debug one first, because the signatures differ.

### Option 2 — A signed release build

Only needed if you want a smaller, optimised build or intend to distribute it.

First create a keystore. **Keep this file and its passwords safe** — Android identifies your app by
its signature, so losing the keystore means you can never update an installed app:

```bash
keytool -genkey -v -keystore my-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

The build picks it up through environment variables:

```bash
export KEYSTORE_PATH="$PWD/my-upload-key.jks"
export STORE_PASSWORD="…"
export KEY_PASSWORD="…"
./gradlew :app:assembleRelease      # APK
./gradlew :app:bundleRelease        # AAB, the format the Play Store wants
```

Outputs land in `app/build/outputs/`. `*.jks` files are git-ignored, so your keystore will not be
committed by accident.

> [!IMPORTANT]
> If no keystore is found the release build still succeeds, but **unsigned** — the build log warns
> you. An unsigned APK cannot be installed on a device.

### Option 3 — Google Play

Only worth it if you want to share the app beyond yourself. In outline: create a
[Play Console](https://play.google.com/console) account (one-off 25 USD), upload the `.aab` from
option 2, and complete the store listing, content rating and data-safety declarations. For a
private personal app, use **Internal testing**, which distributes to a list of up to 100 email
addresses without a public listing or a full review.

Before publishing anything publicly you would need to change `applicationId` from the generated
`com.aistudio.omnibrief.kypzmr` to something you own, and it must be globally unique and permanent.

---

## Troubleshooting

Problems this project has actually hit:

| Symptom | Cause and fix |
|---|---|
| `Unable to locate a Java Runtime` | Terminal builds only. `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` |
| `SDK location not found` | Missing `local.properties`. Opening the project in Studio creates it, or add `sdk.dir=/Users/<you>/Library/Android/sdk` |
| `Keystore file ... debug.keystore not found` | Stale build config. Already fixed here — signing is applied only when a keystore exists. Re-sync Gradle |
| Build seems to hang with no output | Piping Gradle into `tail` buffers everything. Use `tee`, or no pipe |
| Device not listed in the Run dropdown | USB debugging off, or the "Allow USB debugging?" prompt was not accepted. Check `adb devices` |
| Gradle sync fails right after opening | Usually a transient download failure. **File → Sync Project with Gradle Files** and retry |
| `Configuration cache ... cannot be reused` | Informational, not an error. Editing build files invalidates the cache and the next build is slower |
| "unable to strip ... libandroidx.graphics.path.so" | Harmless warning, ignore |

---

## Where this app came from

The first version was generated in [Google AI Studio](https://aistudio.google.com/), which produced
the Compose UI, the Room database and the initial Gemini wiring from a series of prompts. The
generated project is at
[ai.studio/apps/0ff20665-6888-44b0-9d53-f75b5441348e](https://ai.studio/apps/0ff20665-6888-44b0-9d53-f75b5441348e).

Worth understanding if you go back to AI Studio to make changes:

- **AI Studio writes Android code; it does not run it.** There is no emulator in the browser for an
  Android project. Building, running and debugging all happen in Android Studio. The export's own
  instructions said the same.
- **In AI Studio**, the Gemini key comes from its **Secrets** panel, which is why `.env.example`
  mentions it. Outside AI Studio the same value comes from Settings in the app or a local `.env`.
- **Regenerating overwrites hand-written fixes.** A lot of what is in this repo — the working Gradle
  wrapper, the conditional signing config, the removal of hardcoded credentials, the grounding and
  character-limit fixes in the prompt — was added by hand afterwards. Pulling a fresh export over
  the top will undo them.
- Several AI Studio runs on this project stopped mid-edit with "Quota exceeded" and left the code
  uncompilable, so **always build after an export** rather than assuming it is sound.

If you do want to keep using AI Studio for large UI changes, treat it as a code generator: take the
diff it produces, review it, and keep the fixes documented in [AGENTS.md](AGENTS.md).

---

## Privacy

This is a personal app. Briefs are stored only on the device, capped at the 10 most recent. Images
and text are sent to the Gemini API for analysis and, when you approve a post, to X. Nothing else
leaves the device.
