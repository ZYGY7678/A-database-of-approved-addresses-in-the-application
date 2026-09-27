package com.zygy7678.approvedbrowser

import android.content.Context
import java.security.MessageDigest

enum class BrowserRoute(
    val title: String,
    val description: String
) {
    ETROG("אתרוג", "חיוניים בלבד"),
    HADASS("הדס", "חיוניים + AI"),
    LULAV("לולב", "כל האתרים המאושרים")
}

fun BrowserRoute.allows(site: Site): Boolean = when (this) {
    BrowserRoute.ETROG -> !site.isAi && !site.isForum
    BrowserRoute.HADASS -> !site.isForum
    BrowserRoute.LULAV -> true
}

class AccessCodeStore(context: Context) {
    private val prefs = context.getSharedPreferences("browser_access", Context.MODE_PRIVATE)
    private val defaultHash = sha256("2468")

    init {
        if (!prefs.contains("code_hash")) {
            prefs.edit().putString("code_hash", defaultHash).apply()
        }
    }

    fun verify(code: String): Boolean =
        code.isNotBlank() && sha256(code) == prefs.getString("code_hash", defaultHash)

    fun changeCode(newCode: String): Boolean {
        if (!newCode.matches(Regex("\\d{4,12}"))) return false
        prefs.edit().putString("code_hash", sha256(newCode)).apply()
        return true
    }

    fun provisionFromSetup(newCode: String): Boolean {
        if (!newCode.matches(Regex("\\d{4,12}"))) return false
        prefs.edit().putString("code_hash", sha256(newCode)).apply()
        return true
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

class FavoriteStore(context: Context) {
    private val prefs = context.getSharedPreferences("browser_favorites", Context.MODE_PRIVATE)

    fun load(): Set<String> =
        prefs.getStringSet("sites", emptySet())?.toSet() ?: emptySet()

    fun toggle(key: String): Set<String> {
        val next = load().toMutableSet()
        if (!next.add(key)) next.remove(key)
        prefs.edit().putStringSet("sites", next).apply()
        return next
    }
}

fun siteKey(site: Site): String =
    site.name + "|" + site.host + "|" + site.url
