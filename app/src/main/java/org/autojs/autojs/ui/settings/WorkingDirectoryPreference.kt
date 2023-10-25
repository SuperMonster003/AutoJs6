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
import androidx.preference.Preference.SummaryProvider
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs.storage.file.FileObservable
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.tool.SimpleObserver
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.EnvironmentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import java.io.File

class WorkingDirectoryPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    init {
        summaryProvider = SummaryProvider<WorkingDirectoryPreference> { WorkingDirectoryUtils.relativePath }
    }

    override fun onClick() {
        WorkingDirectoryDialogBuilder().apply {
            onFinish = { notifyChanged() }
            show()
        }
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

        var onFinish = {}

        init {
            build()
        }

        override fun build(): MaterialDialog = MaterialDialog.Builder(context)
            .title(R.string.text_change_working_dir)
            .customView(R.layout.pref_working_directory, false)
            .neutralText(R.string.dialog_button_history)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ ->
                MaterialDialog.Builder(context)
                    .title(R.string.text_histories)
                    .content(R.string.text_no_histories)
                    .items(WorkingDirectoryUtils.histories)
                    .itemsCallback { dHistories, _, _, text ->
                        dHistories.dismiss()
                        mContentView.setText(text)
                    }
                    .itemsLongCallback { dHistories, _, _, text ->
                        false.also {
                            MaterialDialog.Builder(context)
                                .title(R.string.text_prompt)
                                .content(R.string.text_confirm_to_delete)
                                .negativeText(R.string.dialog_button_cancel)
                                .positiveText(R.string.dialog_button_confirm)
                                .positiveColorRes(R.color.dialog_button_caution)
                                .onPositive { ds, _ ->
                                    ds.dismiss()
                                    WorkingDirectoryUtils.removeFromHistories(text)
                                    dHistories.items?.let {
                                        it.remove(text)
                                        dHistories.notifyItemsChanged()
                                        DialogUtils.toggleContentViewByItems(dHistories)
                                    }
                                }
                                .show()
                        }
                    }
                    .neutralText(R.string.dialog_button_use_default)
                    .neutralColorRes(R.color.dialog_button_reset)
                    .onNeutral { dHistories, _ ->
                        val paths = WorkingDirectoryUtils.getRecommendedDefaultPaths()
                        if (paths.size == 1) {
                            mContentView.setText(paths.first())
                            dHistories.dismiss()
                        } else {
                            MaterialDialog.Builder(context)
                                .title(R.string.text_multiple_options)
                                .items(paths)
                                .itemsCallback { _, _, _, text ->
                                    true.also {
                                        dHistories.dismiss()
                                        mContentView.setText(text)
                                    }
                                }
                                .negativeText(R.string.dialog_button_cancel)
                                .show()
                        }
                    }
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .onNegative { dHistories, _ -> dHistories.dismiss() }
                    .autoDismiss(false)
                    .show()
                    .also { DialogUtils.toggleContentViewByItems(it) }
            }
            .negativeText(R.string.dialog_button_cancel)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .onPositive { dialog, _ ->
                val inputPath = mContentView.text.toString()
                WorkingDirectoryUtils.addIntoHistories(inputPath)
                if (isDirPathChanged(inputPath)) {
                    if (mSwitchView.isChecked) {
                        transfer(mPrefFullPath, inputPath)
                    }
                    WorkingDirectoryUtils.path = inputPath
                    MainActivity.shouldRecreateMainActivity = true
                }
                dialog.dismiss()
                onFinish()
            }
            .autoDismiss(false)
            .build()
            .apply {
                customView!!.let {
                    mRadioGroupView = it.findViewById(R.id.md_contentRadioGroup)
                    mFileTransferView = it.findViewById(R.id.md_contentFileTransfer)
                    mFileChooserIconView = it.findViewById<ImageView?>(R.id.md_FileChooserIcon).apply {
                        setOnClickListener {
                            val initialDir: String? = try {
                                File(toFullPath(mContentView.text.toString())).path
                            } catch (ignore: Exception) {
                                mPrefFullPath
                            }
                            FileChooserDialogBuilder(context)
                                .title(R.string.text_working_dir_path)
                                .dir(mExtStoragePath, initialDir ?: WorkingDirectoryUtils.path)
                                .chooseDir()
                                .singleChoice { file -> mContentView.setText(WorkingDirectoryUtils.toRelativePath(file.path)) }
                                .show()
                        }
                    }
                    mContentView = it.findViewById<EditText?>(R.id.md_contentPath).apply {
                        setText(WorkingDirectoryUtils.relativePath)
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                                // Ignored.
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
                    mSwitchView = it.findViewById<SwitchCompat?>(R.id.md_contentSwitch).apply {
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
                else -> ViewUtils.showToast(context, R.string.error_unknown_operation, true)
            }
            fileObservable?.let { showFileProgressDialog(it) }
        }

        private fun showFileProgressDialog(observable: Observable<File>) {
            val dialog = MaterialDialog.Builder(context)
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
                        context.getString(R.string.text_error_copy_file, e.message).let {
                            ViewUtils.showToast(context, it, true)
                        }
                    }

                    override fun onComplete() {
                        dialog.dismiss()
                        ViewUtils.showToast(context, R.string.text_operation_is_completed)
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