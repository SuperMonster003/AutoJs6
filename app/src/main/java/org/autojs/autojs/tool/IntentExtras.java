package org.autojs.autojs.tool;

import android.content.Intent;
import android.util.SparseArray;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Stardust on Jul 11, 2017.
 */
public class IntentExtras implements Serializable {

    public static final String EXTRA_ID = "org.autojs.autojs.tool.IntentExtras.id";

    public static final SparseArray<Map<String, Object>> extraStore = new SparseArray<>();
    private static final AtomicInteger mMaxId = new AtomicInteger(-1);

    public final Map<String, Object> map;
    private int mId;

    public IntentExtras() {
        map = new HashMap<>();
        mId = mMaxId.incrementAndGet();
        synchronized (extraStore) {
            extraStore.put(mId, map);
        }
    }

    public IntentExtras(int id, Map<String, Object> map) {
        mId = id;
        this.map = map;
    }

    public static IntentExtras newExtras() {
        return new IntentExtras();
    }

    public static IntentExtras fromIntentAndRelease(Intent intent) {
        int id = intent.getIntExtra(EXTRA_ID, -1);
        if (id < 0) {
            return null;
        }
        return fromIdAndRelease(id);
    }

    public static IntentExtras fromIdAndRelease(int id) {
        Map<String, Object> map = extraStore.get(id);
        if (map == null) {
            return null;
        }
        extraStore.remove(id);
        return new IntentExtras(id, map);
    }

    public static IntentExtras fromId(int id) {
        Map<String, Object> map = extraStore.get(id);
        if (map == null) {
            return null;
        }
        return new IntentExtras(id, map);
    }

    public static IntentExtras fromIntent(Intent intent) {
        int id = intent.getIntExtra(EXTRA_ID, -1);
        if (id < 0) {
            return null;
        }
        return fromId(id);
    }

    public int getId() {
        return mId;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) map.get(key);
    }

    public IntentExtras put(String key, Object value) {
        map.put(key, value);
        return this;
    }

    public IntentExtras putAll(IntentExtras extras) {
        map.putAll(extras.map);
        return this;
    }

    public Intent putInIntent(Intent intent) {
        intent.putExtra(EXTRA_ID, mId);
        return intent;
    }

    public void release() {
        extraStore.remove(mId);
        mId = -1;
    }

}
