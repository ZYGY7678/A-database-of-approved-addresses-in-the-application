package com.zygy7678.approvedbrowser

data class Site(
    val name: String,
    val host: String,
    val category: String,
    val url: String,
    val isAi: Boolean = false,
    val isForum: Boolean = false
)
