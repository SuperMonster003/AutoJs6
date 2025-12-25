package net.dongliu.apk.parser.struct.resource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import net.dongliu.apk.parser.struct.ResourceValue;
import net.dongliu.apk.parser.struct.StringPool;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.ParseUtils;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * @author dongliu
 */
public class Type {
    public final short id;
    @NonNull
    public final Locale locale;
    /**
     * see Densities.java for values
     */
    public final int density;
    private String name;
    private StringPool keyStringPool;
    private ByteBuffer buffer;
    private long[] offsets;
    private StringPool stringPool;

    public Type(final @NonNull TypeHeader header) {
        this.id = header.getId();
        final ResTableConfig config = header.config;

        // Normalize and handle language/region safely to avoid IllformedLocaleException.
        // zh-CN: 规范化并容错处理 language/region, 避免 IllformedLocaleException.
        final String rawLang = config.getLanguage();
        final String rawRegion = config.getCountry();

        final String lang = sanitizeLanguage(rawLang);
        final String region = sanitizeRegion(rawRegion);

        Locale tmpLocale;
        try {
            final Locale.Builder builder = new Locale.Builder();
            if (lang != null) {
                builder.setLanguage(lang);
            }
            if (region != null) {
                builder.setRegion(region);
            }
            tmpLocale = builder.build();
        } catch (final RuntimeException e) {
            // Handle all invalid data including IllformedLocaleException.
            // zh-CN: 包含 IllformedLocaleException 在内的所有非法数据的兜底.
            if (lang != null) {
                // At least keep valid language.
                // zh-CN: 至少保留合法 language.
                tmpLocale = Locale.forLanguageTag(lang);
            } else {
                tmpLocale = Locale.ROOT;
            }
        }
        this.locale = tmpLocale;

        this.density = config.getDensity();
    }

    // Only allows 2 or 3 letter characters (BCP-47 common language subtags).
    // zh-CN: 仅允许 2 或 3 位字母 (BCP-47 常见语言子标签).
    private static String sanitizeLanguage(@Nullable String lang) {
        if (lang == null) return null;
        lang = lang.trim();
        if (lang.isEmpty()) return null;
        // Filter out non-letter characters.
        // zh-CN: 过滤非字母字符.
        final String cleaned = lang.replaceAll("[^A-Za-z]", "");
        if (cleaned.length() == 2 || cleaned.length() == 3) {
            return cleaned.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    // Only allows 2 letter or 3 digit characters (e.g. CN/US/419).
    // zh-CN: 仅允许 2 位字母或 3 位数字 (如 CN/US/419).
    private static String sanitizeRegion(@Nullable String region) {
        if (region == null) return null;
        region = region.trim();
        if (region.isEmpty()) return null;

        final String letters = region.replaceAll("[^A-Za-z]", "");
        if (letters.length() == 2) {
            return letters.toUpperCase(Locale.ROOT);
        }
        final String digits = region.replaceAll("[^0-9]", "");
        if (digits.length() == 3) {
            return digits;
        }
        return null;
    }

    @Nullable
    public ResourceEntry getResourceEntry(final int resId) {
        if (resId >= this.offsets.length) {
            return null;
        }
        if (this.offsets[resId] == TypeHeader.NO_ENTRY) {
            return null;
        }
        if (offsets[resId] >= buffer.limit()) {
            // System.out.println( "invalid offset: " + offsets[resId] );
            return null;
        }
        // read Resource Entries
        Buffers.position(this.buffer, this.offsets[resId]);
        return this.readResourceEntry();
    }

    private ResourceEntry readResourceEntry() {
        long beginPos = buffer.position();
        // ResourceEntry resourceEntry = new ResourceEntry();
        // size is always 8(simple), or 16(complex)
        final int size = Buffers.readUShort(buffer);
        final int flags = Buffers.readUShort(buffer);
        long keyRef = buffer.getInt();

        if ((flags & ResourceEntry.FLAG_COMPLEX) != 0) {
            String key = keyStringPool.get((int) keyRef);

            // Resource identifier of the parent mapping, or 0 if there is none.
            final long parent = Buffers.readUInt(buffer);
            final long count = Buffers.readUInt(buffer);

            Buffers.position(buffer, beginPos + size);

            // An individual complex Resource entry comprises an entry immediately followed by one or more fields.
            ResourceTableMap[] resourceTableMaps = new ResourceTableMap[(int) count];
            for (int i = 0; i < count; i++) {
                resourceTableMaps[i] = readResourceTableMap();
            }
            // ResourceEntry resourceEntry = new ResourceEntry(size, flags, key, resourceTableMaps);
            ResourceMapEntry resourceMapEntry = new ResourceMapEntry(size, flags, key, parent, count, resourceTableMaps);
            return resourceMapEntry;
        } else if ((flags & ResourceEntry.FLAG_COMPACT) != 0) {
            final ResourceValue value = ResourceValue.string((int) keyRef, stringPool);
            return new ResourceEntry(size, flags, null, value);
        } else {
            String key = keyStringPool.get((int) keyRef);
            Buffers.position(buffer, beginPos + size);
            final ResourceValue value = ParseUtils.readResValue(buffer, stringPool);
            return new ResourceEntry(size, flags, key, value);
        }
    }

    private ResourceTableMap readResourceTableMap() {
        final ResourceTableMap resourceTableMap = new ResourceTableMap();
        resourceTableMap.setNameRef(Buffers.readUInt(this.buffer));
        resourceTableMap.setResValue(ParseUtils.readResValue(this.buffer, this.stringPool));
        // noinspection StatementWithEmptyBody
        if ((resourceTableMap.getNameRef() & 0x02000000) != 0) {
            // read arrays
        } else // noinspection StatementWithEmptyBody
            if ((resourceTableMap.getNameRef() & 0x01000000) != 0) {
                // read attrs
            } else {
            }
        return resourceTableMap;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public StringPool getKeyStringPool() {
        return this.keyStringPool;
    }

    public void setKeyStringPool(final StringPool keyStringPool) {
        this.keyStringPool = keyStringPool;
    }

    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    public void setBuffer(final ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public void setOffsets(final long[] offsets) {
        this.offsets = offsets;
    }

    public void setStringPool(final StringPool stringPool) {
        this.stringPool = stringPool;
    }

    @NonNull
    @Override
    public String toString() {
        return "Type{" +
               "name='" + this.name + '\'' +
               ", id=" + this.id +
               ", locale=" + this.locale +
               '}';
    }
}
