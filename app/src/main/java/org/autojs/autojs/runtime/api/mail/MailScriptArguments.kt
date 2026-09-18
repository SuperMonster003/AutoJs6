package org.autojs.autojs.runtime.api.mail

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.autojs.autojs.core.plugin.mail.MailPluginException
import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.math.floor

/**
 * Argument normalization of the `MailClient` methods (mail roadmap P3.2, appendix A.3): the
 * script values arrive as Gson trees (the Rhino layer serializes each argument like
 * `JSON.stringify`, so functions and `undefined` are already gone and `Date` values are ISO-8601
 * strings) and leave as the `args` object of the matching operation. UIDs may be given as a
 * positive integer, a POP3 UIDL string or a message / attachment object carrying `uid` (and the
 * `folder` it came from, used when the options name none). Unknown keys are refused by name
 * before anything reaches the plugin. Nothing here touches Rhino, so the JVM tests cover it.
 * zh-CN: `MailClient` 方法的参数规范化 (邮件路线图 P3.2, 附录 A.3): 脚本值以 Gson 树到达 (Rhino 层像 `JSON.stringify` 一样序列化每个参数,
 * 函数与 `undefined` 已消失, `Date` 已是 ISO-8601 字符串), 离开时是对应操作的 `args` 对象. UID 可为正整数, POP3 UIDL 字符串或携带 `uid`
 * (及来源 `folder`, 选项未给文件夹时采用) 的邮件 / 附件对象. 未知键在到达插件前按名拒绝. 这里不触碰 Rhino, 因此由 JVM 测试覆盖.
 */
internal object MailScriptArguments {

    /** A streamed download: the operation args plus the target file name, the size hint (encoded part size, budgets the timeout) and the overwrite flag. zh-CN: 流式下载: 操作参数加目标文件名, 大小提示 (编码后分部大小, 用于超时预算) 与覆盖标志. */
    class Download(val args: JsonObject, val fileName: String?, val size: Long?, val overwrite: Boolean)

    /** An outgoing message: the operation args plus the attachment files in `descriptorIndex` order. zh-CN: 发出的邮件: 操作参数加按 `descriptorIndex` 排序的附件文件. */
    class Outgoing(val args: JsonObject, val attachmentPaths: List<String>)

    const val FOLDER = "folder"
    const val UID = "uid"
    const val UIDS = "uids"
    const val PART_ID = "partId"
    const val FILE_NAME = "fileName"
    const val SIZE = "size"
    const val OVERWRITE = "overwrite"
    const val ON_PROGRESS = "onProgress"

    val MODES: Set<String> = setOf("add", "remove", "set")
    val FALLBACKS: Set<String> = setOf("client", "none", "always")
    val ORDERS: Set<String> = setOf("desc", "asc")

    private val FETCH_KEYS = setOf(FOLDER, "limit", "before", "after", "order", "unseenOnly")
    private val SEARCH_KEYS = setOf(FOLDER, "limit", "before", "fallback")
    private val GET_KEYS = setOf(FOLDER, "peek", "includeRaw")
    private val RAW_KEYS = setOf(FOLDER, OVERWRITE)
    private val DOWNLOAD_KEYS = setOf(FOLDER, OVERWRITE)
    private val ATTACHMENT_TARGET_KEYS = setOf(UID, PART_ID, FOLDER, FILE_NAME, SIZE, "mimeType", "contentId", "inline")
    private val FLAGS_KEYS = setOf(FOLDER)
    private val TRANSFER_KEYS = setOf("from", FOLDER)
    private val DELETE_KEYS = setOf(FOLDER, "expunge")
    private val FOLDERS_KEYS = setOf("subscribedOnly", "status")
    private val SEND_KEYS = setOf("saveToSent")
    private val MESSAGE_KEYS = setOf("from", "to", "cc", "bcc", "replyTo", "subject", "text", "html", "attachments", "headers", "priority", "inReplyTo", "references", "date")
    private val OUTGOING_ATTACHMENT_KEYS = setOf("path", FILE_NAME, "mimeType", "contentId", "inline")

    // ------------------------------------------------------------------ receiving

    /** `fetch(options?)` -> `messages.list`. */
    fun fetch(options: JsonElement?): JsonObject = JsonObject().apply {
        optionalObject(options, "options")?.let { copyKnown(it, this, "options", FETCH_KEYS) }
        normalizeUid(this, "before", "options.before")
        normalizeUid(this, "after", "options.after")
        optionalInt(this, "limit", "options.limit")
        optionalOneOf(this, "order", "options.order", ORDERS)
        optionalBoolean(this, "unseenOnly", "options.unseenOnly")
    }

    /** `search(query, options?)` -> `messages.search`. */
    fun search(query: JsonElement?, options: JsonElement?): JsonObject = JsonObject().apply {
        val queryObject = query?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw invalid("'query' must be an object such as {subject: 'invoice', since: '2026-09-01'}")
        add("query", queryObject)
        optionalObject(options, "options")?.let { copyKnown(it, this, "options", SEARCH_KEYS) }
        normalizeUid(this, "before", "options.before")
        optionalInt(this, "limit", "options.limit")
        optionalOneOf(this, "fallback", "options.fallback", FALLBACKS)
    }

    /** `get(uid | message, options?)` -> `messages.get`. */
    fun get(target: JsonElement?, options: JsonElement?): JsonObject = JsonObject().apply {
        val (uid, folder) = uidAndFolder(target, "message")
        optionalObject(options, "options")?.let { copyKnown(it, this, "options", GET_KEYS) }
        optionalBoolean(this, "peek", "options.peek")
        optionalBoolean(this, "includeRaw", "options.includeRaw")
        add(UID, uid)
        if (!has(FOLDER) && folder != null) addProperty(FOLDER, folder)
    }

    /** `raw(uid | message, target?, options?)` -> `messages.raw`; the file is named `<uid>.eml` unless the target names it. */
    fun raw(target: JsonElement?, options: JsonElement?): Download {
        val (uid, folder) = uidAndFolder(target, "message")
        val opts = optionalObject(options, "options")?.also { it.rejectUnknown("options", RAW_KEYS) }
        val args = JsonObject().apply {
            add(UID, uid)
            (opts?.stringOrNull("options.folder", FOLDER) ?: folder)?.let { addProperty(FOLDER, it) }
        }
        return Download(args, "${uid.asString}.eml", null, opts?.booleanOrNull("options.overwrite", OVERWRITE) ?: false)
    }

    /** `download(attachment | {uid, partId}, target?, options?)` -> `attachments.download`. */
    fun download(target: JsonElement?, options: JsonElement?): Download {
        val attachment = target?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw invalid("'attachment' must be an attachment object or {uid, partId}")
        attachment.rejectUnknown("attachment", ATTACHMENT_TARGET_KEYS)
        val partId = attachment.stringOrNull("attachment.partId", PART_ID)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw invalid("'attachment.partId' is required")
        val uid = uid(attachment.get(UID), "attachment.uid")
        val opts = optionalObject(options, "options")?.also { it.rejectUnknown("options", DOWNLOAD_KEYS) }
        val folder = opts?.stringOrNull("options.folder", FOLDER) ?: attachment.stringOrNull("attachment.folder", FOLDER)
        val args = JsonObject().apply {
            add(UID, uid)
            addProperty(PART_ID, partId)
            folder?.let { addProperty(FOLDER, it) }
        }
        val size = attachment.longOrNull("attachment.size", SIZE)?.takeIf { it >= 0L }
        return Download(args, attachment.stringOrNull("attachment.fileName", FILE_NAME), size, opts?.booleanOrNull("options.overwrite", OVERWRITE) ?: false)
    }

    // ------------------------------------------------------------------ flags, moves, deletion

    /** `setFlags(uids, flags, mode?, options?)` -> `messages.setFlags`. */
    fun flags(uids: JsonElement?, flags: JsonElement?, mode: JsonElement?, options: JsonElement?): JsonObject {
        val (list, folder) = uids(uids, UIDS)
        val names = stringList(flags, "flags")
        if (names.isEmpty()) throw invalid("'flags' must name at least one flag, e.g. 'seen' or ['seen', 'flagged']")
        val modeText = mode?.takeUnless { it.isJsonNull }?.let { element ->
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it in MODES }
                ?: throw invalid("'mode' must be one of ${MODES.joinToString(", ")}")
        }
        val opts = optionalObject(options, "options")?.also { it.rejectUnknown("options", FLAGS_KEYS) }
        return JsonObject().apply {
            add(UIDS, list)
            add("flags", jsonArray(names))
            modeText?.let { addProperty("mode", it) }
            (opts?.stringOrNull("options.folder", FOLDER) ?: folder)?.let { addProperty(FOLDER, it) }
        }
    }

    /** `move(uids, folder, options?)` / `copy(...)` -> `messages.move` / `messages.copy`. */
    fun transfer(uids: JsonElement?, target: JsonElement?, options: JsonElement?): JsonObject {
        val (list, folder) = uids(uids, UIDS)
        val targetFolder = folderPath(target, "folder")
        val opts = optionalObject(options, "options")?.also { it.rejectUnknown("options", TRANSFER_KEYS) }
        val from = opts?.stringOrNull("options.from", "from") ?: opts?.stringOrNull("options.folder", FOLDER) ?: folder
        return JsonObject().apply {
            add(UIDS, list)
            addProperty("target", targetFolder)
            from?.let { addProperty(FOLDER, it) }
        }
    }

    /** `delete(uids, options?)` -> `messages.delete`. */
    fun delete(uids: JsonElement?, options: JsonElement?): JsonObject {
        val (list, folder) = uids(uids, UIDS)
        val opts = optionalObject(options, "options")?.also { it.rejectUnknown("options", DELETE_KEYS) }
        return JsonObject().apply {
            add(UIDS, list)
            (opts?.stringOrNull("options.folder", FOLDER) ?: folder)?.let { addProperty(FOLDER, it) }
            opts?.booleanOrNull("options.expunge", "expunge")?.let { addProperty("expunge", it) }
        }
    }

    /** `expunge(folder?)` -> `messages.expunge`. */
    fun expunge(folder: JsonElement?): JsonObject = JsonObject().apply {
        optionalFolder(folder, "folder")?.let { addProperty(FOLDER, it) }
    }

    // ------------------------------------------------------------------ folders

    /** `folders(options?)` -> `folders.list`. */
    fun folders(options: JsonElement?): JsonObject = JsonObject().apply {
        optionalObject(options, "options")?.let { copyKnown(it, this, "options", FOLDERS_KEYS) }
        optionalBoolean(this, "subscribedOnly", "options.subscribedOnly")
        optionalBoolean(this, "status", "options.status")
    }

    fun folderStatus(path: JsonElement?): JsonObject = JsonObject().apply { addProperty(FOLDER, folderPath(path, "folder")) }

    fun createFolder(path: JsonElement?): JsonObject = JsonObject().apply { addProperty("path", folderPath(path, "path")) }

    fun deleteFolder(path: JsonElement?): JsonObject = JsonObject().apply { addProperty("path", folderPath(path, "path")) }

    fun renameFolder(path: JsonElement?, newPath: JsonElement?): JsonObject = JsonObject().apply {
        addProperty("path", folderPath(path, "path"))
        addProperty("newPath", folderPath(newPath, "newPath"))
    }

    /** A non-blank folder path. zh-CN: 非空白的文件夹路径. */
    fun folderPath(element: JsonElement?, label: String): String =
        optionalFolder(element, label) ?: throw invalid("'$label' is required: a folder path such as 'INBOX'")

    private fun optionalFolder(element: JsonElement?, label: String): String? {
        if (element == null || element.isJsonNull) return null
        val text = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: throw invalid("'$label' must be a folder path string")
        return text.trim().takeIf { it.isNotEmpty() } ?: throw invalid("'$label' must not be blank")
    }

    // ------------------------------------------------------------------ outgoing

    /**
     * `send(message, options?)` -> `mail.send`. [resolvePath] maps attachment paths of the script
     * onto absolute paths, [sizeOf] returns the size of a resolved file or null when it cannot be read.
     * zh-CN: `send(message, options?)` -> `mail.send`. [resolvePath] 把脚本的附件路径映射为绝对路径, [sizeOf] 返回已解析文件的大小, 不可读时为 null.
     */
    fun send(message: JsonElement?, options: JsonElement?, resolvePath: (String) -> String, sizeOf: (String) -> Long?): Outgoing {
        val opts = optionalObject(options, "options")?.also { it.rejectUnknown("options", SEND_KEYS) }
        val (normalized, paths) = message(message, resolvePath, sizeOf)
        val args = JsonObject().apply {
            add("message", normalized)
            opts?.booleanOrNull("options.saveToSent", "saveToSent")?.let { addProperty("saveToSent", it) }
        }
        return Outgoing(args, paths)
    }

    /** `append(folder, message, flags?)` -> `messages.append`. */
    fun append(folder: JsonElement?, message: JsonElement?, flags: JsonElement?, resolvePath: (String) -> String, sizeOf: (String) -> Long?): Outgoing {
        val path = folderPath(folder, "folder")
        val (normalized, paths) = message(message, resolvePath, sizeOf)
        val names = stringList(flags, "flags")
        val args = JsonObject().apply {
            addProperty(FOLDER, path)
            add("message", normalized)
            if (names.isNotEmpty()) add("flags", jsonArray(names))
        }
        return Outgoing(args, paths)
    }

    private fun message(element: JsonElement?, resolvePath: (String) -> String, sizeOf: (String) -> Long?): Pair<JsonObject, List<String>> {
        val source = element?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw invalid("'message' must be an object with at least 'to' and 'subject'")
        source.rejectUnknown("message", MESSAGE_KEYS)
        val result = JsonObject()
        source.entrySet().forEach { (key, value) ->
            if (value.isJsonNull || key == "attachments" || key == "date") return@forEach
            result.add(key, value)
        }
        source.get("date")?.takeUnless { it.isJsonNull }?.let { result.addProperty("date", dateMillis(it, "message.date")) }
        val paths = ArrayList<String>()
        val attachments = JsonArray()
        source.get("attachments")?.takeUnless { it.isJsonNull }?.let { element ->
            val items = if (element.isJsonArray) element.asJsonArray.toList() else listOf(element)
            items.forEachIndexed { index, item ->
                val label = "message.attachments[$index]"
                val entry = JsonObject()
                val path = when {
                    item.isJsonPrimitive && item.asJsonPrimitive.isString -> item.asString
                    item.isJsonObject -> {
                        val obj = item.asJsonObject
                        obj.rejectUnknown(label, OUTGOING_ATTACHMENT_KEYS)
                        obj.stringOrNull("$label.fileName", FILE_NAME)?.let { entry.addProperty(FILE_NAME, it) }
                        obj.stringOrNull("$label.mimeType", "mimeType")?.let { entry.addProperty("mimeType", it) }
                        obj.stringOrNull("$label.contentId", "contentId")?.let { entry.addProperty("contentId", it) }
                        obj.booleanOrNull("$label.inline", "inline")?.let { entry.addProperty("inline", it) }
                        obj.stringOrNull("$label.path", "path") ?: throw invalid("'$label.path' is required")
                    }
                    else -> throw invalid("'$label' must be a file path or {path, fileName?, mimeType?, contentId?, inline?}")
                }
                if (path.isBlank()) throw invalid("'$label' path must not be blank")
                val resolved = resolvePath(path)
                paths += resolved
                entry.addProperty("descriptorIndex", index)
                if (!entry.has(FILE_NAME)) entry.addProperty(FILE_NAME, File(resolved).name)
                sizeOf(resolved)?.let { entry.addProperty(SIZE, it) }
                attachments.add(entry)
            }
        }
        if (attachments.size() > 0) result.add("attachments", attachments)
        return result to paths
    }

    private fun dateMillis(element: JsonElement, label: String): Long {
        val primitive = element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        when {
            primitive == null -> Unit
            primitive.isNumber -> {
                val value = primitive.asDouble
                if (value >= 0 && value == floor(value)) return value.toLong()
            }
            primitive.isString -> try {
                return Instant.parse(primitive.asString).toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        throw invalid("'$label' must be a Date, UTC milliseconds or an ISO-8601 string")
    }

    // ------------------------------------------------------------------ uids

    /** One UID: a positive integer, a non-blank string or an object carrying `uid`. zh-CN: 一个 UID: 正整数, 非空白字符串或携带 `uid` 的对象. */
    fun uid(element: JsonElement?, label: String): JsonPrimitive = uidAndFolder(element, label).first

    /** The UID plus the `folder` of a message or attachment object, or null for a bare UID. zh-CN: UID 加邮件 / 附件对象的 `folder`, 裸 UID 时为 null. */
    fun uidAndFolder(element: JsonElement?, label: String): Pair<JsonPrimitive, String?> {
        if (element == null || element.isJsonNull) throw invalid("'$label' is required: a UID or a message object")
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val inner = obj.get(UID)?.takeUnless { it.isJsonNull } ?: throw invalid("'$label' is an object without 'uid'")
            if (!inner.isJsonPrimitive) throw invalid("'$label.uid' must be a number or a string")
            return primitiveUid(inner.asJsonPrimitive, "$label.uid") to obj.get(FOLDER)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        }
        if (element.isJsonPrimitive) return primitiveUid(element.asJsonPrimitive, label) to null
        throw invalid("'$label' must be a UID or a message object")
    }

    /** A UID or an array of UIDs / message objects; the folder of the first object rides along. zh-CN: 一个 UID 或 UID / 邮件对象数组; 第一个对象的文件夹随行. */
    fun uids(element: JsonElement?, label: String): Pair<JsonArray, String?> {
        if (element == null || element.isJsonNull) throw invalid("'$label' is required: a UID, a message or an array of them")
        val items = if (element.isJsonArray) element.asJsonArray.toList() else listOf(element)
        if (items.isEmpty()) throw invalid("'$label' must hold at least one UID")
        var folder: String? = null
        val array = JsonArray()
        items.forEachIndexed { index, item ->
            val (uid, itemFolder) = uidAndFolder(item, if (element.isJsonArray) "$label[$index]" else label)
            array.add(uid)
            if (folder == null) folder = itemFolder
        }
        return array to folder
    }

    private fun primitiveUid(primitive: JsonPrimitive, label: String): JsonPrimitive = when {
        primitive.isNumber -> {
            val value = primitive.asDouble
            if (value < 1 || value != floor(value) || value > MAX_UID) throw invalid("'$label' must be a positive integer UID, got $value")
            JsonPrimitive(value.toLong())
        }
        primitive.isString -> {
            val text = primitive.asString.trim()
            if (text.isEmpty()) throw invalid("'$label' must not be blank")
            JsonPrimitive(text)
        }
        else -> throw invalid("'$label' must be a number or a string")
    }

    private const val MAX_UID = 9007199254740991.0

    // ------------------------------------------------------------------ helpers

    private fun invalid(message: String): MailPluginException = MailScriptOptions.invalid(message)

    private fun jsonArray(values: List<String>): JsonArray = JsonArray().apply { values.forEach { add(it) } }

    private fun optionalObject(element: JsonElement?, label: String): JsonObject? {
        if (element == null || element.isJsonNull) return null
        if (!element.isJsonObject) throw invalid("'$label' must be an object")
        return element.asJsonObject
    }

    private fun copyKnown(source: JsonObject, target: JsonObject, label: String, known: Set<String>) {
        source.rejectUnknown(label, known)
        source.entrySet().forEach { (key, value) -> if (!value.isJsonNull) target.add(key, value) }
    }

    private fun normalizeUid(target: JsonObject, key: String, label: String) {
        val element = target.get(key) ?: return
        target.add(key, uid(element, label))
    }

    private fun optionalInt(target: JsonObject, key: String, label: String) {
        val element = target.get(key) ?: return
        val value = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
            ?: throw invalid("'$label' must be a number")
        if (value != floor(value) || value < 1 || value > Int.MAX_VALUE) throw invalid("'$label' must be a positive integer, got $value")
        target.addProperty(key, value.toInt())
    }

    private fun optionalBoolean(target: JsonObject, key: String, label: String) {
        val element = target.get(key) ?: return
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) throw invalid("'$label' must be a boolean")
    }

    private fun optionalOneOf(target: JsonObject, key: String, label: String, allowed: Set<String>) {
        val element = target.get(key) ?: return
        val value = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        if (value == null || value !in allowed) throw invalid("'$label' must be one of ${allowed.joinToString(", ")}")
    }

    private fun stringList(element: JsonElement?, label: String): List<String> {
        if (element == null || element.isJsonNull) return emptyList()
        val items = if (element.isJsonArray) element.asJsonArray.toList() else listOf(element)
        return items.mapIndexed { index, item ->
            val text = item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()
            if (text.isNullOrEmpty()) throw invalid("'$label${if (element.isJsonArray) "[$index]" else ""}' must be a non-blank string")
            text
        }
    }

    private fun JsonObject.rejectUnknown(label: String, known: Set<String>) {
        keySet().firstOrNull { it !in known }?.let { key -> throw invalid("unknown option '$label.$key' (known: ${known.joinToString(", ")})") }
    }

    private fun JsonObject.stringOrNull(label: String, key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) throw invalid("'$label' must be a string")
        return element.asString
    }

    private fun JsonObject.booleanOrNull(label: String, key: String): Boolean? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) throw invalid("'$label' must be a boolean")
        return element.asBoolean
    }

    private fun JsonObject.longOrNull(label: String, key: String): Long? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        val value = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
            ?: throw invalid("'$label' must be a number")
        return value.toLong()
    }
}
