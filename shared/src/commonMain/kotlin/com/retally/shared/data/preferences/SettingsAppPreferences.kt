package com.retally.shared.data.preferences

import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.domain.preferences.Preferences
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.random.Random

internal class SettingsAppPreferences(
    private val settings: Settings,
    private val json: Json,
) : AppPreferences {

    private val state = MutableStateFlow(load())

    override fun observe(): Flow<Preferences> = state.asStateFlow()

    override val current: Preferences get() = state.value

    override fun update(block: (Preferences) -> Preferences) {
        val next = block(state.value)
        settings.putString(KEY_PREFS, json.encodeToString(Preferences.serializer(), next))
        state.value = next
    }

    override val installationId: String
        get() = settings.getStringOrNull(KEY_INSTALL_ID) ?: generateId().also {
            settings.putString(KEY_INSTALL_ID, it)
        }

    override var hasLaunchedBefore: Boolean
        get() = settings.getBoolean(KEY_LAUNCHED, false)
        set(value) = settings.putBoolean(KEY_LAUNCHED, value)

    private fun load(): Preferences {
        val raw = settings.getStringOrNull(KEY_PREFS) ?: return Preferences()
        return runCatching { json.decodeFromString(Preferences.serializer(), raw) }.getOrDefault(Preferences())
    }

    private fun generateId(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(ID_LENGTH) { repeat(ID_LENGTH) { append(alphabet[Random.nextInt(alphabet.length)]) } }
    }

    private companion object {
        const val KEY_PREFS = "preferences.v1"
        const val KEY_INSTALL_ID = "installation.id"
        const val KEY_LAUNCHED = "launched.before"
        const val ID_LENGTH = 24
    }
}
