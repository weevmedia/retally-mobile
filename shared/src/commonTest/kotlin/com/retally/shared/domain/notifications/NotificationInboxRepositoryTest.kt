/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.notifications

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationInboxRepositoryTest {

    private val hour = 60L * 60 * 1000
    private var clock = 1_000_000_000_000L
    private val settings = FakeSettings()
    private fun repo() = NotificationInboxRepository(settings, Json, now = { clock })

    @Test
    fun recordsNewestFirstAndPersists() {
        val inbox = repo()
        inbox.recordNearbyStore("Costco", "You're near Costco", "Milk")
        clock += hour
        inbox.recordNearbyStore("Aldi", "You're near Aldi", "Eggs")
        assertEquals(listOf("Aldi", "Costco"), inbox.notifications.value.map { it.storeName })
        assertEquals(2, repo().notifications.value.size) // survives a relaunch
    }

    @Test
    fun expiresAfter48Hours() {
        val inbox = repo()
        inbox.recordNearbyStore("Costco", "t", "b")
        clock += 47 * hour
        inbox.prune()
        assertEquals(1, inbox.notifications.value.size)
        clock += 2 * hour
        inbox.prune()
        assertTrue(inbox.notifications.value.isEmpty())
        assertTrue(repo().notifications.value.isEmpty())
    }

    @Test
    fun dismissRemovesOnlyThatOne() {
        val inbox = repo()
        val a = inbox.record(AppNotificationKind.NEARBY_STORE, "t", "b", storeName = "Costco")
        clock += 1
        inbox.recordNearbyStore("Aldi", "t", "b")
        inbox.dismiss(a.id)
        assertEquals(listOf("Aldi"), repo().notifications.value.map { it.storeName })
        inbox.dismissAll()
        assertTrue(repo().notifications.value.isEmpty())
    }

    @Test
    fun onlyNotificationsAfterTheLastVisitAreNew() {
        val inbox = repo()
        inbox.recordNearbyStore("Costco", "t", "b")
        assertEquals(1, inbox.newCount())

        clock += hour
        inbox.markAllSeen() // user opened the inbox
        assertEquals(0, inbox.newCount())

        clock += hour
        inbox.recordNearbyStore("Aldi", "t", "b")
        assertEquals(1, inbox.newCount()) // only Aldi
        assertEquals(1, repo().newCount()) // last-seen survives a relaunch
    }

    private fun NotificationInboxRepository.newCount() =
        notifications.value.count { it.postedAtMillis > lastSeenAtMillis.value }
}
