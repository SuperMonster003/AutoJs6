package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File

class NodeDoctorReportPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        MaterialDialog.Builder(context)
            .title(R.string.text_nodejs_doctor)
            .items(
                listOf(
                    context.getString(R.string.text_nodejs_doctor_export_text),
                    context.getString(R.string.text_nodejs_doctor_export_json),
                ),
            )
            .itemsCallback { dialog, _, which, _ ->
                dialog.dismiss()
                generateAndSend(if (which == 0) ExportFormat.TEXT else ExportFormat.JSON)
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .widgetThemeColor()
            .showAdaptive()
    }

    private fun generateAndSend(format: ExportFormat) {
        val progress = MaterialDialog.Builder(context)
            .title(R.string.text_nodejs_doctor)
            .content(R.string.text_nodejs_doctor_generating)
            .progress(true, 0)
            .cancelable(false)
            .showAdaptive()
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { NodeDoctorReportGenerator.generate(context.applicationContext) }
            }
            progress.dismiss()
            result
                .onSuccess { reports -> sendReport(format.file(reports)) }
                .onFailure { error ->
                    ViewUtils.showToast(
                        context,
                        "${context.getString(R.string.text_nodejs_doctor_generation_failed)}: ${error.message.orEmpty()}",
                        true,
                    )
                }
        }
    }

    private fun sendReport(file: File) {
        if (!IntentUtils.sendFile(context, file.absolutePath)) {
            ViewUtils.showToast(context, R.string.error_no_applications_available_for_sending_this_file, true)
        }
    }

    private enum class ExportFormat {
        TEXT,
        JSON;

        fun file(reports: NodeDoctorReportGenerator.ReportFiles): File = when (this) {
            TEXT -> reports.textFile
            JSON -> reports.jsonFile
        }
    }
}
