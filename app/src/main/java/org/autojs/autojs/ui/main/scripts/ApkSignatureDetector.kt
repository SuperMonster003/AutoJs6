package org.autojs.autojs.ui.main.scripts

import com.android.apksig.apk.ApkUtils
import com.android.apksig.internal.apk.ApkSigningBlockUtils
import com.android.apksig.util.DataSources
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.jar.JarEntry
import java.util.jar.JarFile

object ApkSignatureDetector {

    private const val BLOCK_ID_V2 = 0x7109871a
    private const val BLOCK_ID_V3 = 0xF05368C0.toInt()
    private const val BLOCK_ID_V4 = 0x2b09189e

    fun detectSchemes(apkFile: File): String? {
        if (!apkFile.isFile) return null

        RandomAccessFile(apkFile, "r").use { raf ->
            val dataSource = DataSources.asDataSource(raf, 0, raf.length())
            val signingBlock = ApkUtils.findApkSigningBlock(dataSource).contents
            val signingBlockBuffer = signingBlock.getByteBuffer(0, signingBlock.size().toInt())

            // Duplicate() is called for each detection to prevent position being modified by previous checks.
            // zh-CN: 每次检测都 duplicate(), 防止 position 被前一次修改.
            val hasV2 = hasScheme(signingBlockBuffer.duplicateAndOrderSignBuffer(), BLOCK_ID_V2)
            val hasV3 = hasScheme(signingBlockBuffer.duplicateAndOrderSignBuffer(), BLOCK_ID_V3)
            val hasV4 = hasScheme(signingBlockBuffer.duplicateAndOrderSignBuffer(), BLOCK_ID_V4)
            val hasV1 = hasV1Signature(apkFile)

            return listOfNotNull(
                "V1".takeIf { hasV1 },
                "V2".takeIf { hasV2 },
                "V3".takeIf { hasV3 },
                "V4".takeIf { hasV4 },
            ).takeUnless { it.isEmpty() }?.joinToString(" + ")
        }
    }

    private fun ByteBuffer.duplicateAndOrderSignBuffer(): ByteBuffer {
        return duplicate().order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun hasScheme(buf: ByteBuffer, id: Int): Boolean = runCatching {
        ApkSigningBlockUtils.findApkSignatureSchemeBlock(buf, id, null)
    }.isSuccess

    private fun hasV1Signature(apkFile: File): Boolean {
        JarFile(apkFile).use { jar ->
            var hasManifest = false
            var hasSF = false
            var hasRSA = false
            for (entry: JarEntry in jar.entries()) {
                val n = entry.name.uppercase()
                when {
                    n == "META-INF/MANIFEST.MF" -> hasManifest = true
                    n.startsWith("META-INF/") && n.endsWith(".SF") -> hasSF = true
                    n.startsWith("META-INF/") && n.endsWith(".RSA") -> hasRSA = true
                }
                if (hasManifest && hasSF && hasRSA) return true
            }
        }
        return false
    }

}