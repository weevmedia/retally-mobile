package com.retally.shared.presentation.home

import app.cash.turbine.test
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.receipt.SampleReceipts
import com.retally.shared.domain.receipt.model.Receipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeStoreTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeReceiptRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeReceiptRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `receipts from the repository land in state with totals`() = runTest(dispatcher) {
        val store = HomeContract.Store(repository)
        var counter = 0
        repository.seed(SampleReceipts.make(idFactory = { "id-${counter++}" }))
        dispatcher.scheduler.advanceUntilIdle()

        val state = store.observeState().value
        assertEquals(1, state.receipts.size)
        assertEquals(1, state.duplicateCount)
        assertEquals(5.49, state.totalOvercharge, absoluteTolerance = 0.001)
        assertTrue(!state.isLoading)
        store.clear()
    }

    @Test
    fun `scan CTA asks the host to launch the scanner`() = runTest(dispatcher) {
        val store = HomeContract.Store(repository)
        store.observeSideEffect().test {
            store.dispatch(HomeContract.Action.ScanClicked)
            assertIs<HomeContract.SideEffect.LaunchScanner>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        store.clear()
    }

    @Test
    fun `recognized text routes to review, blank text shows a message`() = runTest(dispatcher) {
        val store = HomeContract.Store(repository)
        store.observeSideEffect().test {
            store.dispatch(HomeContract.Action.TextRecognized(listOf(" GREEN MARKET ", "", "MILK 3.99")))
            val open = assertIs<HomeContract.SideEffect.OpenScanReview>(awaitItem())
            assertEquals(listOf("GREEN MARKET", "MILK 3.99"), open.lines)

            store.dispatch(HomeContract.Action.TextRecognized(listOf("", "   ")))
            assertIs<HomeContract.SideEffect.ShowMessage>(awaitItem())

            store.dispatch(HomeContract.Action.ScanFailed("Camera unavailable"))
            assertEquals("Camera unavailable", assertIs<HomeContract.SideEffect.ShowMessage>(awaitItem()).message)
            cancelAndIgnoreRemainingEvents()
        }
        store.clear()
    }

    @Test
    fun `delete requires confirmation and then removes the receipt`() = runTest(dispatcher) {
        val store = HomeContract.Store(repository)
        var counter = 0
        val receipt = SampleReceipts.make(idFactory = { "id-${counter++}" })
        repository.seed(receipt)
        dispatcher.scheduler.advanceUntilIdle()

        store.dispatch(HomeContract.Action.DeleteRequested(receipt.id))
        assertEquals(receipt, store.observeState().value.pendingDelete)

        store.dispatch(HomeContract.Action.DeleteCancelled)
        assertNull(store.observeState().value.pendingDelete)
        assertEquals(1, repository.current().size)

        store.dispatch(HomeContract.Action.DeleteRequested(receipt.id))
        store.dispatch(HomeContract.Action.DeleteConfirmed)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(repository.current().isEmpty())
        assertTrue(store.observeState().value.receipts.isEmpty())
        store.clear()
    }
}

/** In-memory repository for store tests. */
class FakeReceiptRepository : ReceiptRepository {
    private val state = MutableStateFlow<List<Receipt>>(emptyList())

    fun seed(vararg receipts: Receipt) {
        state.value = receipts.toList()
    }

    fun current(): List<Receipt> = state.value

    override fun observeReceipts(): Flow<List<Receipt>> = state
    override suspend fun getReceipt(id: String): Receipt? = state.value.firstOrNull { it.id == id }
    override suspend fun upsert(receipt: Receipt) {
        state.value = state.value.filterNot { it.id == receipt.id } + receipt
    }
    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
    override suspend fun markSynced(ids: Collection<String>, syncedAt: Instant) {
        state.value = state.value.map { if (it.id in ids) it.copy(syncedAt = syncedAt) else it }
    }
    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}
