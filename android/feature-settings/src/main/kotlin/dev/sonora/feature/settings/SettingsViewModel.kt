package dev.sonora.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sonora.core.DeezerAccountSession
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.DownloadItem
import dev.sonora.core.model.NetworkKind
import dev.sonora.core.model.Provider
import dev.sonora.core.model.ProviderAuth
import dev.sonora.core.model.QualityPreference
import dev.sonora.core.model.StreamPurpose
import dev.sonora.core.model.StreamQuality
import dev.sonora.design.theme.SonoraAccent
import dev.sonora.design.theme.SonoraFont
import dev.sonora.design.theme.SonoraUiPreferences
import dev.sonora.secure.CredentialStore
import kotlinx.coroutines.CancellationException
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
    val font: SonoraFont = SonoraFont.SANS,
    val karaokeWords: Boolean = true,
    val lyricsArtwork: Boolean = true,
    val isDemo: Boolean = false,
    val isAccountMode: Boolean = false,
    val version: String = "0.1.0",
    val updateChecking: Boolean = false,
    val updateStatus: String? = null,
    val updateUrl: String? = null,
    val message: String? = null,
)

@Suppress("DEPRECATION")
private fun Context.installedVersionName(): String =
    packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0"

class SettingsViewModel(
    private val core: SonoraCoreClient,
    private val credentials: CredentialStore?,
    private val context: Context?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            isDemo = core.isDemoMode,
            isAccountMode = core is DeezerAccountSession,
            accent = SonoraUiPreferences.accent.value,
            dynamicColor = SonoraUiPreferences.dynamicColor.value,
            font = SonoraUiPreferences.font.value,
            karaokeWords = SonoraUiPreferences.karaokeWords.value,
            lyricsArtwork = SonoraUiPreferences.lyricsArtwork.value,
            preferences = Provider.entries.associateWith { core.qualityPreference(it) },
            version = context?.installedVersionName() ?: "0.1.0",
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

    fun setFont(font: SonoraFont) {
        SonoraUiPreferences.setFont(font)
        _state.update { it.copy(font = font) }
    }

    fun setKaraokeWords(enabled: Boolean) {
        SonoraUiPreferences.setKaraokeWords(enabled)
        _state.update { it.copy(karaokeWords = enabled) }
    }

    fun setLyricsArtwork(enabled: Boolean) {
        SonoraUiPreferences.setLyricsArtwork(enabled)
        _state.update { it.copy(lyricsArtwork = enabled) }
    }

    fun checkForUpdates() {
        if (_state.value.updateChecking) return
        val appContext = context?.applicationContext
        if (appContext == null) {
            _state.update { it.copy(updateStatus = "Update check unavailable in this context.") }
            return
        }
        _state.update { it.copy(updateChecking = true, updateStatus = "Checking GitHub releases…", updateUrl = null) }
        viewModelScope.launch {
            try {
                when (val update = UpdateChecker.check(appContext)) {
                    is UpdateResult.Available -> _state.update {
                        it.copy(updateChecking = false, updateStatus = "Version ${update.version} is available.", updateUrl = update.releaseUrl)
                    }
                    is UpdateResult.Current -> _state.update {
                        it.copy(updateChecking = false, updateStatus = "You're up to date (${update.version}).", updateUrl = null)
                    }
                    UpdateResult.NoRelease -> _state.update {
                        it.copy(updateChecking = false, updateStatus = "No published release is available yet.", updateUrl = null)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update {
                    it.copy(updateChecking = false, updateStatus = "Could not check for updates. Check your connection and try again.", updateUrl = null)
                }
            }
        }
    }

    fun signOut(provider: Provider) {
        viewModelScope.launch {
            core.signOut(provider)
            credentials?.clear(provider.slug)
            _state.update { it.copy(message = "Signed out of ${provider.label}") }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    companion object {
        fun factory(
            core: SonoraCoreClient,
            credentials: CredentialStore? = null,
            context: Context? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(core, credentials, context) }
        }
    }
}
