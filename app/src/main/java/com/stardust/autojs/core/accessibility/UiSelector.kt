package com.stardust.autojs.core.accessibility

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.stardust.autojs.BuildConfig
import com.stardust.autojs.annotation.ScriptInterface
import com.stardust.autojs.runtime.exception.ScriptInterruptedException
import com.stardust.automator.ActionArgument
import com.stardust.automator.UiGlobalSelector
import com.stardust.automator.UiObject
import com.stardust.automator.UiObjectCollection
import com.stardust.automator.filter.Filter
import com.stardust.concurrent.VolatileBox
import com.stardust.view.accessibility.AccessibilityNodeInfoAllocator

/**
 * Created by Stardust on 2017/3/9.
 */
class UiSelector : UiGlobalSelector {
    private val mAccessibilityBridge: AccessibilityBridge
    private var mAllocator: AccessibilityNodeInfoAllocator? = null

    constructor(accessibilityBridge: AccessibilityBridge) {
        mAccessibilityBridge = accessibilityBridge
    }

    constructor(accessibilityBridge: AccessibilityBridge, allocator: AccessibilityNodeInfoAllocator?) {
        mAccessibilityBridge = accessibilityBridge
        mAllocator = allocator
    }

    protected fun find(max: Int): UiObjectCollection {
        ensureAccessibilityServiceEnabled()
        if ((mAccessibilityBridge.getFlags() and AccessibilityBridge.FLAG_FIND_ON_UI_THREAD) != 0
            && Looper.myLooper() != Looper.getMainLooper()
        ) {
            val result = VolatileBox<UiObjectCollection>()
            mAccessibilityBridge.post(Runnable { result.setAndNotify(findImpl(max)) })
            return result.blockedGet()
        }
        return findImpl(max)
    }

    @ScriptInterface
    fun find(): UiObjectCollection {
        return find(Int.Companion.MAX_VALUE)
    }

    @ScriptInterface
    protected fun findImpl(max: Int): UiObjectCollection {
        val roots: MutableList<AccessibilityNodeInfo?> = mAccessibilityBridge.windowRoots()
        if (BuildConfig.DEBUG) Log.d(TAG, "find: roots = " + roots)
        if (roots.isEmpty()) {
            return UiObjectCollection.Companion.EMPTY
        }
        val result: MutableList<UiObject?> = ArrayList<UiObject?>()
        for (root in roots) {
            if (root == null) {
                continue
            }
            if (root.getPackageName() != null && mAccessibilityBridge.getConfig().whiteListContains(root.getPackageName().toString())) {
                Log.d(TAG, "package in white list, return null")
                return UiObjectCollection.Companion.EMPTY
            }
            result.addAll(findAndReturnList(UiObject.Companion.createRoot(root, mAllocator), max - result.size))
            if (result.size >= max) {
                break
            }
        }
        return UiObjectCollection.Companion.of(result)
    }

    public override fun textMatches(regex: String): UiGlobalSelector {
        return super.textMatches(convertRegex(regex))
    }

    // TODO: 2018/1/30 更好的实现方式。
    private fun convertRegex(regex: String): String {
        if (regex.startsWith("/") && regex.endsWith("/") && regex.length > 2) {
            return regex.substring(1, regex.length - 1)
        }
        return regex
    }


    public override fun classNameMatches(regex: String): UiGlobalSelector {
        return super.classNameMatches(convertRegex(regex))
    }

    public override fun idMatches(regex: String): UiGlobalSelector {
        return super.idMatches(convertRegex(regex))
    }

    public override fun packageNameMatches(regex: String): UiGlobalSelector {
        return super.packageNameMatches(convertRegex(regex))
    }

    public override fun descMatches(regex: String): UiGlobalSelector {
        return super.descMatches(convertRegex(regex))
    }

    private fun ensureAccessibilityServiceEnabled() {
        mAccessibilityBridge.ensureServiceEnabled()
    }

    @ScriptInterface
    fun untilFind(): UiObjectCollection {
        ensureNonUiThread()
        var uiObjectCollection = find()
        while (uiObjectCollection.empty()) {
            if (Thread.currentThread().isInterrupted()) {
                throw ScriptInterruptedException()
            }
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                throw ScriptInterruptedException()
            }
            uiObjectCollection = find()
        }
        return uiObjectCollection
    }

    private fun ensureNonUiThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // TODO: 2018/11/1 配置字符串
            throw IllegalThreadStateException("不能在ui线程执行阻塞操作, 请在子线程或子脚本执行findOne()或untilFind()")
        }
    }

    @ScriptInterface
    fun findOne(timeout: Long): UiObject? {
        var uiObjectCollection = find(1)
        val start = SystemClock.uptimeMillis()
        while (uiObjectCollection.empty()) {
            if (Thread.currentThread().isInterrupted()) {
                throw ScriptInterruptedException()
            }
            if (timeout > 0 && SystemClock.uptimeMillis() - start > timeout) {
                return null
            }
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                throw ScriptInterruptedException()
            }
            uiObjectCollection = find(1)
        }
        return uiObjectCollection.get(0)
    }

    fun findOnce(): UiObject? {
        return findOnce(0)
    }

    fun findOnce(index: Int): UiObject? {
        val uiObjectCollection = find(index + 1)
        if (index >= uiObjectCollection.size()) {
            return null
        }
        return uiObjectCollection.get(index)
    }

    @ScriptInterface
    fun findOne(): UiObject {
        return untilFindOne()
    }

    @ScriptInterface
    fun exists(): Boolean {
        val collection = find()
        return collection.nonEmpty()
    }

    fun untilFindOne(): UiObject {
        return findOne(-1)!!
    }

    @ScriptInterface
    fun waitFor() {
        untilFind()
    }

    @ScriptInterface
    public override fun id(id: String): UiSelector {
        if (!id.contains(":")) {
            addFilter(object : Filter {
                override fun filter(node: UiObject): Boolean {
                    val fullId = mAccessibilityBridge.getInfoProvider().latestPackage + ":id/" + id
                    return fullId == node.getViewIdResourceName()
                }

                override fun toString(): String {
                    return "id(\"" + id + "\")"
                }
            })
        } else {
            super.id(id)
        }
        return this
    }

    public override fun idStartsWith(prefix: String): UiGlobalSelector {
        if (!prefix.contains(":")) {
            addFilter(object : Filter {
                override fun filter(nodeInfo: UiObject): Boolean {
                    val fullIdPrefix = mAccessibilityBridge.getInfoProvider().latestPackage + ":id/" + prefix
                    val id = nodeInfo.getViewIdResourceName()
                    return id != null && id.startsWith(fullIdPrefix)
                }

                override fun toString(): String {
                    return "idStartsWith(\"" + prefix + "\")"
                }
            })
        } else {
            super.idStartsWith(prefix)
        }
        return this
    }

    private fun performAction(action: Int, vararg arguments: ActionArgument): Boolean {
        return untilFind().performAction(action, *arguments)
    }


    @ScriptInterface
    fun click(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
    }

    @ScriptInterface
    fun longClick(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
    }

    @ScriptInterface
    fun accessibilityFocus(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)
    }

    @ScriptInterface
    fun clearAccessibilityFocus(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
    }

    @ScriptInterface
    fun focus(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_FOCUS)
    }

    @ScriptInterface
    fun clearFocus(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_CLEAR_FOCUS)
    }

    @ScriptInterface
    fun copy(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_COPY)
    }

    @ScriptInterface
    fun paste(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_PASTE)
    }

    @ScriptInterface
    fun select(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_SELECT)
    }

    @ScriptInterface
    fun cut(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_CUT)
    }

    @ScriptInterface
    fun collapse(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_COLLAPSE)
    }

    @ScriptInterface
    fun expand(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_EXPAND)
    }

    @ScriptInterface
    fun dismiss(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_DISMISS)
    }

    @ScriptInterface
    fun show(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SHOW_ON_SCREEN.getId())
    }

    @ScriptInterface
    fun scrollForward(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
    }

    @ScriptInterface
    fun scrollBackward(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
    }

    @ScriptInterface
    fun scrollUp(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_UP.getId())
    }

    @ScriptInterface
    fun scrollDown(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_DOWN.getId())
    }

    @ScriptInterface
    fun scrollLeft(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_LEFT.getId())
    }

    @ScriptInterface
    fun scrollRight(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_RIGHT.getId())
    }

    @ScriptInterface
    fun contextClick(): Boolean {
        return performAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CONTEXT_CLICK.getId())
    }

    @ScriptInterface
    fun setSelection(s: Int, e: Int): Boolean {
        return performAction(
            AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
            ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, s),
            ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, e)
        )
    }

    @ScriptInterface
    fun setText(text: String): Boolean {
        return performAction(
            AccessibilityNodeInfoCompat.ACTION_SET_TEXT,
            ActionArgument.CharSequenceActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        )
    }

    @ScriptInterface
    fun setProgress(value: Float): Boolean {
        return performAction(
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SET_PROGRESS.getId(),
            ActionArgument.FloatActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE, value)
        )
    }

    @ScriptInterface
    fun scrollTo(row: Int, column: Int): Boolean {
        return performAction(
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_TO_POSITION.getId(),
            ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_ROW_INT, row),
            ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_COLUMN_INT, column)
        )
    }

    companion object {
        private const val TAG = "UiSelector"
    }
}
