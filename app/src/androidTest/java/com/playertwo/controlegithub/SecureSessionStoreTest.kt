package com.playertwo.controlegithub

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun encryptedCredentialsRestoreAndLogoutDeletesDataAndKey() = runBlocking {
        val store = SecureSessionStore(context)
        val credentials = SessionCredentials("private-access-token", "private-refresh-token", 123_000, 456_000)

        store.save(credentials)
        val contents = File(context.filesDir, "datastore/secure_session.preferences_pb").readBytes()
        assertFalse(contents.toString(Charsets.ISO_8859_1).contains(credentials.accessToken))
        assertFalse(contents.toString(Charsets.ISO_8859_1).contains(credentials.refreshToken!!))
        assertEquals(credentials, SecureSessionStore(context).load())

        store.clear()

        assertNull(store.load())
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse(keyStore.containsAlias("com.playertwo.controlegithub.session.aes"))
    }

    @Test
    fun corruptedCiphertextFailsClosedAndRemovesStoredValue() = runBlocking {
        SecureSessionStore(context).save(SessionCredentials("access", "refresh"))
        context.sessionDataStore.edit {
            it[encryptedSessionKey] = "not-valid-ciphertext"
        }

        assertNull(SecureSessionStore(context).load())
        assertNull(context.sessionDataStore.data.first()[encryptedSessionKey])
    }
}
