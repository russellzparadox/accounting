package com.example.accounting.core.data

import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.repo.PartyRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.DomainException
import com.example.accounting.core.domain.PartyKind
import com.example.accounting.core.domain.partyKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PartyRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var parties: PartyRepository

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        runBlocking { ChartSeeder(db).seed() }
        parties = PartyRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun findOrCreateIsStableAcrossSpellingVariants() {
        // Seed already holds the sample معین 1301 and 2101, so the first new codes are 1302 and 2102.
        val first = parties.resolve(PartyKind.CUSTOMER, partyKey("علی احمدی"), "علی احمدی")
        val variant = parties.resolve(PartyKind.CUSTOMER, partyKey("علی‌احمدی  "), "علی‌احمدی")
        val supplier = parties.resolve(PartyKind.SUPPLIER, partyKey("ابرام"), "ابرام")

        assertEquals("1302", first)
        assertEquals(first, variant)
        assertEquals("2102", supplier)
        assertEquals(2, runBlocking { db.partyDao().count() })
    }

    @Test
    fun rangeExhaustionThrows() {
        // CUSTOMER range 1301..1349; 1301 is the sample account, so 48 new parties fit.
        val codes = (1..48).map { parties.resolve(PartyKind.CUSTOMER, "c$it", "c$it") }
        assertEquals("1302", codes.first())
        assertEquals("1349", codes.last())

        assertThrows(DomainException::class.java) {
            parties.resolve(PartyKind.CUSTOMER, "c49", "c49")
        }
    }
}
