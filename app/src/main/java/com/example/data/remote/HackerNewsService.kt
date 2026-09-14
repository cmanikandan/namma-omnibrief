package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Ordering applied to the Today feed after it has been fetched and ranked.
 *
 * This is a *sort*, not a filter: both modes show the same ten stories, so switching to
 * [NEWEST] never hides something that [FOR_YOU] surfaced. [id] is what gets persisted, so it must
 * stay stable even if the enum is reordered or the labels are reworded.
 */
enum class HeadlineSort(val id: String, val label: String) {
    /** The interest-weighted ranking the service produced. The default. */
    FOR_YOU("for_you", "For you"),

    /** Strict reverse-chronological by the story's Hacker News post time. */
    NEWEST("newest", "Newest");

    companion object {
        fun fromId(id: String?): HeadlineSort = entries.firstOrNull { it.id == id } ?: FOR_YOU
    }

    /**
     * Returns [items] in this ordering.
     *
     * Lives here rather than in the ViewModel so the rule is testable on its own and there is one
     * place to change when an ordering is added. [FOR_YOU] deliberately returns the list untouched:
     * the relevance ranking is produced upstream by the service and must be preserved so the user
     * can switch back to it. [NEWEST] uses a stable sort, so stories posted in the same second keep
     * their relevance order instead of shuffling between recompositions.
     */
    fun apply(items: List<HeadlineItem>): List<HeadlineItem> = when (this) {
        FOR_YOU -> items
        NEWEST -> items.sortedByDescending { it.createdAtSeconds }
    }
}

/**
 * One ranked Hacker News story shown on the Headlines home screen.
 *
 * [matchedInterests] records which of the user's interest topics caused this story to surface, so
 * the UI can show why it was picked rather than presenting an opaque list.
 */
data class HeadlineItem(
    val id: String,
    val title: String,
    val url: String,
    val points: Int,
    val commentCount: Int,
    val author: String,
    val createdAt: String,
    /**
     * Story publication time as a Unix epoch in **seconds** (Algolia's `created_at_i`).
     *
     * Kept as the epoch rather than only the ISO string in [createdAt] so the UI can compute a
     * relative age without parsing a date on every recomposition. 0 means unknown.
     */
    val createdAtSeconds: Long = 0L,
    val matchedInterests: List<String> = emptyList(),
    val isFrontPage: Boolean = false
) {
    /** Bare domain for display, e.g. "arstechnica.com". Empty for Ask/Show HN text posts. */
    val domain: String
        get() = runCatching {
            java.net.URI(url).host?.removePrefix("www.").orEmpty()
        }.getOrDefault("")

    /** Permalink to the HN discussion, used when a story has no external URL. */
    val discussionUrl: String
        get() = "https://news.ycombinator.com/item?id=$id"

    /** The link to actually open: the article if there is one, otherwise the HN thread. */
    val openUrl: String
        get() = url.ifBlank { discussionUrl }

    /**
     * Compact age of the story, e.g. "just now", "42m ago", "6h ago", "2d ago".
     *
     * Returns an empty string when [createdAtSeconds] is unknown, so callers can simply omit the
     * label rather than render something misleading like "56y ago".
     */
    fun relativeAge(nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        if (createdAtSeconds <= 0L) return ""
        val delta = nowSeconds - createdAtSeconds
        // Clock skew between the device and the API can make a fresh story look like the future.
        if (delta < 60) return "just now"
        val minutes = delta / 60
        if (minutes < 60) return "${minutes}m ago"
        val hours = minutes / 60
        if (hours < 24) return "${hours}h ago"
        val days = hours / 24
        if (days < 7) return "${days}d ago"
        return "${days / 7}w ago"
    }
}

/**
 * Fetches and ranks Hacker News stories from the public Algolia HN Search API.
 *
 * No API key is required and there is no auth, which is why this can populate the home screen
 * before the user has configured anything.
 *
 * The ranking deliberately favours the user's stated interests over raw popularity: a 400-point
 * story about Anthropic should outrank a 900-point story about an unrelated topic, because the
 * whole point of this screen is relevance rather than a plain HN mirror.
 */
class HackerNewsService {

    companion object {
        private const val TAG = "HackerNews"
        private const val BASE = "https://hn.algolia.com/api/v1"

        /**
         * The user's standing interests. Each entry is a display label plus the query terms that
         * signal it. Terms are matched case-insensitively against story titles.
         */
        val INTERESTS: List<Interest> = listOf(
            Interest("GenAI", listOf("genai", "generative ai", "llm", "ai model", "diffusion")),
            Interest("OpenAI", listOf("openai", "chatgpt", "gpt-4", "gpt-5", "sora", "sam altman")),
            Interest("Gemini", listOf("gemini", "deepmind", "bard")),
            Interest("Google", listOf("google", "alphabet", "android", "chrome")),
            Interest("Anthropic", listOf("anthropic", "claude")),
            Interest("India Tech", listOf("india", "bengaluru", "bangalore", "upi", "indian")),
            Interest("AI", listOf("artificial intelligence", "machine learning", "neural", "agent"))
        )

        /**
         * Search terms sent to the API, one request each, run in parallel.
         *
         * Keep these single-word where possible: Algolia ANDs the words in a query, so "india tech"
         * matches almost nothing while "india" matches plenty.
         */
        private val QUERY_TERMS = listOf(
            "openai", "anthropic claude", "gemini deepmind", "generative ai", "india", "llm"
        )

        const val MAX_HEADLINES = 10

        /** Interest hit dominates the ranking; see the class doc for why. */
        private const val SCORE_PER_INTEREST = 600
        private const val SCORE_FRONT_PAGE = 250

        /**
         * Cap on stories sharing a primary interest, so one busy topic cannot fill the whole
         * screen. Without this a big OpenAI news day crowds out everything else.
         */
        private const val MAX_PER_INTEREST = 3
    }

    data class Interest(val label: String, val terms: List<String>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Returns up to [MAX_HEADLINES] stories, interest-matched first, then whatever is currently
     * burning on the HN front page.
     *
     * Individual request failures are tolerated: as long as one call succeeds the screen still
     * renders. Only a total failure throws.
     */
    suspend fun fetchTopHeadlines(): List<HeadlineItem> = withContext(Dispatchers.IO) {
        val collected = LinkedHashMap<String, HeadlineItem>()

        coroutineScope {
            val frontPage = async { runCatching { fetch("$BASE/search?tags=front_page&hitsPerPage=30") }.getOrDefault(emptyList()) }
            val searches = QUERY_TERMS.map { term ->
                async {
                    val encoded = URLEncoder.encode(term, "UTF-8")
                    // Last 14 days keeps the feed current without starving niche topics.
                    val since = (System.currentTimeMillis() / 1000) - (14L * 24 * 60 * 60)
                    runCatching {
                        fetch("$BASE/search?query=$encoded&tags=story&numericFilters=created_at_i>$since,points>20&hitsPerPage=15")
                    }.getOrDefault(emptyList())
                }
            }

            frontPage.await().forEach { item ->
                collected[item.id] = item.copy(isFrontPage = true)
            }
            searches.forEach { deferred ->
                deferred.await().forEach { item ->
                    // Preserve the front-page flag if we already saw this story there.
                    val existing = collected[item.id]
                    collected[item.id] = item.copy(isFrontPage = existing?.isFrontPage ?: false)
                }
            }
        }

        if (collected.isEmpty()) {
            throw IllegalStateException("Hacker News returned no stories. Check your connection.")
        }

        val ranked = collected.values
            .map { it.copy(matchedInterests = matchInterests(it.title)) }
            .sortedByDescending { score(it) }

        selectDiverse(ranked).also {
            Log.d(TAG, "Ranked ${collected.size} stories down to ${it.size}")
        }
    }

    /**
     * Takes the highest-scoring stories while allowing at most [MAX_PER_INTEREST] per primary
     * interest, then backfills by score if that left us short of [MAX_HEADLINES].
     */
    private fun selectDiverse(ranked: List<HeadlineItem>): List<HeadlineItem> {
        val picked = LinkedHashSet<HeadlineItem>()
        val perInterest = mutableMapOf<String, Int>()

        for (item in ranked) {
            if (picked.size >= MAX_HEADLINES) break
            val primary = item.matchedInterests.firstOrNull() ?: "Burning"
            val used = perInterest.getOrDefault(primary, 0)
            if (used < MAX_PER_INTEREST) {
                picked.add(item)
                perInterest[primary] = used + 1
            }
        }

        // Backfill: a narrow news day should still produce a full screen.
        if (picked.size < MAX_HEADLINES) {
            for (item in ranked) {
                if (picked.size >= MAX_HEADLINES) break
                picked.add(item)
            }
        }
        return picked.toList()
    }

    /** Which interest labels appear in this title. */
    private fun matchInterests(title: String): List<String> {
        val lower = title.lowercase()
        return INTERESTS.filter { interest ->
            interest.terms.any { lower.contains(it) }
        }.map { it.label }
    }

    private fun score(item: HeadlineItem): Int {
        var s = item.points
        s += item.matchedInterests.size * SCORE_PER_INTEREST
        if (item.isFrontPage) s += SCORE_FRONT_PAGE
        return s
    }

    private fun fetch(url: String): List<HeadlineItem> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NammaOmnibrief/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Hacker News request failed (${response.code})")
            }
            val hits = JSONObject(body).optJSONArray("hits") ?: return emptyList()
            val out = ArrayList<HeadlineItem>(hits.length())
            for (i in 0 until hits.length()) {
                val h = hits.optJSONObject(i) ?: continue
                val title = h.optString("title").ifBlank { h.optString("story_title") }
                val id = h.optString("objectID")
                if (title.isBlank() || id.isBlank()) continue
                out.add(
                    HeadlineItem(
                        id = id,
                        title = title,
                        url = h.optString("url").takeIf { it.isNotBlank() && it != "null" } ?: "",
                        points = h.optInt("points"),
                        commentCount = h.optInt("num_comments"),
                        author = h.optString("author"),
                        createdAt = h.optString("created_at"),
                        createdAtSeconds = h.optLong("created_at_i", 0L)
                    )
                )
            }
            return out
        }
    }
}
