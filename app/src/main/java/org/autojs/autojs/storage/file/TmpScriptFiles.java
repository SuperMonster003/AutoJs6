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

    private static final String NODEJS_TMP_SCRIPTS_DIR_NAME = "tmp-nodejs-scripts/";

    public static final String RHINO_TMP_SCRIPTS_DIR_NAME = "tmp-scripts/";

    public static File create(Context context) throws IOException {
        return create(context, JAVASCRIPT.extensionWithDot);
    }

    public static File create(Context context, String extensionWithDot) throws IOException {
        ensureTmpDir(context);
        File tmp = new File(getTmpDir(context), "tmp-" + System.currentTimeMillis() + sanitizeExtension(extensionWithDot));
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
        return new File(context.getCacheDir(), RHINO_TMP_SCRIPTS_DIR_NAME);
    }

    public static File getNodeJsTmpDir(Context context) {
        return new File(getTmpDir(context), NODEJS_TMP_SCRIPTS_DIR_NAME);
    }

    private static void ensureTmpDir(Context context) {
        File dir = getTmpDir(context);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static String sanitizeExtension(String extensionWithDot) {
        if (extensionWithDot == null || extensionWithDot.isBlank()) {
            return JAVASCRIPT.extensionWithDot;
        }
        String extension = extensionWithDot.startsWith(".") ? extensionWithDot : "." + extensionWithDot;
        if (extension.indexOf('/') >= 0 || extension.indexOf('\\') >= 0 || extension.indexOf('\0') >= 0) {
            return JAVASCRIPT.extensionWithDot;
        }
        return extension;
    }

}
