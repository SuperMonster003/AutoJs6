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
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.cert.asn1.ber.BerEncoding;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Encoder of ASN.1 structures into DER-encoded form.
 * <p>
 * <p>Structure is described to the encoder by providing a class annotated with {@link Asn1Class},
 * containing fields annotated with {@link Asn1Field}.
 */
public final class Asn1DerEncoder {
    private Asn1DerEncoder() {
    }

    /**
     * Returns the DER-encoded form of the provided ASN.1 structure.
     *
     * @param container container to be encoded. The container's class must meet the following
     *                  requirements:
     *                  <ul>
     *                  <li>The class must be annotated with {@link Asn1Class}.</li>
     *                  <li>Member fields of the class which are to be encoded must be annotated with
     *                  {@link Asn1Field} and be public.</li>
     *                  </ul>
     * @throws Asn1EncodingException if the input could not be encoded
     */
    @Nullable
    public static byte[] encode(final @NonNull Object container) throws Asn1EncodingException {
        final Class<?> containerClass = container.getClass();
        final Asn1Class containerAnnotation = containerClass.getAnnotation(Asn1Class.class);
        if (containerAnnotation == null) {
            throw new Asn1EncodingException(
                    containerClass.getName() + " not annotated with " + Asn1Class.class.getName());
        }
        final Asn1Type containerType = containerAnnotation.type();
        switch (containerType) {
            case Choice:
                return Asn1DerEncoder.toChoice(container);
            case Sequence:
                return Asn1DerEncoder.toSequence(container);
            default:
                throw new Asn1EncodingException("Unsupported container type: " + containerType);
        }
    }

    @Nullable
    private static byte[] toChoice(@NonNull final Object container) throws Asn1EncodingException {
        final Class<?> containerClass = container.getClass();
        final List<AnnotatedField> fields = Asn1DerEncoder.getAnnotatedFields(container);
        if (fields.isEmpty()) {
            throw new Asn1EncodingException(
                    "No fields annotated with " + Asn1Field.class.getName()
                            + " in CHOICE class " + containerClass.getName());
        }
        AnnotatedField resultField = null;
        for (final AnnotatedField field : fields) {
            final Object fieldValue = Asn1DerEncoder.getMemberFieldValue(container, field.field);
            if (fieldValue != null) {
                if (resultField != null) {
                    throw new Asn1EncodingException(
                            "Multiple non-null fields in CHOICE class " + containerClass.getName()
                                    + ": " + resultField.field.getName()
                                    + ", " + field.field.getName());
                }
                resultField = field;
            }
        }
        if (resultField == null) {
            throw new Asn1EncodingException(
                    "No non-null fields in CHOICE class " + containerClass.getName());
        }
        return resultField.toDer();
    }

    private static byte[] toSequence(final Object container) throws Asn1EncodingException {
        final Class<?> containerClass = container.getClass();
        final List<AnnotatedField> fields = Asn1DerEncoder.getAnnotatedFields(container);
        Collections.sort(
                fields, Comparator.comparingInt(f -> f.annotation.index()));
        if (fields.size() > 1) {
            AnnotatedField lastField = null;
            for (final AnnotatedField field : fields) {
                if ((lastField != null)
                        && (lastField.annotation.index() == field.annotation.index())) {
                    throw new Asn1EncodingException(
                            "Fields have the same index: " + containerClass.getName()
                                    + "." + lastField.field.getName()
                                    + " and ." + field.field.getName());
                }
                lastField = field;
            }
        }
        final List<byte[]> serializedFields = new ArrayList<>(fields.size());
        for (final AnnotatedField field : fields) {
            final byte[] serializedField;
            try {
                serializedField = field.toDer();
            } catch (final Asn1EncodingException e) {
                throw new Asn1EncodingException(
                        "Failed to encode " + containerClass.getName()
                                + "." + field.field.getName(),
                        e);
            }
            if (serializedField != null) {
                serializedFields.add(serializedField);
            }
        }
        return Asn1DerEncoder.createTag(
                BerEncoding.TAG_CLASS_UNIVERSAL, true, BerEncoding.TAG_NUMBER_SEQUENCE,
                serializedFields.toArray(new byte[0][]));
    }

    private static byte[] toSetOf(final Collection<?> values, final Asn1Type elementType)
            throws Asn1EncodingException {
        final List<byte[]> serializedValues = new ArrayList<>(values.size());
        for (final Object value : values) {
            serializedValues.add(JavaToDerConverter.toDer(value, elementType, null));
        }
        if (serializedValues.size() > 1) {
            Collections.sort(serializedValues, ByteArrayLexicographicComparator.INSTANCE);
        }
        return Asn1DerEncoder.createTag(
                BerEncoding.TAG_CLASS_UNIVERSAL, true, BerEncoding.TAG_NUMBER_SET,
                serializedValues.toArray(new byte[0][]));
    }

    /**
     * Compares two bytes arrays based on their lexicographic order. Corresponding elements of the
     * two arrays are compared in ascending order. Elements at out of range indices are assumed to
     * be smaller than the smallest possible value for an element.
     */
    private static class ByteArrayLexicographicComparator implements Comparator<byte[]> {
        private static final ByteArrayLexicographicComparator INSTANCE =
                new ByteArrayLexicographicComparator();

        @Override
        public int compare(final byte[] arr1, final byte[] arr2) {
            final int commonLength = Math.min(arr1.length, arr2.length);
            for (int i = 0; i < commonLength; i++) {
                final int diff = (arr1[i] & 0xff) - (arr2[i] & 0xff);
                if (diff != 0) {
                    return diff;
                }
            }
            return arr1.length - arr2.length;
        }
    }

    private static List<AnnotatedField> getAnnotatedFields(@NonNull final Object container)
            throws Asn1EncodingException {
        final Class<?> containerClass = container.getClass();
        final Field[] declaredFields = containerClass.getDeclaredFields();
        final List<AnnotatedField> result = new ArrayList<>(declaredFields.length);
        for (final Field field : declaredFields) {
            final Asn1Field annotation = field.getAnnotation(Asn1Field.class);
            if (annotation == null) {
                continue;
            }
            if (Modifier.isStatic(field.getModifiers())) {
                throw new Asn1EncodingException(
                        Asn1Field.class.getName() + " used on a static field: "
                                + containerClass.getName() + "." + field.getName());
            }
            final AnnotatedField annotatedField;
            try {
                annotatedField = new AnnotatedField(container, field, annotation);
            } catch (final Asn1EncodingException e) {
                throw new Asn1EncodingException(
                        "Invalid ASN.1 annotation on "
                                + containerClass.getName() + "." + field.getName(),
                        e);
            }
            result.add(annotatedField);
        }
        return result;
    }

    private static byte[] toInteger(final int value) {
        return Asn1DerEncoder.toInteger((long) value);
    }

    private static byte[] toInteger(final long value) {
        return Asn1DerEncoder.toInteger(BigInteger.valueOf(value));
    }

    private static byte[] toInteger(final BigInteger value) {
        return Asn1DerEncoder.createTag(
                BerEncoding.TAG_CLASS_UNIVERSAL, false, BerEncoding.TAG_NUMBER_INTEGER,
                value.toByteArray());
    }

    private static byte[] toOid(final String oid) throws Asn1EncodingException {
        final ByteArrayOutputStream encodedValue = new ByteArrayOutputStream();
        final String[] nodes = oid.split("\\.");
        if (nodes.length < 2) {
            throw new Asn1EncodingException(
                    "OBJECT IDENTIFIER must contain at least two nodes: " + oid);
        }
        final int firstNode;
        try {
            firstNode = Integer.parseInt(nodes[0]);
        } catch (final NumberFormatException e) {
            throw new Asn1EncodingException("Node #1 not numeric: " + nodes[0]);
        }
        if ((firstNode > 6) || (firstNode < 0)) {
            throw new Asn1EncodingException("Invalid value for node #1: " + firstNode);
        }
        final int secondNode;
        try {
            secondNode = Integer.parseInt(nodes[1]);
        } catch (final NumberFormatException e) {
            throw new Asn1EncodingException("Node #2 not numeric: " + nodes[1]);
        }
        if ((secondNode >= 40) || (secondNode < 0)) {
            throw new Asn1EncodingException("Invalid value for node #2: " + secondNode);
        }
        final int firstByte = firstNode * 40 + secondNode;
        if (firstByte > 0xff) {
            throw new Asn1EncodingException(
                    "First two nodes out of range: " + firstNode + "." + secondNode);
        }
        encodedValue.write(firstByte);
        for (int i = 2; i < nodes.length; i++) {
            final String nodeString = nodes[i];
            final int node;
            try {
                node = Integer.parseInt(nodeString);
            } catch (final NumberFormatException e) {
                throw new Asn1EncodingException("Node #" + (i + 1) + " not numeric: " + nodeString);
            }
            if (node < 0) {
                throw new Asn1EncodingException("Invalid value for node #" + (i + 1) + ": " + node);
            }
            if (node <= 0x7f) {
                encodedValue.write(node);
                continue;
            }
            if (node < 1 << 14) {
                encodedValue.write(0x80 | (node >> 7));
                encodedValue.write(node & 0x7f);
                continue;
            }
            if (node < 1 << 21) {
                encodedValue.write(0x80 | (node >> 14));
                encodedValue.write(0x80 | ((node >> 7) & 0x7f));
                encodedValue.write(node & 0x7f);
                continue;
            }
            throw new Asn1EncodingException("Node #" + (i + 1) + " too large: " + node);
        }
        return Asn1DerEncoder.createTag(
                BerEncoding.TAG_CLASS_UNIVERSAL, false, BerEncoding.TAG_NUMBER_OBJECT_IDENTIFIER,
                encodedValue.toByteArray());
    }

    private static Object getMemberFieldValue(final Object obj, final Field field)
            throws Asn1EncodingException {
        try {
            return field.get(obj);
        } catch (final ReflectiveOperationException e) {
            throw new Asn1EncodingException(
                    "Failed to read " + obj.getClass().getName() + "." + field.getName(), e);
        }
    }

    private static final class AnnotatedField {
        public final Field field;
        private final Object mObject;
        public final Asn1Field annotation;
        private final Asn1Type mDataType;
        private final Asn1Type mElementDataType;
        private final int mDerTagClass;
        private final int mDerTagNumber;
        private final Asn1Tagging mTagging;
        private final boolean mOptional;

        public AnnotatedField(@NonNull final Object obj, @NonNull final Field field, @NonNull final Asn1Field annotation)
                throws Asn1EncodingException {
            this.mObject = obj;
            this.field = field;
            this.annotation = annotation;
            this.mDataType = annotation.type();
            this.mElementDataType = annotation.elementType();
            Asn1TagClass tagClass = annotation.cls();
            if (tagClass == Asn1TagClass.Automatic) {
                if (annotation.tagNumber() != -1) {
                    tagClass = Asn1TagClass.ContextSpecific;
                } else {
                    tagClass = Asn1TagClass.Universal;
                }
            }
            this.mDerTagClass = BerEncoding.getTagClass(tagClass);
            final int tagNumber;
            if (annotation.tagNumber() != -1) {
                tagNumber = annotation.tagNumber();
            } else if ((this.mDataType == Asn1Type.Choice) || (this.mDataType == Asn1Type.Any)) {
                tagNumber = -1;
            } else {
                tagNumber = BerEncoding.getTagNumber(this.mDataType);
            }
            this.mDerTagNumber = tagNumber;
            this.mTagging = annotation.tagging();
            if (((this.mTagging == Asn1Tagging.Explicit) || (this.mTagging == Asn1Tagging.Implicit))
                    && (annotation.tagNumber() == -1)) {
                throw new Asn1EncodingException(
                        "Tag number must be specified when tagging mode is " + this.mTagging);
            }
            this.mOptional = annotation.optional();
        }

        @Nullable
        public byte[] toDer() throws Asn1EncodingException {
            final Object fieldValue = Asn1DerEncoder.getMemberFieldValue(this.mObject, this.field);
            if (fieldValue == null) {
                if (this.mOptional) {
                    return null;
                }
                throw new Asn1EncodingException("Required field not set");
            }
            final byte[] encoded = JavaToDerConverter.toDer(fieldValue, this.mDataType, this.mElementDataType);
            switch (this.mTagging) {
                case Normal:
                    return encoded;
                case Explicit:
                    return Asn1DerEncoder.createTag(this.mDerTagClass, true, this.mDerTagNumber, encoded);
                case Implicit:
                    final int originalTagNumber = BerEncoding.getTagNumber(encoded[0]);
                    if (originalTagNumber == 0x1f) {
                        throw new Asn1EncodingException("High-tag-number form not supported");
                    }
                    if (this.mDerTagNumber >= 0x1f) {
                        throw new Asn1EncodingException(
                                "Unsupported high tag number: " + this.mDerTagNumber);
                    }
                    encoded[0] = BerEncoding.setTagNumber(encoded[0], this.mDerTagNumber);
                    encoded[0] = BerEncoding.setTagClass(encoded[0], this.mDerTagClass);
                    return encoded;
                default:
                    throw new RuntimeException("Unknown tagging mode: " + this.mTagging);
            }
        }
    }

    private static byte[] createTag(
            final int tagClass, final boolean constructed, final int tagNumber, final byte[]... contents) {
        if (tagNumber >= 0x1f) {
            throw new IllegalArgumentException("High tag numbers not supported: " + tagNumber);
        }
        // tag class & number fit into the first byte
        final byte firstIdentifierByte =
                (byte) ((tagClass << 6) | (constructed ? 1 << 5 : 0) | tagNumber);
        int contentsLength = 0;
        for (final byte[] c : contents) {
            contentsLength += c.length;
        }
        int contentsPosInResult;
        final byte[] result;
        if (contentsLength < 0x80) {
            // Length fits into one byte
            contentsPosInResult = 2;
            result = new byte[contentsPosInResult + contentsLength];
            result[0] = firstIdentifierByte;
            result[1] = (byte) contentsLength;
        } else {
            // Length is represented as multiple bytes
            // The low 7 bits of the first byte represent the number of length bytes (following the
            // first byte) in which the length is in big-endian base-256 form
            if (contentsLength <= 0xff) {
                contentsPosInResult = 3;
                result = new byte[contentsPosInResult + contentsLength];
                result[1] = (byte) 0x81;
                // 1 length byte
                result[2] = (byte) contentsLength;
            } else if (contentsLength <= 0xffff) {
                contentsPosInResult = 4;
                result = new byte[contentsPosInResult + contentsLength];
                result[1] = (byte) 0x82;
                // 2 length bytes
                result[2] = (byte) (contentsLength >> 8);
                result[3] = (byte) (contentsLength & 0xff);
            } else if (contentsLength <= 0xffffff) {
                contentsPosInResult = 5;
                result = new byte[contentsPosInResult + contentsLength];
                result[1] = (byte) 0x83;
                // 3 length bytes
                result[2] = (byte) (contentsLength >> 16);
                result[3] = (byte) ((contentsLength >> 8) & 0xff);
                result[4] = (byte) (contentsLength & 0xff);
            } else {
                contentsPosInResult = 6;
                result = new byte[contentsPosInResult + contentsLength];
                result[1] = (byte) 0x84;
                // 4 length bytes
                result[2] = (byte) (contentsLength >> 24);
                result[3] = (byte) ((contentsLength >> 16) & 0xff);
                result[4] = (byte) ((contentsLength >> 8) & 0xff);
                result[5] = (byte) (contentsLength & 0xff);
            }
            result[0] = firstIdentifierByte;
        }
        for (final byte[] c : contents) {
            System.arraycopy(c, 0, result, contentsPosInResult, c.length);
            contentsPosInResult += c.length;
        }
        return result;
    }

    private static final class JavaToDerConverter {
        private JavaToDerConverter() {
        }

        public static byte[] toDer(@NonNull final Object source, final Asn1Type targetType, @Nullable final Asn1Type targetElementType)
                throws Asn1EncodingException {
            final Class<?> sourceType = source.getClass();
            if (Asn1OpaqueObject.class.equals(sourceType)) {
                final ByteBuffer buf = ((Asn1OpaqueObject) source).getEncoded();
                final byte[] result = new byte[buf.remaining()];
                buf.get(result);
                return result;
            }
            if ((targetType == null) || (targetType == Asn1Type.Any)) {
                return Asn1DerEncoder.encode(source);
            }
            switch (targetType) {
                case OctetString:
                    byte[] value = null;
                    if (source instanceof ByteBuffer) {
                        final ByteBuffer buf = (ByteBuffer) source;
                        value = new byte[buf.remaining()];
                        buf.slice().get(value);
                    } else if (source instanceof byte[]) {
                        value = (byte[]) source;
                    }
                    if (value != null) {
                        return Asn1DerEncoder.createTag(
                                BerEncoding.TAG_CLASS_UNIVERSAL,
                                false,
                                BerEncoding.TAG_NUMBER_OCTET_STRING,
                                value);
                    }
                    break;
                case Integer:
                    if (source instanceof Integer) {
                        return Asn1DerEncoder.toInteger((Integer) source);
                    } else if (source instanceof Long) {
                        return Asn1DerEncoder.toInteger((Long) source);
                    } else if (source instanceof BigInteger) {
                        return Asn1DerEncoder.toInteger((BigInteger) source);
                    }
                    break;
                case ObjectIdentifier:
                    if (source instanceof String) {
                        return Asn1DerEncoder.toOid((String) source);
                    }
                    break;
                case Sequence: {
                    final Asn1Class containerAnnotation = sourceType.getAnnotation(Asn1Class.class);
                    if ((containerAnnotation != null)
                            && (containerAnnotation.type() == Asn1Type.Sequence)) {
                        return Asn1DerEncoder.toSequence(source);
                    }
                    break;
                }
                case Choice: {
                    final Asn1Class containerAnnotation = sourceType.getAnnotation(Asn1Class.class);
                    if ((containerAnnotation != null)
                            && (containerAnnotation.type() == Asn1Type.Choice)) {
                        return Asn1DerEncoder.toChoice(source);
                    }
                    break;
                }
                case SetOf:
                    return Asn1DerEncoder.toSetOf((Collection<?>) source, targetElementType);
                default:
                    break;
            }
            throw new Asn1EncodingException(
                    "Unsupported conversion: " + sourceType.getName() + " to ASN.1 " + targetType);
        }
    }
}
