/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.di

import com.retally.app.budget.AndroidBudgetReminderScheduler
import com.retally.app.scan.ReceiptTextRecognizer
import com.retally.app.ui.home.HomeViewModel
import com.retally.app.ui.budget.BudgetViewModel
import com.retally.app.ui.scan.ScanReviewViewModel
import com.retally.app.ui.duplicates.DuplicatesViewModel
import com.retally.app.ui.howitworks.HowItWorksViewModel
import com.retally.app.ui.main.MainViewModel
import com.retally.app.ui.receipts.ReceiptsViewModel
import com.retally.app.ui.settings.SettingsViewModel
import com.retally.app.ui.shopping.ShoppingListViewModel
import com.retally.app.ui.alerts.AlertsViewModel
import com.retally.app.ui.notifications.NotificationsViewModel
import com.retally.app.ui.splash.SplashViewModel
import com.retally.app.ui.onboarding.OnboardingViewModel
import com.retally.app.ui.auth.AuthViewModel
import com.retally.app.ui.auth.OtpViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import com.retally.shared.domain.budget.BudgetReminderScheduler
import org.koin.dsl.module

/**
 * Android ViewModel bindings. Stores come from the shared `storesModule` (factory, one per
 * screen instance); each ViewModel wraps one and clears it in `onCleared`.
 */
val viewModelModule = module {
    single<BudgetReminderScheduler> { AndroidBudgetReminderScheduler(androidContext()) }
    viewModel { SplashViewModel(store = get()) }
    viewModel { HowItWorksViewModel(store = get()) }
    viewModel { OnboardingViewModel(store = get()) }
    viewModel { AuthViewModel(store = get()) }
    viewModel { OtpViewModel(store = get()) }
    viewModel { MainViewModel(store = get(), adsConfig = get()) }
    viewModel { HomeViewModel(store = get(), textRecognizer = get(), application = androidApplication()) }
    viewModel { SettingsViewModel(store = get()) }
    viewModel { ScanReviewViewModel(store = get()) }
    viewModel { DuplicatesViewModel(store = get()) }
    viewModel { ReceiptsViewModel(store = get()) }
    viewModel { BudgetViewModel(store = get()) }
    viewModel { ShoppingListViewModel(store = get()) }
    viewModel { AlertsViewModel(store = get()) }
    viewModel { NotificationsViewModel(store = get()) }

    single { ReceiptTextRecognizer(androidContext()) }
}
