package org.autojs.autojs.network

import android.content.Context
import org.autojs.autojs.core.pref.keys.CredentialKeys

/**
 * The GitHub token in the named store `github_auth`, declared as [CredentialKeys.GITHUB_TOKEN] (docs/settings/ROADMAP.md
 * P3.4, sensitive: the value never goes to a log, R6). The [Context] parameters are kept for the callers; the store is
 * opened by the settings layer with the application context, as before.
 */
object GitHubTokenStore {

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun getToken(context: Context): String? {
        return sanitize(CredentialKeys.GITHUB_TOKEN.storedOrNull())
    }

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun setToken(context: Context, token: String?) {
        // A null token removes the key; written with apply() as before.
        CredentialKeys.GITHUB_TOKEN.set(sanitize(token))
    }

    @JvmStatic
    fun clearToken(context: Context) {
        setToken(context, null)
    }

    @JvmStatic
    fun hasToken(context: Context): Boolean {
        return getToken(context) != null
    }

    @JvmStatic
    fun getTokenSuffix(context: Context): String? {
        return getToken(context)?.takeLast(4)
    }

    private fun sanitize(token: String?): String? {
        var value = token
            ?.trim()
            ?.replace("\r", "")
            ?.replace("\n", "")
            ?: return null
        if (value.startsWith("Authorization:", ignoreCase = true)) {
            value = value.substringAfter(':').trim()
        }
        if (value.startsWith("Bearer ", ignoreCase = true)) {
            value = value.substringAfter(' ').trim()
        }
        if (value.startsWith("token ", ignoreCase = true)) {
            value = value.substringAfter(' ').trim()
        }
        return value.takeIf { it.isNotBlank() }
    }
}
