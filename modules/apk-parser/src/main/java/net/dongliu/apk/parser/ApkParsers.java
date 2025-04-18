package net.dongliu.apk.parser;

import androidx.annotation.NonNull;

import net.dongliu.apk.parser.bean.ApkMeta;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Convenient utils method for parse apk file
 *
 * @author Liu Dong
 */
public class ApkParsers {

    private static boolean useBouncyCastle;

    public static boolean useBouncyCastle() {
        return ApkParsers.useBouncyCastle;
    }

    /**
     * Use BouncyCastle instead of JSSE to parse X509 certificate.
     * If want to use BouncyCastle, you will also need to add bcprov and bcpkix lib to your project.
     */
    public static void useBouncyCastle(final boolean useBouncyCastle) {
        ApkParsers.useBouncyCastle = useBouncyCastle;
    }

    /**
     * Get apk meta info for apk file
     */
    public static ApkMeta getMetaInfo(final @NonNull String apkFilePath) throws IOException {
        try (final ApkFile apkFile = new ApkFile(apkFilePath)) {
            return apkFile.getApkMeta();
        }
    }

    /**
     * Get apk meta info for apk file
     */
    public static ApkMeta getMetaInfo(final @NonNull File file) throws IOException {
        try (final ApkFile apkFile = new ApkFile(file)) {
            return apkFile.getApkMeta();
        }
    }

    /**
     * Get apk meta info for apk file
     */
    public static ApkMeta getMetaInfo(final byte[] apkData) throws IOException {
        try (final ByteArrayApkFile apkFile = new ByteArrayApkFile(apkData)) {
            return apkFile.getApkMeta();
        }
    }

    /**
     * Get apk meta info for apk file, with locale
     */
    public static ApkMeta getMetaInfo(final @NonNull String apkFilePath, final Locale locale) throws IOException {
        try (final ApkFile apkFile = new ApkFile(apkFilePath)) {
            apkFile.setPreferredLocale(locale);
            return apkFile.getApkMeta();
        }
    }

    /**
     * Get apk meta info for apk file
     */
    public static ApkMeta getMetaInfo(final @NonNull File file, final Locale locale) throws IOException {
        try (final ApkFile apkFile = new ApkFile(file)) {
            apkFile.setPreferredLocale(locale);
            return apkFile.getApkMeta();
        }
    }

    /**
     * Get apk meta info for apk file
     */
    public static ApkMeta getMetaInfo(final byte[] apkData, final Locale locale) throws IOException {
        try (final ByteArrayApkFile apkFile = new ByteArrayApkFile(apkData)) {
            apkFile.setPreferredLocale(locale);
            return apkFile.getApkMeta();
        }
    }

    /**
     * Get apk manifest xml file as text
     */
    public static String getManifestXml(final @NonNull String apkFilePath) throws IOException {
        try (final ApkFile apkFile = new ApkFile(apkFilePath)) {
            return apkFile.getManifestXml();
        }
    }

    /**
     * Get apk manifest xml file as text
     */
    public static String getManifestXml(final @NonNull File file) throws IOException {
        try (final ApkFile apkFile = new ApkFile(file)) {
            return apkFile.getManifestXml();
        }
    }

    /**
     * Get apk manifest xml file as text
     */
    public static String getManifestXml(final byte[] apkData) throws IOException {
        try (final ByteArrayApkFile apkFile = new ByteArrayApkFile(apkData)) {
            return apkFile.getManifestXml();
        }
    }

    /**
     * Get apk manifest xml file as text
     */
    public static String getManifestXml(final @NonNull String apkFilePath, final Locale locale) throws IOException {
        try (final ApkFile apkFile = new ApkFile(apkFilePath)) {
            apkFile.setPreferredLocale(locale);
            return apkFile.getManifestXml();
        }
    }

    /**
     * Get apk manifest xml file as text
     */
    public static String getManifestXml(final @NonNull File file, final Locale locale) throws IOException {
        try (final ApkFile apkFile = new ApkFile(file)) {
            apkFile.setPreferredLocale(locale);
            return apkFile.getManifestXml();
        }
    }

    /**
     * Get apk manifest xml file as text
     */
    public static String getManifestXml(final byte[] apkData, final Locale locale) throws IOException {
        try (final ByteArrayApkFile apkFile = new ByteArrayApkFile(apkData)) {
            apkFile.setPreferredLocale(locale);
            return apkFile.getManifestXml();
        }
    }
}
