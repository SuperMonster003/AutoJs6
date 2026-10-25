package org.autojs.autojs.runtime.api.modules.automator

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
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsArray
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsFunction
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNumber
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.toolkit.RuntimeToolkit
import org.autojs.autojs.runtime.api.modules.toolkit.ToolkitApi
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.getDeclarationScope
import org.autojs.autojs.util.SdkVersionUtils
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import android.graphics.Point as AndroidPoint
import org.opencv.core.Point as OpencvPoint

@Suppress("unused", "SameParameterValue", "UNUSED_PARAMETER")
class Automator(scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    companion object : ArgumentGuards() {

        // The toolkit (roadmap P4.2, Appendix B). Every call is `(positionals..., options?)`; the target of a
        // search is any `pickup` selector form, a `UiObject` or a function; `options.timeout` (default 0, a
        // single look) / `interval` / `root` / `humanize` are common; a failure throws a `FlowError`
        // (`code` / `step` / `selector` / `reason`). The UI thread is refused (the tools may block).
        // zh-CN: 工具集 (Roadmap P4.2, 附录 B). 每个调用形如 `(位置参数..., options?)`; 搜索目标为 `pickup` 任一选择器形式, `UiObject` 或函数;
        // `options.timeout` (默认 0, 单次查找) / `interval` / `root` / `humanize` 为公共选项; 失败抛出 `FlowError` (`code` / `step` /
        // `selector` / `reason`). 拒绝在 UI 线程调用 (工具可能阻塞).

        private fun toolkit(scriptRuntime: ScriptRuntime): ToolkitApi = RuntimeToolkit.of(scriptRuntime).api

        // Event-driven waits (roadmap P5.2): the synchronous forms of the Flow steps of the same names; each blocks the
        // calling (non-UI) thread and throws a `FlowError` on timeout. zh-CN: 事件驱动等待 (Roadmap P5.2): 同名 Flow 步骤的同步形式;
        // 各阻塞调用线程 (不能是 UI 线程), 超时抛出 `FlowError`.

        /** `waitForIdle(quietFor? | { quietFor?, timeout?, eventTypes? })`: until no window event for `quietFor` ms; `{ quietFor, elapsed, events }`. zh-CN: `waitForIdle(quietFor? | { quietFor?, timeout?, eventTypes? })`: 直到窗口事件已有 `quietFor` 毫秒未到达; `{ quietFor, elapsed, events }`. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForIdle(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = EventWaitApi(scriptRuntime).sync(EventWaitApi.STEP_IDLE, args)

        /** `waitForEvent(type?, filter?, timeout?)`: the next accessibility event of `type` (a name, an array, `"*"`) the filter accepts. zh-CN: `waitForEvent(type?, filter?, timeout?)`: 下一个过滤器接受的 `type` (名称, 数组, `"*"`) 无障碍事件. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForEvent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = EventWaitApi(scriptRuntime).sync(EventWaitApi.STEP_EVENT, args)

        /** `waitForToast(text?, timeout?)`: the next toast (of another app) whose text contains / matches `text`. zh-CN: `waitForToast(text?, timeout?)`: 下一个文本包含 / 匹配 `text` 的 (其它应用的) toast. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForToast(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = EventWaitApi(scriptRuntime).sync(EventWaitApi.STEP_TOAST, args)

        /** `waitForNotification(filter?, timeout?)`: the next notification the filter (text, regex, function or `{ packageName?, title?, text? }`) accepts. zh-CN: `waitForNotification(filter?, timeout?)`: 下一个过滤器 (文本, 正则, 函数或 `{ packageName?, title?, text? }`) 接受的通知. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForNotification(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = EventWaitApi(scriptRuntime).sync(EventWaitApi.STEP_NOTIFICATION, args)

        /** `smartClick(target, options?)`: click, or a clickable ancestor, or a tap; `{ ok, method, target, verified }`. zh-CN: `smartClick(target, options?)`: 点击, 或可点击祖先, 或点按手势; `{ ok, method, target, verified }`. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun smartClick(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).smartClick(args)

        /** Direct bounds tap with optional verification. zh-CN: 直接点按节点中心, 可选点击后校验. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun smartClickBounds(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).smartClickBounds(args)

        /** `clickIfExists(target, options? | timeout?)`: whether it was there and got clicked. zh-CN: `clickIfExists(target, options? | timeout?)`: 目标是否存在并被点击. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clickIfExists(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).clickIfExists(args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clickBoundsIfExists(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).clickBoundsIfExists(args)

        /** `clickAny(targets, options? | timeout?)`: the first present, clicked; `{ index, target, node, method }` or `null`. zh-CN: `clickAny(targets, options? | timeout?)`: 点击首个存在的; `{ index, target, node, method }` 或 `null`. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clickAny(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).clickAny(args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clickBoundsAny(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).clickBoundsAny(args)

        /** `findAny(targets, options? | timeout?)`: `{ index, target, node }` of the first present, or `null`. zh-CN: `findAny(targets, options? | timeout?)`: 首个存在的 `{ index, target, node }`, 或 `null`. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findAny(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).findAny(args)

        /** `scrollUntil(target, options?)`: scrolls a container until the target shows; the node. zh-CN: `scrollUntil(target, options?)`: 滚动容器直到目标出现; 返回节点. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun scrollUntil(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).scrollUntil(args)

        /** `typeInto(target, text, options?)`: types into a field (selector / node / index / `null` for the focused one); the node. zh-CN: `typeInto(target, text, options?)`: 向输入框 (选择器 / 节点 / 索引 / `null` 为焦点所在) 输入; 返回节点. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun typeInto(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).typeInto(args)

        /** `dismissPopups(targets, options? | timeout?)`: closes popups once, all, or as a guard (`watch: true`). zh-CN: `dismissPopups(targets, options? | timeout?)`: 关闭弹窗一次, 全部, 或作为守护 (`watch: true`). */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun dismissPopups(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).dismissPopups(args)

        /** `collectList(container, item, options?)`: scrolls a list and collects its items; an array. zh-CN: `collectList(container, item, options?)`: 滚动列表并采集各项; 返回数组. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun collectList(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).collectList(args)

        /** `launchAndWait(app, options?)`: launches and waits for the package in front; `{ packageName, launched, elapsed, attempts }`. zh-CN: `launchAndWait(app, options?)`: 启动并等待其包名到前台; `{ packageName, launched, elapsed, attempts }`. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchAndWait(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).launchAndWait(args)

        /** `backUntil(cond, options? | maxTimes?)`: presses back until the condition holds; what it yielded. zh-CN: `backUntil(cond, options? | maxTimes?)`: 反复返回直到条件满足; 返回条件的值. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun backUntil(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).backUntil(args)

        /** `backToApp(app, options? | maxTimes?)`: presses back until the app is in front; its package name. zh-CN: `backToApp(app, options? | maxTimes?)`: 反复返回直到该应用在前台; 返回其包名. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun backToApp(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).backToApp(args)

        /** `toggle(target, checked, options?)`: clicks a checkable only when its state differs, then verifies; `{ node, changed, checked, method }`. zh-CN: `toggle(target, checked, options?)`: 仅在状态不一致时点击可勾选控件, 然后校验; `{ node, changed, checked, method }`. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toggle(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).toggle(args)

        /** `retry(fn, options? | times?)`: reruns `fn(attempt)` on failure (`times` reruns, `delay` x `backoff`); its result. zh-CN: `retry(fn, options? | times?)`: 失败时重跑 `fn(attempt)` (`times` 次, `delay` 按 `backoff` 递增); 返回其结果. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun retry(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = toolkit(scriptRuntime).retry(args)

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
                    scriptRuntime.automator.gestureAsync(
                        start = start,
                        duration = duration,
                        points = toPointsGroup(it.drop(1).dropLast(1)).toTypedArray<IntArray>(),
                        callback = toGestureResultCallback(scriptRuntime, last),
                    )
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
                    scriptRuntime.automator.gesturesAsync(
                        strokes = toStrokes(scriptRuntime, it.dropLast(1).toTypedArray<Any?>()),
                        callback = toGestureResultCallback(scriptRuntime, last),
                    )
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
        fun switchToInputMethodWithId(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            scriptRuntime.automator.switchToInputMethodWithId(coerceString(it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun switchToInputMethod(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            scriptRuntime.automator.switchToInputMethod(coerceString(it))
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

        private fun toGestureResultCallback(scriptRuntime: ScriptRuntime, callback: Any?): AccessibilityService.GestureResultCallback? {
            if (callback is AccessibilityService.GestureResultCallback) return callback
            if (!isGestureResultCallbackLike(callback)) return null
            return object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    when (callback) {
                        is Callable -> callGestureCallback(scriptRuntime, callback, true)
                        is NativeObject -> callback.prop("onCompleted")?.let {
                            if (it is Callable) {
                                RhinoUtils.callFunction(scriptRuntime, it, getDeclarationScope(callback), callback, arrayOf(gestureDescription))
                            }
                        }
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    when (callback) {
                        is Callable -> callGestureCallback(scriptRuntime, callback, false)
                        is NativeObject -> callback.prop("onCancelled")?.let {
                            if (it is Callable) {
                                RhinoUtils.callFunction(scriptRuntime, it, getDeclarationScope(callback), callback, arrayOf(gestureDescription))
                            }
                        }
                    }
                }
            }
        }

        private fun callGestureCallback(scriptRuntime: ScriptRuntime, callback: Callable, result: Boolean) {
            val callbackObject = callback as? Scriptable
            val scope = callbackObject?.let(::getDeclarationScope) ?: scriptRuntime.topLevelScope
            val thisObj = callbackObject ?: scriptRuntime.topLevelScope.globalThis
            RhinoUtils.callFunction(scriptRuntime, callback, scope, thisObj, arrayOf(result))
        }

        internal fun isGestureResultCallbackLike(o: Any?): Boolean {
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
