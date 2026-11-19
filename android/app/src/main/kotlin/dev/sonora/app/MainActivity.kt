package dev.sonora.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sonora.design.theme.SonoraTheme
import dev.sonora.design.theme.SonoraUiPreferences

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: Compose owns the insets, the theme keeps the bars transparent.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        requestNotificationPermissionIfNeeded()
        handleIntent(intent)

        val application = application as SonoraApplication
        val graph = application.graph

        setContent {
            val accent by SonoraUiPreferences.accent.collectAsStateWithLifecycle()
            val dynamicColor by SonoraUiPreferences.dynamicColor.collectAsStateWithLifecycle()

            SonoraTheme(accent = accent, dynamicColor = dynamicColor) {
                SonoraApp(graph = graph)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val link = DeepLinks.fromIntent(intent) ?: return
        (application as? SonoraApplication)?.graph?.handleIncomingLink(link)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
