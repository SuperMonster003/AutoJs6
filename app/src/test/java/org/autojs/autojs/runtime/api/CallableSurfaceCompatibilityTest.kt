package org.autojs.autojs.runtime.api

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.pick.PickRequest
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.looper.MainThreadProxy
import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.core.looper.TimerThread
import org.autojs.autojs.core.ui.dialog.BlockedMaterialDialog
import org.autojs.autojs.core.ui.dialog.JsDialog
import org.autojs.autojs.core.ui.dialog.JsDialogBuilder
import org.autojs.autojs.core.ui.nativeview.ViewPrototype
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.web.WebSocket
import org.autojs.autojs.runtime.IScriptBridges
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.modules.tts.TtsUtterance
import org.autojs.autojs.util.RhinoUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Function

class CallableSurfaceCompatibilityTest {

    @Test
    fun callbackAndListenerSurfacesExposeOnlyCallableSignatures() {
        assertCallableOnly(EventEmitter::class.java, "on", String::class.java)
        assertCallableOnly(
            Timer::class.java,
            "setTimeout",
            trailingTypes = arrayOf(Long::class.javaPrimitiveType!!, Array<Any>::class.java),
        )
        assertCallableOnly(
            TimerThread::class.java,
            "setTimeout",
            trailingTypes = arrayOf(Long::class.javaPrimitiveType!!, Array<Any>::class.java),
        )
        assertCallableOnly(MainThreadProxy::class.java, "setImmediate", trailingTypes = arrayOf(Array<Any>::class.java))
        assertCallableOnly(Timers::class.java, "setTimeout")
        assertCallableOnly(Events::class.java, "onKeyDown", String::class.java)
        assertCallableOnly(Dialogs::class.java, "rawInput", String::class.java, String::class.java)
        assertCallableOnly(JsDialog::class.java, "on", String::class.java)
        assertCallableOnly(JsDialogBuilder::class.java, "on", String::class.java)
        assertCallableOnly(ViewPrototype::class.java, "click")
        assertCallableOnly(JsCanvasView::class.java, "on", String::class.java)
        assertCallableOnly(WebSocket::class.java, "on", String::class.java)
        assertCallableOnly(TtsUtterance::class.java, "on", String::class.java)
        assertCallableOnly(TtsUtterance::class.java, "once", String::class.java)
        assertCallableOnly(IScriptBridges::class.java, "call", trailingTypes = arrayOf(Any::class.java, Array<Any>::class.java))
    }

    @Test
    fun scriptVisibleSurfacesDoNotExposeAmbiguousCallableBridges() {
        val scriptVisibleTypes = listOf(
            EventEmitter::class.java,
            EventEmitter.Listeners::class.java,
            EventEmitter.ListenerWrapper::class.java,
            WebSocket::class.java,
            TtsUtterance::class.java,
            ViewPrototype::class.java,
            JsCanvasView::class.java,
            JsDialog::class.java,
            JsDialogBuilder::class.java,
            BlockedMaterialDialog.Builder::class.java,
            Events::class.java,
            Dialogs::class.java,
            Dialogs.NonUiDialogs::class.java,
            Timer::class.java,
            TimerThread::class.java,
            MainThreadProxy::class.java,
            Timers::class.java,
            IScriptBridges::class.java,
            ScriptBridges::class.java,
            UiSelector::class.java,
            UiSelector.Companion::class.java,
            PickRequest::class.java,
            UiObject::class.java,
            UiObject.Companion::class.java,
            RhinoUtils::class.java,
        )

        scriptVisibleTypes.forEach { type ->
            assertNoAmbiguousPairs(type.methods.toList()) { it.name to it.parameterTypes }
            assertNoAmbiguousPairs(type.constructors.toList()) { "<init>" to it.parameterTypes }
        }
    }

    private fun assertCallableOnly(
        type: Class<*>,
        name: String,
        vararg leadingTypes: Class<*>,
        trailingTypes: Array<Class<*>> = emptyArray(),
    ) {
        type.getMethod(name, *leadingTypes, Callable::class.java, *trailingTypes)
        assertFalse(
            "${type.name}.$name must not expose a BaseFunction overload alongside Callable",
            type.methods.any {
                it.name == name && it.parameterTypes.contentEquals(
                    arrayOf(*leadingTypes, BaseFunction::class.java, *trailingTypes),
                )
            },
        )
    }

    private fun <T> assertNoAmbiguousPairs(
        members: List<T>,
        signatureOf: (T) -> Pair<String, Array<Class<*>>>,
    ) {
        members.forEachIndexed { index, first ->
            val (firstName, firstTypes) = signatureOf(first)
            members.drop(index + 1).forEach { second ->
                val (secondName, secondTypes) = signatureOf(second)
                if (firstName == secondName && areAmbiguousCallbackShapes(firstTypes, secondTypes)) {
                    fail("Ambiguous Rhino callback overloads: $first and $second")
                }
            }
        }
    }

    private fun areAmbiguousCallbackShapes(first: Array<Class<*>>, second: Array<Class<*>>): Boolean {
        if (first.size != second.size) return false
        var callbackTypeChanged = false
        first.indices.forEach { index ->
            val firstType = first[index]
            val secondType = second[index]
            if (firstType == secondType) return@forEach
            if (isAmbiguousFunctionTypePair(firstType, secondType)) {
                callbackTypeChanged = true
                return@forEach
            }
            return false
        }
        return callbackTypeChanged
    }

    private fun isAmbiguousFunctionTypePair(first: Class<*>, second: Class<*>): Boolean {
        val functionTypes = setOf(BaseFunction::class.java, Callable::class.java, Function::class.java)
        return first != second && first in functionTypes && second in functionTypes
    }

}
