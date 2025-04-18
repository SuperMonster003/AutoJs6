package net.dongliu.apk.parser.bean;

import androidx.annotation.NonNull;

/**
 * the permission used by apk
 *
 * @author dongliu
 */
public class UseFeature {
    public final String name;
    public final boolean isRequired;

    public UseFeature(final String name, final boolean required) {
        this.name = name;
        this.isRequired = required;
    }

    @NonNull
    @Override
    public String toString() {
        return this.name;
    }
}
