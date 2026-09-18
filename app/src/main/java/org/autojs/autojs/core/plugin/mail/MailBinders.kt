package org.autojs.autojs.core.plugin.mail

import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.mail.api.IMailCallCallback
import org.autojs.plugin.mail.api.IMailSession
import org.autojs.plugin.mail.api.IMailSessionCallback
import org.autojs.plugin.mail.api.IMailWatchCallback
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Bundle shapes of the mail contract as written and read by the host. */
internal object MailBundles {

    fun accountToBundle(account: MailAccountSpec, hostVersionCode: Long): Bundle = Bundle().apply {
        putInt(MailContract.KEY_CONTRACT_VERSION, MailContract.CONTRACT_VERSION)
        putLong(MailContract.KEY_HOST_VERSION_CODE, hostVersionCode)
        account.alias?.let { putString(MailContract.KEY_ACCOUNT_ALIAS, it) }
        account.accountJson?.let { putString(MailContract.KEY_ACCOUNT_JSON, it) }
        account.password?.let { putString(MailContract.KEY_SECRET_PASSWORD, it) }
        account.accessToken?.let { putString(MailContract.KEY_SECRET_ACCESS_TOKEN, it) }
    }

    fun requestToBundle(request: MailJson.Request): Bundle = Bundle().apply {
        putInt(MailContract.KEY_CONTRACT_VERSION, MailContract.CONTRACT_VERSION)
        putString(MailContract.KEY_REQUEST_JSON, request.json)
    }

    fun watchOptionsToBundle(optionsJson: String, generation: Long): Bundle = Bundle().apply {
        putInt(MailContract.KEY_CONTRACT_VERSION, MailContract.CONTRACT_VERSION)
        putLong(MailContract.KEY_GENERATION, generation)
        putString(MailContract.KEY_WATCH_OPTIONS_JSON, optionsJson)
    }

    fun string(bundle: Bundle?, key: String): String? = bundle?.getString(key)
}

/**
 * Delivers `onStatus` of a session; a `closed` status ends the session on the client side. The
 * callback is oneway, so a refused `openSession` may return null before its `closed` status
 * arrives; [awaitClosed] bridges that gap for the opener.
 */
internal class MailSessionCallbackBinder(private val events: MailSessionEvents) : IMailSessionCallback.Stub() {

    private val closed = CountDownLatch(1)

    @Volatile
    var lastStatus: MailJson.Status? = null
        private set

    override fun onStatus(status: Bundle?) {
        val decoded = runCatching { MailJson.decodeStatus(MailBundles.string(status, MailContract.KEY_STATUS_JSON)) }.getOrNull() ?: return
        lastStatus = decoded
        if (decoded.state == MailContract.STATE_CLOSED) {
            closed.countDown()
            events.onClosed(decoded.lastError ?: MailErrorMapper.normalize(MailErrorCodes.SESSION_CLOSED, decoded.reason, retryable = true))
        }
    }

    /** The `closed` status of a refusal, waiting up to [timeoutMs] for the oneway callback; null when none arrived. */
    fun awaitClosed(timeoutMs: Long): MailJson.Status? {
        if (!closed.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
        return lastStatus?.takeIf { it.state == MailContract.STATE_CLOSED }
    }
}

/** Forwards `onEvent` of a watch into the generation and sequence checks of [MailWatchBridge]. */
internal class MailWatchCallbackBinder(private val bridge: MailWatchBridge) : IMailWatchCallback.Stub() {

    override fun onEvent(generation: Long, seq: Long, event: Bundle?) {
        bridge.accept(generation, seq, MailBundles.string(event, MailContract.KEY_EVENT_JSON))
    }
}

/**
 * [MailSessionPort] over an `IMailSession` held on a dedicated binding lease. A death of the
 * plugin process releases the lease and reports `SESSION_CLOSED`; [close] unlinks, closes the
 * remote session and releases the lease exactly once.
 */
internal class BinderMailSessionPort(
    private val leased: AidlPluginHost.LeasedCall<IMailSession>,
    private val events: MailSessionEvents,
) : MailSessionPort {

    private val closed = AtomicBoolean(false)
    private val deathRecipient = IBinder.DeathRecipient { onDied() }

    private val session: IMailSession
        get() = leased.value

    init {
        try {
            session.asBinder().linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            onDied()
        }
    }

    override fun status(): MailJson.Status {
        return MailJson.decodeStatus(MailBundles.string(session.getStatus(), MailContract.KEY_STATUS_JSON))
    }

    override fun call(request: MailJson.Request, descriptors: Array<ParcelFileDescriptor>?, listener: MailCallListener) {
        val callback = object : IMailCallCallback.Stub() {
            override fun onProgress(progress: Bundle?) {
                runCatching { MailJson.decodeProgress(MailBundles.string(progress, MailContract.KEY_PROGRESS_JSON)) }
                    .onSuccess(listener::onProgress)
            }

            override fun onResult(response: Bundle?) {
                val decoded = try {
                    MailJson.decodeResponse(MailBundles.string(response, MailContract.KEY_RESPONSE_JSON))
                } catch (e: MailPluginException) {
                    MailJson.Response(request.id, ok = false, result = null, error = e.error)
                }
                listener.onResult(decoded)
            }
        }
        session.call(MailBundles.requestToBundle(request), descriptors, callback)
    }

    override fun cancel(requestId: String) {
        session.cancel(requestId)
    }

    override fun watch(optionsJson: String, bridge: MailWatchBridge): MailWatchHandle {
        val watch = session.watch(MailBundles.watchOptionsToBundle(optionsJson, bridge.generation), MailWatchCallbackBinder(bridge))
            ?: throw IllegalStateException("mail plugin returned no watch")
        return object : MailWatchHandle {
            override fun status(): MailJson.Status {
                return MailJson.decodeStatus(MailBundles.string(watch.getStatus(), MailContract.KEY_STATUS_JSON))
            }

            override fun stop() {
                bridge.close()
                watch.stop()
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { session.asBinder().unlinkToDeath(deathRecipient, 0) }
            runCatching { session.close() }
            runCatching { leased.bindingLease.close() }
        }
    }

    private fun onDied() {
        if (closed.compareAndSet(false, true)) {
            runCatching { leased.bindingLease.close() }
            events.onClosed(MailErrorMapper.normalize(MailErrorCodes.SESSION_CLOSED, "mail plugin process died", retryable = true))
        }
    }
}
