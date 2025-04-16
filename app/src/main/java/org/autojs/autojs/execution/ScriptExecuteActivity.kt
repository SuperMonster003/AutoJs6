package org.autojs.autojs.execution

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isNotEmpty
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.eventloop.SimpleEvent
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine.ExecuteCallback
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.engine.ScriptEngineManager
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.execution.ScriptExecution.AbstractScriptExecution
import org.autojs.autojs.inrt.autojs.LoopBasedJavaScriptEngineWithDecryption
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.ContinuationPending

/**
 * Created by Stardust on Feb 5, 2017.
 * Modified by SuperMonster003 as of Nov 15, 2023.
 */
class ScriptExecuteActivity : AppCompatActivity() {

    private var mRuntime: ScriptRuntime? = null
    private var mExecutionListener: ScriptExecutionListener? = null
    private var mScriptSource: ScriptSource? = null
    private var mResult: Any? = null

    private lateinit var mScriptEngine: ScriptEngine<*>
    private lateinit var mScriptExecution: ActivityScriptExecution

    @ScriptInterface
    lateinit var eventEmitter: EventEmitter
        private set

    @ScriptInterface
    val emitter: EventEmitter?
        get() = when {
            ::eventEmitter.isInitialized -> eventEmitter
            else -> null
        }

    // FIXME by Stardust on Mar 16, 2018.
    //  ! 如果 Activity 被回收则得不到改进.
    //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
    //  ! No improvements would be obtained if Activity was destroyed.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val windowBackground: Drawable = window.decorView.background
            ?: getColor(R.color.md_gray_50).toDrawable().also { window.setBackgroundDrawable(it) }

        if (windowBackground is ColorDrawable) windowBackground.color.let { backgroundColor ->
            val isLightColor = ViewUtils.isLuminanceDark(backgroundColor)
            ViewUtils.setStatusBarBackgroundColor(this, backgroundColor)
            ViewUtils.setStatusBarAppearanceLight(this, isLightColor)
            ViewUtils.setNavigationBarBackgroundColor(this, backgroundColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ViewUtils.setNavigationBarAppearanceLight(this, isLightColor)
            }
        }

        ViewUtils.addWindowFlags(this, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, ScriptExecution.NO_ID)
        if (executionId == ScriptExecution.NO_ID) {
            super.finish()
            return
        }
        val execution = ScriptEngineService.getInstance().getScriptExecution(executionId)
        if (execution !is ActivityScriptExecution) {
            super.finish()
            return
        }
        mScriptExecution = execution
        mScriptSource = mScriptExecution.source
        mScriptEngine = mScriptExecution.createEngine(this)
        mExecutionListener = mScriptExecution.listener
        mRuntime = (mScriptEngine as JavaScriptEngine?)!!.runtime.also { rt ->
            eventEmitter = EventEmitter(rt.bridges)
        }
        runScript()
        emit("create", savedInstanceState)
    }

    private fun runScript() {
        try {
            prepare()
            doExecution()
        } catch (pending: ContinuationPending) {
            pending.printStackTrace()
        } catch (e: Exception) {
            onException(e)
        }
    }

    private fun onException(e: Throwable) {
        mExecutionListener?.onException(mScriptExecution, e)
        super.finish()
    }

    private fun prepare() {
        mScriptEngine.put("activity", this)
        mScriptEngine.setTag("activity", this)
        mScriptEngine.setTag(ScriptEngine.TAG_ENV_PATH, mScriptExecution.config.envPath)
        mScriptEngine.setTag(ScriptEngine.TAG_WORKING_DIRECTORY, mScriptExecution.config.workingDirectory)
        mScriptEngine.init()
    }

    private fun doExecution() {
        val executeCallback = object : ExecuteCallback {
            override fun onResult(r: Any?) {
                mResult = r
            }

            override fun onException(e: Throwable) {
                this@ScriptExecuteActivity.onException(e)
            }
        }

        mScriptEngine.setTag(ScriptEngine.TAG_SOURCE, mScriptSource)
        mExecutionListener!!.onStart(mScriptExecution)

        if (isInrt) {
            (mScriptEngine as LoopBasedJavaScriptEngineWithDecryption).execute(mScriptSource, executeCallback)
        } else {
            (mScriptEngine as LoopBasedJavaScriptEngine).execute(mScriptSource, executeCallback)
        }
    }

    override fun finish() {
        if (::mScriptEngine.isInitialized) {
            mScriptEngine.uncaughtException
                ?.let { onException(it) }
                ?: mExecutionListener?.onSuccess(mScriptExecution, mResult)
        }
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TAG, "onDestroy")

        if (::mScriptEngine.isInitialized) {
            mScriptEngine.put("activity", null)
            mScriptEngine.setTag("activity", null)
            mScriptEngine.destroy()
        }

        mRuntime?.run {
            loopers.waitWhenIdle(false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_EXECUTION_ID, mScriptExecution.id)
        emit("save_instance_state", outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val event = SimpleEvent()
        emit("back_pressed", event)
        if (!event.consumed) {
            @Suppress("DEPRECATION", "KotlinRedundantDiagnosticSuppress")
            super.onBackPressed()
        }
    }

    override fun onPause() {
        emit("pause")
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        emit("resume")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        emit("restore_instance_state", savedInstanceState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val e = SimpleEvent()
        emit("key_down", keyCode, event, e)
        return e.consumed || super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val e = SimpleEvent()
        emit("generic_motion_event", event, e)
        return super.onGenericMotionEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        emit("activity_result", requestCode, resultCode, data)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        emit("create_options_menu", menu)
        return menu.isNotEmpty()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val e = SimpleEvent()
        emit("options_item_selected", e, item)
        return e.consumed || super.onOptionsItemSelected(item)
    }

    fun emit(event: String?, vararg args: Any?) {
        event ?: return
        try {
            mRuntime?.events?.emit(event, *args)
            eventEmitter.emit(event, *args)
        } catch (e: Exception) {
            mRuntime?.exit(e)
        }
    }

    class ActivityScriptExecution internal constructor(
        private val mScriptEngineManager: ScriptEngineManager,
        task: ScriptExecutionTask?,
    ) : AbstractScriptExecution(task) {
        private var mScriptEngine: ScriptEngine<*>? = null

        @Suppress("unused", "UNUSED_PARAMETER")
        fun createEngine(activity: Activity?): ScriptEngine<*> {
            mScriptEngine?.forceStop()
            return mScriptEngineManager.createEngineOfSourceOrThrow(source, id)
                .apply { setTag(ExecutionConfig.tag, config) }
                .also { mScriptEngine = it }
        }

        override fun getEngine() = mScriptEngine
    }

    companion object {
        private const val LOG_TAG = "ScriptExecuteActivity"
        private val EXTRA_EXECUTION_ID = ScriptExecuteActivity::class.java.name + ".execution_id"

        @JvmStatic
        fun execute(context: Context, manager: ScriptEngineManager, task: ScriptExecutionTask): ActivityScriptExecution {
            val execution = ActivityScriptExecution(manager, task)
            val i = Intent(context, ScriptExecuteActivity::class.java)
                .putExtra(EXTRA_EXECUTION_ID, execution.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(task.config.intentFlags)
            context.startActivity(i)
            return execution
        }
    }
}
