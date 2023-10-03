package org.autojs.autojs.ui.enhancedfloaty;

import android.view.View;
import android.widget.ImageView;

/**
 * Created by Stardust on 2017/4/30.
 */
public interface ResizableFloaty {

    View inflateView(FloatyService floatyService, ResizableFloatyWindow service);

    ImageView getResizerView(View view);

    ImageView getMoveCursorView(View view);

    ImageView getCloseButtonView(View view);

}
