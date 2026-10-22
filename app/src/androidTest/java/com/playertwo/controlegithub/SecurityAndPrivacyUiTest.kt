package com.playertwo.controlegithub

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityAndPrivacyUiTest {

    @Test
    fun runtimeApplicationInfoConfirmsAllowBackupIsDisabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val appInfo = context.applicationInfo

        val isAllowBackup = (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        assertTrue("FLAG_ALLOW_BACKUP deve estar desativado no pacote em execução", !isAllowBackup)
    }

    @Test
    fun runtimePackageDeclaresOnlyMinimalPermissions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )

        val requested = packageInfo.requestedPermissions?.toSet() ?: emptySet()
        val invasivePermissions = setOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )

        assertTrue("Nenhuma permissão invasiva pode estar presente", requested.intersect(invasivePermissions).isEmpty())
        assertTrue("INTERNET deve estar presente", requested.contains("android.permission.INTERNET"))
        assertTrue("ACCESS_NETWORK_STATE deve estar presente", requested.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue("POST_NOTIFICATIONS deve estar presente", requested.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue("FOREGROUND_SERVICE deve estar presente", requested.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue("FOREGROUND_SERVICE_DATA_SYNC deve estar presente", requested.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue("REQUEST_INSTALL_PACKAGES deve estar presente", requested.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
    }

    @Test
    fun secureSessionStoreEncryptsAndDecryptsViaAndroidKeyStore() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SecureSessionStore(context)

        val creds = SessionCredentials(
            accessToken = "gho_test_secret_token_12345",
            userLogin = "testuser_security_audit"
        )

        store.save(creds)
        val loaded = store.load()

        assertEquals("Token descriptografado deve coincidir com o original", creds.accessToken, loaded?.accessToken)
        assertEquals("Login deve coincidir", creds.userLogin, loaded?.userLogin)

        // Limpeza
        store.clear()
        val cleared = store.load()
        assertTrue("Sessão deve ser nula após limpeza", cleared == null)
    }
}
