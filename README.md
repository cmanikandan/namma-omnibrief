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

## Setup

**Prerequisites:** [Android Studio](https://developer.android.com/studio) and an Android device or
emulator running API 24 or newer.

```bash
git clone <your-repo-url>
cd namma-omnibrief
```

Open the project in Android Studio and let it sync, or build from the command line:

```bash
./gradlew :app:assembleDebug
```

> The app will build and run without any keys. It just won't be able to call Gemini or X until you
> add them.

### Adding your keys

There are two ways to supply credentials. **No key is ever committed to this repository.**

#### Option A — in the app (recommended)

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

#### Option B — a local `.env` for development (cannot enable posting)

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

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Robolectric unit tests assert, among other things, that **no credential is ever hardcoded** —
all key fields must resolve to empty before the user configures them.

---

## Privacy

This is a personal app. Briefs are stored only on the device, capped at the 10 most recent. Images
and text are sent to the Gemini API for analysis and, when you approve a post, to X. Nothing else
leaves the device.
