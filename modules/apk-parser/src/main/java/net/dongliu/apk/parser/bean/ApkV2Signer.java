package net.dongliu.apk.parser.bean;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * ApkSignV1 certificate file.
 */
public class ApkV2Signer {
    /**
     * The meta info of certificate contained in this cert file.
     */
    @NonNull
    public final List<CertificateMeta> certificateMetas;

    public ApkV2Signer(final @NonNull List<CertificateMeta> certificateMetas) {
        this.certificateMetas = certificateMetas;
    }

}
