package org.autojs.autojs.ui.floating;

import com.stardust.enhancedfloaty.FloatyService;

public interface CircularMenuFloaty {

    CircularActionView inflateActionView(FloatyService service, CircularMenuWindow window);

    CircularActionMenu inflateMenuItems(FloatyService service, CircularMenuWindow window);

}
