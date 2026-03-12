package com.merlottv.kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

    // Hide sidebar on player screen
    val showSidebar = currentRoute != Screen.Player.route

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        if (showSidebar) {
            SidebarNavigation(
                currentRoute = currentRoute,
                onNavigate = { screen ->
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
            modifier = Modifier.weight(1f)
        )
    }
}
