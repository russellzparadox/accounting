package com.example.accounting.core.data

import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.ChartOfAccounts
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChartSeederTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun secondSeedAddsNoRows() = runBlocking {
        val seeder = ChartSeeder(db)
        val expected = ChartOfAccounts.standard().codes.size

        assertEquals(expected, seeder.seed())
        assertEquals(0, seeder.seed())
        assertEquals(expected, db.accountDao().count())
    }
}
