package com.playertwo.controlegithub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/** Isolated navigation host; keeps emulator account storage out of navigation tests. */
class NavigationTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = remember { ThemePreferences(this@NavigationTestActivity) }
            val themeMode by preferences.themeMode.collectAsState(initial = AppThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()
            var preferenceError by remember { mutableStateOf(false) }
            ControleTheme(themeMode) {
                ControleApp(
                    themeMode = themeMode,
                    preferenceError = preferenceError,
                    apiClient = GitHubHttpClient(),
                    session = null,
                    sessionRestoring = false,
                    sessionRetry = false,
                    sessionStorageError = false,
                    onRetrySession = {},
                    onRetrySessionCleanup = {},
                    onConnected = { false },
                    onSessionExpired = {},
                    onLogout = {},
                    onThemeModeChange = { mode ->
                        scope.launch {
                            runCatching { preferences.setThemeMode(mode) }
                                .onSuccess { preferenceError = false }
                                .onFailure { preferenceError = true }
                        }
                    }
                )
            }
        }
    }
}
