package org.autojs.autojs.ui.project

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.textfield.TextInputLayout
import com.stardust.autojs.project.ProjectConfig
import com.stardust.util.IntentUtil
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_build.*
import org.autojs.autojs.Pref
import org.autojs.autojs.R
import org.autojs.autojs.autojs.build.ApkBuilder
import org.autojs.autojs.build.ApkBuilderPluginHelper
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.theme.dialog.ThemeColorMaterialDialogBuilder
import org.autojs.autojs.tool.BitmapTool
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.ui.shortcut.ShortcutIconSelectActivity
import org.autojs.autojs.ui.shortcut.ShortcutIconSelectActivity_
import java.io.File
import java.util.*
import java.util.regex.Pattern

class BuildActivity : BaseActivity(), ApkBuilder.ProgressCallback {

    private var mProjectConfig: ProjectConfig? = null
    private var mProgressDialog: MaterialDialog? = null
    private var mSource: String? = null
    private var mIsDefaultIcon = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_build)
        setupViews()
    }

    private fun setupViews() {
        setToolbarAsBack(getString(R.string.text_build_apk))
        buttonSelectSource.setOnClickListener { selectSourceFilePath() }
        imageIcon.setOnClickListener { selectIcon() }
        fab.setOnClickListener { buildApk() }
        selectOutput.setOnClickListener { selectOutputDirPath() }
        mSource = intent.getStringExtra(EXTRA_SOURCE)
        if (mSource != null) {
            setupWithSourceFile(ScriptFile(mSource))
        }
        checkApkBuilderPlugin()
    }

    private fun checkApkBuilderPlugin() {
        if (!ApkBuilderPluginHelper.isPluginAvailable(this)) {
            showPluginDownloadDialog(R.string.no_apk_builder_plugin, true)
            return
        }
        val version = ApkBuilderPluginHelper.getPluginVersion(this)
        if (version < 0) {
            showPluginDownloadDialog(R.string.no_apk_builder_plugin, true)
            return
        }
        if (version < ApkBuilderPluginHelper.getSuitablePluginVersion()) {
            showPluginDownloadDialog(R.string.apk_builder_plugin_version_too_low, false)
        }
    }

    private fun showPluginDownloadDialog(msgRes: Int, finishIfCanceled: Boolean) {
        ThemeColorMaterialDialogBuilder(this)
                .content(msgRes)
                .positiveText(R.string.ok)
                .negativeText(R.string.cancel)
                .onPositive { _, _ -> downloadPlugin() }
                .onNegative { _, _ -> if (finishIfCanceled) finish() }
                .show()

    }

    private fun downloadPlugin() {
        IntentUtil.browse(this, String.format(Locale.getDefault(),
                "https://i.autojs.org/autojs/plugin/%d.apk", ApkBuilderPluginHelper.getSuitablePluginVersion()))
    }

    private fun setupWithSourceFile(file: ScriptFile) {
        var dir = file.parent
        if (dir.startsWith(filesDir.path)) {
            dir = Pref.getScriptDirPath()
        }
        inputOutputPath.setText(dir)
        inputAppName.setText(file.simplifiedName)
        inputPackageName.setText(getString(R.string.format_default_package_name, System.currentTimeMillis()))
        setSource(file)
    }

    private fun selectSourceFilePath() {
        val initialDir = File(inputSourcePath.text.toString()).parent
        FileChooserDialogBuilder(this)
                .title(R.string.text_source_file_path)
                .dir(Environment.getExternalStorageDirectory().path,
                        initialDir ?: Pref.getScriptDirPath())
                .singleChoice { this.setSource(it) }
                .show()
    }

    private fun setSource(file: File) {
        if (!file.isDirectory) {
            inputSourcePath.setText(file.path)
            return
        }
        mProjectConfig = ProjectConfig.fromProjectDir(file.path)
        if (mProjectConfig == null) {
            return
        }
        inputOutputPath.setText(File(mSource, mProjectConfig!!.buildDir).path)
        appConfig.visibility = View.GONE
        containerSourcePath.visibility = View.GONE
    }

    private fun selectOutputDirPath() {
        val initialDir = if (File(inputOutputPath.text.toString()).exists())
            inputOutputPath.text.toString()
        else
            Pref.getScriptDirPath()
        FileChooserDialogBuilder(this)
                .title(R.string.text_output_apk_path)
                .dir(initialDir)
                .chooseDir()
                .singleChoice { dir -> inputOutputPath.setText(dir.path) }
                .show()
    }

    private fun selectIcon() {
        ShortcutIconSelectActivity_.intent(this)
                .startForResult(REQUEST_CODE)
    }

    private fun buildApk() {
        if (!ApkBuilderPluginHelper.isPluginAvailable(this)) {
            Toast.makeText(this, R.string.text_apk_builder_plugin_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (!checkInputs()) {
            return
        }
        doBuildingApk()
    }

    private fun checkInputs(): Boolean {
        var inputValid = true
        inputValid = inputValid and checkNotEmpty(inputSourcePath)
        inputValid = inputValid and checkNotEmpty(inputOutputPath)
        inputValid = inputValid and checkNotEmpty(inputAppName)
        inputValid = inputValid and checkNotEmpty(inputSourcePath)
        inputValid = inputValid and checkNotEmpty(inputVersionCode)
        inputValid = inputValid and checkNotEmpty(inputVersionName!!)
        inputValid = inputValid and checkPackageNameValid(inputPackageName)
        return inputValid
    }

    private fun checkPackageNameValid(editText: EditText): Boolean {
        val text = editText.text
        val hint = (editText.parent.parent as TextInputLayout).hint.toString()
        if (TextUtils.isEmpty(text)) {
            editText.error = hint + getString(R.string.text_should_not_be_empty)
            return false
        }
        if (!REGEX_PACKAGE_NAME.matcher(text).matches()) {
            editText.error = getString(R.string.text_invalid_package_name)
            return false
        }
        return true

    }

    private fun checkNotEmpty(editText: EditText): Boolean {
        if (!TextUtils.isEmpty(editText.text) || !editText.isShown)
            return true
        // TODO: 2017/12/8 more beautiful ways?
        val hint = (editText.parent.parent as TextInputLayout).hint.toString()
        editText.error = hint + getString(R.string.text_should_not_be_empty)
        return false
    }

    @SuppressLint("CheckResult")
    private fun doBuildingApk() {
        val appConfig = createAppConfig()
        val tmpDir = File(cacheDir, "build/")
        val outApk = File(inputOutputPath.text.toString(),
                String.format("%s_v%s.apk", appConfig.appName, appConfig.versionName))
        showProgressDialog()
        Observable.fromCallable { callApkBuilder(tmpDir, outApk, appConfig) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ onBuildSuccessful(outApk) }, { this.onBuildFailed(it) })
    }

    private fun createAppConfig(): ApkBuilder.AppConfig {
        if (mProjectConfig != null) {
            return ApkBuilder.AppConfig.fromProjectConfig(mSource, mProjectConfig!!)
        }
        val jsPath = inputSourcePath.text.toString()
        val versionName = inputVersionName.text.toString()
        val versionCode = Integer.parseInt(inputVersionCode.text.toString())
        val appName = inputAppName.text.toString()
        val packageName = inputPackageName.text.toString()
        return ApkBuilder.AppConfig()
                .setAppName(appName)
                .setSourcePath(jsPath)
                .setPackageName(packageName)
                .setVersionCode(versionCode)
                .setVersionName(versionName)
                .setIcon {
                    if (mIsDefaultIcon)
                        null
                    else {
                        BitmapTool.drawableToBitmap(imageIcon.drawable)
                    }
                }
    }

    @Throws(Exception::class)
    private fun callApkBuilder(tmpDir: File, outApk: File, appConfig: ApkBuilder.AppConfig): ApkBuilder {
        val templateApk = ApkBuilderPluginHelper.openTemplateApk(this@BuildActivity)
        return ApkBuilder(templateApk, outApk, tmpDir.path)
                .setProgressCallback(this@BuildActivity)
                .prepare()
                .withConfig(appConfig)
                .build()
                .sign()
                .cleanWorkspace()
    }

    private fun showProgressDialog() {
        mProgressDialog?.dismiss()
        mProgressDialog = MaterialDialog.Builder(this)
                .progress(true, 100)
                .content(R.string.text_on_progress)
                .cancelable(false)
                .show()
    }

    private fun onBuildFailed(error: Throwable) {
        mProgressDialog?.dismiss()
        mProgressDialog = null
        Toast.makeText(this, getString(R.string.text_build_failed) + error.message, Toast.LENGTH_SHORT).show()
        Log.e(LOG_TAG, "Build failed", error)
    }

    private fun onBuildSuccessful(outApk: File) {
        mProgressDialog?.dismiss()
        mProgressDialog = null
        MaterialDialog.Builder(this)
                .title(R.string.text_build_successfully)
                .content(getString(R.string.format_build_successfully, outApk.path))
                .positiveText(R.string.text_install)
                .negativeText(R.string.cancel)
                .onPositive { _, _ -> IntentUtil.installApkOrToast(this@BuildActivity, outApk.path, AppFileProvider.AUTHORITY) }
                .show()

    }

    override fun onPrepare(builder: ApkBuilder) {
        mProgressDialog?.setContent(R.string.apk_builder_prepare)
    }

    override fun onBuild(builder: ApkBuilder) {
        mProgressDialog?.setContent(R.string.apk_builder_build)

    }

    override fun onSign(builder: ApkBuilder) {
        mProgressDialog?.setContent(R.string.apk_builder_package)

    }

    override fun onClean(builder: ApkBuilder) {
        mProgressDialog?.setContent(R.string.apk_builder_clean)
    }

    @SuppressLint("CheckResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        ShortcutIconSelectActivity.getBitmapFromIntent(applicationContext, data)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ bitmap ->
                    imageIcon.setImageBitmap(bitmap)
                    mIsDefaultIcon = false
                }, { it.printStackTrace() })

    }

    companion object {

        private const val REQUEST_CODE = 44401

        val EXTRA_SOURCE = BuildActivity::class.java.name + ".extra_source_file"

        private const val LOG_TAG = "BuildActivity"
        private val REGEX_PACKAGE_NAME = Pattern.compile("^([A-Za-z][A-Za-z\\d_]*\\.)+([A-Za-z][A-Za-z\\d_]*)$")
    }

}
