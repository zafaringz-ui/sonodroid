package dev.sonora.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sonora.core.SonoraCoreClient
import dev.sonora.secure.CredentialStore
import dev.sonora.webview.AuthFlows
import dev.sonora.webview.AuthWebView
import dev.sonora.webview.AuthProvider
import dev.sonora.webview.clearCookiesFor

@Composable
fun SignInRoute(
    core: SonoraCoreClient,
    credentials: CredentialStore,
    providerSlug: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(core, credentials),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(providerSlug) { viewModel.setProvider(providerSlug) }
    LaunchedEffect(state.done) {
        if (state.done) {
            clearCookiesFor(AuthFlows.forSlug(providerSlug))
            onDone()
        }
    }

    SignInScreen(
        state = state,
        onBack = {
            if (state.showWebView) viewModel.closeWebView() else onBack()
        },
        onOpenWebView = viewModel::openWebView,
        onCookiesCaptured = viewModel::onCookiesCaptured,
        onManualCredential = viewModel::onManualCredential,
        onUseDemoAccount = viewModel::useDemoAccount,
    )
}

/** Screen 3/4: provider sign-in with WebView capture and a manual fallback. */
@Composable
fun SignInScreen(
    state: AuthUiState,
    onBack: () -> Unit,
    onOpenWebView: () -> Unit,
    onCookiesCaptured: (Map<String, String>) -> Unit,
    onManualCredential: (String) -> Unit,
    onUseDemoAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = AuthFlows.forSlug(state.providerSlug)
    var manualValue by remember { mutableStateOf("") }

    if (state.showWebView) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Sign in to ${target.label}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            AuthWebView(
                target = target,
                modifier = Modifier.fillMaxSize(),
                onCookiesCaptured = onCookiesCaptured,
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Sign in to ${target.label}",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (state.isAccountMode && state.providerSlug == AuthProvider.DEEZER.slug) {
                "Sign in to Deezer in the secure browser to access your account catalog and full-track playback. Your session is validated and stored encrypted on this device."
            } else {
                "This Android account client currently supports Deezer sign-in only."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.isAccountMode && state.providerSlug == AuthProvider.DEEZER.slug) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenWebView, enabled = !state.busy) {
                Text("Continue with Deezer")
            }
        }

        if (state.isDemo) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onUseDemoAccount, enabled = !state.busy) {
                Text("Use demo account")
            }
        }

        if (state.busy) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        if (state.capturedPreview != null) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Captured credential: ${state.capturedPreview}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.message?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    MaterialTheme.shapes.medium,
                )
                .padding(16.dp),
        ) {
            Text(
                text = "The credential is sealed with an Android Keystore key before it is " +
                    "written to storage. Validation against ${target.label} runs in the native " +
                    "core; in this build the session is accepted locally and marked as demo data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
