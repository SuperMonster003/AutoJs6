package org.autojs.autojs.ui.main.scripts

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Exported, data-only entry point for files opened by another app.
 *
 * Installation actions, local paths, status callbacks, and arbitrary extras are deliberately not
 * forwarded to the non-exported coordinator Activity.
 */
class PackageInstallerEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }

        val incomingIntent = intent
        val action = incomingIntent.action
        val uri = incomingIntent.data
        @Suppress("DEPRECATION")
        if (
            (action != Intent.ACTION_VIEW && action != Intent.ACTION_INSTALL_PACKAGE) ||
            uri == null ||
            !PackageInstallerActivity.isSupportedExternalPackageReference(
                uri = uri,
                displayName = null,
                mimeTypes = listOf(incomingIntent.type),
            )
        ) {
            finish()
            return
        }

        PackageInstallerActivity.openExternalPackage(
            context = this,
            packageUri = uri,
            packageMimeType = PackageInstallerActivity.supportedMimeType(incomingIntent.type),
        )
        finish()
    }
}
