package net.dongliu.apk.parser.bean;

import androidx.annotation.NonNull;

/**
 * the glEsVersion apk used.
 *
 * @author dongliu
 */
public class GlEsVersion {
    public final int major;
    public final int minor;
    public final boolean isRequired;

    public GlEsVersion(final int major, final int minor, final boolean isRequired) {
        this.major = major;
        this.minor = minor;
        this.isRequired = isRequired;
    }

    @NonNull
    @Override
    public String toString() {
        return this.major + "." + this.minor;
    }

}
