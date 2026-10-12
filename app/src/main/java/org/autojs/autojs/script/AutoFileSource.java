package org.autojs.autojs.script;

import androidx.annotation.NonNull;

import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.util.FileUtils;

import java.io.File;
import java.util.Locale;

/**
 * Created by Stardust on Aug 2, 2017.
 */
public class AutoFileSource extends ScriptSource {

    public static final String ENGINE = AutoFileSource.class.getName() + ".Engine";
    private final File mFile;

    public static boolean isAutoFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.toLowerCase(Locale.ROOT).endsWith(FileUtils.TYPE.AUTO.extensionWithDot);
    }

    public AutoFileSource(File file) {
        super(PFiles.getNameWithoutExtension(file.getAbsolutePath()), FileUtils.TYPE.AUTO.extension);
        mFile = file;
    }

    public AutoFileSource(@NonNull String path) {
        this(new File(path));
    }

    @Override
    public String getEngineName() {
        return ENGINE;
    }

    public File getFile() {
        return mFile;
    }

    @NonNull
    @Override
    public String toString() {
        return mFile.toString();
    }
}
