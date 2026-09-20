package org.autojs.autojs.core.plugin.epub

import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.autojs.plugin.epub.api.IEpubBook
import org.autojs.plugin.epub.api.IEpubReaderCallback
import org.autojs.plugin.epub.api.IEpubReaderSession
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Bundle shapes of the EPUB contract as written and read by the host (plugin roadmap P5.4). */
object EpubBundles {

    /** The Bundle every request carries; [build] adds the request keys. */
    fun request(build: Bundle.() -> Unit = {}): Bundle = Bundle().apply {
        putInt(EpubContract.KEY_CONTRACT_VERSION, EpubContract.CONTRACT_VERSION)
        build()
    }

    fun openOptions(displayName: String?): Bundle = request {
        displayName?.takeIf { it.isNotBlank() }?.let { putString(EpubContract.KEY_DISPLAY_NAME, it) }
    }

    fun textRequest(href: String?, index: Int?, offset: Int, maxChars: Int, format: String?): Bundle = request {
        href?.let { putString(EpubContract.KEY_HREF, it) }
        index?.let { putInt(EpubContract.KEY_INDEX, it) }
        putInt(EpubContract.KEY_OFFSET, offset)
        putInt(EpubContract.KEY_MAX_CHARS, maxChars)
        format?.let { putString(EpubContract.KEY_FORMAT, it) }
    }

    fun searchRequest(query: String, offset: Int, limit: Int): Bundle = request {
        putString(EpubContract.KEY_QUERY, query)
        putInt(EpubContract.KEY_OFFSET, offset)
        putInt(EpubContract.KEY_LIMIT, limit)
    }

    fun readerOptions(displayName: String?, locatorJson: String?, href: String?, progression: Double?, preferencesJson: String?): Bundle = request {
        displayName?.takeIf { it.isNotBlank() }?.let { putString(EpubContract.KEY_DISPLAY_NAME, it) }
        locatorJson?.let { putString(EpubContract.KEY_LOCATOR, it) }
        href?.let { putString(EpubContract.KEY_HREF, it) }
        progression?.let { putDouble(EpubContract.KEY_PROGRESSION, it) }
        preferencesJson?.let { putString(EpubContract.KEY_PREFERENCES, it) }
    }

    fun goToTarget(locatorJson: String?, href: String?, progression: Double?): Bundle = request {
        locatorJson?.let { putString(EpubContract.KEY_LOCATOR, it) }
        href?.let { putString(EpubContract.KEY_HREF, it) }
        progression?.let { putDouble(EpubContract.KEY_PROGRESSION, it) }
    }

    fun preferences(preferencesJson: String): Bundle = request { putString(EpubContract.KEY_PREFERENCES, preferencesJson) }

    fun closeOptions(finish: Boolean): Bundle = request { putBoolean(EpubContract.KEY_FINISH, finish) }

    /**
     * A Bundle answer of the contract: refuses a missing answer or an unsupported contract
     * version, throws the carried error, otherwise returns the Bundle for its result keys.
     */
    fun answer(bundle: Bundle?, label: String): Bundle {
        if (bundle == null) throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.INTERNAL, "$label returned no answer"))
        val version = bundle.getInt(EpubContract.KEY_CONTRACT_VERSION, 0)
        if (!EpubContract.supportsContractVersion(version)) {
            throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.PLUGIN_INCOMPATIBLE, "$label answered with contract version $version"))
        }
        EpubErrorMapper.fromBundle(bundle)?.let { throw EpubPluginException(it) }
        return bundle
    }

    /** A required JSON document of an answer. */
    fun document(bundle: Bundle, key: String, label: String): String =
        bundle.getString(key) ?: throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.INTERNAL, "$label answered without $key"))

    fun decodeEvent(bundle: Bundle?): EpubReaderEvent? {
        val type = bundle?.getString(EpubContract.KEY_EVENT) ?: return null
        return EpubReaderEvent(
            type = type,
            reason = bundle.getString(EpubContract.KEY_REASON),
            change = bundle.getString(EpubContract.KEY_CHANGE),
            locatorJson = bundle.getString(EpubContract.KEY_LOCATOR),
            progression = if (bundle.containsKey(EpubContract.KEY_PROGRESSION)) bundle.getDouble(EpubContract.KEY_PROGRESSION) else null,
            title = bundle.getString(EpubContract.KEY_TITLE),
            href = bundle.getString(EpubContract.KEY_HREF),
            visible = if (bundle.containsKey(EpubContract.KEY_VISIBLE)) bundle.getBoolean(EpubContract.KEY_VISIBLE) else null,
            positions = if (bundle.containsKey(EpubContract.KEY_POSITIONS)) bundle.getInt(EpubContract.KEY_POSITIONS) else null,
            bookmarksJson = bundle.getString(EpubContract.KEY_BOOKMARKS),
            errorCode = bundle.getString(EpubContract.KEY_ERROR_CODE),
            errorMessage = bundle.getString(EpubContract.KEY_ERROR_MESSAGE),
        )
    }
}

/** Forwards `onEvent` of a reader session into the generation and sequence checks of [EpubReaderBridge]. */
internal class EpubReaderCallbackBinder(private val bridge: EpubReaderBridge) : IEpubReaderCallback.Stub() {

    override fun onEvent(generation: Long, seq: Long, event: Bundle?) {
        bridge.accept(generation, seq, EpubBundles.decodeEvent(event))
    }
}

/** One text window of `IEpubBook.getText`. */
data class EpubTextChunk(val href: String, val index: Int, val format: String, val offset: Int, val text: String, val hasMore: Boolean)

/** One page of `IEpubBook.search`: the hit documents as a JSON array and whether a later page exists. */
data class EpubSearchPage(val query: String, val offset: Int, val resultsJson: String, val hasMore: Boolean)

/** A JSON array answer with its truncation flag (`toc`, `readingOrder`). */
data class EpubJsonArray(val json: String, val hasMore: Boolean)

/**
 * `IEpubBook` held on a dedicated binding lease (plugin roadmap P5.4): every call maps its
 * failures through [EpubErrorMapper]; a death of the plugin process releases the lease and later
 * calls answer `SESSION_CLOSED`; [close] closes the remote book and releases the lease exactly
 * once.
 */
class EpubBookClient internal constructor(
    private val leased: AidlPluginHost.LeasedCall<IEpubBook>,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val died = AtomicBoolean(false)
    private val deathRecipient = IBinder.DeathRecipient { onDied() }

    private val book: IEpubBook get() = leased.value

    val isClosed: Boolean get() = closed.get()

    init {
        try {
            book.asBinder().linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            onDied()
        }
    }

    fun metadata(): String = call("getMetadata") { EpubBundles.document(EpubBundles.answer(book.metadata, "getMetadata"), EpubContract.KEY_METADATA, "getMetadata") }

    fun toc(): EpubJsonArray = call("getToc") {
        val answer = EpubBundles.answer(book.toc, "getToc")
        EpubJsonArray(EpubBundles.document(answer, EpubContract.KEY_TOC, "getToc"), answer.getBoolean(EpubContract.KEY_HAS_MORE))
    }

    fun readingOrder(): EpubJsonArray = call("getReadingOrder") {
        val answer = EpubBundles.answer(book.readingOrder, "getReadingOrder")
        EpubJsonArray(EpubBundles.document(answer, EpubContract.KEY_READING_ORDER, "getReadingOrder"), answer.getBoolean(EpubContract.KEY_HAS_MORE))
    }

    fun text(href: String? = null, index: Int? = null, offset: Int = 0, maxChars: Int = 0, format: String? = null): EpubTextChunk = call("getText") {
        val answer = EpubBundles.answer(book.getText(EpubBundles.textRequest(href, index, offset, maxChars, format)), "getText")
        EpubTextChunk(
            href = answer.getString(EpubContract.KEY_HREF).orEmpty(),
            index = answer.getInt(EpubContract.KEY_INDEX),
            format = answer.getString(EpubContract.KEY_FORMAT) ?: EpubContract.FORMAT_TEXT,
            offset = answer.getInt(EpubContract.KEY_OFFSET),
            text = answer.getString(EpubContract.KEY_TEXT).orEmpty(),
            hasMore = answer.getBoolean(EpubContract.KEY_HAS_MORE),
        )
    }

    /** The pipe read end of one resource; the caller owns it ([EpubOutputSink.save] drains and closes it). */
    fun resource(href: String): ParcelFileDescriptor = call("openResource", EpubErrorMapper.Phase.TRANSFER) {
        book.openResource(href) ?: throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.INTERNAL, "openResource returned no descriptor"))
    }

    fun search(query: String, offset: Int = 0, limit: Int = 0): EpubSearchPage = call("search") {
        val answer = EpubBundles.answer(book.search(EpubBundles.searchRequest(query, offset, limit)), "search")
        EpubSearchPage(
            query = answer.getString(EpubContract.KEY_QUERY) ?: query,
            offset = answer.getInt(EpubContract.KEY_OFFSET),
            resultsJson = EpubBundles.document(answer, EpubContract.KEY_RESULTS, "search"),
            hasMore = answer.getBoolean(EpubContract.KEY_HAS_MORE),
        )
    }

    fun positions(): Int = call("getPositions") { EpubBundles.answer(book.positions, "getPositions").getInt(EpubContract.KEY_POSITIONS) }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { book.asBinder().unlinkToDeath(deathRecipient, 0) }
            if (!died.get()) runCatching { book.close() }
            runCatching { leased.bindingLease.close() }
        }
    }

    private fun onDied() {
        died.set(true)
        if (closed.compareAndSet(false, true)) runCatching { leased.bindingLease.close() }
    }

    private inline fun <T> call(label: String, phase: EpubErrorMapper.Phase = EpubErrorMapper.Phase.CALL, block: () -> T): T {
        if (closed.get()) {
            val reason = if (died.get()) "EPUB plugin process died" else "the book is closed"
            throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.SESSION_CLOSED, "$label: $reason"))
        }
        return try {
            block()
        } catch (e: EpubPluginException) {
            throw e
        } catch (t: Throwable) {
            throw EpubPluginException(EpubErrorMapper.fromThrowable(t, phase), t)
        }
    }
}

/** The reader session's state as `IEpubReaderSession.getState` answers it. */
data class EpubReaderState(
    val token: String?,
    val visible: Boolean,
    val locatorJson: String?,
    val progression: Double?,
    val title: String?,
    val href: String?,
    val index: Int?,
    val positions: Int,
)

/**
 * `IEpubReaderSession` held on a dedicated binding lease with its event bridge (plugin roadmap
 * P5.3 / P5.4, D12 / D32): [close] with `finish = false` ends the session and leaves the reader
 * open for the user; a death of the plugin process delivers one synthetic `close(reason=error)`.
 */
class EpubReaderClient internal constructor(
    private val leased: AidlPluginHost.LeasedCall<IEpubReaderSession>,
    val bridge: EpubReaderBridge,
    val token: String,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val died = AtomicBoolean(false)
    private val deathRecipient = IBinder.DeathRecipient { onDied() }

    private val session: IEpubReaderSession get() = leased.value

    val isClosed: Boolean get() = closed.get() || bridge.isClosed

    init {
        try {
            session.asBinder().linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            onDied()
        }
    }

    fun state(): EpubReaderState = call("getState") { decodeState(EpubBundles.answer(session.state, "getState")) }

    fun goTo(locatorJson: String? = null, href: String? = null, progression: Double? = null) = call("goTo") {
        session.goTo(EpubBundles.goToTarget(locatorJson, href, progression))
    }

    fun navigate(direction: Int) = call("navigate") { session.navigate(direction) }

    fun setPreferences(preferencesJson: String) = call("setPreferences") { session.setPreferences(EpubBundles.preferences(preferencesJson)) }

    fun bookmarks(): String = call("getBookmarks") {
        EpubBundles.document(EpubBundles.answer(session.bookmarks, "getBookmarks"), EpubContract.KEY_BOOKMARKS, "getBookmarks")
    }

    /** Ends the session; with [finish] the reader Activity is finished too (D32). */
    fun close(finish: Boolean) {
        if (closed.compareAndSet(false, true)) {
            runCatching { session.asBinder().unlinkToDeath(deathRecipient, 0) }
            if (!died.get()) runCatching { session.close(EpubBundles.closeOptions(finish)) }
            runCatching { leased.bindingLease.close() }
            bridge.closeLocally(EpubContract.REASON_HOST)
        }
    }

    override fun close() = close(finish = false)

    private fun onDied() {
        died.set(true)
        if (closed.compareAndSet(false, true)) {
            runCatching { leased.bindingLease.close() }
            bridge.closeLocally(EpubContract.REASON_ERROR)
        }
    }

    private inline fun <T> call(label: String, block: () -> T): T {
        if (closed.get()) {
            val reason = if (died.get()) "EPUB plugin process died" else "the reader session is closed"
            throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.SESSION_CLOSED, "$label: $reason"))
        }
        return try {
            block()
        } catch (e: EpubPluginException) {
            throw e
        } catch (t: Throwable) {
            throw EpubPluginException(EpubErrorMapper.fromThrowable(t, EpubErrorMapper.Phase.CALL), t)
        }
    }

    companion object {
        internal fun decodeState(answer: Bundle): EpubReaderState = EpubReaderState(
            token = answer.getString(EpubContract.KEY_SESSION_TOKEN),
            visible = answer.getBoolean(EpubContract.KEY_VISIBLE),
            locatorJson = answer.getString(EpubContract.KEY_LOCATOR),
            progression = if (answer.containsKey(EpubContract.KEY_PROGRESSION)) answer.getDouble(EpubContract.KEY_PROGRESSION) else null,
            title = answer.getString(EpubContract.KEY_TITLE),
            href = answer.getString(EpubContract.KEY_HREF),
            index = if (answer.containsKey(EpubContract.KEY_INDEX)) answer.getInt(EpubContract.KEY_INDEX) else null,
            positions = answer.getInt(EpubContract.KEY_POSITIONS),
        )
    }
}
