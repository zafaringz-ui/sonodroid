package dev.sonora.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sonora.core.PublicCatalogClient
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.DownloadItem
import dev.sonora.core.model.NetworkKind
import dev.sonora.core.model.Provider
import dev.sonora.core.model.ProviderAuth
import dev.sonora.core.model.QualityPreference
import dev.sonora.core.model.StreamPurpose
import dev.sonora.core.model.StreamQuality
import dev.sonora.design.theme.SonoraAccent
import dev.sonora.design.theme.SonoraUiPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val auth: List<ProviderAuth> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
    val crossfadeSeconds: Int = 0,
    val gapless: Boolean = true,
    val normalizeLoudness: Boolean = true,
    val network: NetworkKind = NetworkKind.WIFI,
    val preferences: Map<Provider, QualityPreference> = emptyMap(),
    val accent: SonoraAccent = SonoraAccent.TIDE,
    val dynamicColor: Boolean = true,
    val isDemo: Boolean = false,
    val isPublicMode: Boolean = false,
    val version: String = "0.1.0",
    val message: String? = null,
)

class SettingsViewModel(
    private val core: SonoraCoreClient,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            isDemo = core.isDemoMode,
            isPublicMode = core is PublicCatalogClient,
            accent = SonoraUiPreferences.accent.value,
            dynamicColor = SonoraUiPreferences.dynamicColor.value,
            preferences = Provider.entries.associateWith { core.qualityPreference(it) },
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            core.auth.collect { accounts -> _state.update { it.copy(auth = accounts) } }
        }
        viewModelScope.launch {
            core.downloads.collect { downloads -> _state.update { it.copy(downloads = downloads) } }
        }
    }

    fun setQualityPreference(provider: Provider, preference: QualityPreference) {
        core.setQualityPreference(provider, preference)
        _state.update { it.copy(preferences = it.preferences + (provider to preference)) }
    }

    fun resolvedQuality(provider: Provider): StreamQuality? =
        core.resolvedQuality(provider, StreamPurpose.STREAMING)

    fun downloadQuality(provider: Provider): StreamQuality? =
        core.resolvedQuality(provider, StreamPurpose.DOWNLOAD)

    fun setCrossfade(seconds: Int) {
        core.setCrossfade(seconds)
        _state.update { it.copy(crossfadeSeconds = seconds) }
    }

    /** Gapless and loudness normalisation are engine-side (Phase 2); stored for now. */
    fun setGapless(enabled: Boolean) = _state.update { it.copy(gapless = enabled) }

    fun setNormalizeLoudness(enabled: Boolean) =
        _state.update { it.copy(normalizeLoudness = enabled) }

    fun setNetwork(kind: NetworkKind) {
        core.setNetwork(kind)
        _state.update { it.copy(network = kind) }
    }

    fun setAccent(accent: SonoraAccent) {
        SonoraUiPreferences.setAccent(accent)
        _state.update { it.copy(accent = accent) }
    }

    fun setDynamicColor(enabled: Boolean) {
        SonoraUiPreferences.setDynamicColor(enabled)
        _state.update { it.copy(dynamicColor = enabled) }
    }

    fun signOut(provider: Provider) {
        viewModelScope.launch {
            core.signOut(provider)
            _state.update { it.copy(message = "Signed out of ${provider.label}") }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    companion object {
        fun factory(core: SonoraCoreClient): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(core) }
        }
    }
}
