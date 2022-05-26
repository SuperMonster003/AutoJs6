package com.stardust.theme.internal;

import android.widget.AbsListView;
import android.widget.EdgeEffect;
import android.widget.ScrollView;

import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Created by Stardust on 2016/8/14.
 */
public class ScrollingViewEdgeGlowColorHelper {

    private static final Field SCROLL_VIEW_FIELD_EDGE_GLOW_TOP;
    private static final Field SCROLL_VIEW_FIELD_EDGE_GLOW_BOTTOM;

    private static final Field ABS_LIST_VIEW_FIELD_EDGE_GLOW_TOP;
    private static final Field ABS_LIST_VIEW_FIELD_EDGE_GLOW_BOTTOM;

    private static final Field RECYCLER_VIEW_FIELD_EDGE_GLOW_TOP;
    private static final Field RECYCLER_VIEW_FIELD_EDGE_GLOW_BOTTOM;

    static {
        Field[] fields = getEdgeGlowField(ScrollView.class);
        SCROLL_VIEW_FIELD_EDGE_GLOW_TOP = fields[0];
        SCROLL_VIEW_FIELD_EDGE_GLOW_BOTTOM = fields[1];

        fields = getEdgeGlowField(AbsListView.class);
        ABS_LIST_VIEW_FIELD_EDGE_GLOW_TOP = fields[0];
        ABS_LIST_VIEW_FIELD_EDGE_GLOW_BOTTOM = fields[1];

        fields = getEdgeGlowField(RecyclerView.class);
        RECYCLER_VIEW_FIELD_EDGE_GLOW_TOP = fields[0];
        RECYCLER_VIEW_FIELD_EDGE_GLOW_BOTTOM = fields[1];
    }

    public static void setEdgeGlowColor(AbsListView listView, int color) {
        try {
            Objects.requireNonNull((EdgeEffect) ABS_LIST_VIEW_FIELD_EDGE_GLOW_TOP.get(listView)).setColor(color);
            Objects.requireNonNull((EdgeEffect) ABS_LIST_VIEW_FIELD_EDGE_GLOW_BOTTOM.get(listView)).setColor(color);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void setEdgeGlowColor(ScrollView scrollView, int color) {
        try {
            Objects.requireNonNull((EdgeEffect) SCROLL_VIEW_FIELD_EDGE_GLOW_TOP.get(scrollView)).setColor(color);
            Objects.requireNonNull((EdgeEffect) SCROLL_VIEW_FIELD_EDGE_GLOW_BOTTOM.get(scrollView)).setColor(color);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void setEdgeGlowColor(RecyclerView recyclerView, int color) {
        try {
            Objects.requireNonNull((EdgeEffect) RECYCLER_VIEW_FIELD_EDGE_GLOW_TOP.get(recyclerView)).setColor(color);
            Objects.requireNonNull((EdgeEffect) RECYCLER_VIEW_FIELD_EDGE_GLOW_BOTTOM.get(recyclerView)).setColor(color);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static Field[] getEdgeGlowField(Class<?> viewClass) {
        Field edgeGlowTop = null, edgeGlowBottom = null;
        for (Field f : viewClass.getDeclaredFields()) {
            switch (f.getName()) {
                case "mEdgeGlowTop" -> {
                    f.setAccessible(true);
                    edgeGlowTop = f;
                }
                case "mEdgeGlowBottom" -> {
                    f.setAccessible(true);
                    edgeGlowBottom = f;
                }
            }
            if (edgeGlowBottom != null && edgeGlowTop != null)
                break;
        }
        return new Field[]{edgeGlowTop, edgeGlowBottom};
    }
}