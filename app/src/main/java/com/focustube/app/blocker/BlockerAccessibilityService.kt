package com.focustube.app.blocker

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.focustube.app.appContainer
import com.focustube.app.data.FocusSettings
import com.focustube.app.domain.YouTubeLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches which app is on screen. During focus time it sends the user away from the
 * YouTube app (and from youtube.com in common browsers) to [BlockedActivity].
 *
 * Android only runs this after the user turns it on in Settings > Accessibility.
 * We only listen to the packages in [BlockTargets], and we never store or send
 * anything we see.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Latest settings, kept up to date by collecting the DataStore flow. */
    @Volatile private var settings: FocusSettings? = null

    private var lastBlockAt = 0L
    private var lastBrowserCheckAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Limit the events we receive to the apps we care about.
        serviceInfo = serviceInfo.apply { packageNames = BlockTargets.allPackages.toTypedArray() }
        scope.launch {
            appContainer.settingsRepository.settings.collect { settings = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val current = settings ?: return
        if (!current.focusStatus().isActive) return
        val pkg = event.packageName?.toString() ?: return

        when {
            pkg in BlockTargets.youTubeApps -> block()
            pkg in BlockTargets.browserUrlBars -> {
                // Content-changed events fire constantly while a page loads; check at most twice a second.
                val now = SystemClock.elapsedRealtime()
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    now - lastBrowserCheckAt < BROWSER_CHECK_INTERVAL_MS
                ) {
                    return
                }
                lastBrowserCheckAt = now
                if (YouTubeLinks.isYouTubeUrl(readBrowserUrl(pkg))) block()
            }
        }
    }

    private fun readBrowserUrl(pkg: String): String? {
        val root = rootInActiveWindow ?: return null
        val urlBarId = BlockTargets.browserUrlBars[pkg] ?: return null
        return root.findAccessibilityNodeInfosByViewId(urlBarId).firstNotNullOfOrNull { it.text?.toString() }
    }

    private fun block() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < BLOCK_DEBOUNCE_MS) return
        lastBlockAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val BLOCK_DEBOUNCE_MS = 1_500L
        private const val BROWSER_CHECK_INTERVAL_MS = 500L

        /** True if the user has switched this service on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, BlockerAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun openSettingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** The apps and websites FocusTube blocks during focus time. */
object BlockTargets {

    val youTubeApps = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "app.revanced.android.youtube",
        "app.rvx.android.youtube",
        "com.vanced.android.youtube",
        "org.schabi.newpipe",
        "com.github.libretube",
    )

    /** Browser package -> the view ID of its address bar, so we can read the current URL. */
    val browserUrlBars = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.chrome.beta" to "com.chrome.beta:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "com.opera.browser" to "com.opera.browser:id/url_field",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
    )

    val allPackages: Set<String> = youTubeApps + browserUrlBars.keys
}
