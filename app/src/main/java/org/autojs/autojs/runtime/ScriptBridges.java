package org.autojs.autojs.runtime;

import org.autojs.autojs.AutoJs;

/**
 * Created by Stardust on 2017/7/21.
 */
public class ScriptBridges {

    public interface Bridges {

        Object call(Object func, Object target, Object arg);

        Object toArray(Iterable<?> o);

        Object toString(Object obj);

        Object asArray(Object obj);

        Object toPrimitive(Object obj);

    }

    private Bridges mBridges;

    public void setBridges(Bridges bridges) {
        mBridges = bridges;
    }

    public Bridges getBridges() {
        return mBridges;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Object callFunction(Object func, Object target, Object args) {
        checkBridges();
        try {
            return mBridges.call(func, target, args);
        } catch (Exception e) {
            e.printStackTrace();
            AutoJs.getInstance().getRuntime().exit(e);
        }
        return null;
    }

    private void checkBridges() {
        if (mBridges == null)
            throw new IllegalStateException("No bridges have been set");
    }


    public Object toArray(Iterable<?> c) {
        checkBridges();
        return mBridges.toArray(c);
    }

    public Object toString(Object obj) {
        checkBridges();
        return mBridges.toString(obj);
    }

    public Object asArray(Object obj) {
        checkBridges();
        return mBridges.asArray(obj);
    }

    public Object toPrimitive(Object obj) {
        checkBridges();
        return mBridges.toPrimitive(obj);
    }

}
