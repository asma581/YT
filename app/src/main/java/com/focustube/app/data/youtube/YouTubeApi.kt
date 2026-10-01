package com.focustube.app.data.youtube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The two YouTube Data API v3 endpoints FocusTube uses.
 * Docs: https://developers.google.com/youtube/v3/docs
 *
 * Quota: search costs 100 units, videos costs 1 unit. A free key gets 10,000 units/day.
 */
interface YouTubeApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("pageToken") pageToken: String? = null,
        @Query("part") part: String = "id",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 50,
        @Query("safeSearch") safeSearch: String = "strict",
        @Query("videoEmbeddable") videoEmbeddable: Boolean = true,
    ): SearchResponse

    /** Full details (category, tags, duration) for up to 50 comma-separated video IDs. */
    @GET("videos")
    suspend fun videos(
        @Query("id") ids: String,
        @Query("key") apiKey: String,
        @Query("part") part: String = "snippet,contentDetails",
    ): VideoListResponse
}

@Serializable
data class SearchResponse(
    val nextPageToken: String? = null,
    val items: List<SearchItem> = emptyList(),
)

@Serializable
data class SearchItem(val id: SearchItemId)

@Serializable
data class SearchItemId(val videoId: String? = null)

@Serializable
data class VideoListResponse(val items: List<VideoItem> = emptyList())

@Serializable
data class VideoItem(
    val id: String,
    val snippet: Snippet? = null,
    val contentDetails: ContentDetails? = null,
)

@Serializable
data class Snippet(
    val title: String = "",
    val description: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    val categoryId: String? = null,
    val tags: List<String> = emptyList(),
    val thumbnails: Thumbnails? = null,
    /** "live", "upcoming" or "none". */
    val liveBroadcastContent: String? = null,
)

@Serializable
data class Thumbnails(
    @SerialName("default") val small: Thumbnail? = null,
    val medium: Thumbnail? = null,
    val high: Thumbnail? = null,
)

@Serializable
data class Thumbnail(val url: String)

@Serializable
data class ContentDetails(val duration: String? = null)
