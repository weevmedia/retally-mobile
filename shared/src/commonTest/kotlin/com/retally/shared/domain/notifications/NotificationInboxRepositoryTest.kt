package com.retally.shared.domain.notifications

import com.russhwolf.settings.Settings
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
}
