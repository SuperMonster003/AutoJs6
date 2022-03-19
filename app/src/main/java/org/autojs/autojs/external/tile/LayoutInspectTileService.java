package org.autojs.autojs.external.tile;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import android.util.Log;
import android.widget.Toast;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.core.accessibility.AccessibilityServiceTool;
import com.stardust.view.accessibility.AccessibilityService;
import com.stardust.view.accessibility.LayoutInspector;
import com.stardust.view.accessibility.NodeInfo;

import org.autojs.autojs.R;
import org.autojs.autojs.autojs.AutoJs;
import org.autojs.autojs.ui.floating.FloatyWindowManger;
import org.autojs.autojs.ui.floating.FullScreenFloatyWindow;

@RequiresApi(api = Build.VERSION_CODES.N)
public abstract class LayoutInspectTileService extends TileService implements LayoutInspector.CaptureAvailableListener {

    private boolean mCapturing = false;

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
        // FIXME by SuperMonster003 as of Mar 19, 2022.
        //   ! Collapse quick settings panel.
        //   ! Sometimes, there'll be a delay for a few seconds.
        //   ! Dunno whether a better way exists.
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        super.onClick();
        Log.d(getClass().getName(), "onClick");
        if (AccessibilityService.Companion.getInstance() == null) {
            Toast.makeText(this, R.string.text_no_accessibility_permission_to_capture, Toast.LENGTH_SHORT).show();
            if (!getAccessibilityServiceTool().enableAccessibilityServiceAutomaticallyIfNeeded()) {
                getAccessibilityServiceTool().goToAccessibilitySetting();
            }
            updateTile();
        } else {
            mCapturing = true;
            GlobalAppContext.postDelayed(() -> AutoJs.getInstance().getLayoutInspector().captureCurrentWindow(), 1000);
        }
    }

    @NonNull
    private AccessibilityServiceTool getAccessibilityServiceTool() {
        return new AccessibilityServiceTool(this);
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
    public void onCaptureAvailable(NodeInfo capture) {
        Log.d(getClass().getName(), "onCaptureAvailable: capturing = " + mCapturing);
        if (!mCapturing) {
            return;
        }
        mCapturing = false;
        GlobalAppContext.post(() -> {
            FullScreenFloatyWindow window = onCreateWindow(capture);
            if (!FloatyWindowManger.addWindow(getApplicationContext(), window)) {
                updateTile();
            }
        });
    }

    protected abstract FullScreenFloatyWindow onCreateWindow(NodeInfo capture);
}
