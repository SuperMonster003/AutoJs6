package org.autojs.autojs.external.tasker

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.widget.Toast

import org.autojs.autojs.R
import org.autojs.autojs.timing.TaskReceiver
import org.autojs.autojs.tool.Observers
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.edit.EditorView

import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.EActivity
import butterknife.BindView

import io.reactivex.android.schedulers.AndroidSchedulers

import org.autojs.autojs.ui.edit.EditorView.EXTRA_CONTENT
import org.autojs.autojs.ui.edit.EditorView.EXTRA_NAME
import org.autojs.autojs.ui.edit.EditorView.EXTRA_RUN_ENABLED
import org.autojs.autojs.ui.edit.EditorView.EXTRA_SAVE_ENABLED

/**
 * Created by Stardust on 2017/4/5.
 */
class TaskerScriptEditActivity : BaseActivity() {

    @BindView(R.id.editor_view)
    internal var mEditorView: EditorView? = null

    @SuppressLint("CheckResult")
    @AfterViews
    internal override fun setUpViews() {
        mEditorView!!.handleIntent(intent
                .putExtra(EXTRA_RUN_ENABLED, false)
                .putExtra(EXTRA_SAVE_ENABLED, false))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Observers.emptyConsumer(),
                        { ex ->
                            Toast.makeText(this@TaskerScriptEditActivity, ex.message, Toast.LENGTH_LONG).show()
                            finish()
                        })
        BaseActivity.setToolbarAsBack(this, R.id.toolbar, mEditorView!!.name)
    }


    override fun finish() {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CONTENT, mEditorView!!.editor.text))
        super@TaskerScriptEditActivity.finish()
    }

    override fun onDestroy() {
        mEditorView!!.destroy()
        super.onDestroy()
    }

    companion object {

        val REQUEST_CODE = 10016
        val EXTRA_TASK_ID = TaskReceiver.EXTRA_TASK_ID

        fun edit(activity: Activity, title: String, summary: String, content: String) {
            activity.startActivityForResult(Intent(activity, TaskerScriptEditActivity_::class.java)
                    .putExtra(EXTRA_CONTENT, content)
                    .putExtra("summary", summary)
                    .putExtra(EXTRA_NAME, title), REQUEST_CODE)
        }
    }
}
