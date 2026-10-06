package com.playertwo.controlegithub

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

internal enum class AppThemeMode(val label: String) {
    SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro");

    companion object {
        fun fromStored(value: String?): AppThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

internal val Context.preferencesDataStore by preferencesDataStore(name = "app_preferences")
private val themeModeKey = stringPreferencesKey("theme_mode")

internal class ThemePreferences(context: Context) {
    private val dataStore = context.preferencesDataStore

    val themeMode: Flow<AppThemeMode> = dataStore.data
        .map { preferences -> AppThemeMode.fromStored(preferences[themeModeKey]) }
        .catch { error ->
            if (error is IOException) emit(AppThemeMode.SYSTEM) else throw error
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { preferences -> preferences[themeModeKey] = mode.name }
    }
}

internal fun appIsDark(mode: AppThemeMode, systemIsDark: Boolean): Boolean = when (mode) {
    AppThemeMode.SYSTEM -> systemIsDark
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
}

@Composable
internal fun ControleTheme(mode: AppThemeMode, content: @Composable () -> Unit) {
    val isDark = appIsDark(mode, isSystemInDarkTheme())
    val colors = if (isDark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}

internal val LightColors = lightColorScheme(
    primary = Color(0xFF0052A4),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFD7E5F8),
    onSecondaryContainer = Color(0xFF17365D),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF17191C),
    surface = Color.White,
    onSurface = Color(0xFF17191C),
    onSurfaceVariant = Color(0xFF424A54),
    outlineVariant = Color(0xFFD5D9DF)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF80B3FF),
    onPrimary = Color(0xFF06254B),
    secondaryContainer = Color(0xFF153562),
    onSecondaryContainer = Color(0xFFD6E7FF),
    background = Color(0xFF080808),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFFC1CAD5),
    outlineVariant = Color(0xFF494B50)
)
