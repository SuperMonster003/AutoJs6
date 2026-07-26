package org.autojs.autojs.core.ui.inflater

import android.content.Context
import android.content.ContextWrapper
import java.util.IdentityHashMap
import java.util.LinkedHashMap

/**
 * Carries theme attributes originating from an uncompiled script style alongside a normal Android
 * [Context]. The platform theme remains responsible for compiled resources; the resolver uses this
 * overlay for project-owned `?attr` references and setter-applied defaults.
 */
internal class ScriptThemeContext(
    baseContext: Context,
    val scriptThemeAttributes: LinkedHashMap<String, String>,
) : ContextWrapper(baseContext)

internal fun Context.findScriptThemeAttributes(): Map<String, String> {
    var current: Context? = this
    val visited = IdentityHashMap<Context, Boolean>()
    while (current != null && visited.put(current, true) == null) {
        if (current is ScriptThemeContext) {
            return current.scriptThemeAttributes
        }
        current = (current as? ContextWrapper)?.baseContext
    }
    return emptyMap()
}
