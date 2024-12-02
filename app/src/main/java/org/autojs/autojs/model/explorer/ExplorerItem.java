package org.autojs.autojs.model.explorer;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.external.fileprovider.AppFileProvider;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.runtime.api.Mime;
import org.autojs.autojs.util.FileUtils;
import org.autojs.autojs.util.IntentUtils;

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

    default boolean isExecutable() {
        return getType().isExecutable();
    }

    default boolean isInstallable() {
        return getType().isInstallable();
    }

    default boolean isTextEditable() {
        return getType().isTextEditable();
    }

    default boolean isExternalEditable() {
        return getType().isExternalEditable();
    }

    default boolean isMediaPlayable() {
        return getType().isMediaPlayable();
    }

    default boolean isMediaMenu() {
        return getType().isMediaMenu();
    }

    default void install(Context context) {
        IntentUtils.installApk(context, getPath());
    }

    default void play(Context context) {
        String mimeType;
        FileUtils.TypeData typeData = getType().getTypeData();
        if (typeData == FileUtils.TypeDataHolder.INSTANCE.getVIDEO() || typeData == FileUtils.TypeDataHolder.INSTANCE.getVIDEO_PLAYLIST()) {
            mimeType = Mime.VIDEO_WILDCARD;
        } else if (typeData == FileUtils.TypeDataHolder.INSTANCE.getAUDIO() || typeData == FileUtils.TypeDataHolder.INSTANCE.getAUDIO_PLAYLIST()) {
            mimeType = Mime.AUDIO_WILDCARD;
        } else {
            mimeType = Mime.WILDCARD;
        }
        IntentUtils.viewFile(context, getPath(), mimeType, AppFileProvider.AUTHORITY);
    }

    default void view(Context context) {
        IntentUtils.viewFile(context, getPath(), Mime.WILDCARD, AppFileProvider.AUTHORITY);
    }

}
