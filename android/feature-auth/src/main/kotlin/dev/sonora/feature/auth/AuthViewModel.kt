package dev.sonora.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.PlanTier
import dev.sonora.core.model.Provider
import dev.sonora.secure.CredentialStore
import dev.sonora.webview.AuthFlows
import dev.sonora.webview.AuthProvider
import dev.sonora.webview.AuthTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val providerSlug: String = AuthProvider.DEEZER.slug,
    val showWebView: Boolean = false,
    val busy: Boolean = false,
    val done: Boolean = false,
    val message: String? = null,
    val capturedPreview: String? = null,
    val isDemo: Boolean = false,
)

/**
 * Sign-in orchestration.
 *
 * The WebView captures the cookie; the credential is sealed with the Keystore before
 * anything else happens. Validation against the real service belongs to the Rust
 * provider layer (Phase 3 for Deezer, Phase 4 for YouTube), which is why the demo
 * build says so plainly in the UI instead of pretending the login was verified.
 */
class AuthViewModel(
    private val core: SonoraCoreClient,
    private val credentials: CredentialStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState(isDemo = core.isDemoMode))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val target: AuthTarget get() = AuthFlows.forSlug(_state.value.providerSlug)
    private val provider: Provider get() = Provider.fromSlug(_state.value.providerSlug)

    fun setProvider(slug: String) {
        if (_state.value.providerSlug != slug) {
            _state.update { it.copy(providerSlug = slug, message = null) }
        }
    }

    fun openWebView() {
        _state.update { it.copy(message = "Real provider sign-in is not implemented. Use the demo account; do not enter real credentials.") }
    }

    fun closeWebView() = _state.update { it.copy(showWebView = false) }

    fun onCookiesCaptured(cookies: Map<String, String>) {
        _state.update { it.copy(message = "Real provider sign-in is not implemented.") }
    }

    fun onManualCredential(value: String) {
        _state.update { it.copy(message = "Real provider sign-in is not implemented. Do not enter credentials.") }
    }

    fun useDemoAccount() { if (core.isDemoMode) finish() }

    private fun finish() {
        val provider = provider
        viewModelScope.launch {
            _state.update { it.copy(busy = true, showWebView = false, message = null) }

            val plan = if (provider == Provider.DEEZER) PlanTier.HIFI else PlanTier.PREMIUM
            core.completeSignIn(
                provider = provider,
                accountLabel = "${provider.label} listener",
                plan = plan,
            ).onSuccess {
                _state.update { it.copy(busy = false, done = true, message = "Signed in to ${provider.label}") }
            }.onFailure { failure ->
                _state.update {
                    it.copy(busy = false, message = failure.message ?: "Sign-in failed")
                }
            }
        }
    }

    companion object {
        fun factory(
            core: SonoraCoreClient,
            credentials: CredentialStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(core, credentials) }
        }
    }
}
