package org.autojs.autojs.ui.error

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.runtime.api.modules.util.VersionCodesInfo.briefOfCurrentVersionInt
import org.autojs.autojs.tool.ProcessExitHistory
import org.autojs.autojs.tool.ProcessExitHistory.Category
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityProcessExitHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Manual entry only: system history includes routine stops as well as crashes. */
class ProcessExitHistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityProcessExitHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProcessExitHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(R.string.text_process_exit_history)
        binding.copy.setOnClickListener { ClipboardUtils.setClip(this, binding.report.text.toString()) }
        binding.refresh.setOnClickListener { refresh() }
        refresh()
    }

    private fun refresh() {
        binding.refresh.isEnabled = false
        binding.copy.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        val appContext = applicationContext
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ProcessExitHistory.read(appContext) }
            binding.report.text = render(result)
            binding.progress.visibility = View.GONE
            binding.refresh.isEnabled = true
            binding.copy.isEnabled = result is ProcessExitHistory.Result.Available
        }
    }

    private fun render(result: ProcessExitHistory.Result): String = buildString {
        appendLine(getString(R.string.text_process_exit_history_current_app, getString(R.string.app_name), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        appendLine(briefOfCurrentVersionInt(true))
        appendLine(packageName)
        appendLine()
        appendLine(getString(R.string.text_process_exit_history_scope, ProcessExitHistory.MAX_RECORDS, ProcessExitHistory.MAX_AGE_DAYS))
        appendLine()
        when (result) {
            ProcessExitHistory.Result.Unsupported -> appendLine(getString(R.string.text_process_exit_history_unsupported))
            ProcessExitHistory.Result.Failed -> appendLine(getString(R.string.text_process_exit_history_failed))
            is ProcessExitHistory.Result.Available -> {
                if (result.records.isEmpty()) appendLine(getString(R.string.text_process_exit_history_empty))
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault())
                result.records.forEach { record ->
                    appendLine(getString(
                        R.string.text_process_exit_history_record,
                        dateFormat.format(Date(record.timestamp)),
                        record.processName,
                        record.pid,
                        getString(record.category.label),
                        record.reasonName,
                        record.reason,
                        record.status,
                        record.pss,
                        record.rss,
                        record.description?.takeIf { it.isNotBlank() }?.let { if (it.length > 4096) it.take(4096) + "..." else it }
                            ?: getString(R.string.text_process_exit_history_no_description),
                    ))
                    appendLine()
                }
            }
        }
    }

    private val Category.label: Int
        get() = when (this) {
            Category.MEMORY_LIMITER -> R.string.text_process_exit_history_memory_limiter
            Category.CRASH -> R.string.text_process_exit_history_crash
            Category.ANR -> R.string.text_process_exit_history_anr
            Category.LOW_MEMORY -> R.string.text_process_exit_history_low_memory
            Category.SELF_EXIT -> R.string.text_process_exit_history_self_exit
            Category.USER_STOP -> R.string.text_process_exit_history_user_stop
            Category.SYSTEM -> R.string.text_process_exit_history_system
            Category.UNKNOWN -> R.string.text_process_exit_history_unknown
        }
}
