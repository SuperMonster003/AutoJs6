package org.autojs.autojs.runtime.exception

import android.content.Context
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Mar 23, 2026.
 */
class OfficialPluginMissingException(context: Context, vararg pluginName: String) : WrappedIllegalArgumentException(
    context.getString(R.string.error_official_plugin_missing, pluginName.joinToString(", ") { "\"$it\"" })
)
