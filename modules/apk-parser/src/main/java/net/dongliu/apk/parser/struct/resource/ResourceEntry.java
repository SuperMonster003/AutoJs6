package net.dongliu.apk.parser.struct.resource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.struct.ResourceValue;

import java.util.Locale;

/**
 * A Resource entry specifies the key (name) of the Resource.
 * It is immediately followed by the value of that Resource.
 *
 * @author dongliu
 */
public class ResourceEntry {
    /**
     * Number of bytes in this structure. uint16_t
     */
    public final int size;

    /**
     * If set, this is a complex entry, holding a set of name/value
     * mappings.  It is followed by an array of ResTable_map structures.
     */
    public static final int FLAG_COMPLEX = 0x0001;
//    /**
//     * If set, this resource has been declared public, so libraries
//     * are allowed to reference it.
//     */
//    public static final int FLAG_PUBLIC = 0x0002;

    /**
     * If set, this is a weak resource and may be overriden by strong
     * resources of the same name/type. This is only useful during
     * linking with other resource tables.
     */
    public static final int FLAG_WEAK = 0x0004;
    /**
     * If set, this is a compact entry with data type and value directly
     * encoded in this entry, see ResTable_entry::compact
     */
    public static final int FLAG_COMPACT = 0x0008;
    /**
     * uint16_t
     */
    public final int flags;

    /**
     * Reference into ResTable_package::keyStrings identifying this entry.
     * public long keyRef;
     */
    public final String key;

    /**
     * the resvalue following this resource entry.
     */
    @Nullable
    public final ResourceValue value;

    public ResourceEntry(final int size, final int flags, final String key, @Nullable final ResourceValue value) {
        this.size = size;
        this.flags = flags;
        this.key = key;
        this.value = value;
    }

    /**
     * get value as string
     */
    @Nullable
    public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
        final ResourceValue value = this.value;
        if (value != null) {
            return value.toStringValue(resourceTable, locale);
        } else {
            return "null";
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ResourceEntry{" +
                "size=" + this.size +
                ", flags=" + this.flags +
                ", key='" + this.key + '\'' +
                ", value=" + this.value +
                '}';
    }
}
