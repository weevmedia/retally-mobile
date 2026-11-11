/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.notifications

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** What raised the notification; drives the row icon and what a tap opens. */
@Serializable
enum class AppNotificationKind {
    /** "You're near {store}" geofence alert. A tap opens the Shopping List on that store. */
    NEARBY_STORE,

    /** Daily "budget is orange / red" reminder or the month-end wrap-up. A tap opens Budget. */
    BUDGET,
}

/** A notification the app raised on this device, kept for the in-app bell. */
@Serializable
data class AppNotification(
    val id: String,
    val kind: AppNotificationKind,
    val title: String,
    val body: String,
    /** Epoch millis when the system notification was posted. */
    val postedAtMillis: Long,
    val storeName: String? = null,
)

/**
 * The Home bell's inbox: every notification the app raised in the last [RETENTION_MS]
 * (48 hours), newest first, until the user dismisses it.
 *
 * Device-local on purpose (Settings-backed, never synced) — it mirrors what this phone
 * showed. Platform hosts call [recordNearbyStore] right after posting the system
 * notification, which also works from a background geofence wake-up.
 */
class NotificationInboxRepository(
    private val settings: Settings,
    private val json: Json,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val _notifications = MutableStateFlow(active(loadStored()))

    /** Active notifications, newest first. Call [prune] to drop ones that just expired. */
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _lastSeenAtMillis = MutableStateFlow(settings.getLong(KEY_LAST_SEEN, 0L))

    /**
     * When the user last opened the inbox. Anything posted after this is "new" and makes the
     * Home bell shake; opening the inbox ([markAllSeen]) stops it.
     */
    val lastSeenAtMillis: StateFlow<Long> = _lastSeenAtMillis.asStateFlow()

    /** The user opened the inbox: everything in it now counts as seen. */
    fun markAllSeen() {
        val latest = maxOf(now(), _notifications.value.maxOfOrNull { it.postedAtMillis } ?: 0L)
        _lastSeenAtMillis.value = latest
        settings.putLong(KEY_LAST_SEEN, latest)
    }

    /** Records a "you're near {store}" notification. Swift-friendly (no default args). */
    fun recordNearbyStore(storeName: String, title: String, body: String) {
        record(AppNotificationKind.NEARBY_STORE, title, body, storeName)
    }

    /**
     * Records a budget notification that fired at [postedAtMillis]. [id] is the reminder's own
     * id, so recording the same reminder twice (alarm receiver and app open) keeps one entry.
     * Already-expired ones are ignored.
     */
    fun recordBudget(id: String, title: String, body: String, postedAtMillis: Long) {
        if (_notifications.value.any { it.id == id }) return
        val entry = AppNotification(
            id = id,
            kind = AppNotificationKind.BUDGET,
            title = title,
            body = body,
            postedAtMillis = postedAtMillis,
        )
        mutate { listOf(entry) + it }
    }

    fun record(
        kind: AppNotificationKind,
        title: String,
        body: String,
        storeName: String? = null,
    ): AppNotification {
        val postedAt = now()
        val entry = AppNotification(
            id = "${kind.name.lowercase()}-$postedAt-${(0..9999).random()}",
            kind = kind,
            title = title,
            body = body,
            postedAtMillis = postedAt,
            storeName = storeName,
        )
        mutate { listOf(entry) + it }
        return entry
    }

    /** User swiped / tapped X: gone for good. */
    fun dismiss(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun dismissAll() = mutate { emptyList() }

    /** Drops anything older than 48 hours. Cheap; call whenever the bell or inbox shows. */
    fun prune() = mutate { it }

    private fun mutate(block: (List<AppNotification>) -> List<AppNotification>) {
        val next = _notifications.updateAndGet { active(block(it)) }
        settings.putString(KEY, json.encodeToString(LIST_SERIALIZER, next))
    }

    private fun active(list: List<AppNotification>): List<AppNotification> {
        val cutoff = now() - RETENTION_MS
        return list
            .filter { it.postedAtMillis >= cutoff }
            .sortedByDescending { it.postedAtMillis }
            .take(MAX_ENTRIES)
    }

    private fun loadStored(): List<AppNotification> {
        val raw = settings.getStringOrNull(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString(LIST_SERIALIZER, raw) }.getOrDefault(emptyList())
    }

    companion object {
        /** How long a notification stays in the bell unless dismissed: 48 hours. */
        const val RETENTION_MS: Long = 48L * 60 * 60 * 1000
        /** Safety cap; nearby alerts are already limited to one per store per 2 hours. */
        const val MAX_ENTRIES = 50
        private const val KEY = "notifications.inbox.v1"
        private const val KEY_LAST_SEEN = "notifications.inbox.lastSeenAt"
        private val LIST_SERIALIZER = ListSerializer(AppNotification.serializer())
    }
}
