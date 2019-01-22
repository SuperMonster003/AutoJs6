package org.autojs.autojs.ui.project

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import androidx.annotation.Nullable
import androidx.cardview.widget.CardView
import android.text.Editable
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast

import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.textfield.TextInputLayout
import com.stardust.autojs.project.ProjectConfig
import com.stardust.util.IntentUtil

import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.ViewById
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
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.Callable
import java.util.regex.Pattern
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by Stardust on 2017/10/22.
 */
@EActivity(R.layout.activity_build)
open class BuildActivity : BaseActivity(), ApkBuilder.ProgressCallback {

    @ViewById(R.id.source_path)
    internal var mSourcePath: EditText? = null

    @ViewById(R.id.source_path_container)
    internal var mSourcePathContainer: View? = null

    @ViewById(R.id.output_path)
    internal var mOutputPath: EditText? = null

    @ViewById(R.id.app_name)
    internal var mAppName: EditText? = null

    @ViewById(R.id.package_name)
    internal var mPackageName: EditText? = null

    @ViewById(R.id.version_name)
    internal var mVersionName: EditText? = null

    @ViewById(R.id.version_code)
    internal var mVersionCode: EditText? = null

    @ViewById(R.id.icon)
    internal var mIcon: ImageView? = null

    @ViewById(R.id.app_config)
    internal var mAppConfig: CardView? = null

    private var mProjectConfig: ProjectConfig? = null
    private var mProgressDialog: MaterialDialog? = null
    private var mSource: String? = null
    private var mIsDefaultIcon = true

    protected override fun onCreate(@Nullable savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
    }

    @AfterViews
    internal fun setupViews() {
        setToolbarAsBack(getString(R.string.text_build_apk))
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
                .onPositive { dialog, which -> downloadPlugin() }
                .onNegative { dialog, which -> if (finishIfCanceled) finish() }
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
        mOutputPath!!.setText(dir)
        mAppName!!.setText(file.simplifiedName)
        mPackageName!!.setText(getString(R.string.format_default_package_name, System.currentTimeMillis()))
        setSource(file)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

    }

    @Click(R.id.select_source)
    internal fun selectSourceFilePath() {
        val initialDir = File(mSourcePath!!.text.toString()).parent
        FileChooserDialogBuilder(this)
                .title(R.string.text_source_file_path)
                .dir(Environment.getExternalStorageDirectory().path,
                        initialDir ?: Pref.getScriptDirPath())
                .singleChoice(SingleChoiceCallback { this.setSource(it) })
                .show()
    }

    private fun setSource(file: File) {
        if (!file.isDirectory) {
            mSourcePath!!.setText(file.path)
            return
        }
        mProjectConfig = ProjectConfig.fromProjectDir(file.path)
        if (mProjectConfig == null) {
            return
        }
        mOutputPath!!.setText(File(mSource, mProjectConfig!!.buildDir).path)
        mAppConfig!!.visibility = View.GONE
        mSourcePathContainer!!.visibility = View.GONE
    }

    @Click(R.id.select_output)
    internal fun selectOutputDirPath() {
        val initialDir = if (File(mOutputPath!!.text.toString()).exists())
            mOutputPath!!.text.toString()
        else
            Pref.getScriptDirPath()
        FileChooserDialogBuilder(this)
                .title(R.string.text_output_apk_path)
                .dir(initialDir)
                .chooseDir()
                .singleChoice { dir -> mOutputPath!!.setText(dir.path) }
                .show()
    }

    @Click(R.id.icon)
    internal fun selectIcon() {
        ShortcutIconSelectActivity_.intent(this)
                .startForResult(REQUEST_CODE)
    }

    @Click(R.id.fab)
    internal fun buildApk() {
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
        inputValid = inputValid and checkNotEmpty(mSourcePath!!)
        inputValid = inputValid and checkNotEmpty(mOutputPath!!)
        inputValid = inputValid and checkNotEmpty(mAppName!!)
        inputValid = inputValid and checkNotEmpty(mSourcePath!!)
        inputValid = inputValid and checkNotEmpty(mVersionCode!!)
        inputValid = inputValid and checkNotEmpty(mVersionName!!)
        inputValid = inputValid and checkPackageNameValid(mPackageName!!)
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
        val outApk = File(mOutputPath!!.text.toString(),
                String.format("%s_v%s.apk", appConfig.appName, appConfig.versionName))
        showProgressDialog()
        Observable.fromCallable { callApkBuilder(tmpDir, outApk, appConfig) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ apkBuilder -> onBuildSuccessful(outApk) },
                        Consumer<Throwable> { this.onBuildFailed(it) })
    }

    private fun createAppConfig(): ApkBuilder.AppConfig {
        if (mProjectConfig != null) {
            return ApkBuilder.AppConfig.fromProjectConfig(mSource, mProjectConfig!!)
        }
        val jsPath = mSourcePath!!.text.toString()
        val versionName = mVersionName!!.text.toString()
        val versionCode = Integer.parseInt(mVersionCode!!.text.toString())
        val appName = mAppName!!.text.toString()
        val packageName = mPackageName!!.text.toString()
        return ApkBuilder.AppConfig()
                .setAppName(appName)
                .setSourcePath(jsPath)
                .setPackageName(packageName)
                .setVersionCode(versionCode)
                .setVersionName(versionName)
                .setIcon(if (mIsDefaultIcon)
                    null
                else {
                    BitmapTool.drawableToBitmap(mIcon!!.drawable)
                } as Callable<Bitmap>
                )
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
        mProgressDialog = MaterialDialog.Builder(this)
                .progress(true, 100)
                .content(R.string.text_on_progress)
                .cancelable(false)
                .show()
    }

    private fun onBuildFailed(error: Throwable) {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
            mProgressDialog = null
        }
        Toast.makeText(this, getString(R.string.text_build_failed) + error.message, Toast.LENGTH_SHORT).show()
        Log.e(LOG_TAG, "Build failed", error)
    }

    private fun onBuildSuccessful(outApk: File) {
        mProgressDialog!!.dismiss()
        mProgressDialog = null
        MaterialDialog.Builder(this)
                .title(R.string.text_build_successfully)
                .content(getString(R.string.format_build_successfully, outApk.path))
                .positiveText(R.string.text_install)
                .negativeText(R.string.cancel)
                .onPositive { dialog, which -> IntentUtil.installApkOrToast(this@BuildActivity, outApk.path, AppFileProvider.AUTHORITY) }
                .show()

    }

    override fun onPrepare(builder: ApkBuilder) {
        mProgressDialog!!.setContent(R.string.apk_builder_prepare)
    }

    override fun onBuild(builder: ApkBuilder) {
        mProgressDialog!!.setContent(R.string.apk_builder_build)

    }

    override fun onSign(builder: ApkBuilder) {
        mProgressDialog!!.setContent(R.string.apk_builder_package)

    }

    override fun onClean(builder: ApkBuilder) {
        mProgressDialog!!.setContent(R.string.apk_builder_clean)
    }

    @SuppressLint("CheckResult")
    protected override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        ShortcutIconSelectActivity.getBitmapFromIntent(applicationContext, data)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ bitmap ->
                    mIcon!!.setImageBitmap(bitmap)
                    mIsDefaultIcon = false
                }, Consumer<Throwable> { it.printStackTrace() })

    }

    companion object {

        private val REQUEST_CODE = 44401

        val EXTRA_SOURCE = BuildActivity::class.java.name + ".extra_source_file"

        private val LOG_TAG = "BuildActivity"
        private val REGEX_PACKAGE_NAME = Pattern.compile("^([A-Za-z][A-Za-z\\d_]*\\.)+([A-Za-z][A-Za-z\\d_]*)$")
    }

}
