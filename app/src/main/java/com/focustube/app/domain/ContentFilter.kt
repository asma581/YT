package com.focustube.app.domain

/**
 * Everything the filter needs to know about one video.
 *
 * This is a plain Kotlin class with no Android or network types in it, so the
 * filtering rules can be unit-tested on any computer.
 */
data class VideoInfo(
    val id: String,
    val title: String,
    val description: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    val categoryId: String? = null,
    val tags: List<String> = emptyList(),
    val durationSeconds: Long? = null,
    val thumbnailUrl: String? = null,
    val isLive: Boolean = false,
)

data class TrustedChannel(val id: String, val title: String)

/** The user's rules. Built from saved settings, see FocusSettings.filterRules(). */
data class FilterRules(
    val allowedCategoryIds: Set<String> = YouTubeCategories.DEFAULT_ALLOWED,
    val blockedKeywords: Set<String> = DEFAULT_BLOCKED_KEYWORDS,
    val trustedChannelIds: Set<String> = emptySet(),
    val blockShorts: Boolean = true,
) {
    companion object {
        /** Words that almost always mean "entertainment" even inside an allowed category. */
        val DEFAULT_BLOCKED_KEYWORDS = setOf(
            "prank", "meme", "memes", "funny", "reaction", "vlog", "gameplay",
            "let's play", "mukbang", "challenge", "tiktok", "compilation", "try not to laugh",
        )
    }
}

sealed interface FilterDecision {
    data object Allowed : FilterDecision
    data class Blocked(val reason: String) : FilterDecision
}

/**
 * Decides whether a video is "educational" according to [rules].
 *
 * Order of the checks:
 * 1. Shorts are blocked (if enabled) - they are built for endless scrolling.
 * 2. Videos from a channel the user trusts are allowed.
 * 3. A blocked keyword in the title or tags blocks the video.
 * 4. Otherwise the video's YouTube category must be one of the allowed ones.
 */
class ContentFilter(private val rules: FilterRules) {

    private val keywordPatterns: List<Pair<String, Regex>> = rules.blockedKeywords
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { keyword -> keyword to wholeWord(keyword) }

    fun evaluate(video: VideoInfo): FilterDecision {
        if (rules.blockShorts && looksLikeShort(video)) {
            return FilterDecision.Blocked("YouTube Shorts are blocked")
        }
        if (video.channelId.isNotEmpty() && video.channelId in rules.trustedChannelIds) {
            return FilterDecision.Allowed
        }
        val searchable = buildString {
            append(video.title)
            video.tags.forEach { append('\n').append(it) }
        }
        keywordPatterns.firstOrNull { (_, regex) -> regex.containsMatchIn(searchable) }?.let { (keyword, _) ->
            return FilterDecision.Blocked("Contains the blocked word \"$keyword\"")
        }
        val category = video.categoryId
            ?: return FilterDecision.Blocked("YouTube didn't say what category this video is")
        if (category !in rules.allowedCategoryIds) {
            val name = YouTubeCategories.nameOf(category)
            return FilterDecision.Blocked("\"$name\" is not an allowed category")
        }
        return FilterDecision.Allowed
    }

    fun isAllowed(video: VideoInfo): Boolean = evaluate(video) == FilterDecision.Allowed

    companion object {
        const val SHORTS_MAX_SECONDS = 60L

        fun looksLikeShort(video: VideoInfo): Boolean {
            val duration = video.durationSeconds
            // Live streams report a duration of 0, which doesn't make them Shorts.
            if (!video.isLive && duration != null && duration in 1..SHORTS_MAX_SECONDS) return true
            return SHORTS_TAG.containsMatchIn(video.title) || SHORTS_TAG.containsMatchIn(video.description)
        }

        private val SHORTS_TAG = Regex("#shorts?(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)

        /** Matches [keyword] only as a whole word: "meme" matches "a meme" but not "memento". */
        private fun wholeWord(keyword: String): Regex =
            Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(keyword) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }
}

/** YouTube's video category IDs (the same in nearly every region). */
object YouTubeCategories {
    const val HOWTO_AND_STYLE = "26"
    const val EDUCATION = "27"
    const val SCIENCE_AND_TECHNOLOGY = "28"

    val DEFAULT_ALLOWED = setOf(EDUCATION, SCIENCE_AND_TECHNOLOGY, HOWTO_AND_STYLE)

    /** Categories a user can pick from in Settings, in display order. */
    val ALL: Map<String, String> = linkedMapOf(
        EDUCATION to "Education",
        SCIENCE_AND_TECHNOLOGY to "Science & Technology",
        HOWTO_AND_STYLE to "Howto & Style",
        "29" to "Nonprofits & Activism",
        "25" to "News & Politics",
        "19" to "Travel & Events",
        "22" to "People & Blogs",
        "10" to "Music",
        "17" to "Sports",
        "1" to "Film & Animation",
        "2" to "Autos & Vehicles",
        "15" to "Pets & Animals",
        "20" to "Gaming",
        "23" to "Comedy",
        "24" to "Entertainment",
    )

    fun nameOf(id: String): String = ALL[id] ?: "Category $id"
}
