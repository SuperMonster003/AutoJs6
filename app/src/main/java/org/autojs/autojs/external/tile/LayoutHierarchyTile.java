package org.autojs.autojs.external.tile;

import android.content.Context;
import org.autojs.autojs.core.accessibility.Capture;
import org.autojs.autojs.ui.floating.FullScreenFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow;

public class LayoutHierarchyTile extends LayoutInspectTileService {

    @Override
    protected FullScreenFloatyWindow onCreateWindow(Capture capture, Context context) {
        return new LayoutHierarchyFloatyWindow(capture, context, true) {
            @Override
            public void close() {
                super.close();
                updateTile();
            }
        };
    }
}
