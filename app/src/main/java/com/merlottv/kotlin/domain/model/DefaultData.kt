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
            id = "cinemeta",
            name = "Cinemeta",
            url = "https://v3-cinemeta.strem.io/manifest.json",
            catalogs = listOf(
                AddonCatalog(
                    id = "top",
                    name = "Popular Movies",
                    type = "movie",
                    extra = listOf(CatalogExtra("search", false))
                ),
                AddonCatalog(
                    id = "top",
                    name = "Popular Series",
                    type = "series",
                    extra = listOf(CatalogExtra("search", false))
                )
            ),
            isDefault = true
        ),
        Addon(
            id = "tmdb-addon",
            name = "TMDB",
            url = "https://94c8cb9f702d-tmdb-addon.baby-beamup.club/manifest.json",
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
