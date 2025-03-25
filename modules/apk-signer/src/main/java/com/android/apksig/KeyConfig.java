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


import java.security.PrivateKey;
import java.util.function.Function;

/**
 * Represents the private key that will be used for signing, where that key could either be locally
 * accessible or exposed only via a Key Management Service (KMS).
 */
public abstract class KeyConfig {
    private KeyConfig() {}

    /** Helper function to perform some operation on a {@link KeyConfig} regardless of subtype. */
    public abstract <T> T match(Function<Jca, T> local, Function<Kms, T> kms);

    /**
     * For signing via Java Crypto Architecture (JCA). Simply wraps a {@link PrivateKey} that is
     * accessible locally.
     */
    public static class Jca extends KeyConfig {
        public final PrivateKey privateKey;

        @Override
        public <T> T match(Function<Jca, T> jca, Function<Kms, T> kms) {
            return jca.apply(this);
        }

        public Jca(PrivateKey privateKey) {
            this.privateKey = privateKey;
        }
    }

    /** For signing via a Key Management Service (KMS). */
    public static class Kms extends KeyConfig {
        public final String kmsType;
        public final String keyAlias;

        @Override
        public <T> T match(Function<Jca, T> jca, Function<Kms, T> kms) {
            return kms.apply(this);
        }

        public Kms(String kmsType, String keyAlias) {
            this.kmsType = kmsType;
            this.keyAlias = keyAlias;
        }
    }
}
