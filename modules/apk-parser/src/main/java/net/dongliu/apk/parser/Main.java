package net.dongliu.apk.parser;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Locale;

/**
 * Main method for parser apk
 *
 * @author Liu Dong {@literal <dongliu@live.cn>}
 */
public class Main {
    public static void main(final @NonNull String[] args) throws IOException, CertificateException {
        final String action = args[0];
        final String apkPath = args[1];
        try (final ApkFile apkFile = new ApkFile(apkPath)) {
            apkFile.setPreferredLocale(Locale.getDefault());
            switch (action) {
                case "meta":
                    System.out.println(apkFile.getApkMeta());
                    break;
                case "manifest":
                    System.out.println(apkFile.getManifestXml());
                    break;
                case "signer":
                    System.out.println(apkFile.getApkSigners());
                    break;
                default:
            }

        }
    }
}
