package com.focustube.app.data.youtube

import com.focustube.app.domain.Durations
import com.focustube.app.domain.VideoInfo
import retrofit2.HttpException
import java.io.IOException

data class SearchPage(val videos: List<VideoInfo>, val nextPageToken: String?)

class MissingApiKeyException : Exception("No YouTube API key set")

/**
 * Talks to YouTube and turns its JSON into [VideoInfo]s.
 * It does *not* decide what is educational - that's ContentFilter's job.
 */
class VideoRepository(
    private val api: YouTubeApi,
    private val apiKey: suspend () -> String,
) {

    /** Search results only carry IDs, so we fetch the details (category, duration...) in a second call. */
    suspend fun search(query: String, pageToken: String? = null): SearchPage {
        val key = requireKey()
        val result = api.search(query = query, apiKey = key, pageToken = pageToken)
        val ids = result.items.mapNotNull { it.id.videoId }.distinct()
        return SearchPage(videos = details(ids, key), nextPageToken = result.nextPageToken)
    }

    suspend fun video(id: String): VideoInfo? = details(listOf(id), requireKey()).firstOrNull()

    private suspend fun details(ids: List<String>, key: String): List<VideoInfo> {
        if (ids.isEmpty()) return emptyList()
        val byId = ids.chunked(50)
            .flatMap { chunk -> api.videos(ids = chunk.joinToString(","), apiKey = key).items }
            .associateBy { it.id }
        // Keep YouTube's relevance order from the search.
        return ids.mapNotNull { byId[it]?.toVideoInfo() }
    }

    private suspend fun requireKey(): String = apiKey().ifBlank { throw MissingApiKeyException() }

    private fun VideoItem.toVideoInfo(): VideoInfo {
        val snippet = snippet ?: Snippet()
        return VideoInfo(
            id = id,
            title = snippet.title,
            description = snippet.description,
            channelId = snippet.channelId,
            channelTitle = snippet.channelTitle,
            categoryId = snippet.categoryId,
            tags = snippet.tags,
            durationSeconds = Durations.parseSeconds(contentDetails?.duration),
            thumbnailUrl = snippet.thumbnails?.let { it.medium ?: it.high ?: it.small }?.url,
            isLive = snippet.liveBroadcastContent == "live",
        )
    }

    companion object {
        /** Turns an exception from this repository into a message for the user. */
        fun describe(error: Throwable): String = when (error) {
            is MissingApiKeyException ->
                "Add your YouTube Data API key in Settings (see the README for how to get one)."
            is HttpException -> when (error.code()) {
                400 -> "YouTube rejected the request. Check that your API key is correct."
                403 -> "YouTube refused the request. Your API key may be wrong, restricted, or out of daily quota."
                else -> "YouTube returned an error (${error.code()}). Try again later."
            }
            is IOException -> "Couldn't reach YouTube. Check your internet connection."
            else -> "Something went wrong: ${error.message}"
        }
    }
}
