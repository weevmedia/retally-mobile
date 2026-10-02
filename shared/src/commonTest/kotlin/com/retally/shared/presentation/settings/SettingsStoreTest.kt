package com.retally.shared.presentation.settings

import com.retally.shared.core.Platform
import com.retally.shared.domain.cloud.CloudSyncResult
import com.retally.shared.domain.cloud.CloudSyncService
import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.domain.preferences.Preferences
import com.retally.shared.domain.receipt.SampleReceipts
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.presentation.home.FakeReceiptRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

    private val dispatcher = StandardTestDispatcher()
    private val preferences = FakePreferences()
    private val receipts = FakeReceiptRepository()
    private val platform = object : Platform {
        override val name = "test"
        override val appVersion = "1.0.0-test"
        override fun openUrl(url: String) = true
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun store(cloud: CloudSyncService) =
        SettingsContract.Store(preferences = preferences, receipts = receipts, cloudSync = cloud, platform = platform)


    @Test
    fun `successful sync marks receipts synced and records the time`() = runTest(dispatcher) {
        var counter = 0
        receipts.seed(SampleReceipts.make(idFactory = { "id-${counter++}" }))
        preferences.update { it.copy(cloudBackupEnabled = true) }
        val cloud = FakeCloud()
        val store = store(cloud)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, store.observeState().value.unsyncedCount)

        store.dispatch(SettingsContract.Action.SyncNowClicked)
        dispatcher.scheduler.advanceUntilIdle()

        val state = store.observeState().value
        assertEquals(SettingsContract.SyncStatus.SUCCESS, state.syncStatus)
        assertEquals(0, state.unsyncedCount)
        assertNotNull(preferences.current.lastSyncedAt)
        assertEquals(1, cloud.uploaded.size)
        store.clear()
    }

    @Test
    fun `failed sync leaves receipts unsynced and surfaces the message`() = runTest(dispatcher) {
        var counter = 0
        receipts.seed(SampleReceipts.make(idFactory = { "id-${counter++}" }))
        preferences.update { it.copy(cloudBackupEnabled = true) }
        val store = store(FakeCloud(result = CloudSyncResult.Failure("Server answered 503.", retryable = true)))
        dispatcher.scheduler.advanceUntilIdle()

        store.dispatch(SettingsContract.Action.SyncNowClicked)
        dispatcher.scheduler.advanceUntilIdle()

        val state = store.observeState().value
        assertEquals(SettingsContract.SyncStatus.FAILURE, state.syncStatus)
        assertEquals("Server answered 503.", state.syncMessage)
        assertEquals(1, state.unsyncedCount)
        store.clear()
    }

    @Test
    fun `sync is refused while backup is off`() = runTest(dispatcher) {
        val cloud = FakeCloud()
        val store = store(cloud)
        store.dispatch(SettingsContract.Action.SyncNowClicked)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(cloud.uploaded.isEmpty())
        assertFalse(store.observeState().value.syncStatus == SettingsContract.SyncStatus.SYNCING)
        store.clear()
    }
}

private class FakeCloud(
    private val result: CloudSyncResult? = null,
) : CloudSyncService {
    val uploaded = mutableListOf<Receipt>()
    override val isConfigured = true

    override suspend fun upload(receipts: List<Receipt>): CloudSyncResult {
        uploaded += receipts
        return result ?: CloudSyncResult.Success(receipts.map { it.id }, Instant.fromEpochMilliseconds(1_700_000_000_000))
    }
}

private class FakePreferences : AppPreferences {
    private val state = MutableStateFlow(Preferences())
    override fun observe(): Flow<Preferences> = state
    override val current: Preferences get() = state.value
    override fun update(block: (Preferences) -> Preferences) {
        state.value = block(state.value)
    }
    override val installationId = "test-install"
    override var hasLaunchedBefore = false
}
