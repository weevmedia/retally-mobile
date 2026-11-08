/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.presentation.home

import com.retally.shared.domain.config.AppConfigRepository
import com.retally.shared.ui.web.inAppPageUrl
import com.retally.shared.domain.budget.BudgetScope
import com.retally.shared.domain.shopping.ShoppingListRepository
import com.retally.shared.domain.ai.NudgeRepository
import com.retally.shared.core.Platform
import com.retally.shared.domain.update.AppUpdateRepository
import com.retally.shared.domain.update.UpdateStatus
import com.retally.shared.domain.budget.daysInMonth
import com.retally.shared.domain.budget.budgetMonthKey
import com.retally.shared.domain.budget.budgetPace
import com.retally.shared.domain.budget.BudgetPace
import com.retally.shared.domain.budget.BudgetRepository
import com.retally.shared.domain.budget.BudgetReminderScheduler
import com.retally.shared.domain.budget.BudgetReminderLog
import com.retally.shared.domain.budget.planBudgetReminders
import com.retally.shared.domain.budget.projectedMonthEnd
import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.core.Effect
import com.retally.shared.data.category.CategoryRepository
import com.retally.shared.domain.auth.Account
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.cloud.BackupOutcome
import com.retally.shared.domain.cloud.ReceiptBackup
import com.retally.shared.domain.cloud.ReceiptRestore
import com.retally.shared.domain.cloud.RestoreOutcome
import com.retally.shared.domain.notifications.NotificationInboxRepository
import com.retally.shared.domain.category.ItemCategory
import com.retally.shared.domain.receipt.CrossReceiptDuplicate
import com.retally.shared.domain.receipt.CrossReceiptDuplicateFinder
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.ai.Nudge
import com.retally.shared.presentation.main.ScanRequests
import com.retally.shared.domain.receipt.SampleReceipts
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.ui.format.formatCurrency
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import com.retally.shared.core.newId
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Home: the receipt list plus the primary "Scan receipt" call to action.
 *
 * Scanning is a platform capability (VisionKit on iOS, ML Kit on Android): the store emits
 * [SideEffect.LaunchScanner], the host runs the camera + OCR and reports back with
 * [Action.TextRecognized] / [Action.ScanFailed]; the store then routes to the review screen
 * with [SideEffect.OpenScanReview]. `Action.AddSampleReceipt` exists so the list, duplicate
 * badges and totals can be exercised without a printer.
 */
/** How long the "N receipts synced" state of the sync modal stays up. */
private const val BACKUP_DONE_SHOWN_MS = 1_800L

interface HomeContract {

    data class State(
        val receipts: List<Receipt> = emptyList(),
        val isLoading: Boolean = true,
        /** Receipt awaiting the user's delete confirmation, if any. */
        val pendingDelete: Receipt? = null,
        /** Items repeated across receipts within the finder's window (milk on Sep 12 and Sep 15). */
        val crossReceiptDuplicates: List<CrossReceiptDuplicate> = emptyList(),
        /** Who is signed in; guests see the "browsing as a guest" banner. */
        val account: Account = Account.Guest,
        /** Server-pushed spending categories, for the Spending breakdown. */
        val categories: List<ItemCategory> = emptyList(),
        /** Downloading the account's receipts after sign-in / reinstall. */
        val isRestoring: Boolean = false,
        /** Receipts scanned before signing in that are being uploaded right now (0 = idle). */
        val backingUpCount: Int = 0,
        /** Just finished: the sync modal shows "N receipts synced" for a moment (0 = hidden). */
        val backedUpCount: Int = 0,
        /** Receipts scanned before signing in, awaiting "Back up / Not now" (0 = no dialog). */
        val pendingBackupPrompt: Int = 0,
        /** Notifications raised in the last 48h and not dismissed — drives the bell's dot. */
        val notificationCount: Int = 0,
        /** Of those, how many arrived since the inbox was last opened — makes the bell shake. */
        val newNotificationCount: Int = 0,
        /** This month's budget; null when none is set (the Home budget bar stays hidden). */
        val budgetAmount: Double? = null,
        /** What this month's budget tracks (all spending or chosen categories). */
        val budgetScope: BudgetScope = BudgetScope.Default,
        /**
         * Server-built nudges for signed-in shoppers, highest priority first — the Home
         * carousel. Empty means still learning (or a guest, who sees the sign-in banner).
         */
        val nudges: List<Nudge> = emptyList(),
        /**
         * Newer build on the store: an "update available" dialog the shopper can dismiss
         * ([UpdateStatus.Available]) or the full-screen "update required" view
         * ([UpdateStatus.Required]) when this build is below the backend's minimum.
         */
        val update: UpdateStatus = UpdateStatus.None,
    ) : com.retally.shared.core.State {
        // ── This calendar month only (device zone): the tiles reset on the 1st. ──

        private fun isThisMonth(instant: kotlin.time.Instant): Boolean {
            val zone = TimeZone.currentSystemDefault()
            val now = Clock.System.now().toLocalDateTime(zone).date
            val d = instant.toLocalDateTime(zone).date
            return d.year == now.year && d.month == now.month
        }

        /** Receipts purchased this month — the "Receipts" tile. */
        val monthReceiptCount: Int get() = receipts.count { isThisMonth(it.purchaseDate) }

        /**
         * Duplicates this month — the "Duplicates" tile: double charges on this month's
         * receipts, plus repeat purchases with a purchase this month.
         */
        val monthDuplicatesCount: Int
            get() = receipts.filter { isThisMonth(it.purchaseDate) }.sumOf { it.duplicates.size } +
                crossReceiptDuplicates.count { dup -> dup.occurrences.any { isThisMonth(it.purchaseDate) } }

        /** 0..1 of this month's budget spent; null without a budget. */
        val budgetFraction: Float?
            get() {
                val budget = budgetAmount?.takeIf { it > 0 } ?: return null
                return (budgetSpent / budget).toFloat().coerceIn(0f, 1f)
            }

        /** On track / warning / over for the Home budget bar; null without a budget. */
        val budgetStatus: BudgetPace?
            get() {
                val budget = budgetAmount?.takeIf { it > 0 } ?: return null
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                return budgetPace(budgetSpent, budget, today.day, daysInMonth(today))
            }

        /** Month-end spend at the current pace, shown on the Home budget card; null without a budget. */
        val budgetProjected: Double?
            get() {
                budgetAmount?.takeIf { it > 0 } ?: return null
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                return projectedMonthEnd(budgetSpent, today.day, daysInMonth(today))
            }

        val hasNotifications: Boolean get() = notificationCount > 0

        val hasNewNotifications: Boolean get() = newNotificationCount > 0

        val totalOvercharge: Double get() = receipts.sumOf { it.overchargeAmount }

        /**
         * Total charged on receipts purchased in the current calendar month — the "Spent in
         * <month>" tile. Calendar month in the device zone, not a rolling 30 days, so the
         * figure matches the month name beside it. A receipt whose total line was never
         * found contributes nothing rather than a guessed item sum.
         */
        val monthToDateTotal: Double
            get() {
                val zone = TimeZone.currentSystemDefault()
                val now = Clock.System.now().toLocalDateTime(zone).date
                return receipts
                    .filter {
                        val d = it.purchaseDate.toLocalDateTime(zone).date
                        d.year == now.year && d.month == now.month
                    }
                    .sumOf { it.total ?: 0.0 }
            }

        /** This month's spending counted the budget's way ([budgetScope]) — the Home budget bar. */
        val budgetSpent: Double
            get() = budgetScope.spentIn(receipts.filter { isThisMonth(it.purchaseDate) })

        /** Newest first; the Home card shows the first [RECENT_RECEIPT_COUNT]. */
        val recentReceipts: List<Receipt> get() = receipts.sortedByDescending { it.purchaseDate }
        /** In-receipt double charges, not yet resolved. */
        val duplicateCount: Int get() = receipts.sumOf { it.duplicates.size }
        /** "Duplicates" tile counter on Home: in-receipt flags plus cross-receipt repeats. */
        val duplicatesFoundCount: Int get() = duplicateCount + crossReceiptDuplicates.size
        val isEmpty: Boolean get() = !isLoading && receipts.isEmpty()

        /**
         * This month's item spend grouped by category, in category-list order.
         * Same calendar-month window as [monthToDateTotal]; categories with no
         * spend are omitted.
         */
        val spendingByCategory: List<CategorySpend>
            get() {
                val zone = TimeZone.currentSystemDefault()
                val now = Clock.System.now().toLocalDateTime(zone).date
                val totals = mutableMapOf<String, Double>()
                receipts
                    .filter {
                        val d = it.purchaseDate.toLocalDateTime(zone).date
                        d.year == now.year && d.month == now.month
                    }
                    .flatMap { it.items }
                    .forEach { item ->
                        totals[item.categoryId] = (totals[item.categoryId] ?: 0.0) + item.price
                    }
                return categories.mapNotNull { category ->
                    val total = totals[category.id] ?: 0.0
                    if (total > 0) CategorySpend(category, total) else null
                }
            }

        companion object {
            /** Home shows only the newest few; the rest live behind "See all". */
            const val RECENT_RECEIPT_COUNT = 2
        }
    }

    /** One row of the Home Spending breakdown. */
    data class CategorySpend(val category: ItemCategory, val total: Double)

    sealed class Action : com.retally.shared.core.Action {
        data object ScanClicked : Action()
        /** OCR finished; `lines` are in reading order, top of the receipt first. */
        data class TextRecognized(val lines: List<String>, val imagePath: String? = null) : Action()
        /** Camera/OCR failed or was denied. Cancelling the scanner dispatches nothing. */
        data class ScanFailed(val reason: String) : Action()
        data class ReceiptClicked(val id: String) : Action()
        data class DeleteRequested(val id: String) : Action()
        data object DeleteConfirmed : Action()
        data object DeleteCancelled : Action()
        data object AddSampleReceipt : Action()
        /** "Duplicates found" tapped → open the cross-receipt list. */
        data object DuplicatesClicked : Action()
        /** Spent tile tapped → open the monthly budget screen. */
        data object BudgetClicked : Action()
        /** "See all" beside Recent receipts → switch to the Receipts tab. */
        data object SeeAllReceiptsClicked : Action()
        /** "See how it works" link on the empty state. */
        data object HowItWorksClicked : Action()
        /** Bell in the top bar → the 48-hour notification inbox. */
        data object NotificationsClicked : Action()
        /** "Sign in to enable cloud sync" on the guest banner. */
        data object SignInClicked : Action()
        /** A nudge card was tapped: open what it points at (Smart List, receipt or budget). */
        data class NudgeClicked(val id: String) : Action()
        /** "Update" on either update view: opens the store page. */
        data object UpdateClicked : Action()
        /** "Not now" on the optional update dialog: hidden until a newer version appears. */
        data object UpdateDismissed : Action()
        /** "Back up" on the after-sign-in dialog: upload the receipts scanned as a guest. */
        data object PendingBackupConfirmed : Action()
        /** "Not now" on that dialog: they stay on this device (Settings → Sync now can still push them). */
        data object PendingBackupDeclined : Action()
    }

    sealed class SideEffect : Effect {
        data class OpenReceipt(val id: String) : SideEffect()
        /** Host: present the document camera, then dispatch [Action.TextRecognized]. */
        data object LaunchScanner : SideEffect()
        /** Host: navigate to the scan-review screen seeded with these lines. */
        data class OpenScanReview(val lines: List<String>, val imagePath: String? = null) : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
        data object OpenDuplicates : SideEffect()
        /** Host: navigate to the monthly budget screen. */
        data object OpenBudget : SideEffect()
        /** Bell: notifications the app raised in the last 48 hours. (Food recalls live under More.) */
        data object OpenNotifications : SideEffect()
        /** Host: select the Receipts tab (Home only lists the newest few). */
        data object OpenReceipts : SideEffect()
        /** Host: present the sign-in / account-linking flow. */
        data object OpenSignIn : SideEffect()
        /** Host: switch to the Smart List tab (the store is already queued on the repository). */
        data object OpenShoppingList : SideEffect()
        /**
         * Host: open "How it works" — the built-in demo screen, or, when the admin console set
         * a link, that page in the in-app web view ([title] / [url] non-null).
         */
        data class OpenHowItWorks(val title: String? = null, val url: String? = null) : SideEffect()
    }

    @OptIn(FlowPreview::class) // debounce
    class Store(
        private val repository: ReceiptRepository,
        private val categories: CategoryRepository,
        private val crossReceiptFinder: CrossReceiptDuplicateFinder = CrossReceiptDuplicateFinder(),
        authRepository: AuthRepository? = null,
        private val strings: StringResolver = EnglishStringResolver,
        restore: ReceiptRestore? = null,
        private val backup: ReceiptBackup? = null,
        private val inbox: NotificationInboxRepository? = null,
        private val budgets: BudgetRepository? = null,
        private val nudgeRepository: NudgeRepository? = null,
        private val shoppingLists: ShoppingListRepository? = null,
        /** Daily "budget is orange / red" local notifications; null in tests and previews. */
        private val budgetReminders: BudgetReminderScheduler? = null,
        /** Files fired budget notifications under the bell; null in tests and previews. */
        private val budgetReminderLog: BudgetReminderLog? = null,
        /** Store version check; null in tests and previews (no update views). */
        private val appUpdates: AppUpdateRepository? = null,
        /** Opens the store page for an update; null in tests and previews. */
        private val platform: Platform? = null,
        /** How it works link from the admin console; null = built-in screen. */
        private val appConfig: AppConfigRepository? = null,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        override fun initState() = State()

        init {
            authRepository?.observeAccount()
                ?.onEach { account ->
                    updateStateSafely { it.copy(account = account) }
                    // Signed-in: fetch nudges (throttled in the repository); guests get none.
                    nudgeRepository?.let { repo -> launch { repo.refresh() } }
                }
                ?.launchIn(this)
            nudgeRepository?.nudges
                ?.onEach { list -> updateStateSafely { it.copy(nudges = list) } }
                ?.launchIn(this)
            // Cached config answers instantly; a network read only when the cache is stale.
            appUpdates?.let { repo ->
                launch {
                    val status = runCatching { repo.check() }.getOrDefault(UpdateStatus.None)
                    updateStateSafely { it.copy(update = status) }
                }
            }
            repository.observeReceipts()
                .onEach { receipts ->
                    val cross = crossReceiptFinder.find(receipts)
                    updateStateSafely { it.copy(receipts = receipts, crossReceiptDuplicates = cross, isLoading = false) }
                }
                .launchIn(this)
            categories.categories
                .onEach { list -> updateStateSafely { it.copy(categories = list) } }
                .launchIn(this)
            restore?.let { r ->
                r.isRestoring
                    .onEach { restoring -> updateStateSafely { it.copy(isRestoring = restoring) } }
                    .launchIn(this)
                // Outcome is held until shown: the restore kicked off by sign-in usually
                // finishes before Home exists, and a failure must never be silent.
                r.outcome
                    .onEach { outcome ->
                        val message = when (outcome) {
                            null -> return@onEach
                            is RestoreOutcome.Restored -> when (outcome.count) {
                                0 -> null
                                1 -> strings.getString(StringAssetIdentifiers.HOME_MSG_RESTORED_ONE)
                                else -> strings.getString(StringAssetIdentifiers.HOME_MSG_RESTORED, outcome.count)
                            }
                            is RestoreOutcome.Failed ->
                                strings.getString(StringAssetIdentifiers.HOME_MSG_RESTORE_FAILED, outcome.message)
                        }
                        r.clearOutcome()
                        message?.let { sideEffect(SideEffect.ShowMessage(it)) }
                    }
                    .launchIn(this)
            }
            backup?.let { b ->
                b.backingUpCount
                    .onEach { count -> updateStateSafely { it.copy(backingUpCount = count) } }
                    .launchIn(this)
                b.pendingPrompt
                    .onEach { count -> updateStateSafely { it.copy(pendingBackupPrompt = count) } }
                    .launchIn(this)
                b.outcome
                    .onEach { outcome ->
                        when (outcome) {
                            null -> return@onEach
                            is BackupOutcome.BackedUp -> if (outcome.count > 0) {
                                // The sync modal flips to its "synced" state, then goes away.
                                updateStateSafely { it.copy(backedUpCount = outcome.count) }
                                launch {
                                    delay(BACKUP_DONE_SHOWN_MS)
                                    updateStateSafely { it.copy(backedUpCount = 0) }
                                }
                            }
                            is BackupOutcome.Failed -> sideEffect(
                                SideEffect.ShowMessage(
                                    strings.getString(StringAssetIdentifiers.HOME_MSG_BACKUP_FAILED, outcome.count, outcome.message),
                                ),
                            )
                        }
                        b.clearOutcome()
                    }
                    .launchIn(this)
            }
            budgets?.let { repo ->
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                repo.observeBudget(budgetMonthKey(today))
                    .onEach { amount -> updateStateSafely { it.copy(budgetAmount = amount) } }
                    .launchIn(this)
                repo.observeScope(budgetMonthKey(today))
                    .onEach { scope -> updateStateSafely { it.copy(budgetScope = scope) } }
                    .launchIn(this)
            }
            inbox?.let { box ->
                box.prune() // drop anything that expired while the app was closed
                combine(box.notifications, box.lastSeenAtMillis) { list, lastSeen ->
                    list.size to list.count { it.postedAtMillis > lastSeen }
                }
                    .onEach { (total, new) ->
                        updateStateSafely { it.copy(notificationCount = total, newNotificationCount = new) }
                    }
                    .launchIn(this)
            }
            budgetReminders?.let { scheduler ->
                // Re-plan whenever the budget or this month's spend changes (receipt added, budget
                // edited, scope switched). Waits for receipts to load so it never plans from $0.
                observeState()
                    .filter { !it.isLoading }
                    .map { it.budgetAmount to it.budgetSpent }
                    .distinctUntilChanged()
                    .debounce(REMINDER_REPLAN_DEBOUNCE_MS)
                    .onEach { (budget, spent) ->
                        val plan = planBudgetReminders(
                            spent = spent,
                            budget = budget,
                            now = Clock.System.now(),
                            timeZone = TimeZone.currentSystemDefault(),
                            strings = strings,
                            formatMoney = ::formatCurrency,
                        )
                        budgetReminderLog?.replacePlan(plan) // bell copy of each notification
                        scheduler.replaceAll(plan)
                    }
                    .launchIn(this)
            }
            budgetReminderLog?.let { log ->
                // Budget notifications are shown by the OS on their own, so file the ones that
                // have fired into the bell: now (missed while closed) and every minute while open.
                launch {
                    while (isActive) {
                        log.collectFired()
                        delay(BELL_SYNC_INTERVAL_MS)
                    }
                }
            }
            // The shell's scan FAB routes here so the scanner flow stays owned by Home.
            ScanRequests.flow
                .onEach { dispatch(Action.ScanClicked) }
                .launchIn(this)
        }

        override fun dispatch(action: Action) {
            when (action) {
                Action.ScanClicked -> sideEffect(SideEffect.LaunchScanner)
                is Action.TextRecognized -> {
                    val lines = action.lines.map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.isEmpty()) {
                        sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.HOME_MSG_NO_TEXT)))
                    } else {
                        sideEffect(SideEffect.OpenScanReview(lines, action.imagePath))
                    }
                }
                is Action.ScanFailed -> sideEffect(SideEffect.ShowMessage(action.reason))
                is Action.ReceiptClicked -> sideEffect(SideEffect.OpenReceipt(action.id))
                is Action.DeleteRequested -> currentState = currentState.copy(
                    pendingDelete = currentState.receipts.firstOrNull { it.id == action.id },
                )
                Action.DeleteCancelled -> currentState = currentState.copy(pendingDelete = null)
                Action.DeleteConfirmed -> confirmDelete()
                Action.DuplicatesClicked -> sideEffect(SideEffect.OpenDuplicates)
                Action.BudgetClicked -> sideEffect(SideEffect.OpenBudget)
                Action.SeeAllReceiptsClicked -> sideEffect(SideEffect.OpenReceipts)
                Action.HowItWorksClicked -> {
                    val url = appConfig?.config?.value?.links?.howItWorks?.takeIf { it.isNotBlank() }
                    sideEffect(
                        if (url == null) SideEffect.OpenHowItWorks()
                        else SideEffect.OpenHowItWorks(strings.getString(StringAssetIdentifiers.HOW_IT_WORKS_TITLE), inAppPageUrl(url)),
                    )
                }
                Action.NotificationsClicked -> sideEffect(SideEffect.OpenNotifications)
                // Sign-in / account linking is not built yet: say so instead of routing to the host.
                // Guest banner "Sign in to enable cloud sync": open the same sign-in flow as More › Log in.
                Action.SignInClicked -> sideEffect(SideEffect.OpenSignIn)
                is Action.NudgeClicked -> openNudge(action.id)
                Action.UpdateClicked -> openStore()
                Action.PendingBackupConfirmed -> backup?.confirmPendingBackup()
                Action.PendingBackupDeclined -> backup?.declinePendingBackup()
                Action.UpdateDismissed -> {
                    val status = currentState.update as? UpdateStatus.Available ?: return
                    appUpdates?.dismiss(status.version)
                    currentState = currentState.copy(update = UpdateStatus.None)
                }
                Action.AddSampleReceipt -> launch {
                    repository.upsert(SampleReceipts.make(idFactory = { newId() }))
                    sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.HOME_MSG_SAMPLE_ADDED)))
                }
            }
        }

        private fun openStore() {
            val url = when (val status = currentState.update) {
                is UpdateStatus.Available -> status.storeUrl
                is UpdateStatus.Required -> status.storeUrl
                UpdateStatus.None -> return
            }
            if (platform?.openUrl(url) != true) {
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.UPDATE_STORE_FAILED)))
            }
        }

        private fun openNudge(id: String) {
            val action = currentState.nudges.firstOrNull { it.id == id }?.action ?: return
            when (action.type) {
                "shopping_list" -> {
                    shoppingLists?.pendingStoreName = action.storeName
                    sideEffect(SideEffect.OpenShoppingList)
                }
                "receipt" -> action.receiptId?.let { rid ->
                    // Only if the receipt is on this device (it may be one deleted locally).
                    if (currentState.receipts.any { it.id == rid }) sideEffect(SideEffect.OpenReceipt(rid))
                }
                "budget" -> sideEffect(SideEffect.OpenBudget)
                else -> Unit // a kind of action this app version doesn't know yet
            }
        }

        private fun confirmDelete() {
            val target = currentState.pendingDelete ?: return
            currentState = currentState.copy(pendingDelete = null)
            launch {
                repository.delete(target.id)
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.HOME_MSG_DELETED, target.storeName)))
            }
        }

        private companion object {
            /** Receipts restore in bursts; wait for the spend to settle before re-planning reminders. */
            const val REMINDER_REPLAN_DEBOUNCE_MS = 1_000L

            /** How often fired budget notifications are copied to the bell while Home is alive. */
            const val BELL_SYNC_INTERVAL_MS = 60_000L
        }
    }
}
