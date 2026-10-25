package com.playertwo.controlegithub

import android.content.Intent
import java.net.URI

internal const val SHORTCUT_PAGE_SUMMARY = 0
internal const val SHORTCUT_PAGE_REPOS = 1
internal const val SHORTCUT_PAGE_WORK = 2
internal const val SHORTCUT_PAGE_NOTIFICATIONS = 3

internal fun resolveAppShortcutPage(action: String?, dataUri: String?): Int? {
    if (action != Intent.ACTION_VIEW || dataUri.isNullOrBlank()) return null
    val parsed = try {
        URI(dataUri.trim())
    } catch (_: Exception) {
        return null
    }
    if (!parsed.scheme.equals("controlegithub", ignoreCase = true)) return null
    if (!parsed.host.equals("shortcut", ignoreCase = true)) return null
    return when (parsed.path) {
        "/summary" -> SHORTCUT_PAGE_SUMMARY
        "/repos" -> SHORTCUT_PAGE_REPOS
        "/work" -> SHORTCUT_PAGE_WORK
        "/notifications" -> SHORTCUT_PAGE_NOTIFICATIONS
        else -> null
    }
}
