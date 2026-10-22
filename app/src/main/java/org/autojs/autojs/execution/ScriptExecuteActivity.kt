package org.autojs.autojs.execution

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isNotEmpty
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.OnActivityResultDelegate
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.eventloop.SimpleEvent
import org.autojs.autojs.core.plugin.compose.ComposeActivityInsets
import org.autojs.autojs.core.plugin.compose.ComposeInsetsHost
import org.autojs.autojs.core.plugin.typescript.TypeScriptExecutionCompiler
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.core.plugin.typescript.TypeScriptExecutionPerformance
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine.ExecuteCallback
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.engine.ScriptEngineManager
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.execution.ScriptExecution.AbstractScriptExecution
import org.autojs.autojs.inrt.autojs.LoopBasedJavaScriptEngineWithDecryption
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.ContinuationPending
import java.io.Closeable

/**
 * Created by Stardust on Feb 5, 2017.
 * Modified by SuperMonster003 as of Jan 20, 2026.
 */
class ScriptExecuteActivity : AppCompatActivity(), OnActivityResultDelegate.DelegateHost, ComposeInsetsHost, UiContentHost {

    // UiContentHost: the Activity window hosts `ui.layout` content; showContent is the Activity's own setContentView.
    // zh-CN: UiContentHost: Activity 窗口承载 `ui.layout` 内容; showContent 即 Activity 自身的 setContentView.
    override val inflaterContext: Context get() = this
    override val contentParent: ViewGroup get() = window.decorView as ViewGroup
    override val contentRoot: ViewGroup? get() = findViewById(android.R.id.content)
    override val hostWindow: Window get() = window
    override fun showContent(view: View) = setContentView(view)

    private val mOnBackPressedCallback = object : OnBackPressedCallback(true) {

        // override fun onBackPressed() {
        //     val event = SimpleEvent()
        //     emit("back_pressed", event)
        //     if (!event.consumed) {
        //         @Suppress("DEPRECATION", "KotlinRedundantDiagnosticSuppress")
        //         super.onBackPressed()
        //     }
        // }

        override fun handleOnBackPressed() {
            val event = SimpleEvent()
            emit("back_pressed", event)
            if (event.consumed) return

            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    private var mRuntime: ScriptRuntime? = null
    private var mExecutionListener: ScriptExecutionListener? = null
    private var mScriptSource: ScriptSource? = null
    private var mResult: Any? = null
    private var mPreparationThread: ThreadCompat? = null
    private var mTypeScriptPreparationTiming: TypeScriptPreparationTiming? = null
    private var mIsResumed = false
    private lateinit var mComposeInsets: ComposeActivityInsets

    private val mMediator = OnActivityResultDelegate.Mediator()

    private var mScriptEngine: ScriptEngine<*>? = null
    private var mEventEmitter: EventEmitter? = null
    private lateinit var mScriptExecution: ActivityScriptExecution

    @ScriptInterface
    val eventEmitter: EventEmitter
        get() = mEventEmitter ?: throw UninitializedPropertyAccessException("The event emitter is not created yet or already released")

    @ScriptInterface
    val emitter: EventEmitter?
        get() = mEventEmitter

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
            ViewUtils.setStatusBarIconLight(this, isLightColor)
            ViewUtils.setNavigationBarBackgroundColor(this, backgroundColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ViewUtils.setNavigationBarIconLight(this, isLightColor)
            }
        }

        ViewUtils.addWindowFlags(this, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        @Suppress("DEPRECATION")
        ViewUtils.appendSystemUiVisibility(this, SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        mComposeInsets = ComposeActivityInsets(window, findViewById(android.R.id.content))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ViewUtils.setNavigationBarBackgroundColor(this, getColor(R.color.black_alpha_44))
        }

        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)

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
        mExecutionListener = mScriptExecution.listener
        val source = mScriptExecution.source
        mScriptSource = source
        if (TypeScriptExecutionCompiler.requiresPreparation(source)) {
            prepareTypeScriptAndRun(source, savedInstanceState)
        } else {
            initializeAndRun(source, savedInstanceState)
        }
    }

    private fun prepareTypeScriptAndRun(source: ScriptSource, savedInstanceState: Bundle?) {
        mPreparationThread = ThreadCompat(
            Runnable {
                try {
                    val prepareStartedAt = SystemClock.elapsedRealtime()
                    val prepared = TypeScriptExecutionCompiler.prepare(applicationContext, source)
                    val preparedAt = SystemClock.elapsedRealtime()
                    runOnUiThread {
                        mPreparationThread = null
                        if (!isFinishing && !isDestroyed) {
                            mTypeScriptPreparationTiming = TypeScriptPreparationTiming(
                                prepareMs = preparedAt - prepareStartedAt,
                                dispatchMs = SystemClock.elapsedRealtime() - preparedAt,
                            )
                            initializeAndRun(prepared, savedInstanceState)
                        }
                    }
                } catch (error: Throwable) {
                    runOnUiThread {
                        mPreparationThread = null
                        if (!isFinishing && !isDestroyed) {
                            onException(error)
                        }
                    }
                }
            },
            "TypeScriptUiPreparation-${mScriptExecution.id}",
        ).also(ThreadCompat::start)
    }

    private fun initializeAndRun(source: ScriptSource, savedInstanceState: Bundle?) {
        try {
            mScriptSource = source
            val engineCreateStartedAt = SystemClock.elapsedRealtime()
            val engine = mScriptExecution.createEngine(this, source).also { mScriptEngine = it }
            mTypeScriptPreparationTiming?.let { timing ->
                mTypeScriptPreparationTiming = null
                TypeScriptExecutionPerformance.report(
                    TypeScriptExecutionPerformance.ENTRY_ACTIVITY,
                    timing.prepareMs,
                    SystemClock.elapsedRealtime() - engineCreateStartedAt,
                    timing.dispatchMs,
                )
            }
            mRuntime = (engine as JavaScriptEngine?)!!.runtime.also { runtime ->
                mEventEmitter = EventEmitter(runtime.bridges)
            }
            runScript(engine)
            emit("create", savedInstanceState)
            if (mIsResumed) {
                emit("resume")
            }
        } catch (error: Throwable) {
            onException(error)
        }
    }

    private fun runScript(engine: ScriptEngine<*>) {
        try {
            prepare(engine)
            doExecution(engine)
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

    private fun prepare(engine: ScriptEngine<*>) {
        engine.put("activity", this)
        engine.setTag("activity", this)
        engine.setTag(ScriptEngine.TAG_ENV_PATH, mScriptExecution.config.envPath)
        engine.setTag(ScriptEngine.TAG_WORKING_DIRECTORY, mScriptExecution.config.workingDirectory)
        engine.init()
    }

    private fun doExecution(engine: ScriptEngine<*>) {
        val executeCallback = object : ExecuteCallback {
            override fun onResult(r: Any?) {
                mResult = r
            }

            override fun onException(e: Throwable) {
                this@ScriptExecuteActivity.onException(e)
            }
        }

        engine.setTag(ScriptEngine.TAG_SOURCE, mScriptSource)
        mExecutionListener!!.onStart(mScriptExecution)

        if (!isInrt) {
            (engine as LoopBasedJavaScriptEngine).execute(mScriptSource, executeCallback)
        } else {
            (engine as LoopBasedJavaScriptEngineWithDecryption).execute(mScriptSource, executeCallback)
        }
    }

    override fun finish() {
        mScriptEngine?.let { engine ->
            engine.uncaughtException
                ?.let { onException(it) }
                ?: mExecutionListener?.onSuccess(mScriptExecution, mResult)
        }
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TAG, "onDestroy")

        mPreparationThread?.interrupt()
        mPreparationThread = null
        mRuntime?.loopers?.waitWhenIdle(false)

        mScriptEngine?.let { engine ->
            try {
                engine.put("activity", null)
                engine.setTag("activity", null)
                engine.destroy()
            } finally {
                if (::mScriptExecution.isInitialized) {
                    mScriptExecution.clearEngine(engine)
                }
            }
        }

        // destroy() has run ScriptRuntime.onExit, so the runtime has ended: WebView 111 (API 33) keeps the destroyed window
        // through a JNI global reference, and these fields and the script's views (their event emitters reach the engine)
        // made it keep the runtime too (docs/memory/ROADMAP.md appendix C Q48).
        // zh-CN: destroy() 已执行 ScriptRuntime.onExit, runtime 已结束: WebView 111 (API 33) 以 JNI 全局引用保留已销毁的窗口,
        // 这些字段与脚本的视图 (其事件发射器可达引擎) 曾使其连带保留 runtime (docs/memory/ROADMAP.md 附录 C Q48).
        contentRoot?.removeAllViews()
        mScriptEngine = null
        mEventEmitter = null
        mRuntime = null
        mExecutionListener = null
        mScriptSource = null
        mResult = null
        if (::mScriptExecution.isInitialized) {
            ScriptEngineService.getInstance().removeScriptExecution(mScriptExecution.id)
        }
    }

    override fun acquireComposeInsets(): Closeable = mComposeInsets.acquire()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_EXECUTION_ID, mScriptExecution.id)
        emit("save_instance_state", outState)
    }

    override fun onPause() {
        if (mEventEmitter != null) {
            emit("pause")
        }
        mIsResumed = false
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mIsResumed = true
        if (mEventEmitter != null) {
            emit("resume")
        }
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
        mMediator.onActivityResult(requestCode, resultCode, data)
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
            emitter?.emit(event, *args)
        } catch (e: Exception) {
            mRuntime?.exit(e)
        }
    }

    override fun getOnActivityResultDelegateMediator(): OnActivityResultDelegate.Mediator {
        return mMediator
    }

    class ActivityScriptExecution internal constructor(
        private val mScriptEngineManager: ScriptEngineManager,
        task: ScriptExecutionTask?,
    ) : AbstractScriptExecution(task) {
        private var mScriptEngine: ScriptEngine<*>? = null
        var isActivityStarted = false
            private set

        @Suppress("unused", "UNUSED_PARAMETER")
        fun createEngine(activity: Activity?, source: ScriptSource = this.source): ScriptEngine<*> {
            mScriptEngine?.forceStop()
            return mScriptEngineManager.createEngineOfSourceOrThrow(source, id)
                .apply { setTag(ExecutionConfig.tag, config) }
                .also { mScriptEngine = it }
        }

        fun markActivityStarted() {
            isActivityStarted = true
        }

        fun clearEngine(engine: ScriptEngine<*>? = null) {
            if (engine == null || mScriptEngine === engine) {
                mScriptEngine = null
            }
        }

        override fun getEngine() = mScriptEngine
    }

    companion object {
        private const val LOG_TAG = "ScriptExecuteActivity"
        private val EXTRA_EXECUTION_ID = ScriptExecuteActivity::class.java.name + ".execution_id"

        @JvmStatic
        fun start(context: Context, execution: ActivityScriptExecution): Boolean {
            // ScriptEngineService publishes this execution before Android can run onCreate.
            val started = Intent(context, ScriptExecuteActivity::class.java)
                .putExtra(EXTRA_EXECUTION_ID, execution.id)
                .addFlags(execution.config.intentFlags)
                .startSafely(context)
            if (started) {
                execution.markActivityStarted()
            }
            return started
        }
    }
}

/** Timing captured by the TypeScript preparation thread for the execution performance line. */
private class TypeScriptPreparationTiming(
    val prepareMs: Long,
    val dispatchMs: Long,
)
