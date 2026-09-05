package il.co.tradesmanager.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Device-local preferences. Nothing here is personal data. */
class SettingsRepository(private val context: Context) {

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    data class Settings(
        val onboardingComplete: Boolean = false,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val largeText: Boolean = false,
        val encryptDatabase: Boolean = true,
        val actorName: String = "",
        val seededCatalogVersion: Int = 0,
        /** Projects as picture cards rather than as rows. */
        val projectsAsGrid: Boolean = true,
    )

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            onboardingComplete = prefs[KEY_ONBOARDED] ?: false,
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            largeText = prefs[KEY_LARGE_TEXT] ?: false,
            encryptDatabase = prefs[KEY_ENCRYPT] ?: true,
            actorName = prefs[KEY_ACTOR] ?: "",
            seededCatalogVersion = prefs[KEY_SEEDED_VERSION] ?: 0,
            projectsAsGrid = prefs[KEY_PROJECTS_GRID] ?: true,
        )
    }

    suspend fun setOnboardingComplete(value: Boolean) = put { it[KEY_ONBOARDED] = value }
    suspend fun setThemeMode(mode: ThemeMode) = put { it[KEY_THEME] = mode.name }
    suspend fun setLargeText(value: Boolean) = put { it[KEY_LARGE_TEXT] = value }
    suspend fun setEncryptDatabase(value: Boolean) = put { it[KEY_ENCRYPT] = value }
    suspend fun setActorName(name: String) = put { it[KEY_ACTOR] = name }
    suspend fun setSeededCatalogVersion(version: Int) = put { it[KEY_SEEDED_VERSION] = version }
    suspend fun setProjectsAsGrid(value: Boolean) = put { it[KEY_PROJECTS_GRID] = value }

    /** Account deletion, as both stores require it to be offered in-app. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_LARGE_TEXT = booleanPreferencesKey("large_text")
        val KEY_ENCRYPT = booleanPreferencesKey("encrypt_database")
        val KEY_ACTOR = stringPreferencesKey("actor_name")
        val KEY_SEEDED_VERSION = intPreferencesKey("seeded_catalog_version")
        val KEY_PROJECTS_GRID = booleanPreferencesKey("projects_as_grid")
    }
}
