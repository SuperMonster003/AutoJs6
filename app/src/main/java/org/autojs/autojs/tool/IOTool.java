package org.autojs.autojs.tool;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class IOTool {
    private static final String TAG = IOTool.class.getSimpleName();

    public static void close(Closeable io) {
        try {
            if (io != null) {
                io.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "ex: " + e);
        }
    }

    public static void close(Closeable io, boolean exceptionMatters) throws IOException {
        try {
            if (io != null) {
                io.close();
            }
        } catch (IOException e) {
            if (exceptionMatters) {
                throw e;
            }
        }
    }

    public static byte[] gzip(String str) {
        ByteArrayOutputStream out = null;
        GZIPOutputStream gzip = null;
        try {
            out = new ByteArrayOutputStream();
            gzip = new GZIPOutputStream(out);

            gzip.write(str.getBytes(StandardCharsets.UTF_8));
            gzip.finish();

            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(out);
            close(gzip);
        }
        return null;
    }

    public static String gunzip(byte[] bytes) {
        ByteArrayOutputStream out = null;
        GZIPInputStream gzip = null;
        try {
            out = new ByteArrayOutputStream();
            gzip = new GZIPInputStream(new ByteArrayInputStream(bytes));

            int res;
            byte[] buf = new byte[1024];
            while ((res = gzip.read(buf)) != -1) {
                out.write(buf, 0, res);
            }
            out.flush();

            return out.toString(String.valueOf(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(out);
            close(gzip);
        }
        return "";
    }
}
