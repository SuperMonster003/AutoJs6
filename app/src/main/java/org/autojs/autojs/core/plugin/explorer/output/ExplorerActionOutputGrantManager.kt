package org.autojs.autojs.core.plugin.explorer.output

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

internal object ExplorerActionOutputGrantManager {

    fun grant(
        context: Context,
        packageName: String,
        outputUri: Uri,
    ) {
        check(ExplorerActionOutputGrantPolicy.isSafe(ExplorerActionOutputGrantPolicy.exactGrantPlan())) {
            "Unsafe Explorer Action output URI grant plan"
        }
        context.grantUriPermission(packageName, outputUri, OUTPUT_FLAGS)
    }

    fun revokeOutput(context: Context, packageName: String, outputUri: Uri) {
        revokeOne(context, packageName, outputUri, OUTPUT_FLAGS)
    }

    private fun revokeOne(
        context: Context,
        packageName: String,
        uri: Uri,
        flags: Int,
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.revokeUriPermission(packageName, uri, flags)
            } else {
                @Suppress("DEPRECATION")
                context.revokeUriPermission(uri, flags)
            }
        }
    }

    const val OUTPUT_FLAGS = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
}
