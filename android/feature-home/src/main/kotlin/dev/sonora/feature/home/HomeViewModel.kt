package dev.sonora.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.AuthState
import dev.sonora.core.model.HomeFeed
import dev.sonora.core.model.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val feeds: Map<Provider, HomeFeed> = emptyMap(),
    val selected: Provider = Provider.DEEZER,
    val signedIn: Set<Provider> = emptySet(),
    val error: String? = null,
    val isDemo: Boolean = false,
) {
    val feed: HomeFeed? get() = feeds[selected]
}

class HomeViewModel(
    private val core: SonoraCoreClient,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(isDemo = core.isDemoMode))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            core.auth.collect { accounts ->
                _state.update { current ->
                    current.copy(
                        signedIn = accounts
                            .filter { it.state == AuthState.SIGNED_IN }
                            .map { it.provider }
                            .toSet(),
                    )
                }
            }
        }
        load(Provider.DEEZER)
    }

    fun select(provider: Provider) {
        if (_state.value.selected != provider) {
            _state.update { it.copy(selected = provider) }
        }
        load(provider)
    }

    fun refresh() = load(_state.value.selected, force = true)

    private fun load(provider: Provider, force: Boolean = false) {
        val cached = _state.value.feeds.containsKey(provider)
        if (cached && !force) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { core.home(provider) }
                .onSuccess { feed ->
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            feeds = current.feeds + (provider to feed),
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = failure.message ?: "Could not load Home",
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(core: SonoraCoreClient): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(core) }
        }
    }
}
