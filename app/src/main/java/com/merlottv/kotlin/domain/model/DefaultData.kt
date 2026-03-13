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
        )
    )

    const val TORBOX_DEFAULT_KEY = "50c74a49-a6bc-40e9-931e-1cee1943e87b"
}

data class EpgSource(
    val name: String,
    val url: String
)
