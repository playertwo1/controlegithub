package com.playertwo.controlegithub

import org.junit.Assert.*
import org.junit.Test

class RepositorySearchTest {
    @Test fun searchMatchesNameIgnoringCaseAndWhitespace() {
        assertEquals("controlegithub", DemoData.search(" CONTROLE ").single().name)
    }
    @Test fun searchMatchesLanguage() { assertEquals("ideias_standard", DemoData.search("python").single().name) }
    @Test fun unknownQueryReturnsEmptyList() { assertTrue(DemoData.search("missing-repository").isEmpty()) }
}
