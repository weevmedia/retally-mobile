package com.retally.shared.core.strings

/**
 * Resolves a string key to the platform's localized text — Android `strings.xml` via
 * resources, iOS `Localizable.strings` via `NSBundle`. Same shape as Sidearm's
 * `StringResolver`; keys live in [StringAssetIdentifiers] so both platforms share one
 * catalogue and a missing translation is a build-time typo, not a runtime surprise.
 */
interface StringResolver {
    fun resolve(resource: StringResource): String
}

/** A key plus optional positional args for `%1$s` / `%1$d` placeholders. */
class StringResource(val id: String, val args: Array<out Any?>? = null)

@Suppress("NOTHING_TO_INLINE")
inline fun StringResolver.getString(id: String, vararg args: Any?): String =
    resolve(StringResource(id, if (args.isEmpty()) null else args))

/**
 * Replaces `%1$s`, `%2$d`, `%s`, `%d` placeholders with [args] in order. Shared by the iOS
 * resolver (Foundation has no varargs bridge from Kotlin) and by tests.
 */
fun formatPlaceholders(template: String, args: Array<out Any?>?): String {
    if (args == null || args.isEmpty()) return template
    var index = 0
    return PLACEHOLDER.replace(template) { match ->
        val position = match.groupValues[1].toIntOrNull()?.minus(1) ?: index++
        args.getOrNull(position)?.toString() ?: match.value
    }
}

private val PLACEHOLDER = Regex("""%(?:(\d+)\$)?[sd@]""")
