package dev.sonora.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.DownloadItem
import dev.sonora.core.model.LibrarySnapshot
import dev.sonora.core.model.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) {
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    SONGS("Songs"),
    DOWNLOADS("Downloads"),
}

data class LibraryUiState(
    val provider: Provider = Provider.DEEZER,
    val tab: LibraryTab = LibraryTab.PLAYLISTS,
    val loading: Boolean = true,
    val snapshot: LibrarySnapshot? = null,
    val downloads: List<DownloadItem> = emptyList(),
    val error: String? = null,
    val grid: Boolean = false,
    val isDemo: Boolean = false,
)

class LibraryViewModel(
    private val core: SonoraCoreClient,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState(isDemo = core.isDemoMode))
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            core.downloads.collect { downloads ->
                _state.update { it.copy(downloads = downloads) }
            }
        }
        load(Provider.DEEZER)
    }

    fun selectProvider(provider: Provider) {
        if (_state.value.provider != provider) {
            _state.update { it.copy(provider = provider, snapshot = null) }
        }
        load(provider)
    }

    fun selectTab(tab: LibraryTab) = _state.update { it.copy(tab = tab) }

    fun toggleLayout() = _state.update { it.copy(grid = !it.grid) }

    fun refresh() = load(_state.value.provider, force = true)

    private fun load(provider: Provider, force: Boolean = false) {
        val cached = _state.value.snapshot?.provider == provider
        if (cached && !force) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { core.library(provider) }
                .onSuccess { snapshot ->
                    _state.update {
                        it.copy(
                            loading = false,
                            snapshot = snapshot,
                            downloads = snapshot.downloads.ifEmpty { it.downloads },
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(loading = false, error = failure.message ?: "Could not load library")
                    }
                }
        }
    }

    companion object {
        fun factory(core: SonoraCoreClient): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(core) }
        }
    }
}
