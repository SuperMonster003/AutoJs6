package org.autojs.autojs.model.explorer;

import androidx.annotation.NonNull;

import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.util.FileUtils;
import org.autojs.autojs.util.Objects;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class ExplorerFileItem implements ExplorerItem {

    private static final Set<String> sEditableFileExtensions = new HashSet<>(Arrays.asList(
            "js", "java", "xml", "json", "txt", "log", "ts", "md", "ini", "html", "css", "kt"
    ));

    private final PFile mFile;
    private final ExplorerPage mParent;

    public ExplorerFileItem(PFile file, ExplorerPage parent) {
        if (file == null) {
            throw new NullPointerException(ExplorerFileItem.class.getSimpleName());
        }
        mFile = file;
        mParent = parent;
    }

    public ExplorerFileItem(String path, ExplorerPage parent) {
        mFile = new PFile(path);
        mParent = parent;
    }

    public ExplorerFileItem(File file, ExplorerPage parent) {
        mFile = new PFile(file.getPath());
        mParent = parent;
    }

    public PFile getFile() {
        return mFile;
    }

    @Override
    public String getName() {
        return mFile.getName();
    }

    @Override
    public ExplorerPage getParent() {
        return mParent;
    }

    @Override
    public String getPath() {
        return mFile.getPath();
    }

    @Override
    public long lastModified() {
        return mFile.lastModified();
    }

    @Override
    public boolean canDelete() {
        return mFile.canWrite();
    }

    @Override
    public boolean canRename() {
        return mFile.canWrite();
    }

    public ExplorerFileItem rename(String newName) {
        return new ExplorerFileItem(mFile.renameTo(newName), getParent());
    }

    @NonNull
    @Override
    public FileUtils.TYPE getType() {
        if (mFile.isDirectory()) {
            return FileUtils.TYPE.DIRECTORY;
        }
        if (mFile.isProject()) {
            return FileUtils.TYPE.PROJECT;
        }
        String extension = mFile.getExtension();
        if (FileUtils.TYPE.JAVASCRIPT.getExtension().equals(extension)) {
            return FileUtils.TYPE.JAVASCRIPT;
        }
        if (FileUtils.TYPE.AUTO.getExtension().equals(extension)) {
            return FileUtils.TYPE.AUTO;
        }
        if (FileUtils.TYPE.XML.getExtension().equals(extension)) {
            return FileUtils.TYPE.XML;
        }
        if (FileUtils.TYPE.APK.getExtension().equals(extension)) {
            return FileUtils.TYPE.APK;
        }
        if (FileUtils.TYPE.JSON.getExtension().equals(extension)) {
            return FileUtils.TYPE.JSON;
        }
        return FileUtils.TYPE.UNKNOWN;
    }

    public String getExtension() {
        return mFile.getExtension();
    }

    @Override
    public long getSize() {
        return mFile.length();
    }

    @Override
    public ScriptFile toScriptFile() {
        return new ScriptFile(mFile);
    }

    @Override
    public boolean isEditable() {
        return sEditableFileExtensions.contains(getExtension());
    }

    @Override
    public boolean isExecutable() {
        FileUtils.TYPE type = getType();
        return type == FileUtils.TYPE.JAVASCRIPT || type == FileUtils.TYPE.AUTO;
    }

    @NonNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "mFile=" + mFile + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExplorerFileItem that = (ExplorerFileItem) o;
        return Objects.equals(mFile, that.mFile);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mFile);
    }
}

