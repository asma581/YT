package com.focustube.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.focustube.app.R
import com.focustube.app.ui.focus.FocusScreen
import com.focustube.app.ui.learn.LearnScreen
import com.focustube.app.ui.player.PlayerScreen
import com.focustube.app.ui.settings.SettingsScreen

/** The three tabs in the bottom bar. */
private enum class TopLevelRoute(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    FOCUS("focus", R.string.tab_focus, Icons.Filled.Lock),
    LEARN("learn", R.string.tab_learn, Icons.Filled.Search),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings),
}

object Routes {
    const val VIDEO_ID_ARG = "videoId"
    const val PLAYER = "player/{$VIDEO_ID_ARG}"
    fun player(videoId: String) = "player/$videoId"
}

@Composable
fun FocusTubeAppUi(pendingVideoId: String?, onPendingVideoHandled: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // A YouTube link was shared into the app: open it in the player.
    LaunchedEffect(pendingVideoId) {
        if (pendingVideoId != null) {
            navController.navigate(Routes.player(pendingVideoId))
            onPendingVideoHandled()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelRoute.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                // Standard bottom-navigation behaviour: one copy of each tab, state restored.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelRoute.FOCUS.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelRoute.FOCUS.route) {
                FocusScreen(onFindSomethingToLearn = { navController.navigate(TopLevelRoute.LEARN.route) })
            }
            composable(TopLevelRoute.LEARN.route) {
                LearnScreen(onVideoClick = { id -> navController.navigate(Routes.player(id)) })
            }
            composable(TopLevelRoute.SETTINGS.route) {
                SettingsScreen()
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument(Routes.VIDEO_ID_ARG) { type = NavType.StringType }),
            ) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
