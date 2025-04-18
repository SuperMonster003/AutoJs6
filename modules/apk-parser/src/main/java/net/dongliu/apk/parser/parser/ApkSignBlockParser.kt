package net.dongliu.apk.parser.parser

import net.dongliu.apk.parser.struct.signingv2.*
import net.dongliu.apk.parser.utils.*
import java.io.ByteArrayInputStream
import java.nio.*
import java.security.cert.*

/**
 * The Apk Sign Block V2 Parser.
 * see https://source.android.com/security/apksigning/v2
 */
class ApkSignBlockParser(data: ByteBuffer) {
    private val data: ByteBuffer

    init {
        this.data = data.order(ByteOrder.LITTLE_ENDIAN)
    }

    @Throws(CertificateException::class)
    fun parse(): ApkSigningBlock {
        // sign block found, read pairs
        val signerBlocks: MutableList<SignerBlock> = ArrayList()
        while (data.remaining() >= 8) {
            val id = data.int
            val size = Unsigned.ensureUInt(data.int.toLong())
            if (id == ApkSigningBlock.SIGNING_V2_ID) {
                val signingV2Buffer = Buffers.sliceAndSkip(data, size)
                // now only care about apk signing v2 entry
                while (signingV2Buffer.hasRemaining()) {
                    val signerBlock = readSigningV2(signingV2Buffer)
                    signerBlocks.add(signerBlock)
                }
            } else {
                // just ignore now
                Buffers.position(data, data.position() + size)
            }
        }
        return ApkSigningBlock(signerBlocks)
    }

    @Throws(CertificateException::class)
    private fun readSigningV2(inputBuffer: ByteBuffer): SignerBlock {
        val buffer = readLenPrefixData(inputBuffer)
        val signedData = readLenPrefixData(buffer)
        val digestsData = readLenPrefixData(signedData)
        val digests = readDigests(digestsData)
        val certificateData = readLenPrefixData(signedData)
        val certificates = readCertificates(certificateData)
        val attributesData = readLenPrefixData(signedData)
        readAttributes(attributesData)
        val signaturesData = readLenPrefixData(buffer)
        val signatures = readSignatures(signaturesData)
        val publicKeyData = readLenPrefixData(buffer)
        return SignerBlock(digests, certificates, signatures)
    }

    private fun readDigests(buffer: ByteBuffer): List<Digest> {
        val list: MutableList<Digest> = ArrayList()
        while (buffer.hasRemaining()) {
            val digestData = readLenPrefixData(buffer)
            val algorithmID = digestData.int
            val digest = Buffers.readBytes(digestData)
            list.add(Digest(algorithmID, digest))
        }
        return list
    }

    @Throws(CertificateException::class)
    private fun readCertificates(buffer: ByteBuffer): List<X509Certificate> {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificates: MutableList<X509Certificate> = ArrayList()
        while (buffer.hasRemaining()) {
            val certificateData = readLenPrefixData(buffer)
            val certificate = certificateFactory.generateCertificate(
                ByteArrayInputStream(Buffers.readBytes(certificateData))
            )
            certificates.add(certificate as X509Certificate)
        }
        return certificates
    }

    private fun readAttributes(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val attributeData = readLenPrefixData(buffer)
            val id = attributeData.int
            //            byte[] value = Buffers.readBytes(attributeData);
        }
    }

    private fun readSignatures(buffer: ByteBuffer): List<Signature> {
        val signatures: MutableList<Signature> = ArrayList()
        while (buffer.hasRemaining()) {
            val signatureData = readLenPrefixData(buffer)
            val algorithmID = signatureData.int
            val signatureDataLen = Unsigned.ensureUInt(signatureData.int.toLong())
            val signature = Buffers.readBytes(signatureData, signatureDataLen)
            signatures.add(Signature(algorithmID, signature))
        }
        return signatures
    }

    private fun readLenPrefixData(buffer: ByteBuffer): ByteBuffer {
        val len = Unsigned.ensureUInt(buffer.int.toLong())
        return Buffers.sliceAndSkip(buffer, len)
    } //    /**
    //     * 0x0101—RSASSA-PSS with SHA2-256 digest, SHA2-256 MGF1, 32 bytes of salt, trailer: 0xbc
    //     */
    //    private static final int PSS_SHA_256 = 0x0101;
    //    /**
    //     * 0x0102—RSASSA-PSS with SHA2-512 digest, SHA2-512 MGF1, 64 bytes of salt, trailer: 0xbc
    //     */
    //    private static final int PSS_SHA_512 = 0x0102;
    //    /**
    //     * 0x0103—RSASSA-PKCS1-v1_5 with SHA2-256 digest. This is for build systems which require deterministic signatures.
    //     */
    //    private static final int PKCS1_SHA_256 = 0x0103;
    //    /**
    //     * 0x0104—RSASSA-PKCS1-v1_5 with SHA2-512 digest. This is for build systems which require deterministic signatures.
    //     */
    //    private static final int PKCS1_SHA_512 = 0x0104;
    //    /**
    //     * 0x0201—ECDSA with SHA2-256 digest
    //     */
    //    private static final int ECDSA_SHA_256 = 0x0201;
    //    /**
    //     * 0x0202—ECDSA with SHA2-512 digest
    //     */
    //    private static final int ECDSA_SHA_512 = 0x0202;
    //    /**
    //     * 0x0301—DSA with SHA2-256 digest
    //     */
    //    private static final int DSA_SHA_256 = 0x0301;
}
