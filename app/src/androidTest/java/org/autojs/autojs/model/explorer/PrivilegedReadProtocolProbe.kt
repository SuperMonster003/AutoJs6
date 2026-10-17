package org.autojs.autojs.model.explorer

import android.system.Os
import androidx.annotation.Keep
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream

/** Test-APK-only peer for bounded-read failures, never used by the production provider. */
@Keep
object PrivilegedReadProtocolProbe {
    @Keep
    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 2)
        val output = DataOutputStream(System.out)
        val identity = PrivilegedReadProtocol.Identity.from(Os.lstat(arguments[0]))
        output.writeInt(PrivilegedReadProtocol.MAGIC)
        output.writeInt(PrivilegedReadProtocol.VERSION)
        PrivilegedReadProtocol.writeJson(output, JSONObject().put("ok", true).put("size", identity.size)
            .put("identity", identity.json()).put("pid", android.os.Process.myPid()))
        output.flush()
        val input = DataInputStream(System.`in`)
        input.readInt()
        val id = input.readInt()
        input.readLong(); input.readInt(); input.readLong()
        if (arguments[1] == "stall") { Thread.sleep(10_000); return }
        output.writeInt(id)
        output.writeInt(0)
        when (arguments[1]) {
            "oversized" -> output.writeInt(PrivilegedReadProtocol.MAX_CHUNK + 1)
            "truncated" -> { output.writeInt(3); output.writeByte(42) }
            else -> error("Unknown probe mode")
        }
        output.flush()
    }
}
