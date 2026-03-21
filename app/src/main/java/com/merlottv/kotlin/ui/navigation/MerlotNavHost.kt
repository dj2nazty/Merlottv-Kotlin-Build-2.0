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
import com.merlottv.kotlin.ui.screens.channelbackup.ChannelBackupScreen
import com.merlottv.kotlin.ui.screens.spacex.SpaceXScreen
import com.merlottv.kotlin.ui.screens.account.AccountScreen
import com.merlottv.kotlin.ui.screens.weather.WeatherScreen
import com.merlottv.kotlin.ui.screens.tvguide.TvGuideScreen
import com.merlottv.kotlin.ui.screens.vod.VodScreen
import com.merlottv.kotlin.ui.screens.vod.VodDetailScreen
import com.merlottv.kotlin.ui.screens.vod.ActorDetailScreen
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
        enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(350)) },
        exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(350)) },
        popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) },
        popExitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) }
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
                },
                onPlatformTabClick = { tab ->
                    navController.navigate("${Screen.Vod.route}?platform=${tab.id}")
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

        composable(
            route = "${Screen.Vod.route}?platform={platform}",
            arguments = listOf(navArgument("platform") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            onLiveTvFullscreenChanged(false)
            val platformId = backStackEntry.arguments?.getString("platform") ?: ""
            VodScreen(
                onNavigateToDetail = { type, id ->
                    val encodedId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate(Screen.VodDetail.createRoute(type, encodedId))
                },
                initialPlatformId = platformId
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

        composable(Screen.ChannelBackup.route) {
            onLiveTvFullscreenChanged(false)
            ChannelBackupScreen(
                onStreamSelected = { streamUrl, channelName ->
                    val encodedUrl = URLEncoder.encode(streamUrl, "UTF-8")
                    val encodedTitle = URLEncoder.encode(channelName, "UTF-8")
                    navController.navigate(
                        Screen.Player.createRoute(
                            url = encodedUrl,
                            title = encodedTitle,
                            contentType = "tv"
                        )
                    )
                }
            )
        }

        composable(Screen.SpaceX.route) {
            onLiveTvFullscreenChanged(false)
            SpaceXScreen()
        }

        composable(Screen.Weather.route) {
            onLiveTvFullscreenChanged(false)
            WeatherScreen()
        }

        composable(Screen.Account.route) {
            onLiveTvFullscreenChanged(false)
            AccountScreen()
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
                },
                onNavigateToActor = { personId, personName ->
                    navController.navigate(Screen.ActorDetail.createRoute(personId, personName))
                }
            )
        }

        composable(
            route = Screen.ActorDetail.route,
            arguments = listOf(
                navArgument("personId") { type = NavType.IntType },
                navArgument("personName") { type = NavType.StringType }
            )
        ) {
            onLiveTvFullscreenChanged(false)
            ActorDetailScreen(
                onBack = { navController.popBackStack() },
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
