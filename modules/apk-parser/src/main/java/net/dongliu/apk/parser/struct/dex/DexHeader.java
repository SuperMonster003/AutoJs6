package net.dongliu.apk.parser.struct.dex;

/**
 * dex file header.
 * see http://dexandroid.googlecode.com/svn/trunk/dalvik/libdex/DexFile.h
 *
 * @author dongliu
 */
public class DexHeader {

    public static final int kSHA1DigestLen = 20;
    public static final int kSHA1DigestOutputLen = DexHeader.kSHA1DigestLen * 2 + 1;

    /**
     * includes version number. 8 bytes.
     * public short magic;
     */
    private int version;
    /**
     * adler32 checksum. u4
     * public long checksum;
     * SHA-1 hash len = kSHA1DigestLen
     */
    private byte[] signature;
    /**
     * length of entire file. u4
     */
    private long fileSize;
    /**
     * len of header.offset to start of next section. u4
     */
    private long headerSize;
    /**
     * u4
     * public long endianTag;
     * u4
     */
    private long linkSize;
    /**
     * u4
     */
    private long linkOff;
    /**
     * u4
     */
    private long mapOff;
    /**
     * u4
     */
    private int stringIdsSize;
    /**
     * u4
     */
    private long stringIdsOff;
    /**
     * u4
     */
    private int typeIdsSize;
    /**
     * u4
     */
    private long typeIdsOff;
    /**
     * u4
     */
    private int protoIdsSize;
    /**
     * u4
     */
    private long protoIdsOff;
    /**
     * u4
     */
    private int fieldIdsSize;
    /**
     * u4
     */
    private long fieldIdsOff;
    /**
     * u4
     */
    private int methodIdsSize;
    /**
     * u4
     */
    private long methodIdsOff;
    /**
     * u4
     */
    private int classDefsSize;
    /**
     * u4
     */
    private long classDefsOff;
    /**
     * u4
     */
    private int dataSize;
    /**
     * u4
     */
    private long dataOff;

    public int getVersion() {
        return this.version;
    }

    public void setVersion(final int version) {
        this.version = version;
    }

    public byte[] getSignature() {
        return this.signature;
    }

    public void setSignature(final byte[] signature) {
        this.signature = signature;
    }

    public long getFileSize() {
        return this.fileSize;
    }

    public void setFileSize(final long fileSize) {
        this.fileSize = fileSize;
    }

    public long getHeaderSize() {
        return this.headerSize;
    }

    public void setHeaderSize(final long headerSize) {
        this.headerSize = headerSize;
    }

    public long getLinkSize() {
        return this.linkSize;
    }

    public void setLinkSize(final long linkSize) {
        this.linkSize = linkSize;
    }

    public long getLinkOff() {
        return this.linkOff;
    }

    public void setLinkOff(final long linkOff) {
        this.linkOff = linkOff;
    }

    public long getMapOff() {
        return this.mapOff;
    }

    public void setMapOff(final long mapOff) {
        this.mapOff = mapOff;
    }

    public int getStringIdsSize() {
        return this.stringIdsSize;
    }

    public void setStringIdsSize(final int stringIdsSize) {
        this.stringIdsSize = stringIdsSize;
    }

    public long getStringIdsOff() {
        return this.stringIdsOff;
    }

    public void setStringIdsOff(final long stringIdsOff) {
        this.stringIdsOff = stringIdsOff;
    }

    public int getTypeIdsSize() {
        return this.typeIdsSize;
    }

    public void setTypeIdsSize(final int typeIdsSize) {
        this.typeIdsSize = typeIdsSize;
    }

    public long getTypeIdsOff() {
        return this.typeIdsOff;
    }

    public void setTypeIdsOff(final long typeIdsOff) {
        this.typeIdsOff = typeIdsOff;
    }

    public int getProtoIdsSize() {
        return this.protoIdsSize;
    }

    public void setProtoIdsSize(final int protoIdsSize) {
        this.protoIdsSize = protoIdsSize;
    }

    public long getProtoIdsOff() {
        return this.protoIdsOff;
    }

    public void setProtoIdsOff(final long protoIdsOff) {
        this.protoIdsOff = protoIdsOff;
    }

    public int getFieldIdsSize() {
        return this.fieldIdsSize;
    }

    public void setFieldIdsSize(final int fieldIdsSize) {
        this.fieldIdsSize = fieldIdsSize;
    }

    public long getFieldIdsOff() {
        return this.fieldIdsOff;
    }

    public void setFieldIdsOff(final long fieldIdsOff) {
        this.fieldIdsOff = fieldIdsOff;
    }

    public int getMethodIdsSize() {
        return this.methodIdsSize;
    }

    public void setMethodIdsSize(final int methodIdsSize) {
        this.methodIdsSize = methodIdsSize;
    }

    public long getMethodIdsOff() {
        return this.methodIdsOff;
    }

    public void setMethodIdsOff(final long methodIdsOff) {
        this.methodIdsOff = methodIdsOff;
    }

    public int getClassDefsSize() {
        return this.classDefsSize;
    }

    public void setClassDefsSize(final int classDefsSize) {
        this.classDefsSize = classDefsSize;
    }

    public long getClassDefsOff() {
        return this.classDefsOff;
    }

    public void setClassDefsOff(final long classDefsOff) {
        this.classDefsOff = classDefsOff;
    }

    public int getDataSize() {
        return this.dataSize;
    }

    public void setDataSize(final int dataSize) {
        this.dataSize = dataSize;
    }

    public long getDataOff() {
        return this.dataOff;
    }

    public void setDataOff(final long dataOff) {
        this.dataOff = dataOff;
    }
}
