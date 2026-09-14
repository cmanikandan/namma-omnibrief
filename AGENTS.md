# AGENTS.md

Operating notes for AI agents (and humans) working on **Namma Omnibrief**.

Read this before touching anything. It records the environment quirks, the design decisions and the
non-obvious traps that have already cost a debugging cycle once.

---

## 1. What this app is

A personal Android app with two jobs:

1. **Article → X.** Photograph / import / paste an article, Gemini drafts a post for X, the user
   reviews and publishes.
2. **Conference Reporter.** Capture slides + audio + metadata for a session, Gemini synthesizes a
   structured field report.

Plus **History** (last 10 briefs, local only) and **Settings** (keys, model, toggles).

It is a *personal* app. There is no backend, no user accounts, no analytics. Everything is on-device
except the direct HTTPS calls to Gemini and X.

**How it was built:** in **Google Antigravity**, agent-first — the Kotlin, the Gradle config, the
tests and the docs were written by an agent and reviewed turn by turn, with verification done on a
real Pixel over `adb` rather than in an emulator.

Before that it started from a machine-generated scaffold whose runs repeatedly died mid-edit. Two
consequences survive and are worth knowing: the leftover naming (package `com.example`,
applicationId `com.aistudio.omnibrief.kypzmr`), and the possibility that any remaining oddity is
half-finished generated output rather than deliberate design. Verify before preserving.

---

## 2. Build environment — read this first

The single biggest time sink on this project. Do these three things or nothing will build.

```bash
# 1. The JDK is NOT on PATH. `java -version` fails. Always export this first:
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# 2. Build from the project root using the wrapper:
./gradlew :app:assembleDebug   --console=plain
./gradlew :app:compileDebugKotlin --console=plain   # fast syntax check
./gradlew :app:testDebugUnitTest  --console=plain
```

`local.properties` (git-ignored) must exist with `sdk.dir=/Users/<you>/Library/Android/sdk`.

### Toolchain

| Component | Version |
|---|---|
| Gradle wrapper | 9.6.0 |
| Android Gradle Plugin | 9.4.0 |
| Kotlin | 2.2.10 |
| compileSdk / targetSdk | 36.1 / 36 |
| minSdk | 24 |
| Java source/target | 11 |
| Compose BOM | 2024.09.00 |

### Pitfalls that have already bitten

- **Piping Gradle to `tail` buffers everything.** The task log stays empty until the build finishes,
  which looks like a hang. Use `2>&1 | tee /tmp/build.log`, or no pipe, and wait for the completion
  notification rather than polling.
- **Configuration cache is ON.** Editing `build.gradle.kts` invalidates it and the next build is
  slower. That is normal, not a failure.
- **`google-services.json` is absent** and that is fine — the plugin is set to
  `MissingGoogleServicesStrategy.WARN` with `googleServices.missing.passthrough=true`.
- **Signing configs are conditional.** The original scaffold hardcoded a `debug.keystore` and an
  upload keystore, both git-ignored, so `assembleDebug` failed on any clean machine. They are now
  applied only if the keystore file exists (debug falls back to AGP's managed keystore, release
  falls back to unsigned with a warning). **Do not revert this to `getByName(...)`.**
- `libandroidx.graphics.path.so` / `libdatastore_shared_counter.so` "unable to strip" warnings are
  benign.
- **Android Studio rewrites tracked files.** Opening the project bumped AGP to 9.4.0, KSP to 2.3.6
  and added `org.gradle.tooling.parallel=true`. That upgrade was verified (`assembleDebug` plus
  8/8 unit tests) and kept. Policy is **verify then keep**, not "always decline" — but never commit
  an IDE-initiated version bump without running the build and tests first.
- **IDE output is git-ignored**, including all of `.idea/` and `gradle/gradle-daemon-jvm.properties`.
  The latter pins a JDK 25 toolchain and would make Gradle try to *download* a JDK on another
  machine, which conflicts with the `JAVA_HOME` instruction above. Do not commit either.

---

## 3. Architecture

Deliberately simple. Do not add a DI framework, a navigation library or a repository abstraction
layer "for correctness" — this is a single-user personal app and the flat structure is the point.

```
app/src/main/java/com/example/
├── MainActivity.kt              # Scaffold + OmniTopBar + OmniNavBar, when(destination) switch
├── audio/                       # AudioRecorderManager, AudioPlayerManager
├── data/
│   ├── local/                   # Room: OmniBriefDatabase, BriefItem, BriefItemDao
│   ├── preferences/             # AppPreferences (SharedPreferences wrapper)
│   ├── remote/                  # GeminiApiService, XApiService, HackerNewsService  (raw OkHttp + org.json)
│   └── repository/              # BriefRepository
└── ui/
    ├── components/              # OmniTopBar, OmniNavBar, XPostPreviewCard, PhotoStripView, ...
    ├── screens/                 # HeadlinesScreen, ArticleToXScreen, ConferenceReporterScreen, HistoryScreen, SettingsScreen
    ├── theme/                   # Light theme (Color.kt, Theme.kt, Type.kt)
    └── viewmodel/               # MainViewModel — ALL state lives here
```

Conventions in force:

- **Package is `com.example`**, applicationId is `com.aistudio.omnibrief.kypzmr`. Leftovers from the
  generated scaffold. Renaming is a wide, risky change — leave them unless asked.
- **No Navigation-Compose.** `MainActivity` switches on an `AppDestination` enum that lives in
  `ui/components/OmniNavBar.kt`. There are **five** destinations; `HEADLINES` is first and is the
  launch destination.
- **One ViewModel.** `MainViewModel : AndroidViewModel` exposes everything as public
  `MutableStateFlow`s and screens write to them directly
  (e.g. `viewModel.articleError.value = "..."`). Screens read with `collectAsStateWithLifecycle()`.
  Unconventional, but consistent — follow it rather than half-migrating to a cleaner pattern.
- **The ViewModel is hoisted into `setContent`**, not obtained inside the theme. `MyApplicationTheme`
  takes a `fontScale` parameter, so it has to observe the ViewModel from outside. Don't push the
  `viewModel()` call back down into the composable tree or live font scaling breaks.
- **No Retrofit/Moshi usage** despite the dependencies being declared. Networking is hand-rolled
  OkHttp with `org.json`. Keep it that way for consistency.
- **DataStore is commented out** in `app/build.gradle.kts`; settings use plain `SharedPreferences`.
- Room writes go through `BriefItemDao.insertWithRollover(item, maxLimit = 10)` — a FIFO trim that
  keeps only the 10 most recent briefs.

---

## 4. Secrets policy — non-negotiable

> [!CAUTION]
> **The repository is PUBLIC** (made public 2026-09-14). Anything committed is world-readable
> immediately and stays in history even if you delete it in a later commit. There is no "fix it
> tomorrow" — a leaked key must be **rotated**, not just removed.

**No credential may ever be hardcoded in source.** A previous revision shipped six of them; they
were stripped, and a unit test now enforces the invariant. Do not reintroduce them, not even as a
"temporary default".

Resolution order in `AppPreferences`:

1. Value saved by the user in **Settings** (SharedPreferences, device-only).
2. `BuildConfig` value injected from the **local, git-ignored `.env`** via the Secrets Gradle plugin.
3. Empty → the UI prompts the user to configure the key.

- `.env` holds real keys and **is git-ignored**. `.env.example` holds placeholders and is committed.
- The OAuth 2.0 fields (client ID/secret, access/refresh token) have **no** build-time fallback at
  all — user-supplied only.
- Before any commit, sanity check the staged diff and the ignore rules:
  ```bash
  git diff --cached | grep -inIE "AQ\.Ab8|AIzaSy[A-Za-z0-9_-]{10,}|github_pat_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9]{20,}|AAAAAAAAAAAAAAAAAAAAA[A-Za-z0-9%]{10,}|-----BEGIN [A-Z ]*PRIVATE KEY"
  git check-ignore -v .env local.properties
  ```
  Note the `github_pat_` literal in this file is a **known false positive** of that grep.
- Test fixtures **and UI examples** must use obviously fake values (`FAKE_CLIENT_ID_123`), never a
  prefix of a real key. This is not theoretical: the Settings *Bulk Import* card shipped
  `X_CLIENT_ID=cXo1...`, which is the first four characters of the live X client ID. Caught and
  replaced during the pre-publication audit.
- **Commit identity is the GitHub noreply address**, `3676043+cmanikandan@users.noreply.github.com`,
  set in the repo-local `git config`. All 14 pre-publication commits were rewritten to it. Do not
  commit with a corporate address.

> Any credential that has appeared in a chat transcript should be treated as compromised and
> rotated.

**The audit that was run before going public** — repeat it before any future visibility change. The
working tree alone is not enough; history is what gets published:

```bash
git log --all -p --no-color | grep -inIE "<the pattern above>"   # every commit, not just HEAD
git log --all --pretty=format: --name-only --diff-filter=A | sort -u | \
  grep -iE "\.env|local\.properties|\.jks|\.keystore|google-services\.json|\.pem|id_rsa"
```

Also **look at the screenshots**. `docs/screenshots/*.png` are rendered device captures; a grep will
never see a key in them.

---

## 5. Gemini integration

`data/remote/GeminiApiService.kt`, endpoint
`https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=`.

- Images are downscaled to max 1600px and sent as base64 JPEG `inlineData` parts.
- **Fallback chain:** `callWithFallbacks()` walks
  `FALLBACK_MODELS = ["gemini-3.8-flash", "gemini-3.5-flash", "gemini-2.5-flash"]` so a rate-limited
  or unavailable model doesn't fail the whole analysis. Used by both the article and conference
  paths.
- The selectable model list is `AppPreferences.AVAILABLE_MODELS`. **Settings renders from this list**
  — don't hardcode chips in the UI again, they drift.
- `gemini-3.8-flash` was confirmed present on the user's key via ListModels.

### The article prompt is load-bearing

The user's explicit requirements are encoded as 8 hard rules. Do not "improve" the prompt casually:

1. Analyse the **single dominant story** in the image only.
2. **Ignore peripheral columns, adjacent headlines, sidebars and advertisements** that were caught
   inadvertently at the edge of the photo.
3. Stay **strictly grounded** in the image — no outside knowledge.
4. **Never editorialise.**
5. Never fabricate figures, quotes or names.
6. **No emojis.** Professional, factual register.
7. Detect the masthead and attribute the source (WSJ, FT, NYT, The Hindu, Times of India, Economic
   Times, Deccan Herald, ...).
8. Emit the required output format, including a `mainTopic` field.

`mainTopic` is surfaced in the UI as "Main topic analysed: …" so the user can verify the model
locked on to the right story. Keep parsing it.

### Correcting the source has to rewrite the draft, not just the chip

The publication name lives **inside the text the model wrote** — in the `Source:` line and usually
mid-sentence too ("Key findings reported by …"). `setArticleSource` used to set
`articleSource.value` and stop there, so correcting a misdetected masthead changed the chip, left
every draft still naming the wrong paper, and published it that way.

Two rules now hold:

1. **Changing the source retargets existing drafts.**
   `MainViewModel.retargetSource(text, oldSource, newSource)` is pure and unit tested
   (`SourceRetargetTest`). It replaces every mention case-insensitively, tries the bare form without
   a leading "The" because the model is inconsistent about it, and then rewrites any line-anchored
   `Source:` outright — which is the only thing that works when the old name was never recorded.
   **Drafts already `POSTED` are skipped**: the tweet is live saying something else, and editing the
   local copy would only hide that.
2. **Detection fills a blank; it never overrules a human.** `sourceIsUserOverride` is set by
   `setArticleSource` and cleared by `clearArticleWorkspace`. Without it, picking a source and then
   re-analysing put the auto-detected masthead straight back.
3. **The archived History row is retargeted too.** Fixing only the in-memory drafts left Room still
   holding the wrong masthead, so the Archive card and anything reopened from it disagreed with the
   editor. `MainViewModel` remembers the row it wrote in `archivedArticleBriefId` — populated by the
   new `onSaved` callback on `saveDraftToRoom`, because `saveWithRollover` returns the id and nothing
   was capturing it — and `retargetArchivedBrief(newSource)` rewrites both `sourceOrSpeaker` and the
   `content` prose. It reads the **old** name from `existing.sourceOrSpeaker` rather than trusting
   the caller, which is the only value guaranteed to match what was actually persisted. Failures are
   logged and swallowed: a History row that did not update must never take down the editor.
   Like the drafts, this is **skipped entirely once any draft in the batch is `POSTED`** — the live
   tweet says something else and quietly rewriting the local record would only conceal that.
   `clearArticleWorkspace` resets the id so a new article cannot retarget the previous one's row.

**Verified on device 2026-09-14** (Pixel 10): a Sample FT draft archived as "Financial Times", the
source was changed to Economic Times in the picker, and the Archive row updated **in place** — same
timestamp, still `1/10`, no duplicate. Reopening it in the editor showed zero occurrences of
"Financial Times" and two of "Economic Times" (the mid-sentence attribution and the `Source:` line).

### Two rules that exist because live testing caught a real failure

Both were found running the real API against a photographed WSJ page. Do not relax them.

1. **Rule 4 forbids promoting incidental mentions.** The model claimed Xbox was prioritising
   *Fallout*, which appears in that article only inside a source's job title ("head of the studio
   that makes 'Fallout' and 'Elder Scrolls'"). Plausible, confident, and wrong — the worst kind of
   grounding error. The rule now bans treating job titles, captions, asides, examples and
   comparisons as claims about the subject.
2. **Rule 7 asks for ≤ 260 characters, not "under 280".** Asked for "strictly under 280" the model
   returned **281** — X rejects that outright. Models count tokens, not characters, so they need
   headroom rather than a boundary. There is also a **pre-flight guard in
   `MainViewModel.approveAndPostToX()`** that fails an over-limit draft locally before the network
   call; keep both layers, the prompt alone is not trustworthy here.

Limits live in `AppPreferences.X_STANDARD_CHAR_LIMIT` / `X_PREMIUM_CHAR_LIMIT`.

---

## 5A. Hacker News home screen (Today tab)

`data/remote/HackerNewsService.kt` + `ui/screens/HeadlinesScreen.kt`. This is the **launch
destination**, so it must degrade gracefully — it is the first thing a brand-new install renders.

- **Endpoint is the Algolia HN Search API**, `https://hn.algolia.com/api/v1`. Chosen specifically
  because it is **public and keyless**: the home screen has to populate before the user has
  configured a Gemini key. Do not move this behind a credential.
- **Ranking is interest-first, not popularity-first**:
  `score = points + 600 × interestMatches + 250 if front page`. The 600 is deliberately large enough
  that a single interest match beats several hundred points of unrelated hype. The user asked for
  *their* topics, not the HN front page.
- `INTERESTS` and `QUERY_TERMS` encode the user's stated list: genai, openai, gemini, google,
  anthropic, india tech, plus general burning news.

### Two things here that look wrong but are not

1. **`QUERY_TERMS` are single words.** Algolia **ANDs** the words in a query, so `"india tech"`
   matched almost nothing — it required both tokens in the same story. It is `"india"` for that
   reason. If you add a term, keep it to one word or verify the hit count first.
2. **`MAX_PER_INTEREST = 3` exists on purpose.** Without the diversity cap and its backfill, a big
   OpenAI news day fills all ten slots with OpenAI stories. Do not remove it as "redundant with the
   ranking" — it is the thing that keeps the list readable.

The ranking logic is mirrored offline in
`<artifacts>/scratch/check_hn_ranking.py`, which hits the live API and prints the chosen ten. Use it
to sanity check any scoring change without rebuilding the app. Keep it in sync with `INTERESTS` and
`QUERY_TERMS`.

Loading state is a set of shimmering placeholder cards (`ShimmerCard`). On a fast connection the
fetch completes in well under a second, so the shimmer is genuinely hard to catch in a screenshot —
that is not evidence it is missing.

### Freshness

- `MainViewModel.startHeadlineAutoRefresh()` refetches every `HEADLINE_REFRESH_INTERVAL_MS` (1 h).
  A plain coroutine loop, **not WorkManager**: this only has to keep a foreground screen current,
  and the loop dies with `viewModelScope` so a backgrounded app is not spending data.
- `MainViewModel.onAppResumed()`, wired from `MainActivity` via `LifecycleEventEffect(ON_RESUME)`,
  refetches on open but only when the feed is older than `HEADLINE_STALE_AFTER_MS` (15 min).
- Each card shows a story age ("16h ago") from `HeadlineItem.relativeAge()`, driven by a 60-second
  ticker in `HeadlinesScreen` — without the ticker the ages are computed once and then quietly go
  stale on a screen the user leaves open. `relativeAge` returns `""` for an unknown timestamp so the
  label is omitted rather than rendered as "56y ago".

### Sorting

The order lives on **`HeadlineSort.apply()`**, not in the ViewModel, so it is unit-testable without
a ViewModel or a network (`HeadlineSortTest`). `MainViewModel.sortedHeadlines` just combines it with
`headlines`; the screen renders **`sortedHeadlines`, never `headlines`**.

- `FOR_YOU` returns the list untouched — the interest ranking above is the product, and re-sorting
  it would throw that work away. It must stay reachable, which is why this is a sort and not a
  filter.
- `NEWEST` is `sortedByDescending { createdAtSeconds }`. HN timestamps are whole seconds so ties are
  common; the sort is stable so tied stories keep their relevance order instead of reshuffling.
- The choice persists under `headline_sort` by the enum's **stable `id`**, not its ordinal or label.


---

## 6. X integration

`data/remote/XApiService.kt`.

| Credential | Read | Post | Notes |
|---|---|---|---|
| App-only **Bearer** token | Yes | **No** | `POST /2/tweets` → `403 Unsupported Authentication` |
| **OAuth 2.0 User Context** (`tweet.write`) | Yes | **Yes** | The only way to publish |
| …the same token **without `media.write`** | Yes | Text only | Image upload → bare `403`. See below. |

This was discovered the hard way. If a change makes posting "work" with a bearer token, it is wrong.

- Posting: `POST https://api.twitter.com/2/tweets`.
- Refresh: `POST https://api.twitter.com/2/oauth2/token`, Basic auth with `clientId:clientSecret`.
  A `401` on post triggers one automatic refresh-and-retry.
- **Refreshing rotates the refresh token.** The new value is written back to Settings. This is why
  automated end-to-end posting tests are avoided.
- Fallback path: `XApiService.launchDirectXComposer()` opens `twitter.com/intent/tweet` so the user
  can post manually without configuring OAuth at all.

### OAuth scopes — `media.write` is separate and is not implied

The app needs all of:

```
tweet.read tweet.write users.read offline.access media.write
```

`tweet.write` alone publishes text perfectly well but **cannot upload an image**. This cost a
debugging cycle: the user's live tweet went out text-only with no error anywhere in the app.

How it was proven, and how to prove it again:

- `POST /2/media/upload`, `POST /1.1/media/upload.json` and `POST /2/media/upload/initialize` all
  returned **403** with a bare `{"title":"Forbidden","status":403}` — **the body never mentions
  scopes.** Do not trust the error text to tell you what is wrong.
- `GET /2/users/me` returned **200** with the same token, so it was not an auth failure.
- The refresh response from `POST /2/oauth2/token` includes a **`scope`** field. That is the only
  introspection available and it is how the missing scope was found. `refreshOAuth2Token` does not
  currently surface it; read it with curl when diagnosing.

Because the 403 is unexplained by X, `XApiService.uploadImage` **translates it** into a message
naming `media.write` and pointing at the Settings escape hatch. Keep that translation.

### Re-authorising: `tools/x_oauth_setup.py`

A standalone stdlib script (no third-party deps) that runs the Authorization Code + PKCE `S256` flow
against `https://x.com/i/oauth2/authorize` and exchanges the code at
`https://api.x.com/2/oauth2/token`. It takes `--redirect-uri` and picks its capture mode from it: a
one-shot local server for a loopback URL, otherwise a paste-the-redirected-URL prompt.

**Status: executed and verified end to end on 2026-09-14.** It produced a token whose granted scope
string was `offline.access tweet.write media.write users.read tweet.read`, and
`POST /2/media/upload` with that token returned **HTTP 200** and a real media id for the same image
that previously 403'd.

Why it exists rather than "regenerate the tokens in the Developer Portal":

> **The Portal's *Generate OAuth 2.0 Access Token* dialog cannot grant `media.write`.** Its checkbox
> list is hardcoded and does not contain the scope at all — it offers only `tweet.*`, `users.read`,
> `dm.*`, `follows.*`, `like.*`, `bookmark.*`, `space.read`, `list.*`, `mute.*`, `block.*` and an
> `offline.access` toggle. That list is an exact match for the scopes the user's broken token had,
> which is how the whole defect arose. Scopes are granted by the **authorize endpoint**, which
> accepts an arbitrary `scope` parameter.

Three traps, all hit for real:

1. **The redirect URI must already be registered**, matched exactly. When the script's default
   loopback was *not* yet on the App, X answered with a generic *"Something went wrong — You weren't
   able to give access to the App."* That page is identical for a bad client id, an unregistered
   redirect, an invalid scope and an incomplete app config, so it tells you nothing. **Diagnose by
   reading the App's registered callbacks, not by guessing.**
2. **Authorization codes expire in ~30 seconds.** Reading the redirect URL out of the browser,
   putting it in a chat turn and then exchanging it is too slow — measured at ~28 s end to end, which
   failed with `{"error":"invalid_request","error_description":"Value passed for the authorization
   code was invalid."}`. That reads like a broken flow and is not. **Keep the capture and the
   exchange in one process.**
3. **The consent screen gates Authorize behind an "I trust this app" checkbox** when the callback
   domain is unverified. The button is greyed out until it is ticked.

### Just run it with no arguments

> [!TIP]
> **`http://127.0.0.1:8765/callback` is now registered on the App** (added 2026-09-14), alongside the
> original `https://github.com/cmanikandan`. That makes the default path the good one:
>
> ```bash
> X_CLIENT_ID=... X_CLIENT_SECRET=... python3 tools/x_oauth_setup.py
> ```
>
> `is_loopback()` sees the loopback host and runs `capture_via_listener`, a one-shot local HTTP
> server that receives the redirect directly. Nothing is copied by hand, so **trap 2 cannot bite** —
> the exchange happens milliseconds after consent. The only human action is clicking Authorize.
> This is the route to use; pass `--redirect-uri https://github.com/cmanikandan` only if the listener
> is somehow blocked, and expect to lose the race if a chat turn is involved.
>
> Plain `http://` is normally rejected by X, but loopback is the documented RFC 8252 exception. It
> must be the literal `127.0.0.1` — `localhost` is matched as a different string and is rejected.
> Check the port is free first (`lsof -nP -iTCP:8765 -sTCP:LISTEN`); the server binds it directly and
> the script has no fallback port.

**Verified unattended on 2026-09-14.** Granted scope came back as
`offline.access tweet.write media.write users.read tweet.read`, and `POST /2/media/upload` with that
token returned **HTTP 200** with a real media id.

Useful confirmations from that consent screen: the permission line that corresponds to `media.write`
reads **"Upload media like photos and videos for you."** If it is absent, the scope was not offered.

Other properties:
- It **shells out to `curl`**, not `urllib` — Python on this Mac fails the token call with
  `CERTIFICATE_VERIFY_FAILED`, which looks like an auth error and is not.
- The client secret is read with `getpass`, or from `X_CLIENT_ID`/`X_CLIENT_SECRET` when there is no
  tty (for harness use). Nothing is written to disk.

Non-destructive verification, preferred over a live post (see the testing policy below) — unattached
media expires in 24 h and never reaches the timeline:

```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST "https://api.x.com/2/media/upload" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "media=@photo.jpg" -F "media_category=tweet_image"
```

The procedure is documented for the user in README → *Re-authorising with `media.write`*.

### Screenshots for the README

`docs/screenshots/01-today.png … 06-text-size.png` are **tracked** and embedded in README §Screenshots.
Captured from a real device, not an emulator, at the default 1.15× text size, then downscaled with
`sips -Z 432` (Pillow is not installed on this machine).

**Check every capture for credentials before committing.** `05-settings.png` is deliberately scrolled
to the "Bulk Import All Keys" card, whose examples are placeholders (`GEMINI_API_KEY=AIzaSy…`); the
key fields further down the screen are not in frame. If you re-shoot Settings, keep it that way.

**`05-settings.png` is one revision stale.** It was captured before the `X_CLIENT_ID=cXo1...` example
was replaced (§4), so the published image still shows the old string. Four characters of an OAuth
client ID is not a secret, so it was not worth blocking publication on — but re-shoot it the next
time the phone is unlocked and the current build is installed.

### Writing tokens onto the device without typing them

The debug build is `run-as`-able, so SharedPreferences can be edited directly. This is far quicker
than driving the Settings text fields with `adb shell input text`:

```bash
P=com.aistudio.omnibrief.kypzmr; X=shared_prefs/omnibrief_settings.xml
adb shell am force-stop $P            # or the app overwrites your edit on exit
adb shell "run-as $P cat $X" > prefs.xml
#  ... edit x_access_token / x_refresh_token / x_token_expires_at (epoch ms) ...
adb shell "run-as $P sh -c 'cat > $X'" < prefs.xml
adb shell am start -n $P/com.example.MainActivity
```

`x_token_expires_at` is a `<long>` in **epoch milliseconds**; set it to issue time + `expires_in`.
`x_auth_method` must be `OAUTH2_USER`.

### Image attachment

- `XApiService.uploadImage(accessToken, imageUri, context)` → `MediaUploadResult`. Single-shot
  multipart to `POST https://api.x.com/2/media/upload`, part name `media`, plus
  `media_category=tweet_image`. The chunked INIT/APPEND/FINALIZE flow is only needed for video.
- The id is read from `data.id`, falling back to `media_id_string`. **Always as a string** — media
  ids overflow a signed 64-bit int.
- The image is re-encoded by **`ImageEncoder`** (`data/remote/ImageEncoder.kt`), shared with
  `GeminiApiService`, so both services see exactly the same picture: EXIF orientation applied,
  longest edge capped at `MAX_IMAGE_DIMENSION_PX` (1600), JPEG quality 85. **Do not reintroduce a
  local copy of this logic in either service** — they each had one, and the rotation fix initially
  landed in only one of them.
- Upload happens **inside `postTweet`**, after the token has been freshened and before the tweet, so
  it always runs with a valid token.
- **A failed upload fails the whole post.** Deliberate: silently publishing text-only is the exact
  bug this exists to fix, and a tweet cannot be edited to add a picture afterwards. The escape hatch
  is `AppPreferences.attachImageToPost` (Settings → *Attach photo to post*, default **on**).

#### EXIF orientation must be baked into the pixels

Phone cameras usually store the sensor buffer unrotated and record how the phone was held in the
EXIF `Orientation` tag. `BitmapFactory.decodeStream` **ignores that tag**, so a portrait photo
decodes sideways. Worse, compressing a decoded bitmap writes a fresh JPEG with **no EXIF at all**,
so nothing downstream can correct it afterwards.

> [!WARNING]
> **Nothing on the device reveals this.** The gallery honours EXIF and so does Coil, so the in-app
> preview is upright while the uploaded bytes are rotated 90°. The only evidence was a published
> post with a sideways newspaper in it. Do not "verify" an image change by looking at the preview.

`ImageEncoder.transformFor()` maps all eight orientation constants — including the four mirrored
ones — and is pure, so `ImageOrientationTest` pins it without a device. The rotation is applied
*before* the scale so the 1600 px cap applies to the final orientation.

This also affects **analysis quality**, not just presentation: the model was being asked to read
rotated newspaper columns, which plausibly contributed to the masthead being misdetected.

### `[Image N]` markers must never reach the composer

`saveDraftToRoom` stores a batch as one string with `[Image 1] `-style labels and `\n\n---\n\n`
separators. History used to assign that raw string to `activePostDraft`, so a reopened brief carried
the label into the composer — and one was **published at the head of a real tweet**.

Reopening now goes through `MainViewModel.loadArchivedDraft(title, content, source)`, which uses the
pure `MainViewModel.splitArchivedDraft()` to split and strip, and restores a multi-part brief as a
**draft queue** rather than one blob (concatenated it would blow the character limit). The label
regex is anchored and refuses to span a newline so a bracketed aside in the body survives.
`ArchivedDraftTest` covers it; mutation-tested — removing the strip fails 3 of its 6 cases.


### Token lifecycle — normally nothing to do by hand

| Token | Lifetime | Who renews it |
|---|---|---|
| Access token | 2 h (`expires_in: 7200`) | The app, automatically |
| Refresh token | ~6 months, **rotates on every use** | The app writes the new one back |
| Client ID / Secret | until regenerated in the Portal | User, only if they regenerate |

Two independent renewal paths, both in `postTweet`:

1. **Proactive.** `needsRefresh(token, expiresAt, now)` refreshes when within
   `ACCESS_TOKEN_EXPIRY_SKEW_MS` (5 min) of expiry. It is pure and unit-tested (`XTokenRefreshTest`).
   `expiresAt <= 0` means "unknown" and deliberately returns **false** — refreshing a hand-pasted
   token for no reason would burn the single-use refresh token.
2. **Reactive.** A `401` on the post triggers one refresh-and-retry, then a message that explicitly
   explains refresh-token rotation, because X's own text ("Value passed for the token was invalid")
   does not.

Both call back into `AppPreferences.saveRefreshedTokens`, which persists the new access token, the
rotated refresh token and a freshly computed `x_token_expires_at`.

**Known gap:** saving tokens through the Settings screen sets `xAccessToken`/`xRefreshToken` but
**does not touch `x_token_expires_at`**, so a hand-pasted token inherits a stale expiry. If the stale
value is in the past the next post refreshes immediately (harmless, costs one rotation); if it is in
the future the proactive path is skipped and the `401` retry covers it. Worth fixing by setting
`xTokenExpiresAt = 0L` on manual save.

Re-authorisation is only needed when: the refresh token goes unused for ~6 months, a stale copy is
used elsewhere and rotation invalidates the saved one, the client secret is regenerated, or the user
revokes app access.

### Testing policy for X

**Do not run a live post test without explicit user consent.** It publishes a real tweet to their
account and rotates their stored refresh token. Prefer the non-destructive media upload above.
Settings → *Test Connection & Refresh Token* is the on-device equivalent.

**One consented end-to-end run has been completed (2026-09-14)** and is the proof that image
attachment works through the app, not just through curl:

| Stage | Result |
|---|---|
| Gallery pick → `1 / 10 images` | pass |
| Gemini analysis | source auto-detected as *The Times of India*, `mainTopic` surfaced |
| Approve & Post | "Posted 1 of 1 to X successfully", badge `PENDING` → `POSTED` |
| Tweet | id `2099467500891386088`, 299 chars |
| **Image attached** | `attachments.media_keys: ["3_2099467496747343872"]`, `type: photo`, **1600×676** |
| Room archive | Archive tab → (1) |

The **1600 px** width is the useful detail: it matches `MAX_IMAGE_DIMENSION_PX`, proving the bytes
went through `XApiService.readUriAsJpegBytes` rather than some other path.

Not covered by that run: the **standard (non-Blue) character limit** path. The account is X Blue, so
`X_PREMIUM_CHAR_LIMIT` applied and the pre-flight guard in `approveAndPostToX()` was never triggered.

---

## 7. Article → X batch behaviour

The user's requirement, verbatim: *"I should be able to add multiple images… preview of the posts of
all the images. keep a cap of a max 10 images, and then once the user approves, post them one by
one."*

Implementation:

- Cap is `AppPreferences.MAX_ARTICLE_IMAGES = 10`, enforced in the picker and in
  `addArticleImageUri` (returns `false` when full).
- `analyzeArticle()` makes **one Gemini call per image**, plus one for pasted text. One image → one
  `XPostDraftItem`. Separately photographed articles must never be merged into a single post.
- Drafts live in `postDrafts: StateFlow<List<XPostDraftItem>>` with a `DraftPostStatus` each. The
  whole batch is saved as a single History row.
- `approveAndPostToX()` posts the queue **sequentially with ~2s spacing**, updating per-draft status
  and refreshing the token when needed. Nothing is ever posted without explicit approval.
- Gmail / Drive exports are batch-aware via `buildArticleBriefMarkdown()`.
- **Each draft keeps its own `imageUri`** and that photo is attached to *its* post, so a 3-image
  batch produces three tweets each carrying the right picture. See §6 for the upload and the
  `media.write` requirement.

### Clearing the page

`clearArticleWorkspace()` resets images, pasted text, the analysis, the draft queue, the active
draft, both status fields and the batch progress. It is **not** the same as `clearDrafts()`, which
only empties the queue and would leave the previous article's photos silently attached to the next
post.

`articleSource` returns to `prefs.defaultSource` rather than being blanked — a blank source
re-triggers the "which publication is this?" dialog on the next analysis.

The UI control is *Start fresh* in `ArticleToXScreen` (tag `clear_article_workspace_button`), shown
only when there is something to discard and gated behind a confirmation dialog
(`confirm_clear_workspace_button`) because each queued draft cost a Gemini call.

---

## 8. UI conventions

- **Light theme with black text.** The user explicitly rejected the dark theme. Don't reintroduce
  dark surfaces. (Note: the colour *identifiers* are still named `ObsidianCard`, `ObsidianSurface`
  etc. from the original dark palette — the names lie, the values are light. Renaming them is
  cosmetic churn; leave them.)
- **The palette is warm, not clinical.** The user's words were "looks like a clinical app… make it
  alive like an Insta app". Backgrounds are warm off-white (`ObsidianBg #FFF8F5`), the accent is
  jacaranda magenta (`CyanAccent #C91F66` — another name that lies, it has not been cyan for a
  while), and there are three gradient vals for hero surfaces: `SunsetGradient`, `VioletGradient`
  and `BrandGradient`. **Every text/surface pair was checked numerically against WCAG AA**, worst
  case 4.6:1 on `ChipBlush`. If you introduce a colour, check it rather than eyeballing it.
- **Text scaling is applied via `LocalDensity`, not via the typography.** This is the important one.
  These screens were written with roughly **150 literal `fontSize = 12.sp`-style values** instead of
  `MaterialTheme.typography.*`. Scaling only the type ramp therefore changed almost nothing and the
  Text Size setting looked broken while being "implemented" — it shipped that way once, and the user
  caught it. `MyApplicationTheme` now overrides `LocalDensity.fontScale`, which is what `sp` → px
  conversion actually reads, so **every** `sp` in the tree scales, literals included. Consequences:
  - The `Typography` handed to `MaterialTheme` must stay at **baseline** scale. Passing
    `appTypography(fontScale)` as well would scale those styles twice.
  - `density` is deliberately left alone, so `dp` paddings and component sizes do not grow. Very
    large scales will therefore tighten layouts — that is the standard Android trade-off.
  - The system font-size setting is **multiplied**, not replaced, so accessibility settings compose.
  - Settings previews use `PreviewAtFontScale(optionScale, currentScale)`, which divides the ambient
    scale back out. Without that, previews are relative to the current selection and drift.
  - Bounds live in `MIN_FONT_SCALE` / `MAX_FONT_SCALE` in `Type.kt`; default is
    `AppPreferences.DEFAULT_FONT_SCALE = 1.15f` because the user asked for a bigger default.
- **Bottom-nav labels shrink, they never wrap.** The direct consequence of the item above: five tabs
  share the width, so at the larger Text Sizes "Conference" stopped fitting and Material wrapped it
  onto a second line, breaking the word and making the bar taller. The user reported it. Labels now
  go through the private `NavLabel` in `OmniNavBar.kt`, which is `maxLines = 1, softWrap = false`
  and steps the size down by `NAV_LABEL_SHRINK_STEP_SP` from `NAV_LABEL_SP` (11) to a floor of
  `NAV_LABEL_MIN_SP` (8) until `hasVisualOverflow` clears. Measuring passes are hidden with
  `drawWithContent` so nothing visibly settles, and the search state is keyed on the ambient
  `fontScale` so labels grow back when the user picks a smaller size. **Do not replace this with a
  plain `Text`, and do not "simplify" it to a fixed size** — the latter silently opts the whole
  navigation bar out of the Text Size setting. `NavBarLabelTest` covers both directions.
- **Bengaluru identity** is a requirement: `img_bengaluru_logo.jpg` in `OmniTopBar` and the launcher
  icon, the "ನಮ್ಮ BLR" badge, and the "Namma BLR" sample buttons. App name is **Namma Omnibrief**.
- **The launcher icon puts the artwork in the `<background>` layer**, not the foreground. The art is
  an opaque full-bleed raster with no alpha, so insetting it as a foreground renders as a square
  photo floating inside the launcher's circular mask. `<foreground>` is therefore
  `@drawable/ic_launcher_fg_transparent` and `<monochrome>` is a hand-authored vector silhouette for
  Android 13+ themed icons. The raster lives at `mipmap-*/ic_launcher_bg_art.png`, sized 108dp per
  density (108/162/216/324/432 px), with the motif inside the centre ~60% so no mask shape clips it.
  Legacy API 24–25 rasters are `mipmap-*/ic_launcher.png` at 48dp per density.
- **Bottom clearance.** Every scrollable screen must clear the bottom navigation bar. Current
  values: Headlines 120dp, Article 120dp, Settings 120dp, Conference 100dp, History
  `contentPadding(bottom = 110.dp)`. If you add a screen, do the same — "I can't scroll down to see
  the stuff at the bottom" was a real reported bug.
- **Key entry.** Every credential field has Paste + Clear via the shared `KeyFieldActions`
  composable, and Settings has a bulk **"Import All Keys From Clipboard"** card backed by
  `parseKeyBlob()` (tolerates `=` and `:`, `export ` prefixes, quotes, trailing commas, `#`/`//`
  comments; first occurrence of a key wins).
- **In a `Row`, an unweighted child wins.** This is the rule behind a real bug in
  `XPostPreviewCard`'s header. The header was `Arrangement.SpaceBetween` over two children with
  **no `weight` on either**, so a long `batchLabel` ("Sample: Financial Times • 1 of 1") took the
  full width and squeezed the source pill and status badge to nothing. They did not truncate — they
  wrapped **one character per line**, measured at `2 × 994 px` and `12 × 553 px`, inflating the
  header to roughly a full screen and pushing the post body off-screen. It reads as "the draft is
  empty", which is how it went unnoticed. Compose measures unweighted children first at their
  intrinsic size and only then splits the remainder among weighted ones, so the fix is to weight the
  thing that may shrink: the identity block and its label get `weight(1f, fill = false)` plus
  `maxLines = 1` and `TextOverflow.Ellipsis`, and the source column stays **unweighted** behind a
  `widthIn(max = SOURCE_COLUMN_MAX_WIDTH)` cap so it gets its intrinsic width first. `fill = false`
  matters: without it a short label stretches and shoves the pill to the far edge.
  The pill also renders the **bare source name**, not `"Source: $source"` — the eight-character
  prefix was eating the budget that the label needed, and the pencil icon already says what it is.
  If you add anything to this header, re-measure with `adb shell uiautomator dump`; the bug is
  invisible in a code review and easy to reintroduce.
- Screens use `testTag(...)` extensively — preserve existing tags, they back the tests.

---

## 9. Tests

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest --console=plain
```

- `ExampleRobolectricTest` — app name, the **no-hardcoded-secrets invariant**, preference
  round-tripping, the model list, and `parseKeyBlob` coverage.
  - The secrets test compares against the `BuildConfig`-derived expectation, *not* against `""`,
    because a developer with a local `.env` legitimately gets non-empty values. Keep it that way or
    it fails on some machines and not others.
- `FontScaleTest` — asserts a **hardcoded `sp` literal** resolves to a bigger pixel size when the
  setting changes. Asserting on the typography instead would have passed against the broken
  implementation, which is exactly how that bug shipped.
- `HeadlineSortTest` — the Today ordering, via `HeadlineSort.apply()`. Pure JVM, no Robolectric.
- `ArchivedDraftTest` — `[Image N]` marker stripping when a History brief is reopened, via the pure
  `MainViewModel.splitArchivedDraft()`. Pure JVM. Mutation-tested: deleting the strip fails 3 of 6.
- `XTokenRefreshTest` — the proactive-refresh policy (`XApiService.needsRefresh`) and token
  persistence. Fake credential values only.
- `NavBarLabelTest` — the nav labels stay one line at `MAX_FONT_SCALE`, and still grow when there is
  room.
- `GreetingScreenshotTest` — Roborazzi snapshot of `OmniTopBar`. If it fails on a pixel diff after an
  intentional top-bar change: `./gradlew recordRoborazziDebug`.
- Robolectric emits `WARNING: A restricted method in java.lang.System has been called` on modern
  JDKs. Benign.

### Instrumented tests — and the Gradle task that must never be used

`ImageEncoderInstrumentedTest` (6 tests) is the only real proof of the EXIF rotation fix. It has to
be instrumented: `ExifInterface` writing and `BitmapFactory` decoding are both being exercised for
real, and Robolectric's shadows would make the test assert against a simulation of the very thing
that was broken. Each test paints a red marker in one corner of a 400×200 white JPEG, stamps an
orientation tag, runs it through `ImageEncoder.readUriAsJpegBytes`, and asserts **which corner the
marker ends up in** — so a failure distinguishes "rotated the wrong way" from "did not rotate at
all". It is non-vacuous by construction: the same source stays 400×200 under `NORMAL` and becomes
200×400 under `ROTATE_90`.

> [!CAUTION]
> **Never run `./gradlew :app:connectedDebugAndroidTest` on this device.** It uninstalls the app
> before reinstalling, its install then fails with
> `INSTALL_FAILED_VERIFICATION_FAILURE: Verification timed out` (the Play Protect adb verifier), and
> it leaves the app **uninstalled** — which wipes `/data/data` and every saved credential with it.
> **Gradle printed `BUILD SUCCESSFUL` both times it did this.** It cost the X OAuth tokens once
> already; they have no `BuildConfig` fallback by design, so they can only be recovered by
> re-running the authorize flow. `settings put global verifier_verify_adb_installs 0` does **not**
> take on Android 17, so there is no reliable mitigation.

Use the adb-only path instead, which has been reliable every time:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.example.ImageEncoderInstrumentedTest \
  com.aistudio.omnibrief.kypzmr.test/androidx.test.runner.AndroidJUnitRunner
```

`adb install -r` upgrades in place and preserves app data. `-t` allows the test-only APK.


### Writing a Compose layout test that actually measures something

All three of these were wrong in the first draft of `NavBarLabelTest`, and it passed cleanly against
the unfixed code. If a layout test passes immediately, **break the implementation on purpose and
confirm it fails** before believing it.

1. **`@GraphicsMode(GraphicsMode.Mode.NATIVE)` is mandatory.** In legacy graphics mode Robolectric
   does not measure text with real fonts, so nothing ever overflows and no wrap is ever detected.
   Enabling it changed the measured height of the broken nav label from 83 px to 166 px — from
   "looks fine" to the obvious two-liner it always was.
2. **Query the unmerged tree** (`useUnmergedTree = true`). `NavigationBarItem` is selectable and
   merges its children, so the merged node is the whole 80dp tab and every size assertion compares
   the tab against itself.
3. **Set a device qualifier** (`RobolectricDeviceQualifiers.Pixel8`). The default screen is 320dp
   wide, narrower than any phone this app targets.

`createComposeRule().setContent` may only be called **once per test** (`Cannot call setContent twice
per test!`). To compare two configurations, render them as sibling subtrees in a single
`setContent`.

---

## 10. Git / publishing

- Remote: `https://github.com/cmanikandan/namma-omnibrief` — **public** since 2026-09-14, default
  branch `main`. See the caution at the top of §4 before committing anything.
- Never write a PAT into the git remote URL permanently. Pass it inline per command instead, and
  keep `.git/config` clean:
  ```bash
  git push "https://<PAT>@github.com/cmanikandan/namma-omnibrief.git" main
  ```
- Never commit `.env`, `local.properties`, `debug.keystore`, `*.jks`, `build/`, `.gradle/`.

### Verifying the remote

**`git fetch origin` fails silently here** — the remote URL carries no credentials, so a fetch can
leave `origin/main` stale, which looks exactly like a failed push. `git ls-remote` with the PAT is
the authority:

```bash
git ls-remote "https://<PAT>@github.com/cmanikandan/namma-omnibrief.git" refs/heads/main
```

### The visibility flip, for the record

Done over the API, not the web UI, and verified unauthenticated afterwards:

```bash
curl -X PATCH -H "Authorization: Bearer $PAT" \
  https://api.github.com/repos/cmanikandan/namma-omnibrief -d '{"private":false}'   # HTTP 200
curl -s -o /dev/null -w "%{http_code}\n" https://raw.githubusercontent.com/.../main/.env   # 404
```

Commit authorship was rewritten first (corporate address → GitHub noreply) with
`git filter-branch --env-filter` over `main`, then force-pushed. Trees were byte-identical before
and after — confirm that with `git diff --stat <old> HEAD` producing no output, which is the only
cheap proof the rewrite changed metadata and nothing else.

> [!NOTE]
> The pre-rewrite commits (old HEAD `514cfad`) still exist on GitHub as unreachable objects until it
> garbage-collects. They are not discoverable by browsing, only by knowing the SHA. Worth knowing
> before assuming a rewrite is a complete erasure.

---

## 11. Known gaps / candidate next steps

- **On-device verification (2026-09-14, two passes).** Physical **Pixel 10, Android 17 (API 37)**
  over wireless debugging. `MainActivity` reaches the resumed state with no crash and no
  `AndroidRuntime` errors.
  - **Pass 1** caught two real bugs the compiler and the unit tests could not see: the source chip
    wrapped mid-word and collided with the heading (header `Row` used `SpaceBetween` with no
    `weight` on either child), and the nav showed a hardcoded "Archive (10)" while History said
    "0 briefs". Both fixed.
  - **Pass 2** (UI overhaul) confirmed: Today is the launch tab and renders ten live, correctly
    interest-matched HN stories; the warm palette and the larger default type render as intended;
    the five-tab nav lays out correctly; Settings → Appearance → Text Size shows "Comfortable"
    selected with each option previewing at its own scale; Settings scrolls clear of the nav bar and
    the saved keys survived reinstall; tapping a headline opens the source article in the browser;
    and the new adaptive launcher icon renders correctly under the circular mask with nothing
    clipped. It also caught the "ನಮ್ಮ BLR" badge still using the old mint-green `EmeraldVerified`
    against the new warm palette. Fixed.
  - **Still unverified on device:** the Conference and History screens since the restyle; the
    multi-image batch queue; the paste buttons; the shimmer loading state (the HN fetch returns in
    under a second, so it cannot be captured without throttling the network).
  - Capturing anything needs the handset unlocked — `adb` cannot bypass the lock screen, and a
    screenshot of a locked phone is simply black. Check
    `adb shell dumpsys window | grep mDreamingLockscreen` first.
  - Don't use `adb shell monkey -p … -c LAUNCHER 1` to launch: it injects a stray tap that has
    previously hit a button and produced a spurious error banner. Use
    `adb shell am start -n com.aistudio.omnibrief.kypzmr/com.example.MainActivity`.
  - Nav tap targets shift when the tab count changes. Get real bounds with
    `adb shell uiautomator dump` rather than guessing coordinates.
- **Verified on device (2026-09-14, pass 3).** Both user-reported bugs are now confirmed fixed on
  the physical Pixel 10, not merely unit tested.
  - *Source override.* Driven through the real UI: auto-detected "Financial Times" → picker →
    "Economic Times". The chip, the draft body, the `Source:` line and the archived History row all
    followed. The character count fell 371 → 369, which is exactly two occurrences rewritten at
    15 → 14 characters each — a cheap, precise signal that the rewrite hit the prose and not just
    the label. Worth reusing.
  - *EXIF rotation.* `ImageEncoderInstrumentedTest`, 6/6 passing on the handset. This one **cannot**
    be confirmed from the in-app preview: Coil and the gallery both honour the orientation tag, so
    the preview looks correct whether or not the bytes that get uploaded are.
  - *Preview card header.* A third, pre-existing bug found while verifying the first two — see §8.
    Fixed and re-measured: the source pill went from `2 × 994 px` to `243 × 79 px`.
- **The X OAuth tokens were destroyed and have since been re-minted.** See the caution in §9 for the
  cause. The access and refresh tokens, the client id and the client secret all lived only in device
  SharedPreferences; the Gemini key and X bearer token self-healed from the `BuildConfig`/`.env`
  fallback. Recovery, for the next time: mint with `tools/x_oauth_setup.py` (§6), then write
  `shared_prefs/omnibrief_settings.xml` with `adb shell "run-as <pkg> sh -c 'cat > …'" < file` while
  the app is **force-stopped** — a running app holds the prefs in memory and will overwrite the file.
  `x_token_expires_at` is epoch **milliseconds**; `0` means "unknown" and disables proactive refresh
  rather than forcing one.
  **Confirmed working on device 2026-09-14:** Settings → *Test Connection & Refresh Token* performed
  a real refresh — both tokens rotated in storage and the expiry advanced to exactly two hours out.
  That token round-trip is the cheapest end-to-end proof of the X credential path, and it needs no
  live post.
- Also still unverified: the **standard (non-Blue) character-limit** guard in `approveAndPostToX()`,
  since the account is X Blue and the premium limit applies.
- Saving tokens through Settings does not reset `x_token_expires_at` (§6, *Token lifecycle*). One
  line to fix: set `xTokenExpiresAt = 0L` on manual save.
- `docs/screenshots/05-settings.png` predates the `cXo1...` placeholder fix and wants a re-shoot.
- Conference photo picker allows `maxItems = 30` while the article picker caps at 10 — intentional,
  but worth confirming if the conference flow is ever revisited.
- `SettingsScreen` has an unused local `defaultSource`.
- Retrofit / Moshi / converter-moshi are declared dependencies but unused; could be dropped.
- Gmail and Drive "integration" is share-intent based only. Real Google APIs / OAuth would be a
  significant addition, not a tweak.

---

## 12. Working agreements

- **Verify, don't assume.** Run the build. If something could not be tested (live posting, on-device
  UI), say so explicitly rather than implying it works.
- **Preserve existing comments and docstrings** unrelated to your change.
- Keep the tone of generated posts professional and emoji-free — that is a product requirement, not
  a style preference.
- When the user reports a UI bug like "I can't scroll", check **every** screen, not just the one
  mentioned.
