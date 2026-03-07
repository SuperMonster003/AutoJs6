package androidx.recyclerview.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EdgeEffect;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;

/**
 * Created by Stardust on Aug 14, 2016.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 6, 2026.
 * Modified by SuperMonster003 as of Mar 6, 2026.
 */
public class ThemeColorRecyclerView extends RecyclerView implements ThemeColorMutable {

    private int mColorPrimary;

    public ThemeColorRecyclerView(Context context) {
        super(context);
        init();
    }

    public ThemeColorRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setEdgeEffectFactory(new EdgeEffectFactory() {
            @NonNull
            @Override
            protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
                EdgeEffect edgeEffect = new EdgeEffect(view.getContext());
                edgeEffect.setColor(mColorPrimary);
                return edgeEffect;
            }
        });
        ThemeColorManager.add(this);
    }

    @Override
    public void setThemeColor(ThemeColor color) {
        if (color.colorPrimary == mColorPrimary) {
            return;
        }
        mColorPrimary = color.colorPrimary;
        super.invalidateGlows();
    }

}
