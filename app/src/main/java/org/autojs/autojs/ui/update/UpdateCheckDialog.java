package org.autojs.autojs.ui.update;

import android.content.Context;

import com.afollestad.materialdialogs.MaterialDialog;
import com.stardust.app.GlobalAppContext;

import org.autojs.autojs.R;
import org.autojs.autojs.network.VersionService;
import org.autojs.autojs.network.entity.VersionInfo;
import org.autojs.autojs.tool.SimpleObserver;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;

/**
 * Created by Stardust on 2017/9/20.
 */

public class UpdateCheckDialog {

    private final MaterialDialog mProgress;
    private final Context mContext;

    public UpdateCheckDialog(Context context) {
        mContext = context;
        mProgress = new MaterialDialog.Builder(context)
                .title(R.string.text_under_development_title)
                .content(R.string.text_under_development_content)
                .positiveText(R.string.text_ok)
                .build();
//        mProgress = new MaterialDialog.Builder(context)
//                .progress(true, 0)
//                .content(R.string.text_checking_update)
//                .build();
    }

    public void show() {
        mProgress.show();
//        VersionService.checkForUpdates()
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(new SimpleObserver<>() {
//                    @Override
//                    public void onNext(@NonNull VersionInfo versionInfo) {
//                        mProgress.dismiss();
//                        if (versionInfo.isNewer()) {
//                            new UpdateInfoDialogBuilder(mContext, versionInfo).show();
//                        } else {
//                            GlobalAppContext.toast(R.string.text_is_latest_version);
//                        }
//                    }
//
//                    @Override
//                    public void onError(@NonNull Throwable e) {
//                        e.printStackTrace();
//                        mProgress.dismiss();
//                        GlobalAppContext.toast(R.string.text_check_update_error);
//                    }
//                });
    }
}
