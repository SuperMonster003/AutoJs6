package org.autojs.autojs.runtime.api.augment.automator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.ViewConfiguration
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.action.ActionTarget
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.extension.AnyExtensions.isJsArray
import org.autojs.autojs.extension.AnyExtensions.isJsFunction
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.isJsNumber
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.autojs.autojs.util.SdkVersionUtils
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.NativeObject
import android.graphics.Point as AndroidPoint
import org.opencv.core.Point as OpencvPoint

@Suppress("unused", "SameParameterValue", "UNUSED_PARAMETER")
class Automator(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::click.name to AS_GLOBAL,
        ::longClick.name to AS_GLOBAL,
        ::press.name to AS_GLOBAL,
        ::swipe.name to AS_GLOBAL,
        ::gesture.name to AS_GLOBAL,
        ::gestureAsync.name to AS_GLOBAL,
        ::gestures.name to AS_GLOBAL,
        ::gesturesAsync.name to AS_GLOBAL,
        ::isServiceRunning.name,
        ::ensureService.name,
        ::waitForService.name,
        ::scrollDown.name to AS_GLOBAL,
        ::scrollUp.name to AS_GLOBAL,
        ::input.name to AS_GLOBAL,
        ::setText.name to AS_GLOBAL,
        ::captureScreen.name,
        ::lockScreen.name,
        ::takeScreenshot.name,
        ::headsethook.name,
        ::accessibilityButton.name,
        ::accessibilityButtonChooser.name,
        ::accessibilityShortcut.name,
        ::accessibilityAllApps.name,
        ::dismissNotificationShade.name,
        ::back.name to AS_GLOBAL,
        ::home.name to AS_GLOBAL,
        ::powerDialog.name to AS_GLOBAL,
        ::notifications.name to AS_GLOBAL,
        ::quickSettings.name to AS_GLOBAL,
        ::recents.name to AS_GLOBAL,
        ::splitScreen.name to AS_GLOBAL,
    )

    companion object : FlexibleArray() {

        /**
         * TypeScript Declarations:
         * - click(x: number, y: number): boolean
         * - click(point: [x: number, y: number]): boolean
         * - click(text: string, index?: number): boolean
         * - click(left: number, top: number, right: number, bottom: number): boolean
         * - click(widget: UiObject): boolean
         * - click(bounds: Rect): boolean
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun click(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) { argList ->
            require(argList.isNotEmpty()) { "Arguments cannot be empty for automator.click" }
            when (argList.size) {
                1 -> when (val o = argList[0]) {
                    is Rect -> return@unwrapArguments click(scriptRuntime, arrayOf(o.centerX(), o.centerY()))
                    is UiObject -> return@unwrapArguments when {
                        o.clickable() -> o.click()
                        else -> click(scriptRuntime, arrayOf(o.bounds()))
                    }
                    is List<*> -> {
                        val (x, y) = o
                        if (x.isJsNumber()) {
                            if (y.isJsNumber()) return@unwrapArguments click(scriptRuntime, arrayOf(x, y))
                            if (y.isJsNullish()) return@unwrapArguments click(scriptRuntime, arrayOf(x, x))
                        }
                    }
                    is AndroidPoint -> return@unwrapArguments click(scriptRuntime, arrayOf(o.x, o.y))
                    is OpencvPoint -> return@unwrapArguments click(scriptRuntime, arrayOf(o.x, o.y))
                    is NativeObject -> {
                        val x = o.inquire("x", ::coerceIntNumber) ?: throw IllegalStateException(
                            "Property \"x\" for automator.click must be a valid number",
                        )
                        val y = o.inquire("y", ::coerceIntNumber) ?: throw IllegalStateException(
                            "Property \"y\" for automator.click must be a valid number",
                        )
                        return@unwrapArguments click(scriptRuntime, arrayOf(x, y))
                    }
                }
                2 -> {
                    val (x, y) = argList
                    if (x.isJsNumber() && y.isJsNumber()) {
                        return@unwrapArguments scriptRuntime.automator.click(coerceIntNumber(x), coerceIntNumber(y))
                    }
                }
            }
            return@unwrapArguments performAction(scriptRuntime, fun(target: ActionTarget) = scriptRuntime.automator.click(target), argList)
        }

        /**
         * TypeScript Declarations:
         * - longClick(x: number, y: number): boolean
         * - longClick(point: [x: number, y: number]): boolean
         * - longClick(text: string, index?: number): boolean
         * - longClick(left: number, top: number, right: number, bottom: number): boolean
         * - longClick(widget: UiObject): boolean
         * - longClick(bounds: Rect): boolean
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun longClick(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) { argList ->
            require(argList.isNotEmpty()) { "Arguments cannot be empty for automator.longClick" }
            when (argList.size) {
                1 -> when (val o = argList[0]) {
                    is Rect -> return@unwrapArguments longClick(scriptRuntime, arrayOf(o.centerX(), o.centerY()))
                    is UiObject -> return@unwrapArguments when {
                        o.longClickable() -> o.longClick()
                        else -> longClick(scriptRuntime, arrayOf(o.bounds()))
                    }
                    is List<*> -> {
                        val (x, y) = o
                        if (x.isJsNumber()) {
                            if (y.isJsNumber()) return@unwrapArguments longClick(scriptRuntime, arrayOf(x, y))
                            if (y.isJsNullish()) return@unwrapArguments longClick(scriptRuntime, arrayOf(x, x))
                        }
                    }
                    is AndroidPoint -> return@unwrapArguments longClick(scriptRuntime, arrayOf(o.x, o.y))
                    is OpencvPoint -> return@unwrapArguments longClick(scriptRuntime, arrayOf(o.x, o.y))
                    is NativeObject -> {
                        val x = o.inquire("x", ::coerceIntNumber) ?: throw IllegalStateException(
                            "Property \"x\" for automator.longClick must be a valid number",
                        )
                        val y = o.inquire("y", ::coerceIntNumber) ?: throw IllegalStateException(
                            "Property \"y\" for automator.longClick must be a valid number",
                        )
                        return@unwrapArguments longClick(scriptRuntime, arrayOf(x, y))
                    }
                }
                2 -> {
                    val (x, y) = argList
                    if (x.isJsNumber() && y.isJsNumber()) {
                        return@unwrapArguments scriptRuntime.automator.longClick(coerceIntNumber(x), coerceIntNumber(y))
                    }
                }
            }
            return@unwrapArguments performAction(scriptRuntime, fun(target: ActionTarget) = scriptRuntime.automator.longClick(target), argList)
        }

        /**
         * TypeScript Declarations:
         * - press(x: number, y: number, duration?: number): boolean
         * - press(point: [x: number, y: number], duration?: number): boolean
         * - press(duration: number, point: [x: number, y: number]): boolean
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun press(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) {
            when (it.size) {
                1 -> {
                    when {
                        it[0].isJsArray() -> {
                            val (x, y) = it[0] as List<*>
                            press(scriptRuntime, arrayOf(x, y))
                        }
                        else -> throw WrappedIllegalArgumentException("Invalid arguments [(${it.joinToString(", ")})] for automator.press")
                    }
                }
                2 -> {
                    when {
                        it[0].isJsNumber() && it[1].isJsArray() -> {
                            val (x, y) = it[1] as List<*>
                            scriptRuntime.automator.press(coerceIntNumber(x), coerceIntNumber(y), coerceIntNumber(it[0]))
                        }
                        it[0].isJsArray() && it[1].isJsNumber() -> {
                            val (x, y) = it[0] as List<*>
                            scriptRuntime.automator.press(coerceIntNumber(x), coerceIntNumber(y), coerceIntNumber(it[1]))
                        }
                        it[0].isJsNumber() && it[1].isJsNumber() -> {
                            press(scriptRuntime, arrayOf(it[0], it[1], ViewConfiguration.getTapTimeout()))
                        }
                        else -> throw WrappedIllegalArgumentException("Invalid arguments [(${it.joinToString(", ")})] for automator.press")
                    }
                }
                3 -> {
                    val (x, y, duration) = it
                    scriptRuntime.automator.press(coerceIntNumber(x), coerceIntNumber(y), coerceIntNumber(duration))
                }
                else -> throw WrappedIllegalArgumentException("Arguments length (${it.size}) is unacceptable for automator.press")
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun swipe(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) {
            swipeRhinoWithRuntime(scriptRuntime, *it)
        }

        /**
         * TypeScript Declarations:
         * - swipe(x1: number, y1: number, x2: number, y2: number, duration: number): boolean
         * - swipe(pointA: [x: number, y: number], pointB: [x: number, y: number], duration: number): boolean
         * - swipe(pointsGroup: [[x: number, y: number], [x: number, y: number]], duration: number): boolean
         * - swipe(points: [x1: number, y1: number, x2: number, y2: number], duration: number): boolean
         * - swipe(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number]): boolean
         * - swipe(duration: number, pointsGroup: [[x: number, y: number], [x: number, y: number]]): boolean
         * - swipe(duration: number, points: [x1: number, y1: number, x2: number, y2: number]): boolean
         */
        @JvmStatic
        @RhinoFunctionBody
        fun swipeRhinoWithRuntime(scriptRuntime: ScriptRuntime, vararg args: Any?): Boolean = when (args.size) {
            2 -> {
                when {
                    args[0].isJsNumber() && args[1].isJsArray() -> {
                        val (pt1, pt2) = toPointsGroup(args[1] as List<*>)
                        swipeInternal(scriptRuntime, pt1, pt2, args[0])
                    }
                    args[0].isJsArray() && args[1].isJsNumber() -> {
                        val (pt1, pt2) = toPointsGroup(args[0] as List<*>)
                        swipeInternal(scriptRuntime, pt1, pt2, args[1])
                    }
                    else -> throw WrappedIllegalArgumentException("Invalid arguments [(${args.joinToString(", ")})] for automator.swipe")
                }
            }
            3 -> {
                when {
                    args[0].isJsNumber() && args[1].isJsArray() && args[2].isJsArray() -> {
                        val (pt1, pt2) = toPointsGroup(listOf(args[1], args[2]))
                        swipeInternal(scriptRuntime, pt1, pt2, args[0])
                    }
                    args[0].isJsArray() && args[1].isJsArray() && args[2].isJsNumber() -> {
                        val (pt1, pt2) = toPointsGroup(listOf(args[0], args[1]))
                        swipeInternal(scriptRuntime, pt1, pt2, args[2])
                    }
                    else -> throw WrappedIllegalArgumentException("Invalid arguments [(${args.joinToString(", ")})] for automator.swipe")
                }
            }
            5 -> {
                val (x1, y1, x2, y2, duration) = args
                swipeInternal(scriptRuntime, x1, y1, x2, y2, duration)
            }
            else -> throw WrappedIllegalArgumentException("Arguments length (${args.size}) is unacceptable for automator.swipe")
        }

        /**
         * TypeScript Declarations:
         * - gesture(duration: number, ...point: [x: number, y: number][]): boolean
         * - gesture(duration: number, pointsGroup: [...[x: number, y: number][]]): boolean
         * - gesture(duration: number, points: [...number[]]): boolean
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun gesture(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) {
            require(it.isNotEmpty()) { "Arguments cannot be empty for automator.gesture" }
            scriptRuntime.automator.gesture(0, coerceLongNumber(it[0]), toPointsGroup(it.drop(1)).toTypedArray<IntArray>())
        }

        /**
         * TypeScript Declarations:
         * - gestureAsync(duration: number, point: [x: number, y: number], callback: GestureResultCallbackLike): void
         * - gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], callback: GestureResultCallbackLike): void
         * - gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], callback: GestureResultCallbackLike): void
         * - gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], callback: GestureResultCallbackLike): void
         * - gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], callback: GestureResultCallbackLike): void
         * - gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], callback: GestureResultCallbackLike): void
         * - gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], pointG: [x: number, y: number], callback: GestureResultCallbackLike): void
         * - gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], pointG: [x: number, y: number], pointH: [x: number, y: number], callback: GestureResultCallbackLike): void
         * - gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], pointG: [x: number, y: number], pointH: [x: number, y: number], pointI: [x: number, y: number], callback: GestureResultCallbackLike): void
         * - ...
         * - gestureAsync(duration: number, pointsGroup: [...[x: number, y: number][]], callback?: GestureResultCallbackLike): void
         * - gestureAsync(duration: number, points: [...number[]], callback?: GestureResultCallbackLike): void
         * - gestureAsync(duration: number, ...point: [x: number, y: number][]): void
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun gestureAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = unwrapArguments(args) {
            require(it.isNotEmpty()) { "Arguments cannot be empty for automator.gestureAsync" }
            val start = 0L
            val duration = coerceLongNumber(it[0])
            val last = it.last()
            when {
                isGestureResultCallbackLike(last) -> {
                    scriptRuntime.automator.gestureAsync(start, duration, toPointsGroup(it.drop(1).dropLast(1)).toTypedArray<IntArray>(), toGestureResultCallback(last))
                }
                else -> scriptRuntime.automator.gestureAsync(start, duration, toPointsGroup(it.drop(1)).toTypedArray<IntArray>())
            }
        }

        /**
         * TypeScript Declarations:
         * - gestures(...stroke: StrokeParams[]): boolean
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun gestures(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) {
            it.forEach { o -> require((o is List<*>)) { "Arguments for automator.gestures should be of type Array<Any>" } }
            scriptRuntime.automator.gestures(toStrokes(scriptRuntime, it))
        }

        /**
         * TypeScript Declarations:
         * - gesturesAsync(stroke: StrokeParams, callback: GestureResultCallbackLike): void
         * - gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, callback: GestureResultCallbackLike): void
         * - gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, callback: GestureResultCallbackLike): void
         * - gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, callback: GestureResultCallbackLike): void
         * - gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, callback: GestureResultCallbackLike): void
         * - gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, callback: GestureResultCallbackLike): void
         * - gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, strokeG: StrokeParams, callback: GestureResultCallbackLike): void
         * - gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, strokeG: StrokeParams, strokeH: StrokeParams, callback: GestureResultCallbackLike): void
         * - gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, strokeG: StrokeParams, strokeH: StrokeParams, strokeI: StrokeParams, callback: GestureResultCallbackLike): void
         * - ...
         * - gesturesAsync(strokesGroup: [...StrokeParams[]], callback?: GestureResultCallbackLike): void
         * - gesturesAsync(...stroke: StrokeParams[]): void
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun gesturesAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = unwrapArguments(args) {
            require(it.isNotEmpty()) { "Arguments cannot be empty for automator.gesturesAsync" }
            val last = it.last()
            when {
                isGestureResultCallbackLike(last) -> {
                    it.dropLast(1).forEach { o -> require((o is List<*>)) { "Arguments for automator.gesturesAsync should be of type Array<Any>" } }
                    scriptRuntime.automator.gesturesAsync(toStrokes(scriptRuntime, it.dropLast(1).toTypedArray<Any?>()), toGestureResultCallback(last))
                }
                else -> {
                    it.dropLast(1).forEach { o -> require((o is List<*>)) { "Arguments for automator.gesturesAsync should be of type Array<Any>" } }
                    scriptRuntime.automator.gesturesAsync(toStrokes(scriptRuntime, it))
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isServiceRunning(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.isServiceRunning()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ensureService(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.ensureService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForService(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsAtMost(args, 1) {
            val (timeout) = it
            waitForServiceRhino(timeout)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun waitForServiceRhino(timeout: Any?) {
            Auto.accessibilityTool.startServiceAndWaitFor(coerceLongNumber(timeout, -1L))
        }

        /**
         * TypeScript Declarations:
         * - scrollDown(): boolean
         * - scrollDown(index: number): boolean
         * - scrollDown(text: string, index?: number): boolean
         * - scrollDown(left: number, top: number, right: number, bottom: number): boolean
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun scrollDown(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) {
            when (it.size) {
                1 -> {
                    val (index) = it
                    if (index.isJsNumber()) {
                        return@unwrapArguments scriptRuntime.automator.scrollForward(coerceIntNumber(index))
                    }
                }
                0 -> return@unwrapArguments scriptRuntime.automator.scrollMaxForward()
            }
            return@unwrapArguments performAction(scriptRuntime, fun(target: ActionTarget) = scriptRuntime.automator.scrollDown(target), it)
        }

        /**
         * TypeScript Declarations:
         * - scrollUp(): boolean
         * - scrollUp(index: number): boolean
         * - scrollUp(text: string, index?: number): boolean
         * - scrollUp(left: number, top: number, right: number, bottom: number): boolean
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun scrollUp(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) {
            when (it.size) {
                1 -> {
                    val (index) = it
                    if (index.isJsNumber()) {
                        return@unwrapArguments scriptRuntime.automator.scrollBackward(coerceIntNumber(index))
                    }
                }
                0 -> return@unwrapArguments scriptRuntime.automator.scrollMaxBackward()
            }
            return@unwrapArguments performAction(scriptRuntime, fun(target: ActionTarget) = scriptRuntime.automator.scrollUp(target), it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun input(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 1..2) {
            when (it.size) {
                1 -> inputRhinoWithRuntime(scriptRuntime, it[0])
                2 -> inputRhinoWithRuntime(scriptRuntime, it[0], it[1])
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @RhinoFunctionBody
        fun inputRhinoWithRuntime(scriptRuntime: ScriptRuntime, index: Any?, text: Any?): Boolean {
            return scriptRuntime.automator.appendText(
                scriptRuntime.automator.editable(coerceIntNumber(index)),
                Context.toString(text),
            )
        }

        @JvmStatic
        @RhinoFunctionBody
        fun inputRhinoWithRuntime(scriptRuntime: ScriptRuntime, text: Any?): Boolean {
            return scriptRuntime.automator.appendText(
                scriptRuntime.automator.editable(coerceIntNumber(-1)),
                Context.toString(text),
            )
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 1..2) {
            when (it.size) {
                1 -> setTextRhinoWithRuntime(scriptRuntime, it[0])
                2 -> setTextRhinoWithRuntime(scriptRuntime, it[0], it[1])
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setTextRhinoWithRuntime(scriptRuntime: ScriptRuntime, text: Any?): Boolean {
            return scriptRuntime.automator.setText(
                scriptRuntime.automator.editable(-1),
                Context.toString(text),
            )
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setTextRhinoWithRuntime(scriptRuntime: ScriptRuntime, index: Any?, text: Any?): Boolean {
            return scriptRuntime.automator.setText(
                scriptRuntime.automator.editable(coerceIntNumber(index)),
                Context.toString(text),
            )
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun captureScreen(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsIsEmpty(args) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {

                    // TODO by SuperMonster003 on Jun 23, 2024.
                    //  ! Compatible with UI mode (async).
                    //  ! zh-CN: 兼容 UI 模式 (异步).

                    val o = RhinoUtils.callFunction(scriptRuntime, scriptRuntime.js_ResultAdapter, "wait", arrayOf(scriptRuntime.automator.captureScreen())) as NativeJavaObject
                    o.unwrap() as ImageWrapper
                }
                else -> StringUtils.str(
                    R.string.text_requires_android_os_version,
                    SdkVersionUtils.sdkIntToString(Build.VERSION_CODES.R),
                    Build.VERSION_CODES.R,
                    SdkVersionUtils.sdkIntToString(Build.VERSION.SDK_INT),
                    Build.VERSION.SDK_INT,
                ).let { throw ScriptException(it) }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun lockScreen(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.lockScreen()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun takeScreenshot(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.takeScreenshot()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun headsethook(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.headsethook()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun accessibilityButton(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.accessibilityButton()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun accessibilityButtonChooser(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.accessibilityButtonChooser()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun accessibilityShortcut(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.accessibilityShortcut()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun accessibilityAllApps(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.accessibilityAllApps()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun dismissNotificationShade(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.dismissNotificationShade()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun back(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.back()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun home(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.home()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun powerDialog(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.powerDialog()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun notifications(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.notifications()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun quickSettings(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.quickSettings()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recents(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.recents()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun splitScreen(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.automator.splitScreen()
        }

        private fun toStrokes(scriptRuntime: ScriptRuntime, argsList: Array<out Any?>): Array<GestureDescription.StrokeDescription> {
            val screenMetrics = scriptRuntime.screenMetrics
            val strokes = arrayOfNulls<GestureDescription.StrokeDescription>(argsList.size)

            argsList.forEachIndexed { i, args ->
                require(args is List<Any?>) { "Argument [$i] for Automator#toStrokes must be a JavaScript Array" }
                val startTime: Long
                val durationIndex: Int
                val pointsIndex: Int

                if (args[1] /* duration */ is Number) {
                    /* arguments: [startTime, duration, points[]] */
                    startTime = coerceLongNumber(args[0])
                    durationIndex = 1
                    pointsIndex = 2
                } else {
                    /* arguments: [duration, points[]] */
                    startTime = 0L // default value
                    durationIndex = 0
                    pointsIndex = 1
                }

                val path = Path()
                require(args[pointsIndex] is List<*>) { "Argument argsList[$pointsIndex] must be a JavaScript Array" }
                val (x, y) = args[pointsIndex] as List<*>
                path.moveTo(
                    screenMetrics.scaleX(coerceIntNumber(x)).toFloat(),
                    screenMetrics.scaleY(coerceIntNumber(y)).toFloat(),
                )

                for (j in pointsIndex + 1 until args.size) {
                    require(args[j] is List<*>) { "Argument argsList[$j] must be a JavaScript Array" }
                    val (xj, yj) = args[j] as List<*>
                    path.lineTo(
                        screenMetrics.scaleX(coerceIntNumber(xj)).toFloat(),
                        screenMetrics.scaleY(coerceIntNumber(yj)).toFloat(),
                    )
                }

                strokes[i] = GestureDescription.StrokeDescription(path, startTime, coerceLongNumber(args[durationIndex]))
            }

            return strokes.requireNoNulls()
        }

        private fun performAction(scriptRuntime: ScriptRuntime, action: (ActionTarget) -> Boolean, args: Array<out Any?>): Boolean = when (args.size) {
            1 -> performAction(scriptRuntime, action, arrayOf(args[0], -1))
            2 -> {
                val (text, index) = args
                scriptRuntime.automator.text(
                    Context.toString(text),
                    coerceIntNumber(index),
                ).let { action(it) }
            }
            4 -> {
                val (left, top, right, bottom) = args
                scriptRuntime.automator.bounds(
                    coerceIntNumber(left),
                    coerceIntNumber(top),
                    coerceIntNumber(right),
                    coerceIntNumber(bottom),
                ).let { action(it) }
            }
            else -> throw WrappedIllegalArgumentException("Invalid arguments length (${args.size}) for \"args\" of Automator#performAction")
        }

        private fun toGestureResultCallback(callback: Any?): AccessibilityService.GestureResultCallback? {
            if (!isGestureResultCallbackLike(callback)) return null
            return object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    when (callback) {
                        is BaseFunction -> withRhinoContext { context ->
                            callback.call(context, ImporterTopLevel(context), callback, arrayOf(true))
                        }
                        is NativeObject -> callback.prop("onCompleted")?.let {
                            if (it is BaseFunction) withRhinoContext { context ->
                                it.call(context, ImporterTopLevel(context), callback, arrayOf(gestureDescription))
                            }
                        }
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    when (callback) {
                        is BaseFunction -> withRhinoContext { context ->
                            callback.call(context, ImporterTopLevel(context), callback, arrayOf(false))
                        }
                        is NativeObject -> callback.prop("onCancelled")?.let {
                            if (it is BaseFunction) withRhinoContext { context ->
                                it.call(context, ImporterTopLevel(context), callback, arrayOf(gestureDescription))
                            }
                        }
                    }
                }
            }
        }

        private fun isGestureResultCallbackLike(o: Any?): Boolean {
            return o.isJsFunction() ||
                    o is NativeObject && (o.prop("onCompleted").isJsFunction() || o.prop("onCancelled").isJsFunction()) ||
                    o is AccessibilityService.GestureResultCallback
        }

        private fun toPointsGroup(args: List<Any?>): List<IntArray> {
            val group = mutableListOf<Pair<Double, Double>>()
            val argsArray = when {
                args.size == 1 && args[0] is List<*> -> args[0] as List<*>
                else -> args
            }
            if (argsArray.isNotEmpty() && argsArray.all { it is Number }) {
                if (argsArray.size % 2 != 0) {
                    throw WrappedIllegalArgumentException("Arguments cannot be converted as points as the amount of them must be even")
                }
                for (i in argsArray.indices step 2) {
                    group += coerceNumber(argsArray[i]) to coerceNumber(argsArray[i + 1])
                }
                return group.map { intArrayOf(it.first.toInt(), it.second.toInt()) }
            }
            argsArray.forEach { arg ->
                require(arg is List<*>) { "Argument \"$arg\" cannot be converted to a point as it must be a JavaScript Array" }
                require(arg.isNotEmpty()) { "Argument \"[]\" cannot be converted to a point as it has no elements" }
                when {
                    arg.all { it is List<*> } -> group.addAll(toPointsGroup(arg).map {
                        coerceNumber(it[0]) to coerceNumber(it[1])
                    })
                    else -> when (arg.size) {
                        1 -> group.add(coerceNumber(arg[0]) to coerceNumber(arg[0]))
                        2 -> group.add(coerceNumber(arg[0]) to coerceNumber(arg[1]))
                        else -> throw WrappedIllegalArgumentException("Argument \"[$arg]\" cannot be converted to a point as it has too much elements")
                    }
                }
            }
            return group.map { intArrayOf(it.first.toInt(), it.second.toInt()) }
        }

        private fun swipeInternal(scriptRuntime: ScriptRuntime, pt1: IntArray, pt2: IntArray, duration: Any?): Boolean {
            val (xA, yA) = pt1
            val (xB, yB) = pt2
            return swipeInternal(scriptRuntime, xA, yA, xB, yB, duration)
        }

        private fun swipeInternal(scriptRuntime: ScriptRuntime, x1: Any?, y1: Any?, x2: Any?, y2: Any?, duration: Any?): Boolean {
            return scriptRuntime.automator.swipe(
                coerceIntNumber(x1),
                coerceIntNumber(y1),
                coerceIntNumber(x2),
                coerceIntNumber(y2),
                coerceIntNumber(duration),
            )
        }

    }

}
