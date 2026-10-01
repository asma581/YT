package com.focustube.app.domain

/** Understands the many shapes of YouTube links. */
object YouTubeLinks {

    private const val ID = "([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])"

    private val VIDEO_LINK_PATTERNS = listOf(
        // youtube.com/watch?v=ID, /shorts/ID, /embed/ID, /live/ID, /v/ID (www., m., music. and -nocookie too)
        Regex(
            """(?:https?://)?(?:(?:www|m|music)\.)?youtube(?:-nocookie)?\.com/(?:watch\?(?:[^\s#]*&)?v=|shorts/|embed/|live/|v/)$ID""",
            RegexOption.IGNORE_CASE,
        ),
        // youtu.be/ID
        Regex("""(?:https?://)?youtu\.be/$ID""", RegexOption.IGNORE_CASE),
    )

    private val YOUTUBE_HOST = Regex(
        """^(?:https?://)?(?:[a-z0-9-]+\.)*(?:youtube\.com|youtu\.be|youtube-nocookie\.com)(?:[/:?#]|$)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Finds a video ID in a link, or in shared text that contains a link
     * ("Check this out https://youtu.be/abc123def45").
     */
    fun extractVideoId(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return VIDEO_LINK_PATTERNS.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
    }

    /** True if [urlBarText] (what a browser shows in its address bar) is a YouTube page. */
    fun isYouTubeUrl(urlBarText: String?): Boolean {
        if (urlBarText.isNullOrBlank()) return false
        return YOUTUBE_HOST.containsMatchIn(urlBarText.trim())
    }
}
