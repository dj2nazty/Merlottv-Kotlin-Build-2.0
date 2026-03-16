package com.merlottv.kotlin.domain.model

data class TmdbCastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profileUrl: String
)

data class TmdbFilmCredit(
    val id: Int,
    val imdbId: String,
    val title: String,
    val posterUrl: String,
    val type: String,
    val year: String,
    val character: String,
    val voteAverage: String
)
