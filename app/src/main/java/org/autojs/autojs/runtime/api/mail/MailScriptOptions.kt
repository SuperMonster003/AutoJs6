package org.autojs.autojs.runtime.api.mail

import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.mail.MailAccountSpec
import org.autojs.autojs.core.plugin.mail.MailErrorMapper
import org.autojs.autojs.core.plugin.mail.MailPluginException
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes

/**
 * The `mail.connect(options | alias)` argument as the host client needs it (mail roadmap P3.1,
 * appendix A.2): the [spec] handed to `openSession` (account JSON without secrets plus the
 * password or access token), the [debug] flag (decision D28) and the [snapshot] a script sees as
 * `client.account`, which never carries `password`, `accessToken` or `tokenProvider`. Shape
 * checks that do not need the provider table happen here so a script gets `INVALID_ARGUMENT`
 * with a precise message before any Binder call; the plugin validates the rest.
 * zh-CN: `mail.connect(options | alias)` 的参数在宿主客户端需要的形态 (邮件路线图 P3.1, 附录 A.2): 交给 `openSession` 的 [spec] (不含秘密的
 * 账户 JSON 加密码或访问令牌), [debug] 标志 (决策 D28), 以及脚本经 `client.account` 看到的 [snapshot] (永不携带 `password` / `accessToken` /
 * `tokenProvider`). 不依赖服务商表的形态检查在这里完成, 脚本在任何 Binder 调用前就得到带精确文案的 `INVALID_ARGUMENT`; 其余由插件校验.
 */
class MailScriptOptions private constructor(
    val spec: MailAccountSpec,
    val debug: Boolean,
    val snapshot: JsonObject,
) {

    val alias: String?
        get() = spec.alias

    /** A one-line description without secrets. zh-CN: 不含秘密的单行描述. */
    fun describe(): String = when {
        alias != null -> "alias=$alias"
        else -> listOfNotNull(
            snapshot.stringOrNull(PROVIDER)?.let { "provider=$it" },
            snapshot.stringOrNull(ADDRESS)?.let { "address=$it" },
            snapshot.stringOrNull(RECEIVE)?.let { "receive=$it" },
        ).joinToString(", ")
    }

    companion object {

        const val PROVIDER = "provider"
        const val ADDRESS = "address"
        const val USER = "user"
        const val NAME = "name"
        const val AUTH = "auth"
        const val PASSWORD = "password"
        const val ACCESS_TOKEN = "accessToken"
        const val TOKEN_PROVIDER = "tokenProvider"
        const val RECEIVE = "receive"
        const val IMAP = "imap"
        const val POP3 = "pop3"
        const val SMTP = "smtp"
        const val TIMEOUT = "timeout"
        const val TLS = "tls"
        const val DEBUG = "debug"
        const val CLIENT_ID = "clientId"

        /** Every key `mail.connect` accepts; anything else is refused by name. zh-CN: `mail.connect` 接受的全部键; 其余按名拒绝. */
        val KNOWN_KEYS: Set<String> = setOf(
            PROVIDER, ADDRESS, USER, NAME, AUTH, PASSWORD, ACCESS_TOKEN, TOKEN_PROVIDER, RECEIVE,
            IMAP, POP3, SMTP, TIMEOUT, TLS, DEBUG, CLIENT_ID,
        )

        private val ENDPOINTS = listOf(IMAP, POP3, SMTP)
        private val TIMEOUT_KEYS = setOf("connect", "read", "write")
        private val TLS_KEYS = setOf("trustAll")
        private val CLIENT_ID_KEYS = setOf("name", "version")
        private val ENDPOINT_KEYS = setOf("host", "port", "tls")

        /** The alias form: an account saved in the plugin (roadmap P4). zh-CN: 别名形态: 保存在插件里的账户 (路线图 P4). */
        fun alias(alias: String): MailScriptOptions {
            if (alias.isBlank()) throw invalid("mail account alias must not be blank")
            val snapshot = JsonObject().apply { addProperty("alias", alias.trim()) }
            return MailScriptOptions(MailAccountSpec.alias(alias.trim()), debug = false, snapshot = snapshot)
        }

        /**
         * The inline form. [options] is the script object as JSON (functions are already gone);
         * [hasTokenProvider] tells whether the script passed a `tokenProvider` function.
         * zh-CN: 内联形态. [options] 是脚本对象的 JSON (函数已不在其中); [hasTokenProvider] 表示脚本是否传了 `tokenProvider` 函数.
         */
        fun inline(options: JsonObject, hasTokenProvider: Boolean = false): MailScriptOptions {
            options.keySet().firstOrNull { it !in KNOWN_KEYS }?.let { key ->
                throw invalid("unknown mail account option '$key'")
            }
            val address = options.requireString(ADDRESS)
            if (!address.contains('@') || address.startsWith("@") || address.endsWith("@")) {
                throw invalid("'address' must be a mail address, got '$address'")
            }
            listOf(PROVIDER, USER, NAME).forEach { key -> options.optionalString(key) }
            val password = options.optionalString(PASSWORD)
            val accessToken = options.optionalString(ACCESS_TOKEN)
            if (password != null && accessToken != null) throw invalid("'password' and 'accessToken' are exclusive")
            if (password == null && accessToken == null && !hasTokenProvider) {
                throw invalid("'password', 'accessToken' or 'tokenProvider' is required")
            }
            if (password != null && password.isEmpty()) throw invalid("'password' must not be empty")
            if (accessToken != null && accessToken.isEmpty()) throw invalid("'accessToken' must not be empty")
            if (hasTokenProvider && password != null) throw invalid("'tokenProvider' needs an access token account, not a password")

            val auth = options.optionalOneOf(AUTH, MailContract.AUTH_MECHANISMS)
            val usesToken = accessToken != null || hasTokenProvider
            if (auth == MailContract.AUTH_PASSWORD && usesToken) throw invalid("'auth: password' cannot be combined with an access token")
            if (auth == MailContract.AUTH_XOAUTH2 && password != null) throw invalid("'auth: xoauth2' needs 'accessToken', not 'password'")
            options.optionalOneOf(RECEIVE, listOf(MailContract.PROTOCOL_IMAP, MailContract.PROTOCOL_POP3))
            ENDPOINTS.forEach { key -> options.optionalObject(key)?.let { endpoint -> checkEndpoint(key, endpoint) } }
            options.optionalObject(TIMEOUT)?.let { timeout ->
                timeout.rejectUnknown(TIMEOUT, TIMEOUT_KEYS)
                TIMEOUT_KEYS.forEach { key -> timeout.optionalInt("$TIMEOUT.$key", key, 0..Int.MAX_VALUE) }
            }
            options.optionalObject(TLS)?.let { tls ->
                tls.rejectUnknown(TLS, TLS_KEYS)
                tls.optionalBoolean("$TLS.trustAll", "trustAll")
            }
            options.optionalObject(CLIENT_ID)?.let { clientId ->
                clientId.rejectUnknown(CLIENT_ID, CLIENT_ID_KEYS)
                CLIENT_ID_KEYS.forEach { key -> clientId.optionalString("$CLIENT_ID.$key", key) }
            }
            val debug = options.optionalBoolean(DEBUG, DEBUG) ?: false

            val snapshot = options.deepCopy().apply {
                remove(PASSWORD)
                remove(ACCESS_TOKEN)
                remove(TOKEN_PROVIDER)
                if (auth == null && usesToken) addProperty(AUTH, MailContract.AUTH_XOAUTH2)
            }
            val spec = MailAccountSpec.inline(snapshot.toString(), password = password, accessToken = accessToken)
            spec.validate()?.let { throw MailPluginException(it) }
            return MailScriptOptions(spec, debug, snapshot)
        }

        private fun checkEndpoint(name: String, endpoint: JsonObject) {
            endpoint.rejectUnknown(name, ENDPOINT_KEYS)
            endpoint.optionalString("$name.host", "host")?.let { host ->
                if (host.isBlank()) throw invalid("'$name.host' must not be blank")
            }
            endpoint.optionalInt("$name.port", "port", 1..65535)
            endpoint.optionalOneOf("$name.tls", "tls", MailContract.TLS_MODES)
        }

        internal fun invalid(message: String): MailPluginException =
            MailPluginException(MailErrorMapper.normalize(MailErrorCodes.INVALID_ARGUMENT, message, retryable = false))

        private fun JsonObject.rejectUnknown(label: String, known: Set<String>) {
            keySet().firstOrNull { it !in known }?.let { key -> throw invalid("unknown mail account option '$label.$key'") }
        }

        private fun JsonObject.requireString(key: String): String {
            val value = optionalString(key) ?: throw invalid("'$key' is required")
            if (value.isBlank()) throw invalid("'$key' must not be blank")
            return value
        }

        private fun JsonObject.optionalString(key: String): String? = optionalString(key, key)

        private fun JsonObject.optionalString(label: String, key: String): String? {
            val element = get(key) ?: return null
            if (element.isJsonNull) return null
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) throw invalid("'$label' must be a string")
            return element.asString
        }

        private fun JsonObject.optionalOneOf(key: String, allowed: Collection<String>): String? = optionalOneOf(key, key, allowed)

        private fun JsonObject.optionalOneOf(label: String, key: String, allowed: Collection<String>): String? {
            val value = optionalString(label, key) ?: return null
            if (value !in allowed) throw invalid("'$label' must be one of ${allowed.joinToString(", ")}, got '$value'")
            return value
        }

        private fun JsonObject.optionalInt(label: String, key: String, range: IntRange): Int? {
            val element = get(key) ?: return null
            if (element.isJsonNull) return null
            val number = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
                ?: throw invalid("'$label' must be a number")
            if (number != Math.floor(number) || number < range.first || number > range.last) {
                throw invalid("'$label' must be an integer in ${range.first}..${range.last}, got $number")
            }
            return number.toInt()
        }

        private fun JsonObject.optionalBoolean(label: String, key: String): Boolean? {
            val element = get(key) ?: return null
            if (element.isJsonNull) return null
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) throw invalid("'$label' must be a boolean")
            return element.asBoolean
        }

        private fun JsonObject.optionalObject(key: String): JsonObject? {
            val element = get(key) ?: return null
            if (element.isJsonNull) return null
            if (!element.isJsonObject) throw invalid("'$key' must be an object")
            return element.asJsonObject
        }

        private fun JsonObject.stringOrNull(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    }
}
