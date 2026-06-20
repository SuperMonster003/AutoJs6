package org.autojs.autojs.engine

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import org.json.JSONObject
import java.io.File

internal data class NodeResourceStressSnapshot(
    val label: String,
    val pid: Int,
    val rssKb: Long?,
    val javaHeapKb: Long,
    val javaHeapCommittedKb: Long,
    val nativeHeapKb: Long,
    val threadCount: Int?,
    val fdCount: Int?,
    val libuvHandleCount: Int?,
    val bridgeDiagnostics: Map<String, String>,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("label", label)
            .put("pid", pid)
            .put("rssKb", rssKb ?: JSONObject.NULL)
            .put("javaHeapKb", javaHeapKb)
            .put("javaHeapCommittedKb", javaHeapCommittedKb)
            .put("nativeHeapKb", nativeHeapKb)
            .put("threadCount", threadCount ?: JSONObject.NULL)
            .put("fdCount", fdCount ?: JSONObject.NULL)
            .put("libuvHandleCount", libuvHandleCount ?: JSONObject.NULL)
            .put("bridgeDiagnostics", JSONObject(bridgeDiagnostics))

    companion object {
        fun capture(
            label: String,
            bridgeDiagnostics: Map<String, String> = emptyMap(),
            libuvHandleCount: Int? = null,
            settle: Boolean = true,
        ): NodeResourceStressSnapshot {
            if (settle) {
                Runtime.getRuntime().gc()
                System.runFinalization()
                SystemClock.sleep(25L)
            }
            val runtime = Runtime.getRuntime()
            val pid = Process.myPid()
            return NodeResourceStressSnapshot(
                label = label,
                pid = pid,
                rssKb = readStatusLongKb(pid, "VmRSS:"),
                javaHeapKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
                javaHeapCommittedKb = runtime.totalMemory() / 1024L,
                nativeHeapKb = Debug.getNativeHeapAllocatedSize() / 1024L,
                threadCount = File("/proc/$pid/task").safeCountChildren(),
                fdCount = File("/proc/$pid/fd").safeCountChildren(),
                libuvHandleCount = libuvHandleCount,
                bridgeDiagnostics = bridgeDiagnostics,
            )
        }

        private fun readStatusLongKb(pid: Int, prefix: String): Long? =
            runCatching {
                File("/proc/$pid/status")
                    .readLines()
                    .firstOrNull { it.startsWith(prefix) }
                    ?.split(Regex("""\s+"""))
                    ?.firstOrNull { it.toLongOrNull() != null }
                    ?.toLongOrNull()
            }.getOrNull()

        private fun File.safeCountChildren(): Int? =
            runCatching { listFiles()?.size }.getOrNull()
    }
}
