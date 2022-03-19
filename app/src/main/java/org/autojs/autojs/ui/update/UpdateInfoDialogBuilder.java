package org.autojs.autojs.ui.update;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;
import com.stardust.app.GlobalAppContext;
import com.stardust.util.IntentUtil;

import org.autojs.autojs.Pref;
import org.autojs.autojs.R;
import org.autojs.autojs.external.fileprovider.AppFileProvider;
import org.autojs.autojs.network.download.DownloadManager;
import org.autojs.autojs.network.entity.VersionInfo;

import java.io.File;

import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * Created by Stardust on 2017/4/9.
 */

public class UpdateInfoDialogBuilder extends MaterialDialog.Builder {

    private static final String KEY_IGNORED_VERSION_PREFIX = "Ignored version: ";
    private final SharedPreferences mSharedPreferences;
    private VersionInfo mVersionInfo;

    public UpdateInfoDialogBuilder(@NonNull Context context, VersionInfo info) {
        super(context);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        updateInfo(info);
    }

    public void updateInfo(VersionInfo info) {
        mVersionInfo = info;
        title(GlobalAppContext.getString(R.string.text_new_version_found));
        content(info.appVersionName + "\n" + info.appVersionCode);
    }

    @Override
    public MaterialDialog show() {
        if (mSharedPreferences.getBoolean(KEY_IGNORED_VERSION_PREFIX + mVersionInfo.appVersionCode, false)) {
            return null;
        }
        return super.show();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void directlyDownload(String downloadUrl) {
        final String path = new File(Pref.getScriptDirPath(), "autojs6-latest.apk").getPath();
        DownloadManager.getInstance().downloadWithProgress(getContext(), downloadUrl, path)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(file -> IntentUtil.installApkOrToast(getContext(), file.getPath(), AppFileProvider.AUTHORITY),
                        error -> {
                            error.printStackTrace();
                            GlobalAppContext.toast(R.string.text_download_failed);
                        });

    }

}
