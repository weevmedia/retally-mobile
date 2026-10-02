package com.retally.shared.core.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** The app's [StringResolver], provided by `ReTallyTheme` from Koin. */
val LocalStringResolver = staticCompositionLocalOf<StringResolver> {
    error("No StringResolver provided — wrap the screen in ReTallyTheme")
}

/** `str(StringAssetIdentifiers.HOME_EMPTY_TITLE)` inside a composable. */
@Composable
@ReadOnlyComposable
fun str(id: String, vararg args: Any?): String = LocalStringResolver.current.getString(id, *args)

/** English fallback used when no platform resolver is installed (previews, tests). */
object EnglishStringResolver : StringResolver {
    override fun resolve(resource: StringResource): String =
        formatPlaceholders(EnglishStrings[resource.id] ?: resource.id, resource.args)
}
