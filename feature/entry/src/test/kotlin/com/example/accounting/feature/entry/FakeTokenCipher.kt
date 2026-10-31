package com.example.accounting.feature.entry

import com.example.accounting.core.data.config.TokenCipher

/** Reversible stand-in for the Keystore cipher. AndroidKeyStore does not run under Robolectric. */
object FakeTokenCipher : TokenCipher {
    private const val PREFIX = "enc:"
    override fun encrypt(plain: String): String = PREFIX + plain.reversed()
    override fun decrypt(stored: String): String? =
        if (stored.startsWith(PREFIX)) stored.removePrefix(PREFIX).reversed() else null
}
