package org.autojs.autojs.core.ui.inflater

import org.autojs.autojs.core.ui.xml.SecureXml
import org.w3c.dom.Element
import java.io.File
import java.util.LinkedHashMap

/**
 * A small, source-resource repository for Android XML used directly by scripts.
 *
 * Android's [android.content.res.Resources] can only see resources compiled into an APK. Rhino
 * projects, however, keep their resources as ordinary files. This repository reads the familiar
 * `res/layout`, `res/drawable`, and `res/values` tree without assigning compiled resource IDs.
 *
 * Qualified directories (`values-night`, `layout-land`, `drawable-xxhdpi`, host roadmap A7) take part: each resource
 * comes from the directory Android would choose for [configuration] ([ResourceQualifiers.best]); a directory with a
 * qualifier the repository does not support is ignored and listed in [ignoredDirectories].
 */
class ScriptResourceRepository {

    data class Style(
        val parent: String?,
        val items: LinkedHashMap<String, String>,
    )

    internal data class ResolvedStyle(
        val attributes: LinkedHashMap<String, String>,
        val compiledParent: String?,
    )

    var resourceRoot: File? = null
        private set

    /** What qualified directories are matched against; set with [configure]. */
    var configuration: ResourceConfiguration = ResourceConfiguration.DEFAULT
        private set

    private val values = LinkedHashMap<String, String>()
    private val styles = LinkedHashMap<String, Style>()
    private val qualifiedValues = LinkedHashMap<String, MutableList<Pair<ResourceQualifiers, String>>>()
    private val qualifiedStyles = LinkedHashMap<String, MutableList<Pair<ResourceQualifiers, Style>>>()
    private val ignored = LinkedHashSet<String>()
    private var valuesLoaded = false

    /** Names of resource directories skipped for a qualifier the repository does not support (`layout-large`). */
    val ignoredDirectories: Set<String>
        @Synchronized get() = LinkedHashSet(ignored)

    @Synchronized
    fun configure(sourceFile: File? = null, workingDirectory: File? = null, configuration: ResourceConfiguration = ResourceConfiguration.DEFAULT) {
        this.configuration = configuration
        resourceRoot = findResourceRoot(sourceFile, workingDirectory)
        clearValues()
        ignored.clear()
    }

    private fun clearValues() {
        values.clear()
        styles.clear()
        qualifiedValues.clear()
        qualifiedStyles.clear()
        valuesLoaded = false
    }

    fun hasResourceRoot() = resourceRoot?.isDirectory == true

    @Synchronized
    fun resolveValue(reference: String): String {
        ensureValuesLoaded()
        return resolveValue(reference, LinkedHashSet())
    }

    @Synchronized
    fun resolveStyle(reference: String): LinkedHashMap<String, String>? {
        return resolveStyleDetails(reference)?.attributes
    }

    @Synchronized
    internal fun resolveStyleDetails(reference: String): ResolvedStyle? {
        ensureValuesLoaded()
        val styleName = parseReference(reference)?.takeIf { it.first == "style" }?.second ?: return null
        if (!styles.containsKey(styleName)) {
            return null
        }
        return resolveStyleDetails(styleName, LinkedHashSet())
    }

    fun readLayout(reference: String): String? {
        val file = findFileResource(reference, setOf("layout"), listOf("xml")) ?: return null
        return SecureXml.read(file)
    }

    fun findDrawable(reference: String): File? {
        return findFileResource(
            reference,
            setOf("drawable", "mipmap"),
            listOf("xml", "9.png", "png", "webp", "jpg", "jpeg", "gif"),
        )
    }

    fun findColor(reference: String): File? {
        return findFileResource(reference, setOf("color"), listOf("xml"))
    }

    @Synchronized
    internal fun findFileResource(reference: String, acceptedTypes: Set<String>, extensions: List<String>): File? {
        val (type, name) = parseReference(reference) ?: return null
        if (type !in acceptedTypes || !RESOURCE_NAME.matches(name)) {
            return null
        }
        val root = resourceRoot?.canonicalFile ?: return null
        val candidates = resourceDirectories(root, type).mapNotNull { (directory, qualifiers) ->
            extensions.asSequence()
                .map { extension -> File(directory, "$name.$extension").canonicalFile }
                .firstOrNull { candidate -> candidate.startsWith(root) && candidate.isFile }
                ?.let { qualifiers to it }
        }
        return ResourceQualifiers.best(candidates, configuration)
    }

    internal fun parseDocument(xml: String) = SecureXml.parse(xml)

    internal fun parseDocument(file: File) = SecureXml.parse(file)

    private fun resolveValue(reference: String, resolving: LinkedHashSet<String>): String {
        val key = parseReference(reference)?.let { (type, name) -> "$type/$name" } ?: return reference
        val value = values[key] ?: return reference
        check(resolving.size < MAX_RESOURCE_DEPTH) {
            "Android resource reference depth exceeds $MAX_RESOURCE_DEPTH: ${resolving.joinToString(" -> ")}"
        }
        check(resolving.add(key)) {
            "Circular Android resource reference: ${(resolving.toList() + key).joinToString(" -> ")}"
        }
        return try {
            if (key.startsWith("array/")) {
                value.split('|').joinToString("|") { resolveValue(it, resolving) }
            } else {
                resolveValue(value, resolving)
            }
        } finally {
            resolving.remove(key)
        }
    }

    private fun resolveStyleDetails(name: String, resolving: LinkedHashSet<String>): ResolvedStyle {
        check(resolving.size < MAX_RESOURCE_DEPTH) {
            "Android style inheritance depth exceeds $MAX_RESOURCE_DEPTH: ${resolving.joinToString(" -> ")}"
        }
        check(resolving.add(name)) {
            "Circular Android style inheritance: ${(resolving.toList() + name).joinToString(" -> ")}"
        }
        return try {
            val style = styles[name] ?: return ResolvedStyle(linkedMapOf(), null)
            val result = linkedMapOf<String, String>()
            val parentReference = if (style.parent != null) {
                style.parent.takeIf(String::isNotBlank)
            } else {
                name.substringBeforeLast('.', "").takeIf(String::isNotEmpty)?.let { "@style/$it" }
            }
            val localParent = parentReference
                ?.let(::localStyleName)
                ?.takeIf(styles::containsKey)
            val inherited = localParent?.let { parent ->
                resolveStyleDetails(parent, resolving)
            }
            inherited?.attributes?.let(result::putAll)
            result.putAll(style.items)
            ResolvedStyle(
                attributes = result,
                compiledParent = inherited?.compiledParent
                    ?: parentReference?.takeIf { localParent == null },
            )
        } finally {
            resolving.remove(name)
        }
    }

    private fun localStyleName(reference: String): String? {
        parseReference(reference)?.takeIf { it.first == "style" }?.second?.let { return it }
        return reference
            .takeIf { !it.startsWith("@") && !it.startsWith("?") && STYLE_NAME.matches(it) }
    }

    private fun ensureValuesLoaded() {
        if (valuesLoaded) {
            return
        }
        val root = resourceRoot ?: return
        clearValues()
        try {
            resourceDirectories(root, "values").forEach { (directory, qualifiers) ->
                directory.listFiles { file -> file.isFile && file.extension.equals("xml", true) }
                    ?.sortedBy(File::getName)
                    ?.forEach { loadValuesFile(it, qualifiers) }
            }
            // Each value and style from the directory that suits the configuration best.
            qualifiedValues.forEach { (key, candidates) -> ResourceQualifiers.best(candidates, configuration)?.let { values[key] = it } }
            qualifiedStyles.forEach { (name, candidates) -> ResourceQualifiers.best(candidates, configuration)?.let { styles[name] = it } }
            valuesLoaded = true
        } catch (error: Exception) {
            clearValues()
            throw error
        }
    }

    /** A later definition in the same directory replaces an earlier one, as before qualifiers were read. */
    private fun <T> MutableMap<String, MutableList<Pair<ResourceQualifiers, T>>>.define(key: String, qualifiers: ResourceQualifiers, value: T) {
        val candidates = getOrPut(key) { ArrayList() }
        candidates.removeAll { it.first == qualifiers }
        candidates += qualifiers to value
    }

    private fun loadValuesFile(file: File, qualifiers: ResourceQualifiers) {
        val root = parseDocument(file).documentElement ?: return
        if (root.tagName != "resources") {
            return
        }
        root.childElements().forEach { element ->
            val name = element.getAttribute("name").trim()
            if (!RESOURCE_NAME.matches(name) && !(element.tagName == "style" && STYLE_NAME.matches(name))) {
                return@forEach
            }
            when (element.tagName) {
                "style" -> {
                    val items = linkedMapOf<String, String>()
                    element.childElements()
                        .filter { it.tagName == "item" }
                        .forEach { item ->
                            item.getAttribute("name").trim()
                                .takeIf(String::isNotEmpty)
                                ?.let { items[it] = decodeValue(item.textContent) }
                        }
                    qualifiedStyles.define(
                        name,
                        qualifiers,
                        Style(
                            parent = element.getAttribute("parent").takeIf { element.hasAttribute("parent") },
                            items = items,
                        ),
                    )
                }
                "string-array", "integer-array", "array" -> {
                    qualifiedValues.define(
                        "array/$name",
                        qualifiers,
                        element.childElements()
                            .filter { it.tagName == "item" }
                            .joinToString("|") { decodeValue(it.textContent) },
                    )
                }
                "item" -> {
                    val type = element.getAttribute("type").trim()
                    if (RESOURCE_TYPE.matches(type)) {
                        qualifiedValues.define("$type/$name", qualifiers, decodeValue(element.textContent))
                    }
                }
                else -> if (RESOURCE_TYPE.matches(element.tagName)) {
                    qualifiedValues.define("${element.tagName}/$name", qualifiers, decodeValue(element.textContent))
                }
            }
        }
    }

    /** The directories of [type] (`drawable`, `drawable-night-xxhdpi`) with their qualifiers, by name; unsupported ones are noted. */
    private fun resourceDirectories(root: File, type: String): List<Pair<File, ResourceQualifiers>> =
        root.listFiles { file -> file.isDirectory && (file.name == type || file.name.startsWith("$type-")) }
            .orEmpty()
            .sortedBy(File::getName)
            .mapNotNull { directory ->
                ResourceQualifiers.parse(directory.name, type)?.let { directory to it } ?: run {
                    ignored += directory.name
                    null
                }
            }

    private fun findResourceRoot(sourceFile: File?, workingDirectory: File?): File? {
        sourceFile?.canonicalFile?.let { source ->
            var directory = source.takeIf(File::isDirectory) ?: source.parentFile
            while (directory != null) {
                if (directory.name == "res" && directory.isDirectory) {
                    return directory
                }
                File(directory, "res").takeIf(File::isDirectory)?.let { return it }
                directory = directory.parentFile
            }
        }
        workingDirectory?.canonicalFile?.let { working ->
            if (working.name == "res" && working.isDirectory) {
                return working
            }
            File(working, "res").takeIf(File::isDirectory)?.let { return it }
        }
        return null
    }

    private fun parseReference(reference: String): Pair<String, String>? {
        val match = RESOURCE_REFERENCE.matchEntire(reference.trim()) ?: return null
        if (!match.groupValues[1].isBlank()) {
            return null
        }
        return match.groupValues[2] to match.groupValues[3]
    }

    private fun decodeValue(raw: String): String {
        var value = raw.trim()
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            value = value.substring(1, value.lastIndex)
        }
        return value
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")
    }

    companion object {

        private val RESOURCE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val STYLE_NAME = Regex("[A-Za-z_][A-Za-z0-9_.]*")
        private val RESOURCE_TYPE = Regex("[A-Za-z_][A-Za-z0-9_-]*")
        private val RESOURCE_REFERENCE = Regex("@(?:\\+)?(?:(\\w+):)?([\\w-]+)/([A-Za-z_][A-Za-z0-9_.]*)")
        private const val MAX_RESOURCE_DEPTH = 32

        internal fun Element.childElements(): List<Element> = buildList {
            val children = childNodes
            for (index in 0 until children.length) {
                (children.item(index) as? Element)?.let(::add)
            }
        }

    }
}
