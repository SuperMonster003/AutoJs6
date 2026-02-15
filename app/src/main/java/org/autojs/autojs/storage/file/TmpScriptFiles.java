package org.autojs.autojs.storage.file;

import android.content.Context;
import org.autojs.autojs.pio.PFiles;

import java.io.File;
import java.io.IOException;

import static org.autojs.autojs.util.FileUtils.TYPE.JAVASCRIPT;

/**
 * Created by Stardust on Oct 21, 2017.
 * Modified by SuperMonster003 as of Feb 15, 2026.
 */
public class TmpScriptFiles {

    public static File create(Context context) throws IOException {
        ensureTmpDir(context);
        File tmp = new File(getTmpDir(context), "tmp-" + System.currentTimeMillis() + JAVASCRIPT.extensionWithDot);
        tmp.createNewFile();
        return tmp;
    }

    public static void clearTmpDir(Context context, long maxAgeMs) {
        File dir = getTmpDir(context);
        PFiles.deleteRecursivelyOlderThan(dir, maxAgeMs);
    }

    public static void clearTmpDir(Context context) {
        File dir = getTmpDir(context);
        PFiles.deleteRecursively(dir);
    }

    public static File getTmpDir(Context context) {
        return new File(context.getCacheDir(), "tmp-scripts/");
    }

    private static void ensureTmpDir(Context context) {
        File dir = getTmpDir(context);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

}
