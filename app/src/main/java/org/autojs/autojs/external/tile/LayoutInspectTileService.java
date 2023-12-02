package org.autojs.autojs.external.tile;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs.core.accessibility.AccessibilityTool;
import org.autojs.autojs.core.accessibility.LayoutInspector;
import org.autojs.autojs.core.accessibility.NodeInfo;
import org.autojs.autojs.ui.floating.FloatyWindowManger;
import org.autojs.autojs.ui.floating.FullScreenFloatyWindow;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

public abstract class LayoutInspectTileService extends TileService implements LayoutInspector.CaptureAvailableListener {

    private boolean mCapturing = false;

    AccessibilityTool.Service mA11yService = new AccessibilityTool(this).getService();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(getClass().getName(), "onCreate");
        AutoJs.getInstance().getLayoutInspector().addCaptureAvailableListener(this);
        activeTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(getClass().getName(), "onStartListening");
        updateTile();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(getClass().getName(), "onDestroy");
        AutoJs.getInstance().getLayoutInspector().removeCaptureAvailableListener(this);
    }

    @Override
    public void onClick() {
        super.onClick();

        Log.d(getClass().getName(), "onClick");

        // FIXME by SuperMonster003 on Mar 19, 2022.
        //   ! Collapse quick settings panel.
        //   ! Sometimes, there'll be a delay for a few seconds.
        //   ! Dunno whether a better way exists.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                collapseTile();
            } else {
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            }
        } catch (Exception e) {
            /* Ignored. */
            // FIXME by SuperMonster003 on Sep 4, 2022.
            //  ! Maybe GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE could be helpful ?
        }

        if (mA11yService.isRunning()) {
            mCapturing = true;
            captureCurrentWindowDelayed();
        } else {
            if (mA11yService.start(false)) {
                mCapturing = true;
                captureCurrentWindowDelayed();
            } else {
                ViewUtils.showToast(this, R.string.error_no_accessibility_permission_to_capture);
                mA11yService.launchSettings();
                updateTile();
            }
        }
    }

    private static void captureCurrentWindowDelayed() {
        GlobalAppContext.postDelayed(() -> AutoJs.getInstance().getLayoutInspector().captureCurrentWindow(), 1000);
    }

    // @Hint by SuperMonster003 on Oct 8, 2022.
    //  ! Apparently not a good idea.
    private void collapseTile() {
        MaterialDialog dialog = new MaterialDialog.Builder(getApplicationContext()).build();
        showDialog(dialog);
        dialog.dismiss();
    }

    protected void updateTile() {
        Tile qsTile = getQsTile();
        if (qsTile != null) {
            qsTile.updateTile();
        }
    }

    public void activeTile() {
        Tile qsTile = getQsTile();
        if (qsTile != null) {
            qsTile.setState(Tile.STATE_ACTIVE);
        }
    }

    public void inactiveTile() {
        Tile qsTile = getQsTile();
        if (qsTile != null) {
            qsTile.setState(Tile.STATE_INACTIVE);
        }
    }

    @Override
    public void onCaptureAvailable(NodeInfo capture, @NonNull Context context) {
        Log.d(getClass().getName(), "onCaptureAvailable: capturing = " + mCapturing);
        if (!mCapturing) {
            return;
        }
        mCapturing = false;
        GlobalAppContext.post(() -> {
            FullScreenFloatyWindow window = onCreateWindow(capture, context);
            if (!FloatyWindowManger.addWindow(context, window)) {
                updateTile();
            }
        });
    }

    protected abstract FullScreenFloatyWindow onCreateWindow(NodeInfo capture, Context context);

}
