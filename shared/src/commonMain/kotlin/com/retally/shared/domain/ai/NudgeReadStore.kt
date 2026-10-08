package com.retally.shared.domain.ai

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers which nudges the user has already seen, so the Home card can pulse an
 * attention dot on fresh ones. Ids are stable per nudge ([Nudge.id]): a nudge that
 * keeps firing across recomputes is recorded once and never re-pulses.
 *
 * Backed by multiplatform Settings (survives restarts); the in-memory flow keeps the
 * UI reactive within a session.
 */
class NudgeReadStore(private val settings: Settings) {

    private val _readIds = MutableStateFlow(load())
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    fun markRead(ids: Collection<String>) {
        val next = _readIds.value + ids
        if (next == _readIds.value) return
        _readIds.value = next
        settings.putString(KEY_READ_IDS, next.joinToString(SEPARATOR))
    }

    private fun load(): Set<String> =
        settings.getStringOrNull(KEY_READ_IDS)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    private companion object {
        const val KEY_READ_IDS = "nudge.read_ids.v1"
        // Item names can contain commas, so ids are newline-separated instead.
        const val SEPARATOR = "\n"
    }
}
