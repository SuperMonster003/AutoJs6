package net.dongliu.apk.parser.struct.resource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * @author dongliu.
 */
public class ResourceMapEntry extends ResourceEntry {
    /**
     * Resource identifier of the parent mapping, or 0 if there is none.
     * ResTable_ref specifies the parent Resource, if any, of this Resource.
     * struct ResTable_ref { uint32_t ident; };
     */
    public final long parent;

    /**
     * Number of name/value pairs that follow for FLAG_COMPLEX. uint32_t
     */
    public final long count;
    @NonNull
    public final ResourceTableMap[] resourceTableMaps;

    public ResourceMapEntry(final int size, final int flags, final String key, final long parent, final long count, final @NonNull ResourceTableMap[] resourceTableMaps) {
        super(size, flags, key, null);
        this.parent = parent;
        this.count = count;
        this.resourceTableMaps = resourceTableMaps;
    }

    /**
     * get value as string
     */
    @Nullable
    @Override
    public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
        if (this.resourceTableMaps.length > 0) {
            return this.resourceTableMaps[0].toString();
        } else {
            return null;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ResourceMapEntry{" +
                "parent=" + this.parent +
                ", count=" + this.count +
                ", resourceTableMaps=" + Arrays.toString(this.resourceTableMaps) +
                '}';
    }
}
