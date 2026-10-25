package com.playertwo.controlegithub

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppShortcutsTest {
    @Test
    fun mapsAllFourStaticShortcutsToExpectedPages() {
        assertEquals(
            SHORTCUT_PAGE_SUMMARY,
            resolveAppShortcutPage(Intent.ACTION_VIEW, "controlegithub://shortcut/summary")
        )
        assertEquals(
            SHORTCUT_PAGE_REPOS,
            resolveAppShortcutPage(Intent.ACTION_VIEW, "controlegithub://shortcut/repos")
        )
        assertEquals(
            SHORTCUT_PAGE_WORK,
            resolveAppShortcutPage(Intent.ACTION_VIEW, "controlegithub://shortcut/work")
        )
        assertEquals(
            SHORTCUT_PAGE_NOTIFICATIONS,
            resolveAppShortcutPage(Intent.ACTION_VIEW, "controlegithub://shortcut/notifications")
        )
    }

    @Test
    fun rejectsUnknownPathsInvalidSchemesAndNonViewActions() {
        assertNull(resolveAppShortcutPage(Intent.ACTION_MAIN, "controlegithub://shortcut/summary"))
        assertNull(resolveAppShortcutPage(null, "controlegithub://shortcut/summary"))
        assertNull(resolveAppShortcutPage(Intent.ACTION_VIEW, null))
        assertNull(resolveAppShortcutPage(Intent.ACTION_VIEW, ""))
        assertNull(resolveAppShortcutPage(Intent.ACTION_VIEW, "https://github.com/shortcut/summary"))
        assertNull(resolveAppShortcutPage(Intent.ACTION_VIEW, "controlegithub://other/summary"))
        assertNull(resolveAppShortcutPage(Intent.ACTION_VIEW, "controlegithub://shortcut/unknown"))
        assertNull(resolveAppShortcutPage(Intent.ACTION_VIEW, "not a valid uri"))
    }
}
