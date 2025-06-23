package net.dongliu.apk.parser.parser

import net.dongliu.apk.parser.struct.xml.*

/**
 * the xml file's namespaces.
 *
 * @author dongliu
 */
internal class XmlNamespaces {
    private val namespaces: MutableList<XmlNamespace> = ArrayList()
    private val newNamespaces: MutableList<XmlNamespace> = ArrayList()
    fun addNamespace(tag: XmlNamespaceStartTag) {
        val namespace = XmlNamespace(tag.prefix, tag.uri)
        namespaces.add(namespace)
        newNamespaces.add(namespace)
    }

    fun removeNamespace(tag: XmlNamespaceEndTag) {
        val namespace = XmlNamespace(tag.prefix, tag.uri)
        namespaces.remove(namespace)
        newNamespaces.remove(namespace)
    }

    fun getPrefixViaUri(uri: String?): String? {
        if (uri == null) {
            return null
        }
        for (namespace in namespaces) {
            if (uri == namespace.uri) {
                return namespace.prefix
            }
        }
        return null
    }

    fun consumeNameSpaces(): List<XmlNamespace> {
        return if (newNamespaces.isNotEmpty()) {
            val xmlNamespaces: List<XmlNamespace> =
                ArrayList(newNamespaces)
            newNamespaces.clear()
            xmlNamespaces
        } else {
            emptyList()
        }
    }

    /**
     * one namespace
     */
    class XmlNamespace constructor(@JvmField val prefix: String?, @JvmField val uri: String?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is XmlNamespace) return false
            return prefix == other.prefix && uri == other.uri
        }

        override fun hashCode(): Int {
            var result = prefix?.hashCode() ?: 0
            result = 31 * result + (uri?.hashCode() ?: 0)
            return result
        }
    }
}
