package org.autojs.autojs.ui.project

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.drawable.toBitmapOrNull
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputLayout
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.project.ProjectTemplate
import org.autojs.autojs.pio.PFiles.ensureDir
import org.autojs.autojs.pio.PFiles.write
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.shortcut.AppsIconSelectActivity
import org.autojs.autojs.ui.widget.SimpleTextWatcher
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityProjectConfigBinding
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

/**
 * Modified by SuperMonster003 as of Mar 20, 2022.
 * Transformed by SuperMonster003 on May 13, 2023.
 */
class ProjectConfigActivity : BaseActivity() {

    private lateinit var mProjectLocation: EditText
    private lateinit var mProjectLocationWrapper: LinearLayout
    private lateinit var mAppName: EditText
    private lateinit var mPackageName: EditText
    private lateinit var mVersionName: EditText
    private lateinit var mVersionCode: EditText
    private lateinit var mMainFileName: EditText
    private lateinit var mIcon: ImageView

    private var mDirectory: File? = null
    private var mParentDirectory: File? = null
    private var mProjectConfig: ProjectConfig? = null
    private var mNewProject = false
    private var mIconBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityProjectConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mProjectLocation = binding.projectLocation
        mProjectLocationWrapper = binding.projectLocationWrapper
        mAppName = binding.appName

        mPackageName = binding.packageName
        mPackageName.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_UP) {
                    mVersionName.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        mVersionName = binding.versionName
        mVersionCode = binding.versionCode
        mMainFileName = binding.mainFileName

        mIcon = binding.appIcon.apply {
            setOnClickListener { selectIcon() }
        }

        binding.fab.setOnClickListener { commit() }

        mNewProject = intent.getBooleanExtra(EXTRA_NEW_PROJECT, false)

        val parentDirectory = intent.getStringExtra(EXTRA_PARENT_DIRECTORY)
        if (mNewProject) {
            if (parentDirectory == null) {
                finish()
                return
            }
            mParentDirectory = File(parentDirectory).also {
                mProjectLocation.setText(it.path)
            }
            mProjectConfig = ProjectConfig()
        } else {
            val dir = intent.getStringExtra(EXTRA_DIRECTORY)
            if (dir == null) {
                finish()
                return
            }
            mDirectory = File(dir)
            mProjectConfig = ProjectConfig.fromProjectDir(dir)
            if (mProjectConfig == null) {
                MaterialDialog.Builder(this)
                    .title(R.string.text_invalid_project)
                    .positiveText(R.string.text_ok)
                    .dismissListener { finish() }
                    .show()
            }
        }
        mProjectConfig?.let { config ->
            if (mNewProject) {
                mAppName.addTextChangedListener(SimpleTextWatcher { s -> mProjectLocation.setText(File(mParentDirectory, s.toString()).path) })
                setToolbarAsBack(R.string.text_new_project)
            } else {
                mAppName.setText(config.name)
                setToolbarAsBack(config.name)
                mVersionCode.setText(config.versionCode.toString())
                mPackageName.setText(config.packageName)
                mVersionName.setText(config.versionName)
                mMainFileName.setText(config.mainScriptFile)
                mProjectLocationWrapper.visibility = View.GONE
                config.icon?.let { icon ->
                    File(mDirectory, icon).takeIf { it.exists() }?.let { iconFile ->
                        Glide.with(this)
                            .load(iconFile)
                            .into(mIcon)
                    }
                }
            }
        }
    }

    @SuppressLint("CheckResult")
    fun commit() {
        if (!checkInputs()) {
            return
        }
        syncProjectConfig()
        if (mIconBitmap != null) {
            saveIcon(mIconBitmap!!)
                .subscribe({ saveProjectConfig() }) { e: Throwable ->
                    e.printStackTrace()
                    showToast(this, e.message, true)
                }
        } else {
            saveProjectConfig()
        }
    }

    @SuppressLint("CheckResult")
    private fun saveProjectConfig() {
        if (mNewProject) {
            ProjectTemplate(mProjectConfig, mDirectory)
                .newProject()
                .subscribe({
                    Explorers.workspace().notifyChildrenChanged(ExplorerDirPage(mParentDirectory, null))
                    finish()
                }) { e: Throwable ->
                    e.printStackTrace()
                    showToast(this, e.message, true)
                }
        } else {
            Observable.fromCallable {
                write(
                    ProjectConfig.configFileOfDir(mDirectory!!.path),
                    mProjectConfig!!.toJson()
                )
                Void.TYPE
            }
                .observeOn(Schedulers.io())
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val item = ExplorerFileItem(mDirectory, null)
                    Explorers.workspace().notifyItemChanged(item, item)
                    finish()
                }) { e: Throwable ->
                    e.printStackTrace()
                    showToast(this, e.message, true)
                }
        }
    }

    private fun selectIcon() {
        AppsIconSelectActivity.launchForResult(this, REQUEST_CODE)
    }

    private fun syncProjectConfig() {
        mProjectConfig!!.let {
            it.name = mAppName.text.toString()
            it.versionCode = mVersionCode.text.toString().toInt()
            it.versionName = mVersionName.text.toString()
            it.mainScriptFile = mMainFileName.text.toString()
            it.packageName = mPackageName.text.toString()
        }
        if (mNewProject) {
            val location = mProjectLocation.text.toString()
            mDirectory = File(location)
        }
        // mProjectConfig.getLaunchConfig().setHideLogs(true);
    }

    private fun checkInputs(): Boolean {
        return (checkNotEmpty(mAppName)
                and checkNotEmpty(mVersionCode)
                and checkNotEmpty(mVersionName)
                and checkPackageNameValid(mPackageName))
    }

    private fun checkPackageNameValid(editText: EditText?): Boolean {
        val text = editText!!.text
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

    private fun checkNotEmpty(editText: EditText?): Boolean {
        if (!TextUtils.isEmpty(editText!!.text)) return true
        // TODO by Stardust on Dec 8, 2017.
        //  ! More beautiful ways?
        //  ! zh-CN (translated by SuperMonster003 on Jul 29, 2024):
        //  ! 更优雅的方式?
        val hint = (editText.parent.parent as TextInputLayout).hint.toString()
        editText.error = hint + getString(R.string.text_should_not_be_empty)
        return false
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("CheckResult", "MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) {
            return
        }
        AppsIconSelectActivity.getDrawableFromIntent(applicationContext, data)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ drawable: Drawable? ->
                drawable ?: return@subscribe
                mIcon.setImageDrawable(drawable)
                mIconBitmap = drawable.toBitmapOrNull()
            }) { obj: Throwable -> obj.printStackTrace() }
    }

    @SuppressLint("CheckResult")
    private fun saveIcon(b: Bitmap): Observable<String> {
        return Observable.just(b)
            .map { bitmap: Bitmap ->
                var iconPath = mProjectConfig!!.icon
                if (iconPath == null) {
                    iconPath = "res/logo.png"
                }
                val iconFile = File(mDirectory, iconPath)
                ensureDir(iconFile.path)
                val fos = FileOutputStream(iconFile)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.close()
                iconPath
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { iconPath: String? -> mProjectConfig!!.icon = iconPath }
    }

    override fun onDestroy() {
        super.onDestroy()

        mDirectory = null
        mParentDirectory = null
        mProjectConfig = null

        mIconBitmap?.recycle()
        mIconBitmap = null
    }

    companion object {
        const val EXTRA_PARENT_DIRECTORY = "parent_directory"
        const val EXTRA_NEW_PROJECT = "new_project"
        const val EXTRA_DIRECTORY = "directory"
        private const val REQUEST_CODE = 12477
        private val REGEX_PACKAGE_NAME = Pattern.compile("^([A-Za-z][A-Za-z\\d_]*\\.)+([A-Za-z][A-Za-z\\d_]*)$")
    }
}