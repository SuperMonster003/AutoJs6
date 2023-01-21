package org.autojs.autojs.util;

import android.app.Activity;
import android.content.res.AssetManager;

import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.tool.SimpleCache;

/**
 * Created by Stardust on 2017/3/14.
 */
public class AssetsCache {

    private static final long PERSIST_TIME = 5 * 60 * 1000;

    private static final SimpleCache<String> cache = new SimpleCache<>(PERSIST_TIME, 5, 30 * 1000);

    public static String get(final AssetManager assetManager, final String path) {
        return cache.get(path, key -> PFiles.readAsset(assetManager, path));
    }

    public static String get(final Activity activity, final String path) {
        return get(activity.getAssets(), path);
    }

}
