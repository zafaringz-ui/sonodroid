package dev.sonora.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sonora.core.DeezerAccountSession
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.AuthState
import dev.sonora.core.model.HomeFeed
import dev.sonora.core.model.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val isAccountMode: Boolean = false,
) {
    val feed: HomeFeed? get() = feeds[selected]
}

class HomeViewModel(
    private val core: SonoraCoreClient,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(
            isDemo = core.isDemoMode,
            isAccountMode = core is DeezerAccountSession,
        ),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private val loadJobs = mutableMapOf<Provider, Job>()
    private val loadGenerations = mutableMapOf<Provider, Long>()

    init {
        viewModelScope.launch {
            core.auth.collect { accounts ->
                val signedIn = accounts
                    .filter { it.state == AuthState.SIGNED_IN }
                    .map { it.provider }
                    .toSet()
                val accountSignedIn = core !is DeezerAccountSession ||
                    (Provider.DEEZER in signedIn && core.isSignedIn)
                if (core is DeezerAccountSession && !accountSignedIn) {
                    invalidateLoad(Provider.DEEZER)
                }
                _state.update { current ->
                    current.copy(
                        signedIn = signedIn,
                        loading = if (accountSignedIn) current.loading else false,
                        feeds = if (accountSignedIn) current.feeds else emptyMap(),
                        error = if (accountSignedIn) current.error else null,
                    )
                }
                if (core is DeezerAccountSession && accountSignedIn) {
                    load(Provider.DEEZER, force = true)
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
        if (!canLoad(provider)) {
            _state.update { it.copy(loading = false) }
            return
        }
        val cached = _state.value.feeds.containsKey(provider)
        if (cached && !force) {
            _state.update { it.copy(loading = false) }
            return
        }
        val generation = (loadGenerations[provider] ?: 0L) + 1L
        loadGenerations[provider] = generation
        loadJobs[provider]?.cancel()
        loadJobs[provider] = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val feed = core.home(provider)
                if (isCurrentLoad(provider, generation)) {
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            feeds = current.feeds + (provider to feed),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrentLoad(provider, generation)) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = failure.message ?: "Could not load Home",
                        )
                    }
                }
            } finally {
                if (loadGenerations[provider] == generation) loadJobs.remove(provider)
            }
        }
    }

    private fun canLoad(provider: Provider): Boolean =
        core !is DeezerAccountSession || provider != Provider.DEEZER ||
            (core.isSignedIn && Provider.DEEZER in _state.value.signedIn)

    private fun isCurrentLoad(provider: Provider, generation: Long): Boolean =
        loadGenerations[provider] == generation && canLoad(provider)

    private fun invalidateLoad(provider: Provider) {
        loadGenerations[provider] = (loadGenerations[provider] ?: 0L) + 1L
        loadJobs.remove(provider)?.cancel()
    }

    companion object {
        fun factory(core: SonoraCoreClient): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(core) }
        }
    }
}
