package com.focustube.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLinksTest {

    private val id = "dQw4w9WgXcQ"

    @Test
    fun `extracts the video id from every common link shape`() {
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?v=$id&t=42s",
            "https://www.youtube.com/watch?feature=share&v=$id",
            "youtube.com/watch?v=$id",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube-nocookie.com/embed/$id",
            "https://www.youtube.com/live/$id",
            "https://music.youtube.com/watch?v=$id",
            "Watch this lecture: https://youtu.be/$id",
        ).forEach { link -> assertEquals(link, id, YouTubeLinks.extractVideoId(link)) }
    }

    @Test
    fun `returns null when there is no video`() {
        assertNull(YouTubeLinks.extractVideoId(null))
        assertNull(YouTubeLinks.extractVideoId("https://www.youtube.com/@3blue1brown"))
        assertNull(YouTubeLinks.extractVideoId("https://youtu.be/tooShort"))
        assertNull(YouTubeLinks.extractVideoId("https://youtu.be/${id}EXTRA"))
        assertNull(YouTubeLinks.extractVideoId("https://example.com/watch?v=$id"))
    }

    @Test
    fun `recognises youtube urls in a browser address bar`() {
        assertTrue(YouTubeLinks.isYouTubeUrl("youtube.com"))
        assertTrue(YouTubeLinks.isYouTubeUrl("m.youtube.com/watch?v=$id"))
        assertTrue(YouTubeLinks.isYouTubeUrl("https://www.youtube.com/"))
        assertTrue(YouTubeLinks.isYouTubeUrl(" youtu.be/$id "))
        assertTrue(YouTubeLinks.isYouTubeUrl("YOUTUBE.COM/feed/trending"))
    }

    @Test
    fun `does not flag other sites or search text`() {
        assertFalse(YouTubeLinks.isYouTubeUrl(null))
        assertFalse(YouTubeLinks.isYouTubeUrl("notyoutube.com"))
        assertFalse(YouTubeLinks.isYouTubeUrl("youtube.com.evil.example"))
        assertFalse(YouTubeLinks.isYouTubeUrl("how to learn youtube editing"))
        assertFalse(YouTubeLinks.isYouTubeUrl("en.wikipedia.org/wiki/YouTube"))
    }
}
