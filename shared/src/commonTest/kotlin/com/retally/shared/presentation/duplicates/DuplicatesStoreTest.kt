package com.retally.shared.presentation.duplicates

import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import com.retally.shared.presentation.home.FakeReceiptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicatesStoreTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeReceiptRepository

    private val sep12 = Instant.parse("2026-09-12T10:00:00Z")
    private val sep15 = Instant.parse("2026-09-15T10:00:00Z")

    private val earlier = Receipt(
        id = "r1", storeName = "Costco", purchaseDate = sep12, createdAt = sep12,
        items = listOf(ReceiptItem(id = "a", name = "Whole Milk", price = 2.59)),
    )
    private val later = Receipt(
        id = "r2", storeName = "Costco", purchaseDate = sep15, createdAt = sep15,
        items = listOf(ReceiptItem(id = "b", name = "Whole Milk", price = 2.59), ReceiptItem(id = "c", name = "Bananas", price = 1.19)),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeReceiptRepository().also { it.seed(earlier, later) }
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lists the cross receipt repeat`() = runTest(dispatcher) {
        val store = DuplicatesContract.Store(repository)
        dispatcher.scheduler.advanceUntilIdle()
        val state = store.observeState().value
        assertEquals(1, state.crossReceipt.size)
        assertEquals("Whole Milk", state.crossReceipt.single().name)
        assertEquals(1, state.total)
        store.clear()
    }

    @Test
    fun `keep both marks every occurrence and clears the list`() = runTest(dispatcher) {
        val store = DuplicatesContract.Store(repository)
        dispatcher.scheduler.advanceUntilIdle()
        val key = store.observeState().value.crossReceipt.single().key

        store.dispatch(DuplicatesContract.Action.KeepBoth(key))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(store.observeState().value.crossReceipt.isEmpty())
        val items = repository.current().flatMap { it.items }
        assertTrue(items.filter { it.name == "Whole Milk" }.all { it.keptAcrossReceipts })
        assertEquals(3, items.size) // nothing deleted
        store.clear()
    }

    @Test
    fun `remove one needs confirmation and deletes only the latest occurrence`() = runTest(dispatcher) {
        val store = DuplicatesContract.Store(repository)
        dispatcher.scheduler.advanceUntilIdle()
        val key = store.observeState().value.crossReceipt.single().key

        store.dispatch(DuplicatesContract.Action.RemoveOneRequested(key))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(key, store.observeState().value.pendingRemoval?.key)
        assertEquals(3, repository.current().flatMap { it.items }.size) // still nothing deleted

        store.dispatch(DuplicatesContract.Action.RemoveOneCancelled)
        assertEquals(null, store.observeState().value.pendingRemoval)

        store.dispatch(DuplicatesContract.Action.RemoveOneRequested(key))
        store.dispatch(DuplicatesContract.Action.RemoveOneConfirmed)
        dispatcher.scheduler.advanceUntilIdle()

        val r2 = repository.current().single { it.id == "r2" }
        assertEquals(listOf("Bananas"), r2.items.map { it.name })          // Sep 15 copy removed
        assertEquals(1, repository.current().single { it.id == "r1" }.items.size) // Sep 12 copy kept
        assertTrue(store.observeState().value.crossReceipt.isEmpty())
        store.clear()
    }
}
