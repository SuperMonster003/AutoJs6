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
    private String name;
    public final short id;

    @NonNull
    public final Locale locale;

    private StringPool keyStringPool;
    private ByteBuffer buffer;
    private long[] offsets;
    private StringPool stringPool;

    /**
     * see Densities.java for values
     */
    public final int density;

    public Type(final @NonNull TypeHeader header) {
        this.id = header.getId();
        final ResTableConfig config = header.config;
        this.locale = new Locale(config.getLanguage(), config.getCountry());
        this.density = config.getDensity();
    }

    @Nullable
    public ResourceEntry getResourceEntry(final int resId) {
        if (resId >= this.offsets.length) {
            return null;
        }
        if (this.offsets[resId] == TypeHeader.NO_ENTRY) {
            return null;
        }
        if( offsets[resId] >= buffer.limit() ) {
            //System.out.println( "invalid offset: " + offsets[resId] );
            return null;
        }
        // read Resource Entries
        Buffers.position(this.buffer, this.offsets[resId]);
        return this.readResourceEntry();
    }

    private ResourceEntry readResourceEntry() {
        long beginPos = buffer.position();
//        ResourceEntry resourceEntry = new ResourceEntry();
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

            //An individual complex Resource entry comprises an entry immediately followed by one or more fields.
            ResourceTableMap[] resourceTableMaps = new ResourceTableMap[(int) count];
            for (int i = 0; i < count; i++) {
                resourceTableMaps[i] = readResourceTableMap();
            }
//            ResourceEntry resourceEntry = new ResourceEntry(size,flags,key,resourceTableMaps);
            ResourceMapEntry resourceMapEntry = new ResourceMapEntry(size,flags,key,parent,count,resourceTableMaps);
            return resourceMapEntry;
        } else if ((flags & ResourceEntry.FLAG_COMPACT) != 0) {
            final ResourceValue value = ResourceValue.string((int) keyRef, stringPool);
            return new ResourceEntry(size,flags,null,value);
        } else {
            String key = keyStringPool.get((int) keyRef);
            Buffers.position(buffer, beginPos + size);
            final ResourceValue value = ParseUtils.readResValue(buffer, stringPool);
            return new ResourceEntry(size,flags,key,value);
        }
    }

    private ResourceTableMap readResourceTableMap() {
        final ResourceTableMap resourceTableMap = new ResourceTableMap();
        resourceTableMap.setNameRef(Buffers.readUInt(this.buffer));
        resourceTableMap.setResValue(ParseUtils.readResValue(this.buffer, this.stringPool));
        //noinspection StatementWithEmptyBody
        if ((resourceTableMap.getNameRef() & 0x02000000) != 0) {
            //read arrays
        } else //noinspection StatementWithEmptyBody
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
