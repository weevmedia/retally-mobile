package com.retally.shared.domain.ai

/**
 * One proactive nudge for the Home card. Sealed so the UI renders each type
 * exhaustively; every subtype carries exactly the data its copy needs and nothing
 * more. Higher [priority] wins when several nudges fire at once — Home shows one.
 *
 * Everything here is computed on-device from receipt history; no network, no account.
 */
sealed interface Nudge {
    val priority: Int

    /**
     * Stable identity for read-tracking: the same underlying nudge keeps the same id
     * every time the engine recomputes it, so it only ever pulses as "new" once.
     */
    val id: String

    /** An item's days-since-last-buy reached its median buy interval. */
    data class RunningLow(
        val itemName: String,
        val intervalDays: Int,
        val overdueDays: Int,
        override val priority: Int,
    ) : Nudge {
        override val id: String get() = "running-low:${itemName.lowercase()}"
    }

    /** The latest price paid is well above the shopper's usual price for the item. */
    data class PriceSpike(
        val itemName: String,
        val lastPrice: Double,
        val usualPrice: Double,
        override val priority: Int,
    ) : Nudge {
        override val id: String get() = "price-spike:${itemName.lowercase()}"
    }

    /** Longer than usual since the last grocery trip. */
    data class GroceryRunOverdue(
        val daysSinceLastTrip: Int,
        val usualIntervalDays: Int,
        override val priority: Int,
    ) : Nudge {
        override val id: String get() = "grocery-run-overdue"
    }

    /** The same item on two receipts inside a week — usually a forgotten pantry check. */
    data class DoubleBought(
        val itemName: String,
        val receiptCount: Int,
        override val priority: Int,
    ) : Nudge {
        override val id: String get() = "double-bought:${itemName.lowercase()}"
    }

    /** This month's spend is notably off the shopper's usual monthly pace. */
    data class SavingsRecap(
        val monthToDate: Double,
        val usualMonthly: Double,
        override val priority: Int,
    ) : Nudge {
        override val id: String get() = "savings-recap"
    }
}
