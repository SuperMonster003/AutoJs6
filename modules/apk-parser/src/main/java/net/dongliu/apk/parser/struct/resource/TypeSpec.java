package net.dongliu.apk.parser.struct.resource;

import androidx.annotation.NonNull;

/**
 * @author dongliu
 */
public class TypeSpec {

    public final long[] entryFlags;
    public final String name;
    public final short id;

    public TypeSpec(final @NonNull TypeSpecHeader header, @NonNull final long[] entryFlags, final String name) {
        this.id = header.getId();
        this.entryFlags = entryFlags;
        this.name = name;
    }

    public boolean exists(final int id) {
        return id < this.entryFlags.length;
    }

    @NonNull
    @Override
    public String toString() {
        return "TypeSpec{" +
                "name='" + this.name + '\'' +
                ", id=" + this.id +
                '}';
    }
}
