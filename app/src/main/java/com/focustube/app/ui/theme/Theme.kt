package com.focustube.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// A calm teal palette: focus apps should feel quiet, not loud like YouTube red.
private val Teal = Color(0xFF00696B)
private val TealLight = Color(0xFF6FF6F8)
private val TealContainer = Color(0xFF9CF1F2)
private val TealContainerDark = Color(0xFF004F51)
private val Amber = Color(0xFF7A5900)
private val AmberLight = Color(0xFFF8BD2A)

private val LightColors = lightColorScheme(
    primary = Teal,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF002020),
    secondary = Amber,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealContainer,
    secondary = AmberLight,
)

/**
 * On Android 12+ we use "Material You" dynamic colours taken from the user's wallpaper;
 * older phones get the teal palette above.
 */
@Composable
fun FocusTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
