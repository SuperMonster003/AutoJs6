package org.autojs.autojs.script;

import org.autojs.autojs.pio.UncheckedIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * Opens script files with Unicode BOM awareness while preserving the legacy default charset for
 * files without a BOM.
 */
public final class ScriptFileEncoding {

    private static final int MAX_BOM_SIZE = 4;
    private static final int READ_BUFFER_SIZE = 8 * 1024;

    private static final Bom[] BOMS = {
            new Bom("UTF-32BE", 0x00, 0x00, 0xFE, 0xFF),
            new Bom("UTF-32LE", 0xFF, 0xFE, 0x00, 0x00),
            new Bom("UTF-8", 0xEF, 0xBB, 0xBF),
            new Bom("UTF-16BE", 0xFE, 0xFF),
            new Bom("UTF-16LE", 0xFF, 0xFE),
    };

    private ScriptFileEncoding() {
        /* Utility class. */
    }

    static String read(File file) {
        try (Reader reader = openReader(file)) {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[READ_BUFFER_SIZE];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                if (count > 0) {
                    result.append(buffer, 0, count);
                }
            }
            return result.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Reader openReader(File file) {
        PushbackInputStream input = null;
        boolean opened = false;
        try {
            input = new PushbackInputStream(new FileInputStream(file), MAX_BOM_SIZE);
            byte[] prefix = new byte[MAX_BOM_SIZE];
            int prefixLength = readPrefix(input, prefix);
            Bom bom = detectBom(prefix, prefixLength);
            int bodyOffset = bom == null ? 0 : bom.bytes.length;
            if (prefixLength > bodyOffset) {
                input.unread(prefix, bodyOffset, prefixLength - bodyOffset);
            }
            Charset charset = bom == null ? Charset.defaultCharset() : Charset.forName(bom.charsetName);
            Reader reader = new InputStreamReader(input, charset);
            opened = true;
            return reader;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (!opened && input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    /* Ignored. */
                }
            }
        }
    }

    private static int readPrefix(PushbackInputStream input, byte[] prefix) throws IOException {
        int total = 0;
        while (total < prefix.length) {
            int count = input.read(prefix, total, prefix.length - total);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                int singleByte = input.read();
                if (singleByte < 0) {
                    break;
                }
                prefix[total++] = (byte) singleByte;
            } else {
                total += count;
            }
        }
        return total;
    }

    private static Bom detectBom(byte[] prefix, int prefixLength) {
        for (Bom bom : BOMS) {
            if (bom.matches(prefix, prefixLength)) {
                return bom;
            }
        }
        return null;
    }

    private static final class Bom {

        private final String charsetName;
        private final int[] bytes;

        private Bom(String charsetName, int... bytes) {
            this.charsetName = charsetName;
            this.bytes = bytes;
        }

        private boolean matches(byte[] prefix, int prefixLength) {
            if (prefixLength < bytes.length) {
                return false;
            }
            for (int i = 0; i < bytes.length; i++) {
                if ((prefix[i] & 0xFF) != bytes[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
