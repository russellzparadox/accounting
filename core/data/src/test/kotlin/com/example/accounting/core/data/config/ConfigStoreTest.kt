package com.example.accounting.core.data.config

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun defaultIsNotConfigured() = runBlocking {
        val config = ConfigStore(context).config.first()
        assertEquals(BackendConfig.NOT_CONFIGURED, config)
        assertFalse(config.isConfigured)
    }

    @Test
    fun saveTrimsAndPersistsAcrossInstances() = runBlocking {
        ConfigStore(context).save(BackendConfig("  https://backend.test/ ", " token-1 "))

        val reloaded = ConfigStore(context).config.first()
        assertEquals(BackendConfig("https://backend.test/", "token-1"), reloaded)
        assertTrue(reloaded.isConfigured)
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
