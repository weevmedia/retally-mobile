package com.retally.shared.presentation.scan

import app.cash.turbine.test
import com.retally.shared.domain.category.CategorySource
import com.retally.shared.domain.category.ItemCategory
import com.retally.shared.domain.cloud.CloudSyncResult
import com.retally.shared.domain.cloud.CloudSyncService
import com.retally.shared.domain.cloud.ReceiptBackup
import com.retally.shared.domain.favorites.FavoriteStoresRepository
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.scan.ReceiptParser
import com.retally.shared.presentation.home.FakeReceiptRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScanReviewStoreTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeReceiptRepository
    private var counter = 0

    private val lines = listOf("Green Market", "Organic Bananas 2.19", "Sourdough Loaf 5.49", "Sourdough Loaf 5.49", "TOTAL 13.17")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeReceiptRepository()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun store() = ScanReviewContract.Store(
        repository = repository,
        backup = ReceiptBackup(cloudSync = NoCloud, repository = repository),
        favorites = FavoriteStoresRepository(settings = FakeSettings(), json = Json { ignoreUnknownKeys = true }),
        categories = FakeCategories(),
        parser = ReceiptParser(idFactory = { "item-${counter++}" }),
    )

    private class FakeCategories : CategorySource {
        override val categories: StateFlow<List<ItemCategory>> =
            MutableStateFlow(ItemCategory.bundled())
    }

    /** In-memory Settings so the store under test never touches the device. */
    private class FakeSettings : Settings {
        private val map = mutableMapOf<String, Any?>()
        override val keys: Set<String> get() = map.keys
        override val size: Int get() = map.size
        override fun clear() = map.clear()
        override fun remove(key: String) { map.remove(key) }
        override fun hasKey(key: String) = map.containsKey(key)
        override fun putInt(key: String, value: Int) { map[key] = value }
        override fun getInt(key: String, defaultValue: Int) = map[key] as? Int ?: defaultValue
        override fun putLong(key: String, value: Long) { map[key] = value }
        override fun getLong(key: String, defaultValue: Long) = map[key] as? Long ?: defaultValue
        override fun putString(key: String, value: String) { map[key] = value }
        override fun getString(key: String, defaultValue: String) = map[key] as? String ?: defaultValue
        override fun getStringOrNull(key: String) = map[key] as? String
        override fun putFloat(key: String, value: Float) { map[key] = value }
        override fun getFloat(key: String, defaultValue: Float) = map[key] as? Float ?: defaultValue
        override fun putDouble(key: String, value: Double) { map[key] = value }
        override fun getDouble(key: String, defaultValue: Double) = map[key] as? Double ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = map[key] as? Boolean ?: defaultValue
    }

    /** Backup is skipped when no backend is configured — keeps these tests offline. */
    private object NoCloud : CloudSyncService {
        override val isConfigured = false
        override suspend fun upload(receipts: List<Receipt>): CloudSyncResult =
            CloudSyncResult.Failure("offline", retryable = true)
    }

    @Test
    fun `a scan is parsed and saved immediately`() = runTest(dispatcher) {
        val store = store()
        store.observeSideEffect().test {
            store.dispatch(ScanReviewContract.Action.TextRecognized(lines))
            store.dispatch(ScanReviewContract.Action.TextRecognized(listOf("Other Shop", "Thing 1.00"))) // ignored
            dispatcher.scheduler.advanceUntilIdle()

            val saved = assertIs<ScanReviewContract.SideEffect.Saved>(awaitItem())
            val state = store.observeState().value
            val receipt = repository.current().single()
            assertEquals(saved.receiptId, receipt.id)
            assertEquals(receipt, state.receipt)
            assertEquals("Green Market", receipt.storeName)
            assertEquals(3, receipt.items.size)
            assertEquals(2, receipt.duplicates.size)
            assertEquals(5.49, state.overchargeAmount, absoluteTolerance = 0.001)
            assertFalse(state.isSaving)
            assertTrue(state.savedAt != null)
            cancelAndIgnoreRemainingEvents()
        }
        store.clear()
    }

    @Test
    fun `toggling a duplicate re-saves the receipt`() = runTest(dispatcher) {
        val store = store()
        store.dispatch(ScanReviewContract.Action.TextRecognized(lines))
        dispatcher.scheduler.advanceUntilIdle()
        val dup = store.observeState().value.duplicates.first()

        store.dispatch(ScanReviewContract.Action.ItemDuplicateToggled(dup.id))
        dispatcher.scheduler.advanceUntilIdle()

        val state = store.observeState().value
        assertEquals(1, state.duplicates.size)
        assertEquals(1, repository.current().single().duplicates.size)
        assertEquals(null, repository.current().single().syncedAt)
        store.clear()
    }

    @Test
    fun `an existing receipt is loaded by id`() = runTest(dispatcher) {
        val store = store()
        store.dispatch(ScanReviewContract.Action.TextRecognized(lines))
        dispatcher.scheduler.advanceUntilIdle()
        val saved = repository.current().single()
        store.clear()

        val viewer = store()
        viewer.dispatch(ScanReviewContract.Action.LoadReceipt(saved.id))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(saved, viewer.observeState().value.receipt)
        assertFalse(viewer.observeState().value.isLoading)
        viewer.clear()
    }

    @Test
    fun `nothing recognisable saves nothing and close pops`() = runTest(dispatcher) {
        val store = store()
        store.dispatch(ScanReviewContract.Action.TextRecognized(listOf("THANK YOU", "COME AGAIN")))
        dispatcher.scheduler.advanceUntilIdle()
        val state = store.observeState().value
        assertTrue(state.nothingFound)
        assertEquals(null, state.receipt)
        assertTrue(repository.current().isEmpty())

        store.observeSideEffect().test {
            store.dispatch(ScanReviewContract.Action.CloseClicked)
            assertIs<ScanReviewContract.SideEffect.Closed>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        store.clear()
    }

    @Test
    fun `keep both marks the line distinct and clears the overcharge`() = runTest(dispatcher) {
        val store = store()
        store.dispatch(ScanReviewContract.Action.TextRecognized(lines))
        dispatcher.scheduler.advanceUntilIdle()
        val dup = store.observeState().value.duplicates.first()

        store.dispatch(ScanReviewContract.Action.KeepBoth(dup.id))
        dispatcher.scheduler.advanceUntilIdle()

        val state = store.observeState().value
        assertTrue(state.duplicates.isEmpty())
        assertEquals(0.0, state.overchargeAmount, absoluteTolerance = 0.001)
        assertEquals(3, state.receipt!!.items.size) // nothing deleted
        assertTrue(repository.current().single().items.any { it.keptAsDistinct })
        store.clear()
    }

    @Test
    fun `remove one asks first then deletes exactly one line`() = runTest(dispatcher) {
        val store = store()
        store.dispatch(ScanReviewContract.Action.TextRecognized(lines))
        dispatcher.scheduler.advanceUntilIdle()
        val dup = store.observeState().value.duplicates.first()

        store.dispatch(ScanReviewContract.Action.RemoveOneRequested(dup.id))
        assertEquals(dup, store.observeState().value.pendingRemoval)
        assertEquals(3, repository.current().single().items.size)

        store.dispatch(ScanReviewContract.Action.RemoveOneConfirmed)
        dispatcher.scheduler.advanceUntilIdle()

        val state = store.observeState().value
        assertEquals(null, state.pendingRemoval)
        assertEquals(2, state.receipt!!.items.size)
        assertTrue(state.duplicates.isEmpty())
        assertEquals(2, repository.current().single().items.size)
        store.clear()
    }
}
