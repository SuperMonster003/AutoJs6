/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.apksig.internal.apk.stamp;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.internal.apk.ApkSigningBlockUtils;
import com.android.apksig.internal.apk.SignatureAlgorithm;
import com.android.apksig.internal.util.GuaranteedEncodedFormX509Certificate;
import com.android.apksig.internal.util.X509CertificateUtils;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Source Stamp verifier.
 *
 * <p>SourceStamp improves traceability of apps with respect to unauthorized distribution.
 *
 * <p>The stamp is part of the APK that is protected by the signing block.
 *
 * <p>The APK contents hash is signed using the stamp key, and is saved as part of the signing
 * block.
 */
class SourceStampVerifier {
    /**
     * Hidden constructor to prevent instantiation.
     */
    private SourceStampVerifier() {
    }

    /**
     * Parses the SourceStamp block and populates the {@code result}.
     *
     * <p>This verifies signatures over digest provided.
     *
     * <p>This method adds one or more errors to the {@code result} if a verification error is
     * expected to be encountered on an Android platform version in the {@code [minSdkVersion,
     * maxSdkVersion]} range.
     */
    public static void verifyV1SourceStamp(
            ByteBuffer sourceStampBlockData,
            CertificateFactory certFactory,
            ApkSigningBlockUtils.Result.SignerInfo result,
            byte[] apkDigest,
            byte[] sourceStampCertificateDigest,
            int minSdkVersion,
            int maxSdkVersion)
            throws ApkFormatException, NoSuchAlgorithmException {
        X509Certificate sourceStampCertificate =
                verifySourceStampCertificate(
                        sourceStampBlockData, certFactory, sourceStampCertificateDigest, result);
        if (result.containsWarnings() || result.containsErrors()) {
            return;
        }

        verifySourceStampSignature(
                apkDigest,
                minSdkVersion,
                maxSdkVersion,
                sourceStampCertificate,
                sourceStampBlockData,
                result);
    }

    /**
     * Parses the SourceStamp block and populates the {@code result}.
     *
     * <p>This verifies signatures over digest of multiple signature schemes provided.
     *
     * <p>This method adds one or more errors to the {@code result} if a verification error is
     * expected to be encountered on an Android platform version in the {@code [minSdkVersion,
     * maxSdkVersion]} range.
     */
    public static void verifyV2SourceStamp(
            ByteBuffer sourceStampBlockData,
            CertificateFactory certFactory,
            ApkSigningBlockUtils.Result.SignerInfo result,
            Map<Integer, byte[]> signatureSchemeApkDigests,
            byte[] sourceStampCertificateDigest,
            int minSdkVersion,
            int maxSdkVersion)
            throws ApkFormatException, NoSuchAlgorithmException {
        X509Certificate sourceStampCertificate =
                verifySourceStampCertificate(
                        sourceStampBlockData, certFactory, sourceStampCertificateDigest, result);
        if (result.containsWarnings() || result.containsErrors()) {
            return;
        }

        // Parse signed signature schemes block.
        ByteBuffer signedSignatureSchemes =
                ApkSigningBlockUtils.getLengthPrefixedSlice(sourceStampBlockData);
        Map<Integer, ByteBuffer> signedSignatureSchemeData = new HashMap<>();
        while (signedSignatureSchemes.hasRemaining()) {
            ByteBuffer signedSignatureScheme =
                    ApkSigningBlockUtils.getLengthPrefixedSlice(signedSignatureSchemes);
            int signatureSchemeId = signedSignatureScheme.getInt();
            signedSignatureSchemeData.put(signatureSchemeId, signedSignatureScheme);
        }

        for (Map.Entry<Integer, byte[]> signatureSchemeApkDigest :
                signatureSchemeApkDigests.entrySet()) {
            if (!signedSignatureSchemeData.containsKey(signatureSchemeApkDigest.getKey())) {
                result.addWarning(ApkVerifier.Issue.SOURCE_STAMP_NO_SIGNATURE);
                return;
            }
            verifySourceStampSignature(
                    signatureSchemeApkDigest.getValue(),
                    minSdkVersion,
                    maxSdkVersion,
                    sourceStampCertificate,
                    signedSignatureSchemeData.get(signatureSchemeApkDigest.getKey()),
                    result);
            if (result.containsWarnings() || result.containsWarnings()) {
                return;
            }
        }
    }

    private static X509Certificate verifySourceStampCertificate(
            ByteBuffer sourceStampBlockData,
            CertificateFactory certFactory,
            byte[] sourceStampCertificateDigest,
            ApkSigningBlockUtils.Result.SignerInfo result)
            throws NoSuchAlgorithmException, ApkFormatException {
        // Parse the SourceStamp certificate.
        byte[] sourceStampEncodedCertificate =
                ApkSigningBlockUtils.readLengthPrefixedByteArray(sourceStampBlockData);
        X509Certificate sourceStampCertificate;
        try {
            sourceStampCertificate =
                    X509CertificateUtils.generateCertificate(
                            sourceStampEncodedCertificate, certFactory);
        } catch (CertificateException e) {
            result.addWarning(ApkVerifier.Issue.SOURCE_STAMP_MALFORMED_CERTIFICATE, e);
            return null;
        }
        // Wrap the cert so that the result's getEncoded returns exactly the original encoded
        // form. Without this, getEncoded may return a different form from what was stored in
        // the signature. This is because some X509Certificate(Factory) implementations
        // re-encode certificates.
        sourceStampCertificate =
                new GuaranteedEncodedFormX509Certificate(
                        sourceStampCertificate, sourceStampEncodedCertificate);
        result.certs.add(sourceStampCertificate);
        // Verify the SourceStamp certificate found in the signing block is the same as the
        // SourceStamp certificate found in the APK.
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(sourceStampEncodedCertificate);
        byte[] sourceStampBlockCertificateDigest = messageDigest.digest();
        if (!Arrays.equals(sourceStampCertificateDigest, sourceStampBlockCertificateDigest)) {
            result.addWarning(
                    ApkVerifier.Issue
                            .SOURCE_STAMP_CERTIFICATE_MISMATCH_BETWEEN_SIGNATURE_BLOCK_AND_APK,
                    ApkSigningBlockUtils.toHex(sourceStampBlockCertificateDigest),
                    ApkSigningBlockUtils.toHex(sourceStampCertificateDigest));
            return null;
        }
        return sourceStampCertificate;
    }

    private static void verifySourceStampSignature(
            byte[] apkDigest,
            int minSdkVersion,
            int maxSdkVersion,
            X509Certificate sourceStampCertificate,
            ByteBuffer signedData,
            ApkSigningBlockUtils.Result.SignerInfo result)
            throws ApkFormatException {
        // Parse the signatures block and identify supported signatures
        ByteBuffer signatures = ApkSigningBlockUtils.getLengthPrefixedSlice(signedData);
        int signatureCount = 0;
        List<ApkSigningBlockUtils.SupportedSignature> supportedSignatures = new ArrayList<>(1);
        while (signatures.hasRemaining()) {
            signatureCount++;
            try {
                ByteBuffer signature = ApkSigningBlockUtils.getLengthPrefixedSlice(signatures);
                int sigAlgorithmId = signature.getInt();
                byte[] sigBytes = ApkSigningBlockUtils.readLengthPrefixedByteArray(signature);
                SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.findById(sigAlgorithmId);
                if (signatureAlgorithm == null) {
                    result.addWarning(
                            ApkVerifier.Issue.SOURCE_STAMP_UNKNOWN_SIG_ALGORITHM, sigAlgorithmId);
                    continue;
                }
                supportedSignatures.add(
                        new ApkSigningBlockUtils.SupportedSignature(signatureAlgorithm, sigBytes));
            } catch (ApkFormatException | BufferUnderflowException e) {
                result.addWarning(
                        ApkVerifier.Issue.SOURCE_STAMP_MALFORMED_SIGNATURE, signatureCount);
                return;
            }
        }
        if (supportedSignatures.isEmpty()) {
            result.addWarning(ApkVerifier.Issue.SOURCE_STAMP_NO_SIGNATURE);
            return;
        }
        // Verify signatures over digests using the SourceStamp's certificate.
        List<ApkSigningBlockUtils.SupportedSignature> signaturesToVerify;
        try {
            signaturesToVerify =
                    ApkSigningBlockUtils.getSignaturesToVerify(
                            supportedSignatures, minSdkVersion, maxSdkVersion);
        } catch (ApkSigningBlockUtils.NoSupportedSignaturesException e) {
            result.addWarning(ApkVerifier.Issue.SOURCE_STAMP_NO_SUPPORTED_SIGNATURE);
            return;
        }
        for (ApkSigningBlockUtils.SupportedSignature signature : signaturesToVerify) {
            SignatureAlgorithm signatureAlgorithm = signature.algorithm;
            String jcaSignatureAlgorithm =
                    signatureAlgorithm.getJcaSignatureAlgorithmAndParams().getFirst();
            AlgorithmParameterSpec jcaSignatureAlgorithmParams =
                    signatureAlgorithm.getJcaSignatureAlgorithmAndParams().getSecond();
            PublicKey publicKey = sourceStampCertificate.getPublicKey();
            try {
                Signature sig = Signature.getInstance(jcaSignatureAlgorithm);
                sig.initVerify(publicKey);
                if (jcaSignatureAlgorithmParams != null) {
                    sig.setParameter(jcaSignatureAlgorithmParams);
                }
                sig.update(apkDigest);
                byte[] sigBytes = signature.signature;
                if (!sig.verify(sigBytes)) {
                    result.addWarning(
                            ApkVerifier.Issue.SOURCE_STAMP_DID_NOT_VERIFY, signatureAlgorithm);
                    return;
                }
            } catch (InvalidKeyException
                    | InvalidAlgorithmParameterException
                    | SignatureException
                    | NoSuchAlgorithmException e) {
                result.addWarning(
                        ApkVerifier.Issue.SOURCE_STAMP_VERIFY_EXCEPTION, signatureAlgorithm, e);
                return;
            }
        }
    }
}
