package org.autojs.autojs.memory.scenarios

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.opencv.Mat
import org.json.JSONObject
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

/**
 * Native memory detail for the gate (docs/memory/ROADMAP.md P3.1), next to the six T1 measurements:
 * image, Mat and media Image counters of the host, instance counts of the classes that own native memory,
 * the dumpsys meminfo categories of this process and the native heap peak inside a run.
 * Nothing here changes host state; the dumpsys output is read through the shell identity of UiAutomation.
 * zh-CN: 门禁的原生内存明细 (docs/memory/ROADMAP.md P3.1), 与 T1 的六项度量并列: 宿主的图像, Mat 与 media Image 计数, 持有原生内存的类的实例数,
 * 本进程 dumpsys meminfo 的分类, 以及一次运行内的原生堆峰值. 这里不改动宿主状态; dumpsys 输出经 UiAutomation 的 shell 身份读取.
 */
internal object GateNativeProbe {

    /** Classes whose instances hold native memory or framework buffers (counted through VMDebug when allowed). */
    private val INSTANCE_CLASSES = linkedMapOf(
        "bitmap" to "android.graphics.Bitmap",
        "imageWrapper" to "org.autojs.autojs.core.image.ImageWrapper",
        "cvMat" to "org.opencv.core.Mat",
        "mediaImage" to "android.media.Image",
        "accessibilityNode" to "android.view.accessibility.AccessibilityNodeInfo",
    )

    private val countInstancesOfClasses: Method? by lazy {
        runCatching {
            Class.forName("dalvik.system.VMDebug").getMethod("countInstancesOfClasses", Class.forName("[Ljava.lang.Class;"), Boolean::class.javaPrimitiveType)
        }.getOrNull()
    }

    private val countInstancesOfClass: Method? by lazy {
        runCatching {
            Class.forName("dalvik.system.VMDebug").getMethod("countInstancesOfClass", Class::class.java, Boolean::class.javaPrimitiveType)
        }.getOrNull()
    }

    /**
     * Host counters: cheap, read after every run. Mat counters cover org.autojs.autojs.core.opencv.Mat only.
     * zh-CN: 宿主计数器: 开销很小, 每次运行后读取. Mat 计数只覆盖 org.autojs.autojs.core.opencv.Mat.
     */
    fun counters(): JSONObject = JSONObject()
        .put("imagesLive", ImageWrapper.liveCount())
        .put("openMediaImages", ImageWrapper.openMediaImageCount())
        .put("matsLive", Mat.getLiveCount())
        .put("matsCreated", Mat.getCreatedCount())
        .put("matsReleased", Mat.getReleasedCount())
        .put("matsFinalized", Mat.getFinalizedCount())
        .put("nativeHeapSizeBytes", Debug.getNativeHeapSize())
        .put("nativeHeapFreeBytes", Debug.getNativeHeapFreeSize())

    /**
     * Reachable instances per class (VMDebug collects garbage first); -1 when the hidden API is not accessible.
     * zh-CN: 每个类的可达实例数 (VMDebug 会先回收垃圾); 隐藏 API 不可用时为 -1.
     */
    fun instanceCounts(): JSONObject {
        val result = JSONObject()
        val classes = INSTANCE_CLASSES.mapValues { (_, name) -> runCatching { Class.forName(name) }.getOrNull() }
        val present = classes.filterValues { it != null }.mapValues { it.value!! }
        val bulk = countInstancesOfClasses?.let { method ->
            runCatching { method.invoke(null, present.values.toTypedArray(), true) as LongArray }.getOrNull()
        }
        present.keys.forEachIndexed { index, key ->
            val count = bulk?.getOrNull(index) ?: countInstancesOfClass?.let { method ->
                runCatching { method.invoke(null, present.getValue(key), true) as Long }.getOrNull()
            }
            result.put(key, count ?: -1L)
        }
        classes.filterValues { it == null }.keys.forEach { result.put(it, -1L) }
        return result
    }

    /**
     * `dumpsys meminfo <pid>` of this process, reduced to the categories the roadmap tracks (KB), or null when the
     * shell is unavailable. The first call connects UiAutomation, so callers make it before their first sample.
     * zh-CN: 本进程的 `dumpsys meminfo <pid>`, 精简为路线图关注的分类 (KB); shell 不可用时为 null. 首次调用会连接 UiAutomation, 因此调用方应在首个采样之前调用一次.
     */
    fun meminfo(instrumentation: Instrumentation): JSONObject? {
        val started = SystemClock.elapsedRealtime()
        val text = runCatching {
            val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys meminfo ${Process.myPid()}"))
                .bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return parseMeminfo(text)?.put("elapsedMs", SystemClock.elapsedRealtime() - started)
    }

    /** Parses the detail table rows and the App Summary of dumpsys meminfo (API 24-36 layouts). */
    fun parseMeminfo(text: String): JSONObject? {
        val result = JSONObject()
        fun numbers(line: String) = Regex("""-?\d+""").findAll(line).map { it.value.toLong() }.toList()
        var inSummary = false
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.trim() == "App Summary") inSummary = true
            val summary = Regex("""^\s*([A-Za-z ]+):\s+(\d+)""").find(line)
            if (inSummary && summary != null) {
                when (summary.groupValues[1].trim()) {
                    "Java Heap" -> result.put("summaryJavaHeapKb", summary.groupValues[2].toLong())
                    "Native Heap" -> result.put("summaryNativeHeapKb", summary.groupValues[2].toLong())
                    "Code" -> result.put("summaryCodeKb", summary.groupValues[2].toLong())
                    "Stack" -> result.put("summaryStackKb", summary.groupValues[2].toLong())
                    "Graphics" -> result.put("graphicsKb", summary.groupValues[2].toLong())
                    "Private Other" -> result.put("privateOtherKb", summary.groupValues[2].toLong())
                    "System" -> result.put("systemKb", summary.groupValues[2].toLong())
                    "TOTAL", "TOTAL PSS" -> result.put("totalPssKb", summary.groupValues[2].toLong())
                }
                continue
            }
            if (inSummary) continue
            val row = Regex("""^\s*(Native Heap|Dalvik Heap|Unknown|EGL mtrack|GL mtrack|Other mmap|Gfx dev|TOTAL)\s+(-?\d.*)$""").find(line) ?: continue
            val values = numbers(row.groupValues[2])
            if (values.isEmpty()) continue
            val key = when (row.groupValues[1]) {
                "Native Heap" -> "nativePss"
                "Dalvik Heap" -> "dalvikPss"
                "Unknown" -> "unknownPss"
                "EGL mtrack" -> "eglMtrackPss"
                "GL mtrack" -> "glMtrackPss"
                "Other mmap" -> "otherMmapPss"
                "Gfx dev" -> "gfxDevPss"
                else -> "tablePss"
            }
            if (result.has(key + "Kb")) continue
            result.put(key + "Kb", values[0])
            if (row.groupValues[1] == "Native Heap" && values.size >= 7) {
                // The last three columns are Heap Size, Heap Alloc and Heap Free on every layout.
                // zh-CN: 各版本布局中最后三列都是 Heap Size, Heap Alloc 与 Heap Free.
                result.put("nativeHeapSizeKb", values[values.size - 3])
                result.put("nativeHeapAllocKb", values[values.size - 2])
                result.put("nativeHeapFreeKb", values[values.size - 1])
            }
        }
        return if (result.length() == 0) null else result
    }

    /**
     * Samples Debug.getNativeHeapAllocatedSize while a run is in progress; [stop] returns the peak above the value
     * at [start]. Shows native memory that only the garbage collector would free (finalizer lag, issue #372), which
     * the after-GC samples of T1 cannot see.
     * zh-CN: 在运行期间采样 Debug.getNativeHeapAllocatedSize; [stop] 返回高于 [start] 时数值的峰值. 用于观察只能由 GC 释放的原生内存
     * (finalizer 滞后, issue #372), 这是 T1 在 GC 之后的采样看不到的.
     */
    class PeakSampler(private val intervalMs: Long = 20) {
        private val peak = AtomicLong()
        private var base = 0L

        @Volatile
        private var running = false
        private var thread: Thread? = null

        fun start() {
            base = Debug.getNativeHeapAllocatedSize()
            peak.set(base)
            running = true
            thread = Thread({
                while (running) {
                    peak.accumulateAndGet(Debug.getNativeHeapAllocatedSize(), ::maxOf)
                    SystemClock.sleep(intervalMs)
                }
            }, "MemoryGatePeakSampler").apply {
                isDaemon = true
                start()
            }
        }

        fun stop(): Long {
            running = false
            thread?.join(1_000)
            thread = null
            peak.accumulateAndGet(Debug.getNativeHeapAllocatedSize(), ::maxOf)
            return peak.get() - base
        }
    }
}
