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
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun MerlotNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) },
        exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDetail = { type, id ->
                    val encodedId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate(Screen.VodDetail.createRoute(type, encodedId))
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateToDetail = { type, id ->
                    val encodedId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate(Screen.VodDetail.createRoute(type, encodedId))
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
                    val encodedId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate(Screen.VodDetail.createRoute(type, encodedId))
                }
            )
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onNavigateToDetail = { type, id ->
                    val encodedId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate(Screen.VodDetail.createRoute(type, encodedId))
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
            val id = URLDecoder.decode(backStackEntry.arguments?.getString("id") ?: "", "UTF-8")
            VodDetailScreen(
                type = type,
                id = id,
                onBack = { navController.popBackStack() },
                onPlay = { streamUrl, title ->
                    val encodedUrl = URLEncoder.encode(streamUrl, "UTF-8")
                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                    navController.navigate(Screen.Player.createRoute(encodedUrl, encodedTitle))
                }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val url = URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", "UTF-8")
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
            PlayerScreen(
                streamUrl = url,
                title = title,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
