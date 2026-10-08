package com.retally.shared.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountTest {
    @Test
    fun guests_have_no_first_name() {
        assertNull(Account.Guest.firstName)
        assertNull(Account(uid = "u1", isGuest = true, displayName = null).firstName)
        assertNull(Account(uid = "u1", isGuest = false, displayName = "   ").firstName)
    }

    @Test
    fun signed_in_users_are_greeted_by_first_name() {
        assertEquals("Mukesh", Account(uid = "u1", isGuest = false, displayName = "Mukesh Jha").firstName)
    }
}
