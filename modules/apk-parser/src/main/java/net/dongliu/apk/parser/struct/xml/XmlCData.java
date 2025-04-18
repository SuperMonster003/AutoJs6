package net.dongliu.apk.parser.struct.xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.struct.ResourceValue;
import net.dongliu.apk.parser.struct.resource.ResourceTable;

import java.util.Locale;

/**
 * @author dongliu
 */
public class XmlCData {

    public static final String CDATA_START = "<![CDATA[";
    public static final String CDATA_END = "]]>";

    /**
     * The raw CDATA character data.
     */
    private String data;

    /**
     * The typed value of the character data if this is a CDATA node.
     */
    private ResourceValue typedData;

    /**
     * the final value as string
     */
    private String value;

    /**
     * get value as string
     */
    @NonNull
    public String toStringValue(final ResourceTable resourceTable, final Locale locale) {
        if (this.data != null) {
            return XmlCData.CDATA_START + this.data + XmlCData.CDATA_END;
        } else {
            return XmlCData.CDATA_START + this.typedData.toStringValue(resourceTable, locale) + XmlCData.CDATA_END;
        }
    }

    public String getData() {
        return this.data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public ResourceValue getTypedData() {
        return this.typedData;
    }

    public void setTypedData(final @Nullable ResourceValue typedData) {
        this.typedData = typedData;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    @NonNull
    @Override
    public String toString() {
        return "XmlCData{" +
                "data='" + this.data + '\'' +
                ", typedData=" + this.typedData +
                '}';
    }
}
