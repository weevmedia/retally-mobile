package com.retally.shared.di

import com.retally.shared.core.Platform
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.data.cloud.KtorCloudSyncService
import com.retally.shared.data.cloud.SimulatedCloudSyncService
import com.retally.shared.data.preferences.SettingsAppPreferences
import com.retally.shared.data.receipt.SettingsReceiptRepository
import com.retally.shared.db.ReTallyDatabase
import com.retally.shared.domain.ai.NudgeReadStore
import com.retally.shared.domain.ads.AdsConfig
import com.retally.shared.domain.ads.AdsSession
import com.retally.shared.domain.ads.AdsInitializer
import com.retally.shared.data.auth.HttpOtpService
import com.retally.shared.data.auth.FirebaseAuthRepository
import com.retally.shared.domain.auth.OtpService
import com.retally.shared.data.cloud.FirebaseCloudSyncService
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.cloud.CloudBackend
import com.retally.shared.domain.cloud.CloudConfig
import com.retally.shared.domain.cloud.ReceiptBackup
import com.retally.shared.domain.cloud.CloudSyncService
import com.retally.shared.domain.favorites.FavoriteStoresRepository
import com.retally.shared.domain.location.StoreLocationRepository
import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.presentation.auth.AuthContract
import com.retally.shared.presentation.auth.OtpContract
import com.retally.shared.presentation.home.HomeContract
import com.retally.shared.presentation.main.MainContract
import com.retally.shared.presentation.duplicates.DuplicatesContract
import com.retally.shared.presentation.receipts.ReceiptsContract
import com.retally.shared.presentation.scan.ScanReviewContract
import com.retally.shared.presentation.settings.SettingsContract
import com.retally.shared.presentation.splash.SplashContract
import com.retally.shared.presentation.onboarding.OnboardingContract
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Everything the platform must provide before the shared graph can start. Each app builds
 * one of these in its Application/AppDelegate and calls [initKoin].
 */
data class PlatformDependencies(
    val platform: Platform,
    val settings: Settings,
    val adsInitializer: AdsInitializer,
    val adsConfig: AdsConfig,
    val cloudConfig: CloudConfig,
    /** Debug builds may log HTTP bodies; release must not. */
    val verboseNetworkLogging: Boolean,
    /** Platform string lookup (strings.xml / Localizable.strings). */
    val stringResolver: StringResolver,
)

fun initKoin(
    dependencies: PlatformDependencies,
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication = startKoin {
    appDeclaration()
    modules(
        platformModule(dependencies),
        dataModule,
        storesModule,
        *extraModules.toTypedArray(),
    )
}

private fun platformModule(deps: PlatformDependencies) = module {
    single { deps.platform }
    single { deps.settings }
    single { deps.adsInitializer }
    single { deps.adsConfig }
    single { AdsSession(initializer = get(), config = get()) }
    single { deps.cloudConfig }
    single { deps.stringResolver }
    single(qualifier = org.koin.core.qualifier.named(VERBOSE_LOGGING)) { deps.verboseNetworkLogging }
}

private const val VERBOSE_LOGGING = "verboseNetworkLogging"

val dataModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
    single {
        HttpClient {
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
            install(Logging) {
                val verbose: Boolean = get(org.koin.core.qualifier.named(VERBOSE_LOGGING))
                level = if (verbose) LogLevel.BODY else LogLevel.NONE
            }
        }
    }
    single<AppPreferences> { SettingsAppPreferences(settings = get(), json = get()) }
    single { NudgeReadStore(settings = get()) }
    single { FavoriteStoresRepository(settings = get(), json = get()) }
    // Shared SQLite. The SqlDriver single comes from the platform database module
    // (androidDatabaseModule / iosDatabaseModule, passed as extraModules by each host).
    single { ReTallyDatabase(driver = get()) }
    single { StoreLocationRepository(receipts = get(), settings = get(), json = get(), database = get()) }
    single<ReceiptRepository> { SettingsReceiptRepository(settings = get(), json = get()) }
    single<AuthRepository> { FirebaseAuthRepository() }
    single<OtpService> { HttpOtpService(client = get()) }
    single { ReceiptBackup(cloudSync = get(), repository = get()) }
    single<CloudSyncService> {
        val config: CloudConfig = get()
        when {
            config.simulate -> SimulatedCloudSyncService()
            config.backend == CloudBackend.FIREBASE -> FirebaseCloudSyncService(
                auth = get(),
                platform = get(),
                deviceIdProvider = { get<AppPreferences>().installationId },
            )
            else -> KtorCloudSyncService(
                client = get(),
                config = config,
                platform = get(),
                deviceIdProvider = { get<AppPreferences>().installationId },
            )
        }
    }
}

/**
 * Stores are `factory` so every screen entry gets a fresh instance and `Store.clear()` can
 * cancel its work on exit. Only [MainContract.Store] is a `single` — it outlives its tabs.
 */
val storesModule = module {
    factory { SplashContract.Store(adsSession = get(), preferences = get()) }
    factory { OnboardingContract.Store() }
    factory { AuthContract.Store(authRepository = get(), otpService = get(), strings = get()) }
    factory { OtpContract.Store(otpService = get(), authRepository = get(), strings = get()) }
    single { MainContract.Store(adsSession = get()) }
    factory { HomeContract.Store(repository = get(), authRepository = get(), strings = get()) }
    factory { ScanReviewContract.Store(repository = get(), backup = get(), favorites = get(), strings = get()) }
    factory { DuplicatesContract.Store(repository = get(), strings = get()) }
    factory { ReceiptsContract.Store(repository = get(), strings = get()) }
    factory {
        SettingsContract.Store(
            preferences = get(),
            receipts = get(),
            cloudSync = get(),
            platform = get(),
            nearbyStores = get(),
            auth = get(),
            favorites = get(),
            strings = get(),
        )
    }
}
