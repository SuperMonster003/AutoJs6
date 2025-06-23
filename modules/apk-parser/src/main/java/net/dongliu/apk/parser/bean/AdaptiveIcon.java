package net.dongliu.apk.parser.bean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;

/**
 * Android adaptive icon, from android 8.0
 */
public class AdaptiveIcon implements IconFace, Serializable {
    private static final long serialVersionUID = 4185750290211529320L;
    /**
     * The foreground icon
     */
    @Nullable
    public final Icon foreground;
    /**
     * The background icon
     */
    @Nullable
    public final Icon background;

    public AdaptiveIcon(@Nullable final Icon foreground, @Nullable final Icon background) {
        this.foreground = foreground;
        this.background = background;
    }

    @NonNull
    @Override
    public String toString() {
        return "AdaptiveIcon{" +
                "foreground=" + this.foreground +
                ", background=" + this.background +
                '}';
    }

    @Override
    public boolean isFile() {
        return this.foreground.isFile();
    }

    @Override
    @Nullable
    public byte[] getData() {
        return this.foreground.getData();
    }

    @Override
    public String getPath() {
        return this.foreground.getPath();
    }
}
