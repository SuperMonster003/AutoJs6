/*
 * Copyright (C) 2017 The Android Open Source Project
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

package net.dongliu.apk.parser.cert.asn1;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

/**
 * Opaque holder of encoded ASN.1 stuff.
 */
public class Asn1OpaqueObject {
    private final ByteBuffer encoded;

    public Asn1OpaqueObject(final @NonNull ByteBuffer encoded) {
        this.encoded = encoded.slice();
    }

    public Asn1OpaqueObject(final @NonNull byte[] encoded) {
        this.encoded = ByteBuffer.wrap(encoded);
    }

    @NonNull
    public ByteBuffer getEncoded() {
        return this.encoded.slice();
    }
}
