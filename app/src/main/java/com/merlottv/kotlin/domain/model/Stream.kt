package com.merlottv.kotlin.domain.model

data class Stream(
    val name: String = "",
    val title: String = "",
    val url: String = "",
    val ytId: String = "",
    val infoHash: String = "",
    val fileIdx: Int? = null,
    val externalUrl: String = "",
    val addonName: String = "",
    val addonLogo: String = ""
)
