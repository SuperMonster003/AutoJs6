package org.mozilla.javascript;

import android.util.Log;
import org.autojs.autojs.engine.RhinoJavaScriptEngine;
import org.autojs.autojs.rhino.AutoJsContext;
import org.mozilla.javascript.jdk18.VMBridge_jdk18;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.autojs.autojs.util.RhinoUtils.isUiThread;

@SuppressWarnings("unused")
public class VMBridge_custom extends VMBridge_jdk18 {

    // @Hint by SuperMonster003 on Oct 30, 2022.
    //  ! Current class was used in method `org.mozilla.javascript.VMBridge.makeInstance`.
    //  ! zh-CN: 当前类在 `org.mozilla.javascript.VMBridge.makeInstance` 方法中被使用.

    private static final String LOG_TAG = VMBridge_custom.class.getSimpleName();

    @SuppressWarnings("RedundantThrows")
    public VMBridge_custom() throws SecurityException, InstantiationException {
        /* Empty body. */
    }

    @Override
    protected Object newInterfaceProxy(Object proxyHelper, ContextFactory cf, InterfaceAdapter adapter, Object target, Scriptable topScope) {
        Context context = Context.getCurrentContext();
        InterfaceAdapterWrapper adapterWrapper = new InterfaceAdapterWrapper(adapter, context);
        // --- The following code is copied from super class --
        Constructor<?> c = (Constructor<?>) proxyHelper;
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                String methodName = method.getName();
                if (methodName.equals("equals")) {
                    Object other = args[0];
                    return proxy == other;
                }

                if (methodName.equals("hashCode")) {
                    return target.hashCode();
                }

                if (methodName.equals("toString")) {
                    return "Proxy[" + target.toString() + "]";
                }
            }
            // Add thread check
            // Check if the current thread is ui thread
            if (isUiThread()) {
                // If so, catch any exception of invoking
                // Because an exception on ui thread will cause the whole app to crash
                try {
                    Object result = adapterWrapper.invoke(cf, target, topScope, proxy, method, args);
                    return castReturnValue(method, result);
                } catch (ContinuationPending pending) {
                    return defaultValue(method.getReturnType());
                } catch (Throwable e) {
                    e.printStackTrace();
                    // @Reference to aiselp (https://github.com/aiselp) by SuperMonster003 on Nov 7, 2023.
                    //  ! https://github.com/kkevsekk1/AutoX/commit/9cfeb00280447ef2807bdc5e195095d5a83afb39#diff-18d5934044cd3d259e52c3b1053ecd57e653ae70d14a7c0f31700029e97da239
                    if (context instanceof AutoJsContext) {
                        // notify the script thread to exit
                        var engine = ((AutoJsContext) context).getRhinoJavaScriptEngine();
                        if (engine != null) {
                            var runtime = engine.getRuntime();
                            Log.d(LOG_TAG, "runtime = " + runtime);
                            runtime.exit(e);
                        }
                    }
                    // even if we caught the exception, we must return a value to for the method call.
                    return defaultValue(method.getReturnType());
                }
            } else {
                return castReturnValue(method, adapterWrapper.invoke(cf, target, topScope, proxy, method, args));
            }
        };

        // --- The following code is copied from super class --
        try {
            return /* proxy = */ c.newInstance(handler);
        } catch (InvocationTargetException e) {
            throw Context.throwAsScriptRuntimeEx(e);
        } catch (IllegalAccessException | InstantiationException e) {
            throw new IllegalStateException(e);
        }
    }

    // cast the return value to boolean if needed.
    // if a javascript function that implements a java interface returns nothing,
    // it will be regarded as "false", like javascript behavior, instead of reporting error "undefined cannot be cast to boolean"
    protected Object castReturnValue(Method method, Object returnValue) {
        if (method.getReturnType().equals(Boolean.TYPE) || method.getReturnType().equals(Boolean.class)) {
            return ScriptRuntime.toBoolean(returnValue);
        }
        return returnValue;
    }

    protected Object defaultValue(Class<?> type) {
        if (type.equals(Boolean.TYPE) || type.equals(Boolean.class)) {
            return false;
        }
        if (type.equals(Integer.TYPE) || type.equals(Integer.class)) {
            return 0;
        }
        if (type.equals(Long.TYPE) || type.equals(Long.class)) {
            return 0L;
        }
        if (type.equals(Float.TYPE) || type.equals(Float.class)) {
            return 0F;
        }
        if (type.equals(Double.TYPE) || type.equals(Double.class)) {
            return 0.0;
        }
        if (type.equals(Byte.TYPE) || type.equals(Byte.class)) {
            return (byte) 0;
        }
        if (type.equals(Character.TYPE) || type.equals(Character.class)) {
            return (char) 0;
        }
        if (type.isAssignableFrom(CharSequence.class)) {
            return "";
        }
        return null;
    }

    private static class InterfaceAdapterWrapper {

        private final InterfaceAdapter mInterfaceAdapter;
        private final Context mCallerContext;

        private InterfaceAdapterWrapper(InterfaceAdapter interfaceAdapter, Context callerContext) {
            mInterfaceAdapter = interfaceAdapter;
            mCallerContext = callerContext;
        }

        public Object invoke(ContextFactory cf, Object target, Scriptable topScope, Object thisObject, Method method, Object[] args) {
            ContextAction<Object> action = cx -> invokeImpl(cx, target, topScope, thisObject, method, args);
            return call(cf, action);
        }

        private Object call(ContextFactory cf, ContextAction<Object> action) {
            Context cx = Context.enter(null, cf);
            // TODO null check
            cx.setWrapFactory(mCallerContext.getWrapFactory());
            try {
                return action.run(cx);
            } finally {
                Context.exit();
            }
        }

        public Object invokeImpl(Context cx, Object target, Scriptable topScope, Object thisObject, Method method, Object[] args) {
            cx.isContinuationsTopCall = true;
            return mInterfaceAdapter.invokeImpl(cx, target, topScope, thisObject, method, args);
        }

    }

}
