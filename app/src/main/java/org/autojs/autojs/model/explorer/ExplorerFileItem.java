package org.autojs.autojs.model.explorer;

import androidx.annotation.NonNull;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.util.FileUtils;
import org.autojs.autojs.util.Objects;

import java.io.File;

public class ExplorerFileItem implements ExplorerItem {

    private final PFile mFile;
    private final ExplorerPage mParent;

    private FileUtils.TYPE cachedType;

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
    public boolean canRename() {
        return !isInSampleDir(mFile) && mFile.canWrite();
    }

    @Override
    public boolean canDelete() {
        return !isInSampleDir(mFile) && mFile.canWrite();
    }

    @Override
    public boolean canBuildApk() {
        return getType() == FileUtils.TYPE.JAVASCRIPT || mFile.canBuildApk();
    }

    @Override
    public boolean canSetAsWorkingDir() {
        return !isInSampleDir(mFile);
    }

    public ExplorerFileItem rename(String newName) {
        return new ExplorerFileItem(mFile.renameTo(newName), getParent());
    }

    @Override
    @NonNull
    public FileUtils.TYPE getType() {
        if (cachedType == null) {
            cachedType = FileUtils.TYPE.determineBy(mFile);
        }
        return cachedType;
    }

    public String getExtension() {
        return mFile.getExtension();
    }

    public String getNonEmptyExtension() {
        String extension = getExtension();
        return !extension.isEmpty() ? extension : mFile.getName();
    }

    @Override
    public long getSize() {
        return mFile.length();
    }

    @Override
    public ScriptFile toScriptFile() {
        return new ScriptFile(mFile);
    }

    private boolean isInSampleDir(PFile file) {
        return Explorers.Providers.workspace().isInSampleDir(file);
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
