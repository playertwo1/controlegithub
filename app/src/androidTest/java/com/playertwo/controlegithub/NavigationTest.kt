package com.playertwo.controlegithub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun exploreSearchAndOpenRepository() {
        compose.onNodeWithText("Explorar demonstração").performClick()
        compose.onNodeWithText("Repos").performClick()
        compose.onNodeWithText("Buscar nome ou linguagem").performTextInput("Python")
        compose.onNodeWithText("ideias_standard").performClick()
        compose.onNodeWithText("Visão geral").assertIsDisplayed()
        compose.onNodeWithText("← Voltar").performClick()
        compose.onNodeWithText("Repositórios").assertIsDisplayed()
    }
}
