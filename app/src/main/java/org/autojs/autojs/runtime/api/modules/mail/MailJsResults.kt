package org.autojs.autojs.runtime.api.modules.mail

import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.plugin.mail.api.MailErrorCodes
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.VarScope

/**
 * How the result documents of the plugin become script objects (mail roadmap P3.2, appendix
 * A.4): a `MailMessage` is a plain object whose `date` / `receivedDate` are JavaScript `Date`
 * values (decision D26), whose addresses print as `Name <address>`, whose attachments carry the
 * `uid` and `folder` of their message plus a bound `download(target?, options?)`, and which has
 * a bound `load(options?)` for the body; every bound method also exists as `xAsync`. Bound
 * methods are non-enumerable, so `JSON.stringify(message)` and passing a message back into the
 * API keep working. Runs on the script thread inside a Rhino context.
 * zh-CN: 插件的结果文档如何成为脚本对象 (邮件路线图 P3.2, 附录 A.4): `MailMessage` 是普通对象, `date` / `receivedDate` 为 JavaScript `Date`
 * (决策 D26), 地址打印为 `Name <address>`, 附件携带所属邮件的 `uid` 与 `folder` 及绑定的 `download(target?, options?)`, 邮件带绑定的
 * `load(options?)` 取正文; 每个绑定方法都有 `xAsync` 形态. 绑定方法不可枚举, 因此 `JSON.stringify(message)` 与把邮件传回 API 都照常工作.
 * 在脚本线程的 Rhino 上下文内运行.
 */
internal object MailJsResults {

    /** What the bound methods of messages and attachments call. zh-CN: 邮件与附件的绑定方法所调用的对象. */
    interface Binding {
        fun load(message: Scriptable, options: Any?): Any?
        fun loadAsync(message: Scriptable, options: Any?): Any?
        fun download(attachment: Scriptable, target: Any?, options: Any?): Any?
        fun downloadAsync(attachment: Scriptable, target: Any?, options: Any?): Any?
    }

    const val LOAD = "load"
    const val LOAD_ASYNC = "loadAsync"
    const val DOWNLOAD = "download"
    const val DOWNLOAD_ASYNC = "downloadAsync"
    const val FALLBACK = "fallback"

    private val DATE_KEYS = listOf("date", "receivedDate")
    private val SINGLE_ADDRESS_KEYS = listOf("from", "sender")
    private val ADDRESS_LIST_KEYS = listOf("replyTo", "to", "cc", "bcc")
    private val ATTACHMENT_LIST_KEYS = listOf("attachments", "bodyParts")

    /** One `MailMessage`. zh-CN: 一封 `MailMessage`. */
    fun message(cx: Context, scope: VarScope, plain: Any?, binding: Binding): Scriptable {
        val message = RhinoUtils.toJsValue(scope, plain) as? NativeObject
            ?: throw MailJsErrors.failure(MailErrorCodes.INTERNAL, "the mail plugin sent no message object")
        decorateMessage(cx, scope, message, binding)
        return message
    }

    /** A `MailMessage[]`. zh-CN: `MailMessage[]`. */
    fun messages(cx: Context, scope: VarScope, plain: Any?, binding: Binding): Scriptable {
        val list = plain as? List<*> ?: emptyList<Any?>()
        val array = cx.newArray(scope, list.size)
        list.forEachIndexed { index, item -> array.put(index, array, message(cx, scope, item, binding)) }
        return array
    }

    /** The `messages.search` result: the page as an array with a non-enumerable `fallback` marker. zh-CN: `messages.search` 结果: 页作为数组, 附不可枚举的 `fallback` 标记. */
    fun search(cx: Context, scope: VarScope, plain: Any?, binding: Binding): Scriptable {
        val document = plain as? Map<*, *>
        val array = messages(cx, scope, document?.get("messages"), binding)
        ScriptableObject.defineProperty(array, FALLBACK, document?.get(FALLBACK) ?: "server", DONTENUM)
        return array
    }

    /** A JavaScript `Date` for UTC [millis], or null. zh-CN: UTC [millis] 的 JavaScript `Date`, 或 null. */
    fun date(cx: Context, scope: VarScope, millis: Any?): Any? =
        (millis as? Number)?.let { cx.newObject(scope, "Date", arrayOf<Any?>(it.toDouble())) }

    /** `Name <address>` or the bare address. zh-CN: `Name <address>` 或裸地址. */
    fun formatAddress(address: Scriptable): String {
        val name = (ScriptableObject.getProperty(address, "name") as? CharSequence)?.toString()?.trim().orEmpty()
        val mailbox = ScriptableObject.getProperty(address, "address").let { if (it == Scriptable.NOT_FOUND || it == null) "" else Context.toString(it) }
        return if (name.isEmpty()) mailbox else "$name <$mailbox>"
    }

    private fun decorateMessage(cx: Context, scope: VarScope, message: NativeObject, binding: Binding) {
        DATE_KEYS.forEach { key ->
            val raw = ScriptableObject.getProperty(message, key)
            if (raw is Number) message.put(key, message, date(cx, scope, raw))
        }
        SINGLE_ADDRESS_KEYS.forEach { key -> (ScriptableObject.getProperty(message, key) as? NativeObject)?.let { decorateAddress(scope, it) } }
        ADDRESS_LIST_KEYS.forEach { key -> (ScriptableObject.getProperty(message, key) as? NativeArray)?.forEachObject { decorateAddress(scope, it) } }
        val uid = ScriptableObject.getProperty(message, "uid")
        val folder = ScriptableObject.getProperty(message, "folder")
        ATTACHMENT_LIST_KEYS.forEach { key ->
            (ScriptableObject.getProperty(message, key) as? NativeArray)?.forEachObject { decorateAttachment(scope, it, uid, folder, binding) }
        }
        define(scope, message, LOAD, 1) { a -> binding.load(message, a.getOrNull(0)) }
        define(scope, message, LOAD_ASYNC, 1) { a -> binding.loadAsync(message, a.getOrNull(0)) }
    }

    private fun decorateAddress(scope: VarScope, address: NativeObject) {
        define(scope, address, "toString", 0) { formatAddress(address) }
    }

    private fun decorateAttachment(scope: VarScope, attachment: NativeObject, uid: Any?, folder: Any?, binding: Binding) {
        // Enumerable on purpose: the attachment must still identify its message after JSON.stringify.
        // zh-CN: 故意可枚举: 附件经 JSON.stringify 后仍须能指认所属邮件.
        if (!attachment.has("uid", attachment) && uid != Scriptable.NOT_FOUND) attachment.put("uid", attachment, uid)
        if (!attachment.has("folder", attachment) && folder != Scriptable.NOT_FOUND) attachment.put("folder", attachment, folder)
        define(scope, attachment, DOWNLOAD, 2) { a -> binding.download(attachment, a.getOrNull(0), a.getOrNull(1)) }
        define(scope, attachment, DOWNLOAD_ASYNC, 2) { a -> binding.downloadAsync(attachment, a.getOrNull(0), a.getOrNull(1)) }
    }

    /**
     * A bound method taking at most [maxArgs] arguments; excess arguments throw `MailError`
     * (INVALID_ARGUMENT) into the script. The host argument guards are not used here because
     * their messages need an application context.
     * zh-CN: 最多取 [maxArgs] 个参数的绑定方法; 多余参数向脚本抛 `MailError` (INVALID_ARGUMENT). 这里不用宿主参数守卫, 因其消息需要应用上下文.
     */
    private fun define(scope: VarScope, target: NativeObject, name: String, maxArgs: Int, body: (Array<out Any?>) -> Any?) {
        val guarded: (Array<out Any?>) -> Any? = { args ->
            if (args.size > maxArgs) {
                val failure = MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "$name expects at most $maxArgs argument${if (maxArgs == 1) "" else "s"}, got ${args.size}")
                throw MailJsErrors.jsException(Context.getCurrentContext(), scope, failure)
            }
            body(args)
        }
        target.defineProperty(name, newBaseFunction(scope, name, guarded, NOT_CONSTRUCTABLE), DONTENUM or READONLY or PERMANENT)
    }

    private fun NativeArray.forEachObject(action: (NativeObject) -> Unit) {
        for (index in 0 until length.toInt()) {
            (get(index, this) as? NativeObject)?.let(action)
        }
    }
}
