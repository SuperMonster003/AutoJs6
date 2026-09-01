package org.autojs.plugin.screencolorpicker.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Runtime states returned by {@link IScreenColorPickerPlugin#getState()}. */
public final class ScreenColorPickerStates {

    public static final int INACTIVE = 0;
    public static final int STARTING = 1;
    public static final int ACTIVE = 2;
    public static final int STOPPING = 3;

    public static final Set<Integer> ALL;

    static {
        LinkedHashSet<Integer> states = new LinkedHashSet<>();
        states.add(INACTIVE);
        states.add(STARTING);
        states.add(ACTIVE);
        states.add(STOPPING);
        ALL = Collections.unmodifiableSet(states);
    }

    public static boolean isKnown(int state) {
        return ALL.contains(state);
    }

    public static boolean isOn(int state) {
        return state != INACTIVE && isKnown(state);
    }

    public static boolean isTransitioning(int state) {
        return state == STARTING || state == STOPPING;
    }

    public static int requireKnown(int state) {
        if (!isKnown(state)) {
            throw new IllegalArgumentException("Unknown Screen Color Picker state: " + state);
        }
        return state;
    }

    private ScreenColorPickerStates() {
        throw new AssertionError("No instances");
    }
}
