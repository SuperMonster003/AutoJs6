package net.dongliu.apk.parser.struct.xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author dongliu
 */
public class XmlNodeStartTag {
    @Nullable
    public final String namespace;
    @Nullable
    public final String name;

    //    /**
//     * Byte offset from the start of this structure where the attributes start. uint16
//     */
//    public int attributeStart;
//    /**
//     * Size of the ResXMLTree_attribute structures that follow. unit16
//     */
//    public int attributeSize;
//    /**
//     * Number of attributes associated with an ELEMENT. uint 16
//     * These are available as an array of ResXMLTree_attribute structures immediately following this node.
//     */
//    public int attributeCount;
//    /**
//     * Index (1-based) of the "id" attribute. 0 if none. uint16
//     */
//    public short idIndex;
//    /**
//     * Index (1-based) of the "style" attribute. 0 if none. uint16
//     */
//    public short styleIndex;
    @NonNull
    public final Attributes attributes;

    public XmlNodeStartTag(@Nullable final String namespace, final @Nullable String name, final @NonNull Attributes attributes) {
        this.namespace = namespace;
        this.name = name;
        this.attributes = attributes;
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append('<');
        if (this.namespace != null) {
            sb.append(this.namespace).append(":");
        }
        sb.append(this.name);
        sb.append('>');
        return sb.toString();
    }
}
