package org.autojs.autojs.core.ui.nativeview;

import android.view.View;
import org.autojs.autojs.core.ui.JsViewHelper;
import org.autojs.autojs.core.ui.ViewExtras;
import org.autojs.autojs.core.ui.attribute.ViewAttributes;
import org.autojs.autojs.rhino.NativeJavaObjectWithPrototype;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.util.RhinoUtils;
import org.mozilla.javascript.Scriptable;

public class NativeView extends NativeJavaObjectWithPrototype {

    public static class ScrollEvent {
        public int scrollX;
        public int scrollY;
        public int oldScrollX;
        public int oldScrollY;

        public ScrollEvent(int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.oldScrollX = oldScrollX;
            this.oldScrollY = oldScrollY;
        }
    }

    public static class LongClickEvent {
        public final View view;

        public LongClickEvent(View view) {
            this.view = view;
        }
    }

    private final ViewAttributes mViewAttributes;
    private final View mView;
    private final ViewPrototype mViewPrototype;

    public NativeView(Scriptable scope, View view, Class<?> staticType, ScriptRuntime runtime) {
        super(scope, view, staticType);
        mViewAttributes = ViewExtras.getViewAttributes(runtime, view, runtime.ui.getResourceParser());
        mView = view;
        mViewPrototype = new ViewPrototype(mView, mViewAttributes, scope, runtime);
        prototype = new NativeJavaObjectWithPrototype(scope, mViewPrototype, mViewPrototype.getClass());
        prototype.setPrototype(RhinoUtils.newNativeObject());
    }

    @Override
    public boolean has(String name, Scriptable start) {
        if (mViewAttributes.contains(name)) {
            return true;
        }
        return super.has(name, start);
    }

    @Override
    public Object get(String name, Scriptable start) {
        if (super.has(name, start)) {
            return super.get(name, start);
        }
        View view = JsViewHelper.findViewByStringId(mView, name);
        return view != null ? view : Scriptable.NOT_FOUND;
    }

    public ViewPrototype getViewPrototype() {
        return mViewPrototype;
    }

    @Override
    public View unwrap() {
        return mView;
    }

}
