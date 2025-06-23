package net.dongliu.apk.parser.struct.zip;

/**
 * End of central directory record
 */
public class EOCD {
    public static final int SIGNATURE = 0x06054b50;
    /**
     * private int signature;
     * Number of this disk
     */
    private short diskNum;
    /**
     * Disk where central directory starts
     */
    private short cdStartDisk;
    /**
     * Number of central directory records on this disk
     */
    private short cdRecordNum;
    /**
     * Total number of central directory records
     */
    private short totalCDRecordNum;
    /**
     * Size of central directory (bytes)
     */
    private int cdSize;
    /**
     * Offset of start of central directory, relative to start of archive
     */
    private int cdStart;
    /**
     * Comment length (n)
     */
    private short commentLen;

    public short getDiskNum() {
        return this.diskNum;
    }

    public void setDiskNum(final int diskNum) {
        this.diskNum = (short) diskNum;
    }

    public int getCdStartDisk() {
        return this.cdStartDisk & 0xffff;
    }

    public void setCdStartDisk(final int cdStartDisk) {
        this.cdStartDisk = (short) cdStartDisk;
    }

    public int getCdRecordNum() {
        return this.cdRecordNum & 0xffff;
    }

    public void setCdRecordNum(final int cdRecordNum) {
        this.cdRecordNum = (short) cdRecordNum;
    }

    public int getTotalCDRecordNum() {
        return this.totalCDRecordNum & 0xffff;
    }

    public void setTotalCDRecordNum(final int totalCDRecordNum) {
        this.totalCDRecordNum = (short) totalCDRecordNum;
    }

    public long getCdSize() {
        return this.cdSize & 0xffffffffL;
    }

    public void setCdSize(final long cdSize) {
        this.cdSize = (int) cdSize;
    }

    public long getCdStart() {
        return this.cdStart & 0xffffffffL;
    }

    public void setCdStart(final long cdStart) {
        this.cdStart = (int) cdStart;
    }

    public int getCommentLen() {
        return this.commentLen & 0xffff;
    }

    public void setCommentLen(final int commentLen) {
        this.commentLen = (short) commentLen;
    }

}
