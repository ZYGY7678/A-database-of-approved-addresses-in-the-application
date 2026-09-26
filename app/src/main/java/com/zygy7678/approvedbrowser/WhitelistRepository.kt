package com.zygy7678.approvedbrowser

import android.content.Context
import org.json.JSONObject
import java.net.URI

object WhitelistRepository {
    fun load(context: Context): List<Site> {
        val text = context.assets.open("approved_sites.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val result = mutableListOf<Site>()
        fun read(key: String, ai: Boolean = false, forum: Boolean = false) {
            val array = root.optJSONArray(key) ?: return
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val url = item.optString("url")
                val host = item.optString("host").ifBlank { runCatching { URI(url).host ?: "" }.getOrDefault("") }
                result += Site(item.optString("name", "אתר"), normalizeHost(host), item.optString("category", key), url, ai, forum)
            }
        }
        read("ai", ai = true)
        read("forums", forum = true)
        read("core")
        return result.filter { it.url.startsWith("https://") || it.url.startsWith("http://") }.distinctBy { it.name + "|" + it.host }
    }

    fun isAllowed(url: String, sites: List<Site>): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        val normalized = normalizeHost(host)
        return sites.any { normalized == it.host || normalized.endsWith("." + it.host) }
    }

    private fun normalizeHost(value: String) = value.lowercase().trim()
        .removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .substringBefore("/").substringBefore(":")
}
