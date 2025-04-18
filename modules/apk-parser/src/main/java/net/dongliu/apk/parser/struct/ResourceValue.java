package net.dongliu.apk.parser.struct;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.struct.resource.Densities;
import net.dongliu.apk.parser.struct.resource.ResourceEntry;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import net.dongliu.apk.parser.struct.resource.Type;
import net.dongliu.apk.parser.struct.resource.TypeSpec;
import net.dongliu.apk.parser.utils.Locales;

import java.util.List;
import java.util.Locale;

/**
 * Resource entity, contains the resource id, should retrieve the value from resource table, or string pool if it is a string resource.
 *
 * @author dongliu
 */
public abstract class ResourceValue {
    protected final int value;

    protected ResourceValue(final int value) {
        this.value = value;
    }

    /**
     * get value as string.
     */
    @Nullable
    public abstract String toStringValue(ResourceTable resourceTable, Locale locale);

    @NonNull
    public static ResourceValue decimal(final int value) {
        return new DecimalResourceValue(value);
    }

    @NonNull
    public static ResourceValue hexadecimal(final int value) {
        return new HexadecimalResourceValue(value);
    }

    @NonNull
    public static ResourceValue bool(final int value) {
        return new BooleanResourceValue(value);
    }

    @NonNull
    public static ResourceValue string(final int value, final StringPool stringPool) {
        return new StringResourceValue(value, stringPool);
    }

    @NonNull
    public static ResourceValue reference(final int value) {
        return new ReferenceResourceValue(value);
    }

    @NonNull
    public static ResourceValue nullValue() {
        return NullResourceValue.instance;
    }

    @NonNull
    public static ResourceValue rgb(final int value, final int len) {
        return new RGBResourceValue(value, len);
    }

    @NonNull
    public static ResourceValue dimension(final int value) {
        return new DimensionValue(value);
    }

    @NonNull
    public static ResourceValue fraction(final int value) {
        return new FractionValue(value);
    }

    @NonNull
    public static ResourceValue raw(final int value, final short type) {
        return new RawValue(value, type);
    }

    private static class DecimalResourceValue extends ResourceValue {

        private DecimalResourceValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
            return String.valueOf(this.value);
        }
    }

    private static class HexadecimalResourceValue extends ResourceValue {

        private HexadecimalResourceValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
            return "0x" + Integer.toHexString(this.value);
        }
    }

    private static class BooleanResourceValue extends ResourceValue {

        private BooleanResourceValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
            return String.valueOf(this.value != 0);
        }
    }

    private static class StringResourceValue extends ResourceValue {
        private final StringPool stringPool;

        private StringResourceValue(final int value, final StringPool stringPool) {
            super(value);
            this.stringPool = stringPool;
        }

        @Nullable
        @Override
        public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
            if (this.value >= 0) {
                return this.stringPool.get(this.value);
            } else {
                return null;
            }
        }

        @NonNull
        @Override
        public String toString() {
            return this.value + ":" + this.stringPool.get(this.value);
        }
    }

    /**
     * ReferenceResource ref one another resources, and may has different value for different resource config(locale, density, etc)
     */
    public static class ReferenceResourceValue extends ResourceValue {

        private ReferenceResourceValue(final int value) {
            super(value);
        }

        @Override
        @Nullable
        public String toStringValue(final @Nullable ResourceTable resourceTable, final Locale locale) {
            final long resourceId = this.getReferenceResourceId();
            // android system styles.
            if (resourceId > AndroidConstants.SYS_STYLE_ID_START && resourceId < AndroidConstants.SYS_STYLE_ID_END) {
                return "@android:style/" + ResourceTable.sysStyle.get((int) resourceId);
            }
            final String raw = "resourceId:0x" + Long.toHexString(resourceId);
            if (resourceTable == null) {
                return raw;
            }
            final List<ResourceTable.Resource> resources = resourceTable.getResourcesById(resourceId);
            // read from type resource
            ResourceEntry selected = null;
            TypeSpec typeSpec = null;
            int currentLocalMatchLevel = -1;
            int currentDensityLevel = -1;
            for (final ResourceTable.Resource resource : resources) {
                final Type type = resource.type;
                typeSpec = resource.typeSpec;
                final ResourceEntry resourceEntry = resource.resourceEntry;
                final int localMatchLevel = Locales.match(locale, type.locale);
                final int densityLevel = ReferenceResourceValue.densityLevel(type.density);
                if (localMatchLevel > currentLocalMatchLevel) {
                    selected = resourceEntry;
                    currentLocalMatchLevel = localMatchLevel;
                    currentDensityLevel = densityLevel;
                } else if (densityLevel > currentDensityLevel) {
                    selected = resourceEntry;
                    currentDensityLevel = densityLevel;
                }
            }
            final String result;
            if (selected == null) {
                result = raw;
            } else if (locale == null) {
                result = "@" + typeSpec.name + "/" + selected.key;
            } else {
                result = selected.toStringValue(resourceTable, locale);
            }
            return result;
        }

        public long getReferenceResourceId() {
            return this.value & 0xFFFFFFFFL;
        }

        private static int densityLevel(final int density) {
            if (density == Densities.ANY || density == Densities.NONE) {
                return -1;
            }
            return density;
        }
    }

    private static class NullResourceValue extends ResourceValue {
        private static final NullResourceValue instance = new NullResourceValue();

        private NullResourceValue() {
            super(-1);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
            return "";
        }
    }

    private static class RGBResourceValue extends ResourceValue {
        private final int len;

        private RGBResourceValue(final int value, final int len) {
            super(value);
            this.len = len;
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
            final StringBuilder sb = new StringBuilder();
            for (int i = this.len / 2 - 1; i >= 0; i--) {
                sb.append(Integer.toHexString((this.value >> i * 8) & 0xff));
            }
            return sb.toString();
        }
    }

    private static class DimensionValue extends ResourceValue {

        private DimensionValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
            final short unit = (short) (this.value & 0xff);
            final String unitStr;
            switch (unit) {
                case ResValue.ResDataCOMPLEX.UNIT_MM:
                    unitStr = "mm";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_PX:
                    unitStr = "px";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_DIP:
                    unitStr = "dp";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_SP:
                    unitStr = "sp";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_PT:
                    unitStr = "pt";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_IN:
                    unitStr = "in";
                    break;
                default:
                    unitStr = "unknown unit:0x" + Integer.toHexString(unit);
            }
            return (this.value >> 8) + unitStr;
        }
    }

    private static class FractionValue extends ResourceValue {

        private FractionValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
            // The low-order 4 bits of the data value specify the type of the fraction
            final short type = (short) (this.value & 0xf);
            final String pstr;
            switch (type) {
                case ResValue.ResDataCOMPLEX.UNIT_FRACTION:
                    pstr = "%";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_FRACTION_PARENT:
                    pstr = "%p";
                    break;
                default:
                    pstr = "unknown type:0x" + Integer.toHexString(type);
            }
            final float f = Float.intBitsToFloat(this.value >> 4);
            return f + pstr;
        }
    }

    private static class RawValue extends ResourceValue {
        private final short dataType;

        private RawValue(final int value, final short dataType) {
            super(value);
            this.dataType = dataType;
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
            return "{" + this.dataType + ":" + (this.value & 0xFFFFFFFFL) + "}";
        }
    }
}
