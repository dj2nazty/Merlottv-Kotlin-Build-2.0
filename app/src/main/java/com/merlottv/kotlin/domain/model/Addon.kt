package com.merlottv.kotlin.domain.model

data class Addon(
    val id: String = "",
    val name: String,
    val url: String,
    val description: String = "",
    val logo: String = "",
    val version: String = "",
    val catalogs: List<AddonCatalog> = emptyList(),
    val types: List<String> = emptyList(),
    val resources: List<String> = emptyList(),
    val isDefault: Boolean = false,
    val isNetflix: Boolean = false
)

data class AddonCatalog(
    val id: String,
    val name: String,
    val type: String,
    val extra: List<CatalogExtra> = emptyList()
)

data class CatalogExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList()
)
