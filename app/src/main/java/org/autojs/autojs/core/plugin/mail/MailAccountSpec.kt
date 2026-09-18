package org.autojs.autojs.core.plugin.mail

import org.autojs.plugin.mail.api.MailErrorCodes

/**
 * What the host hands to `IMailPlugin.openSession`: either the account JSON without secrets plus
 * the password or access token, or the alias of an account saved in the plugin (roadmap B.1).
 * Secrets live only in this object and in the open-session bundle; `toString()` never prints them.
 */
class MailAccountSpec private constructor(
    val accountJson: String?,
    val alias: String?,
    val password: String?,
    val accessToken: String?,
) {

    val usesAlias: Boolean
        get() = alias != null

    fun withAccessToken(token: String): MailAccountSpec = MailAccountSpec(accountJson, alias, password, token)

    /** Validates the shape; returns the error instead of throwing so callers can map it. */
    fun validate(): MailError? {
        if (alias != null) {
            if (alias.isBlank()) return invalid("account alias must not be blank")
            if (accountJson != null || password != null || accessToken != null) return invalid("account alias excludes inline account fields")
            return null
        }
        if (accountJson.isNullOrBlank()) return invalid("account is missing")
        runCatching { MailJson.requireObject(accountJson, "account") }
            .onFailure { return (it as? MailPluginException)?.error ?: invalid("account is not valid JSON") }
        if (password != null && accessToken != null) return invalid("password and access token are exclusive")
        return null
    }

    override fun toString(): String {
        return "MailAccountSpec(alias=$alias, inline=${accountJson != null}, password=${mask(password)}, accessToken=${mask(accessToken)})"
    }

    private fun mask(secret: String?): String = if (secret == null) "null" else "***"

    private fun invalid(message: String) = MailError(MailErrorCodes.INVALID_ARGUMENT, message, retryable = false)

    companion object {

        fun inline(accountJson: String, password: String? = null, accessToken: String? = null): MailAccountSpec {
            return MailAccountSpec(accountJson, null, password, accessToken)
        }

        fun alias(alias: String): MailAccountSpec = MailAccountSpec(null, alias, null, null)
    }
}
