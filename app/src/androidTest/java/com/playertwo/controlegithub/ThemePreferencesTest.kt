package com.playertwo.controlegithub

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemePreferencesTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationTestActivity>()

    @After fun restoreSystemTheme() = runBlocking {
        ThemePreferences(InstrumentationRegistry.getInstrumentation().targetContext)
            .setThemeMode(AppThemeMode.SYSTEM)
    }

    @Test fun appearanceSelectionPersistsAfterActivityRecreationAndBackReturns() {
        val preferences = ThemePreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking { preferences.setThemeMode(AppThemeMode.SYSTEM) }
        compose.waitUntil(5_000) { runBlocking { preferences.themeMode.first() == AppThemeMode.SYSTEM } }
        compose.onNodeWithContentDescription("Configurações de aparência").performClick()
        compose.onNodeWithText("Aparência").assertIsDisplayed()
        compose.onNodeWithText("Sistema").assertIsSelected()

        selectAndWaitForStoredMode(AppThemeMode.LIGHT)
        compose.onNodeWithText("Claro").assertIsSelected()
        selectAndWaitForStoredMode(AppThemeMode.SYSTEM)
        compose.onNodeWithText("Sistema").assertIsSelected()

        selectAndWaitForStoredMode(AppThemeMode.DARK)
        compose.onNodeWithText("Escuro").assertIsSelected()
        compose.onNodeWithText("Repos").assertDoesNotExist()

        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Aparência").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Escuro").assertIsSelected()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Seu código.\nSua visão.\nSeu controle.").assertIsDisplayed()
    }

    @Test fun appearanceCanBeOpenedFromDashboardAndReturnsToDock() {
        compose.onNodeWithText("Explorar demonstração").performClick()
        compose.onNodeWithContentDescription("Configurações de aparência").performClick()
        compose.onNodeWithText("Aparência").assertIsDisplayed()
        compose.onNodeWithText("Início").assertDoesNotExist()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Início").assertIsDisplayed()
    }

    @Test fun supportedThemeColorRolesMeetNormalTextContrast() {
        listOf(LightColors, DarkColors).forEach { scheme ->
            assertContrast(scheme.onBackground, scheme.background)
            assertContrast(scheme.onSurface, scheme.surface)
            assertContrast(scheme.onSurfaceVariant, scheme.background)
            assertContrast(scheme.onSurfaceVariant, scheme.surface)
            assertContrast(scheme.primary, scheme.background)
            assertContrast(scheme.primary, scheme.surface)
            assertContrast(scheme.onPrimary, scheme.primary)
            assertContrast(scheme.onSecondaryContainer, scheme.secondaryContainer)
        }
    }

    @Test fun systemModeTracksSystemAndExplicitModesOverrideIt() {
        assertTrue(appIsDark(AppThemeMode.SYSTEM, true))
        assertTrue(!appIsDark(AppThemeMode.SYSTEM, false))
        assertTrue(appIsDark(AppThemeMode.DARK, false))
        assertTrue(!appIsDark(AppThemeMode.LIGHT, true))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStored("invalid"))
    }

    private fun assertContrast(foreground: Color, background: Color) {
        val lighter = maxOf(luminance(foreground), luminance(background))
        val darker = minOf(luminance(foreground), luminance(background))
        assertTrue("Contrast $foreground on $background was below 4.5:1", (lighter + 0.05) / (darker + 0.05) >= 4.5)
    }

    private fun selectAndWaitForStoredMode(mode: AppThemeMode) {
        compose.onNodeWithText(mode.label).performClick()
        val preferences = ThemePreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.waitUntil(5_000) { runBlocking { preferences.themeMode.first() == mode } }
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
