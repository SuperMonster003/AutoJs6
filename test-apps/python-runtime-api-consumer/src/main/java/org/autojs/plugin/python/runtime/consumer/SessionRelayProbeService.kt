package org.autojs.plugin.python.runtime.consumer

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import org.autojs.plugin.python.runtime.api.IPythonExecutionSession

/**
 * Test-only distinct-UID endpoint which attempts to use a session Binder admitted to the host.
 *
 * [IPythonExecutionSession] is oneway, so Binder cannot return the provider-side
 * [SecurityException] to this process. The host-side instrumentation therefore proves rejection
 * behaviorally: after all four calls below, the owning host UID must still be able to start the
 * untouched session exactly once and receive its only terminal callback.
 */
class SessionRelayProbeService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action != null) return null
        return relayBinder
    }

    private val relayBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != TRANSACTION_PROBE_SESSION) {
                return super.onTransact(code, data, reply, flags)
            }
            data.enforceInterface(DESCRIPTOR)
            val session = IPythonExecutionSession.Stub.asInterface(
                requireNotNull(data.readStrongBinder()) { "Relayed Python session Binder is null" },
            )

            // Any one of these calls taking effect would prevent the owner from completing the
            // still-CREATED session normally. Int.MAX_VALUE is deliberately invalid output credit.
            session.start()
            session.grantOutputCredits(Int.MAX_VALUE)
            session.cancel()
            session.close()

            reply?.writeNoException()
            reply?.writeInt(EXPECTED_OPERATION_COUNT)
            return true
        }
    }

    companion object {
        const val DESCRIPTOR =
            "org.autojs.plugin.python.runtime.consumer.SessionRelayProbeService"
        const val TRANSACTION_PROBE_SESSION = IBinder.FIRST_CALL_TRANSACTION
        const val EXPECTED_OPERATION_COUNT = 4
    }
}
