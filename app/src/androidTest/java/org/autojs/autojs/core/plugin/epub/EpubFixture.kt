package org.autojs.autojs.core.plugin.epub

import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal EPUB 3 written by the test itself (two chapters, a navigation document, one PNG):
 * the host repository keeps no binary fixtures, and the values below are what the round trip
 * asserts against.
 */
object EpubFixture {

    const val TITLE = "Host Round Trip"
    const val AUTHOR = "AutoJs6 host tests"
    const val CHAPTER1_HREF = "OEBPS/chapter1.xhtml"
    const val CHAPTER2_HREF = "OEBPS/chapter2.xhtml"
    const val IMAGE_HREF = "OEBPS/images/dot.png"
    const val CHAPTER1_HEADING = "The Harbour"
    const val CHAPTER2_HEADING = "The Lighthouse"
    const val SEARCH_WORD = "lantern"

    /** A 1x1 PNG. */
    val PNG: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41, 0x54,
        0x78, 0x9C.toByte(), 0x63, 0x60, 0x00, 0x02, 0x00, 0x00, 0x05, 0x00, 0x01,
        0xE2.toByte(), 0x26, 0x05, 0x9B.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )

    val CHAPTER1_XHTML: String = xhtml(
        CHAPTER1_HEADING,
        "<p>Boats rocked in the harbour while the keeper trimmed his $SEARCH_WORD before dusk.</p>" +
            "<p>Gulls circled the pier. Nobody noticed the tide turning.</p>" +
            "<figure><img src=\"images/dot.png\" alt=\"A single dot\"/><figcaption>A single dot</figcaption></figure>",
    )

    val CHAPTER2_XHTML: String = xhtml(
        CHAPTER2_HEADING,
        "<p>The $SEARCH_WORD burned all night on the reef. By dawn the $SEARCH_WORD was out.</p>" +
            "<blockquote><p>Keep the light.</p></blockquote>",
    )

    private const val NAV_XHTML = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Contents</title></head>
<body>
<nav epub:type="toc"><h1>Contents</h1>
<ol>
<li><a href="chapter1.xhtml">$CHAPTER1_HEADING</a></li>
<li><a href="chapter2.xhtml">$CHAPTER2_HEADING</a></li>
</ol>
</nav>
</body>
</html>
"""

    private const val CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
"""

    private const val PACKAGE_OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="en">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">urn:uuid:autojs6-host-round-trip</dc:identifier>
<dc:title>$TITLE</dc:title>
<dc:creator>$AUTHOR</dc:creator>
<dc:language>en</dc:language>
<meta property="dcterms:modified">2026-09-20T00:00:00Z</meta>
</metadata>
<manifest>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
<item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
<item id="dot" href="images/dot.png" media-type="image/png"/>
</manifest>
<spine>
<itemref idref="c1"/>
<itemref idref="c2"/>
</spine>
</package>
"""

    private fun xhtml(heading: String, body: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>$heading</title></head>\n" +
            "<body><h1>$heading</h1>\n$body\n</body></html>\n"

    /** Writes the fixture to [file] (parent directories are created) and returns it. */
    fun write(file: File): File {
        file.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                compressedSize = mimetype.size.toLong()
                crc = CRC32().apply { update(mimetype) }.value
            })
            zip.write(mimetype)
            zip.closeEntry()
            entry(zip, "META-INF/container.xml", CONTAINER_XML.toByteArray())
            entry(zip, "OEBPS/package.opf", PACKAGE_OPF.toByteArray())
            entry(zip, "OEBPS/nav.xhtml", NAV_XHTML.toByteArray())
            entry(zip, CHAPTER1_HREF, CHAPTER1_XHTML.toByteArray())
            entry(zip, CHAPTER2_HREF, CHAPTER2_XHTML.toByteArray())
            entry(zip, IMAGE_HREF, PNG)
        }
        return file
    }

    private fun entry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }
}
