package com.example.accounting.di

import android.content.Context
import com.example.accounting.core.data.config.ConfigStore
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.nlp.NlParserHttpProvider
import com.example.accounting.core.data.nlp.NlParserProvider
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.repo.PartyRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.data.speech.AndroidSpeechSource
import com.example.accounting.core.data.speech.SpeechSource
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.PartyAccountResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

/** Process-wide singletons. Fiscal-year and time logic uses [Clock] with Asia/Tehran (fixed +03:30, no DST). */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.file(context)

    @Provides
    @Singleton
    fun provideChart(): ChartOfAccounts = ChartOfAccounts.standard()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.system(ZoneId.of("Asia/Tehran"))

    @Provides
    @Singleton
    fun provideLedger(db: AppDatabase, chart: ChartOfAccounts): LedgerRepository = LedgerRepository(db, chart)

    @Provides
    @Singleton
    fun provideSeeder(db: AppDatabase, chart: ChartOfAccounts): ChartSeeder = ChartSeeder(db, chart)

    @Provides
    @Singleton
    fun providePartyRepository(db: AppDatabase): PartyRepository = PartyRepository(db)

    @Provides
    @Singleton
    fun providePartyResolver(repository: PartyRepository): PartyAccountResolver = repository

    @Provides
    @Singleton
    fun provideConfigStore(@ApplicationContext context: Context): ConfigStore = ConfigStore(context)

    @Provides
    @Singleton
    fun provideNlParserProvider(): NlParserProvider = NlParserHttpProvider()

    /** Unscoped on purpose: each VoiceViewModel owns its recognizer and destroys it in onCleared. Swap to CloudSpeechSourceStub or a cloud impl here. */
    @Provides
    fun provideSpeechSource(@ApplicationContext context: Context): SpeechSource = AndroidSpeechSource(context)
}
