package dev.sonora.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sonora.core.DeezerAccountSession
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.AuthState
import dev.sonora.core.model.Provider
import dev.sonora.core.model.SearchFilter
import dev.sonora.core.model.SearchResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val provider: Provider = Provider.DEEZER,
    val filter: SearchFilter = SearchFilter.ALL,
    val loading: Boolean = false,
    val results: SearchResults? = null,
    val error: String? = null,
    val recent: List<String> = emptyList(),
    val isDemo: Boolean = false,
    val isAccountMode: Boolean = false,
    val isSignedIn: Boolean = false,
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val core: SonoraCoreClient,
) : ViewModel() {
    private data class Request(
        val term: String,
        val filter: SearchFilter,
        val provider: Provider,
        val retry: Int,
    )

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SearchFilter.ALL)
    private val provider = MutableStateFlow(Provider.DEEZER)
    private val retryCounter = MutableStateFlow(0)
    private var requestGeneration = 0L

    private val _state = MutableStateFlow(
        SearchUiState(
            isDemo = core.isDemoMode,
            isAccountMode = core is DeezerAccountSession,
            isSignedIn = core !is DeezerAccountSession || core.isSignedIn,
        ),
    )
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(query, filter, provider, retryCounter) { text, selectedFilter, selectedProvider, retry ->
                Request(text.trim(), selectedFilter, selectedProvider, retry)
            }
                .debounce(250)
                .distinctUntilChanged()
                .collectLatest { request ->
                    if (!isCurrent(request)) return@collectLatest
                    if (request.term.isBlank()) {
                        _state.update { it.copy(loading = false, results = null, error = null) }
                        return@collectLatest
                    }
                    val generation = ++requestGeneration
                    _state.update { it.copy(loading = true, error = null) }
                    try {
                        val results = core.search(request.provider, request.term, request.filter)
                        if (generation == requestGeneration && isCurrent(request)) {
                            _state.update { it.copy(loading = false, results = results, error = null) }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (generation == requestGeneration && isCurrent(request)) {
                            _state.update {
                                it.copy(loading = false, error = failure.message ?: "Search failed")
                            }
                        }
                    }
                }
        }
        if (core is DeezerAccountSession) {
            viewModelScope.launch {
                core.auth.collect { accounts ->
                    val signedIn = accounts.any {
                        it.provider == Provider.DEEZER && it.state == AuthState.SIGNED_IN
                    }
                    requestGeneration++
                    _state.update { current ->
                        current.copy(
                            isSignedIn = signedIn,
                            loading = false,
                            results = null,
                            error = if (!signedIn && current.query.isNotBlank()) {
                                "Sign in to Deezer to search the account catalog."
                            } else {
                                null
                            },
                        )
                    }
                    retryCounter.value += 1
                }
            }
        }
    }

    private fun isCurrent(request: Request): Boolean =
        (core !is DeezerAccountSession || (core.isSignedIn && _state.value.isSignedIn)) &&
            request.term == query.value.trim() &&
            request.filter == filter.value &&
            request.provider == provider.value &&
            request.retry == retryCounter.value

    fun onQueryChange(value: String) {
        requestGeneration++
        query.value = value
        _state.update { it.copy(query = value, loading = false, results = null, error = null) }
    }

    fun setFilter(value: SearchFilter) {
        requestGeneration++
        filter.value = value
        _state.update { it.copy(filter = value, loading = false, results = null, error = null) }
    }

    fun setProvider(value: Provider) {
        requestGeneration++
        provider.value = value
        _state.update { it.copy(provider = value, loading = false, results = null, error = null) }
    }

    fun retry() {
        if (query.value.isBlank()) return
        requestGeneration++
        _state.update { it.copy(loading = true, error = null) }
        retryCounter.value += 1
    }

    /** Enter on the keyboard: remember the term, then let the debounce fire. */
    fun submit() {
        val term = query.value.trim()
        if (term.isEmpty()) return
        _state.update { current ->
            current.copy(recent = (listOf(term) + current.recent).distinct().take(8))
        }
    }

    fun clearQuery() = onQueryChange("")

    companion object {
        fun factory(core: SonoraCoreClient): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(core) }
        }
    }
}
