package org.autojs.autojs.external.tile;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;

import androidx.annotation.RequiresApi;

import com.stardust.view.accessibility.NodeInfo;

import org.autojs.autojs.ui.floating.FullScreenFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow;

@RequiresApi(api = Build.VERSION_CODES.N)
public class LayoutBoundsTile extends LayoutInspectTileService {
    @Override
    protected FullScreenFloatyWindow onCreateWindow(NodeInfo capture) {
        return new LayoutBoundsFloatyWindow(capture) {
            @Override
            public void close() {
                super.close();
                updateTile();
            }
        };
    }
}
