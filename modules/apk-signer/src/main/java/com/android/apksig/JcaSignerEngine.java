/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.apksig;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.AlgorithmParameterSpec;

/** Cryptographic signing using standard Java Crypto Architecture (JCA) */
public class JcaSignerEngine implements SignerEngine {
    private final PrivateKey mPrivateKey;
    private final String mSignatureAlgorithm;
    private final AlgorithmParameterSpec mAlgorithmParameterSpec;

    public JcaSignerEngine(
            PrivateKey privateKey,
            String signatureAlgorithm,
            AlgorithmParameterSpec algorithmParameterSpec) {
        if (privateKey == null) {
            throw new IllegalArgumentException("privateKey cannot be null");
        }
        if (signatureAlgorithm == null) {
            throw new IllegalArgumentException("signatureAlgorithm cannot be null");
        }
        mPrivateKey = privateKey;
        mSignatureAlgorithm = signatureAlgorithm;
        mAlgorithmParameterSpec = algorithmParameterSpec;
    }

    @Override
    public byte[] sign(byte[] data)
            throws InvalidKeyException, NoSuchAlgorithmException,
                    InvalidAlgorithmParameterException, SignatureException {
        Signature signature = Signature.getInstance(mSignatureAlgorithm);
        signature.initSign(mPrivateKey);
        if (mAlgorithmParameterSpec != null) {
            signature.setParameter(mAlgorithmParameterSpec);
        }
        signature.update(data);
        return signature.sign();
    }
}
