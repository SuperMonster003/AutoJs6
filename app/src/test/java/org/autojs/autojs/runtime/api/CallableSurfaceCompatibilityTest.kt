package org.autojs.autojs.runtime.api

import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.looper.MainThreadProxy
import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.core.looper.TimerThread
import org.autojs.autojs.core.ui.dialog.JsDialog
import org.autojs.autojs.core.ui.dialog.JsDialogBuilder
import org.autojs.autojs.core.ui.nativeview.ViewPrototype
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.web.WebSocket
import org.autojs.autojs.runtime.IScriptBridges
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable

class CallableSurfaceCompatibilityTest {

    @Test
    fun callbackAndListenerSurfacesExposeCallableAndKeepBaseFunctionBridges() {
        assertCallableAndLegacyOverloads(EventEmitter::class.java, "on", String::class.java)
        assertCallableAndLegacyOverloads(
            Timer::class.java,
            "setTimeout",
            trailingTypes = arrayOf(Long::class.javaPrimitiveType!!, Array<Any>::class.java),
        )
        assertCallableAndLegacyOverloads(
            TimerThread::class.java,
            "setTimeout",
            trailingTypes = arrayOf(Long::class.javaPrimitiveType!!, Array<Any>::class.java),
        )
        assertCallableAndLegacyOverloads(MainThreadProxy::class.java, "setImmediate", trailingTypes = arrayOf(Array<Any>::class.java))
        assertCallableAndLegacyOverloads(Timers::class.java, "setTimeout")
        assertCallableAndLegacyOverloads(Events::class.java, "onKeyDown", String::class.java)
        assertCallableAndLegacyOverloads(Dialogs::class.java, "rawInput", String::class.java, String::class.java)
        assertCallableAndLegacyOverloads(JsDialog::class.java, "on", String::class.java)
        assertCallableAndLegacyOverloads(JsDialogBuilder::class.java, "on", String::class.java)
        assertCallableAndLegacyOverloads(ViewPrototype::class.java, "click")
        assertCallableAndLegacyOverloads(JsCanvasView::class.java, "on", String::class.java)
        assertCallableAndLegacyOverloads(WebSocket::class.java, "on", String::class.java)
        assertCallableAndLegacyOverloads(IScriptBridges::class.java, "call", trailingTypes = arrayOf(Any::class.java, Array<Any>::class.java))
    }

    private fun assertCallableAndLegacyOverloads(
        type: Class<*>,
        name: String,
        vararg leadingTypes: Class<*>,
        trailingTypes: Array<Class<*>> = emptyArray(),
    ) {
        type.getMethod(name, *leadingTypes, Callable::class.java, *trailingTypes)
        type.getMethod(name, *leadingTypes, BaseFunction::class.java, *trailingTypes)
    }

}
