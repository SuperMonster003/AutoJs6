package com.stardust.autojs.core.accessibility;

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ARGUMENT_COLUMN_INT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ARGUMENT_ROW_INT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLEAR_FOCUS;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_COLLAPSE;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_COPY;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CUT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_DISMISS;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_EXPAND;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_FOCUS;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_LONG_CLICK;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_PASTE;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SELECT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SET_SELECTION;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SET_TEXT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CONTEXT_CLICK;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_DOWN;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_LEFT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_RIGHT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_TO_POSITION;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_UP;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SET_PROGRESS;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SHOW_ON_SCREEN;

import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.BuildConfig;
import com.stardust.autojs.R;
import com.stardust.autojs.annotation.ScriptInterface;
import com.stardust.autojs.runtime.exception.ScriptInterruptedException;
import com.stardust.automator.ActionArgument;
import com.stardust.automator.UiGlobalSelector;
import com.stardust.automator.UiObject;
import com.stardust.automator.UiObjectCollection;
import com.stardust.automator.filter.Filter;
import com.stardust.concurrent.VolatileBox;
import com.stardust.view.accessibility.AccessibilityNodeInfoAllocator;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stardust on 2017/3/9.
 */
public class UiSelector extends UiGlobalSelector {

    private static final String TAG = "UiSelector";

    private final AccessibilityBridge mAccessibilityBridge;
    private AccessibilityNodeInfoAllocator mAllocator = null;

    public UiSelector(AccessibilityBridge accessibilityBridge) {
        mAccessibilityBridge = accessibilityBridge;
    }

    public UiSelector(AccessibilityBridge accessibilityBridge, AccessibilityNodeInfoAllocator allocator) {
        mAccessibilityBridge = accessibilityBridge;
        mAllocator = allocator;
    }

    protected UiObjectCollection find(int max) {
        mAccessibilityBridge.ensureServiceEnabled();
        if ((mAccessibilityBridge.getFlags() & AccessibilityBridge.FLAG_FIND_ON_UI_THREAD) != 0
                && Looper.myLooper() != Looper.getMainLooper()) {
            VolatileBox<UiObjectCollection> result = new VolatileBox<>();
            mAccessibilityBridge.post(() -> result.setAndNotify(findImpl(max)));
            return result.blockedGet();
        }
        return findImpl(max);
    }

    @NonNull
    @ScriptInterface
    public UiObjectCollection find() {
        return find(Integer.MAX_VALUE);
    }

    @NonNull
    @ScriptInterface
    protected UiObjectCollection findImpl(int max) {
        List<AccessibilityNodeInfo> roots = mAccessibilityBridge.windowRoots();
        if (BuildConfig.DEBUG)
            Log.d(TAG, "find: roots = " + roots);
        if (roots.isEmpty()) {
            return UiObjectCollection.Companion.getEMPTY();
        }
        List<UiObject> result = new ArrayList<>();
        for (AccessibilityNodeInfo root : roots) {
            if (root == null) {
                continue;
            }
            if (root.getPackageName() != null && mAccessibilityBridge.getConfig().whiteListContains(root.getPackageName().toString())) {
                Log.d(TAG, "package in white list, return null");
                return UiObjectCollection.Companion.getEMPTY();
            }
            result.addAll(findAndReturnList(UiObject.Companion.createRoot(root, mAllocator), max - result.size()));
            if (result.size() >= max) {
                break;
            }
        }
        return UiObjectCollection.Companion.of(result);
    }

    @NonNull
    @Override
    public UiGlobalSelector textMatches(@NotNull String regex) {
        return super.textMatches(convertRegex(regex));
    }

    // @Overwrite by SuperMonster003 on May 5, 2022.
    //  ! Dunno what "better implementation" really means.
    //  ! The only thing I did was make code more "lightweight". ;)
    // @TodoDiary by Stardust on Jan 30, 2018.
    //  ! 更好的实现方式。
    @NonNull
    private String convertRegex(@NonNull String regex) {
        String pattern = "^/(.+)/$";
        return regex.matches(pattern) ? regex.replaceAll(pattern, "$1") : regex;
    }

    private boolean isUiThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    @NonNull
    @Override
    public UiGlobalSelector classNameMatches(@NotNull String regex) {
        return super.classNameMatches(convertRegex(regex));
    }

    @NonNull
    @Override
    public UiGlobalSelector idMatches(@NotNull String regex) {
        return super.idMatches(convertRegex(regex));
    }

    @NonNull
    @Override
    public UiGlobalSelector packageNameMatches(@NotNull String regex) {
        return super.packageNameMatches(convertRegex(regex));
    }

    @NonNull
    @Override
    public UiGlobalSelector descMatches(@NotNull String regex) {
        return super.descMatches(convertRegex(regex));
    }

    @ScriptInterface
    @NonNull
    public UiObjectCollection untilFind() {
        if (isUiThread()) {
            throw new IllegalThreadStateException(GlobalAppContext.getString(R.string.error_function_called_in_ui_thread, "untilFind"));
        }
        UiObjectCollection uiObjectCollection = find();
        while (uiObjectCollection.empty()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ScriptInterruptedException();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new ScriptInterruptedException();
            }
            uiObjectCollection = find();
        }
        return uiObjectCollection;
    }

    @ScriptInterface
    public UiObject findOne(long timeout) {
        if (isUiThread()) {
            throw new IllegalThreadStateException(GlobalAppContext.getString(R.string.error_function_called_in_ui_thread, "findOne"));
        }
        UiObjectCollection uiObjectCollection = find(1);
        long start = SystemClock.uptimeMillis();
        while (uiObjectCollection.empty()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ScriptInterruptedException();
            }
            if (timeout > 0 && SystemClock.uptimeMillis() - start > timeout) {
                return null;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new ScriptInterruptedException();
            }
            uiObjectCollection = find(1);
        }
        return uiObjectCollection.get(0);
    }

    public UiObject findOnce() {
        return findOnce(0);
    }

    public UiObject findOnce(int index) {
        UiObjectCollection uiObjectCollection = find(index + 1);
        if (index >= uiObjectCollection.size()) {
            return null;
        }
        return uiObjectCollection.get(index);
    }

    @ScriptInterface
    public UiObject findOne() {
        return untilFindOne();
    }

    @ScriptInterface
    public boolean exists() {
        UiObjectCollection collection = find();
        return collection.nonEmpty();
    }

    @NonNull
    public UiObject untilFindOne() {
        return findOne(-1);
    }

    @ScriptInterface
    public void waitFor() {
        untilFind();
    }

    @NonNull
    @ScriptInterface
    public UiSelector id(@NotNull final String id) {
        if (!id.contains(":")) {
            addFilter(new Filter() {
                @Override
                public boolean filter(@NotNull UiObject node) {
                    String fullId = mAccessibilityBridge.getInfoProvider().getLatestPackage() + ":id/" + id;
                    return fullId.equals(node.getViewIdResourceName());
                }

                @NonNull
                @Override
                public String toString() {
                    return "id(\"" + id + "\")";
                }
            });
        } else {
            super.id(id);
        }
        return this;
    }

    @NonNull
    @Override
    public UiGlobalSelector idStartsWith(@NotNull String prefix) {
        if (!prefix.contains(":")) {
            addFilter(new Filter() {
                @Override
                public boolean filter(@NotNull UiObject nodeInfo) {
                    String fullIdPrefix = mAccessibilityBridge.getInfoProvider().getLatestPackage() + ":id/" + prefix;
                    String id = nodeInfo.getViewIdResourceName();
                    return id != null && id.startsWith(fullIdPrefix);
                }

                @NonNull
                @Override
                public String toString() {
                    return "idStartsWith(\"" + prefix + "\")";
                }
            });
        } else {
            super.idStartsWith(prefix);
        }
        return this;
    }

    private boolean performAction(int action, ActionArgument... arguments) {
        return untilFind().performAction(action, arguments);
    }

    @ScriptInterface
    public boolean click() {
        return performAction(ACTION_CLICK);
    }

    @ScriptInterface
    public boolean longClick() {
        return performAction(ACTION_LONG_CLICK);
    }

    @ScriptInterface
    public boolean accessibilityFocus() {
        return performAction(ACTION_ACCESSIBILITY_FOCUS);
    }

    @ScriptInterface
    public boolean clearAccessibilityFocus() {
        return performAction(ACTION_CLEAR_ACCESSIBILITY_FOCUS);
    }

    @ScriptInterface
    public boolean focus() {
        return performAction(ACTION_FOCUS);
    }

    @ScriptInterface
    public boolean clearFocus() {
        return performAction(ACTION_CLEAR_FOCUS);
    }

    @ScriptInterface
    public boolean copy() {
        return performAction(ACTION_COPY);
    }

    @ScriptInterface
    public boolean paste() {
        return performAction(ACTION_PASTE);
    }

    @ScriptInterface
    public boolean select() {
        return performAction(ACTION_SELECT);
    }

    @ScriptInterface
    public boolean cut() {
        return performAction(ACTION_CUT);
    }

    @ScriptInterface
    public boolean collapse() {
        return performAction(ACTION_COLLAPSE);
    }

    @ScriptInterface
    public boolean expand() {
        return performAction(ACTION_EXPAND);
    }

    @ScriptInterface
    public boolean dismiss() {
        return performAction(ACTION_DISMISS);
    }

    @ScriptInterface
    public boolean show() {
        return performAction(ACTION_SHOW_ON_SCREEN.getId());
    }

    @ScriptInterface
    public boolean scrollForward() {
        return performAction(ACTION_SCROLL_FORWARD);
    }

    @ScriptInterface
    public boolean scrollBackward() {
        return performAction(ACTION_SCROLL_BACKWARD);
    }

    @ScriptInterface
    public boolean scrollUp() {
        return performAction(ACTION_SCROLL_UP.getId());
    }

    @ScriptInterface
    public boolean scrollDown() {
        return performAction(ACTION_SCROLL_DOWN.getId());
    }

    @ScriptInterface
    public boolean scrollLeft() {
        return performAction(ACTION_SCROLL_LEFT.getId());
    }

    @ScriptInterface
    public boolean scrollRight() {
        return performAction(ACTION_SCROLL_RIGHT.getId());
    }

    @ScriptInterface
    public boolean contextClick() {
        return performAction(ACTION_CONTEXT_CLICK.getId());
    }

    @ScriptInterface
    public boolean setSelection(int s, int e) {
        return performAction(ACTION_SET_SELECTION,
                new ActionArgument.IntActionArgument(ACTION_ARGUMENT_SELECTION_START_INT, s),
                new ActionArgument.IntActionArgument(ACTION_ARGUMENT_SELECTION_END_INT, e));
    }

    @ScriptInterface
    public boolean setText(String text) {
        return performAction(ACTION_SET_TEXT,
                new ActionArgument.CharSequenceActionArgument(ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text));
    }

    @ScriptInterface
    public boolean setProgress(float value) {
        return performAction(ACTION_SET_PROGRESS.getId(),
                new ActionArgument.FloatActionArgument(ACTION_ARGUMENT_PROGRESS_VALUE, value));
    }

    @ScriptInterface
    public boolean scrollTo(int row, int column) {
        return performAction(ACTION_SCROLL_TO_POSITION.getId(),
                new ActionArgument.IntActionArgument(ACTION_ARGUMENT_ROW_INT, row),
                new ActionArgument.IntActionArgument(ACTION_ARGUMENT_COLUMN_INT, column));
    }

}