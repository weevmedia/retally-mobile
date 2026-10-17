package com.retally.shared.domain.cloud

import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.receipt.ReceiptRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Receipts deleted on this device. A restore never brings them back, even if their cloud
 * soft-delete hasn't reached the server yet (offline delete, or a receipt deleted before
 * soft-deletes existed).
 */
class DeletedReceipts(private val settings: Settings) {
    fun ids(): Set<String> =
        settings.getStringOrNull(KEY)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun add(ids: Collection<String>) {
        if (ids.isEmpty()) return
        // Keep the newest few thousand; older ones have long been purged from the cloud.
        val merged = (ids() + ids).toList().takeLast(MAX)
        settings.putString(KEY, merged.joinToString(","))
    }

    private companion object {
        const val KEY = "receipts.deleted.v1"
        const val MAX = 5_000
    }
}

/**
 * Pulls a signed-in shopper's cloud receipts onto the device: after signing in (new phone,
 * reinstall) and as a catch-up on launch (receipts added on another device). Only receipts
 * missing locally are added; local copies are never overwritten. Images stay in Cloud Storage
 * and download the first time a receipt shows them.
 *
 * App-lifetime singleton (created at Koin start) so it runs regardless of which screen is up.
 */
class ReceiptRestore(
    private val cloudSync: CloudSyncService,
    private val repository: ReceiptRepository,
    private val auth: AuthRepository,
    private val deleted: DeletedReceipts,
    private val settings: Settings,
    private val clock: Clock = Clock.System,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _isRestoring = MutableStateFlow(false)
    /** True while a restore is downloading — Home shows "Restoring your receipts…". */
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _restoredCount = MutableStateFlow(0)
    /** Receipts added by the last restore (0 when nothing new). */
    val restoredCount: StateFlow<Int> = _restoredCount.asStateFlow()

    init {
        auth.observeAccount()
            .map { account -> account.uid?.takeUnless { account.isGuest } }
            .distinctUntilChanged()
            .onEach { uid -> if (uid != null) restore(uid, force = false) }
            .launchIn(scope)
    }

    /** Runs a restore now (e.g. a future Settings → "Restore receipts"), ignoring the throttle. */
    fun restoreNow() {
        scope.launch {
            val account = auth.observeAccount().first()
            account.uid?.takeUnless { account.isGuest }?.let { restore(it, force = true) }
        }
    }

    private fun lastUid(): String? = settings.getStringOrNull(KEY_LAST_UID)

    private fun restore(uid: String, force: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            val local = repository.observeReceipts().first()
            val sameUser = lastUid() == uid
            val lastRun = settings.getLongOrNull(KEY_LAST_RUN) ?: 0L
            val recent = clock.now().toEpochMilliseconds() - lastRun < THROTTLE.inWholeMilliseconds
            // A different account, or an empty device, always restores; otherwise at most every few hours.
            if (!force && sameUser && recent && local.isNotEmpty()) return@launch

            _isRestoring.value = true
            try {
                val skip = local.map { it.id }.toSet() + deleted.ids()
                when (val result = cloudSync.download(skip)) {
                    is CloudRestoreResult.Success -> {
                        result.receipts.forEach { repository.upsert(it) }
                        _restoredCount.value = result.receipts.size
                        settings.putString(KEY_LAST_UID, uid)
                        settings.putLong(KEY_LAST_RUN, clock.now().toEpochMilliseconds())
                        println("[ReTallyRestore] restored ${result.receipts.size} receipt(s)")
                    }
                    is CloudRestoreResult.Failure -> println("[ReTallyRestore] failed: ${result.message}")
                }
            } finally {
                _isRestoring.value = false
            }
        }
    }

    private companion object {
        const val KEY_LAST_UID = "restore.lastUid"
        const val KEY_LAST_RUN = "restore.lastRunAt"
        val THROTTLE = 6.hours
    }
}
