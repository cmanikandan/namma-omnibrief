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

**Origin:** generated in Google AI Studio, then repaired by hand. Several AI Studio runs terminated
mid-edit on "Quota exceeded", so treat any remaining oddity as possible half-finished machine output
rather than deliberate design.

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
- **Signing configs are conditional.** The AI Studio export hardcoded a `debug.keystore` and an
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
  AI Studio scaffold. Renaming is a wide, risky change — leave them unless asked.
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
- Before any commit, sanity check:
  `git diff --cached | grep -iE "AQ\.|github_pat_|AAAAAAAAAAAA"` and
  `git check-ignore -v .env local.properties`.
- Test fixtures must use obviously fake values (`FAKE_CLIENT_ID_123`), never a prefix of a real key.

> Any credential that has appeared in a chat transcript should be treated as compromised and
> rotated.

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

### Image attachment

- `XApiService.uploadImage(accessToken, imageUri, context)` → `MediaUploadResult`. Single-shot
  multipart to `POST https://api.x.com/2/media/upload`, part name `media`, plus
  `media_category=tweet_image`. The chunked INIT/APPEND/FINALIZE flow is only needed for video.
- The id is read from `data.id`, falling back to `media_id_string`. **Always as a string** — media
  ids overflow a signed 64-bit int.
- The image is re-encoded to JPEG capped at `MAX_IMAGE_DIMENSION_PX` (1600), mirroring
  `GeminiApiService.readUriAsBase64Jpeg` so both services see the same picture.
- Upload happens **inside `postTweet`**, after the token has been freshened and before the tweet, so
  it always runs with a valid token.
- **A failed upload fails the whole post.** Deliberate: silently publishing text-only is the exact
  bug this exists to fix, and a tweet cannot be edited to add a picture afterwards. The escape hatch
  is `AppPreferences.attachImageToPost` (Settings → *Attach photo to post*, default **on**).

### `[Image N]` markers must never reach the composer

`saveDraftToRoom` stores a batch as one string with `[Image 1] `-style labels and `\n\n---\n\n`
separators. History used to assign that raw string to `activePostDraft`, so a reopened brief carried
the label into the composer — and one was **published at the head of a real tweet**.

Reopening now goes through `MainViewModel.loadArchivedDraft(title, content, source)`, which uses the
pure `MainViewModel.splitArchivedDraft()` to split and strip, and restores a multi-part brief as a
**draft queue** rather than one blob (concatenated it would blow the character limit). The label
regex is anchored and refuses to span a newline so a bracketed aside in the body survives.
`ArchivedDraftTest` covers it; mutation-tested — removing the strip fails 3 of its 6 cases.


### Testing policy for X

**Do not run a live post test without explicit user consent.** It publishes a real tweet to their
account and rotates their stored refresh token. Verify code paths by reading, and direct the user to
Settings → *Test Connection & Refresh Token* for on-device verification.

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

- Remote: `https://github.com/cmanikandan/namma-omnibrief` — **private**, default branch `main`.
- Never write a PAT into the git remote URL permanently. If you must use one to push, scrub it
  immediately afterwards:
  ```bash
  git remote set-url origin https://github.com/cmanikandan/namma-omnibrief.git
  ```
- Never commit `.env`, `local.properties`, `debug.keystore`, `*.jks`, `build/`, `.gradle/`.

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
- X posting has never been executed end to end (see §6).
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
