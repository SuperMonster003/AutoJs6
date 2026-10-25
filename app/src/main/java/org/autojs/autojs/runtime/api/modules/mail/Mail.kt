package org.autojs.autojs.runtime.api.modules.mail

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.ScriptableExtensions.getProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.api.mail.MailScriptOptions
import org.autojs.autojs.runtime.api.mail.MailService
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.plugin.mail.api.MailErrorCodes
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.function.Supplier

/**
 * The global `mail` object (mail roadmap P3.1, appendix A.1 and A.2): `connect(options | alias)`
 * / `connectAsync`, `setDefault(client | options | alias)`, `default`, `providers.list() / get(id)
 * / resolve(address)`, `accounts.list() / has(alias)`, the `MailError` constructor and `close()`;
 * every client method of appendix A.3 ([FORWARDED], with the `Async` forms) is mirrored here and
 * forwards to the default client, throwing (or rejecting with) `NO_DEFAULT_ACCOUNT` without one.
 * Argument shapes are checked in `MailScriptOptions` (pure Kotlin); only the Rhino conversion
 * happens here.
 * zh-CN: 全局 `mail` 对象 (邮件路线图 P3.1, 附录 A.1 与 A.2): `connect(options | alias)` / `connectAsync`, `setDefault(client | options | alias)`,
 * `default`, `providers.list() / get(id) / resolve(address)`, `accounts.list() / has(alias)`, `MailError` 构造器与 `close()`; 附录 A.3 的每个客户端
 * 方法 ([FORWARDED], 含 `Async` 形态) 在此镜像并转发到默认客户端, 无默认客户端时抛出 (或拒绝为) `NO_DEFAULT_ACCOUNT`. 参数形态在
 * `MailScriptOptions` (纯 Kotlin) 检查; 这里只做 Rhino 转换.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class Mail(
    private val scriptRuntime: ScriptRuntime,
    private val service: MailService,
) : Augmentable(scriptRuntime) {

    override val keys = listOf(AugmentableKey("mail"))

    override val selfAssignmentFunctions = listOf(
        ::connect.name,
        ::connectAsync.name,
        ::setDefault.name,
        ::close.name,
    )

    /** `MailError` instances thrown by these functions reach the script unwrapped. zh-CN: 这些函数抛出的 `MailError` 实例不经包装到达脚本. */
    override val scriptErrorPassThroughFunctions: Set<String>
        get() = selfAssignmentFunctions.toSet()

    override val selfAssignmentGetters = listOf<Any>(
        "default" to Supplier<Any?> { defaultClient },
    )

    override val selfAssignmentProperties: List<Pair<Any, Any?>> get() = scriptProperties

    /**
     * The data properties the `mail` script module defines on its module object, in their order (its install hook
     * defines them, roadmap P5.2).
     * zh-CN: `mail` 脚本模块在模块对象上定义的数据属性, 按顺序 (由其 install 钩子定义, P5.2).
     */
    /** The value of the `mail.default` getter: the default client of the runtime. */
    internal val defaultClient: Any?
        get() = service.defaultClient

    val scriptProperties: List<Pair<Any, Any?>> by lazy {
        listOf<Pair<Any, Any?>>(
            MailJsErrors.NAME to MailJsErrors.constructor(scriptRuntime),
            "providers" to providersObject(),
            "accounts" to accountsObject(),
        ) + FORWARDED
            .flatMap { name -> if (name in SYNC_ONLY) listOf(name) else listOf(name, name + ASYNC_SUFFIX) }
            .map { name -> name to newBaseFunction(scriptRuntime.topLevelScope, name, { args -> forward(name, args) }, NOT_CONSTRUCTABLE) }
    }

    @RhinoRuntimeFunctionInterface
    fun connect(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsOnlyOne(args) { raw -> open(raw) }
    }

    @RhinoRuntimeFunctionInterface
    fun connectAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            val client = try {
                newClient(raw)
            } catch (t: Throwable) {
                return@ensureArgumentsOnlyOne MailPromises.rejected(scriptRuntime, t)
            }
            client.async { _ ->
                try {
                    client.session.open()
                    client
                } catch (t: Throwable) {
                    client.discard()
                    throw t
                }
            }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun setDefault(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            val client = raw as? MailClientNativeObject ?: open(raw)
            service.setDefault(client)
            client
        }
    }

    @RhinoRuntimeFunctionInterface
    fun close(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsIsEmpty(args) {
            service.defaultClient?.close()
            UNDEFINED
        }
    }

    // ------------------------------------------------------------------ internals

    /** Opens a client for [raw] eagerly, so refusals of the plugin surface at `connect`. zh-CN: 为 [raw] 立即打开客户端, 插件的拒绝在 `connect` 处即浮现. */
    private fun open(raw: Any?): MailClientNativeObject {
        val client = try {
            newClient(raw)
        } catch (t: Throwable) {
            MailJsErrors.throwInto(scriptRuntime, t)
        }
        try {
            client.sync { _ -> client.session.open() }
        } catch (t: Throwable) {
            client.discard()
            throw t
        }
        return client
    }

    private fun newClient(raw: Any?): MailClientNativeObject {
        if (raw is CharSequence) {
            return service.newClient(MailScriptOptions.alias(raw.toString()), tokenProvider = null)
        }
        if (raw.isJsNullish() || raw !is Scriptable || raw is Callable) {
            throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "mail.connect expects an account options object or a saved account alias, got ${raw.jsBrief()}")
        }
        val provider = raw.getProp(MailScriptOptions.TOKEN_PROVIDER).takeUnless { it.isJsNullish() }?.let { value ->
            value as? Callable ?: throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "'${MailScriptOptions.TOKEN_PROVIDER}' must be a function")
        }
        val json = toJsonObject(raw)
        if (provider != null && !json.has(MailScriptOptions.ACCESS_TOKEN)) {
            json.addProperty(MailScriptOptions.ACCESS_TOKEN, callTokenProvider(provider))
        }
        val options = MailScriptOptions.inline(json, hasTokenProvider = provider != null)
        val tokenProvider: (suspend () -> String)? = provider?.let { function -> { callTokenProvider(function) } }
        return service.newClient(options, tokenProvider)
    }

    /** The options object as JSON: functions and `undefined` values fall away like in `JSON.stringify`. zh-CN: 选项对象的 JSON: 函数与 `undefined` 值如 `JSON.stringify` 一样消失. */
    private fun toJsonObject(value: Scriptable): JsonObject {
        val serialized = js_json_stringify(value)
        if (serialized is Undefined) throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "mail account options must be a plain object")
        val parsed = try {
            JsonParser.parseString(Context.toString(serialized))
        } catch (e: RuntimeException) {
            throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "mail account options must be JSON-compatible: ${e.message.orEmpty()}")
        }
        if (!parsed.isJsonObject) throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "mail account options must be a plain object")
        return parsed.asJsonObject
    }

    /** Calls the script's `tokenProvider`; it must return a non-empty string synchronously. zh-CN: 调用脚本的 `tokenProvider`; 它必须同步返回非空字符串. */
    private fun callTokenProvider(function: Callable): String {
        val result = RhinoUtils.callFunction(scriptRuntime, function, scriptRuntime.topLevelScope, null, emptyArray())
        val token = (result as? CharSequence)?.toString()?.takeIf { it.isNotBlank() }
            ?: throw MailJsErrors.failure(MailErrorCodes.AUTH_FAILED, "'${MailScriptOptions.TOKEN_PROVIDER}' must return a non-empty access token string, got ${result.jsBrief()}")
        return token
    }

    private fun forward(name: String, args: Array<out Any?>): Any? = guarded {
        val async = name.endsWith(ASYNC_SUFFIX)
        val client = service.defaultClient ?: return@guarded fail(async, MailErrorCodes.NO_DEFAULT_ACCOUNT, "no default mail account: call mail.setDefault(client) first")
        val function = ScriptableObject.getProperty(client, name) as? Callable
            ?: return@guarded fail(async, MailErrorCodes.UNSUPPORTED_OPERATION, "mail.$name is not available in this build")
        RhinoUtils.callFunction(scriptRuntime, function, scriptRuntime.topLevelScope, client, arrayOf(*args))
    }

    private fun fail(async: Boolean, code: String, message: String): Any {
        val failure = MailJsErrors.failure(code, message)
        if (async) return MailPromises.rejected(scriptRuntime, failure)
        MailJsErrors.throwInto(scriptRuntime, failure)
    }

    private fun providersObject(): NativeObject = nativeObject { target ->
        target.defineFunction("list") { args -> ensureArgumentsIsEmpty(args) { toJs(service.providers().list()) } }
        target.defineFunction("get") { args -> ensureArgumentsOnlyOne(args) { id -> toJs(service.providers().get(stringOrNull(id))) } }
        target.defineFunction("resolve") { args -> ensureArgumentsOnlyOne(args) { address -> toJs(service.providers().resolve(stringOrNull(address))) } }
    }

    private fun accountsObject(): NativeObject = nativeObject { target ->
        target.defineFunction("list") { args -> ensureArgumentsIsEmpty(args) { toJs(service.savedAccounts()) } }
        target.defineFunction("has") { args ->
            ensureArgumentsOnlyOne(args) { alias ->
                val wanted = stringOrNull(alias)?.trim()
                wanted != null && wanted.isNotEmpty() && service.savedAccounts().any { account ->
                    (account as? Map<*, *>)?.get("alias") == wanted
                }
            }
        }
    }

    /** Host failures and argument guards leave as `MailError`; script values pass unchanged. zh-CN: 宿主失败与参数守卫以 `MailError` 离开; 脚本值原样通过. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        MailJsErrors.throwInto(scriptRuntime, t)
    }

    private fun toJs(value: Any?): Any? = RhinoUtils.toJsValue(scriptRuntime.topLevelScope, value)

    private fun stringOrNull(value: Any?): String? = when {
        value.isJsNullish() -> null
        value is CharSequence -> value.toString()
        else -> Context.toString(value)
    }

    private fun nativeObject(init: (NativeObject) -> Unit): NativeObject {
        val target = RhinoUtils.newNativeObject()
        init(target)
        return target
    }

    private fun NativeObject.defineFunction(name: String, body: (Array<out Any?>) -> Any?) {
        defineProperty(
            name,
            newBaseFunction(scriptRuntime.topLevelScope, name, { args -> guarded { body(args) } }, NOT_CONSTRUCTABLE),
            ScriptableObject.READONLY or ScriptableObject.DONTENUM or ScriptableObject.PERMANENT,
        )
    }

    private companion object {
        const val ASYNC_SUFFIX = "Async"

        /** Methods without an `Async` form: they answer without waiting for the network. zh-CN: 没有 `Async` 形态的方法: 它们不等待网络即应答. */
        val SYNC_ONLY: Set<String> = setOf("folder", "watch")

        /** Client methods mirrored on `mail` (appendix A.3); each has an `Async` form except [SYNC_ONLY]. zh-CN: 在 `mail` 上镜像的客户端方法 (附录 A.3); 除 [SYNC_ONLY] 外各有 `Async` 形态. */
        val FORWARDED: List<String> = listOf(
            "test", "send", "folders", "folder", "folderStatus", "createFolder", "deleteFolder", "renameFolder",
            "fetch", "search", "get", "fetchBody", "download", "raw", "setFlags", "markRead", "markUnread", "flag", "unflag",
            "move", "copy", "delete", "expunge", "append", "watch",
        )
    }
}
