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

package net.dongliu.apk.parser.cert.asn1.ber;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

/**
 * ASN.1 Basic Encoding Rules (BER) data value -- see {@code X.690}.
 */
public class BerDataValue {
    private final ByteBuffer encoded;
    private final ByteBuffer encodedContents;
    /**
     * Returns the tag class of this data value. See {@link BerEncoding} {@code TAG_CLASS}
     * constants.
     */
    public final int tagClass;
    /**
     * Returns {@code true} if the content octets of this data value are the complete BER encoding
     * of one or more data values, {@code false} if the content octets of this data value directly
     * represent the value.
     */
    public final boolean isConstructed;
    /**
     * Returns the tag number of this data value. See {@link BerEncoding} {@code TAG_NUMBER}
     * constants.
     */
    public final int tagNumber;

    BerDataValue(
            @NonNull final ByteBuffer encoded,
            @NonNull final ByteBuffer encodedContents,
            final int tagClass,
            final boolean constructed,
            final int tagNumber) {
        this.encoded = encoded;
        this.encodedContents = encodedContents;
        this.tagClass = tagClass;
        this.isConstructed = constructed;
        this.tagNumber = tagNumber;
    }

    /**
     * Returns the encoded form of this data value.
     */
    public ByteBuffer getEncoded() {
        return this.encoded.slice();
    }

    /**
     * Returns the encoded contents of this data value.
     */
    @NonNull
    public ByteBuffer getEncodedContents() {
        return this.encodedContents.slice();
    }

    /**
     * Returns a new reader of the contents of this data value.
     */
    @NonNull
    public BerDataValueReader contentsReader() {
        return new ByteBufferBerDataValueReader(this.getEncodedContents());
    }

    /**
     * Returns a new reader which returns just this data value. This may be useful for re-reading
     * this value in different contexts.
     */
    @NonNull
    public BerDataValueReader dataValueReader() {
        return new ParsedValueReader(this);
    }

    private static final class ParsedValueReader implements BerDataValueReader {
        private final BerDataValue mValue;
        private boolean mValueOutput;

        public ParsedValueReader(final BerDataValue value) {
            this.mValue = value;
        }

        @Nullable
        @Override
        public BerDataValue readDataValue() {
            if (this.mValueOutput) {
                return null;
            }
            this.mValueOutput = true;
            return this.mValue;
        }
    }
}
