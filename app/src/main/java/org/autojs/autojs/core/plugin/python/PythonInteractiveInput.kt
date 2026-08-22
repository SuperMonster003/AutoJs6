package org.autojs.autojs.core.plugin.python

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R
import org.autojs.plugin.python.runtime.api.PythonInputEcho
import org.autojs.plugin.python.runtime.api.PythonInputPrompt
import org.autojs.plugin.python.runtime.api.PythonInputReplyStatus
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Opaque authorization minted only by a foreground user-gesture launch path. */
internal sealed interface PythonInteractiveInputGrant {
    fun openController(): PythonInteractiveInputController
}

internal object PythonInteractiveInputAuthorization {
    const val ARGUMENT = "autojs6.python.foregroundInteractiveInputGrant"

    fun authorizeForeground(config: ExecutionConfig, context: Context) {
        val activity = context.findActivity()
            ?.takeUnless(Activity::isFinishing)
            ?.takeUnless { Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && it.isDestroyed }
            ?: throw PythonRuntimeHostException(
                PythonRuntimeHostError.INTERACTIVE_INPUT_NOT_ALLOWED,
                "Python interactive input requires a live foreground Activity",
            )
        config.setArgument(ARGUMENT, ForegroundGrant(activity))
    }

    fun resolve(config: ExecutionConfig?): PythonInteractiveInputGrant? {
        if (config == null || !config.arguments.containsKey(ARGUMENT)) return null
        return config.getArgument(ARGUMENT) as? ForegroundGrant
            ?: throw PythonRuntimeHostException(
                PythonRuntimeHostError.INTERACTIVE_INPUT_NOT_ALLOWED,
                "Python interactive input grant was not minted by a foreground launch",
            )
    }

    private class ForegroundGrant(activity: Activity) : PythonInteractiveInputGrant {
        private val activity = WeakReference(activity)

        override fun openController(): PythonInteractiveInputController {
            val current = activity.get()
                ?.takeUnless(Activity::isFinishing)
                ?.takeUnless { Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && it.isDestroyed }
                ?: throw PythonRuntimeHostException(
                    PythonRuntimeHostError.INTERACTIVE_INPUT_NOT_ALLOWED,
                    "Python interactive input foreground Activity is no longer available",
                )
            return PythonMaterialDialogInputController(current)
        }
    }
}

internal data class PythonInteractiveInputResponse(
    val status: PythonInputReplyStatus,
    val value: String? = null,
)

internal interface PythonInteractiveInputController : AutoCloseable {
    @Throws(InterruptedException::class)
    fun request(prompt: PythonInputPrompt): PythonInteractiveInputResponse

    fun cancel()

    override fun close() = cancel()
}

/** One-dialog-at-a-time foreground controller. Cancellation always dismisses the active UI. */
private class PythonMaterialDialogInputController(activity: Activity) : PythonInteractiveInputController {
    private val activity = WeakReference(activity)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = AtomicReference<Pending?>()
    private val closed = AtomicBoolean(false)

    override fun request(prompt: PythonInputPrompt): PythonInteractiveInputResponse {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Python interactive input must not block the Android main thread"
        }
        check(!closed.get()) { "Python interactive input controller is closed" }
        val pending = Pending()
        check(active.compareAndSet(null, pending)) { "A Python input dialog is already active" }
        try {
            if (!mainHandler.post { show(prompt, pending) }) {
                pending.complete(cancelledResponse())
            }
            val completed = try {
                pending.done.await(prompt.replyTimeoutMillis, TimeUnit.MILLISECONDS)
            } catch (error: InterruptedException) {
                pending.complete(cancelledResponse())
                Thread.currentThread().interrupt()
                throw error
            }
            if (!completed) pending.complete(cancelledResponse())
            return requireNotNull(pending.response.get()) {
                "Python interactive input dialog completed without a response"
            }
        } finally {
            active.compareAndSet(pending, null)
            dismiss(pending)
        }
    }

    override fun cancel() {
        closed.set(true)
        active.get()?.let { pending ->
            pending.complete(cancelledResponse())
            dismiss(pending)
        }
    }

    private fun show(prompt: PythonInputPrompt, pending: Pending) {
        if (pending.completed.get()) return
        val current = activity.get()
            ?.takeUnless(Activity::isFinishing)
            ?.takeUnless { Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && it.isDestroyed }
        if (current == null) {
            pending.complete(cancelledResponse())
            return
        }
        val inputType = when (prompt.echo) {
            PythonInputEcho.VISIBLE -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            PythonInputEcho.HIDDEN -> InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val builder = MaterialDialog.Builder(current)
            .title(R.string.text_prompt)
            .inputType(inputType)
            .input(null, "", true) { dialog, input ->
                pending.complete(
                    PythonInteractiveInputResponse(PythonInputReplyStatus.VALUE, input?.toString().orEmpty()),
                )
                dialog.dismiss()
            }
            .negativeText(R.string.dialog_button_cancel)
            .onNegative { dialog, _ ->
                pending.complete(cancelledResponse())
                dialog.dismiss()
            }
            .positiveText(R.string.dialog_button_confirm)
            .autoDismiss(false)
            .dismissListener { pending.complete(cancelledResponse()) }
            .widgetThemeColor()
        if (prompt.text.isNotEmpty()) builder.content(prompt.text)
        try {
            pending.dialog.set(builder.show())
        } catch (_: RuntimeException) {
            pending.complete(cancelledResponse())
        }
    }

    private fun dismiss(pending: Pending) {
        mainHandler.post {
            pending.dialog.getAndSet(null)?.let { dialog ->
                if (dialog.isShowing) runCatching { dialog.dismiss() }
            }
        }
    }

    private class Pending {
        val completed = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val response = AtomicReference<PythonInteractiveInputResponse?>()
        val dialog = AtomicReference<MaterialDialog?>()

        fun complete(value: PythonInteractiveInputResponse) {
            if (!completed.compareAndSet(false, true)) return
            response.set(value)
            done.countDown()
        }
    }
}

private fun cancelledResponse() = PythonInteractiveInputResponse(PythonInputReplyStatus.CANCELLED)

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    val visited = HashSet<Context>()
    while (current != null && visited.add(current)) {
        when (current) {
            is Activity -> return current
            is ContextWrapper -> current = current.baseContext
            else -> return null
        }
    }
    return null
}
