package app.dearme

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dearme.data.Settings
import app.dearme.notify.Alarms
import app.dearme.ui.DearRoot
import app.dearme.ui.theme.DearTheme
import app.dearme.ui.theme.isDark
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {
    /** Screen requested from outside: a notification. */
    private val openRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val dark = isDark((settings ?: Settings()).theme)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DearTheme(dark) {
                // Wait for settings so the theme doesn't flash light-then-dark.
                if (settings != null) DearRoot(openRoute = openRoute, settings = settings!!)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(Alarms.EXTRA_ROUTE)?.let { openRoute.value = it }
    }
}
