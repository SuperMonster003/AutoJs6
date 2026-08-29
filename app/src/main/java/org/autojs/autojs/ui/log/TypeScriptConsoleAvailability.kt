package org.autojs.autojs.ui.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import org.autojs.autojs.core.plugin.typescript.TypeScriptCompilerPluginClient

internal object TypeScriptConsoleAvailability {

    suspend fun isAvailable(context: Context): Boolean = try {
        TypeScriptCompilerPluginClient(context.applicationContext).isAvailable()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(TAG, "Unable to determine TypeScript console availability", error)
        false
    }

    private const val TAG = "TypeScriptConsole"

}
