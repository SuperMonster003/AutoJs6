package net.dongliu.apk.parser.parser

import net.dongliu.apk.parser.bean.CertificateMeta
import net.dongliu.apk.parser.cert.asn1.*
import net.dongliu.apk.parser.cert.pkcs7.*
import net.dongliu.apk.parser.parser.CertificateMetas.from
import net.dongliu.apk.parser.utils.Buffers
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.security.cert.*

/**
 * Parser certificate info using jsse.
 *
 * @author dongliu
 */
internal class JSSECertificateParser(data: ByteArray) : CertificateParser(data) {
    @Throws(CertificateException::class)
    override fun parse(): List<CertificateMeta> {
        val contentInfo: ContentInfo = try {
            Asn1BerParser.parse(ByteBuffer.wrap(data), ContentInfo::class.java)
        } catch (e: Asn1DecodingException) {
            throw CertificateException(e)
        }
        if (Pkcs7Constants.OID_SIGNED_DATA != contentInfo.contentType) {
            throw CertificateException("Unsupported ContentInfo.contentType: " + contentInfo.contentType)
        }
        val signedData: SignedData = try {
            Asn1BerParser.parse(contentInfo.content.encoded, SignedData::class.java)
        } catch (e: Asn1DecodingException) {
            throw CertificateException(e)
        }
        val encodedCertificates = signedData.certificates
        val certFactory = CertificateFactory.getInstance("X.509")
        val result: MutableList<X509Certificate> = ArrayList(encodedCertificates.size)
        for (i in encodedCertificates.indices) {
            val encodedCertificate = encodedCertificates[i]
            val encodedForm = Buffers.readBytes(encodedCertificate.encoded)
            val certificate = certFactory.generateCertificate(ByteArrayInputStream(encodedForm))
            result.add(certificate as X509Certificate)
        }
        return from(result)
    }
}
