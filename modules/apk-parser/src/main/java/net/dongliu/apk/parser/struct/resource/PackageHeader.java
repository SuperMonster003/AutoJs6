package net.dongliu.apk.parser.struct.resource;

import androidx.annotation.NonNull;

import net.dongliu.apk.parser.struct.ChunkHeader;
import net.dongliu.apk.parser.struct.ChunkType;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.ParseUtils;
import net.dongliu.apk.parser.utils.Unsigned;

import java.nio.ByteBuffer;

/**
 * @author dongliu
 */
public class PackageHeader extends ChunkHeader {

    /**
     * ResourcePackage IDs start at 1 (corresponding to the value of the package bits in a resource identifier).
     * 0 means this is not a base package.
     * uint32_t
     * 0 framework-res.apk
     * 2-9 other framework files
     * 127 application package
     * Anroid 5.0+: Shared libraries will be assigned a package ID of 0x00 at build-time.
     * At runtime, all loaded shared libraries will be assigned a new package ID.
     */
    private int id;

    /**
     * Actual name of this package, -terminated.
     * char16_t name[128]
     */
    @NonNull
    private final String name;

    /**
     * Offset to a ResStringPool_header defining the resource type symbol table.
     * If zero, this package is inheriting from another base package (overriding specific values in it).
     * uinit 32
     */
    private final int typeStrings;

    /**
     * Last index into typeStrings that is for public use by others.
     * uint32_t
     */
    public final int lastPublicType;

    /**
     * Offset to a ResStringPool_header defining the resource
     * key symbol table.  If zero, this package is inheriting from
     * another base package (overriding specific values in it).
     * uint32_t
     */
    private final int keyStrings;

    /**
     * Last index into keyStrings that is for public use by others.
     * uint32_t
     */
    public final int lastPublicKey;

    public PackageHeader(final int headerSize, final long chunkSize, final @NonNull ByteBuffer buffer) {
        super(ChunkType.TABLE_PACKAGE, headerSize, chunkSize);
        this.id = Unsigned.toUInt(Buffers.readUInt(buffer));
        this.name = ParseUtils.readStringUTF16(buffer, 128);
        this.typeStrings = Unsigned.ensureUInt(Buffers.readUInt(buffer));
        this.lastPublicType = Unsigned.ensureUInt(Buffers.readUInt(buffer));
        this.keyStrings = Unsigned.ensureUInt(Buffers.readUInt(buffer));
        this.lastPublicKey = Unsigned.ensureUInt(Buffers.readUInt(buffer));
    }

    public long getId() {
        return Unsigned.toLong(this.id);
    }

    public void setId(final long id) {
        this.id = Unsigned.toUInt(id);
    }

    @NonNull
    public String getName() {
        return this.name;
    }

    public int getTypeStrings() {
        return this.typeStrings;
    }

    public int getKeyStrings() {
        return this.keyStrings;
    }

}
