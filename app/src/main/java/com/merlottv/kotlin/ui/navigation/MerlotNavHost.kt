package com.merlottv.kotlin.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merlottv.kotlin.ui.screens.favorites.FavoritesScreen
import com.merlottv.kotlin.ui.screens.home.HomeScreen
import com.merlottv.kotlin.ui.screens.livetv.LiveTvScreen
import com.merlottv.kotlin.ui.screens.livetv.LiveTvViewModel
import com.merlottv.kotlin.ui.screens.player.PlayerScreen
import com.merlottv.kotlin.ui.screens.profiles.ProfilePickerScreen
import com.merlottv.kotlin.ui.screens.search.SearchScreen
import com.merlottv.kotlin.ui.screens.settings.SettingsScreen
import com.merlottv.kotlin.ui.screens.sports.GameDetailScreen
import com.merlottv.kotlin.ui.screens.spacex.SpaceXScreen
import com.merlottv.kotlin.ui.screens.sports.SportsScreen
import com.merlottv.kotlin.ui.screens.sports.TeamDetailScreen
import com.merlottv.kotlin.ui.screens.tvguide.TvGuideScreen
import com.merlottv.kotlin.ui.screens.vod.VodScreen
import com.merlottv.kotlin.ui.screens.vod.VodDetailScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun MerlotNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route,
    onLiveTvFullscreenChanged: (Boolean) -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) },
        exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) }
    ) {
        // Profile picker
        composable(Screen.ProfilePicker.route) {
            onLiveTvFullscreenChanged(false)
            ProfilePickerScreen(
                onProfileSelected = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ProfilePicker.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            onLiveTvFullscreenChanged(false)
            HomeScreen(
                onNavigateToDetail = { type, id ->
                    val encodedId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate(Screen.VodDetail.createRoute(type, encodedId))
                }
            )
        }

        composable(Screen.Search.route) {
            onLiveTvFullscreenChanged(false)
            SearchScreen(
                onNavigateToDetail = { type, id ->
                    val encodedId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate(Screen.VodDetail.createRoute(type, encodedId))
                }
            )
        }

        composable(Screen.LiveTv.route) {
            val viewModel: LiveTvViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            onLiveTvFullscreenChanged(uiState.isFullscreen)

            // Stop playback when leaving Live TV screen
            DisposableEffect(Unit) {
                viewModel.resumePlayback()
                onDispose {
                    viewModel.stopPlayback()
                }
            }

            LiveTvScreen(viewModel = viewModel)
        }

        composable(Screen.TvGuide.route) {
            onLiveTvFullscreenChanged(false)
            TvGuideScreen(
                onChannelSelected = {
                    // Navigate to Live TV — channel ID already saved to SettingsDataStore
                    // by TvGuideViewModel.saveChannelForPlayback() before this callback fires
                    navController.navigate(Screen.LiveTv.route) {
                        popUpTo(Screen.TvGuide.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Vod.route) {
            onLiveTvFullscreenChanged(false)
            VodScreen(
                onNavigateToDetail = { type, id ->
                    val encodedId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate(Screen.VodDetail.createRoute(type, encodedId))
                }
            )
        }

        composable(Screen.Favorites.route) {
            onLiveTvFullscreenChanged(false)
            FavoritesScreen(
                onNavigateToDetail = { type, id ->
                    val encodedId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate(Screen.VodDetail.createRoute(type, encodedId))
                }
            )
        }

        composable(Screen.Sports.route) {
            onLiveTvFullscreenChanged(false)
            SportsScreen(
                onNavigateToGame = { league, eventId ->
                    navController.navigate(Screen.GameDetail.createRoute(league, eventId))
                },
                onNavigateToTeam = { league, teamId ->
                    navController.navigate(Screen.TeamDetail.createRoute(league, teamId))
                }
            )
        }

        composable(
            route = Screen.GameDetail.route,
            arguments = listOf(
                navArgument("league") { type = NavType.StringType },
                navArgument("eventId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            onLiveTvFullscreenChanged(false)
            val league = backStackEntry.arguments?.getString("league") ?: "nfl"
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            GameDetailScreen(
                league = league,
                eventId = eventId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.TeamDetail.route,
            arguments = listOf(
                navArgument("league") { type = NavType.StringType },
                navArgument("teamId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            onLiveTvFullscreenChanged(false)
            val league = backStackEntry.arguments?.getString("league") ?: "nfl"
            val teamId = backStackEntry.arguments?.getString("teamId") ?: ""
            TeamDetailScreen(
                league = league,
                teamId = teamId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SpaceX.route) {
            onLiveTvFullscreenChanged(false)
            SpaceXScreen()
        }

        composable(Screen.Settings.route) {
            onLiveTvFullscreenChanged(false)
            SettingsScreen()
        }

        composable(
            route = Screen.VodDetail.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            onLiveTvFullscreenChanged(false)
            val type = backStackEntry.arguments?.getString("type") ?: "movie"
            val id = URLDecoder.decode(backStackEntry.arguments?.getString("id") ?: "", "UTF-8")
            VodDetailScreen(
                type = type,
                id = id,
                onBack = { navController.popBackStack() },
                onPlay = { streamUrl, title, contentId, poster, contentType ->
                    val encodedUrl = URLEncoder.encode(streamUrl, "UTF-8")
                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                    val encodedContentId = URLEncoder.encode(contentId, "UTF-8")
                    val encodedPoster = URLEncoder.encode(poster, "UTF-8")
                    navController.navigate(
                        Screen.Player.createRoute(
                            url = encodedUrl,
                            title = encodedTitle,
                            contentId = encodedContentId,
                            poster = encodedPoster,
                            contentType = contentType
                        )
                    )
                },
                onNavigateToDetail = { detailType, detailId ->
                    val encodedId = URLEncoder.encode(detailId, "UTF-8")
                    navController.navigate(Screen.VodDetail.createRoute(detailType, encodedId))
                }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("contentId") { type = NavType.StringType; defaultValue = "" },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                navArgument("contentType") { type = NavType.StringType; defaultValue = "movie" }
            )
        ) { backStackEntry ->
            onLiveTvFullscreenChanged(false)
            val url = URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", "UTF-8")
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
            val contentId = URLDecoder.decode(backStackEntry.arguments?.getString("contentId") ?: "", "UTF-8")
            val poster = URLDecoder.decode(backStackEntry.arguments?.getString("poster") ?: "", "UTF-8")
            val contentType = backStackEntry.arguments?.getString("contentType") ?: "movie"
            PlayerScreen(
                streamUrl = url,
                title = title,
                contentId = contentId,
                poster = poster,
                contentType = contentType,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
