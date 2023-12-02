package org.autojs.autojs.core.record.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.autojs.autojs.core.accessibility.AccessibilityNodeInfoHelper;
import org.autojs.autojs.core.accessibility.NodeInfo;
import org.autojs.autojs.core.automator.UiObject;
import org.autojs.autojs.core.automator.action.FilterAction;
import org.autojs.autojs.tool.SparseArrayEntries;
import org.greenrobot.eventbus.EventBus;

import java.util.List;

/**
 * Created by Stardust on Feb 14, 2017.
 */
public class AccessibilityActionConverter {

    private static final SparseArray<EventToScriptConverter> CONVERTER_MAP = new SparseArrayEntries<EventToScriptConverter>()
            .entry(AccessibilityEvent.TYPE_VIEW_CLICKED, new DoUtilSucceedConverter("click"))
            .entry(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED, new DoUtilSucceedConverter("longClick"))
            .entry(AccessibilityEvent.TYPE_VIEW_SCROLLED, new DoOnceConverter("//scroll???"))
            .entry(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED, new SetTextEventConverter())
            .sparseArray();

    static {
        CONVERTER_MAP.put(AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED, new DoOnceConverter("contextClick"));
    }

    private final StringBuilder mScript = new StringBuilder();
    private boolean mFirstAction = true;

    public AccessibilityActionConverter(boolean shouldIgnoreFirstAction) {
        mShouldIgnoreFirstAction = shouldIgnoreFirstAction;
    }

    private boolean mShouldIgnoreFirstAction = false;

    public void record(AccessibilityService service, AccessibilityEvent event) {
        EventToScriptConverter converter = CONVERTER_MAP.get(event.getEventType());
        if (converter != null) {
            if (mFirstAction && mShouldIgnoreFirstAction) {
                mFirstAction = false;
                return;
            }
            converter.onAccessibilityEvent(service, event, mScript);
            mScript.append("\n");
            EventBus.getDefault().post(new AccessibilityActionRecorder.AccessibilityActionRecordEvent(event));
        }
    }

    public String getScript() {
        return mScript.toString();
    }

    public void onResume() {
        mFirstAction = true;
    }

    interface EventToScriptConverter {

        void onAccessibilityEvent(AccessibilityService service, AccessibilityEvent event, StringBuilder sb);
    }

    private static abstract class BoundsEventConverter implements EventToScriptConverter {

        @Override
        public void onAccessibilityEvent(AccessibilityService service, AccessibilityEvent event, StringBuilder sb) {
            AccessibilityNodeInfo source = event.getSource();
            if (source == null)
                return;
            String bounds = NodeInfo.Companion.boundsToString(AccessibilityNodeInfoHelper.getBoundsInScreen(source));
            source.recycle();
            onAccessibilityEvent(event, bounds, sb);
        }

        protected abstract void onAccessibilityEvent(AccessibilityEvent event, String bounds, StringBuilder sb);

    }

    private static class DoOnceConverter extends BoundsEventConverter {

        private final String mActionFunction;

        DoOnceConverter(String actionFunction) {
            mActionFunction = actionFunction;
        }

        @Override
        protected void onAccessibilityEvent(AccessibilityEvent event, String bounds, StringBuilder sb) {
            sb.append(mActionFunction).append(bounds).append(";");
        }
    }

    private static class DoUtilSucceedConverter extends BoundsEventConverter {

        private final String mActionFunction;

        DoUtilSucceedConverter(String actionFunction) {
            mActionFunction = actionFunction;
        }

        @Override
        protected void onAccessibilityEvent(AccessibilityEvent event, String bounds, StringBuilder sb) {
            sb.append("while(!").append(mActionFunction).append(bounds).append(");");
        }
    }

    private static class SetTextEventConverter implements EventToScriptConverter {

        @Override
        public void onAccessibilityEvent(AccessibilityService service, AccessibilityEvent event, StringBuilder sb) {
            AccessibilityNodeInfo source = event.getSource();
            if (source == null)
                return;
            UiObject uiObject = UiObject.createRoot(service.getRootInActiveWindow());
            List<UiObject> editableList = FilterAction.EditableFilter.Companion.findEditable(uiObject);
            int i = findInEditableList(editableList, source);
            sb.append("while(!input(").append(i).append(", \"").append(source.getText()).append("\"));");
            source.recycle();
        }

        private static int findInEditableList(List<UiObject> editableList, AccessibilityNodeInfo editable) {
            int i = 0;
            for (UiObject nodeInfo : editableList) {
                if (AccessibilityNodeInfoHelper.getBoundsInScreen(nodeInfo).equals(AccessibilityNodeInfoHelper.getBoundsInScreen(editable))) {
                    return i;
                }
                i++;
            }
            return -1;
        }
    }

}
