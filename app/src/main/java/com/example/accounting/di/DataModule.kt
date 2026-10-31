package com.example.accounting.di

import android.content.Context
import com.example.accounting.core.data.config.ConfigStore
import com.example.accounting.core.data.config.SettingsRepository
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.nlp.NlParserHttpProvider
import com.example.accounting.core.data.nlp.NlParserProvider
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.repo.PartyRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.data.speech.AndroidRecognizerFactory
import com.example.accounting.core.data.speech.LanguagePackController
import com.example.accounting.core.data.speech.SpeechLanguagePack
import com.example.accounting.core.data.speech.SpeechDiagnostics
import com.example.accounting.core.data.speech.SpeechProbe
import com.example.accounting.core.data.speech.SettingsVoicePreferences
import com.example.accounting.core.data.speech.VoicePreferences
import com.example.accounting.core.data.speech.createVoiceSpeech
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
    fun provideSettings(@ApplicationContext context: Context): SettingsRepository = SettingsRepository(context)

    @Provides
    @Singleton
    fun provideNlParserProvider(): NlParserProvider = NlParserHttpProvider()

    @Provides
    @Singleton
    fun provideVoicePreferences(settings: SettingsRepository, config: ConfigStore): VoicePreferences =
        SettingsVoicePreferences(settings, config)

    /**
     * Unscoped on purpose: each VoiceViewModel owns its engines (on-device, cloud) and releases them in onCleared.
     * Routing between them follows the voice engine setting (see RoutingSpeechSource).
     */
    @Provides
    fun provideSpeechSource(
        @ApplicationContext context: Context,
        prefs: VoicePreferences,
        config: ConfigStore,
    ): SpeechSource = createVoiceSpeech(context, prefs) { config.current }

    /** Last voice error for the Settings diagnostics card. Process-wide, so it is a singleton. */
    @Provides
    @Singleton
    fun provideSpeechDiagnostics(): SpeechDiagnostics = SpeechDiagnostics()

    @Provides
    @Singleton
    fun provideLanguagePack(@ApplicationContext context: Context): SpeechLanguagePack =
        LanguagePackController(AndroidRecognizerFactory(context))

    @Provides
    @Singleton
    fun provideSpeechProbe(
        @ApplicationContext context: Context,
        diagnostics: SpeechDiagnostics,
        pack: SpeechLanguagePack,
    ): SpeechProbe = SpeechProbe(context, AndroidRecognizerFactory(context), diagnostics, pack)
}
