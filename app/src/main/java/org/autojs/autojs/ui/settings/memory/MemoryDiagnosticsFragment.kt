package org.autojs.autojs.ui.settings.memory

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.runtime.memory.MemoryDiagnostics
import org.autojs.autojs.runtime.memory.MemorySnapshot
import org.autojs.autojs.runtime.memory.RetainedRuntime
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.theme.preference.ThemeColorPreferenceCategory
import org.autojs.autojs.ui.error.ProcessExitHistoryActivity
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The list of the memory diagnostics page (docs/memory/ROADMAP.md T10): the snapshot values refreshed every 2 s, the finished but
 * unreclaimed runtimes (script name, exit kind, time since it ended), and the actions "Force GC and check again", "Export heap dump"
 * (SAF CreateDocument) and "Copy diagnostics text". Everything is read from [MemoryDiagnostics].
 * zh-CN: 内存诊断页面的列表 (docs/memory/ROADMAP.md T10): 每 2 s 刷新的快照各项, 已结束未回收的 runtime (脚本名, 结束方式, 已过时长), 以及
 * "强制 GC 并复查", "导出堆转储" (SAF CreateDocument), "复制诊断文本" 三个操作. 数据全部取自 [MemoryDiagnostics].
 */
class MemoryDiagnosticsFragment : PreferenceFragmentCompat() {

    private lateinit var javaHeap: Preference
    private lateinit var nativeHeap: Preference
    private lateinit var pss: Preference
    private lateinit var engines: Preference
    private lateinit var retainedRuntimes: Preference
    private lateinit var images: Preference
    private lateinit var threads: Preference
    private lateinit var contexts: Preference
    private var leakCanary: Preference? = null
    private lateinit var updatedAt: Preference
    private lateinit var retainedCategory: PreferenceGroup
    private lateinit var forceGc: Preference
    private lateinit var exportHeapDump: Preference

    private var shownRetainedIds: List<Int>? = null
    private var isCollecting = false
    private var isExporting = false

    private val createHeapDump = registerForActivityResult(ActivityResultContracts.CreateDocument(HPROF_MIME_TYPE)) { uri ->
        uri?.let(::exportHeapDump)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        preferenceScreen = screen

        val snapshot = category(screen, R.string.text_memory_snapshot)
        javaHeap = row(snapshot, R.string.text_memory_java_heap)
        nativeHeap = row(snapshot, R.string.text_memory_native_heap)
        pss = row(snapshot, R.string.text_memory_pss)
        engines = row(snapshot, R.string.text_memory_engines)
        retainedRuntimes = row(snapshot, R.string.text_memory_retained_runtimes)
        images = row(snapshot, R.string.text_memory_images)
        threads = row(snapshot, R.string.text_memory_threads)
        contexts = row(snapshot, R.string.text_memory_rhino_contexts)
        leakCanary = if (BuildConfig.DEBUG) row(snapshot, R.string.text_leakcanary) else null
        updatedAt = row(snapshot, R.string.text_memory_updated_at)

        retainedCategory = category(screen, R.string.text_memory_retained_runtimes)

        val actions = category(screen, R.string.text_operation)
        forceGc = action(actions, R.string.text_memory_force_gc, R.string.summary_memory_force_gc) { collect() }
        exportHeapDump = action(actions, R.string.text_memory_export_heap_dump, R.string.summary_memory_export_heap_dump) {
            createHeapDump.launch(heapDumpFileName())
        }
        action(actions, R.string.text_memory_copy_report, R.string.summary_memory_copy_report) { copyReport() }
        action(actions, R.string.text_process_exit_history, R.string.summary_process_exit_history) {
            startActivity(Intent(requireContext(), ProcessExitHistoryActivity::class.java))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.excludePaddingClippableViewFromBottomNavigationBar()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val context = requireContext()
                    val reading = withContext(Dispatchers.Default) { read(context) }
                    show(reading)
                    delay(REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    private class Reading(val snapshot: MemorySnapshot, val retained: List<RetainedRuntime>, val leakCanary: String?)

    private fun read(context: Context, snapshot: MemorySnapshot = MemoryDiagnostics.snapshot()) =
        Reading(snapshot, MemoryDiagnostics.retained(), MemoryDiagnostics.describeLeakCanary(context))

    private fun show(reading: Reading) {
        val context = context ?: return
        val s = reading.snapshot
        javaHeap.summary = MemoryDiagnosticsText.javaHeap(context, s)
        nativeHeap.summary = MemoryDiagnosticsText.nativeHeap(context, s)
        pss.summary = MemoryDiagnosticsText.bytes(s.pss)
        engines.summary = MemoryDiagnosticsText.count(s.engines)
        retainedRuntimes.summary = MemoryDiagnosticsText.count(s.retainedRuntimes)
        images.summary = MemoryDiagnosticsText.count(s.images)
        threads.summary = MemoryDiagnosticsText.threads(context, s)
        contexts.summary = MemoryDiagnosticsText.count(s.contexts)
        leakCanary?.summary = reading.leakCanary ?: "-"
        updatedAt.summary = getString(R.string.text_memory_updated_at_value, MemoryDiagnosticsText.time(s.timestamp), MemoryDiagnosticsText.seconds(REFRESH_INTERVAL_MS))
        showRetained(context, reading.retained)
    }

    private fun showRetained(context: Context, retained: List<RetainedRuntime>) {
        val ids = retained.map { it.id }
        if (ids != shownRetainedIds) {
            shownRetainedIds = ids
            retainedCategory.removeAll()
            if (retained.isEmpty()) {
                row(retainedCategory, R.string.text_none)
            }
            retained.forEach { row(retainedCategory, 0).title = it.name }
        }
        retained.forEachIndexed { index, runtime ->
            retainedCategory.getPreference(index).summary = MemoryDiagnosticsText.retained(context, runtime)
        }
    }

    private fun collect() {
        if (isCollecting) return
        isCollecting = true
        forceGc.isEnabled = false
        forceGc.summary = getString(R.string.text_memory_force_gc_running)
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val started = SystemClock.elapsedRealtime()
            val reading = withContext(Dispatchers.Default) { read(context, MemoryDiagnostics.collect()) }
            val elapsed = SystemClock.elapsedRealtime() - started
            isCollecting = false
            forceGc.isEnabled = true
            forceGc.summary = getString(R.string.text_memory_force_gc_done, MemoryDiagnosticsText.seconds(elapsed), MemoryDiagnosticsText.count(reading.snapshot.retainedRuntimes))
            show(reading)
        }
    }

    private fun exportHeapDump(uri: Uri) {
        if (isExporting) return
        isExporting = true
        exportHeapDump.isEnabled = false
        exportHeapDump.summary = getString(R.string.text_memory_export_heap_dump_running)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Dumped to the cache first: Debug.dumpHprofData needs a file path, the document is only a stream.
                    // zh-CN: 先转储到缓存目录: Debug.dumpHprofData 需要文件路径, 而文档只有输出流.
                    val temporary = File(appContext.cacheDir, "memory/heap-${SystemClock.elapsedRealtime()}.hprof")
                    try {
                        MemoryDiagnostics.dumpHeap(temporary)
                        val output = appContext.contentResolver.openOutputStream(uri) ?: throw IOException("No output stream for $uri")
                        output.use { out -> temporary.inputStream().use { it.copyTo(out) } }
                        temporary.length()
                    } finally {
                        temporary.delete()
                    }
                }
            }
            isExporting = false
            exportHeapDump.isEnabled = true
            exportHeapDump.summary = getString(R.string.summary_memory_export_heap_dump)
            result.onSuccess { size ->
                ViewUtils.showToast(appContext, getString(R.string.text_memory_export_heap_dump_done, MemoryDiagnosticsText.bytes(size)))
            }.onFailure { e ->
                ViewUtils.showToast(appContext, getString(R.string.text_memory_export_heap_dump_failed, e.message ?: e.javaClass.simpleName), true)
            }
        }
    }

    private fun copyReport() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val reading = withContext(Dispatchers.Default) { read(context) }
            ClipboardUtils.setClip(context, MemoryDiagnosticsText.report(context, reading.snapshot, reading.retained, reading.leakCanary))
            ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
            show(reading)
        }
    }

    private fun category(screen: PreferenceGroup, titleRes: Int): PreferenceCategory =
        ThemeColorPreferenceCategory(screen.context).apply {
            layoutResource = R.layout.preference_category_custom
            setTitle(titleRes)
            isIconSpaceReserved = false
        }.also(screen::addPreference)

    private fun row(group: PreferenceGroup, titleRes: Int): Preference =
        MaterialPreference(group.context).apply {
            layoutResource = R.layout.preference_custom
            if (titleRes != 0) setTitle(titleRes)
            isPersistent = false
            isSelectable = false
            isCopyingEnabled = true
            isIconSpaceReserved = false
        }.also(group::addPreference)

    private fun action(group: PreferenceGroup, titleRes: Int, summaryRes: Int, onClick: () -> Unit): Preference =
        MaterialPreference(group.context).apply {
            layoutResource = R.layout.preference_custom
            setTitle(titleRes)
            setSummary(summaryRes)
            isPersistent = false
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                onClick()
                true
            }
        }.also(group::addPreference)

    private fun heapDumpFileName() = "AutoJs6-heap-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.hprof"

    private companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
        const val HPROF_MIME_TYPE = "application/octet-stream"
    }

}
