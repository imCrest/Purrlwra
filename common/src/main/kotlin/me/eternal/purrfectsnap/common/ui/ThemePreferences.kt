package cock.crest.purrfectsnap.lite.common.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.themeDataStore by preferencesDataStore("theme_prefs")

object ThemePreferences {
    private val THEME_KEY = stringPreferencesKey("theme_mode")

    fun getThemeModeFlow(context: Context): Flow<ThemeMode> =
        context.themeDataStore.data.map { prefs ->
            when (prefs[THEME_KEY]) {
                ThemeMode.LIGHT.name -> ThemeMode.LIGHT
                ThemeMode.DARK.name -> ThemeMode.DARK
                ThemeMode.AMOLED.name -> ThemeMode.AMOLED
                else -> ThemeMode.SYSTEM
            }
        }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.themeDataStore.edit { it[THEME_KEY] = mode.name }
    }
}
