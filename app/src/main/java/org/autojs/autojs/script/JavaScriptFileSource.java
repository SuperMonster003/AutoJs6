package org.autojs.autojs.script;

import androidx.annotation.NonNull;

import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.pio.UncheckedIOException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;

/**
 * Created by Stardust on Apr 2, 2017.
 */
public class JavaScriptFileSource extends JavaScriptSource {

    private final File mFile;
    private String mScript;
    private boolean mCustomsName = false;

    public JavaScriptFileSource(File file) {
        super(PFiles.getNameWithoutExtension(file.getName()));
        mFile = file;
    }

    public JavaScriptFileSource(@NonNull String path) {
        this(new File(path));
    }

    public JavaScriptFileSource(@NonNull String name, File file) {
        super(name);
        mCustomsName = true;
        mFile = file;
    }

    @NonNull
    @Override
    public String getScript() {
        if (mScript == null)
            mScript = PFiles.read(mFile);
        return mScript;
    }

    @Override
    protected int parseExecutionMode() {
        short flags = EncryptedScriptFileHeader.getHeaderFlags(mFile);
        if (flags == EncryptedScriptFileHeader.FLAG_INVALID_FILE) {
            return super.parseExecutionMode();
        }
        return flags;
    }

    @Override
    public Reader getScriptReader() {
        try {
            return new FileReader(mFile);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    public File getFile() {
        return mFile;
    }

    @NonNull
    @Override
    public String toString() {
        if (mCustomsName) {
            return super.toString();
        }
        return mFile.toString();
    }
}
