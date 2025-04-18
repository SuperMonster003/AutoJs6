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

import net.dongliu.apk.parser.cert.asn1.ber.BerDataValue;
import net.dongliu.apk.parser.cert.asn1.ber.BerDataValueFormatException;
import net.dongliu.apk.parser.cert.asn1.ber.BerDataValueReader;
import net.dongliu.apk.parser.cert.asn1.ber.BerEncoding;
import net.dongliu.apk.parser.cert.asn1.ber.ByteBufferBerDataValueReader;
import net.dongliu.apk.parser.utils.Buffers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Parser of ASN.1 BER-encoded structures.
 * <p>
 * <p>Structure is described to the parser by providing a class annotated with {@link Asn1Class},
 * containing fields annotated with {@link Asn1Field}.
 */
public final class Asn1BerParser {
    private Asn1BerParser() {
    }

    /**
     * Returns the ASN.1 structure contained in the BER encoded input.
     *
     * @param encoded        encoded input. If the decoding operation succeeds, the position of this buffer
     *                       is advanced to the first position following the end of the consumed structure.
     * @param containerClass class describing the structure of the input. The class must meet the
     *                       following requirements:
     *                       <ul>
     *                       <li>The class must be annotated with {@link Asn1Class}.</li>
     *                       <li>The class must expose a public no-arg constructor.</li>
     *                       <li>Member fields of the class which are populated with parsed input must be
     *                       annotated with {@link Asn1Field} and be public and non-final.</li>
     *                       </ul>
     * @throws Asn1DecodingException if the input could not be decoded into the specified Java
     *                               object
     */
    public static <T> T parse(final @NonNull ByteBuffer encoded, final Class<T> containerClass)
            throws Asn1DecodingException {
        final BerDataValue containerDataValue;
        try {
            containerDataValue = new ByteBufferBerDataValueReader(encoded).readDataValue();
        } catch (final BerDataValueFormatException e) {
            throw new Asn1DecodingException("Failed to decode top-level data value", e);
        }
        if (containerDataValue == null) {
            throw new Asn1DecodingException("Empty input");
        }
        return Asn1BerParser.parse(containerDataValue, containerClass);
    }

    /**
     * Returns the implicit {@code SET OF} contained in the provided ASN.1 BER input. Implicit means
     * that this method does not care whether the tag number of this data structure is
     * {@code SET OF} and whether the tag class is {@code UNIVERSAL}.
     * <p>
     * <p>Note: The returned type is {@link List} rather than {@link java.util.Set} because ASN.1
     * SET may contain duplicate elements.
     *
     * @param encoded      encoded input. If the decoding operation succeeds, the position of this buffer
     *                     is advanced to the first position following the end of the consumed structure.
     * @param elementClass class describing the structure of the values/elements contained in this
     *                     container. The class must meet the following requirements:
     *                     <ul>
     *                     <li>The class must be annotated with {@link Asn1Class}.</li>
     *                     <li>The class must expose a public no-arg constructor.</li>
     *                     <li>Member fields of the class which are populated with parsed input must be
     *                     annotated with {@link Asn1Field} and be public and non-final.</li>
     *                     </ul>
     * @throws Asn1DecodingException if the input could not be decoded into the specified Java
     *                               object
     */
    public static <T> List<T> parseImplicitSetOf(final @NonNull ByteBuffer encoded, final Class<T> elementClass)
            throws Asn1DecodingException {
        final BerDataValue containerDataValue;
        try {
            containerDataValue = new ByteBufferBerDataValueReader(encoded).readDataValue();
        } catch (final BerDataValueFormatException e) {
            throw new Asn1DecodingException("Failed to decode top-level data value", e);
        }
        if (containerDataValue == null) {
            throw new Asn1DecodingException("Empty input");
        }
        return Asn1BerParser.parseSetOf(containerDataValue, elementClass);
    }

    private static <T> T parse(final BerDataValue container, final Class<T> containerClass)
            throws Asn1DecodingException {
        if (container == null) {
            throw new NullPointerException("container == null");
        }
        if (containerClass == null) {
            throw new NullPointerException("containerClass == null");
        }
        final Asn1Type dataType = Asn1BerParser.getContainerAsn1Type(containerClass);
        switch (dataType) {
            case Choice:
                return Asn1BerParser.parseChoice(container, containerClass);
            case Sequence: {
                final int expectedTagClass = BerEncoding.TAG_CLASS_UNIVERSAL;
                final int expectedTagNumber = BerEncoding.getTagNumber(dataType);
                if ((container.tagClass != expectedTagClass)
                        || (container.tagNumber != expectedTagNumber)) {
                    throw new Asn1UnexpectedTagException(
                            "Unexpected data value read as " + containerClass.getName()
                                    + ". Expected " + BerEncoding.tagClassAndNumberToString(
                                    expectedTagClass, expectedTagNumber)
                                    + ", but read: " + BerEncoding.tagClassAndNumberToString(
                                    container.tagClass, container.tagNumber));
                }
                return Asn1BerParser.parseSequence(container, containerClass);
            }
            default:
                throw new Asn1DecodingException("Parsing container " + dataType + " not supported");
        }
    }

    private static <T> T parseChoice(final BerDataValue dataValue, final Class<T> containerClass)
            throws Asn1DecodingException {
        final List<AnnotatedField> fields = Asn1BerParser.getAnnotatedFields(containerClass);
        if (fields.isEmpty()) {
            throw new Asn1DecodingException(
                    "No fields annotated with " + Asn1Field.class.getName()
                            + " in CHOICE class " + containerClass.getName());
        }
        // Check that class + tagNumber don't clash between the choices
        for (int i = 0; i < fields.size() - 1; i++) {
            final AnnotatedField f1 = fields.get(i);
            final int tagNumber1 = f1.berTagNumber;
            final int tagClass1 = f1.berTagClass;
            for (int j = i + 1; j < fields.size(); j++) {
                final AnnotatedField f2 = fields.get(j);
                final int tagNumber2 = f2.berTagNumber;
                final int tagClass2 = f2.berTagClass;
                if ((tagNumber1 == tagNumber2) && (tagClass1 == tagClass2)) {
                    throw new Asn1DecodingException(
                            "CHOICE fields are indistinguishable because they have the same tag"
                                    + " class and number: " + containerClass.getName()
                                    + "." + f1.field.getName()
                                    + " and ." + f2.field.getName());
                }
            }
        }
        // Instantiate the container object / result
        final T obj;
        try {
            obj = containerClass.getConstructor().newInstance();
        } catch (final IllegalArgumentException | ReflectiveOperationException e) {
            throw new Asn1DecodingException("Failed to instantiate " + containerClass.getName(), e);
        }
        // Set the matching field's value from the data value
        for (final AnnotatedField field : fields) {
            try {
                field.setValueFrom(dataValue, obj);
                return obj;
            } catch (final Asn1UnexpectedTagException expected) {
                // not a match
            }
        }
        throw new Asn1DecodingException(
                "No options of CHOICE " + containerClass.getName() + " matched");
    }

    private static <T> T parseSequence(final BerDataValue container, final Class<T> containerClass)
            throws Asn1DecodingException {
        final List<AnnotatedField> fields = Asn1BerParser.getAnnotatedFields(containerClass);
        Collections.sort(
                fields, Comparator.comparingInt(f -> f.annotation.index()));
        // Check that there are no fields with the same index
        if (fields.size() > 1) {
            AnnotatedField lastField = null;
            for (final AnnotatedField field : fields) {
                if ((lastField != null)
                        && (lastField.annotation.index() == field.annotation.index())) {
                    throw new Asn1DecodingException(
                            "Fields have the same index: " + containerClass.getName()
                                    + "." + lastField.field.getName()
                                    + " and ." + field.field.getName());
                }
                lastField = field;
            }
        }
        // Instantiate the container object / result
        final T t;
        try {
            t = containerClass.getConstructor().newInstance();
        } catch (final IllegalArgumentException | ReflectiveOperationException e) {
            throw new Asn1DecodingException("Failed to instantiate " + containerClass.getName(), e);
        }
        // Parse fields one by one. A complication is that there may be optional fields.
        int nextUnreadFieldIndex = 0;
        final BerDataValueReader elementsReader = container.contentsReader();
        while (nextUnreadFieldIndex < fields.size()) {
            final BerDataValue dataValue;
            try {
                dataValue = elementsReader.readDataValue();
            } catch (final BerDataValueFormatException e) {
                throw new Asn1DecodingException("Malformed data value", e);
            }
            if (dataValue == null) {
                break;
            }
            for (int i = nextUnreadFieldIndex; i < fields.size(); i++) {
                final AnnotatedField field = fields.get(i);
                try {
                    if (field.isOptional) {
                        // Optional field -- might not be present and we may thus be trying to set
                        // it from the wrong tag.
                        try {
                            field.setValueFrom(dataValue, t);
                            nextUnreadFieldIndex = i + 1;
                            break;
                        } catch (final Asn1UnexpectedTagException e) {
                            // This field is not present, attempt to use this data value for the
                            // next / iteration of the loop
                        }
                    } else {
                        // Mandatory field -- if we can't set its value from this data value, then
                        // it's an error
                        field.setValueFrom(dataValue, t);
                        nextUnreadFieldIndex = i + 1;
                        break;
                    }
                } catch (final Asn1DecodingException e) {
                    throw new Asn1DecodingException(
                            "Failed to parse " + containerClass.getName()
                                    + "." + field.field.getName(),
                            e);
                }
            }
        }
        return t;
    }

    /**
     * NOTE: This method returns List rather than Set because ASN.1 SET_OF does require uniqueness
     * of elements -- it's an unordered collection.
     */
    @SuppressWarnings("unchecked")
    private static <T> List<T> parseSetOf(final BerDataValue container, final Class<T> elementClass)
            throws Asn1DecodingException {
        final List<T> result = new ArrayList<>();
        final BerDataValueReader elementsReader = container.contentsReader();
        while (true) {
            final BerDataValue dataValue;
            try {
                dataValue = elementsReader.readDataValue();
            } catch (final BerDataValueFormatException e) {
                throw new Asn1DecodingException("Malformed data value", e);
            }
            if (dataValue == null) {
                break;
            }
            final T element;
            if (ByteBuffer.class.equals(elementClass)) {
                element = (T) dataValue.getEncodedContents();
            } else if (Asn1OpaqueObject.class.equals(elementClass)) {
                element = (T) new Asn1OpaqueObject(dataValue.getEncoded());
            } else {
                element = Asn1BerParser.parse(dataValue, elementClass);
            }
            result.add(element);
        }
        return result;
    }

    private static Asn1Type getContainerAsn1Type(final Class<?> containerClass)
            throws Asn1DecodingException {
        final Asn1Class containerAnnotation = containerClass.getAnnotation(Asn1Class.class);
        if (containerAnnotation == null) {
            throw new Asn1DecodingException(
                    containerClass.getName() + " is not annotated with "
                            + Asn1Class.class.getName());
        }
        switch (containerAnnotation.type()) {
            case Choice:
            case Sequence:
                return containerAnnotation.type();
            default:
                throw new Asn1DecodingException(
                        "Unsupported ASN.1 container annotation type: "
                                + containerAnnotation.type());
        }
    }

    private static Class<?> getElementType(final Field field)
            throws Asn1DecodingException, ClassNotFoundException {
        final String type = field.getGenericType().toString();
        final int delimiterIndex = type.indexOf('<');
        if (delimiterIndex == -1) {
            throw new Asn1DecodingException("Not a container type: " + field.getGenericType());
        }
        final int startIndex = delimiterIndex + 1;
        final int endIndex = type.indexOf('>', startIndex);
        // TODO: handle comma?
        if (endIndex == -1) {
            throw new Asn1DecodingException("Not a container type: " + field.getGenericType());
        }
        final String elementClassName = type.substring(startIndex, endIndex);
        return Class.forName(elementClassName);
    }

    private static final class AnnotatedField {
        public final Field field;
        public final Asn1Field annotation;
        private final Asn1Type dataType;
        public final int berTagClass;
        public final int berTagNumber;
        private final Asn1Tagging tagging;
        public final boolean isOptional;

        public AnnotatedField(@NonNull final Field field, @NonNull final Asn1Field annotation) throws Asn1DecodingException {
            this.field = field;
            this.annotation = annotation;
            this.dataType = annotation.type();
            Asn1TagClass tagClass = annotation.cls();
            if (tagClass == Asn1TagClass.Automatic) {
                if (annotation.tagNumber() != -1) {
                    tagClass = Asn1TagClass.ContextSpecific;
                } else {
                    tagClass = Asn1TagClass.Universal;
                }
            }
            this.berTagClass = BerEncoding.getTagClass(tagClass);
            final int tagNumber;
            if (annotation.tagNumber() != -1) {
                tagNumber = annotation.tagNumber();
            } else if ((this.dataType == Asn1Type.Choice) || (this.dataType == Asn1Type.Any)) {
                tagNumber = -1;
            } else {
                tagNumber = BerEncoding.getTagNumber(this.dataType);
            }
            this.berTagNumber = tagNumber;
            this.tagging = annotation.tagging();
            if (((this.tagging == Asn1Tagging.Explicit) || (this.tagging == Asn1Tagging.Implicit))
                    && (annotation.tagNumber() == -1)) {
                throw new Asn1DecodingException(
                        "Tag number must be specified when tagging mode is " + this.tagging);
            }
            this.isOptional = annotation.optional();
        }

        public void setValueFrom(@NonNull BerDataValue dataValue, final Object obj) throws Asn1DecodingException {
            final int readTagClass = dataValue.tagClass;
            if (this.berTagNumber != -1) {
                final int readTagNumber = dataValue.tagNumber;
                if ((readTagClass != this.berTagClass) || (readTagNumber != this.berTagNumber)) {
                    throw new Asn1UnexpectedTagException(
                            "Tag mismatch. Expected: "
                                    + BerEncoding.tagClassAndNumberToString(this.berTagClass, this.berTagNumber)
                                    + ", but found "
                                    + BerEncoding.tagClassAndNumberToString(readTagClass, readTagNumber));
                }
            } else {
                if (readTagClass != this.berTagClass) {
                    throw new Asn1UnexpectedTagException(
                            "Tag mismatch. Expected class: "
                                    + BerEncoding.tagClassToString(this.berTagClass)
                                    + ", but found "
                                    + BerEncoding.tagClassToString(readTagClass));
                }
            }
            if (this.tagging == Asn1Tagging.Explicit) {
                try {
                    dataValue = dataValue.contentsReader().readDataValue();
                } catch (final BerDataValueFormatException e) {
                    throw new Asn1DecodingException(
                            "Failed to read contents of EXPLICIT data value", e);
                }
            }
            BerToJavaConverter.setFieldValue(obj, this.field, this.dataType, dataValue);
        }
    }

    private static class Asn1UnexpectedTagException extends Asn1DecodingException {
        private static final long serialVersionUID = 1L;

        public Asn1UnexpectedTagException(final String message) {
            super(message);
        }
    }

    private static String oidToString(final ByteBuffer encodedOid) throws Asn1DecodingException {
        if (!encodedOid.hasRemaining()) {
            throw new Asn1DecodingException("Empty OBJECT IDENTIFIER");
        }
        // First component encodes the first two nodes, X.Y, as X * 40 + Y, with 0 <= X <= 2
        final long firstComponent = Asn1BerParser.decodeBase128UnsignedLong(encodedOid);
        final int firstNode = (int) Math.min(firstComponent / 40, 2);
        final long secondNode = firstComponent - firstNode * 40L;
        final StringBuilder result = new StringBuilder();
        result.append(Long.toString(firstNode)).append('.')
                .append(secondNode);
        // Each consecutive node is encoded as a separate component
        while (encodedOid.hasRemaining()) {
            final long node = Asn1BerParser.decodeBase128UnsignedLong(encodedOid);
            result.append('.').append(node);
        }
        return result.toString();
    }

    private static long decodeBase128UnsignedLong(final ByteBuffer encoded) throws Asn1DecodingException {
        if (!encoded.hasRemaining()) {
            return 0;
        }
        long result = 0;
        while (encoded.hasRemaining()) {
            if (result > Long.MAX_VALUE >>> 7) {
                throw new Asn1DecodingException("Base-128 number too large");
            }
            final int b = encoded.get() & 0xff;
            result <<= 7;
            result |= b & 0x7f;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new Asn1DecodingException(
                "Truncated base-128 encoded input: missing terminating byte, with highest bit not"
                        + " set");
    }

    @NonNull
    private static BigInteger integerToBigInteger(final ByteBuffer encoded) {
        if (!encoded.hasRemaining()) {
            return BigInteger.ZERO;
        }
        return new BigInteger(Buffers.readBytes(encoded));
    }

    private static int integerToInt(final ByteBuffer encoded) throws Asn1DecodingException {
        final BigInteger value = Asn1BerParser.integerToBigInteger(encoded);
        try {
            return value.intValue();
        } catch (final ArithmeticException e) {
            throw new Asn1DecodingException(
                    String.format("INTEGER cannot be represented as int: %1$d (0x%1$x)", value), e);
        }
    }

    private static long integerToLong(final ByteBuffer encoded) throws Asn1DecodingException {
        final BigInteger value = Asn1BerParser.integerToBigInteger(encoded);
        try {
            return value.intValue();
        } catch (final ArithmeticException e) {
            throw new Asn1DecodingException(
                    String.format("INTEGER cannot be represented as long: %1$d (0x%1$x)", value),
                    e);
        }
    }

    private static List<AnnotatedField> getAnnotatedFields(final Class<?> containerClass)
            throws Asn1DecodingException {
        final Field[] declaredFields = containerClass.getDeclaredFields();
        final List<AnnotatedField> result = new ArrayList<>(declaredFields.length);
        for (final Field field : declaredFields) {
            final Asn1Field annotation = field.getAnnotation(Asn1Field.class);
            if (annotation == null) {
                continue;
            }
            if (Modifier.isStatic(field.getModifiers())) {
                throw new Asn1DecodingException(
                        Asn1Field.class.getName() + " used on a static field: "
                                + containerClass.getName() + "." + field.getName());
            }
            final AnnotatedField annotatedField;
            try {
                annotatedField = new AnnotatedField(field, annotation);
            } catch (final Asn1DecodingException e) {
                throw new Asn1DecodingException(
                        "Invalid ASN.1 annotation on "
                                + containerClass.getName() + "." + field.getName(),
                        e);
            }
            result.add(annotatedField);
        }
        return result;
    }

    private static final class BerToJavaConverter {
        private BerToJavaConverter() {
        }

        public static void setFieldValue(
                final Object obj, final Field field, final Asn1Type type, final BerDataValue dataValue)
                throws Asn1DecodingException {
            try {
                switch (type) {
                    case SetOf:
                    case SequenceOf:
                        if (Asn1OpaqueObject.class.equals(field.getType())) {
                            field.set(obj, BerToJavaConverter.convert(type, dataValue, field.getType()));
                        } else {
                            field.set(obj, Asn1BerParser.parseSetOf(dataValue, Asn1BerParser.getElementType(field)));
                        }
                        return;
                    default:
                        field.set(obj, BerToJavaConverter.convert(type, dataValue, field.getType()));
                        break;
                }
            } catch (final ReflectiveOperationException e) {
                throw new Asn1DecodingException(
                        "Failed to set value of " + obj.getClass().getName()
                                + "." + field.getName(),
                        e);
            }
        }

        private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

        @SuppressWarnings("unchecked")
        public static <T> T convert(
                final Asn1Type sourceType,
                final BerDataValue dataValue,
                final Class<T> targetType) throws Asn1DecodingException {
            if (ByteBuffer.class.equals(targetType)) {
                return (T) dataValue.getEncodedContents();
            } else if (byte[].class.equals(targetType)) {
                final ByteBuffer resultBuf = dataValue.getEncodedContents();
                if (!resultBuf.hasRemaining()) {
                    return (T) BerToJavaConverter.EMPTY_BYTE_ARRAY;
                }
                final byte[] result = new byte[resultBuf.remaining()];
                resultBuf.get(result);
                return (T) result;
            } else if (Asn1OpaqueObject.class.equals(targetType)) {
                return (T) new Asn1OpaqueObject(dataValue.getEncoded());
            }
            final ByteBuffer encodedContents = dataValue.getEncodedContents();
            switch (sourceType) {
                case Integer:
                    if ((int.class.equals(targetType)) || (Integer.class.equals(targetType))) {
                        return (T) Integer.valueOf(Asn1BerParser.integerToInt(encodedContents));
                    } else if ((long.class.equals(targetType)) || (Long.class.equals(targetType))) {
                        return (T) Long.valueOf(Asn1BerParser.integerToLong(encodedContents));
                    } else if (BigInteger.class.equals(targetType)) {
                        return (T) Asn1BerParser.integerToBigInteger(encodedContents);
                    }
                    break;
                case ObjectIdentifier:
                    if (String.class.equals(targetType)) {
                        return (T) Asn1BerParser.oidToString(encodedContents);
                    }
                    break;
                case Sequence: {
                    final Asn1Class containerAnnotation = targetType.getAnnotation(Asn1Class.class);
                    if ((containerAnnotation != null)
                            && (containerAnnotation.type() == Asn1Type.Sequence)) {
                        return Asn1BerParser.parseSequence(dataValue, targetType);
                    }
                    break;
                }
                case Choice: {
                    final Asn1Class containerAnnotation = targetType.getAnnotation(Asn1Class.class);
                    if ((containerAnnotation != null)
                            && (containerAnnotation.type() == Asn1Type.Choice)) {
                        return Asn1BerParser.parseChoice(dataValue, targetType);
                    }
                    break;
                }
                default:
                    break;
            }
            throw new Asn1DecodingException(
                    "Unsupported conversion: ASN.1 " + sourceType + " to " + targetType.getName());
        }
    }
}
