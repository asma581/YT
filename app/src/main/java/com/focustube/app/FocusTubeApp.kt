package com.focustube.app

import android.app.Application
import android.content.Context
import com.focustube.app.data.SettingsRepository
import com.focustube.app.data.focusDataStore
import com.focustube.app.data.youtube.VideoRepository
import com.focustube.app.data.youtube.YouTubeApi
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class FocusTubeApp : Application() {
    /** Created once, shared by every screen and by the blocker service. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * "Manual dependency injection": one place that builds the objects the app needs.
 * Bigger apps often use Hilt for this, but the idea is the same.
 */
class AppContainer(context: Context) {

    val settingsRepository = SettingsRepository(context.focusDataStore)

    private val json = Json { ignoreUnknownKeys = true }

    private val youTubeApi: YouTubeApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/youtube/v3/")
        .client(OkHttpClient())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YouTubeApi::class.java)

    val videoRepository = VideoRepository(youTubeApi) {
        settingsRepository.settings.first().apiKeyOverride.ifBlank { BuildConfig.YOUTUBE_API_KEY }
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as FocusTubeApp).container
