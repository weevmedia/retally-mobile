/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.presentation.settings

import com.russhwolf.settings.Settings
import com.retally.shared.core.crash.CrashReporting
import com.retally.shared.domain.ai.NudgeRepository
import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.ui.web.inAppPageUrl
import com.retally.shared.core.Effect
import com.retally.shared.core.Platform
import com.retally.shared.domain.recalls.RecallsRepository
import com.retally.shared.domain.shopping.ShoppingListRepository
import com.retally.shared.domain.auth.Account
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.cloud.CloudSyncResult
import com.retally.shared.domain.cloud.CloudSyncService
import com.retally.shared.domain.favorites.FavoriteStore
import com.retally.shared.domain.favorites.FavoriteStoresRepository
import com.retally.shared.domain.location.StoreLocationRepository
import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.domain.preferences.Preferences
import com.retally.shared.domain.receipt.ReceiptRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * The More menu: profile card, favorite stores, grouped settings (Settings / Legal),
 * and the full-screen Account page (login for guests, logout + delete for members).
 * Cloud backup, duplicate detection, haptics, nearby-store reminders and the legal
 * links live here too.
 */
interface SettingsContract {

    enum class SyncStatus { IDLE, SYNCING, SUCCESS, FAILURE }

    data class State(
        val preferences: Preferences = Preferences(),
        val appVersion: String = "",
        val cloudConfigured: Boolean = false,
        val unsyncedCount: Int = 0,
        val syncStatus: SyncStatus = SyncStatus.IDLE,
        val syncMessage: String? = null,
        val lastSyncedAt: Instant? = null,
        val showDeleteAllConfirmation: Boolean = false,
        val nearbyNotificationsEnabled: Boolean = false,
        /** The store-alerts explanation (same copy as onboarding) shown before the permission prompts. */
        val showNearbyDisclosure: Boolean = false,
        val account: Account = Account.Guest,
        val favoriteStores: List<FavoriteStore> = emptyList(),
        /** Remove mode for the favorite-store circles (tap × to remove). */
        val favoritesEditMode: Boolean = false,
        /**
         * Full-screen store picker: a search box over a 3-column grid of every store seen on
         * receipts, each tile carrying a corner heart to select it; Save commits the draft.
         */
        val showStorePickerPage: Boolean = false,
        /** Search filter typed on the store picker page. */
        val storeSearchQuery: String = "",
        /** Draft selection on the picker page — normalized store keys, committed by Save. */
        val storePickerSelection: Set<String> = emptySet(),
        /** Distinct store names from saved receipts, alphabetical — the picker source. */
        val knownStores: List<String> = emptyList(),
        /** Full-screen Account page: login for guests, logout + delete for members. */
        val showAccountPage: Boolean = false,
        val showLogoutConfirm: Boolean = false,
        val showDeleteAccountConfirm: Boolean = false,
        /** Non-null while the "Edit name" page is open (Edit on the profile card). */
        val nameEdit: NameEdit? = null,
    ) : com.retally.shared.core.State

    /** Draft for the "Edit name" page. */
    data class NameEdit(val firstName: String, val lastName: String, val isSaving: Boolean = false) {
        val canSave: Boolean get() = firstName.isNotBlank() && !isSaving
    }

    sealed class Action : com.retally.shared.core.Action {
        data class SetCloudBackupEnabled(val enabled: Boolean) : Action()
        data class SetHighlightDuplicates(val enabled: Boolean) : Action()
        data class SetHapticsEnabled(val enabled: Boolean) : Action()
        data class SetNearbyNotificationsEnabled(val enabled: Boolean) : Action()
        /** "Turn on" on the store-alerts explanation: run the permission prompts. */
        data object NearbyDisclosureConfirmed : Action()
        /** "Not now" (or outside tap) on the explanation: the toggle stays off. */
        data object NearbyDisclosureDismissed : Action()
        /** Host finished the location+notification permission prompts. */
        data class NearbyPermissionsResult(val granted: Boolean) : Action()
        data object SyncNowClicked : Action()
        /** DEBUG-ONLY: crash on purpose to check Crashlytics end to end. */
        data object CrashTestClicked : Action()
        data object DeleteAllRequested : Action()
        data object DeleteAllConfirmed : Action()
        data object DeleteAllCancelled : Action()
        data object PrivacyPolicyClicked : Action()
        data object TermsClicked : Action()
        data object SupportClicked : Action()
        /** More › Food recalls. */
        data object RecallsClicked : Action()
        data object DismissSyncMessage : Action()
        // ── More menu: account & favorites ──
        data object AccountClicked : Action()
        /** Edit on the profile card (signed-in shoppers): open the "Edit name" page. */
        data object EditNameClicked : Action()
        data class FirstNameChanged(val value: String) : Action()
        data class LastNameChanged(val value: String) : Action()
        data object NameSaveClicked : Action()
        data object NameEditDismissed : Action()
        data object AccountDismissed : Action()
        /** Guest tapped "Log in / Sign up" — the host opens the auth flow. */
        data object LoginClicked : Action()
        data object LogoutRequested : Action()
        data object LogoutConfirmed : Action()
        data object LogoutCancelled : Action()
        data object DeleteAccountRequested : Action()
        data object DeleteAccountConfirmed : Action()
        data object DeleteAccountCancelled : Action()
        data object EditFavoritesToggled : Action()
        data class RemoveFavorite(val key: String) : Action()
        /** The + circle / empty state: open the add-favorites picker. */
        data object AddFavoritesClicked : Action()
        /** Store picker page: back arrow — discards the draft, returns to More. */
        data object StorePickerDismissed : Action()
        /** Store picker page: the search box text changed. */
        data class StoreSearchQueryChanged(val query: String) : Action()
        /** Store picker page: a grid tile (or its heart) tapped — flips the store in the draft. */
        data class StorePickerSelectionToggled(val name: String) : Action()
        /** Store picker page: Save — commits the draft selection, returns to More. */
        data object StorePickerSaved : Action()
    }

    sealed class SideEffect : Effect {
        data class OpenUrl(val url: String) : SideEffect()
        /** Host: show one of our own pages (privacy, terms, support) in the in-app web page screen. */
        data class OpenWebPage(val title: String, val url: String) : SideEffect()
        /** Host: push the food recalls screen. */
        data object OpenRecalls : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
        /** Host must run the location + notification permission prompts, then dispatch [Action.NearbyPermissionsResult]. */
        data object RequestNearbyPermissions : SideEffect()
        /**
         * The nearby store set changed (toggle, or the known-stores directory was
         * wiped) — the host should re-sync geofence monitoring.
         */
        data object NearbyMonitoringChanged : SideEffect()
        /** Guest chose to log in — the host opens the sign-in flow. */
        data object OpenAuth : SideEffect()
        /** The session ended — the host shows the message and routes to sign-in. */
        data class LoggedOut(val message: String) : SideEffect()
        /** The account (and its local data) is gone — the host shows the message; the account flow reverts to guest. */
        data class AccountDeleted(val message: String) : SideEffect()
    }

    class Store(
        private val preferences: AppPreferences,
        private val receipts: ReceiptRepository,
        private val cloudSync: CloudSyncService,
        private val platform: Platform,
        private val nearbyStores: StoreLocationRepository,
        private val auth: AuthRepository,
        private val favorites: FavoriteStoresRepository,
        /** Cached recalls carry purchase matches; cleared with the account's other local data. */
        private val recalls: RecallsRepository? = null,
        /** Smart List cache: built from purchases, cleared with them. */
        private val shoppingLists: ShoppingListRepository? = null,
        /** Home nudges describe the account's purchases too. */
        private val nudges: NudgeRepository? = null,
        private val links: LegalLinks = LegalLinks(),
        private val strings: StringResolver = EnglishStringResolver,
        /** For the debug "Send test crash" (lets a debug build upload that one crash). */
        private val settings: Settings? = null,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        private var syncJob: Job? = null

        override fun initState() = State(
            preferences = preferences.current,
            appVersion = platform.appVersion,
            cloudConfigured = cloudSync.isConfigured,
            lastSyncedAt = preferences.current.lastSyncedAt,
            nearbyNotificationsEnabled = nearbyStores.isEnabled,
        )

        init {
            combine(
                preferences.observe(),
                receipts.observeReceipts(),
                auth.observeAccount(),
                favorites.favorites,
            ) { prefs, list, account, favs ->
                // One entry per store key: "PATEL BROTHERS" and "Patel Brothers" (e.g. after a
                // store-name edit) are the same store — and the picker grid keys tiles by it,
                // so duplicates crash Compose ("key was already used").
                val known = list.mapNotNull { it.storeName.trim().takeIf(String::isNotEmpty) }
                    .distinctBy { FavoriteStoresRepository.keyOf(it) }
                    .sortedBy { it.lowercase() }
                Tuple4(prefs, list.count { it.syncedAt == null }, account, favs to known)
            }.onEach { (prefs, unsynced, account, favsAndKnown) ->
                val (favs, known) = favsAndKnown
                updateStateSafely {
                    it.copy(
                        preferences = prefs,
                        unsyncedCount = unsynced,
                        lastSyncedAt = prefs.lastSyncedAt,
                        account = account,
                        favoriteStores = favs,
                        knownStores = known,
                        // Removing the last favourite ends edit mode: nothing is left to edit.
                        favoritesEditMode = it.favoritesEditMode && favs.isNotEmpty(),
                    )
                }
            }.launchIn(this)
        }

        private fun openWebPage(titleId: String, url: String) =
            sideEffect(SideEffect.OpenWebPage(strings.getString(titleId), inAppPageUrl(url)))

        override fun dispatch(action: Action) {
            when (action) {
                is Action.SetCloudBackupEnabled ->
                    // Guests are local-only; the switch is disabled for them, this is the backstop.
                    if (!currentState.account.isGuest) preferences.update { it.copy(cloudBackupEnabled = action.enabled) }
                is Action.SetHighlightDuplicates -> preferences.update { it.copy(highlightDuplicates = action.enabled) }
                is Action.SetHapticsEnabled -> preferences.update { it.copy(hapticsEnabled = action.enabled) }
                is Action.SetNearbyNotificationsEnabled -> onNearbyToggle(action.enabled)
                Action.NearbyDisclosureConfirmed -> {
                    currentState = currentState.copy(showNearbyDisclosure = false)
                    sideEffect(SideEffect.RequestNearbyPermissions)
                }
                Action.NearbyDisclosureDismissed ->
                    currentState = currentState.copy(showNearbyDisclosure = false)
                is Action.NearbyPermissionsResult -> onNearbyPermissionsResult(action.granted)
                Action.SyncNowClicked -> syncNow()
                Action.CrashTestClicked -> settings?.let { CrashReporting.crashForTest(it) }
                Action.DeleteAllRequested -> currentState = currentState.copy(showDeleteAllConfirmation = true)
                Action.DeleteAllCancelled -> currentState = currentState.copy(showDeleteAllConfirmation = false)
                Action.DeleteAllConfirmed -> deleteAll()
                // Our own pages open in the app (no browser, no address bar).
                Action.PrivacyPolicyClicked -> openWebPage(StringAssetIdentifiers.SETTINGS_PRIVACY, links.privacyPolicy)
                Action.TermsClicked -> openWebPage(StringAssetIdentifiers.SETTINGS_TERMS, links.terms)
                Action.SupportClicked -> openWebPage(StringAssetIdentifiers.SETTINGS_HELP, links.support)
                Action.RecallsClicked -> sideEffect(SideEffect.OpenRecalls)
                Action.DismissSyncMessage -> currentState = currentState.copy(
                    syncMessage = null,
                    syncStatus = SyncStatus.IDLE,
                )
                Action.AccountClicked -> currentState = currentState.copy(showAccountPage = true)
                Action.EditNameClicked -> {
                    val full = currentState.account.displayName?.trim().orEmpty()
                    currentState = currentState.copy(
                        nameEdit = NameEdit(
                            firstName = full.substringBefore(' '),
                            lastName = full.substringAfter(' ', "").trim(),
                        ),
                    )
                }
                is Action.FirstNameChanged -> currentState.nameEdit?.let {
                    currentState = currentState.copy(nameEdit = it.copy(firstName = action.value))
                }
                is Action.LastNameChanged -> currentState.nameEdit?.let {
                    currentState = currentState.copy(nameEdit = it.copy(lastName = action.value))
                }
                Action.NameSaveClicked -> saveName()
                Action.NameEditDismissed -> if (currentState.nameEdit?.isSaving != true) {
                    currentState = currentState.copy(nameEdit = null)
                }
                Action.AccountDismissed -> currentState = currentState.copy(showAccountPage = false)
                Action.LoginClicked -> sideEffect(SideEffect.OpenAuth)
                Action.LogoutRequested -> currentState = currentState.copy(showLogoutConfirm = true)
                Action.LogoutCancelled -> currentState = currentState.copy(showLogoutConfirm = false)
                Action.LogoutConfirmed -> logout()
                Action.DeleteAccountRequested -> currentState = currentState.copy(showDeleteAccountConfirm = true)
                Action.DeleteAccountCancelled -> currentState = currentState.copy(showDeleteAccountConfirm = false)
                Action.DeleteAccountConfirmed -> deleteAccount()
                Action.EditFavoritesToggled -> when {
                    // DONE always leaves edit mode, even if the last favourite was just removed
                    // (it used to open the picker instead, leaving the button stuck on DONE).
                    currentState.favoritesEditMode -> currentState = currentState.copy(favoritesEditMode = false)
                    // Nothing to remove — EDIT opens the picker, matching the empty-state copy.
                    currentState.favoriteStores.isEmpty() -> openStorePicker()
                    else -> currentState = currentState.copy(favoritesEditMode = true)
                }
                is Action.RemoveFavorite -> favorites.remove(action.key)
                Action.AddFavoritesClicked -> openStorePicker()
                Action.StorePickerDismissed -> currentState = currentState.copy(showStorePickerPage = false)
                is Action.StoreSearchQueryChanged ->
                    currentState = currentState.copy(storeSearchQuery = action.query)
                is Action.StorePickerSelectionToggled -> toggleStorePickerSelection(action.name)
                Action.StorePickerSaved -> saveStorePicker()
            }
        }

        /** Opens the picker page, seeding the draft selection from the current favorites. */
        private fun openStorePicker() {
            currentState = currentState.copy(
                showStorePickerPage = true,
                storeSearchQuery = "",
                storePickerSelection = currentState.favoriteStores.map { it.key }.toSet(),
            )
        }

        /** Flips a store in the picker's draft selection (committed by Save). */
        private fun toggleStorePickerSelection(name: String) {
            val key = FavoriteStoresRepository.keyOf(name)
            val selection = currentState.storePickerSelection
            currentState = currentState.copy(
                storePickerSelection = if (selection.contains(key)) selection - key else selection + key,
            )
        }

        /** Commits the picker's draft: adds newly selected stores, removes deselected ones. */
        private fun saveStorePicker() {
            val selection = currentState.storePickerSelection
            val current = currentState.favoriteStores
            val known = currentState.knownStores
            var changed = false
            selection.forEach { key ->
                if (current.none { it.key == key }) {
                    val name = known.firstOrNull { FavoriteStoresRepository.keyOf(it) == key }
                        ?: current.firstOrNull { it.key == key }?.name
                        ?: key
                    favorites.toggle(name)
                    changed = true
                }
            }
            current.forEach { favorite ->
                if (!selection.contains(favorite.key)) {
                    favorites.remove(favorite.key)
                    changed = true
                }
            }
            currentState = currentState.copy(showStorePickerPage = false)
            if (changed) {
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.MORE_FAVORITES_SAVED)))
            }
        }

        /** Saves "First Last" as the account's display name (greeting, profile card, emails). */
        private fun saveName() {
            val draft = currentState.nameEdit ?: return
            if (!draft.canSave) return
            val displayName = listOf(draft.firstName, draft.lastName)
                .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ").take(100)
            if (displayName == currentState.account.displayName?.trim()) {
                currentState = currentState.copy(nameEdit = null)
                return
            }
            currentState = currentState.copy(nameEdit = draft.copy(isSaving = true))
            launch {
                runCatching { auth.updateDisplayName(displayName) }
                    .onSuccess {
                        currentState = currentState.copy(nameEdit = null)
                        sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.MORE_NAME_UPDATED)))
                    }
                    .onFailure {
                        currentState = currentState.copy(nameEdit = currentState.nameEdit?.copy(isSaving = false))
                        sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.MORE_NAME_UPDATE_FAILED)))
                    }
            }
        }

        private fun logout() {
            currentState = currentState.copy(showLogoutConfirm = false)
            launch {
                runCatching { auth.signOut() }
                    .onSuccess {
                        recalls?.clearCache()
                        shoppingLists?.clearCache()
                        nudges?.clearCache()
                        sideEffect(SideEffect.LoggedOut(strings.getString(StringAssetIdentifiers.MORE_LOGGED_OUT)))
                    }
                    .onFailure {
                        sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.MORE_LOGGED_OUT_FAILED)))
                    }
            }
        }

        private fun deleteAccount() {
            currentState = currentState.copy(showDeleteAccountConfirm = false)
            launch {
                // Firebase first: local data is wiped only when the account is actually gone.
                val ok = runCatching { auth.deleteAccount() }.getOrDefault(false)
                if (ok) {
                    receipts.deleteAll()
                    favorites.clear()
                    recalls?.clearCache()
                    shoppingLists?.clearCache()
                    nudges?.clearCache()
                    nearbyStores.clearKnownStores()
                    nearbyStores.setEnabled(false)
                    currentState = currentState.copy(nearbyNotificationsEnabled = false)
                    sideEffect(SideEffect.NearbyMonitoringChanged)
                    sideEffect(SideEffect.AccountDeleted(strings.getString(StringAssetIdentifiers.MORE_ACCOUNT_DELETED)))
                } else {
                    sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.MORE_ACCOUNT_DELETE_FAILED)))
                }
            }
        }

        private fun syncNow() {
            if (currentState.syncStatus == SyncStatus.SYNCING) return
            // Guests have no cloud copy (the upload skips them), so don't report "backed up".
            if (currentState.account.isGuest) {
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.SETTINGS_BACKUP_SUB_GUEST)))
                return
            }
            if (!currentState.preferences.cloudBackupEnabled) {
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.SETTINGS_MSG_TURN_ON_BACKUP)))
                return
            }
            currentState = currentState.copy(syncStatus = SyncStatus.SYNCING, syncMessage = null)
            syncJob = launch {
                // Read the live list rather than state, so a receipt added mid-flight is included.
                val pending = receipts.observeReceipts().first().filter { it.syncedAt == null }
                when (val result = cloudSync.upload(pending)) {
                    is CloudSyncResult.Success -> {
                        receipts.markSynced(result.syncedIds, result.syncedAt)
                        preferences.update { it.copy(lastSyncedAtMillis = result.syncedAt.toEpochMilliseconds()) }
                        currentState = currentState.copy(
                            syncStatus = SyncStatus.SUCCESS,
                            syncMessage = when (val n = result.syncedIds.size) {
                                0 -> strings.getString(StringAssetIdentifiers.SETTINGS_MSG_ALREADY_BACKED_UP)
                                1 -> strings.getString(StringAssetIdentifiers.SETTINGS_MSG_BACKED_UP_ONE)
                                else -> strings.getString(StringAssetIdentifiers.SETTINGS_MSG_BACKED_UP_MANY, n)
                            },
                        )
                    }
                    is CloudSyncResult.Failure -> currentState = currentState.copy(
                        syncStatus = SyncStatus.FAILURE,
                        syncMessage = result.message,
                    )
                }
            }
        }

        private fun deleteAll() {
            currentState = currentState.copy(showDeleteAllConfirmation = false)
            launch {
                receipts.deleteAll()
                // Recall matches point at the purchases that were just deleted.
                recalls?.clearCache()
                // Smart Lists and nudges were built from those receipts.
                shoppingLists?.clearCache()
                nudges?.clearCache()
                // The known-stores directory dies with the receipts — the one
                // path where geofences are intentionally torn down.
                nearbyStores.clearKnownStores()
                sideEffect(SideEffect.NearbyMonitoringChanged)
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.SETTINGS_MSG_ALL_DELETED)))
            }
        }

        private fun onNearbyToggle(enabled: Boolean) {
            if (enabled) {
                // Explain first (the stores' prominent-disclosure requirement), then the host
                // runs the permission prompts; the toggle flips only on grant.
                currentState = currentState.copy(showNearbyDisclosure = true)
            } else {
                nearbyStores.setEnabled(false)
                currentState = currentState.copy(nearbyNotificationsEnabled = false)
                sideEffect(SideEffect.NearbyMonitoringChanged)
            }
        }

        private fun onNearbyPermissionsResult(granted: Boolean) {
            nearbyStores.setEnabled(granted)
            currentState = currentState.copy(nearbyNotificationsEnabled = granted)
            if (granted) {
                sideEffect(SideEffect.NearbyMonitoringChanged)
            } else {
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.SETTINGS_MSG_NEARBY_DENIED)))
            }
        }

        /** Tiny tuple — keeps the 4-flow combine readable without a nested data class. */
        private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    }
}

/** Store-listing URLs. Replace with your hosted pages before submission. */
data class LegalLinks(
    // Firebase Hosting (retally-backend/firestore/hosting). Swap to a custom domain here once
    // one is connected in Firebase → Hosting.
    val privacyPolicy: String = "https://retally-c5212.web.app/privacy",
    val terms: String = "https://retally-c5212.web.app/terms",
    val support: String = "https://retally-c5212.web.app/support",
)