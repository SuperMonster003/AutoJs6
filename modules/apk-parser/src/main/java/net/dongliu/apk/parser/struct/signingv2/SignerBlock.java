package net.dongliu.apk.parser.struct.signingv2;

import androidx.annotation.NonNull;

import java.security.cert.X509Certificate;
import java.util.List;

public class SignerBlock {
    public final List<Digest> digests;
    public final List<X509Certificate> certificates;
    public final List<Signature> signatures;

    public SignerBlock(final @NonNull List<Digest> digests, final @NonNull List<X509Certificate> certificates, final @NonNull List<Signature> signatures) {
        this.digests = digests;
        this.certificates = certificates;
        this.signatures = signatures;
    }

}
