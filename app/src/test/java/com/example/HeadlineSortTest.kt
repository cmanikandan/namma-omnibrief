package com.example

import com.example.data.remote.HeadlineItem
import com.example.data.remote.HeadlineSort
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the Today screen's sort control.
 *
 * The ordering lives on [HeadlineSort.apply] rather than inside the ViewModel's `combine` precisely
 * so it can be exercised here without spinning up a ViewModel, a coroutine scope or the network.
 */
class HeadlineSortTest {

  private fun story(id: String, ageSeconds: Long) =
    HeadlineItem(
      id = id,
      title = "Story $id",
      url = "https://example.com/$id",
      points = 0,
      commentCount = 0,
      author = "someone",
      createdAt = "",
      createdAtSeconds = ageSeconds
    )

  @Test
  fun `newest orders by post time, most recent first`() {
    val relevanceOrder = listOf(story("a", 100), story("b", 300), story("c", 200))

    val sorted = HeadlineSort.NEWEST.apply(relevanceOrder)

    assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
  }

  @Test
  fun `for you leaves the service ranking alone`() {
    // The relevance ranking is the product of the interest matching upstream. Re-sorting it here
    // would silently throw that work away.
    val relevanceOrder = listOf(story("a", 100), story("b", 300), story("c", 200))

    val sorted = HeadlineSort.FOR_YOU.apply(relevanceOrder)

    assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
  }

  @Test
  fun `sorting is stable for stories posted in the same second`() {
    // Hacker News timestamps are whole seconds, so ties are common. An unstable sort would let the
    // list reshuffle itself on every recomposition, which reads as the app glitching.
    val tied = listOf(story("a", 500), story("b", 500), story("c", 500))

    val sorted = HeadlineSort.NEWEST.apply(tied)

    assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
  }

  @Test
  fun `stories with an unknown post time sink to the bottom rather than to the top`() {
    // createdAtSeconds defaults to 0 when the API omits it. Treating that as "very old" is the
    // safe reading; treating it as "brand new" would promote the least trustworthy entries.
    val mixed = listOf(story("unknown", 0), story("recent", 900))

    val sorted = HeadlineSort.NEWEST.apply(mixed)

    assertEquals(listOf("recent", "unknown"), sorted.map { it.id })
  }

  @Test
  fun `sorting an empty feed is a no-op rather than a crash`() {
    assertEquals(emptyList<HeadlineItem>(), HeadlineSort.NEWEST.apply(emptyList()))
  }

  @Test
  fun `the persisted id round-trips and unknown values fall back to the default`() {
    // The id is what lands in SharedPreferences, so a rename would silently reset everyone's
    // choice. fromId must also survive a value written by an older or newer build.
    HeadlineSort.entries.forEach { sort ->
      assertEquals(sort, HeadlineSort.fromId(sort.id))
    }
    assertEquals(HeadlineSort.FOR_YOU, HeadlineSort.fromId(null))
    assertEquals(HeadlineSort.FOR_YOU, HeadlineSort.fromId("not_a_sort"))
  }
}
