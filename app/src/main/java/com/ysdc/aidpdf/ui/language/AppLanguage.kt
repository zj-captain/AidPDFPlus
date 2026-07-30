package com.ysdc.aidpdf.ui.language

import java.util.Locale

data class AppLanguage(
    val label: String,
    val tag: String
)

object AppLanguages {

    val all = listOf(
        AppLanguage("English", "en"),
        AppLanguage("Français", "fr"),
        AppLanguage("Español", "es"),
        AppLanguage("Deutsch", "de"),
        AppLanguage("Português", "pt"),
        AppLanguage("繁體中文（香港）", "zh-HK"),
        AppLanguage("繁體中文（台灣）", "zh-TW"),
        AppLanguage("简体中文", "zh"),
        AppLanguage("日本語", "ja"),
        AppLanguage("한국어", "ko"),
        AppLanguage("العربية", "ar"),
        AppLanguage("हिन्दी", "hi"),
        AppLanguage("Italiano", "it"),
        AppLanguage("ภาษาไทย", "th"),
        AppLanguage("Bahasa Indonesia", "id")
    )

    fun ordered(selectedTag: String): List<AppLanguage> {
        val selected = all.firstOrNull { it.tag == selectedTag } ?: all.first()
        return listOf(selected) + all.filterNot { it.tag == selected.tag }
    }

    fun defaultForSystem(locale: Locale = Locale.getDefault()): AppLanguage {
        val tag = tagForLocale(locale)
        return all.firstOrNull { it.tag == tag } ?: all.first()
    }

    private fun tagForLocale(locale: Locale): String {
        val language = locale.language.lowercase(Locale.US)
        val country = locale.country.uppercase(Locale.US)
        val script = locale.script

        if (language == "zh") {
            return when {
                country == "HK" || country == "MO" -> "zh-HK"
                country == "TW" -> "zh-TW"
                script.equals("Hant", ignoreCase = true) -> "zh-TW"
                else -> "zh"
            }
        }

        val normalizedLanguage = if (language == "in") "id" else language
        return all.firstOrNull { it.tag.equals(normalizedLanguage, ignoreCase = true) }?.tag ?: "en"
    }
}
