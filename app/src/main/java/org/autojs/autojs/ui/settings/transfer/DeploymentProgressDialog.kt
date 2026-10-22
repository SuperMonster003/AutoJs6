package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.flow.MutableStateFlow
import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs6.R
import kotlin.math.roundToInt

/**
 * The deployment items one import runs (docs/settings/ROADMAP.md T18, P7.6): one [Row] per item, updated as they run
 * one after another; [cancelRequested] gives up the items that have not started (they keep the current values, R8).
 * zh-CN: 一次导入运行的部署项 (ROADMAP T18, P7.6): 每项一个 [Row], 按顺序运行并随之更新; [cancelRequested] 放弃尚未开始的项 (保持当前值, R8).
 */
class DeploymentRun(rows: List<Row>) {

    enum class Status { WAITING, RUNNING, DONE, FAILED, CANCELLED }

    data class Row(val kind: DeploymentKind, val target: String, val status: Status = Status.WAITING, val fraction: Float? = null)

    val rows = MutableStateFlow(rows)

    @Volatile
    var cancelRequested = false

    fun update(index: Int, status: Status, fraction: Float? = null) {
        rows.value = rows.value.mapIndexed { i, row -> if (i == index) row.copy(status = status, fraction = fraction) else row }
    }

    /** The share of the work done, 0 to 1. zh-CN: 已完成的比例, 0 到 1. */
    val progress: Float
        get() = rows.value.let { list ->
            if (list.isEmpty()) 1f
            else list.sumOf { row ->
                when (row.status) {
                    Status.WAITING -> 0.0
                    Status.RUNNING -> (row.fraction ?: 0f).toDouble()
                    else -> 1.0
                }
            }.toFloat() / list.size
        }
}

/**
 * The progress dialog of the deployment items (T18): every item with its target and state, the total progress, and
 * "Cancel the rest", which gives up the items that have not started. A failure does not stop the others; the result
 * page lists the failures with their reasons.
 * zh-CN: 部署项的进度对话框 (T18): 列出每项的目标与状态及总进度, "取消其余项" 放弃尚未开始的项. 某项失败不影响其他项, 失败原因在结果页列出.
 */
class DeploymentProgressDialog(context: Context, @StringRes titleRes: Int, private val run: DeploymentRun) {

    private val context = context
    private val lines = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
    private val dialog: MaterialDialog

    init {
        val padding = DisplayUtils.dpToPx(24f).roundToInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(lines, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = padding / 2 })
        }
        dialog = MaterialDialog.Builder(context)
            .title(titleRes)
            .customView(root, true)
            .widgetThemeColor()
            .cancelable(false)
            .autoDismiss(false)
            .negativeText(R.string.settings_transfer_deploy_cancel_rest)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ ->
                run.cancelRequested = true
                dialog.getActionButton(DialogAction.NEGATIVE).isEnabled = false
            }
            .show()
        render(run.rows.value)
    }

    /** Follows the items until the coroutine is cancelled. zh-CN: 跟随部署项的变化, 直到协程被取消. */
    suspend fun follow() {
        run.rows.collect { render(it) }
    }

    fun dismiss() = dialog.dismiss()

    private fun render(rows: List<DeploymentRun.Row>) {
        while (lines.childCount < rows.size) {
            lines.addView(TextView(context).apply { setTextColor(context.getColor(R.color.text_color_primary)); textSize = 14f; setPadding(0, 6, 0, 6) })
        }
        rows.forEachIndexed { index, row ->
            val status = when (row.status) {
                DeploymentRun.Status.WAITING -> context.getString(R.string.settings_transfer_deploy_waiting)
                DeploymentRun.Status.RUNNING -> context.getString(R.string.settings_transfer_deploy_running) +
                        (row.fraction?.let { " ${(it * 100).roundToInt()}%" } ?: "")
                DeploymentRun.Status.DONE -> context.getString(R.string.settings_transfer_deploy_done)
                DeploymentRun.Status.FAILED -> context.getString(R.string.settings_transfer_deploy_failed)
                DeploymentRun.Status.CANCELLED -> context.getString(R.string.settings_transfer_deploy_cancelled)
            }
            (lines.getChildAt(index) as TextView).text = context.getString(SettingsTransferMessages.deploymentRes(row.kind)) + "\n" + row.target + "\n" + status
        }
        bar.progress = (run.progress * 100).roundToInt()
        if (rows.none { it.status == DeploymentRun.Status.WAITING }) dialog.getActionButton(DialogAction.NEGATIVE).isEnabled = false
    }
}
