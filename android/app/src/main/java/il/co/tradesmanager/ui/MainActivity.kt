package il.co.tradesmanager.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.co.tradesmanager.R
import il.co.tradesmanager.TradesManagerApp
import il.co.tradesmanager.data.repository.SettingsRepository
import il.co.tradesmanager.ui.components.ProvideCatalogImagery
import il.co.tradesmanager.ui.nav.AppNavHost
import il.co.tradesmanager.ui.theme.TradesManagerTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.lifecycleScope

/**
 * The single activity.
 *
 * It extends AppCompatActivity because the per-app language switcher is
 * AppCompat's: that is what makes "change the language" work on Android 8
 * through 12 as well as it does on 13+, without the app tracking a locale of
 * its own or restarting itself.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hands the window back from the splash theme before the first frame.
        setTheme(R.style.Theme_TradesManager)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TradesManagerApp).container
        val settingsFlow = container.settings.settings.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsRepository.Settings(),
        )

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle()
            TradesManagerTheme(themeMode = settings.themeMode, largeText = settings.largeText) {
                ProvideCatalogImagery(container.categoryIcons, container.bundledImages) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppNavHost(container = container, settings = settings)
                    }
                }
            }
        }
    }
}
