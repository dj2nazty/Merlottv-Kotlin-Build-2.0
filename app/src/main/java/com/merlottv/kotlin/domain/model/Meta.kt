package com.merlottv.kotlin.domain.model

data class Meta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String = "",
    val posterShape: String = "poster",
    val background: String = "",
    val logo: String = "",
    val description: String = "",
    val releaseInfo: String = "",
    val year: String = "",
    val runtime: String = "",
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val writer: List<String> = emptyList(),
    val imdbRating: String = "",
    val videos: List<Video> = emptyList(),
    val links: List<MetaLink> = emptyList(),
    val trailerStreams: List<TrailerStream> = emptyList()
)

data class TrailerStream(
    val title: String = "",
    val ytId: String = "",
    val url: String = "",
    val source: String = ""
)

data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String = "",
    val posterShape: String = "poster",
    val description: String = "",
    val imdbRating: String = "",
    val background: String = "",
    val logo: String = ""
)

data class Video(
    val id: String,
    val title: String,
    val season: Int = 0,
    val episode: Int = 0,
    val released: String = "",
    val overview: String = "",
    val thumbnail: String = ""
)

data class MetaLink(
    val name: String,
    val category: String,
    val url: String = ""
)
