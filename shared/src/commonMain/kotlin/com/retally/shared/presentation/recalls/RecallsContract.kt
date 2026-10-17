package com.retally.shared.presentation.recalls

import com.retally.shared.core.Effect
import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.recalls.Recall
import com.retally.shared.domain.recalls.RecallsLoadResult
import com.retally.shared.domain.recalls.RecallsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Food recall alerts, opened from the Home bell. Shows FDA + USDA recalls from the last
 * couple of months; for signed-in shoppers, recalls that mention something they bought
 * recently are pulled to the top under "May affect you".
 */
interface RecallsContract {

    data class State(
        val recalls: List<Recall> = emptyList(),
        /** Full-screen spinner — only when nothing is cached yet. */
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val loadFailed: Boolean = false,
        val isGuest: Boolean = false,
    ) : com.retally.shared.core.State {
        val matched: List<Recall> get() = recalls.filter { it.match != null }
        val others: List<Recall> get() = recalls.filter { it.match == null }
    }

    sealed class Action : com.retally.shared.core.Action {
        data object Appeared : Action()
        data object Refresh : Action()
        data object CloseClicked : Action()
        data object SignInClicked : Action()
    }

    sealed class SideEffect : Effect {
        data object Closed : SideEffect()
        data object OpenSignIn : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
    }

    class Store(
        private val repository: RecallsRepository,
        authRepository: AuthRepository,
        private val strings: StringResolver = EnglishStringResolver,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        private var loadJob: Job? = null

        override fun initState() = State(recalls = repository.recalls.value)

        init {
            authRepository.observeAccount()
                .map { it.isGuest }
                .onEach { guest -> currentState = currentState.copy(isGuest = guest) }
                .launchIn(this)
            repository.recalls
                .onEach { currentState = currentState.copy(recalls = it) }
                .launchIn(this)
        }

        override fun dispatch(action: Action) {
            when (action) {
                Action.Appeared -> if (loadJob?.isActive != true) load(refresh = false)
                Action.Refresh -> load(refresh = true)
                Action.CloseClicked -> sideEffect(SideEffect.Closed)
                Action.SignInClicked -> sideEffect(SideEffect.OpenSignIn)
            }
        }

        private fun load(refresh: Boolean) {
            loadJob?.cancel()
            val hasData = currentState.recalls.isNotEmpty()
            currentState = currentState.copy(isLoading = !hasData, isRefreshing = hasData, loadFailed = false)
            loadJob = launch {
                currentState = when (val result = repository.load(refresh)) {
                    is RecallsLoadResult.Success -> currentState.copy(
                        isLoading = false, isRefreshing = false, isGuest = !result.signedIn,
                    )
                    is RecallsLoadResult.Failure -> {
                        if (hasData) sideEffect(SideEffect.ShowMessage(strings.getString(S.ALERTS_REFRESH_FAILED)))
                        currentState.copy(isLoading = false, isRefreshing = false, loadFailed = !hasData)
                    }
                }
            }
        }
    }
}
