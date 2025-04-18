package net.dongliu.apk.parser.struct

/**
 * Apk res value struct.
 * Only for description now, The value is hold in ResourceValue
 *
 * @author dongliu
 */
object ResValue {
    object ResType {
        /**
         * Contains no data.
         */
        const val NULL: Short = 0x00

        /**
         * The 'data' holds a ResTable_ref; a reference to another resource
         * table entry.
         */
        const val REFERENCE: Short = 0x01
        //        /**
        //         * The 'data' holds an attribute resource identifier.
        //         */
        //        public static final short ATTRIBUTE = 0x02;
        /**
         * The 'data' holds an index into the containing resource table's
         * global value string pool.
         */
        const val STRING: Short = 0x03
        //        /**
        //         * The 'data' holds a single-precision floating point number.
        //         */
        //        public static final short FLOAT = 0x04;
        /**
         * The 'data' holds a complex number encoding a dimension value;
         * such as "100in".
         */
        const val DIMENSION: Short = 0x05

        /**
         * The 'data' holds a complex number encoding a fraction of a
         * container.
         */
        const val FRACTION: Short = 0x06

        /**
        The 'data' holds a dynamic ResTable_ref, which needs to be
        resolved before it can be used like a TYPE_REFERENCE.
         */
        const val TYPE_DYNAMIC_REFERENCE: Short = 0x07

        //        /**
        //         * Beginning of integer flavors...
        //         */
        //        public static final short FIRST_INT = 0x10;
        /**
         * The 'data' is a raw integer value of the form n..n.
         */
        const val INT_DEC: Short = 0x10

        /**
         * The 'data' is a raw integer value of the form 0xn..n.
         */
        const val INT_HEX: Short = 0x11

        /**
         * The 'data' is either 0 or 1; for input "false" or "true" respectively.
         */
        const val INT_BOOLEAN: Short = 0x12
        //        /**
        //         * Beginning of color integer flavors...
        //         */
        //        public static final short FIRST_COLOR_INT = 0x1c;
        /**
         * The 'data' is a raw integer value of the form #aarrggbb.
         */
        const val INT_COLOR_ARGB8: Short = 0x1c

        /**
         * The 'data' is a raw integer value of the form #rrggbb.
         */
        const val INT_COLOR_RGB8: Short = 0x1d

        /**
         * The 'data' is a raw integer value of the form #argb.
         */
        const val INT_COLOR_ARGB4: Short = 0x1e

        /**
         * The 'data' is a raw integer value of the form #rgb.
         */
        const val INT_COLOR_RGB4: Short = 0x1f //        /**
        //         * ...end of integer flavors.
        //         */
        //        public static final short LAST_COLOR_INT = 0x1f;
        //
        //        /**
        //         * ...end of integer flavors.
        //         */
        //        public static final short LAST_INT = 0x1f;
    }

    /**
     * A number of constants used when the data is interpreted as a composite value are defined
     * by the following anonymous C++ enum
     */
    object ResDataCOMPLEX {
        //        /**
        //         * Where the unit type information is.  This gives us 16 possible
        //         * types; as defined below.
        //         */
        //        public static final short UNIT_SHIFT = 0;
        //        public static final short UNIT_MASK = 0xf;
        /**
         * TYPE_DIMENSION: Value is raw pixels.
         */
        const val UNIT_PX: Short = 0

        /**
         * TYPE_DIMENSION: Value is Device Independent Pixels.
         */
        const val UNIT_DIP: Short = 1

        /**
         * TYPE_DIMENSION: Value is a Scaled device independent Pixels.
         */
        const val UNIT_SP: Short = 2

        /**
         * TYPE_DIMENSION: Value is in points.
         */
        const val UNIT_PT: Short = 3

        /**
         * TYPE_DIMENSION: Value is in inches.
         */
        const val UNIT_IN: Short = 4

        /**
         * TYPE_DIMENSION: Value is in millimeters.
         */
        const val UNIT_MM: Short = 5

        /**
         * TYPE_FRACTION: A basic fraction of the overall size.
         */
        const val UNIT_FRACTION: Short = 0

        /**
         * TYPE_FRACTION: A fraction of the parent size.
         */
        const val UNIT_FRACTION_PARENT: Short = 1 //        /**
        //         * Where the radix information is; telling where the decimal place
        //         * appears in the mantissa.  This give us 4 possible fixed point
        //         * representations as defined below.
        //         */
        //        public static final short RADIX_SHIFT = 4;
        //        public static final short RADIX_MASK = 0x3;
        //
        //        /**
        //         * The mantissa is an integral number -- i.e.; 0xnnnnnn.0
        //         */
        //        public static final short RADIX_23p0 = 0;
        //        /**
        //         * The mantissa magnitude is 16 bits -- i.e; 0xnnnn.nn
        //         */
        //        public static final short RADIX_16p7 = 1;
        //        /**
        //         * The mantissa magnitude is 8 bits -- i.e; 0xnn.nnnn
        //         */
        //        public static final short RADIX_8p15 = 2;
        //        /**
        //         * The mantissa magnitude is 0 bits -- i.e; 0x0.nnnnnn
        //         */
        //        public static final short RADIX_0p23 = 3;
        //
        //        /**
        //         * Where the actual value is.  This gives us 23 bits of
        //         * precision.  The top bit is the sign.
        //         */
        //        public static final short MANTISSA_SHIFT = 8;
        //        public static final int MANTISSA_MASK = 0xffffff;
    }
}
