package net.dongliu.apk.parser.bean

import net.dongliu.apk.parser.bean.CertificateMeta

/**
 * ApkSignV1 certificate file.
 */
class ApkSigner(
    /**
     * The cert file path in apk file
     */
    val path: String,
    /**
     * The meta info of certificate contained in this cert file.
     */
    val certificateMetas: List<CertificateMeta>
) {
    override fun toString(): String {
        return "ApkSigner{" +
                "path='" + path + '\'' +
                ", certificateMetas=" + certificateMetas +
                '}'
    }
}
