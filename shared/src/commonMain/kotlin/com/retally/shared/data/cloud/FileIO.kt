package com.retally.shared.data.cloud

/** Reads file bytes from a local path. Platform-specific. */
expect fun readFileBytes(path: String): ByteArray?

/** Writes bytes to a local path. Platform-specific. */
expect fun writeFileBytes(path: String, bytes: ByteArray): Boolean

/**
 * Resolves a stored image reference (filename or legacy absolute path) to the current
 * full filesystem path, or null if the file doesn't exist. Platform-specific because
 * the receipts directory location differs per platform.
 */
expect fun resolveImagePath(stored: String): String?
