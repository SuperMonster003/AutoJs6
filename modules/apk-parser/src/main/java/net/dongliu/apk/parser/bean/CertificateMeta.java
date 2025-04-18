package net.dongliu.apk.parser.bean;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * basic certificate info.
 *
 * @author dongliu
 */
public class CertificateMeta {

    /**
     * the sign algorithm name
     */
    public final String signAlgorithm;

    /**
     * the signature algorithm OID string.
     * An OID is represented by a set of non-negative whole numbers separated by periods.
     * For example, the string "1.2.840.10040.4.3" identifies the SHA-1 with DSA signature algorithm defined in
     * <a href="http://www.ietf.org/rfc/rfc3279.txt">
     * RFC 3279: Algorithms and Identifiers for the Internet X.509 Public Key Infrastructure Certificate and CRL Profile
     * </a>.
     */
    public final String signAlgorithmOID;

    /**
     * the start date of the validity period.
     */
    public final Date startDate;

    /**
     * the end date of the validity period.
     */
    public final Date endDate;

    /**
     * certificate binary data.
     */
    public final byte[] data;

    /**
     * first use base64 to encode certificate binary data, and then calculate md5 of base64b string.
     * some programs use this as the certMd5 of certificate
     */
    public final String certBase64Md5;

    /**
     * use md5 to calculate certificate's certMd5.
     */
    public final String certMd5;

    public CertificateMeta(final @NonNull String signAlgorithm, final @NonNull String signAlgorithmOID, final @NonNull Date startDate, final @NonNull Date endDate,
                           final @NonNull byte[] data, final @NonNull String certBase64Md5, final @NonNull String certMd5) {
        this.signAlgorithm = signAlgorithm;
        this.signAlgorithmOID = signAlgorithmOID;
        this.startDate = startDate;
        this.endDate = endDate;
        this.data = data;
        this.certBase64Md5 = certBase64Md5;
        this.certMd5 = certMd5;
    }

    @NonNull
    @Override
    public String toString() {
        final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        return "CertificateMeta{signAlgorithm=" + this.signAlgorithm + ", " +
                "certBase64Md5=" + this.certBase64Md5 + ", " +
                "startDate=" + df.format(this.startDate) + ", " + "endDate=" + df.format(this.endDate) + "}";
    }
}
