/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.budget

import com.retally.shared.domain.notifications.NotificationInboxRepository
import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Puts budget notifications under the Home bell. They are scheduled ahead of time and the OS
 * shows them on its own (iOS runs no app code when they fire), so the planned list is kept
 * here and every reminder whose time has passed is moved into [NotificationInboxRepository]
 * — when the app opens, when the plan changes, while the app is open (Home waits for the next
 * one), and from the Android alarm receiver. The inbox ignores repeats by id.
 *
 * Works even with system notifications turned off: the bell still gets the entry.
 */
class BudgetReminderLog(
    private val settings: Settings,
    private val json: Json,
    private val inbox: NotificationInboxRepository,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /** Called before scheduling a new plan: files what already fired, then keeps [reminders]. */
    fun replacePlan(reminders: List<BudgetReminder>) {
        collectFired()
        save(reminders)
    }

    /** Moves every planned reminder whose time has passed into the bell. */
    fun collectFired() {
        val time = now()
        val (fired, pending) = load().partition { it.triggerAtEpochMillis <= time }
        if (fired.isEmpty()) return
        fired.forEach { inbox.recordBudget(it.id, it.title, it.body, it.triggerAtEpochMillis) }
        save(pending)
    }

    /** When the next planned reminder fires, or null when none is left. */
    fun nextTriggerAtMillis(): Long? = load().minOfOrNull { it.triggerAtEpochMillis }

    private fun load(): List<BudgetReminder> = settings.getStringOrNull(KEY)
        ?.let { runCatching { json.decodeFromString(SERIALIZER, it) }.getOrNull() }
        .orEmpty()

    private fun save(reminders: List<BudgetReminder>) =
        settings.putString(KEY, json.encodeToString(SERIALIZER, reminders))

    private companion object {
        const val KEY = "budget.reminders.plan.v1"
        val SERIALIZER = ListSerializer(BudgetReminder.serializer())
    }
}
