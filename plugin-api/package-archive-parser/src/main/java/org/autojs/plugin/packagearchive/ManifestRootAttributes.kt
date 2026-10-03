package org.autojs.plugin.packagearchive

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/** Security-relevant manifest values must not come from comments or unrelated namespaces. */
internal object ManifestRootAttributes {
    const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    const val SHARED_USER_ID = "sharedUserId"
    const val SHARED_USER_ID_RESOURCE = android.R.attr.sharedUserId
    const val PERMISSION_NAME = "name"
    const val PERMISSION_NAME_RESOURCE = android.R.attr.name
    private const val MAX_ATTRIBUTES = 4096
    private const val MAX_IDENTIFIER_CHARS = 1024
    private const val MAX_NODES = 200_000
    private const val MAX_TOTAL_ATTRIBUTES = 400_000
    private const val MAX_DEPTH = 256
    private val PERMISSION_ELEMENTS = setOf("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")

    data class Root(val attributes: Map<String, String>, val sharedUserId: String?, val requestedPermissions: List<String> = emptyList())

    fun isPermissionElement(name: String?): Boolean = name != null && name in PERMISSION_ELEMENTS

    fun read(xml: String): Root {
        if (xml.length > PackageInspectionLimits.MANIFEST_BYTES) throw IOException("Manifest root input exceeds its limit")
        // No external DTD or custom entity is needed by an Android manifest. The resolver is
        // also closed below; this guard applies even when a platform lacks optional SAX flags.
        if (xml.contains("<!DOCTYPE", true) || xml.contains("<!ENTITY", true)) {
            throw IOException("Manifest document types and entity declarations are not supported")
        }
        var result: Root? = null
        var depth = 0
        var nodes = 0
        var totalAttributes = 0
        val permissions = linkedSetOf<String>()
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                depth++
                nodes++
                totalAttributes += attributes.length
                if (depth > MAX_DEPTH || nodes > MAX_NODES || totalAttributes > MAX_TOTAL_ATTRIBUTES || attributes.length > MAX_ATTRIBUTES) {
                    throw SAXException("Manifest structure exceeds its inspection limits")
                }
                if (depth == 1 && (result != null || !uri.isNullOrEmpty() || localName != "manifest")) {
                    throw SAXException("Android manifest root element is missing or duplicated")
                }
                val permission = depth == 2 && isPermissionElement(localName)
                if (permission && !uri.isNullOrEmpty()) throw SAXException("Permission element namespace cannot be verified")
                if (depth != 1 && !permission) return
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
                    if (depth == 1 && namespace == ANDROID_NAMESPACE && name == SHARED_USER_ID) shared = requireLiteral(value)
                    if (permission && namespace == ANDROID_NAMESPACE && name == PERMISSION_NAME) {
                        permissions += requireLiteral(value, "Permission name")
                    }
                }
                if (depth == 1) result = Root(values, shared)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) { depth-- }

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
            throw IOException("Manifest attributes and permission declarations cannot be verified", failure)
        }
        return result?.copy(requestedPermissions = permissions.sorted()) ?: throw IOException("Android manifest root element is missing")
    }

    /** Compiled resource IDs determine Android attributes; contradictory display names are unsafe. */
    fun compiledName(namespace: String?, name: String?, resourceId: Int,
        expectedName: String = SHARED_USER_ID, expectedResource: Int = SHARED_USER_ID_RESOURCE,
        label: String = "Shared UID"): String? {
        if (resourceId == expectedResource) {
            if (namespace != ANDROID_NAMESPACE || (!name.isNullOrEmpty() && name != expectedName)) {
                throw IOException("$label attribute namespace/name disagrees with its Android resource ID")
            }
            return expectedName
        }
        if (namespace == ANDROID_NAMESPACE && name == expectedName) {
            throw IOException("$label attribute lacks its verified Android resource ID")
        }
        return name
    }

    fun requireLiteral(value: String, label: String = "Shared UID"): String {
        if (value.isBlank() || value.length > MAX_IDENTIFIER_CHARS || value.any(Char::isISOControl) ||
            value != value.trim() || value.startsWith('@') || value.startsWith('?')) {
            throw IOException("$label is not a bounded literal string")
        }
        return value
    }
}
