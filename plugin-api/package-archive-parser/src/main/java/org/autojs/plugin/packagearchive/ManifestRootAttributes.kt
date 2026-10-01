package org.autojs.plugin.packagearchive

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/** Security-relevant root attributes must not come from comments or an unrelated namespace. */
internal object ManifestRootAttributes {
    const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    const val SHARED_USER_ID = "sharedUserId"
    const val SHARED_USER_ID_RESOURCE = android.R.attr.sharedUserId
    private const val MAX_ATTRIBUTES = 4096
    private const val MAX_SHARED_USER_ID_CHARS = 1024

    data class Root(val attributes: Map<String, String>, val sharedUserId: String?)

    fun read(xml: String): Root {
        if (xml.length > PackageInspectionLimits.MANIFEST_BYTES) throw IOException("Manifest root input exceeds its limit")
        // No external DTD or custom entity is needed by an Android manifest. The resolver is
        // also closed below; this guard applies even when a platform lacks optional SAX flags.
        if (xml.contains("<!DOCTYPE", true) || xml.contains("<!ENTITY", true)) {
            throw IOException("Manifest document types and entity declarations are not supported")
        }
        val found = SAXException("manifest root captured")
        var result: Root? = null
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                if (!uri.isNullOrEmpty() || localName != "manifest") throw SAXException("Android manifest root element is missing")
                if (attributes.length > MAX_ATTRIBUTES) throw SAXException("Manifest root has too many attributes")
                val expanded = HashSet<Pair<String, String>>()
                val values = linkedMapOf<String, String>()
                var shared: String? = null
                for (index in 0 until attributes.length) {
                    val name = attributes.getLocalName(index)
                    val namespace = attributes.getURI(index).orEmpty()
                    if (!expanded.add(namespace to name)) throw SAXException("Duplicate manifest root attribute")
                    val value = attributes.getValue(index)
                    val key = when (namespace) {
                        "" -> name
                        ANDROID_NAMESPACE -> "android:$name"
                        else -> "{$namespace}$name"
                    }
                    values[key] = value
                    if (namespace == ANDROID_NAMESPACE && name == SHARED_USER_ID) shared = requireLiteral(value)
                }
                result = Root(values, shared)
                throw found
            }

            override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
                throw SAXException("External manifest entities are forbidden")

            override fun error(error: SAXParseException): Unit = throw error
            override fun fatalError(error: SAXParseException): Unit = throw error
        }
        try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }
            val reader = factory.newSAXParser().xmlReader
            for (feature in listOf("http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd")) {
                runCatching { reader.setFeature(feature, false) }
            }
            reader.contentHandler = handler
            reader.entityResolver = handler
            reader.errorHandler = handler
            reader.parse(InputSource(StringReader(xml)))
        } catch (failure: Exception) {
            if (failure !== found) throw IOException("Manifest root attributes cannot be verified", failure)
        }
        return result ?: throw IOException("Android manifest root element is missing")
    }

    /** Compiled resource IDs determine Android attributes; contradictory display names are unsafe. */
    fun compiledName(namespace: String?, name: String?, resourceId: Int): String? {
        if (resourceId == SHARED_USER_ID_RESOURCE) {
            if (namespace != ANDROID_NAMESPACE || (!name.isNullOrEmpty() && name != SHARED_USER_ID)) {
                throw IOException("Shared UID attribute namespace/name disagrees with its Android resource ID")
            }
            return SHARED_USER_ID
        }
        if (namespace == ANDROID_NAMESPACE && name == SHARED_USER_ID) {
            throw IOException("Shared UID attribute lacks its verified Android resource ID")
        }
        return name
    }

    fun requireLiteral(value: String): String {
        if (value.isBlank() || value.length > MAX_SHARED_USER_ID_CHARS || value.any(Char::isISOControl) ||
            value != value.trim() || value.startsWith('@') || value.startsWith('?')) {
            throw IOException("Shared UID is not a bounded literal string")
        }
        return value
    }
}
