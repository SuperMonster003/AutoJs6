package org.autojs.autojs.model.explorer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.util.FileUtils;

public interface ExplorerItem {

    String getName();

    @Nullable
    ExplorerPage getParent();

    String getPath();

    long lastModified();

    boolean canDelete();

    boolean canRename();

    default boolean canBuildApk() {
        return true;
    }

    default boolean canSetAsWorkingDir() {
        return true;
    }

    @NonNull
    FileUtils.TYPE getType();

    long getSize();

    ScriptFile toScriptFile();

    boolean isEditable();

    boolean isExecutable();
}
