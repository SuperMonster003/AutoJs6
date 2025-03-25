package com.android.apksigner.utils;

import java.io.File;

public class FileUtils {
    public static boolean moveFile(File sourcePath, File targetPath) {
        return sourcePath.renameTo(targetPath);
    }
}
