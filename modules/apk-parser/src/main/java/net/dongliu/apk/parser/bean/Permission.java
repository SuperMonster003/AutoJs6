package net.dongliu.apk.parser.bean;


import androidx.annotation.Nullable;

/**
 * permission provided by the app
 *
 * @author Liu Dong
 */
public class Permission {
    @Nullable
    public final String name;
    @Nullable
    public final String label;
    @Nullable
    public final String icon;
    @Nullable
    public final String description;
    @Nullable
    public final String group;
    @Nullable
    public final String protectionLevel;

    public Permission(final @Nullable String name, final @Nullable String label, final @Nullable String icon, final @Nullable String description, final @Nullable String group,
                      final @Nullable String protectionLevel) {
        this.name = name;
        this.label = label;
        this.icon = icon;
        this.description = description;
        this.group = group;
        this.protectionLevel = protectionLevel;
    }

}
