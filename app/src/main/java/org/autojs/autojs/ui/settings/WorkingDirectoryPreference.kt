package org.autojs.autojs.ui.settings

import android.content.Context
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.SwitchCompat
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.storage.file.FileObservable
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.tool.SimpleObserver
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.EnvironmentUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import java.io.File

class WorkingDirectoryPreference : MaterialPreference {

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    init {
        summaryProvider = SummaryProvider<WorkingDirectoryPreference> { WorkingDirectoryUtils.relativePath }
    }

    override fun onClick() {
        WorkingDirectoryDialogBuilder().showAdaptive()
        super.onClick()
    }

    inner class WorkingDirectoryDialogBuilder : MaterialDialog.Builder(prefContext) {

        private lateinit var mRadioGroupView: RadioGroup
        private lateinit var mContentView: EditText
        private lateinit var mSwitchView: SwitchCompat
        private lateinit var mFileChooserIconView: ImageView
        private lateinit var mFileTransferView: LinearLayoutCompat

        private val mPrefFullPath = WorkingDirectoryUtils.path
        private val mExtStoragePath = EnvironmentUtils.externalStoragePath

        init {
            build()
        }

        override fun build(): MaterialDialog = MaterialDialog.Builder(prefContext)
            .title(R.string.text_working_dir_path)
            .options(
                listOf(
                    MaterialDialog.OptionMenuItemSpec(prefContext.getString(R.string.dialog_button_use_default)) {
                        val paths = WorkingDirectoryUtils.getRecommendedDefaultPaths()
                        when (paths.size) {
                            1 -> mContentView.setText(paths.first())
                            else -> MaterialDialog.Builder(prefContext)
                                .title(R.string.text_multiple_options)
                                .items(paths)
                                .itemsCallback { _, _, _, text ->
                                    mContentView.setText(text)
                                }
                                .choiceWidgetThemeColor()
                                .negativeText(R.string.dialog_button_back)
                                .showAdaptive()
                        }
                    },
                    MaterialDialog.OptionMenuItemSpec(prefContext.getString(R.string.dialog_button_details)) {
                        MaterialDialog.Builder(prefContext)
                            .title(R.string.text_working_dir_path)
                            .content(R.string.description_change_working_dir_preference)
                            .positiveText(R.string.dialog_button_dismiss)
                            .positiveColorRes(R.color.dialog_button_default)
                            .showAdaptive()
                    },
                )
            )
            .customView(R.layout.pref_working_directory, false)
            .neutralText(R.string.dialog_button_history)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ ->
                MaterialDialog.Builder(prefContext)
                    .title(R.string.text_history)
                    .content(R.string.text_no_history)
                    .items(WorkingDirectoryUtils.history)
                    .itemsCallback { dHistory, _, _, text ->
                        dHistory.dismiss()
                        mContentView.setText(text)
                    }
                    .itemsLongCallback { dHistory, _, _, text ->
                        false.also {
                            MaterialDialog.Builder(prefContext)
                                .title(R.string.text_prompt)
                                .content(R.string.text_confirm_to_delete)
                                .negativeText(R.string.dialog_button_cancel)
                                .positiveText(R.string.dialog_button_confirm)
                                .positiveColorRes(R.color.dialog_button_caution)
                                .onPositive { ds, _ ->
                                    ds.dismiss()
                                    WorkingDirectoryUtils.removeFromHistory(text)
                                    dHistory.items?.let {
                                        it.remove(text)
                                        dHistory.notifyItemsChanged()
                                        DialogUtils.toggleContentViewByItems(dHistory)
                                    }
                                }
                                .showAdaptive()
                        }
                    }
                    .choiceWidgetThemeColor()
                    .negativeText(R.string.dialog_button_back)
                    .negativeColorRes(R.color.dialog_button_default)
                    .onNegative { dHistory, _ -> dHistory.dismiss() }
                    .autoDismiss(false)
                    .showAdaptive()
                    .also { DialogUtils.toggleContentViewByItems(it) }
            }
            .negativeText(R.string.dialog_button_cancel)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { dialog, _ ->
                val inputPath = mContentView.text.toString()
                WorkingDirectoryUtils.addIntoHistory(inputPath)
                if (isDirPathChanged(inputPath)) {
                    if (mSwitchView.isChecked) {
                        transfer(mPrefFullPath, inputPath)
                    }
                    WorkingDirectoryUtils.path = inputPath
                    MainActivity.shouldRecreateMainActivity = true
                }
                dialog.dismiss()
                notifyChanged()
            }
            .autoDismiss(false)
            .build()
            .apply {
                customView!!.let {
                    mRadioGroupView = it.findViewById(R.id.md_contentRadioGroup)
                    mFileTransferView = it.findViewById(R.id.md_contentFileTransfer)
                    mFileChooserIconView = it.findViewById<ImageView>(R.id.md_FileChooserIcon).apply {
                        setOnClickListener {
                            val initialDir: String? = try {
                                File(toFullPath(mContentView.text.toString())).path
                            } catch (_: Exception) {
                                mPrefFullPath
                            }
                            FileChooserDialogBuilder(prefContext)
                                .title(R.string.text_working_dir_path)
                                .dir(mExtStoragePath, initialDir ?: WorkingDirectoryUtils.path)
                                .chooseDir()
                                .singleChoice { file -> mContentView.setText(WorkingDirectoryUtils.toRelativePath(file.path)) }
                                .show()
                        }
                    }
                    mContentView = it.findViewById<EditText>(R.id.md_contentPath).apply {
                        setText(WorkingDirectoryUtils.relativePath)
                        ThemeColorHelper.setThemeColorPrimary(this, true)
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                                /* Ignored. */
                            }

                            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                                mFileTransferView.visibility = when (isDirPathChanged(s)) {
                                    true -> View.VISIBLE
                                    else -> View.GONE
                                }
                            }

                            override fun afterTextChanged(s: Editable) {
                                setSelection(s.length)
                            }
                        })
                    }
                    mSwitchView = it.findViewById<SwitchCompat>(R.id.md_contentSwitch).apply {
                        setOnCheckedChangeListener { _, isChecked ->
                            mRadioGroupView.visibility = when (isChecked) {
                                true -> View.VISIBLE
                                else -> View.GONE
                            }
                        }
                    }
                }
            }

        private fun transfer(srcPath: String, dstPath: String) {
            var fileObservable: Observable<File>? = null
            when (mRadioGroupView.checkedRadioButtonId) {
                R.id.copy -> fileObservable = FileObservable.copy(srcPath, toFullPath(dstPath))
                R.id.move -> fileObservable = FileObservable.move(srcPath, toFullPath(dstPath))
                else -> ViewUtils.showToast(prefContext, R.string.error_unknown_operation, true)
            }
            fileObservable?.let { showFileProgressDialog(it) }
        }

        private fun showFileProgressDialog(observable: Observable<File>) {
            val dialog = MaterialDialog.Builder(prefContext)
                .progress(true, 0)
                .progressIndeterminateStyle(true)
                .title(R.string.text_in_progress)
                .cancelable(false)
                .content("")
                .show()
                .also {
                    it.contentView?.apply {
                        textSize = 15f
                        setLines(3)
                        maxLines = 3
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER_VERTICAL
                    }
                }
            observable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : SimpleObserver<File?>() {
                    override fun onNext(file: File) {
                        dialog.setContent(file.path.replaceFirst(mExtStoragePath, ""))
                    }

                    override fun onError(e: Throwable) {
                        e.printStackTrace()
                        dialog.dismiss()
                        prefContext.getString(R.string.text_error_copy_file, e.message).let {
                            ViewUtils.showToast(prefContext, it, true)
                        }
                    }

                    override fun onComplete() {
                        dialog.dismiss()
                        ViewUtils.showToast(prefContext, R.string.text_operation_is_completed)
                    }
                })
        }

        private fun isDirPathChanged(inputPath: CharSequence) = isDirPathChanged(inputPath.toString())

        private fun isDirPathChanged(inputPath: String) = toFullPath(inputPath) != mPrefFullPath

        private fun toFullPath(path: String) = when (path.startsWith(mExtStoragePath)) {
            true -> path
            else -> File(mExtStoragePath, path).path
        }

    }

}