package dev.sonora.design.theme

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small process-wide theme preferences, persisted in the app's private preferences. */
object SonoraUiPreferences {
    private var storage: android.content.SharedPreferences? = null

    private val _accent = MutableStateFlow(SonoraAccent.TIDE)
    val accent: StateFlow<SonoraAccent> = _accent.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _font = MutableStateFlow(SonoraFont.SANS)
    val font: StateFlow<SonoraFont> = _font.asStateFlow()

    private val _karaokeWords = MutableStateFlow(true)
    val karaokeWords: StateFlow<Boolean> = _karaokeWords.asStateFlow()

    private val _lyricsArtwork = MutableStateFlow(true)
    val lyricsArtwork: StateFlow<Boolean> = _lyricsArtwork.asStateFlow()

    private val _followSystem = MutableStateFlow(true)
    val followSystem: StateFlow<Boolean> = _followSystem.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    fun initialize(context: Context) {
        storage = context.applicationContext.getSharedPreferences("sonora.ui", Context.MODE_PRIVATE)
        val prefs = storage ?: return
        _accent.value = SonoraAccent.entries.firstOrNull { it.name == prefs.getString(KEY_ACCENT, null) }
            ?: SonoraAccent.TIDE
        _dynamicColor.value = prefs.getBoolean(KEY_DYNAMIC, true)
        _font.value = SonoraFont.entries.firstOrNull { it.name == prefs.getString(KEY_FONT, null) }
            ?: SonoraFont.SANS
        _karaokeWords.value = prefs.getBoolean(KEY_KARAOKE, true)
        _lyricsArtwork.value = prefs.getBoolean(KEY_LYRICS_ARTWORK, true)
    }

    fun setAccent(accent: SonoraAccent) {
        _accent.value = accent
        storage?.edit()?.putString(KEY_ACCENT, accent.name)?.apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        storage?.edit()?.putBoolean(KEY_DYNAMIC, enabled)?.apply()
    }

    fun setFont(font: SonoraFont) {
        _font.value = font
        storage?.edit()?.putString(KEY_FONT, font.name)?.apply()
    }

    fun setKaraokeWords(enabled: Boolean) {
        _karaokeWords.value = enabled
        storage?.edit()?.putBoolean(KEY_KARAOKE, enabled)?.apply()
    }

    fun setLyricsArtwork(enabled: Boolean) {
        _lyricsArtwork.value = enabled
        storage?.edit()?.putBoolean(KEY_LYRICS_ARTWORK, enabled)?.apply()
    }

    fun setFollowSystem(enabled: Boolean) {
        _followSystem.value = enabled
    }

    fun setDarkTheme(enabled: Boolean) {
        _darkTheme.value = enabled
    }

    fun fontFamily(font: SonoraFont): FontFamily = when (font) {
        SonoraFont.SANS -> FontFamily.SansSerif
        SonoraFont.SERIF -> FontFamily.Serif
        SonoraFont.MONOSPACE -> FontFamily.Monospace
    }

    private const val KEY_ACCENT = "accent"
    private const val KEY_DYNAMIC = "dynamic_color"
    private const val KEY_FONT = "font"
    private const val KEY_KARAOKE = "karaoke_words"
    private const val KEY_LYRICS_ARTWORK = "lyrics_artwork"
}

enum class SonoraFont(val label: String) {
    SANS("System sans"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
}
