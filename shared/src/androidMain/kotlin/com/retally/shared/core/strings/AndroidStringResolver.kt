package com.retally.shared.core.strings

import android.content.Context

/** Looks keys up in the app's `strings.xml` by name (like Sidearm's `AndroidStringResolver`). */
class AndroidStringResolver(private val context: Context) : StringResolver {
    override fun resolve(resource: StringResource): String {
        val resId = context.resources.getIdentifier(resource.id, "string", context.packageName)
        if (resId == 0) return EnglishStringResolver.resolve(resource)
        val args = resource.args
        return if (args == null || args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
    }
}
