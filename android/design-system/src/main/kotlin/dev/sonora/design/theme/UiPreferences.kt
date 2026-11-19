package dev.sonora.design.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide appearance preferences.
 *
 * The theme lives above the navigation graph, so settings and the theme root need a
 * small shared holder. Persisting these belongs in DataStore (Phase 5); for now they
 * survive configuration changes and reset with the process.
 */
object SonoraUiPreferences {
    private val _accent = MutableStateFlow(SonoraAccent.TIDE)
    val accent: StateFlow<SonoraAccent> = _accent.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _followSystem = MutableStateFlow(true)
    val followSystem: StateFlow<Boolean> = _followSystem.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    fun setAccent(accent: SonoraAccent) {
        _accent.value = accent
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
    }

    fun setFollowSystem(enabled: Boolean) {
        _followSystem.value = enabled
    }

    fun setDarkTheme(enabled: Boolean) {
        _darkTheme.value = enabled
    }
}
