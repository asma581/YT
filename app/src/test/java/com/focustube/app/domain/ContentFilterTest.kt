package com.focustube.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterTest {

    private val filter = ContentFilter(FilterRules())

    private fun video(
        title: String = "Linear algebra, lecture 1",
        categoryId: String? = YouTubeCategories.EDUCATION,
        durationSeconds: Long? = 1_800,
        channelId: String = "UC_teacher",
        tags: List<String> = emptyList(),
        description: String = "",
        isLive: Boolean = false,
    ) = VideoInfo(
        id = "abcdefghijk",
        title = title,
        description = description,
        channelId = channelId,
        categoryId = categoryId,
        tags = tags,
        durationSeconds = durationSeconds,
        isLive = isLive,
    )

    @Test
    fun `educational categories are allowed`() {
        assertTrue(filter.isAllowed(video(categoryId = YouTubeCategories.EDUCATION)))
        assertTrue(filter.isAllowed(video(categoryId = YouTubeCategories.SCIENCE_AND_TECHNOLOGY)))
        assertTrue(filter.isAllowed(video(categoryId = YouTubeCategories.HOWTO_AND_STYLE)))
    }

    @Test
    fun `entertainment categories are blocked with a readable reason`() {
        val decision = filter.evaluate(video(categoryId = "24"))
        assertEquals(FilterDecision.Blocked("\"Entertainment\" is not an allowed category"), decision)
        assertFalse(filter.isAllowed(video(categoryId = "20"))) // Gaming
        assertFalse(filter.isAllowed(video(categoryId = "23"))) // Comedy
    }

    @Test
    fun `missing category is blocked`() {
        assertFalse(filter.isAllowed(video(categoryId = null)))
    }

    @Test
    fun `blocked keywords match whole words only, ignoring case`() {
        assertFalse(filter.isAllowed(video(title = "Physics PRANK gone wrong")))
        assertFalse(filter.isAllowed(video(title = "Try not to laugh: chemistry edition")))
        assertTrue(filter.isAllowed(video(title = "Memento mori in Stoic philosophy"))) // "meme" inside a word
    }

    @Test
    fun `blocked keywords are found in tags`() {
        assertFalse(filter.isAllowed(video(tags = listOf("science", "gameplay"))))
    }

    @Test
    fun `shorts are blocked by duration or hashtag`() {
        assertFalse(filter.isAllowed(video(durationSeconds = 45)))
        assertFalse(filter.isAllowed(video(title = "Derivatives in 1 minute #shorts", durationSeconds = 90)))
        assertFalse(filter.isAllowed(video(description = "Quick tip #Short")))
        assertTrue(filter.isAllowed(video(description = "See #shortstack for more", durationSeconds = 600)))
    }

    @Test
    fun `live streams with zero duration are not treated as shorts`() {
        assertTrue(filter.isAllowed(video(durationSeconds = 0, isLive = true)))
    }

    @Test
    fun `shorts are allowed when the user turns that rule off`() {
        val lenient = ContentFilter(FilterRules(blockShorts = false))
        assertTrue(lenient.isAllowed(video(durationSeconds = 45)))
    }

    @Test
    fun `trusted channels skip category and keyword checks but not the shorts rule`() {
        val rules = FilterRules(trustedChannelIds = setOf("UC_trusted"))
        val trusting = ContentFilter(rules)
        assertTrue(trusting.isAllowed(video(channelId = "UC_trusted", categoryId = "22", title = "My study vlog")))
        assertFalse(trusting.isAllowed(video(channelId = "UC_trusted", durationSeconds = 30)))
    }

    @Test
    fun `custom keywords and categories are respected`() {
        val custom = ContentFilter(
            FilterRules(allowedCategoryIds = setOf("10"), blockedKeywords = setOf("  ", "music video")),
        )
        assertTrue(custom.isAllowed(video(categoryId = "10", title = "Music theory: scales")))
        assertFalse(custom.isAllowed(video(categoryId = "10", title = "Official Music Video")))
        assertFalse(custom.isAllowed(video(categoryId = YouTubeCategories.EDUCATION)))
    }
}
