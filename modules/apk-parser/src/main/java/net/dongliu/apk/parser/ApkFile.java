package net.dongliu.apk.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.bean.ApkSignStatus;
import net.dongliu.apk.parser.utils.Inputs;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ApkFile, for parsing apk file info.
 * This class is not thread-safe.
 *
 * @author dongliu
 */
public class ApkFile extends AbstractApkFile implements Closeable {

    @NonNull
    private final ZipFile zf;
    private final File apkFile;

    public ApkFile(final @NonNull File apkFile) throws IOException {
        this.apkFile = apkFile;
        // create zip file cost time, use one zip file for apk parser life cycle
        this.zf = new ZipFile(apkFile);
    }

    public ApkFile(@NonNull final String filePath) throws IOException {
        this(new File(filePath));
    }

    @NonNull
    @Override
    protected List<CertificateFile> getAllCertificateData() throws IOException {
        final Enumeration<? extends ZipEntry> enu = this.zf.entries();
        final List<CertificateFile> list = new ArrayList<>();
        while (enu.hasMoreElements()) {
            final ZipEntry ne = enu.nextElement();
            if (ne.isDirectory()) {
                continue;
            }
            final String name = ne.getName().toUpperCase();
            if (name.endsWith(".RSA") || name.endsWith(".DSA")) {
                list.add(new CertificateFile(name, Inputs.readAllAndClose(this.zf.getInputStream(ne))));
            }
        }
        return list;
    }

    @Nullable
    @Override
    public byte[] getFileData(final @NonNull String path) throws IOException {
        final ZipEntry entry = this.zf.getEntry(path);
        if (entry == null) {
            return null;
        }
        final InputStream inputStream = this.zf.getInputStream(entry);
        return Inputs.readAllAndClose(inputStream);
    }

    @NonNull
    @Override
    protected ByteBuffer fileData() throws IOException {
        final FileChannel channel = new FileInputStream(this.apkFile).getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated using google official ApkVerifier of apksig lib instead.
     */
    @NonNull
    @Override
    @Deprecated
    public ApkSignStatus verifyApk() throws IOException {
        final ZipEntry entry = this.zf.getEntry("META-INF/MANIFEST.MF");
        if (entry == null) {
            // apk is not signed;
            return ApkSignStatus.NotSigned;
        }
        try (final JarFile jarFile = new JarFile(this.apkFile)) {
            final Enumeration<JarEntry> entries = jarFile.entries();
            final byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                final JarEntry e = entries.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                try (final InputStream in = jarFile.getInputStream(e)) {
                    // Read in each jar entry. A security exception will be thrown if a signature/digest check fails.
                    //noinspection StatementWithEmptyBody
                    while (in.read(buffer, 0, buffer.length) != -1) {
                        // Don't care
                    }
                } catch (final SecurityException se) {
                    return ApkSignStatus.Incorrect;
                }
            }
        }
        return ApkSignStatus.Signed;
    }

    @Override
    public void close() throws IOException {
        try (final Closeable superClosable = ApkFile.super::close) {
        }
    }

}
