/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.di

import com.retally.shared.core.crash.CrashContext
import com.retally.shared.data.category.HttpItemCategoryClassifier
import com.retally.shared.data.device.DeviceRegistrar
import com.retally.shared.domain.category.ItemCategoryClassifier
import com.retally.shared.data.ai.HttpNudgeRepository
import com.retally.shared.data.update.FirestoreAppUpdateRepository
import com.retally.shared.domain.ai.NudgeRepository
import com.retally.shared.domain.update.AppUpdateRepository
import com.retally.shared.core.Platform
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.data.cloud.KtorCloudSyncService
import com.retally.shared.data.cloud.SimulatedCloudSyncService
import com.retally.shared.data.cloud.TornReceiptClient
import com.retally.shared.data.preferences.SettingsAppPreferences
import com.retally.shared.data.receipt.DbReceiptRepository
import com.retally.shared.data.recalls.HttpRecallsRepository
import com.retally.shared.data.shopping.HttpShoppingListRepository
import com.retally.shared.db.ReTallyDatabase
import com.retally.shared.domain.ai.NudgeReadStore
import com.retally.shared.domain.ads.AdsConfig
import com.retally.shared.domain.ads.AdsSession
import com.retally.shared.domain.ads.AdsInitializer
import com.retally.shared.data.auth.HttpOtpService
import com.retally.shared.data.category.CategoryRepository
import com.retally.shared.domain.category.CategorySource
import com.retally.shared.data.store.StoreIconRepository
import com.retally.shared.domain.budget.BudgetRepository
import com.retally.shared.data.auth.FirebaseAuthRepository
import com.retally.shared.domain.auth.OtpService
import com.retally.shared.data.cloud.FirebaseCloudSyncService
import com.retally.shared.data.cloud.FirebaseStorageService
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.cloud.CloudBackend
import com.retally.shared.domain.cloud.CloudConfig
import com.retally.shared.data.receipt.CloudMirroredReceiptRepository
import com.retally.shared.domain.cloud.DeletedReceipts
import com.retally.shared.domain.cloud.ReceiptBackup
import com.retally.shared.domain.cloud.ReceiptRestore
import com.retally.shared.domain.cloud.CloudSyncService
import com.retally.shared.domain.favorites.FavoriteStoresRepository
import com.retally.shared.core.locale.LocaleInfo
import com.retally.shared.core.locale.deviceLocaleInfo
import com.retally.shared.core.locale.effective
import com.retally.shared.domain.location.StoreLocationRepository
import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.recalls.RecallsRepository
import com.retally.shared.domain.notifications.NotificationInboxRepository
import com.retally.shared.domain.budget.BudgetReminderLog
import com.retally.shared.domain.shopping.ShoppingListRepository
import com.retally.shared.presentation.auth.AuthContract
import com.retally.shared.presentation.auth.OtpContract
import com.retally.shared.presentation.budget.BudgetContract
import com.retally.shared.presentation.home.HomeContract
import com.retally.shared.presentation.main.MainContract
import com.retally.shared.presentation.duplicates.DuplicatesContract
import com.retally.shared.presentation.receipts.ReceiptsContract
import com.retally.shared.presentation.scan.ScanReviewContract
import com.retally.shared.presentation.settings.SettingsContract
import com.retally.shared.presentation.recalls.RecallsContract
import com.retally.shared.presentation.notifications.NotificationsContract
import com.retally.shared.presentation.howitworks.HowItWorksContract
import com.retally.shared.presentation.shopping.ShoppingListContract
import com.retally.shared.presentation.splash.SplashContract
import com.retally.shared.presentation.onboarding.OnboardingContract
import com.retally.shared.presentation.storealerts.StoreAlertsContract
import com.retally.shared.presentation.storealerts.StoreAlertsPrompt
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import com.retally.shared.domain.security.APP_CHECK_HEADER
import com.retally.shared.domain.security.AppCheckTokenProvider
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import com.retally.shared.ui.image.installImageLoader
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
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
    /** App Check token for every backend call; see [AppCheckTokenProvider]. */
    val appCheck: AppCheckTokenProvider = AppCheckTokenProvider.None,
)

fun initKoin(
    dependencies: PlatformDependencies,
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication = startKoin {
    // Coil's disk cache for store logos (looked up per store, cached on first display).
    installImageLoader()
    appDeclaration()
    modules(
        platformModule(dependencies),
        dataModule,
        storesModule,
        *extraModules.toTypedArray(),
    )
}

private fun platformModule(deps: PlatformDependencies) = module {
    // Device language + region, collapsed onto a supported launch market (US for now).
    single<LocaleInfo> { deviceLocaleInfo().effective() }
    single { deps.platform }
    single { deps.settings }
    single { deps.adsInitializer }
    single { deps.adsConfig }
    single { AdsSession(initializer = get(), config = get()) }
    single { deps.cloudConfig }
    single { deps.stringResolver }
    single { deps.appCheck }
    single(qualifier = org.koin.core.qualifier.named(VERBOSE_LOGGING)) { deps.verboseNetworkLogging }
}

private const val VERBOSE_LOGGING = "verboseNetworkLogging"

/** Cloud Functions host suffix the App Check token is sent to (region-specific hosts share it). */
private const val RETALLY_FUNCTIONS_HOST = "retally-c5212.cloudfunctions.net"

val dataModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
    single {
        val appCheck: AppCheckTokenProvider = get()
        // Every call to our backend carries the App Check token (the Firebase SDKs attach their
        // own). Fetched per request so a refreshed token is always the one sent, and only for
        // our own host: the token must never travel to a third party.
        val appCheckHeader = createClientPlugin("AppCheck") {
            onRequest { request, _ ->
                if (request.url.host.endsWith(RETALLY_FUNCTIONS_HOST)) {
                    appCheck.token()?.let { request.headers.append(APP_CHECK_HEADER, it) }
                }
            }
        }
        HttpClient {
            install(appCheckHeader)
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
    single { CrashContext(auth = get(), preferences = get(), platform = get()) }
    single { DeviceRegistrar(http = get(), auth = get(), platform = get(), preferences = get(), settings = get()) }
    single<ItemCategoryClassifier> { HttpItemCategoryClassifier(http = get(), auth = get(), json = get()) }
    single<NudgeRepository> { HttpNudgeRepository(http = get(), auth = get(), settings = get(), json = get()) }
    single<AppUpdateRepository> { FirestoreAppUpdateRepository(platform = get(), settings = get(), json = get()) }
    single { FavoriteStoresRepository(settings = get(), json = get()) }
    // Shared SQLite. The SqlDriver single comes from the platform database module
    // (androidDatabaseModule / iosDatabaseModule, passed as extraModules by each host).
    single { ReTallyDatabase(driver = get()) }
    single { StoreLocationRepository(receipts = get(), settings = get(), json = get(), database = get()) }
    single { DeletedReceipts(settings = get()) }
    // Local SQLite store; deletes are mirrored to the cloud so a restore never resurrects them.
    single<ReceiptRepository> {
        CloudMirroredReceiptRepository(
            local = DbReceiptRepository(database = get(), settings = get(), json = get()),
            deleted = get(),
            cloudSync = { get<CloudSyncService>() },
        )
    }
    // Pulls a signed-in shopper's cloud receipts onto the device (sign-in, reinstall, other devices).
    single(createdAtStart = true) {
        ReceiptRestore(cloudSync = get(), repository = get(), auth = get(), deleted = get(), settings = get())
    }
    single { BudgetRepository(database = get(), settings = get(), json = get()) }
    single<ShoppingListRepository> { HttpShoppingListRepository(http = get(), auth = get(), settings = get(), json = get()) }
    single { NotificationInboxRepository(settings = get(), json = get()) }
    single { BudgetReminderLog(settings = get(), json = get(), inbox = get()) }
    single<RecallsRepository> { HttpRecallsRepository(http = get(), auth = get(), settings = get(), json = get()) }
    single<AuthRepository> { FirebaseAuthRepository() }
    single<OtpService> { HttpOtpService(client = get()) }
    single<FirebaseStorageService> { FirebaseStorageService() }
    single {
        TornReceiptClient(
            http = get(),
            auth = get(),
            functionUrl = TornReceiptClient.DEFAULT_URL,
        )
    }
    // App-lifetime so its sign-in hook (upload receipts scanned as a guest) runs from launch.
    single(createdAtStart = true) {
        ReceiptBackup(
            cloudSync = get(),
            repository = get(),
            prefs = get(),
            auth = get(),
            onDeleteImageFiles = { paths ->
                // Delete local image files for pruned receipts.
                // Platform-specific: the paths are filenames or absolute paths.
                // For now, best-effort via the shared resolver (actual deletion needs platform file API).
                // TODO: Wire platform file deletion via expect/actual.
            },
            settings = get(),
        )
    }
    single<CloudSyncService> {
        val config: CloudConfig = get()
        when {
            config.simulate -> SimulatedCloudSyncService()
            config.backend == CloudBackend.FIREBASE -> FirebaseCloudSyncService(
                auth = get(),
                platform = get(),
                deviceIdProvider = { get<AppPreferences>().installationId },
                currencyProvider = { get<LocaleInfo>().currencyCode },
                localeProvider = { get<LocaleInfo>().tag },
                storageService = get(),
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
    // Store-alerts opt-in after the first sign-in; the gate is a single so hosts can ask it.
    single { StoreAlertsPrompt(settings = get(), nearbyStores = get()) }
    factory { StoreAlertsContract.Store(prompt = get()) }
    factory { AuthContract.Store(authRepository = get(), otpService = get(), strings = get(), preferences = get()) }
    factory { OtpContract.Store(otpService = get(), authRepository = get(), strings = get(), preferences = get()) }
    single { CategoryRepository(client = get(), settings = get(), json = get()) } bind CategorySource::class
    single { StoreIconRepository(client = get(), settings = get(), json = get()) }
    single { MainContract.Store(adsSession = get(), categories = get(), storeIcons = get(), receipts = get(), settings = get(), itemClassifier = get(), devices = get(), crashContext = get()) }
    factory {
        HomeContract.Store(
            repository = get(), categories = get(), authRepository = get(), strings = get(), restore = get(),
            backup = get(), inbox = get(), budgets = get(), nudgeRepository = get(), shoppingLists = get(),
            budgetReminders = getOrNull(), budgetReminderLog = get(), appUpdates = get(), platform = get(),
        )
    }
    factory { ScanReviewContract.Store(repository = get(), backup = get(), favorites = get(), strings = get(), categories = get(), tornReceiptClient = get(), storeIcons = get(), itemClassifier = get(), locale = get(), auth = get()) }
    factory { DuplicatesContract.Store(repository = get(), strings = get()) }
    factory { ReceiptsContract.Store(repository = get(), strings = get(), storeIcons = get()) }
    factory { BudgetContract.Store(budgets = get(), receipts = get(), categories = get(), strings = get()) }
    factory { ShoppingListContract.Store(repository = get(), authRepository = get(), strings = get()) }
    factory { RecallsContract.Store(repository = get(), authRepository = get(), strings = get()) }
    factory { NotificationsContract.Store(inbox = get(), shoppingLists = get(), budgetReminderLog = get()) }
    factory { HowItWorksContract.Store() }
    factory {
        SettingsContract.Store(
            preferences = get(),
            receipts = get(),
            cloudSync = get(),
            platform = get(),
            nearbyStores = get(),
            auth = get(),
            favorites = get(),
            recalls = get(),
            shoppingLists = get(),
            nudges = get(),
            strings = get(),
            settings = get(),
        )
    }
}
