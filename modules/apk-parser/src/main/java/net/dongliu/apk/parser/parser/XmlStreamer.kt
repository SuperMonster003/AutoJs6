package net.dongliu.apk.parser.parser

import net.dongliu.apk.parser.struct.xml.XmlNodeStartTag
import net.dongliu.apk.parser.struct.xml.XmlNodeEndTag
import net.dongliu.apk.parser.struct.xml.XmlCData
import net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag
import net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag

/**
 * callback interface for parse binary xml file.
 *
 * @author dongliu
 */
interface XmlStreamer {
    fun onStartTag(xmlNodeStartTag: XmlNodeStartTag)
    fun onEndTag(xmlNodeEndTag: XmlNodeEndTag)
    fun onCData(xmlCData: XmlCData)
    fun onNamespaceStart(tag: XmlNamespaceStartTag)
    fun onNamespaceEnd(tag: XmlNamespaceEndTag)
}
