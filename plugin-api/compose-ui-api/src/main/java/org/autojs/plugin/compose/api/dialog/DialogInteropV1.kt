package org.autojs.plugin.compose.api.dialog

import android.os.IBinder
import android.view.WindowManager
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.interop.AndroidViewRendererV1
import org.autojs.plugin.compose.api.loading.ComposeUiHostEnvironment

/**
 * Optional process-local presentation protocol. Neither the frozen V1 tree/Parcel catalog nor
 * AndroidViewInteropV1 changes. Load this factory by its advertised string only after negotiation.
 */
object DialogInteropV1 {
    const val VERSION = 1
    const val FEATURE = "dialog-v1"
    const val FACTORY_CLASS_KEY = "dialogFactoryV1"
    const val DISMISS_REQUEST = "dialogDismissRequest"
    const val ALERT = "alert"
    const val BOTTOM_SHEET = "bottomSheet"
}

/**
 * Immutable options for one dialog lifetime. The host chooses APPLICATION with its Activity
 * context/token, or PHONE / APPLICATION_OVERLAY with permission and no Activity token.
 * Themes use the existing environment.initialTheme / renderer.setTheme contract.
 */
class DialogOptionsV1(
    val type: String = DialogInteropV1.ALERT,
    val cancelable: Boolean = true,
    val windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION,
    val windowToken: IBinder? = null,
) {
    init {
        if (type != DialogInteropV1.ALERT && type != DialogInteropV1.BOTTOM_SHEET) {
            throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
                "dialog.type: Expected alert or bottomSheet", prop = "type")
        }
        @Suppress("DEPRECATION")
        if (windowType != WindowManager.LayoutParams.TYPE_APPLICATION &&
            windowType != WindowManager.LayoutParams.TYPE_PHONE &&
            windowType != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
            throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
                "dialog.windowType: Unsupported window type", prop = "windowType")
        }
    }
}

interface DialogRendererFactoryV1 {
    fun extensionVersion(): Int

    /**
     * Main thread. view() returns the composition anchor; the host owns its lifecycle/attachment.
     * The presentation creates its own dialog window after attachment. User dismissal enqueues
     * DISMISS_REQUEST through environment.eventSink with the current generation, NO_NODE_ID and
     * SYSTEM_CALLBACK_ID, at most once. The host handles this as lifecycle control and closes the
     * session. dispose() always closes the window without emitting a dismissal request.
     */
    fun create(environment: ComposeUiHostEnvironment, options: DialogOptionsV1): AndroidViewRendererV1
}
