package com.merlottv.kotlin.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Search : Screen("search", "Search", Icons.Default.Search)
    data object LiveTv : Screen("live_tv", "Live TV", Icons.Default.LiveTv)
    data object TvGuide : Screen("tv_guide", "TV Guide", Icons.Default.Tv)
    data object Vod : Screen("vod", "VOD", Icons.Default.Movie)
    data object Favorites : Screen("favorites", "Favorites", Icons.Default.Favorite)
    data object ChannelBackup : Screen("channel_backup", "Channel Backup", Icons.Default.SettingsInputAntenna)
    data object SpaceX : Screen("spacex", "SpaceX", Icons.Default.Rocket)
    data object Weather : Screen("weather", "Weather", Icons.Default.Cloud)
    data object Account : Screen("account", "Account", Icons.Default.AccountCircle)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    // Profile picker
    data object ProfilePicker : Screen("profile_picker", "Profiles", Icons.Default.Person)

    // Detail routes
    data object VodDetail : Screen("vod_detail/{type}/{id}", "Detail", Icons.Default.Movie) {
        fun createRoute(type: String, id: String) = "vod_detail/$type/$id"
    }
    data object ActorDetail : Screen("actor_detail/{personId}/{personName}", "Actor", Icons.Default.Person) {
        fun createRoute(personId: Int, personName: String): String {
            val encodedName = java.net.URLEncoder.encode(personName, "UTF-8")
            return "actor_detail/$personId/$encodedName"
        }
    }

    data object Player : Screen("player/{url}?title={title}&contentId={contentId}&poster={poster}&contentType={contentType}", "Player", Icons.Default.LiveTv) {
        fun createRoute(
            url: String,
            title: String = "",
            contentId: String = "",
            poster: String = "",
            contentType: String = "movie"
        ) = "player/$url?title=$title&contentId=$contentId&poster=$poster&contentType=$contentType"
    }

    companion object {
        val sidebarItems = listOf(Home, Search, LiveTv, TvGuide, ChannelBackup, Vod, Favorites, SpaceX, Weather, Account, Settings)
    }
}
