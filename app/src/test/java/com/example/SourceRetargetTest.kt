package com.example

import com.example.ui.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for [MainViewModel.retargetSource].
 *
 * The bug these exist for: correcting the source chip after analysis updated the chip and nothing
 * else, so a draft that said "The Times of India" was published saying "The Times of India" even
 * though the user had selected "The Economic Times".
 */
class SourceRetargetTest {

    private val draft = """
        Hiring mandates for AI adoption managers in India have risen 65% year-on-year.

        Key findings reported by The Times of India:
        - Mandates projected to rise to 2,600-2,700 in 2026.

        Source: The Times of India
    """.trimIndent()

    @Test
    fun `rewrites both the prose mention and the attribution line`() {
        val result = MainViewModel.retargetSource(
            text = draft,
            oldSource = "The Times of India",
            newSource = "The Economic Times"
        )

        assertEquals(
            "no mention of the old publication may survive",
            false,
            result.contains("Times of India")
        )
        assertEquals(
            "the attribution line is rewritten",
            true,
            result.contains("Source: The Economic Times")
        )
        assertEquals(
            "the mid-sentence mention is rewritten",
            true,
            result.contains("reported by The Economic Times:")
        )
    }

    @Test
    fun `leaves the rest of the draft untouched`() {
        val result = MainViewModel.retargetSource(draft, "The Times of India", "The Economic Times")

        assertEquals(true, result.contains("risen 65% year-on-year"))
        assertEquals(true, result.contains("2,600-2,700 in 2026"))
        assertEquals("line count is unchanged", draft.lines().size, result.lines().size)
    }

    @Test
    fun `handles the model dropping the leading The`() {
        // Detected as "The Times of India" but written mid-sentence without the article.
        val text = "Times of India reported the figure.\n\nSource: The Times of India"
        val result = MainViewModel.retargetSource(text, "The Times of India", "Deccan Herald")

        assertEquals("Deccan Herald reported the figure.\n\nSource: Deccan Herald", result)
    }

    @Test
    fun `rewrites the attribution line even when the old source is unknown`() {
        // draft.source can be blank — that is exactly when a plain find-and-replace does nothing.
        val text = "A summary.\n\nSource: Some Paper"
        val result = MainViewModel.retargetSource(text, "", "Financial Times")

        assertEquals("A summary.\n\nSource: Financial Times", result)
    }

    @Test
    fun `is case insensitive when matching the old source`() {
        val text = "per the economic times.\n\nSource: The Economic Times"
        val result = MainViewModel.retargetSource(text, "The Economic Times", "Mint")

        assertEquals("per Mint.\n\nSource: Mint", result)
    }

    @Test
    fun `normalises the attribution line when the source is unchanged`() {
        // Same source, but the line drifted. Should be tidied, not duplicated.
        val text = "A summary.\n\nSource: the economic times"
        val result = MainViewModel.retargetSource(text, "The Economic Times", "The Economic Times")

        assertEquals("A summary.\n\nSource: The Economic Times", result)
    }

    @Test
    fun `returns the text unchanged when the new source is blank`() {
        assertEquals(draft, MainViewModel.retargetSource(draft, "The Times of India", ""))
    }

    @Test
    fun `does not touch a Source colon appearing mid sentence`() {
        // The rewrite is anchored to the start of a line, so prose is safe.
        val text = "He said Source: is a word.\n\nSource: The Hindu"
        val result = MainViewModel.retargetSource(text, "The Hindu", "Mint")

        assertEquals("He said Source: is a word.\n\nSource: Mint", result)
    }
}
