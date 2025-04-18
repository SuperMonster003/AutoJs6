package net.dongliu.apk.parser.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.struct.xml.Attribute;
import net.dongliu.apk.parser.struct.xml.Attributes;
import net.dongliu.apk.parser.struct.xml.XmlCData;
import net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag;
import net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag;
import net.dongliu.apk.parser.struct.xml.XmlNodeEndTag;
import net.dongliu.apk.parser.struct.xml.XmlNodeStartTag;

/**
 * Parse adaptive icon xml file.
 *
 * @author Liu Dong dongliu@live.cn
 */
public class AdaptiveIconParser implements XmlStreamer {
    @Nullable
    private String foreground;
    @Nullable
    private String background;

    @Nullable
    public String getForeground() {
        return this.foreground;
    }

    @Nullable
    public String getBackground() {
        return this.background;
    }

    @Override
    public void onStartTag(final @NonNull XmlNodeStartTag xmlNodeStartTag) {
        if ("background".equals(xmlNodeStartTag.name)) {
            this.background = this.getDrawable(xmlNodeStartTag);
        } else if ("foreground".equals(xmlNodeStartTag.name)) {
            this.foreground = this.getDrawable(xmlNodeStartTag);
        }
    }

    @Nullable
    private String getDrawable(final XmlNodeStartTag xmlNodeStartTag) {
        final Attributes attributes = xmlNodeStartTag.attributes;
        for (final Attribute attribute : attributes.attributes) {
            if (attribute.name.equals("drawable")) {
                return attribute.value;
            }
        }
        return null;
    }

    @Override
    public void onEndTag(@NonNull final XmlNodeEndTag xmlNodeEndTag) {
    }

    @Override
    public void onCData(@NonNull final XmlCData xmlCData) {
    }

    @Override
    public void onNamespaceStart(@NonNull final XmlNamespaceStartTag tag) {
    }

    @Override
    public void onNamespaceEnd(@NonNull final XmlNamespaceEndTag tag) {
    }
}
