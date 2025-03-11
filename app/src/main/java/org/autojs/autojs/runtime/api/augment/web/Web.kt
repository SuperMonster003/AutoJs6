package org.autojs.autojs.runtime.api.augment.web

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.web.InjectableWebClient
import org.autojs.autojs.core.web.InjectableWebView
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.Context
import android.content.Context as AndroidContext
import org.autojs.autojs.core.web.WebSocket as CoreWebSocket

class Web(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::newInjectableWebView.name to AS_GLOBAL,
        ::newInjectableWebClient.name to AS_GLOBAL,
        ::newWebSocket.name to AS_GLOBAL,
    )

    companion object : FlexibleArray() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun newInjectableWebView(scriptRuntime: ScriptRuntime, args: Array<out Any?>): InjectableWebView = ensureArgumentsAtMost(args, 2) {
            newInjectableWebViewRhinoWithRuntime(scriptRuntime, *it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun newInjectableWebViewRhinoWithRuntime(scriptRuntime: ScriptRuntime, vararg args: Any?): InjectableWebView = withRhinoContext { cx ->
            when (args.size) {
                2 -> {
                    val (androidContext, url) = args
                    val niceUrl = when {
                        url.isJsNullish() -> null
                        else -> Context.toString(url)
                    }
                    val contextForWebView = androidContext.jsUnwrapped() as? AndroidContext ?: globalContext
                    InjectableWebView(contextForWebView, cx, scriptRuntime.topLevelScope, niceUrl)
                }
                1 -> when {
                    args[0] is String -> {
                        newInjectableWebViewRhinoWithRuntime(scriptRuntime, scriptRuntime.topLevelScope.prop("activity"), args[0])
                    }
                    else -> newInjectableWebViewRhinoWithRuntime(scriptRuntime, args[0], null)
                }
                0 -> newInjectableWebViewRhinoWithRuntime(scriptRuntime, scriptRuntime.topLevelScope.prop("activity"))
                else -> throw WrappedIllegalArgumentException("Invalid arguments length ${args.size} for web.newInjectableWebView")
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun newInjectableWebClient(scriptRuntime: ScriptRuntime, args: Array<out Any?>): InjectableWebClient = ensureArgumentsIsEmpty(args) {
            withRhinoContext { cx -> InjectableWebClient(cx, scriptRuntime.topLevelScope) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun newWebSocket(scriptRuntime: ScriptRuntime, args: Array<out Any?>): CoreWebSocket = ensureArgumentsOnlyOne(args) {
            CoreWebSocket(scriptRuntime, scriptRuntime.http.okhttp, coerceString(it))
        }

    }

}