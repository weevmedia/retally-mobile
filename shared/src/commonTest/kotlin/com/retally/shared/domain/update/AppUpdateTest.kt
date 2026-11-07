/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppUpdateTest {

    private val config = AppVersionConfig(
        latest = "1.2.0",
        minSupported = "1.1.0",
        storeUrl = "https://play.google.com/store/apps/details?id=com.weevmedia.retally",
        message = "Faster scanning.",
    )

    @Test
    fun `numeric parts compare, not strings`() {
        assertTrue(compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue(compareVersions("1.9.0", "1.10.0") < 0)
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertTrue(compareVersions("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun `build suffixes are ignored`() {
        assertEquals(0, compareVersions("1.0.0-debug", "1.0.0"))
        assertEquals(0, compareVersions("1.0.0+42", "1.0.0"))
        assertEquals(0, compareVersions(" 1.0.0 ", "1.0.0"))
    }

    @Test
    fun `current build needs nothing`() {
        assertEquals(UpdateStatus.None, evaluateUpdate("1.2.0", config))
        assertEquals(UpdateStatus.None, evaluateUpdate("1.3.0", config))
    }

    @Test
    fun `behind latest but supported is a soft nudge`() {
        assertEquals(
            UpdateStatus.Available("1.2.0", "Faster scanning.", config.storeUrl),
            evaluateUpdate("1.1.0", config),
        )
    }

    @Test
    fun `below the minimum is a required update`() {
        assertEquals(
            UpdateStatus.Required("1.2.0", "Faster scanning.", config.storeUrl),
            evaluateUpdate("1.0.0", config),
        )
    }

    @Test
    fun `missing config or store url never blocks the app`() {
        assertEquals(UpdateStatus.None, evaluateUpdate("0.1.0", null))
        assertEquals(UpdateStatus.None, evaluateUpdate("0.1.0", config.copy(storeUrl = "")))
    }

    @Test
    fun `blank thresholds are skipped`() {
        assertEquals(UpdateStatus.None, evaluateUpdate("0.1.0", config.copy(latest = "", minSupported = "")))
        assertEquals(
            UpdateStatus.Required("1.1.0", "Faster scanning.", config.storeUrl),
            evaluateUpdate("0.1.0", config.copy(latest = "")),
        )
    }
}
