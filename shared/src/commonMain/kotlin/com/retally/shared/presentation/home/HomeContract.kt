package com.retally.shared.presentation.home

import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.core.Effect
import com.retally.shared.data.category.CategoryRepository
import com.retally.shared.domain.auth.Account
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.category.ItemCategory
import com.retally.shared.domain.receipt.CrossReceiptDuplicate
import com.retally.shared.domain.receipt.CrossReceiptDuplicateFinder
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.ai.Nudge
import com.retally.shared.domain.ai.NudgeEngine
import com.retally.shared.presentation.main.ScanRequests
import com.retally.shared.domain.receipt.SampleReceipts
import com.retally.shared.domain.receipt.model.Receipt
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import com.retally.shared.core.newId
import kotlinx.coroutines.launch

/**
 * Home: the receipt list plus the primary "Scan receipt" call to action.
 *
 * Scanning is a platform capability (VisionKit on iOS, ML Kit on Android): the store emits
 * [SideEffect.LaunchScanner], the host runs the camera + OCR and reports back with
 * [Action.TextRecognized] / [Action.ScanFailed]; the store then routes to the review screen
 * with [SideEffect.OpenScanReview]. `Action.AddSampleReceipt` exists so the list, duplicate
 * badges and totals can be exercised without a printer.
 */
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
    ) : com.retally.shared.core.State {
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

        /** Newest first; the Home card shows the first [RECENT_RECEIPT_COUNT]. */
        val recentReceipts: List<Receipt> get() = receipts.sortedByDescending { it.purchaseDate }
        /** In-receipt double charges, not yet resolved. */
        val duplicateCount: Int get() = receipts.sumOf { it.duplicates.size }
        /** "Duplicates" tile counter on Home: in-receipt flags plus cross-receipt repeats. */
        val duplicatesFoundCount: Int get() = duplicateCount + crossReceiptDuplicates.size
        /** Top AI nudge for the Home card, ranked by the on-device nudge engine; null while history is too thin. */
        val topNudge: Nudge?
            get() = NudgeEngine.topNudge(receipts)
        /**
         * Every nudge firing right now, highest priority first.
         * The Home carousel pages through these; empty means still learning.
         */
        val allNudges: List<Nudge>
            get() = NudgeEngine.allNudges(receipts)
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
        /** "See all" beside Recent receipts → switch to the Receipts tab. */
        data object SeeAllReceiptsClicked : Action()
        /** "See how it works" link on the empty state. */
        data object HowItWorksClicked : Action()
        /** Bell in the top bar (notifications are not built yet). */
        data object NotificationsClicked : Action()
        /** "Sign in to enable cloud sync" on the guest banner. */
        data object SignInClicked : Action()
    }

    sealed class SideEffect : Effect {
        data class OpenReceipt(val id: String) : SideEffect()
        /** Host: present the document camera, then dispatch [Action.TextRecognized]. */
        data object LaunchScanner : SideEffect()
        /** Host: navigate to the scan-review screen seeded with these lines. */
        data class OpenScanReview(val lines: List<String>, val imagePath: String? = null) : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
        data object OpenDuplicates : SideEffect()
        /** Host: select the Receipts tab (Home only lists the newest few). */
        data object OpenReceipts : SideEffect()
        /** Host: present the sign-in / account-linking flow. */
        data object OpenSignIn : SideEffect()
    }

    class Store(
        private val repository: ReceiptRepository,
        private val categories: CategoryRepository,
        private val crossReceiptFinder: CrossReceiptDuplicateFinder = CrossReceiptDuplicateFinder(),
        authRepository: AuthRepository? = null,
        private val strings: StringResolver = EnglishStringResolver,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        override fun initState() = State()

        init {
            authRepository?.observeAccount()
                ?.onEach { account -> updateStateSafely { it.copy(account = account) } }
                ?.launchIn(this)
            repository.observeReceipts()
                .onEach { receipts ->
                    val cross = crossReceiptFinder.find(receipts)
                    updateStateSafely { it.copy(receipts = receipts, crossReceiptDuplicates = cross, isLoading = false) }
                }
                .launchIn(this)
            categories.categories
                .onEach { list -> updateStateSafely { it.copy(categories = list) } }
                .launchIn(this)
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
                Action.SeeAllReceiptsClicked -> sideEffect(SideEffect.OpenReceipts)
                Action.HowItWorksClicked -> sideEffect(
                    SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.HOME_MSG_HOW_IT_WORKS)),
                )
                Action.NotificationsClicked -> sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.HOME_MSG_NOTIFICATIONS_SOON)))
                // Sign-in / account linking is not built yet: say so instead of routing to the host.
                Action.SignInClicked -> sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.HOME_MSG_SIGN_IN_SOON)))
                Action.AddSampleReceipt -> launch {
                    repository.upsert(SampleReceipts.make(idFactory = { newId() }))
                    sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.HOME_MSG_SAMPLE_ADDED)))
                }
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
    }
}
