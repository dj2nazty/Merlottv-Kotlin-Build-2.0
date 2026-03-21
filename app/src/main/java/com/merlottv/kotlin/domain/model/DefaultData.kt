package com.merlottv.kotlin.domain.model

object DefaultData {
    const val PLAYLIST_URL = "https://x-api.uk/get.php?username=MetrlotBackup&password=2813308004&type=m3u_plus"

    val EPG_SOURCES = listOf(
        EpgSource("Merlot TV EPG", "https://x-api.uk/xmltv.php?username=MetrlotBackup&password=2813308004"),
        EpgSource("EPG.pw US", "https://epg.pw/xmltv/epg_US.xml"),
        EpgSource("PlutoTV US", "https://i.mjh.nz/PlutoTV/us.xml"),
        EpgSource("EPG.pw UK", "https://epg.pw/xmltv/epg_UK.xml")
    )

    // OpenSubtitles Stremio addon URL (hardcoded)
    const val OPENSUBTITLES_ADDON_URL = "https://opensubtitles-v3.strem.io"

    val DEFAULT_ADDONS = listOf(
        // Cinemeta — meta-only provider (no catalogs).
        // Provides /meta/movie/ and /meta/series/ with full episode data.
        // Essential for TV show detail screens (episode lists, descriptions, etc.)
        Addon(
            id = "cinemeta",
            name = "Cinemeta",
            url = "https://v3-cinemeta.strem.io/manifest.json",
            isDefault = true,
            resources = listOf("meta"),
            types = listOf("movie", "series")
        ),
        Addon(
            id = "opensubtitles-v3",
            name = "OpenSubtitles v3",
            url = "https://opensubtitles-v3.strem.io/manifest.json",
            isDefault = true,
            resources = listOf("subtitles"),
            types = listOf("movie", "series")
        ),
        Addon(
            id = "netflix-catalog",
            name = "Netflix Catalog",
            url = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhbXAsYXRwLGhibSxwbXAscGNwLGRwZTo6VVM6MTc3MjcyODk3MDc1NDowOjA6VVM%3D/manifest.json",
            isDefault = true,
            isNetflix = true
        ),
        Addon(
            id = "torbox-stremio",
            name = "Torbox Stremio",
            url = "https://stremio.torbox.app/50c74a49-a6bc-40e9-931e-1cee1943e87b/manifest.json",
            isDefault = true
        ),
        Addon(
            id = "torrentio",
            name = "Torrentio",
            url = "https://torrentio.strem.fun/manifest.json",
            isDefault = true
        ),
        Addon(
            id = "imdb-catalogs",
            name = "IMDB Catalogs",
            url = "https://1fe84bc728af-imdb-catalogs.baby-beamup.club/manifest.json",
            isDefault = true
        ),
        Addon(
            id = "com.merlottv.tmdb",
            name = "MerlotTV+",
            url = "https://merlottv-addon.onrender.com/manifest.json",
            catalogs = listOf(
                // Combined catalogs (TMDB + Cinemeta merged, deduped)
                AddonCatalog(id = "merlot.popular_movies", name = "Popular Movies", type = "movie"),
                AddonCatalog(id = "merlot.popular_series", name = "Popular Series", type = "series"),
                AddonCatalog(id = "merlot.new_movies", name = "New Movies", type = "movie"),
                AddonCatalog(id = "merlot.new_series", name = "New Series", type = "series"),
                AddonCatalog(id = "merlot.featured_movies", name = "Featured Movies", type = "movie"),
                AddonCatalog(id = "merlot.featured_series", name = "Featured Series", type = "series"),
                // Original MerlotTV+ catalogs
                AddonCatalog(id = "merlot.upcoming", name = "Upcoming Movies", type = "movie"),
                AddonCatalog(id = "merlot.now_playing", name = "In Theaters Now", type = "movie"),
                AddonCatalog(id = "merlot.top_rated_movies", name = "Top Rated Movies", type = "movie"),
                AddonCatalog(id = "merlot.popular_new_tvshows", name = "Popular New TV Shows", type = "series"),
                AddonCatalog(id = "merlot.airing_today", name = "Airing Today", type = "series"),
                AddonCatalog(id = "merlot.on_the_air", name = "On The Air", type = "series"),
                AddonCatalog(id = "merlot.top_rated_series", name = "Top Rated Series", type = "series"),
                // Network catalogs
                AddonCatalog(id = "net.nbc", name = "NBC", type = "series"),
                AddonCatalog(id = "net.abc", name = "ABC", type = "series"),
                AddonCatalog(id = "net.cbs", name = "CBS", type = "series"),
                AddonCatalog(id = "net.fox", name = "FOX", type = "series"),
                AddonCatalog(id = "net.cw", name = "The CW", type = "series"),
                AddonCatalog(id = "net.hbo", name = "HBO", type = "series"),
                AddonCatalog(id = "net.showtime", name = "Showtime", type = "series"),
                AddonCatalog(id = "net.fx", name = "FX", type = "series"),
                AddonCatalog(id = "net.amc", name = "AMC", type = "series"),
                AddonCatalog(id = "net.usa", name = "USA Network", type = "series"),
                AddonCatalog(id = "net.bravo", name = "Bravo", type = "series"),
                AddonCatalog(id = "net.hgtv", name = "HGTV", type = "series"),
                AddonCatalog(id = "net.history", name = "History", type = "series"),
                AddonCatalog(id = "net.pbs", name = "PBS", type = "series")
            ),
            isDefault = true
        ),
        Addon(
            id = "comet",
            name = "Comet",
            url = "https://comet.feels.legal/eyJtYXhSZXN1bHRzUGVyUmVzb2x1dGlvbiI6MCwibWF4U2l6ZSI6MCwiY2FjaGVkT25seSI6ZmFsc2UsInNvcnRDYWNoZWRVbmNhY2hlZFRvZ2V0aGVyIjpmYWxzZSwicmVtb3ZlVHJhc2giOnRydWUsInJlc3VsdEZvcm1hdCI6WyJhbGwiXSwiZGVicmlkU2VydmljZXMiOlt7InNlcnZpY2UiOiJ0b3Jib3giLCJhcGlLZXkiOiI1MGM3NGE0OS1hNmJjLTQwZTktOTMxZS0xY2VlMTk0M2U4N2IifV0sImVuYWJsZVRvcnJlbnQiOnRydWUsImRlZHVwbGljYXRlU3RyZWFtcyI6ZmFsc2UsInNjcmFwZURlYnJpZEFjY291bnRUb3JyZW50cyI6ZmFsc2UsImRlYnJpZFN0cmVhbVByb3h5UGFzc3dvcmQiOiIiLCJsYW5ndWFnZXMiOnsicmVxdWlyZWQiOlsiZW4iXSwiYWxsb3dlZCI6WyJlbiJdLCJleGNsdWRlIjpbImphIiwiemgiLCJydSIsImFyIiwicHQiLCJlcyIsImZyIiwiZGUiLCJpdCIsImtvIiwiaGkiLCJibiIsInBhIiwibXIiLCJndSIsInRhIiwidGUiLCJrbiIsIm1sIiwidGgiLCJ2aSIsImlkIiwidHIiLCJoZSIsImZhIiwidWsiLCJlbCIsImx0IiwibHYiLCJldCIsInBsIiwiY3MiLCJzayIsImh1Iiwicm8iLCJiZyIsInNyIiwiaHIiLCJzbCIsIm5sIiwiZGEiLCJmaSIsInN2Iiwibm8iLCJtcyIsImxhIl0sInByZWZlcnJlZCI6WyJlbiJdfSwicmVzb2x1dGlvbnMiOnsicjU3NnAiOmZhbHNlLCJyNDgwcCI6ZmFsc2UsInIzNjBwIjpmYWxzZSwicjI0MHAiOmZhbHNlLCJ1bmtub3duIjpmYWxzZX0sIm9wdGlvbnMiOnsicmVtb3ZlX3JhbmtzX3VuZGVyIjotMTAwMDAwMDAwMDAsImFsbG93X2VuZ2xpc2hfaW5fbGFuZ3VhZ2VzIjpmYWxzZSwicmVtb3ZlX3Vua25vd25fbGFuZ3VhZ2VzIjpmYWxzZX19/manifest.json",
            isDefault = true
        ),
        Addon(
            id = "mediafusion",
            name = "Fusion",
            url = "https://mediafusion.elfhosted.com/D-zRJlbm56oO-Xoyd5NuONYDCRilVv-jvkthMtkVIjxcMFWqiP6UXwK69ulb6QT8sBIspMHBgZuoI78Yb32IqPSFRwVlG-mAMwx5lnIbQSY82z4LpZ6RBS1haWtOzjlQ01jssCyrG_1xMI0LjY18Po-0K1MOnwEZUrXIFYJ6KDZDQEuJ29vfpPsnh97O8PYtJbl6gAWZXLGyMbA5812-w0LAXJiyQkPJy3zDDDQGAoayZ3C-X_27DuW8wvgCgDXvBS3emhuutxNQ1Y9QJt7CLaukq5No7H7Kw5iRPbnZH0X4c/manifest.json",
            isDefault = true
        )
    )

    const val TORBOX_DEFAULT_KEY = "50c74a49-a6bc-40e9-931e-1cee1943e87b"

    /** Hardcoded backup IPTV playlists for channel failover.
     *  When a channel stream fails, the app searches these sources for a working alternative. */
    val DEFAULT_BACKUP_SOURCES = listOf(
        BackupSource("NoCable 1", "http://nocable.cc:8080/get.php?username=ynn9Mz&password=200059&type=m3u"),
        BackupSource("ForTV 1", "http://fortv.cc:8080/get.php?username=4842428407&password=Kayla4545&type=m3u"),
        BackupSource("ForTV 2", "http://fortv.cc:8080/get.php?username=9A8vCh&password=118344&type=m3u"),
        BackupSource("TVMate", "http://tvmate.icu:8080/get.php?username=pU3Mes&password=259845&type=m3u"),
        BackupSource("ForTV 3", "http://fortv.cc:8080/get.php?username=aWbYH5&password=095767&type=m3u"),
        BackupSource("NoCable 2", "http://nocable.cc:8080/get.php?username=43761449506&password=smallwood&type=m3u"),
        BackupSource("IPTV WTF", "http://live.iptv.wtf:80/get.php?username=wbnbxoua&password=68k8A7bftS&type=m3u_plus"),
        BackupSource("NoCable 3", "http://nocable.cc:8080/get.php?username=80799785&password=80799785&type=m3u"),
        BackupSource("1TV41 1", "http://1tv41.icu:8080/get.php?username=ahx4CN&password=815233&type=m3u"),
        BackupSource("NoCable 4", "http://nocable.cc:8080/get.php?username=06121100&password=35428205&type=m3u"),
        BackupSource("1TV41 2", "http://1tv41.icu:8080/get.php?username=Gratitude@ogbtv.com&password=0128@1802&type=m3u"),
        BackupSource("SupersonicTV", "http://supersonictv.live:8080/get.php?username=Shacara1&password=Shacara1&type=m3u_plus&output=ts"),
        BackupSource("CordCutter 1", "http://cord-cutter.net:8080/get.php?username=3YyHfu&password=612143&type=m3u"),
        BackupSource("CordCutter 2", "http://cord-cutter.net:8080/get.php?username=bx6pUU&password=546211&type=m3u")
    )
}

data class EpgSource(
    val name: String,
    val url: String
)

data class BackupSource(
    val name: String,
    val url: String
)
