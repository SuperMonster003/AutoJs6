package org.autojs.autojs.io;

import org.autojs.autojs.pio.PFiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Zip {

    public static void unzip(InputStream stream, File dir) throws IOException {
        FileOutputStream fos = null;
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(stream);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(dir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    PFiles.ensureDir(file.getPath());
                    fos = new FileOutputStream(file);
                    PFiles.write(zis, fos, false);
                    fos.close();
                    fos = null;
                    zis.closeEntry();
                }
            }
        } finally {
            PFiles.closeSilently(fos);
            PFiles.closeSilently(stream);
            PFiles.closeSilently(zis);
        }
    }

    public static void unzip(File zipFile, File dir) throws IOException {
        unzip(new FileInputStream(zipFile), dir);
    }


}
