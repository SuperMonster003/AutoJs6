package org.autojs.autojs.runtime.api.modules.epub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.plugin.epub.EpubError
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.epub.EpubScriptArguments
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.mozilla.javascript.Callable
import java.util.concurrent.atomic.AtomicReference

/**
 * The script-visible `EpubReaderSession` (plugin roadmap P6.2, appendix A.4): an `EventEmitter`
 * with the events `open({ locator, title, href, positions })`, `progress({ locator,
 * totalProgression, chapterTitle, href })`, `bookmark({ action, locator, createdAt, title, text
 * })`, `highlight({ action, id, style, color, note, quote, title, locator, createdAt, updatedAt
 * })` (plugin roadmap P9.4, contract version 2; `action` is `added`, `updated` or `removed`),
 * `error(EpubError)` and `close({ reason })`, the properties `path`, `locator`, `progress`,
 * `title`, `chapterTitle`, `href`, `positions`, `state`, `reason`, `isOpen` / `isClosed`, and the
 * methods `goTo(target)`, `next()`, `prev()`, `nextChapter()`, `prevChapter()`,
 * `setPreferences(partial)`, `bookmarks()` and `close(options?)`. Every event reaches the
 * listeners on the script thread that opened the session through an [EpubAsyncDispatcher], which
 * also keeps the script alive until the session closed; `close` is the single terminal event.
 * State changes come from the unexposed [Controller] (like `MailWatch`), and the reader calls go
 * through the unexposed [Port] bound by the runner, so scripts only see the event and method
 * surface.
 * zh-CN: 脚本可见的 `EpubReaderSession` (插件路线图 P6.2, 附录 A.4): 带事件 `open({ locator, title, href, positions })`, `progress({ locator,
 * totalProgression, chapterTitle, href })`, `bookmark({ action, locator, createdAt, title, text })`, `highlight({ action, id, style, color, note,
 * quote, title, locator, createdAt, updatedAt })` (插件路线图 P9.4, 契约版本 2; `action` 为 `added`, `updated` 或 `removed`), `error(EpubError)` 与
 * `close({ reason })` 的 `EventEmitter`, 属性 `path`, `locator`, `progress`, `title`, `chapterTitle`, `href`, `positions`, `state`, `reason`, `isOpen` / `isClosed`, 方法
 * `goTo(target)`, `next()`, `prev()`, `nextChapter()`, `prevChapter()`, `setPreferences(partial)`, `bookmarks()` 与 `close(options?)`. 每个事件经
 * [EpubAsyncDispatcher] 回到打开会话的脚本线程, 分发器同时让脚本活到会话关闭; `close` 是唯一的终结事件. 状态变更来自不对脚本暴露的 [Controller]
 * (同 `MailWatch`), 阅读器调用经运行器绑定的不暴露的 [Port], 脚本只看到事件与方法表面.
 */
class EpubReaderSession internal constructor(
    private val dispatcher: EpubAsyncDispatcher,
    bridges: ScriptBridges,
    val path: String,
    private val scriptRuntime: ScriptRuntime?,
    private val convertValue: (Any?) -> Any?,
    private val onTerminal: (() -> Unit)?,
) : EventEmitter(bridges) {

    /** The reader calls behind the session methods; bound by the runner once the plugin session exists. zh-CN: 会话方法背后的阅读器调用; 插件会话存在后由运行器绑定. */
    internal interface Port {
        fun goTo(target: EpubScriptArguments.GoTo)
        fun navigate(direction: Int)
        fun setPreferences(preferencesJson: String)
        fun bookmarks(): List<Any?>
        fun close(finish: Boolean)
    }

    private val port = AtomicReference<Port?>()
    private val terminalLock = Any()

    @Volatile
    private var currentLocator: Any? = null

    @Volatile
    private var currentProgression: Double? = null

    @Volatile
    private var bookTitle: String? = null

    @Volatile
    private var currentChapter: String? = null

    @Volatile
    private var currentHref: String? = null

    @Volatile
    private var currentPositions: Int? = null

    @Volatile
    private var opened = false

    @Volatile
    private var closeReason: String? = null

    /** The last locator the reader reported (`open` / `progress`), or null. zh-CN: 阅读器最近报告的 locator (`open` / `progress`), 或 null. */
    val locator: Any?
        get() = convertValue(currentLocator)

    /**
     * The last total progression (0 to 1) the reader reported, or [UNKNOWN_PROGRESS] (-1) before the
     * first report. A primitive on purpose: Rhino hands boxed numbers to scripts as Java objects under
     * the default `javaPrimitiveWrap`, so a nullable Double would fail `typeof` and `===`.
     * zh-CN: 阅读器最近报告的总进度 (0 到 1), 首次报告前为 [UNKNOWN_PROGRESS] (-1). 刻意使用原始类型:
     * 默认的 `javaPrimitiveWrap` 下 Rhino 会把装箱的数字作为 Java 对象交给脚本, 可空 Double 会让 `typeof` 与 `===` 失效.
     */
    val progress: Double
        get() = currentProgression ?: UNKNOWN_PROGRESS

    /** The book title from the `open` event. zh-CN: 来自 `open` 事件的书名. */
    val title: String?
        get() = bookTitle

    /** The chapter title of the last `progress` event, when the reader knew one. zh-CN: 最近 `progress` 事件的章节标题 (阅读器知道时). */
    val chapterTitle: String?
        get() = currentChapter

    val href: String?
        get() = currentHref

    /** The position count from the `open` event, 0 before it. zh-CN: 来自 `open` 事件的位置数, 之前为 0. */
    val positions: Int
        get() = currentPositions ?: 0

    @get:JvmName("getIsOpen")
    val isOpen: Boolean
        get() = opened && !dispatcher.isTerminal

    @get:JvmName("getIsClosed")
    val isClosed: Boolean
        get() = dispatcher.isTerminal

    /** `pending` until the `open` event, then `open`, then `closed`. zh-CN: `open` 事件前为 `pending`, 之后 `open`, 关闭后 `closed`. */
    val state: String
        get() = when {
            dispatcher.isTerminal -> STATE_CLOSED
            opened -> STATE_OPEN
            else -> STATE_PENDING
        }

    /** The reason of the `close` event once closed. zh-CN: 关闭后为 `close` 事件的原因. */
    val reason: String?
        get() = closeReason

    override fun on(eventName: String, listener: Callable): EpubReaderSession {
        super.on(eventName, listener)
        return this
    }

    override fun once(eventName: String, listener: Callable): EpubReaderSession {
        super.once(eventName, listener)
        return this
    }

    /** `goTo(href | progression | locator)`: the reader answers with a `progress` event. zh-CN: `goTo(href | progression | locator)`: 阅读器以 `progress` 事件应答. */
    @JvmOverloads
    fun goTo(target: Any? = null): EpubReaderSession = guarded {
        val request = EpubScriptArguments.goTo(EpubCalls.json(target))
        call { it.goTo(request) }
        this
    }

    fun next(): EpubReaderSession = navigate(EpubContract.DIRECTION_NEXT_PAGE)

    fun prev(): EpubReaderSession = navigate(EpubContract.DIRECTION_PREVIOUS_PAGE)

    fun nextChapter(): EpubReaderSession = navigate(EpubContract.DIRECTION_NEXT_CHAPTER)

    fun prevChapter(): EpubReaderSession = navigate(EpubContract.DIRECTION_PREVIOUS_CHAPTER)

    /** Applies a partial preferences object (appendix A.6); unknown keys arrive as an `error` event. zh-CN: 应用部分偏好对象 (附录 A.6); 未知键以 `error` 事件到达. */
    fun setPreferences(partial: Any?): EpubReaderSession = guarded {
        val json = EpubScriptArguments.preferences(EpubCalls.json(partial))
        call { it.setPreferences(json) }
        this
    }

    /** The reader's bookmark list of this book. zh-CN: 阅读器里这本书的书签列表. */
    fun bookmarks(): Any? = guarded { call { it.bookmarks() } }

    /**
     * Ends the session; the reader Activity stays open for the user unless `{ finish: true }`
     * (plugin decision D32). The `close` event follows with reason `host`. Idempotent.
     * zh-CN: 结束会话; 除非 `{ finish: true }`, 阅读器 Activity 留给用户 (插件决策 D32). 随后是原因为 `host` 的 `close` 事件. 幂等.
     */
    @JvmOverloads
    fun close(options: Any? = null): EpubReaderSession = guarded {
        val request = EpubScriptArguments.close(EpubCalls.json(options))
        val bound = port.get() ?: return@guarded this
        run(bound) { it.close(request.finish) }
        this
    }

    override fun toString(): String = buildString {
        append("EpubReaderSession { path: ").append(path).append(", state: ").append(state)
        currentHref?.let { append(", href: ").append(it) }
        currentProgression?.let { append(", progress: ").append(it) }
        closeReason?.let { append(", reason: ").append(it) }
        append(" }")
    }

    // ------------------------------------------------------------------ calls

    private fun navigate(direction: Int): EpubReaderSession = guarded {
        call { it.navigate(direction) }
        this
    }

    private fun <T> call(block: (Port) -> T): Any? {
        val bound = port.get() ?: throw EpubJsErrors.failure(EpubErrorCodes.SESSION_CLOSED, "the reader session is closed")
        return run(bound, block)
    }

    /** Runs [block] against the plugin on the IO dispatcher while the script thread waits inside the runtime's coroutine context. zh-CN: 在 IO 分发器上对插件运行 [block], 脚本线程在运行时协程上下文里等待. */
    private fun <T> run(bound: Port, block: (Port) -> T): Any? {
        val runtime = scriptRuntime ?: return block(bound)
        return EpubCalls.sync(runtime) { _ ->
            withContext(Dispatchers.IO) { withTimeout(EpubContract.CALL_TIMEOUT_MS) { block(bound) } }
        }
    }

    /** Argument and reader failures leave as `EpubError`; script values pass unchanged. zh-CN: 参数与阅读器失败以 `EpubError` 离开; 脚本值原样通过. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        val runtime = scriptRuntime ?: throw t
        EpubJsErrors.throwInto(runtime, t)
    }

    // ------------------------------------------------------------------ transitions (Controller only)

    private fun bindPort(bound: Port): Boolean = synchronized(terminalLock) {
        if (dispatcher.isTerminal) false else {
            port.set(bound)
            true
        }
    }

    private fun open(locator: Any?, title: String?, href: String?, positions: Int?): Boolean {
        currentLocator = locator
        currentProgression = totalProgressionOf(locator) ?: currentProgression
        bookTitle = title
        currentHref = href
        currentPositions = positions
        opened = true
        val payload = LinkedHashMap<String, Any?>()
        payload[FIELD_LOCATOR] = locator
        payload[FIELD_TITLE] = title
        payload[FIELD_HREF] = href
        payload[FIELD_POSITIONS] = positions
        return dispatcher.dispatchValues(arrayOf(payload)) { values -> emit(EVENT_OPEN, values[0]) }
    }

    private fun progress(locator: Any?, progression: Double?, chapterTitle: String?, href: String?): Boolean {
        currentLocator = locator
        currentProgression = progression ?: totalProgressionOf(locator) ?: currentProgression
        currentChapter = chapterTitle
        currentHref = href
        val payload = LinkedHashMap<String, Any?>()
        payload[FIELD_LOCATOR] = locator
        payload[FIELD_TOTAL_PROGRESSION] = currentProgression
        payload[FIELD_CHAPTER_TITLE] = chapterTitle
        payload[FIELD_HREF] = href
        return dispatcher.dispatchValues(arrayOf(payload)) { values -> emit(EVENT_PROGRESS, values[0]) }
    }

    private fun bookmark(action: String, bookmark: Any?): Boolean {
        val source = bookmark as? Map<*, *>
        val payload = LinkedHashMap<String, Any?>()
        payload[FIELD_ACTION] = action
        payload[FIELD_LOCATOR] = source?.get(EpubContract.FIELD_LOCATOR)
        payload[FIELD_CREATED_AT] = source?.get(EpubContract.FIELD_CREATED_AT)
        payload[FIELD_TITLE] = source?.get(EpubContract.FIELD_TITLE)
        payload[FIELD_TEXT] = source?.get(EpubContract.FIELD_TEXT)
        return dispatcher.dispatchValues(arrayOf(payload)) { values -> emit(EVENT_BOOKMARK, values[0]) }
    }

    /** Contract version 2: one highlight or note of the book changed in the reader. zh-CN: 契约版本 2: 阅读器里这本书的一条高亮或笔记发生变化. */
    private fun highlight(action: String, annotation: Any?): Boolean {
        val source = annotation as? Map<*, *>
        val payload = LinkedHashMap<String, Any?>()
        payload[FIELD_ACTION] = action
        payload[FIELD_ID] = source?.get(EpubContract.FIELD_ID)
        payload[FIELD_STYLE] = source?.get(EpubContract.FIELD_STYLE)
        payload[FIELD_COLOR] = source?.get(EpubContract.FIELD_COLOR)
        payload[FIELD_NOTE] = source?.get(EpubContract.FIELD_NOTE)
        payload[FIELD_QUOTE] = source?.get(EpubContract.FIELD_QUOTE)
        payload[FIELD_TITLE] = source?.get(EpubContract.FIELD_TITLE)
        payload[FIELD_LOCATOR] = source?.get(EpubContract.FIELD_LOCATOR)
        payload[FIELD_CREATED_AT] = source?.get(EpubContract.FIELD_CREATED_AT)
        payload[FIELD_UPDATED_AT] = source?.get(EpubContract.FIELD_UPDATED_AT)
        return dispatcher.dispatchValues(arrayOf(payload)) { values -> emit(EVENT_HIGHLIGHT, values[0]) }
    }

    private fun error(error: EpubError, convert: (EpubError) -> Any?): Boolean =
        dispatcher.dispatchValues(emptyArray()) { emit(EVENT_ERROR, convert(error)) }

    /** The terminal `close`; queued events go first. zh-CN: 终结的 `close`; 排队中的事件先投递. */
    private fun close(reason: String): Boolean = terminate(reason) {
        dispatcher.terminalValues(arrayOf(closePayload(reason))) { values ->
            try {
                emit(EVENT_CLOSE, values[0])
            } finally {
                notifyTerminal()
            }
        }
    }

    /** `error` then `close` in one terminal action that discards the queued events (the script stopped draining). zh-CN: 一个丢弃排队事件的终结动作里先 `error` 后 `close` (脚本不再消费事件). */
    private fun fail(error: EpubError, convert: (EpubError) -> Any?, reason: String): Boolean = terminate(reason) {
        dispatcher.replaceQueuedWithTerminalValues(arrayOf(closePayload(reason))) { values ->
            try {
                var failure: Throwable? = null
                try {
                    emit(EVENT_ERROR, convert(error))
                } catch (t: Throwable) {
                    failure = t
                }
                try {
                    emit(EVENT_CLOSE, values[0])
                } catch (t: Throwable) {
                    failure?.addSuppressed(t) ?: run { failure = t }
                }
                failure?.let { throw it }
            } finally {
                notifyTerminal()
            }
        }
    }

    /** Script exit: no event after teardown began. zh-CN: 脚本退出: 拆除开始后不再有事件. */
    private fun cancelSilently(): Boolean {
        synchronized(terminalLock) {
            if (!dispatcher.cancelSilently(::notifyTerminal)) return false
            closeReason = closeReason ?: REASON_EXIT
            port.set(null)
            return true
        }
    }

    private fun terminate(reason: String, enqueue: () -> Boolean): Boolean {
        synchronized(terminalLock) {
            if (dispatcher.isTerminal) return false
            closeReason = reason
            port.set(null)
            val accepted = try {
                enqueue()
            } catch (t: Throwable) {
                notifyTerminal()
                throw t
            }
            return accepted
        }
    }

    private fun closePayload(reason: String): Map<String, Any?> = LinkedHashMap<String, Any?>().apply { put(FIELD_REASON, reason) }

    private fun notifyTerminal() {
        runCatching { onTerminal?.invoke() }
    }

    /**
     * Transport-side capability kept off the Java object exposed to Rhino: Kotlin `internal`
     * members are JVM-public and reachable through reflection, so every transition lives here.
     * zh-CN: 不暴露给 Rhino 的传输侧能力: Kotlin `internal` 成员在 JVM 上是 public 且可经反射到达, 因此每个状态变更都在这里.
     */
    internal class Controller internal constructor(val session: EpubReaderSession) {

        constructor(
            dispatcher: EpubAsyncDispatcher,
            bridges: ScriptBridges,
            path: String,
            scriptRuntime: ScriptRuntime? = null,
            convertValue: (Any?) -> Any? = { it },
            onTerminal: (() -> Unit)? = null,
        ) : this(EpubReaderSession(dispatcher, bridges, path, scriptRuntime, convertValue, onTerminal))

        val isClosed: Boolean
            get() = session.isClosed

        /** Binds the reader calls; false when the session closed already. zh-CN: 绑定阅读器调用; 会话已关闭时为 false. */
        fun bindPort(port: Port): Boolean = session.bindPort(port)

        fun open(locator: Any?, title: String?, href: String?, positions: Int?): Boolean = session.open(locator, title, href, positions)

        fun progress(locator: Any?, progression: Double?, chapterTitle: String?, href: String?): Boolean = session.progress(locator, progression, chapterTitle, href)

        fun bookmark(action: String, bookmark: Any?): Boolean = session.bookmark(action, bookmark)

        fun highlight(action: String, annotation: Any?): Boolean = session.highlight(action, annotation)

        fun error(error: EpubError, convert: (EpubError) -> Any? = { it }): Boolean = session.error(error, convert)

        fun close(reason: String): Boolean = session.close(reason)

        fun fail(error: EpubError, reason: String, convert: (EpubError) -> Any? = { it }): Boolean = session.fail(error, convert, reason)

        fun cancelSilently(): Boolean = session.cancelSilently()
    }

    companion object {
        const val EVENT_OPEN = EpubContract.EVENT_OPEN
        const val EVENT_PROGRESS = EpubContract.EVENT_PROGRESS
        const val EVENT_BOOKMARK = EpubContract.EVENT_BOOKMARK
        const val EVENT_HIGHLIGHT = EpubContract.EVENT_HIGHLIGHT
        const val EVENT_ERROR = EpubContract.EVENT_ERROR
        const val EVENT_CLOSE = EpubContract.EVENT_CLOSE

        const val STATE_PENDING = "pending"
        const val STATE_OPEN = "open"
        const val STATE_CLOSED = "closed"

        /** `close` reasons raised on the host side; the plugin's own (`user`, `host`, `replaced`, `timeout`, `error`) pass through. zh-CN: 宿主侧产生的 `close` 原因; 插件自己的 (`user`, `host`, `replaced`, `timeout`, `error`) 原样透传. */
        const val REASON_EXIT = "script-exit"
        const val REASON_OVERFLOW = "overflow"

        const val FIELD_LOCATOR = "locator"
        const val FIELD_TITLE = "title"
        const val FIELD_HREF = "href"
        const val FIELD_POSITIONS = "positions"
        const val UNKNOWN_PROGRESS = -1.0
        const val FIELD_TOTAL_PROGRESSION = "totalProgression"
        const val FIELD_CHAPTER_TITLE = "chapterTitle"
        const val FIELD_ACTION = "action"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_TEXT = "text"
        const val FIELD_REASON = "reason"

        // `highlight` payload (contract version 2). zh-CN: `highlight` 载荷 (契约版本 2).
        const val FIELD_ID = "id"
        const val FIELD_STYLE = "style"
        const val FIELD_COLOR = "color"
        const val FIELD_NOTE = "note"
        const val FIELD_QUOTE = "quote"
        const val FIELD_UPDATED_AT = "updatedAt"

        /** `locations.totalProgression` of a plain locator, when present. zh-CN: 普通 locator 的 `locations.totalProgression` (存在时). */
        fun totalProgressionOf(locator: Any?): Double? {
            val locations = (locator as? Map<*, *>)?.get(EpubContract.FIELD_LOCATIONS) as? Map<*, *> ?: return null
            return (locations[EpubContract.FIELD_TOTAL_PROGRESSION] as? Number)?.toDouble()
        }
    }
}
