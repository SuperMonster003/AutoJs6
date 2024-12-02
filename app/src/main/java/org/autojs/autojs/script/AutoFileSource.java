package org.autojs.autojs.script;

import androidx.annotation.NonNull;

import org.autojs.autojs.pio.PFiles;

import java.io.File;

/**
 * Created by Stardust on Aug 2, 2017.
 */
public class AutoFileSource extends ScriptSource {

    public static final String ENGINE = AutoFileSource.class.getName() + ".Engine";
    private final File mFile;

    public AutoFileSource(File file) {
        super(PFiles.getNameWithoutExtension(file.getAbsolutePath()));
        mFile = file;
    }

    public AutoFileSource(String path) {
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
