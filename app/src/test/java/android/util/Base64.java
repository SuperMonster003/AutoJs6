package android.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * JVM fake of the Android framework class for local unit tests. The mockable android.jar that local tests run
 * against throws "not mocked" from every method, so code under test that encodes or decodes Base64 (the
 * augment differential tests compare the obsolete JS modules and the Kotlin modules on top of it) could not run
 * at all. Test classes come before the mockable jar on the unit test classpath, so this class replaces it.
 * <p>
 * The behavior follows the AOSP implementation: the encoder wraps every 76 characters and ends wrapped output
 * with a newline unless {@link #NO_WRAP} is set; the decoder skips every character outside the alphabet,
 * accepts missing padding and throws {@code IllegalArgumentException("bad base-64")} for malformed input.
 * <p>
 * zh-CN: Android 框架类的 JVM fake, 供本地单元测试使用. 本地测试所用的 mockable android.jar 对所有方法抛出 "not mocked",
 * 依赖 Base64 的被测代码 (如 augment 差分测试中的旧 JS 模块与 Kotlin 模块) 无法运行; 测试类在单元测试 classpath 上位于
 * mockable jar 之前, 因此本类会替换它. 行为按 AOSP 实现: 编码每 76 个字符换行 (除非 NO_WRAP), 解码跳过字母表以外的字符,
 * 接受缺省填充, 格式错误时抛出 IllegalArgumentException("bad base-64").
 */
@SuppressWarnings("unused")
public class Base64 {

    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    private static final int LINE_GROUPS = 19;
    private static final int SKIP = -1;
    private static final int EQUALS = -2;

    private static final byte[] ENCODE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ENCODE_WEBSAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".getBytes(StandardCharsets.US_ASCII);
    private static final int[] DECODE = decodeTable(ENCODE);
    private static final int[] DECODE_WEBSAFE = decodeTable(ENCODE_WEBSAFE);

    private Base64() {
        /* Empty body. */
    }

    private static int[] decodeTable(byte[] alphabet) {
        int[] table = new int[256];
        java.util.Arrays.fill(table, SKIP);
        for (int i = 0; i < alphabet.length; i++) {
            table[alphabet[i] & 0xff] = i;
        }
        table['='] = EQUALS;
        return table;
    }

    public static byte[] decode(String str, int flags) {
        return decode(str.getBytes(), flags);
    }

    public static byte[] decode(byte[] input, int flags) {
        return decode(input, 0, input.length, flags);
    }

    public static byte[] decode(byte[] input, int offset, int len, int flags) {
        int[] table = (flags & URL_SAFE) == 0 ? DECODE : DECODE_WEBSAFE;
        ByteArrayOutputStream out = new ByteArrayOutputStream(len * 3 / 4);
        int state = 0;
        int value = 0;
        for (int p = offset; p < offset + len; p++) {
            int d = table[input[p] & 0xff];
            switch (state) {
                case 0:
                    if (d >= 0) {
                        value = d;
                        state++;
                    } else if (d != SKIP) {
                        throw badBase64();
                    }
                    break;
                case 1:
                    if (d >= 0) {
                        value = (value << 6) | d;
                        state++;
                    } else if (d != SKIP) {
                        throw badBase64();
                    }
                    break;
                case 2:
                    if (d >= 0) {
                        value = (value << 6) | d;
                        state++;
                    } else if (d == EQUALS) {
                        out.write(value >> 4);
                        state = 4;
                    } else if (d != SKIP) {
                        throw badBase64();
                    }
                    break;
                case 3:
                    if (d >= 0) {
                        value = (value << 6) | d;
                        out.write(value >> 16);
                        out.write(value >> 8);
                        out.write(value);
                        state = 0;
                    } else if (d == EQUALS) {
                        out.write(value >> 10);
                        out.write(value >> 2);
                        state = 5;
                    } else if (d != SKIP) {
                        throw badBase64();
                    }
                    break;
                case 4:
                    if (d == EQUALS) {
                        state++;
                    } else if (d != SKIP) {
                        throw badBase64();
                    }
                    break;
                default:
                    if (d != SKIP) {
                        throw badBase64();
                    }
                    break;
            }
        }
        switch (state) {
            case 1:
            case 4:
                throw badBase64();
            case 2:
                out.write(value >> 4);
                break;
            case 3:
                out.write(value >> 10);
                out.write(value >> 2);
                break;
            default:
                break;
        }
        return out.toByteArray();
    }

    private static IllegalArgumentException badBase64() {
        return new IllegalArgumentException("bad base-64");
    }

    public static String encodeToString(byte[] input, int flags) {
        return new String(encode(input, flags), StandardCharsets.US_ASCII);
    }

    public static String encodeToString(byte[] input, int offset, int len, int flags) {
        return new String(encode(input, offset, len, flags), StandardCharsets.US_ASCII);
    }

    public static byte[] encode(byte[] input, int flags) {
        return encode(input, 0, input.length, flags);
    }

    public static byte[] encode(byte[] input, int offset, int len, int flags) {
        byte[] alphabet = (flags & URL_SAFE) == 0 ? ENCODE : ENCODE_WEBSAFE;
        boolean padding = (flags & NO_PADDING) == 0;
        boolean newline = (flags & NO_WRAP) == 0;
        boolean cr = (flags & CRLF) != 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream(len * 4 / 3 + 4);
        int groups = 0;
        int p = offset;
        int end = offset + len;
        while (p + 3 <= end) {
            int v = ((input[p] & 0xff) << 16) | ((input[p + 1] & 0xff) << 8) | (input[p + 2] & 0xff);
            out.write(alphabet[(v >> 18) & 0x3f]);
            out.write(alphabet[(v >> 12) & 0x3f]);
            out.write(alphabet[(v >> 6) & 0x3f]);
            out.write(alphabet[v & 0x3f]);
            p += 3;
            if (newline && ++groups == LINE_GROUPS) {
                if (cr) out.write('\r');
                out.write('\n');
                groups = 0;
            }
        }
        int rest = end - p;
        if (rest == 1) {
            int v = (input[p] & 0xff) << 4;
            out.write(alphabet[(v >> 6) & 0x3f]);
            out.write(alphabet[v & 0x3f]);
            if (padding) {
                out.write('=');
                out.write('=');
            }
        } else if (rest == 2) {
            int v = ((input[p] & 0xff) << 10) | ((input[p + 1] & 0xff) << 2);
            out.write(alphabet[(v >> 12) & 0x3f]);
            out.write(alphabet[(v >> 6) & 0x3f]);
            out.write(alphabet[v & 0x3f]);
            if (padding) {
                out.write('=');
            }
        }
        if (newline && out.size() > 0 && (groups > 0 || rest > 0)) {
            if (cr) out.write('\r');
            out.write('\n');
        }
        return out.toByteArray();
    }

}
