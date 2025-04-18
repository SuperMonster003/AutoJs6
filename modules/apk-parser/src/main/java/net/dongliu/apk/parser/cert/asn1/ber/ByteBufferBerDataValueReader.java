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
 * {@link BerDataValueReader} which reads from a {@link ByteBuffer} containing BER-encoded data
 * values. See {@code X.690} for the encoding.
 */
public class ByteBufferBerDataValueReader implements BerDataValueReader {
    private final ByteBuffer mBuf;

    public ByteBufferBerDataValueReader(final @NonNull ByteBuffer buf) {
        this.mBuf = buf;
    }

    @Nullable
    @Override
    public BerDataValue readDataValue() throws BerDataValueFormatException {
        final int startPosition = this.mBuf.position();
        if (!this.mBuf.hasRemaining()) {
            return null;
        }
        final byte firstIdentifierByte = this.mBuf.get();
        final int tagNumber = this.readTagNumber(firstIdentifierByte);
        final boolean constructed = BerEncoding.isConstructed(firstIdentifierByte);
        if (!this.mBuf.hasRemaining()) {
            throw new BerDataValueFormatException("Missing length");
        }
        final int firstLengthByte = this.mBuf.get() & 0xff;
        final int contentsLength;
        final int contentsOffsetInTag;
        if ((firstLengthByte & 0x80) == 0) {
            // short form length
            contentsLength = this.readShortFormLength(firstLengthByte);
            contentsOffsetInTag = this.mBuf.position() - startPosition;
            this.skipDefiniteLengthContents(contentsLength);
        } else if (firstLengthByte != 0x80) {
            // long form length
            contentsLength = this.readLongFormLength(firstLengthByte);
            contentsOffsetInTag = this.mBuf.position() - startPosition;
            this.skipDefiniteLengthContents(contentsLength);
        } else {
            // indefinite length -- value ends with 0x00 0x00
            contentsOffsetInTag = this.mBuf.position() - startPosition;
            contentsLength =
                    constructed
                            ? this.skipConstructedIndefiniteLengthContents()
                            : this.skipPrimitiveIndefiniteLengthContents();
        }
        // Create the encoded data value ByteBuffer
        final int endPosition = this.mBuf.position();
        this.mBuf.position(startPosition);
        final int bufOriginalLimit = this.mBuf.limit();
        this.mBuf.limit(endPosition);
        final ByteBuffer encoded = this.mBuf.slice();
        this.mBuf.position(this.mBuf.limit());
        this.mBuf.limit(bufOriginalLimit);
        // Create the encoded contents ByteBuffer
        encoded.position(contentsOffsetInTag);
        encoded.limit(contentsOffsetInTag + contentsLength);
        final ByteBuffer encodedContents = encoded.slice();
        encoded.clear();
        return new BerDataValue(
                encoded,
                encodedContents,
                BerEncoding.getTagClass(firstIdentifierByte),
                constructed,
                tagNumber);
    }

    private int readTagNumber(final byte firstIdentifierByte) throws BerDataValueFormatException {
        final int tagNumber = BerEncoding.getTagNumber(firstIdentifierByte);
        if (tagNumber == 0x1f) {
            // high-tag-number form, where the tag number follows this byte in base-128
            // big-endian form, where each byte has the highest bit set, except for the last
            // byte
            return this.readHighTagNumber();
        } else {
            // low-tag-number form
            return tagNumber;
        }
    }

    private int readHighTagNumber() throws BerDataValueFormatException {
        // Base-128 big-endian form, where each byte has the highest bit set, except for the last
        // byte
        int b;
        int result = 0;
        do {
            if (!this.mBuf.hasRemaining()) {
                throw new BerDataValueFormatException("Truncated tag number");
            }
            b = this.mBuf.get();
            if (result > Integer.MAX_VALUE >>> 7) {
                throw new BerDataValueFormatException("Tag number too large");
            }
            result <<= 7;
            result |= b & 0x7f;
        } while ((b & 0x80) != 0);
        return result;
    }

    private int readShortFormLength(final int firstLengthByte) {
        return firstLengthByte & 0x7f;
    }

    private int readLongFormLength(final int firstLengthByte) throws BerDataValueFormatException {
        // The low 7 bits of the first byte represent the number of bytes (following the first
        // byte) in which the length is in big-endian base-256 form
        final int byteCount = firstLengthByte & 0x7f;
        if (byteCount > 4) {
            throw new BerDataValueFormatException("Length too large: " + byteCount + " bytes");
        }
        int result = 0;
        for (int i = 0; i < byteCount; i++) {
            if (!this.mBuf.hasRemaining()) {
                throw new BerDataValueFormatException("Truncated length");
            }
            final int b = this.mBuf.get();
            if (result > Integer.MAX_VALUE >>> 8) {
                throw new BerDataValueFormatException("Length too large");
            }
            result <<= 8;
            result |= b & 0xff;
        }
        return result;
    }

    private void skipDefiniteLengthContents(final int contentsLength) throws BerDataValueFormatException {
        if (this.mBuf.remaining() < contentsLength) {
            throw new BerDataValueFormatException(
                    "Truncated contents. Need: " + contentsLength + " bytes, available: "
                            + this.mBuf.remaining());
        }
        this.mBuf.position(this.mBuf.position() + contentsLength);
    }

    private int skipPrimitiveIndefiniteLengthContents() throws BerDataValueFormatException {
        // Contents are terminated by 0x00 0x00
        boolean prevZeroByte = false;
        int bytesRead = 0;
        while (true) {
            if (!this.mBuf.hasRemaining()) {
                throw new BerDataValueFormatException(
                        "Truncated indefinite-length contents: " + bytesRead + " bytes read");

            }
            final int b = this.mBuf.get();
            bytesRead++;
            if (bytesRead < 0) {
                throw new BerDataValueFormatException("Indefinite-length contents too long");
            }
            if (b == 0) {
                if (prevZeroByte) {
                    // End of contents reached -- we've read the value and its terminator 0x00 0x00
                    return bytesRead - 2;
                }
                prevZeroByte = true;
            } else {
                prevZeroByte = false;
            }
        }
    }

    private int skipConstructedIndefiniteLengthContents() throws BerDataValueFormatException {
        // Contents are terminated by 0x00 0x00. However, this data value is constructed, meaning it
        // can contain data values which are themselves indefinite length encoded. As a result, we
        // must parse the direct children of this data value to correctly skip over the contents of
        // this data value.
        final int startPos = this.mBuf.position();
        while (this.mBuf.hasRemaining()) {
            // Check whether the 0x00 0x00 terminator is at current position
            if ((this.mBuf.remaining() > 1) && (this.mBuf.getShort(this.mBuf.position()) == 0)) {
                final int contentsLength = this.mBuf.position() - startPos;
                this.mBuf.position(this.mBuf.position() + 2);
                return contentsLength;
            }
            // No luck. This must be a BER-encoded data value -- skip over it by parsing it
            this.readDataValue();
        }
        throw new BerDataValueFormatException(
                "Truncated indefinite-length contents: "
                        + (this.mBuf.position() - startPos) + " bytes read");
    }
}
