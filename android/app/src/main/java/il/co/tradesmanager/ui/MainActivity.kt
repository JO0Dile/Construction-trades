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
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.SettingsRepository
import il.co.tradesmanager.ui.account.AccountGateScreen
import il.co.tradesmanager.ui.account.InductionScreen
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
        val sessionFlow = container.session.state.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = SessionRepository.State.Loading,
        )

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle()
            val session by sessionFlow.collectAsStateWithLifecycle()
            TradesManagerTheme(themeMode = settings.themeMode, largeText = settings.largeText) {
                ProvideCatalogImagery(container.categoryIcons, container.bundledImages) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        // Who is holding the phone is settled before anything
                        // else, because it decides what the app even contains.
                        when (val state = session) {
                            SessionRepository.State.Loading -> Unit

                            SessionRepository.State.NeedsSetup ->
                                AccountGateScreen(container, needsSetup = true)

                            SessionRepository.State.SignedOut ->
                                AccountGateScreen(container, needsSetup = false)

                            is SessionRepository.State.SignedIn ->
                                // The induction is not a screen inside the app,
                                // it is the last thing before it. Somebody who
                                // closes the phone half way through comes back
                                // to the induction, not to the inside.
                                if (state.needsInduction) {
                                    InductionScreen(
                                        container = container,
                                        accountId = state.account.id,
                                        level = state.inductionLevel,
                                    )
                                } else {
                                    AppNavHost(
                                        container = container,
                                        settings = settings,
                                        role = state.role,
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}
