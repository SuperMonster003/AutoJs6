package org.autojs.autojs.model.explorer

import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.annotation.Keep
import org.json.JSONObject
import kotlin.system.exitProcess

/** app_process entry point: Root and Shizuku execute the same filesystem implementation. */
@Keep
object PrivilegedFileCommand {
    internal const val SAVE_STARTED_MARKER = "AUTOJS6_PRIVILEGED_SAVE_STARTED"
    @Keep
    @JvmStatic
    fun main(arguments: Array<String>) {
        try {
            require(arguments.size == 1 && arguments[0].length <= 65536)
            val request = String(Base64.decode(arguments[0], Base64.NO_WRAP), Charsets.UTF_8)
            val json = JSONObject(request)
            PrivilegedFileNative.configure(json.optString("nativeLibrary").takeIf { it.isNotEmpty() })
            if (json.optString("operation") == "read") {
                ParcelFileDescriptor.AutoCloseInputStream(PrivilegedFileEngine.openReadDescriptor(request)).use { input ->
                    val deadline = json.getLong("deadlineMillis")
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        check(System.currentTimeMillis() < deadline) { "File read timed out" }
                        val count = input.read(buffer)
                        if (count < 0) break
                        System.out.write(buffer, 0, count)
                    }
                    System.out.flush()
                }
            } else if (json.optString("operation") == "snapshot") {
                System.out.println(PrivilegedFileSaveEngine.statSnapshot(request))
            } else if (json.optString("operation") == "reconcile") {
                System.out.println(PrivilegedFileSaveEngine.reconcile(request))
            } else if (json.optString("operation") == "save") {
                // Distinguish a denied su/app_process launch from a write whose reply was lost.
                System.err.println(SAVE_STARTED_MARKER)
                System.err.flush()
                System.out.println(PrivilegedFileSaveEngine.save(request, System.`in`))
            } else {
                System.out.println(PrivilegedFileEngine.execute(request))
            }
        } catch (error: Exception) {
            System.err.println(error.message.orEmpty().take(4096))
            exitProcess(1)
        }
    }
}
