package com.retally.shared.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Marker for an immutable screen state rendered by the UI. */
interface State

/** Marker for a user or system intent dispatched into a [Store]. */
interface Action

/** Marker for a one-shot event the UI must react to (navigation, toasts). */
interface Effect

/**
 * Minimal unidirectional data flow container ("NanoRedux").
 *
 * - The UI observes [observeState] and renders it.
 * - The UI dispatches [Action]s via [dispatch]; the store reduces them into new state.
 * - Anything that is not state — navigation, a snackbar, opening a URL — is emitted once
 *   through [observeSideEffect] so it is never replayed on recomposition or rotation.
 *
 * Stores own a [CoroutineScope] on the main dispatcher so state writes are serialized on the
 * UI thread; background work is switched inside repositories, not here. Call [clear] when the
 * owning screen leaves for good.
 */
abstract class Store<S : State, A : Action, E : Effect> :
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main) {

    protected abstract fun initState(): S

    private val state by lazy(LazyThreadSafetyMode.NONE) { MutableStateFlow(initState()) }

    protected var currentState: S
        get() = state.value
        set(value) {
            state.value = value
        }

    private val effects = Channel<E>(
        capacity = Channel.UNLIMITED,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    private val mutex = Mutex()

    fun observeState(): StateFlow<S> = state.asStateFlow()

    fun observeSideEffect(): Flow<E> = effects.receiveAsFlow()

    fun sideEffect(effect: E) {
        launch { effects.send(effect) }
    }

    /**
     * Thread-safe state update for reducers that run from more than one coroutine at once
     * (e.g. a repository flow and a user action racing). Single-coroutine reducers can assign
     * [currentState] directly.
     */
    protected suspend fun updateStateSafely(block: (current: S) -> S) {
        mutex.withLock { state.value = block(currentState) }
    }

    abstract fun dispatch(action: A)

    /**
     * Callback-style observers for hosts that cannot collect Kotlin Flows directly (Swift
     * ViewModels). Emits on the main thread; the current state is delivered immediately.
     * Close the returned handle in `deinit`. Members (not extensions) so generics survive
     * the Objective-C export.
     */
    fun collectState(onEach: (S) -> Unit): Closeable = Closeable(launch { state.collect { onEach(it) } })

    /** One collector per store: the effect channel hands each effect to exactly one listener. */
    fun collectSideEffect(onEach: (E) -> Unit): Closeable = Closeable(launch { observeSideEffect().collect { onEach(it) } })

    /** Cancels every coroutine this store launched. The store must not be used afterwards. */
    fun clear() {
        cancel()
    }
}

/** Handle for a running collector. Idempotent. */
class Closeable(private val job: Job) {
    fun close() = job.cancel()
}
