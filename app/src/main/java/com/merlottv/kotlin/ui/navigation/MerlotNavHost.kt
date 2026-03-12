package com.merlottv.kotlin.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merlottv.kotlin.ui.screens.favorites.FavoritesScreen
import com.merlottv.kotlin.ui.screens.home.HomeScreen
import com.merlottv.kotlin.ui.screens.livetv.LiveTvScreen
import com.merlottv.kotlin.ui.screens.player.PlayerScreen
import com.merlottv.kotlin.ui.screens.search.SearchScreen
import com.merlottv.kotlin.ui.screens.settings.SettingsScreen
import com.merlottv.kotlin.ui.screens.tvguide.TvGuideScreen
import com.merlottv.kotlin.ui.screens.vod.VodScreen
import com.merlottv.kotlin.ui.screens.vod.VodDetailScreen

@Composable
fun MerlotNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) },
        exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDetail = { type, id ->
                    navController.navigate(Screen.VodDetail.createRoute(type, id))
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateToDetail = { type, id ->
                    navController.navigate(Screen.VodDetail.createRoute(type, id))
                }
            )
        }

        composable(Screen.LiveTv.route) {
            LiveTvScreen()
        }

        composable(Screen.TvGuide.route) {
            TvGuideScreen()
        }

        composable(Screen.Vod.route) {
            VodScreen(
                onNavigateToDetail = { type, id ->
                    navController.navigate(Screen.VodDetail.createRoute(type, id))
                }
            )
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onNavigateToDetail = { type, id ->
                    navController.navigate(Screen.VodDetail.createRoute(type, id))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(
            route = Screen.VodDetail.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "movie"
            val id = backStackEntry.arguments?.getString("id") ?: ""
            VodDetailScreen(
                type = type,
                id = id,
                onBack = { navController.popBackStack() },
                onPlay = { streamUrl ->
                    navController.navigate(Screen.Player.route)
                }
            )
        }

        composable(Screen.Player.route) {
            PlayerScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
