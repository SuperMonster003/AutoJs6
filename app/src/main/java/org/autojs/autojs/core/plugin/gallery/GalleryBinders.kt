package org.autojs.autojs.core.plugin.gallery

import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import org.autojs.plugin.gallery.api.IGalleryCallback
import org.autojs.plugin.gallery.api.IGallerySession
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The contract version and features the host agreed with one plugin: [contractVersion] is the
 * smaller of the host's `MAX_CONTRACT_VERSION` and the newest version the plugin advertises, and
 * travels in every request; [features] is the plugin's `galleryFeatures` array.
 */
data class GalleryPeer(val contractVersion: Int, val features: Set<String>) {

    fun supports(feature: String): Boolean = feature in features

    companion object {
        /** A version-1 peer that declared no features. */
        val LEGACY = GalleryPeer(GalleryContract.MIN_CONTRACT_VERSION, emptySet())

        /**
         * The negotiated version for a plugin advertising [base] (`galleryContractVersion`) and
         * [max] (`galleryMaxContractVersion`, null when absent): the plugin's newest version bounded
         * by the host's own, never below [base].
         */
        fun negotiate(base: Int, max: Int?): Int {
            val newest = maxOf(base, max ?: base)
            return maxOf(base, minOf(newest, GalleryContract.MAX_CONTRACT_VERSION))
        }
    }
}

/** Options of `IGalleryPlugin.openPicker` as the script layer collected them. */
data class GalleryPickerOptions(
    val multiple: Boolean = false,
    val maxCount: Int? = null,
    val mimeTypes: List<String> = emptyList(),
    val title: String? = null,
)

/** Output format and metadata options shared by `openEditor` and `convert`. */
data class GalleryEncodeOptions(
    val format: String? = null,
    val quality: Int? = null,
    val keepExif: Boolean = false,
    val keepLocation: Boolean = false,
)

/** The size and encoding options of `convert` beyond [GalleryEncodeOptions]. */
data class GalleryConvertOptions(
    val encode: GalleryEncodeOptions = GalleryEncodeOptions(),
    val lossless: Boolean = false,
    val targetBytes: Long? = null,
    val sizeMode: String? = null,
    val percent: Int? = null,
    val longEdge: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val keepAspectRatio: Boolean = true,
    val background: Int? = null,
)

/** Bundle shapes of the gallery contract as written and read by the host. */
object GalleryBundles {

    /** The Bundle every request carries, stamped with the negotiated [version]; [build] adds the request keys. */
    fun request(version: Int, build: Bundle.() -> Unit = {}): Bundle = Bundle().apply {
        putInt(GalleryContract.KEY_CONTRACT_VERSION, version)
        build()
    }

    fun pickerOptions(version: Int, options: GalleryPickerOptions): Bundle = request(version) {
        putBoolean(GalleryContract.KEY_MULTIPLE, options.multiple)
        options.maxCount?.let { putInt(GalleryContract.KEY_MAX_COUNT, it) }
        if (options.mimeTypes.isNotEmpty()) putStringArray(GalleryContract.KEY_MIME_TYPES, options.mimeTypes.toTypedArray())
        options.title?.takeIf { it.isNotBlank() }?.let { putString(GalleryContract.KEY_TITLE, it) }
    }

    fun viewerOptions(version: Int, displayNames: List<String>, startIndex: Int): Bundle = request(version) {
        putStringArray(GalleryContract.KEY_DISPLAY_NAMES, displayNames.toTypedArray())
        putInt(GalleryContract.KEY_START_INDEX, startIndex)
    }

    fun editorOptions(version: Int, displayName: String, encode: GalleryEncodeOptions): Bundle = request(version) {
        putString(GalleryContract.KEY_DISPLAY_NAME, displayName)
        putEncode(encode)
    }

    fun convertOptions(version: Int, displayName: String, options: GalleryConvertOptions): Bundle = request(version) {
        putString(GalleryContract.KEY_DISPLAY_NAME, displayName)
        putEncode(options.encode)
        if (options.lossless) putBoolean(GalleryContract.KEY_LOSSLESS, true)
        options.targetBytes?.let { putLong(GalleryContract.KEY_TARGET_BYTES, it) }
        options.sizeMode?.let { putString(GalleryContract.KEY_SIZE_MODE, it) }
        options.percent?.let { putInt(GalleryContract.KEY_PERCENT, it) }
        options.longEdge?.let { putInt(GalleryContract.KEY_LONG_EDGE, it) }
        options.width?.let { putInt(GalleryContract.KEY_WIDTH, it) }
        options.height?.let { putInt(GalleryContract.KEY_HEIGHT, it) }
        if (!options.keepAspectRatio) putBoolean(GalleryContract.KEY_KEEP_ASPECT_RATIO, false)
        options.background?.let { putInt(GalleryContract.KEY_BACKGROUND, it) }
    }

    fun closeOptions(version: Int, finish: Boolean): Bundle = request(version) { putBoolean(GalleryContract.KEY_FINISH, finish) }

    private fun Bundle.putEncode(encode: GalleryEncodeOptions) {
        encode.format?.let { putString(GalleryContract.KEY_FORMAT, it) }
        encode.quality?.let { putInt(GalleryContract.KEY_QUALITY, it) }
        if (encode.keepExif) putBoolean(GalleryContract.KEY_KEEP_EXIF, true)
        if (encode.keepLocation) putBoolean(GalleryContract.KEY_KEEP_LOCATION, true)
    }

    /**
     * A Bundle answer of the contract: refuses a missing answer or an unsupported contract version,
     * throws the carried error, otherwise returns the Bundle for its result keys.
     */
    fun answer(bundle: Bundle?, label: String): Bundle {
        if (bundle == null) throw GalleryErrorMapper.failure(GalleryErrorCodes.INTERNAL, "$label returned no answer")
        val version = bundle.getInt(GalleryContract.KEY_CONTRACT_VERSION, 0)
        if (!GalleryContract.supportsContractVersion(version)) {
            throw GalleryErrorMapper.failure(GalleryErrorCodes.PLUGIN_INCOMPATIBLE, "$label answered with contract version $version")
        }
        GalleryErrorMapper.fromBundle(bundle)?.let { throw GalleryPluginException(it) }
        return bundle
    }

    fun decodeEvent(bundle: Bundle?): GalleryEvent? {
        val type = bundle?.getString(GalleryContract.KEY_EVENT) ?: return null
        return GalleryEvent(
            type = type,
            reason = bundle.getString(GalleryContract.KEY_REASON),
            kind = bundle.getString(GalleryContract.KEY_KIND),
            index = bundle.intOrNull(GalleryContract.KEY_INDEX),
            itemsJson = bundle.getString(GalleryContract.KEY_ITEMS),
            mimeType = bundle.getString(GalleryContract.KEY_MIME_TYPE),
            width = bundle.intOrNull(GalleryContract.KEY_WIDTH),
            height = bundle.intOrNull(GalleryContract.KEY_HEIGHT),
            size = if (bundle.containsKey(GalleryContract.KEY_SIZE)) bundle.getLong(GalleryContract.KEY_SIZE) else null,
            errorCode = bundle.getString(GalleryContract.KEY_ERROR_CODE),
            errorMessage = bundle.getString(GalleryContract.KEY_ERROR_MESSAGE),
        )
    }

    private fun Bundle.intOrNull(key: String): Int? = if (containsKey(key)) getInt(key) else null
}

/** Forwards `onEvent` of a session into the generation and sequence checks of [GallerySessionBridge]. */
internal class GalleryCallbackBinder(private val bridge: GallerySessionBridge) : IGalleryCallback.Stub() {

    override fun onEvent(generation: Long, seq: Long, event: Bundle?) {
        bridge.accept(generation, seq, GalleryBundles.decodeEvent(event))
    }
}

/** A session's state as `IGallerySession.getState` answers it. */
data class GallerySessionState(
    val token: String?,
    val kind: String?,
    val visible: Boolean,
    val index: Int?,
)

/**
 * `IGallerySession` held on a dedicated binding lease with its event bridge: [close] ends the
 * session (and by default finishes the plugin Activity); a death of the plugin process delivers one
 * synthetic `close(reason=error)`.
 */
class GallerySessionClient internal constructor(
    private val leased: AidlPluginHost.LeasedCall<IGallerySession>,
    val bridge: GallerySessionBridge,
    val token: String,
    val peer: GalleryPeer = GalleryPeer.LEGACY,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val died = AtomicBoolean(false)
    private val deathRecipient = IBinder.DeathRecipient { onDied() }

    private val session: IGallerySession get() = leased.value

    val isClosed: Boolean get() = closed.get() || bridge.isClosed

    init {
        try {
            session.asBinder().linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            onDied()
        }
    }

    fun state(): GallerySessionState = call("getState") { decodeState(GalleryBundles.answer(session.state, "getState")) }

    /** Ends the session; with [finish] (the default) the plugin Activity is finished too. */
    fun close(finish: Boolean) {
        if (closed.compareAndSet(false, true)) {
            runCatching { session.asBinder().unlinkToDeath(deathRecipient, 0) }
            if (!died.get()) runCatching { session.close(GalleryBundles.closeOptions(peer.contractVersion, finish)) }
            runCatching { leased.bindingLease.close() }
            bridge.closeLocally(GalleryContract.REASON_HOST)
        }
    }

    /** Releases the binding once the plugin itself ended the session (its `close` event arrived). */
    fun release() {
        if (closed.compareAndSet(false, true)) {
            runCatching { session.asBinder().unlinkToDeath(deathRecipient, 0) }
            runCatching { leased.bindingLease.close() }
        }
    }

    override fun close() = close(finish = true)

    private fun onDied() {
        died.set(true)
        if (closed.compareAndSet(false, true)) {
            runCatching { leased.bindingLease.close() }
            bridge.closeLocally(GalleryContract.REASON_ERROR)
        }
    }

    private inline fun <T> call(label: String, block: () -> T): T {
        if (closed.get()) {
            val reason = if (died.get()) "gallery plugin process died" else "the session is closed"
            throw GalleryErrorMapper.failure(GalleryErrorCodes.SESSION_CLOSED, "$label: $reason")
        }
        return try {
            block()
        } catch (e: GalleryPluginException) {
            throw e
        } catch (t: Throwable) {
            throw GalleryPluginException(GalleryErrorMapper.fromThrowable(t, GalleryErrorMapper.Phase.CALL), t)
        }
    }

    companion object {
        internal fun decodeState(answer: Bundle): GallerySessionState = GallerySessionState(
            token = answer.getString(GalleryContract.KEY_SESSION_TOKEN),
            kind = answer.getString(GalleryContract.KEY_KIND),
            visible = answer.getBoolean(GalleryContract.KEY_VISIBLE),
            index = if (answer.containsKey(GalleryContract.KEY_INDEX)) answer.getInt(GalleryContract.KEY_INDEX) else null,
        )
    }
}
