package org.autojs.autojs.network

import android.content.Context

object GitHubTokenStore {

    private const val PREF_NAME = "github_auth"
    private const val KEY_TOKEN = "github_token"

    @JvmStatic
    fun getToken(context: Context): String? {
        return sanitize(getPrefs(context).getString(KEY_TOKEN, null))
    }

    @JvmStatic
    fun setToken(context: Context, token: String?) {
        val sanitized = sanitize(token)
        getPrefs(context).edit().let { editor ->
            if (sanitized == null) {
                editor.remove(KEY_TOKEN)
            } else {
                editor.putString(KEY_TOKEN, sanitized)
            }
            editor.apply()
        }
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

    private fun getPrefs(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

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
