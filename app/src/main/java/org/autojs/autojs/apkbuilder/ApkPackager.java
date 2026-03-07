package org.autojs.autojs.apkbuilder;

import android.text.TextUtils;

import org.autojs.autojs.apkbuilder.util.StreamUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import pxb.android.tinysign.TinySign;

/**
 * Created by Stardust on Oct 23, 2017.
 */
public class ApkPackager {

    private final InputStream mApkInputStream;
    private final String mWorkspacePath;
    private AtomicBoolean mCancelSignal;

    public ApkPackager(InputStream apkInputStream, String workspacePath) {
        mApkInputStream = apkInputStream;
        mWorkspacePath = workspacePath;
    }

    public ApkPackager(String apkPath, String workspacePath) throws FileNotFoundException {
        mApkInputStream = new FileInputStream(apkPath);
        mWorkspacePath = workspacePath;
    }

    public ApkPackager setCancelSignal(AtomicBoolean cancelSignal) {
        mCancelSignal = cancelSignal;
        return this;
    }

    // Throw early when cancellation is requested to avoid partial outputs.
    // zh-CN: 当收到取消请求时尽早抛出, 以避免产生部分输出.
    private void ensureNotCancelled() {
        if (mCancelSignal != null && mCancelSignal.get()) {
            throw new CancellationException("Build aborted");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Build aborted");
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void unzip() throws IOException {
        // Buffer IO to reduce syscall overhead during APK extraction.
        // zh-CN: 使用缓冲 IO 以减少 APK 解压过程中的系统调用开销.
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(mApkInputStream, StreamUtils.DEFAULT_BUFFER_SIZE))) {
            for (ZipEntry e = zis.getNextEntry(); e != null; e = zis.getNextEntry()) {
                ensureNotCancelled();
                String name = e.getName();
                if (!e.isDirectory() && !TextUtils.isEmpty(name)) {
                    File file = new File(mWorkspacePath, name);
                    File parentFile = file.getParentFile();
                    if (parentFile != null) {
                        parentFile.mkdirs();
                    }
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file), StreamUtils.DEFAULT_BUFFER_SIZE)) {
                        byte[] buffer = new byte[StreamUtils.DEFAULT_BUFFER_SIZE];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            ensureNotCancelled();
                            bos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    public void repackage(String newApkPath) throws Exception {
        FileOutputStream fos = new FileOutputStream(newApkPath);
        TinySign.sign(new File(mWorkspacePath), fos);
        fos.close();
    }

    public void cleanWorkspace() {
        /* Empty body. */
    }

}
