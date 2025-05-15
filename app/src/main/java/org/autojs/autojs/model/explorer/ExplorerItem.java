package org.autojs.autojs.model.explorer;

import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.external.fileprovider.AppFileProvider;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.runtime.api.Mime;
import org.autojs.autojs.util.FileUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.IntentUtils.SnackExceptionHolder;
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder;
import org.autojs.autojs6.R;
import org.jetbrains.annotations.NotNull;

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

    default boolean install(@NotNull View view) {
        return IntentUtils.installApk(view.getContext(), getPath(), AppFileProvider.AUTHORITY, new SnackExceptionHolder(view));
    }

    default boolean install(Context context) {
        return IntentUtils.installApk(context, getPath(), AppFileProvider.AUTHORITY, new ToastExceptionHolder(context));
    }

    default boolean play(@NotNull View view) {
        String mimeType = determineMimeTypeForPlay();
        return IntentUtils.viewFile(
                view.getContext(),
                getPath(),
                mimeType,
                AppFileProvider.AUTHORITY,
                new SnackExceptionHolder(view, R.string.error_no_applications_available_for_playing_this_file)
        );
    }

    default boolean play(Context context) {
        String mimeType = determineMimeTypeForPlay();
        return IntentUtils.viewFile(
                context,
                getPath(),
                mimeType,
                AppFileProvider.AUTHORITY,
                new ToastExceptionHolder(context, R.string.error_no_applications_available_for_playing_this_file)
        );
    }

    private @NotNull String determineMimeTypeForPlay() {
        String mimeType;
        FileUtils.TypeData typeData = getType().getTypeData();
        if (typeData == FileUtils.TypeDataHolder.INSTANCE.getVIDEO() || typeData == FileUtils.TypeDataHolder.INSTANCE.getVIDEO_PLAYLIST()) {
            mimeType = Mime.VIDEO_WILDCARD;
        } else if (typeData == FileUtils.TypeDataHolder.INSTANCE.getAUDIO() || typeData == FileUtils.TypeDataHolder.INSTANCE.getAUDIO_PLAYLIST()) {
            mimeType = Mime.AUDIO_WILDCARD;
        } else {
            mimeType = Mime.WILDCARD;
        }
        return mimeType;
    }

    default boolean view(@NotNull View view) {
        return IntentUtils.viewFile(view.getContext(), getPath(), null, AppFileProvider.AUTHORITY, new SnackExceptionHolder(view));
    }

    default boolean view(Context context) {
        return IntentUtils.viewFile(context, getPath(), null, AppFileProvider.AUTHORITY, new ToastExceptionHolder(context));
    }

    default boolean edit(@NotNull View view) {
        return IntentUtils.editFile(view.getContext(), getPath(), AppFileProvider.AUTHORITY, new SnackExceptionHolder(view));
    }

    default boolean edit(Context context) {
        return IntentUtils.editFile(context, getPath(), AppFileProvider.AUTHORITY, new ToastExceptionHolder(context));
    }

}
