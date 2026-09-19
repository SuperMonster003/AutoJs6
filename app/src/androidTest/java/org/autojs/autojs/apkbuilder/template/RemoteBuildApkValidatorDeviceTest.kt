package org.autojs.autojs.apkbuilder.template

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.apksig.ApkVerifier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Opt-in G5 qualification for host-side validation of a real remote-build APK. The driver must
 * first produce or push the known G1 directory fixture into the host external-files directory.
 */
@RunWith(AndroidJUnit4::class)
class RemoteBuildApkValidatorDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun resolvesExtensionlessCustomBksSignerOnAndroid() {
        requireQualificationEnvironment()

        val externalFiles = requireNotNull(context.getExternalFilesDir(null))
        val customKeyStore = File(externalFiles, CUSTOM_KEY_STORE_FILE_NAME)
        assertTrue("Missing externally supplied custom-signing fixture", customKeyStore.isFile)

        val defaultDigest = RemoteBuildExpectedSignerResolver.resolve(
            context = context,
            keyStoreFile = null,
            keyStorePassword = null,
            keyAlias = null,
        )
        val customDigest = RemoteBuildExpectedSignerResolver.resolve(
            context = context,
            keyStoreFile = customKeyStore,
            keyStorePassword = DEFAULT_TEST_CREDENTIAL,
            keyAlias = DEFAULT_TEST_CREDENTIAL,
        )

        assertEquals(defaultDigest, customDigest)
        Log.i(
            TAG,
            "M3_G5_CUSTOM_SIGNER_RESOLUTION api=${Build.VERSION.SDK_INT} " +
                "extensionless_transport=1 signer_match=1 packaged_private_keystore=0",
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun reportsArchiveSignerVisibilityWithoutExposingCertificates() {
        requireQualificationEnvironment()

        val externalFiles = requireNotNull(context.getExternalFilesDir(null))
        val validApk = File(externalFiles, VALID_APK_FILE_NAME)
        assertTrue("Missing real G1 APK fixture: ${validApk.path}", validApk.isFile)

        val cryptographicResult = ApkVerifier.Builder(validApk).build().verify()
        val modernInfo = context.packageManager.getPackageArchiveInfo(
            validApk.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val legacyInfo = context.packageManager.getPackageArchiveInfo(
            validApk.absolutePath,
            PackageManager.GET_SIGNATURES,
        )
        val modernSignerCount = modernInfo?.signingInfo?.apkContentsSigners?.size ?: 0
        val legacySignerCount = legacyInfo?.signatures?.size ?: 0
        val modernIdentityMatches = modernInfo?.packageName == EXPECTED_PACKAGE_NAME &&
            modernInfo.versionName == EXPECTED_VERSION_NAME &&
            modernInfo.longVersionCode == EXPECTED_VERSION_CODE
        val legacyIdentityMatches = legacyInfo?.packageName == EXPECTED_PACKAGE_NAME &&
            legacyInfo.versionName == EXPECTED_VERSION_NAME &&
            legacyInfo.longVersionCode == EXPECTED_VERSION_CODE

        Log.i(
            TAG,
            "M3_G2_ARCHIVE_SIGNER_VISIBILITY api=${Build.VERSION.SDK_INT} " +
                "apksig_verified=${if (cryptographicResult.isVerified) 1 else 0} " +
                "apksig_signers=${cryptographicResult.signerCertificates.size} " +
                "modern_parsed=${if (modernInfo != null) 1 else 0} " +
                "modern_signers=$modernSignerCount " +
                "modern_identity=${if (modernIdentityMatches) 1 else 0} " +
                "legacy_parsed=${if (legacyInfo != null) 1 else 0} " +
                "legacy_signers=$legacySignerCount " +
                "legacy_identity=${if (legacyIdentityMatches) 1 else 0}",
        )

        assertTrue("Cryptographic fixture verification failed", cryptographicResult.isVerified)
        assertTrue("Cryptographic fixture has no signer", cryptographicResult.signerCertificates.isNotEmpty())
        assertTrue("Modern archive parse lost package identity", modernIdentityMatches)
        assertTrue("Legacy archive parse lost package identity", legacyIdentityMatches)
    }

    @Test
    fun validatesRealOutputAndRejectsTamperingBeforePublicationOrInstall() {
        requireQualificationEnvironment()

        val externalFiles = requireNotNull(context.getExternalFilesDir(null))
        val validApk = File(externalFiles, VALID_APK_FILE_NAME)
        assertTrue("Missing real G1 APK fixture: ${validApk.path}", validApk.isFile)
        assertTrue("Real G1 APK fixture is unexpectedly small", validApk.length() > MIN_FIXTURE_BYTES)
        assertPackageAbsent(EXPECTED_PACKAGE_NAME)

        val root = File(context.cacheDir, WORKSPACE_NAME).apply {
            deleteRecursively()
            check(mkdirs())
        }
        try {
            val expectedSignerCertificateSha256 = RemoteBuildExpectedSignerResolver.resolve(
                context = context,
                keyStoreFile = null,
                keyStorePassword = null,
                keyAlias = null,
            )
            val expectedIdentity = RemoteBuildExpectedApkIdentity(
                packageName = EXPECTED_PACKAGE_NAME,
                versionName = EXPECTED_VERSION_NAME,
                versionCode = EXPECTED_VERSION_CODE,
                signerCertificateSha256 = expectedSignerCertificateSha256,
            )

            RemoteBuildApkValidator.validate(context, validApk, expectedIdentity)

            val validatedCopy = File(root, "validated-copy.apk").apply {
                writeBytes(PREVIOUS_OUTPUT_BYTES)
            }
            FileInputStream(validApk).use { input ->
                RemoteBuildOutputTransport.copyToFile(
                    input = input,
                    outputFile = validatedCopy,
                    declaredSizeBytes = validApk.length(),
                    expectedSha256 = sha256(validApk),
                    validateTemporaryFile = { temporary ->
                        RemoteBuildApkValidator.validate(context, temporary, expectedIdentity)
                    },
                )
            }
            assertEquals(validApk.length(), validatedCopy.length())
            assertEquals(sha256(validApk), sha256(validatedCopy))
            assertNoPublicationArtifacts(validatedCopy)

            val signatureTampered = File(root, "signature-tampered.apk")
            rewriteArchive(
                source = validApk,
                output = signatureTampered,
                mutateEntryName = PROJECT_SCRIPT_ENTRY,
            )
            RemoteBuildApkArchivePolicy.validate(signatureTampered)
            assertIOException {
                RemoteBuildApkValidator.validate(context, signatureTampered, expectedIdentity)
            }

            val structureTampered = File(root, "structure-tampered.apk")
            rewriteArchive(
                source = validApk,
                output = structureTampered,
                skipEntryName = "classes.dex",
            )
            assertIOException {
                RemoteBuildApkArchivePolicy.validate(structureTampered)
            }

            assertIOException {
                RemoteBuildApkValidator.validate(
                    context,
                    validApk,
                    expectedIdentity.copy(packageName = "$EXPECTED_PACKAGE_NAME.mismatch"),
                )
            }

            val rejectedOutput = File(root, "rejected-output.apk").apply {
                writeBytes(PREVIOUS_OUTPUT_BYTES)
            }
            assertIOException {
                FileInputStream(signatureTampered).use { input ->
                    RemoteBuildOutputTransport.copyToFile(
                        input = input,
                        outputFile = rejectedOutput,
                        declaredSizeBytes = signatureTampered.length(),
                        expectedSha256 = sha256(signatureTampered),
                        validateTemporaryFile = { temporary ->
                            RemoteBuildApkValidator.validate(context, temporary, expectedIdentity)
                        },
                    )
                }
            }
            assertArrayEquals(PREVIOUS_OUTPUT_BYTES, rejectedOutput.readBytes())
            assertNoPublicationArtifacts(rejectedOutput)

            val signerRejectedOutput = File(root, "signer-rejected-output.apk").apply {
                writeBytes(PREVIOUS_OUTPUT_BYTES)
            }
            assertIOException {
                FileInputStream(validApk).use { input ->
                    RemoteBuildOutputTransport.copyToFile(
                        input = input,
                        outputFile = signerRejectedOutput,
                        declaredSizeBytes = validApk.length(),
                        expectedSha256 = sha256(validApk),
                        validateTemporaryFile = { temporary ->
                            RemoteBuildApkValidator.validate(
                                context,
                                temporary,
                                expectedIdentity.copy(
                                    signerCertificateSha256 = MISMATCHED_SIGNER_SHA256,
                                ),
                            )
                        },
                    )
                }
            }
            assertArrayEquals(PREVIOUS_OUTPUT_BYTES, signerRejectedOutput.readBytes())
            assertNoPublicationArtifacts(signerRejectedOutput)
            assertPackageAbsent(EXPECTED_PACKAGE_NAME)

            Log.i(
                TAG,
                "M3_G5_OUTPUT_VALIDATION_RESULT accepted=1 signature_rejected=1 " +
                    "structure_rejected=1 identity_rejected=1 signer_identity_rejected=1 " +
                    "old_output_preserved=1 " +
                    "temporary_artifacts=0 install_attempts=0 package_installed=0",
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun rewriteArchive(
        source: File,
        output: File,
        mutateEntryName: String? = null,
        skipEntryName: String? = null,
    ) {
        var mutated = mutateEntryName == null
        var skipped = skipEntryName == null
        ZipInputStream(BufferedInputStream(FileInputStream(source))).use { input ->
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { archive ->
                val buffer = ByteArray(256 * 1_024)
                while (true) {
                    val sourceEntry = input.nextEntry ?: break
                    if (sourceEntry.name == skipEntryName) {
                        drain(input, buffer)
                        skipped = true
                        input.closeEntry()
                        continue
                    }

                    archive.putNextEntry(ZipEntry(sourceEntry.name))
                    if (sourceEntry.name == mutateEntryName) {
                        val bytes = input.readBytes()
                        check(bytes.isNotEmpty()) { "Mutation fixture entry is empty" }
                        bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
                        archive.write(bytes)
                        mutated = true
                    } else {
                        copy(input, archive, buffer)
                    }
                    archive.closeEntry()
                    input.closeEntry()
                }
            }
        }
        check(mutated) { "Mutation fixture entry was not found" }
        check(skipped) { "Skipped fixture entry was not found" }
    }

    private fun copy(
        input: ZipInputStream,
        output: ZipOutputStream,
        buffer: ByteArray,
    ) {
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            if (read > 0) output.write(buffer, 0, read)
        }
    }

    private fun drain(input: ZipInputStream, buffer: ByteArray) {
        while (input.read(buffer) >= 0) {
            // Drain the skipped entry so the stream can advance to the next local header.
        }
    }

    private fun assertNoPublicationArtifacts(output: File) {
        val prefix = ".${output.name}."
        assertFalse(
            output.parentFile?.listFiles().orEmpty().any { file ->
                file.name.startsWith(prefix) &&
                    (file.name.endsWith(".tmp") || file.name.endsWith(".publish-backup"))
            }
        )
    }

    private fun assertIOException(block: () -> Unit) {
        try {
            block()
            fail("Expected IOException")
        } catch (_: IOException) {
            // Expected rejection.
        }
    }

    private fun assertPackageAbsent(packageName: String) {
        val installed = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        assertFalse("Qualification package unexpectedly installed: $packageName", installed)
    }

    private fun requireQualificationEnvironment() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Output-validation qualification is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        val expectedSerial = arguments.getString(ARG_DEVICE_SERIAL).orEmpty().trim()
        assertTrue("Pass the exact device serial through -e $ARG_DEVICE_SERIAL", expectedSerial.isNotEmpty())
        val actualSerial = executeShellCommand("getprop ro.serialno").trim().ifEmpty {
            executeShellCommand("getprop ro.boot.serialno").trim()
        }
        assertEquals("Instrumentation is running on a different device", expectedSerial, actualSerial)
    }

    private fun executeShellCommand(command: String): String {
        return instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1_024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    companion object {
        const val ARG_ENABLED = "autojs.apkBuilder.outputValidation.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.apkBuilder.outputValidation.serial"

        private const val TAG = "RemoteApkOutputGate"
        private const val WORKSPACE_NAME = "m3-g5-output-validation"
        private const val VALID_APK_FILE_NAME = "m3-g1-directory-runtime-smoke.apk"
        private const val CUSTOM_KEY_STORE_FILE_NAME = "m3-g5-custom-signer-fixture.bin"
        private const val PROJECT_SCRIPT_ENTRY = "assets/project/main.js"
        private const val EXPECTED_PACKAGE_NAME = "org.autojs.autojs6.m3g1directorysmoke"
        private const val EXPECTED_VERSION_NAME = "1.0.1"
        private const val EXPECTED_VERSION_CODE = 1L
        private const val MIN_FIXTURE_BYTES = 1_000_000L
        private const val MISMATCHED_SIGNER_SHA256 =
            "0000000000000000000000000000000000000000000000000000000000000000"
        private const val DEFAULT_TEST_CREDENTIAL = "AutoJs6"
        private val PREVIOUS_OUTPUT_BYTES = "previous-output-must-survive".toByteArray()
    }
}
