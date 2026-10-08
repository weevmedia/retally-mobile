package com.retally.shared.core.strings

import platform.Foundation.NSBundle

/**
 * Looks keys up in `Localizable.strings` via the main bundle (like Sidearm's
 * `IosStringResolver`). Placeholders use the shared `%1$s` syntax and are filled in Kotlin.
 */
object IosStringResolver : StringResolver {
    override fun resolve(resource: StringResource): String {
        val raw = NSBundle.mainBundle.localizedStringForKey(resource.id, MISSING, null)
        if (raw == MISSING) return EnglishStringResolver.resolve(resource)
        return formatPlaceholders(raw, resource.args)
    }

    private const val MISSING = "\u0000missing"
}
