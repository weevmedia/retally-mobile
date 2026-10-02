package com.retally.shared.core

import kotlin.random.Random

/** Compact, URL-safe random id for locally created entities (receipts, line items). */
fun newId(length: Int = 16): String = buildString(length) {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    repeat(length) { append(alphabet[Random.nextInt(alphabet.length)]) }
}
