package net.dongliu.apk.parser.struct.resource;

/**
 * Library chunk entry
 *
 * @author Liu Dong
 */
public class LibraryEntry {
    /**
     * uint32. The package-id this shared library was assigned at build time.
     */
    public final int packageId;

    /**
     * The package name of the shared library. \0 terminated. max 128
     */
    public final String name;

    public LibraryEntry(final int packageId, final String name) {
        this.packageId = packageId;
        this.name = name;
    }
}
