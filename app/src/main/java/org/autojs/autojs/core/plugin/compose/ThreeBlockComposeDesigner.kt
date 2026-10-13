package org.autojs.autojs.core.plugin.compose

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.autojs.plugin.compose.api.ComposeUiIds
import java.io.InputStream
import java.security.MessageDigest

/**
 * Host side of the 3-Block Compose designer hand-over (plugin roadmap P8.4 / D39, Q10): the editor passes
 * the current script to the plugin's visual designer through a content Uri with a read / write grant and
 * reloads the file when the designer wrote it back. The entry exists only while the installed plugin is
 * 1.2.0 or newer, the version that shipped the designer; the plugin's INFO version is the installed
 * package version, so the package manager answers without binding the INFO service.
 */
object ThreeBlockComposeDesigner {

    const val MIN_VERSION_NAME = "1.2.0"

    const val SCRIPT_MIME_TYPE = "application/x-javascript"

    @JvmStatic
    fun installedVersionName(context: Context): String? = try {
        context.packageManager.getPackageInfo(ComposeUiIds.PLUGIN_PACKAGE_NAME, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    @JvmStatic
    fun isAvailable(context: Context): Boolean = installedVersionName(context)?.let { isAtLeast(it, MIN_VERSION_NAME) } == true

    /** Compares dotted numeric version names; a suffix after `-` or `+` is ignored, so "1.2.0-beta" counts as 1.2.0. */
    @JvmStatic
    fun isAtLeast(versionName: String, minimum: String): Boolean {
        val actual = components(versionName)
        val required = components(minimum)
        for (index in 0 until maxOf(actual.size, required.size)) {
            val a = actual.getOrElse(index) { 0 }
            val b = required.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return true
    }

    private fun components(name: String): List<Int> = name.trim()
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /** EDIT with the script mime type, limited to the plugin package so no other handler is offered. */
    @JvmStatic
    fun openIntent(uri: Uri, writable: Boolean): Intent = Intent(Intent.ACTION_EDIT)
        .setDataAndType(uri, SCRIPT_MIME_TYPE)
        .setPackage(ComposeUiIds.PLUGIN_PACKAGE_NAME)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .also { if (writable) it.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }

    /** SHA-256 of a document's bytes, used to tell whether the designer wrote the file back; null without a stream. */
    @JvmStatic
    fun fingerprint(stream: InputStream?): String? = stream?.use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
