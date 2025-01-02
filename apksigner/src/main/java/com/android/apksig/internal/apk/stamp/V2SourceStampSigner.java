/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

import static com.android.apksig.internal.apk.ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V2;
import static com.android.apksig.internal.apk.ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V3;
import static com.android.apksig.internal.apk.ApkSigningBlockUtils.VERSION_JAR_SIGNATURE_SCHEME;
import static com.android.apksig.internal.apk.ApkSigningBlockUtils.encodeAsLengthPrefixedElement;
import static com.android.apksig.internal.apk.ApkSigningBlockUtils.encodeAsSequenceOfLengthPrefixedElements;
import static com.android.apksig.internal.apk.ApkSigningBlockUtils.encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes;

import com.android.apksig.internal.apk.ApkSigningBlockUtils;
import com.android.apksig.internal.apk.ApkSigningBlockUtils.SignerConfig;
import com.android.apksig.internal.apk.ContentDigestAlgorithm;
import com.android.apksig.internal.util.Pair;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SourceStamp signer.
 *
 * <p>SourceStamp improves traceability of apps with respect to unauthorized distribution.
 *
 * <p>The stamp is part of the APK that is protected by the signing block.
 *
 * <p>The APK contents hash is signed using the stamp key, and is saved as part of the signing
 * block.
 *
 * <p>V2 of the source stamp allows signing the digests of more than one signature schemes.
 */
public abstract class V2SourceStampSigner {

    public static final int V2_SOURCE_STAMP_BLOCK_ID = 0x6dff800d;

    /**
     * Hidden constructor to prevent instantiation.
     */
    private V2SourceStampSigner() {
    }

    public static Pair<byte[], Integer> generateSourceStampBlock(
            SignerConfig sourceStampSignerConfig,
            Map<Integer, Map<ContentDigestAlgorithm, byte[]>> signatureSchemeDigestInfos)
            throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        if (sourceStampSignerConfig.certificates.isEmpty()) {
            throw new SignatureException("No certificates configured for signer");
        }

        // Extract the digests for signature schemes.
        List<Pair<Integer, byte[]>> signatureSchemeDigests = new ArrayList<>();
        getSignedDigestsFor(
                VERSION_APK_SIGNATURE_SCHEME_V3,
                signatureSchemeDigestInfos,
                sourceStampSignerConfig,
                signatureSchemeDigests);
        getSignedDigestsFor(
                VERSION_APK_SIGNATURE_SCHEME_V2,
                signatureSchemeDigestInfos,
                sourceStampSignerConfig,
                signatureSchemeDigests);
        getSignedDigestsFor(
                VERSION_JAR_SIGNATURE_SCHEME,
                signatureSchemeDigestInfos,
                sourceStampSignerConfig,
                signatureSchemeDigests);
        Collections.sort(signatureSchemeDigests, (o1, o2) -> o1.getFirst().compareTo(o2.getFirst()));

        SourceStampBlock sourceStampBlock = new SourceStampBlock();

        try {
            sourceStampBlock.stampCertificate =
                    sourceStampSignerConfig.certificates.get(0).getEncoded();
        } catch (CertificateEncodingException e) {
            throw new SignatureException(
                    "Retrieving the encoded form of the stamp certificate failed", e);
        }

        sourceStampBlock.signedDigests = signatureSchemeDigests;

        // FORMAT:
        // * length-prefixed bytes: X.509 certificate (ASN.1 DER encoded)
        // * length-prefixed sequence of length-prefixed signed signature scheme digests:
        //   * uint32: signature scheme id
        //   * length-prefixed bytes: signed digests for the respective signature scheme
        byte[] sourceStampSignerBlock =
                encodeAsSequenceOfLengthPrefixedElements(
                        new byte[][]{
                                sourceStampBlock.stampCertificate,
                                encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes(
                                        sourceStampBlock.signedDigests),
                        });

        // FORMAT:
        // * length-prefixed stamp block.
        return Pair.of(
                encodeAsLengthPrefixedElement(sourceStampSignerBlock), V2_SOURCE_STAMP_BLOCK_ID);
    }

    private static void getSignedDigestsFor(
            int signatureSchemeVersion,
            Map<Integer, Map<ContentDigestAlgorithm, byte[]>> signatureSchemeDigestInfos,
            SignerConfig sourceStampSignerConfig,
            List<Pair<Integer, byte[]>> signatureSchemeDigests)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        if (!signatureSchemeDigestInfos.containsKey(signatureSchemeVersion)) {
            return;
        }

        Map<ContentDigestAlgorithm, byte[]> digestInfo =
                signatureSchemeDigestInfos.get(signatureSchemeVersion);
        List<Pair<Integer, byte[]>> digests = new ArrayList<>();
        for (Map.Entry<ContentDigestAlgorithm, byte[]> digest : digestInfo.entrySet()) {
            digests.add(Pair.of(digest.getKey().getId(), digest.getValue()));
        }
        Collections.sort(digests, (o1, o2) -> o1.getFirst().compareTo(o2.getFirst()));

        // FORMAT:
        // * length-prefixed sequence of length-prefixed digests:
        //   * uint32: digest algorithm id
        //   * length-prefixed bytes: digest of the respective digest algorithm
        byte[] digestBytes =
                encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes(digests);

        // FORMAT:
        // * length-prefixed sequence of length-prefixed signed digests:
        //   * uint32: signature algorithm id
        //   * length-prefixed bytes: signed digest for the respective signature algorithm
        List<Pair<Integer, byte[]>> signedDigest =
                ApkSigningBlockUtils.generateSignaturesOverData(
                        sourceStampSignerConfig, digestBytes);

        // FORMAT:
        // * length-prefixed sequence of length-prefixed signed signature scheme digests:
        //   * uint32: signature scheme id
        //   * length-prefixed bytes: signed digests for the respective signature scheme
        signatureSchemeDigests.add(
                Pair.of(
                        signatureSchemeVersion,
                        encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes(
                                signedDigest)));
    }

    private static final class SourceStampBlock {
        public byte[] stampCertificate;
        public List<Pair<Integer, byte[]>> signedDigests;
    }
}
