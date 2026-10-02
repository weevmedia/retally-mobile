package com.retally.app.di

import com.retally.app.scan.ReceiptTextRecognizer
import com.retally.app.ui.home.HomeViewModel
import com.retally.app.ui.scan.ScanReviewViewModel
import com.retally.app.ui.duplicates.DuplicatesViewModel
import com.retally.app.ui.main.MainViewModel
import com.retally.app.ui.receipts.ReceiptsViewModel
import com.retally.app.ui.settings.SettingsViewModel
import com.retally.app.ui.splash.SplashViewModel
import com.retally.app.ui.onboarding.OnboardingViewModel
import com.retally.app.ui.auth.AuthViewModel
import com.retally.app.ui.auth.OtpViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Android ViewModel bindings. Stores come from the shared `storesModule` (factory, one per
 * screen instance); each ViewModel wraps one and clears it in `onCleared`.
 */
val viewModelModule = module {
    viewModel { SplashViewModel(store = get()) }
    viewModel { OnboardingViewModel(store = get()) }
    viewModel { AuthViewModel(store = get()) }
    viewModel { OtpViewModel(store = get()) }
    viewModel { MainViewModel(store = get(), adsConfig = get()) }
    viewModel { HomeViewModel(store = get(), textRecognizer = get(), application = androidApplication()) }
    viewModel { SettingsViewModel(store = get()) }
    viewModel { ScanReviewViewModel(store = get()) }
    viewModel { DuplicatesViewModel(store = get()) }
    viewModel { ReceiptsViewModel(store = get()) }

    single { ReceiptTextRecognizer(androidContext()) }
}
