package com.merlottv.kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.merlottv.kotlin.ui.components.SidebarNavigation
import com.merlottv.kotlin.ui.navigation.MerlotNavHost
import com.merlottv.kotlin.ui.navigation.Screen
import com.merlottv.kotlin.ui.theme.MerlotColors
import com.merlottv.kotlin.ui.theme.MerlotTVTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MerlotTVTheme {
                MerlotApp()
            }
        }
    }
}

@Composable
fun MerlotApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var isLiveTvFullscreen by remember { mutableStateOf(false) }
    var sidebarVisible by remember { mutableStateOf(true) }
    val sidebarFocusRequester = remember { FocusRequester() }

    // Hide sidebar on player screen or when live TV is fullscreen
    val showSidebar = currentRoute != Screen.Player.route && !isLiveTvFullscreen

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // D-pad Left at the content edge → show sidebar and give it focus
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionLeft &&
                        showSidebar && !sidebarVisible
                    ) {
                        sidebarVisible = true
                        try { sidebarFocusRequester.requestFocus() } catch (_: Exception) {}
                        true
                    } else {
                        false
                    }
                }
        ) {
            if (showSidebar && sidebarVisible) {
                SidebarNavigation(
                    currentRoute = currentRoute,
                    focusRequester = sidebarFocusRequester,
                    onNavigate = { screen ->
                        sidebarVisible = false
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            MerlotNavHost(
                navController = navController,
                modifier = Modifier.weight(1f),
                onLiveTvFullscreenChanged = { isLiveTvFullscreen = it }
            )
        }
    }
}
