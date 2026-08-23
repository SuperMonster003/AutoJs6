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

internal object PythonHostDialogPolicy {
    const val MAX_TITLE_BYTES = 256
    const val MAX_TEXT_BYTES = 4 * 1024
    const val MAX_PROMPT_REPLY_BYTES = 32 * 1024
    const val MAX_ITEMS = 64
    const val MAX_ITEM_BYTES = 1024
    const val MAX_TOTAL_ITEM_BYTES = 32 * 1024
    const val WAIT_MILLIS = 5L * 60L * 1000L
}

internal sealed interface PythonHostDialogRequest {
    val title: String

    data class Alert(
        override val title: String,
        val text: String,
    ) : PythonHostDialogRequest

    data class Confirm(
        override val title: String,
        val text: String,
    ) : PythonHostDialogRequest

    data class Prompt(
        override val title: String,
        val text: String,
        val defaultValue: String,
    ) : PythonHostDialogRequest

    data class Select(
        override val title: String,
        val items: List<String>,
    ) : PythonHostDialogRequest
}

internal sealed interface PythonHostDialogResponse {
    object Acknowledged : PythonHostDialogResponse

    data class Confirmation(val confirmed: Boolean) : PythonHostDialogResponse

    data class PromptValue(val value: String?) : PythonHostDialogResponse

    data class Selection(val index: Int?) : PythonHostDialogResponse
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

    @Throws(InterruptedException::class)
    fun requestDialog(request: PythonHostDialogRequest): PythonHostDialogResponse

    fun cancel()

    override fun close() = cancel()
}

/** One-dialog-at-a-time foreground controller. Cancellation always dismisses the active UI. */
private class PythonMaterialDialogInputController(activity: Activity) : PythonInteractiveInputController {
    private val activity = WeakReference(activity)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = AtomicReference<CancellablePending?>()
    private val closed = AtomicBoolean(false)

    override fun request(prompt: PythonInputPrompt): PythonInteractiveInputResponse = awaitResponse(
        timeoutMillis = prompt.replyTimeoutMillis,
        cancelled = cancelledInputResponse(),
    ) { pending ->
        showInput(prompt, pending)
    }

    override fun requestDialog(request: PythonHostDialogRequest): PythonHostDialogResponse = awaitResponse(
        timeoutMillis = PythonHostDialogPolicy.WAIT_MILLIS,
        cancelled = cancelledDialogResponse(request),
    ) { pending ->
        showHostDialog(request, pending)
    }

    private fun <T : Any> awaitResponse(
        timeoutMillis: Long,
        cancelled: T,
        show: (Pending<T>) -> Unit,
    ): T {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Python foreground UI must not block the Android main thread"
        }
        check(timeoutMillis > 0L) { "Python foreground UI wait must be positive" }
        check(!closed.get()) { "Python foreground UI controller is closed" }
        val pending = Pending(cancelled)
        check(active.compareAndSet(null, pending)) { "A Python foreground dialog is already active" }
        try {
            if (!mainHandler.post { show(pending) }) {
                pending.cancel()
            }
            val completed = try {
                pending.done.await(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (error: InterruptedException) {
                pending.cancel()
                Thread.currentThread().interrupt()
                throw error
            }
            if (!completed) pending.cancel()
            return requireNotNull(pending.response.get()) {
                "Python foreground dialog completed without a response"
            }
        } finally {
            active.compareAndSet(pending, null)
            dismiss(pending)
        }
    }

    override fun cancel() {
        closed.set(true)
        active.get()?.let { pending ->
            pending.cancel()
            dismiss(pending)
        }
    }

    private fun showInput(prompt: PythonInputPrompt, pending: Pending<PythonInteractiveInputResponse>) {
        if (pending.completed.get()) return
        val current = currentActivity()
        if (current == null) {
            pending.cancel()
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
                pending.cancel()
                dialog.dismiss()
            }
            .positiveText(R.string.dialog_button_confirm)
            .autoDismiss(false)
            .dismissListener { pending.cancel() }
            .widgetThemeColor()
        if (prompt.text.isNotEmpty()) builder.content(prompt.text)
        try {
            pending.dialog.set(builder.show())
        } catch (_: RuntimeException) {
            pending.cancel()
        }
    }

    private fun showHostDialog(
        request: PythonHostDialogRequest,
        pending: Pending<PythonHostDialogResponse>,
    ) {
        if (pending.completed.get()) return
        val current = currentActivity()
        if (current == null) {
            pending.cancel()
            return
        }
        val builder = MaterialDialog.Builder(current)
            .title(request.title)
            .autoDismiss(false)
            .dismissListener { pending.cancel() }
        when (request) {
            is PythonHostDialogRequest.Alert -> {
                if (request.text.isNotEmpty()) builder.content(request.text)
                builder
                    .positiveText(R.string.dialog_button_confirm)
                    .onPositive { dialog, _ ->
                        pending.complete(PythonHostDialogResponse.Acknowledged)
                        dialog.dismiss()
                    }
            }
            is PythonHostDialogRequest.Confirm -> {
                if (request.text.isNotEmpty()) builder.content(request.text)
                builder
                    .negativeText(R.string.dialog_button_cancel)
                    .onNegative { dialog, _ ->
                        pending.complete(PythonHostDialogResponse.Confirmation(false))
                        dialog.dismiss()
                    }
                    .positiveText(R.string.dialog_button_confirm)
                    .onPositive { dialog, _ ->
                        pending.complete(PythonHostDialogResponse.Confirmation(true))
                        dialog.dismiss()
                    }
            }
            is PythonHostDialogRequest.Prompt -> {
                if (request.text.isNotEmpty()) builder.content(request.text)
                builder
                    .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                    .input(null, request.defaultValue, true) { dialog, input ->
                        pending.complete(PythonHostDialogResponse.PromptValue(input?.toString().orEmpty()))
                        dialog.dismiss()
                    }
                    .negativeText(R.string.dialog_button_cancel)
                    .onNegative { dialog, _ ->
                        pending.complete(PythonHostDialogResponse.PromptValue(null))
                        dialog.dismiss()
                    }
                    .positiveText(R.string.dialog_button_confirm)
            }
            is PythonHostDialogRequest.Select -> builder
                .items(request.items)
                .itemsCallback { dialog, _, which, _ ->
                    pending.complete(PythonHostDialogResponse.Selection(which))
                    dialog.dismiss()
                }
                .negativeText(R.string.dialog_button_cancel)
                .onNegative { dialog, _ ->
                    pending.complete(PythonHostDialogResponse.Selection(null))
                    dialog.dismiss()
                }
        }
        try {
            pending.dialog.set(builder.widgetThemeColor().show())
        } catch (_: RuntimeException) {
            pending.cancel()
        }
    }

    private fun currentActivity(): Activity? = activity.get()
        ?.takeUnless(Activity::isFinishing)
        ?.takeUnless { Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && it.isDestroyed }

    private fun dismiss(pending: CancellablePending) {
        mainHandler.post {
            pending.dialog.getAndSet(null)?.let { dialog ->
                if (dialog.isShowing) runCatching { dialog.dismiss() }
            }
        }
    }

    private interface CancellablePending {
        val dialog: AtomicReference<MaterialDialog?>

        fun cancel()
    }

    private class Pending<T : Any>(private val cancelled: T) : CancellablePending {
        val completed = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val response = AtomicReference<T?>()
        override val dialog = AtomicReference<MaterialDialog?>()

        fun complete(value: T) {
            if (!completed.compareAndSet(false, true)) return
            response.set(value)
            done.countDown()
        }

        override fun cancel() = complete(cancelled)
    }
}

private fun cancelledInputResponse() = PythonInteractiveInputResponse(PythonInputReplyStatus.CANCELLED)

private fun cancelledDialogResponse(request: PythonHostDialogRequest): PythonHostDialogResponse = when (request) {
    is PythonHostDialogRequest.Alert -> PythonHostDialogResponse.Acknowledged
    is PythonHostDialogRequest.Confirm -> PythonHostDialogResponse.Confirmation(false)
    is PythonHostDialogRequest.Prompt -> PythonHostDialogResponse.PromptValue(null)
    is PythonHostDialogRequest.Select -> PythonHostDialogResponse.Selection(null)
}

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
