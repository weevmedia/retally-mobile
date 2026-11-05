/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.budget

import com.retally.shared.domain.notifications.AppNotificationKind
import com.retally.shared.domain.notifications.FakeSettings
import com.retally.shared.domain.notifications.NotificationInboxRepository
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetReminderLogTest {

    private val hour = 60L * 60 * 1000
    private var clock = 1_000_000_000_000L
    private val settings = FakeSettings()
    private val inbox = NotificationInboxRepository(settings, Json, now = { clock })
    private fun log() = BudgetReminderLog(settings, Json, inbox, now = { clock })

    private fun reminder(id: String, inHours: Long) =
        BudgetReminder(id = id, triggerAtEpochMillis = clock + inHours * hour, title = "t-$id", body = "b-$id")

    @Test
    fun onlyFiredRemindersReachTheBell() {
        log().replacePlan(listOf(reminder("a", 1), reminder("b", 25)))
        assertTrue(inbox.notifications.value.isEmpty())

        clock += 2 * hour
        log().collectFired()
        val entries = inbox.notifications.value
        assertEquals(listOf("a"), entries.map { it.id })
        assertEquals(AppNotificationKind.BUDGET, entries.single().kind)
        assertEquals(clock - hour, entries.single().postedAtMillis) // posted when it fired
    }

    @Test
    fun collectingTwiceKeepsOneEntry() {
        log().replacePlan(listOf(reminder("a", 1)))
        clock += 2 * hour
        log().collectFired()
        log().collectFired()
        inbox.recordBudget("a", "t", "b", clock) // e.g. the Android alarm receiver too
        assertEquals(1, inbox.notifications.value.size)
    }

    @Test
    fun replanningFilesWhatAlreadyFiredFirst() {
        log().replacePlan(listOf(reminder("a", 1)))
        clock += 2 * hour
        log().replacePlan(listOf(reminder("c", 5)))
        assertEquals(listOf("a"), inbox.notifications.value.map { it.id })
        assertEquals(clock + 5 * hour, log().nextTriggerAtMillis())
    }

    @Test
    fun nothingPlannedMeansNoNextTrigger() {
        log().replacePlan(emptyList())
        assertNull(log().nextTriggerAtMillis())
    }

    @Test
    fun budgetEntriesExpireAfter48HoursLikeEveryOther() {
        log().replacePlan(listOf(reminder("a", 1)))
        clock += 2 * hour
        log().collectFired()
        clock += 48 * hour
        inbox.prune()
        assertTrue(inbox.notifications.value.isEmpty())
    }
}
