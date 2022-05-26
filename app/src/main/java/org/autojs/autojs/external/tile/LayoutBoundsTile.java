package org.autojs.autojs.external.tile;

import com.stardust.view.accessibility.NodeInfo;

import org.autojs.autojs.ui.floating.FullScreenFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow;

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
