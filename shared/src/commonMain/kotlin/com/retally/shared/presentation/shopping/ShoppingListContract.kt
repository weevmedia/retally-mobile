package com.retally.shared.presentation.shopping

import com.retally.shared.core.Effect
import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.shopping.ShoppingList
import com.retally.shared.domain.shopping.ShoppingListLoadResult
import com.retally.shared.domain.shopping.ShoppingListRepository
import com.retally.shared.domain.shopping.SuggestedItem
import com.retally.shared.domain.shopping.normalizeStoreName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * AI shopping list tab (signed-in users). Lists are built server-side by Gemini from the
 * shopper's synced receipts; this screen shows the cached list instantly, refreshes in the
 * background, and only shows the full "Preparing your list" view when nothing is cached.
 * Check-off, dismiss and added items are kept on the device per store.
 */
interface ShoppingListContract {

    data class State(
        val isGuest: Boolean = false,
        /** Full-screen "Preparing your list" — only when there is no cached list to show. */
        val isPreparing: Boolean = false,
        /** Background refresh while a list is already visible. */
        val isRefreshing: Boolean = false,
        /** Index into the rotating status lines of the preparing view. */
        val preparingStep: Int = 0,
        val lists: List<ShoppingList> = emptyList(),
        val selectedStoreKey: String? = null,
        val checked: Set<String> = emptySet(),
        val dismissed: Set<String> = emptySet(),
        val added: List<String> = emptyList(),
        /** Signed in, but too few synced receipts to predict anything yet. */
        val noHistory: Boolean = false,
        /** First load failed and there is nothing cached to fall back to. */
        val loadFailed: Boolean = false,
        val draftItem: String = "",
    ) : com.retally.shared.core.State {
        val selected: ShoppingList?
            get() = lists.firstOrNull { it.storeKey == selectedStoreKey } ?: lists.firstOrNull()

        private fun visible(items: List<SuggestedItem>?): List<SuggestedItem> =
            items.orEmpty().filterNot { it.item in dismissed }

        val dueItems: List<SuggestedItem> get() = visible(selected?.dueHere)
        val alsoHereItems: List<SuggestedItem> get() = visible(selected?.alsoHere)

        /** Staples not already on the list (added or suggested). */
        val stapleItems: List<SuggestedItem>
            get() {
                val onList = (dueItems + alsoHereItems).map { it.item }.toSet() + added
                return visible(selected?.frequent).filterNot { it.item in onList }
            }

        val isEmptyList: Boolean
            get() = selected != null && dueItems.isEmpty() && alsoHereItems.isEmpty() && added.isEmpty()
    }

    sealed class Action : com.retally.shared.core.Action {
        data object Appeared : Action()
        data object Refresh : Action()
        /** Opened from a store notification: preselect that store's list. */
        data class OpenForStore(val storeName: String) : Action()
        data class SelectStore(val storeKey: String) : Action()
        data class ToggleItem(val item: String) : Action()
        data class DismissItem(val item: String) : Action()
        data class AddStaple(val item: String) : Action()
        data class DraftChanged(val text: String) : Action()
        data object AddDraft : Action()
        data object DoneShopping : Action()
        data object ShareClicked : Action()
        data object SignInClicked : Action()
    }

    sealed class SideEffect : Effect {
        data class ShowMessage(val message: String) : SideEffect()
        data object OpenSignIn : SideEffect()
        data class ShareText(val text: String) : SideEffect()
    }

    class Store(
        private val repository: ShoppingListRepository,
        authRepository: AuthRepository,
        private val strings: StringResolver = EnglishStringResolver,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        private var loadJob: Job? = null
        private var stepJob: Job? = null

        override fun initState() = State(lists = repository.lists.value)

        init {
            authRepository.observeAccount()
                .map { it.isGuest }
                .distinctUntilChanged()
                .onEach { guest ->
                    currentState = currentState.copy(isGuest = guest)
                    if (!guest) load(refresh = false)
                }
                .launchIn(this)
            repository.lists
                .onEach { lists -> currentState = currentState.copy(lists = lists).withEdits() }
                .launchIn(this)
        }

        override fun dispatch(action: Action) {
            when (action) {
                Action.Appeared -> {
                    val pending = repository.pendingStoreName
                    if (pending != null) {
                        repository.pendingStoreName = null
                        dispatch(Action.OpenForStore(pending))
                    } else if (!currentState.isGuest && loadJob?.isActive != true) {
                        load(refresh = false)
                    }
                }
                Action.Refresh -> load(refresh = true)
                is Action.OpenForStore -> {
                    val key = normalizeStoreName(action.storeName)
                    currentState = currentState.copy(selectedStoreKey = key).withEdits()
                    load(refresh = false, storeName = action.storeName)
                }
                is Action.SelectStore -> currentState = currentState.copy(selectedStoreKey = action.storeKey).withEdits()
                is Action.ToggleItem -> withStore { key ->
                    repository.setChecked(key, action.item, action.item !in currentState.checked)
                }
                is Action.DismissItem -> withStore { key ->
                    repository.dismiss(key, action.item)
                    sideEffect(SideEffect.ShowMessage(strings.getString(S.SHOPPING_DISMISSED, action.item)))
                }
                is Action.AddStaple -> withStore { key -> repository.addItem(key, action.item) }
                is Action.DraftChanged -> currentState = currentState.copy(draftItem = action.text)
                Action.AddDraft -> withStore { key ->
                    repository.addItem(key, currentState.draftItem)
                    currentState = currentState.copy(draftItem = "")
                }
                Action.DoneShopping -> withStore { key -> repository.finishShopping(key) }
                Action.ShareClicked -> currentState.selected?.let { sideEffect(SideEffect.ShareText(shareText(currentState))) }
                Action.SignInClicked -> sideEffect(SideEffect.OpenSignIn)
            }
        }

        /** Runs an edit for the selected store, then re-reads the edits into state. */
        private fun withStore(block: (String) -> Unit) {
            val key = currentState.selected?.storeKey ?: return
            block(key)
            currentState = currentState.withEdits()
        }

        private fun State.withEdits(): State {
            val key = selected?.storeKey ?: return copy(checked = emptySet(), dismissed = emptySet(), added = emptyList())
            return copy(
                checked = repository.checkedItems(key),
                dismissed = repository.dismissedItems(key),
                added = repository.addedItems(key),
            )
        }

        private fun load(refresh: Boolean, storeName: String? = null) {
            loadJob?.cancel()
            val hasList = currentState.lists.isNotEmpty()
            currentState = currentState.copy(
                isPreparing = !hasList, isRefreshing = hasList, loadFailed = false, preparingStep = 0,
            )
            if (!hasList) startStepTicker()
            loadJob = launch {
                val target = storeName ?: if (refresh) currentState.selected?.storeName else null
                var result = repository.load(storeName = target, refresh = refresh)
                if (result is ShoppingListLoadResult.Failure && !hasList) {
                    // The first call often lands on a cold function that is also building the list
                    // with Gemini; one quiet retry saves the shopper from tapping "Try again".
                    delay(FIRST_LOAD_RETRY_MS)
                    ensureActive()
                    result = repository.load(storeName = target, refresh = false)
                }
                // A newer load (account change, notification tap) replaced this one: its result
                // must not overwrite the newer load's state with a stale failure.
                ensureActive()
                stepJob?.cancel()
                currentState = when (result) {
                    is ShoppingListLoadResult.Success ->
                        currentState.copy(
                            isPreparing = false, isRefreshing = false, loadFailed = false,
                            noHistory = result.noHistory && result.lists.isEmpty(),
                        )
                    // Only the account stream decides guest-ness; a SignInRequired here just ends loading.
                    ShoppingListLoadResult.SignInRequired ->
                        currentState.copy(isPreparing = false, isRefreshing = false)
                    is ShoppingListLoadResult.Failure -> {
                        if (hasList) sideEffect(SideEffect.ShowMessage(strings.getString(S.SHOPPING_REFRESH_FAILED)))
                        currentState.copy(isPreparing = false, isRefreshing = false, loadFailed = !hasList)
                    }
                }.withEdits()
            }
        }

        private fun startStepTicker() {
            stepJob?.cancel()
            stepJob = launch {
                while (true) {
                    delay(STEP_MS)
                    currentState = currentState.copy(preparingStep = (currentState.preparingStep + 1) % PREPARING_STEPS)
                }
            }
        }

        private fun shareText(state: State): String {
            val list = state.selected ?: return ""
            val items = (state.dueItems + state.alsoHereItems).map { it.item } + state.added
            return buildString {
                append(strings.getString(S.SHOPPING_SHARE_TITLE, list.storeName))
                items.filterNot { it in state.checked }.forEach { append("\n• ").append(it) }
            }
        }

        companion object {
            const val PREPARING_STEPS = 4
            private const val STEP_MS = 2_000L
            private const val FIRST_LOAD_RETRY_MS = 1_500L
        }
    }
}
