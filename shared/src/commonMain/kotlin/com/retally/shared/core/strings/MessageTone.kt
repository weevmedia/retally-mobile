package com.retally.shared.core.strings

/**
 * Tells error messages apart from confirmations, so the bottom bar can show a red cross or a
 * green check without every screen having to say which it is.
 *
 * A message is an error when it was built from an error string — one whose key says so
 * (`*_error*`, `*_failed*`, `*_invalid*`, `*_no_email*`) or whose English text reads like one
 * ("Couldn't…", "Could not…", "Failed…") — or when its own text reads like one.
 */
object MessageTone {
    private val ERROR_KEY = Regex("(^|_)(error|failed|invalid|no_email|rate_limited|expired|mismatch)(_|$)")
    private val ERROR_TEXT = Regex("^(couldn['’]t|could not|can['’]t|cannot|failed|unable|something went wrong|enter a valid|too many)", RegexOption.IGNORE_CASE)

    /** Error keys from the English source of truth (stable across languages). */
    private val errorKeys: List<String> by lazy {
        EnglishStrings.filter { (key, text) -> ERROR_KEY.containsMatchIn(key) || ERROR_TEXT.containsMatchIn(text) }.keys.toList()
    }

    /** Per resolver: each error string's fixed opening (the text before its first placeholder). */
    private val prefixCache = mutableMapOf<StringResolver, List<String>>()

    fun isError(message: String, resolver: StringResolver): Boolean {
        val text = message.trim()
        if (text.isEmpty()) return false
        if (ERROR_TEXT.containsMatchIn(text)) return true
        val prefixes = prefixCache.getOrPut(resolver) {
            errorKeys.mapNotNull { key ->
                val template = runCatching { resolver.resolve(StringResource(key)) }.getOrNull() ?: return@mapNotNull null
                template.substringBefore('%').trim().takeIf { it.length >= 6 }
            }
        }
        return prefixes.any { text.startsWith(it) }
    }
}
