package com.example.accounting.core.data.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.nlp.NlParserHttpProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reversible stand-in for the Keystore cipher (AndroidKeyStore does not run under Robolectric). */
object ReversibleTestCipher : TokenCipher {
    private const val PREFIX = "enc:"
    override fun encrypt(plain: String): String = PREFIX + plain.reversed()
    override fun decrypt(stored: String): String? =
        if (stored.startsWith(PREFIX)) stored.removePrefix(PREFIX).reversed() else null
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun rawPrefs() = context.getSharedPreferences("backend_config", Context.MODE_PRIVATE)

    @Test
    fun defaultIsNotConfigured() = runBlocking {
        val config = ConfigStore(context, ReversibleTestCipher).config.first()
        assertEquals(BackendConfig.NOT_CONFIGURED, config)
        assertFalse(config.isConfigured)
    }

    @Test
    fun saveTrimsAndPersistsAcrossInstances() = runBlocking {
        ConfigStore(context, ReversibleTestCipher).save(BackendConfig("  https://backend.test/ ", " token-1 "))

        val reloaded = ConfigStore(context, ReversibleTestCipher).config.first()
        assertEquals(BackendConfig("https://backend.test/", "token-1"), reloaded)
        assertTrue(reloaded.isConfigured)
    }

    @Test
    fun tokenIsNotStoredInPlainTextAndIsNotPrintedInToString() {
        ConfigStore(context, ReversibleTestCipher).save(BackendConfig("https://backend.test", "secret-token-42"))

        val stored = rawPrefs().all.values.map { it.toString() }
        assertTrue(stored.none { it.contains("secret-token-42") })
        assertFalse(BackendConfig("https://backend.test", "secret-token-42").toString().contains("secret-token-42"))
    }

    @Test
    fun legacyPlainTokenIsEncryptedOnFirstRead() = runBlocking {
        rawPrefs().edit().putString("base_url", "https://backend.test").putString("token", "old-token").commit()

        val config = ConfigStore(context, ReversibleTestCipher).config.first()

        assertEquals(BackendConfig("https://backend.test", "old-token"), config)
        assertNull(rawPrefs().getString("token", null))
        assertTrue(rawPrefs().getString("token_enc", "").orEmpty().startsWith("enc:"))
    }

    @Test
    fun undecryptableStoredTokenReadsAsBlankNotCrash() = runBlocking {
        rawPrefs().edit().putString("base_url", "https://backend.test").putString("token_enc", "garbage").commit()

        val config = ConfigStore(context, ReversibleTestCipher).config.first()

        assertEquals("", config.token)
        assertFalse(config.isConfigured)
    }

    @Test
    fun incompleteOrNonHttpConfigIsNotConfigured() {
        assertFalse(BackendConfig("", "token").isConfigured)
        assertFalse(BackendConfig("https://backend.test", "  ").isConfigured)
        assertFalse(BackendConfig("ftp://backend.test", "token").isConfigured)
    }

    @Test
    fun providerReturnsNullUntilConfiguredAndReusesParser() {
        val provider = NlParserHttpProvider()
        assertNull(provider.parserFor(BackendConfig.NOT_CONFIGURED))

        val config = BackendConfig("https://backend.test", "token")
        val first = provider.parserFor(config)
        assertNotNull(first)
        assertTrue(first === provider.parserFor(config))
        assertFalse(first === provider.parserFor(BackendConfig("https://other.test", "token")))
    }
}
