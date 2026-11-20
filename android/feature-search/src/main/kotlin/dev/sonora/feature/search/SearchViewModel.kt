package dev.sonora.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.Provider
import dev.sonora.core.model.SearchFilter
import dev.sonora.core.model.SearchResults
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
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val core: SonoraCoreClient,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SearchFilter.ALL)
    private val provider = MutableStateFlow(Provider.DEEZER)

    private val _state = MutableStateFlow(SearchUiState(isDemo = core.isDemoMode))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(query, filter, provider) { text, selectedFilter, selectedProvider ->
                Triple(text.trim(), selectedFilter, selectedProvider)
            }
                .debounce(250)
                .distinctUntilChanged()
                .collectLatest { (text, selectedFilter, selectedProvider) ->
                    if (text.isBlank()) {
                        _state.update { it.copy(loading = false, results = null, error = null) }
                        return@collectLatest
                    }
                    _state.update { it.copy(loading = true, error = null) }
                    runCatching { core.search(selectedProvider, text, selectedFilter) }
                        .onSuccess { results ->
                            _state.update { it.copy(loading = false, results = results) }
                        }
                        .onFailure { failure ->
                            _state.update {
                                it.copy(
                                    loading = false,
                                    error = failure.message ?: "Search failed",
                                )
                            }
                        }
                }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
        _state.update { it.copy(query = value) }
    }

    fun setFilter(value: SearchFilter) {
        filter.value = value
        _state.update { it.copy(filter = value) }
    }

    fun setProvider(value: Provider) {
        provider.value = value
        _state.update { it.copy(provider = value) }
    }

    fun retry() {
        val term = query.value.trim()
        if (term.isBlank()) return
        val selectedFilter = filter.value
        val selectedProvider = provider.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { core.search(selectedProvider, term, selectedFilter) }
                .onSuccess { results -> _state.update { it.copy(loading = false, results = results) } }
                .onFailure { failure ->
                    _state.update { it.copy(loading = false, error = failure.message ?: "Search failed") }
                }
        }
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
