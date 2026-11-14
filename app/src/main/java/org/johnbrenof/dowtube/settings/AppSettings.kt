package org.johnbrenof.dowtube.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSettings {

    private const val PREFS = "settings"
    private const val KEY_THEME = "theme"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_MAX_QUALITY = "max_quality_video"
    private const val KEY_AUTO_CLEAR = "auto_clear_7d"

    private lateinit var prefs: SharedPreferences

    private val _theme = MutableStateFlow("system")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _language = MutableStateFlow("system")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _maxQualityVideo = MutableStateFlow(true)
    val maxQualityVideo: StateFlow<Boolean> = _maxQualityVideo.asStateFlow()

    private val _autoClearHistory = MutableStateFlow(false)
    val autoClearHistory: StateFlow<Boolean> = _autoClearHistory.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _theme.value = prefs.getString(KEY_THEME, "system") ?: "system"
        _language.value = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        _maxQualityVideo.value = prefs.getBoolean(KEY_MAX_QUALITY, true)
        _autoClearHistory.value = prefs.getBoolean(KEY_AUTO_CLEAR, false)
    }

    fun setTheme(value: String) {
        prefs.edit().putString(KEY_THEME, value).apply()
        _theme.value = value
    }

    fun setLanguage(value: String) {
        prefs.edit().putString(KEY_LANGUAGE, value).apply()
        _language.value = value
    }

    fun setMaxQualityVideo(value: Boolean) {
        prefs.edit().putBoolean(KEY_MAX_QUALITY, value).apply()
        _maxQualityVideo.value = value
    }

    fun setAutoClearHistory(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CLEAR, value).apply()
        _autoClearHistory.value = value
    }
}

object AppLocale {

    fun wrap(base: Context): Context {
        val language = base.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("language", "system") ?: "system"
        if (language == "system") return base

        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
