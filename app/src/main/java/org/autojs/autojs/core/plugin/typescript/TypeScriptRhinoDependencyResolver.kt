package org.autojs.autojs.core.plugin.typescript

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.util.Locale
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.commonjs.module.ModuleScope
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider
import org.mozilla.javascript.commonjs.module.Require

/**
 * Resolves static CommonJS package requests against the exact dependency snapshot materialized
 * for one Rhino TypeScript execution. Resolution starts at the calling module and walks parent
 * node_modules directories, so nested dependency versions do not collapse into a global alias.
 */
internal class TypeScriptRhinoDependencyResolver(
    runtimeRoot: File,
    packageManifestSources: Map<String, String>,
) {

    private val root = runtimeRoot.absoluteFile.normalize()
    private val manifestSources = packageManifestSources.toMap()
    private val parsedManifests = HashMap<String, JsonObject>()

    fun resolve(specifier: String, parentUri: URI?): File? {
        val request = PackageRequest.parse(specifier) ?: return null
        var directory = parentDirectory(parentUri)
        while (directory.isInsideOrSame(root)) {
            if (!directory.name.equals(NODE_MODULES_DIRECTORY, true)) {
                val packageDirectory = File(directory, NODE_MODULES_DIRECTORY)
                    .resolve(request.packageName)
                    .absoluteFile
                    .normalize()
                if (packageDirectory.isInsideOrSame(root) && packageDirectory.isDirectory) {
                    return resolvePackage(packageDirectory, request)
                }
            }
            if (directory == root) break
            directory = directory.parentFile ?: break
        }
        return null
    }

    private fun parentDirectory(parentUri: URI?): File {
        val parent = parentUri
            ?.takeIf { uri -> uri.scheme.equals("file", true) }
            ?.let { uri -> runCatching { File(uri).absoluteFile.normalize() }.getOrNull() }
            ?.let { file -> if (file.isDirectory) file else file.parentFile }
        return parent?.takeIf { directory -> directory.isInsideOrSame(root) } ?: root
    }

    private fun resolvePackage(packageDirectory: File, request: PackageRequest): File {
        val manifest = manifest(packageDirectory)
        val packageTypeModule = manifest?.stringValue("type") == "module"
        if (manifest?.has("exports") == true) {
            val selection = selectExport(manifest.get("exports"), request.exportKey)
            return when (selection) {
                is PackageTarget.Target -> resolveTarget(
                    packageDirectory,
                    selection.value,
                    request.specifier,
                    packageTypeModule,
                    exportsTarget = true,
                )
                PackageTarget.EsmOnly -> throw esmOnly(request.specifier)
                PackageTarget.Blocked,
                PackageTarget.NoMatch,
                -> throw runtimeError(
                    TypeScriptR2Contract.ERROR_RHINO_DEPENDENCY_PATH_NOT_EXPORTED,
                    "Package '${request.packageName}' does not export '${request.exportKey}' for CommonJS.",
                )
            }
        }

        if (request.subpath.isNotEmpty()) {
            return resolveTarget(
                packageDirectory,
                request.subpath,
                request.specifier,
                packageTypeModule,
                exportsTarget = false,
            )
        }

        manifest?.stringValue("main")
            ?.takeIf(String::isNotBlank)
            ?.let { main ->
                resolveTargetOrNull(
                    packageDirectory,
                    main,
                    request.specifier,
                    packageTypeModule,
                    exportsTarget = false,
                )?.let { return it }
            }
        resolveIndexOrNull(packageDirectory, request.specifier, packageTypeModule)?.let { return it }

        manifest?.stringValue("module")
            ?.takeIf(String::isNotBlank)
            ?.let { module ->
                if (targetExists(packageDirectory, module)) throw esmOnly(request.specifier)
            }
        throw runtimeError(
            TypeScriptR2Contract.ERROR_RHINO_DEPENDENCY_ENTRY_UNSUPPORTED,
            "Package '${request.packageName}' has no executable CommonJS entry.",
        )
    }

    private fun resolveTarget(
        packageDirectory: File,
        target: String,
        specifier: String,
        packageTypeModule: Boolean,
        exportsTarget: Boolean,
    ): File = resolveTargetOrNull(
        packageDirectory,
        target,
        specifier,
        packageTypeModule,
        exportsTarget,
    ) ?: throw runtimeError(
        TypeScriptR2Contract.ERROR_RHINO_DEPENDENCY_ENTRY_UNSUPPORTED,
        "CommonJS entry '$target' for '$specifier' is missing from the compiled dependency snapshot.",
    )

    private fun resolveTargetOrNull(
        packageDirectory: File,
        target: String,
        specifier: String,
        packageTypeModule: Boolean,
        exportsTarget: Boolean,
    ): File? {
        if (target.isBlank() || exportsTarget && !target.startsWith("./")) {
            throw invalidPackage(specifier, "invalid package target '$target'")
        }
        val candidate = File(packageDirectory, target.removePrefix("./"))
            .absoluteFile
            .normalize()
        if (!candidate.isInsideOrSame(packageDirectory)) {
            throw invalidPackage(specifier, "package target escapes its package root")
        }
        val files = if (candidate.extension.isNotEmpty()) {
            listOf(candidate)
        } else {
            listOf(
                candidate,
                File(candidate.path + ".cjs"),
                File(candidate.path + ".js"),
                File(candidate.path + ".json"),
                File(candidate.path + ".mjs"),
                File(candidate.path + ".cts"),
                File(candidate.path + ".ts"),
                File(candidate.path + ".mts"),
            )
        }
        files.firstOrNull(File::isFile)?.let { file ->
            return admitCommonJsFile(
                file,
                specifier,
                packageTypeFor(file, packageDirectory, packageTypeModule),
            )
        }
        if (candidate.isDirectory) {
            return resolveIndexOrNull(
                candidate,
                specifier,
                packageTypeFor(candidate, packageDirectory, packageTypeModule),
            )
        }
        return null
    }

    private fun resolveIndexOrNull(
        directory: File,
        specifier: String,
        packageTypeModule: Boolean,
    ): File? {
        val candidates = listOf(
            File(directory, "index.cjs"),
            File(directory, "index.js"),
            File(directory, "index.json"),
            File(directory, "index.mjs"),
            File(directory, "index.cts"),
            File(directory, "index.ts"),
            File(directory, "index.mts"),
        )
        val selected = candidates.firstOrNull(File::isFile) ?: return null
        return admitCommonJsFile(selected, specifier, packageTypeModule)
    }

    private fun admitCommonJsFile(
        file: File,
        specifier: String,
        packageTypeModule: Boolean,
    ): File {
        return when (file.extension.lowercase(Locale.ROOT)) {
            "cjs", "json" -> file
            "js", "" -> if (packageTypeModule) throw esmOnly(specifier) else file
            "mjs", "mts" -> throw esmOnly(specifier)
            else -> throw runtimeError(
                TypeScriptR2Contract.ERROR_RHINO_DEPENDENCY_ENTRY_UNSUPPORTED,
                "Dependency '$specifier' selects unsupported Rhino entry '${file.name}'.",
            )
        }
    }

    private fun packageTypeFor(
        target: File,
        packageDirectory: File,
        fallback: Boolean,
    ): Boolean {
        var directory = if (target.isDirectory) target else target.parentFile
        while (directory != null && directory.isInsideOrSame(packageDirectory)) {
            manifest(directory)?.let { metadata ->
                return metadata.stringValue("type") == "module"
            }
            if (directory == packageDirectory) break
            directory = directory.parentFile
        }
        return fallback
    }

    private fun manifest(packageDirectory: File): JsonObject? {
        val path = File(packageDirectory, PACKAGE_MANIFEST_NAME)
            .absoluteFile
            .normalize()
            .path
        val source = manifestSources[path] ?: return null
        return parsedManifests.getOrPut(path) {
            runCatching {
                JsonParser.parseString(source).takeIf(JsonElement::isJsonObject)?.asJsonObject
                    ?: error("Package manifest must be a JSON object")
            }.getOrElse { error ->
                throw TypeScriptRuntimeDependencyException(
                    TypeScriptR2Contract.ERROR_RHINO_DEPENDENCY_PACKAGE_INVALID,
                    "Dependency package metadata is invalid: ${relative(path)}",
                    error,
                )
            }
        }
    }

    private fun selectExport(value: JsonElement?, key: String): PackageTarget {
        return when {
            value == null || value.isJsonNull -> PackageTarget.Blocked
            value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                if (key == ".") PackageTarget.Target(value.asString) else PackageTarget.NoMatch
            value.isJsonObject -> {
                val valueObject = value.asJsonObject
                val keys = valueObject.stringKeys()
                if (keys.any { candidate -> candidate.startsWith(".") }) {
                    val exact = valueObject.get(key)
                    if (exact != null) return selectCondition(exact)
                    val pattern = keys
                        .asSequence()
                        .filter { candidate -> candidate.startsWith("./") && candidate.count { it == '*' } == 1 }
                        .mapNotNull { candidate ->
                            val prefix = candidate.substringBefore('*')
                            val suffix = candidate.substringAfter('*')
                            if (key.startsWith(prefix) && key.endsWith(suffix)) {
                                candidate to key.substring(prefix.length, key.length - suffix.length)
                            } else {
                                null
                            }
                        }
                        .maxByOrNull { candidate -> candidate.first.substringBefore('*').length }
                    if (pattern == null) {
                        PackageTarget.NoMatch
                    } else {
                        when (val selected = selectCondition(valueObject.get(pattern.first))) {
                            is PackageTarget.Target -> PackageTarget.Target(
                                selected.value.replace("*", pattern.second),
                            )
                            else -> selected
                        }
                    }
                } else if (key == ".") {
                    selectCondition(valueObject)
                } else {
                    PackageTarget.NoMatch
                }
            }
            else -> PackageTarget.NoMatch
        }
    }

    private fun selectCondition(value: JsonElement?): PackageTarget {
        return when {
            value == null -> PackageTarget.NoMatch
            value.isJsonNull -> PackageTarget.Blocked
            value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                PackageTarget.Target(value.asString)
            value.isJsonArray -> {
                val valueArray: JsonArray = value.asJsonArray
                var esmOnly = false
                for (index in 0 until valueArray.size()) {
                    when (val selected = selectCondition(valueArray.get(index))) {
                        is PackageTarget.Target -> return selected
                        PackageTarget.Blocked -> return selected
                        PackageTarget.EsmOnly -> esmOnly = true
                        PackageTarget.NoMatch -> Unit
                    }
                }
                if (esmOnly) PackageTarget.EsmOnly else PackageTarget.NoMatch
            }
            value.isJsonObject -> {
                val valueObject = value.asJsonObject
                var importOnly = false
                valueObject.stringKeys().forEach { condition ->
                    if (condition == "import" || condition == "module") importOnly = true
                    if (condition in RHINO_ACTIVE_EXPORT_CONDITIONS) {
                        when (val selected = selectCondition(valueObject.get(condition))) {
                            PackageTarget.NoMatch -> Unit
                            else -> return selected
                        }
                    }
                }
                if (importOnly) PackageTarget.EsmOnly else PackageTarget.NoMatch
            }
            else -> PackageTarget.NoMatch
        }
    }

    private fun targetExists(packageDirectory: File, target: String): Boolean {
        val candidate = File(packageDirectory, target.removePrefix("./"))
            .absoluteFile
            .normalize()
        return candidate.isInsideOrSame(packageDirectory) && candidate.exists()
    }

    private fun esmOnly(specifier: String) = runtimeError(
        TypeScriptR2Contract.ERROR_RHINO_ESM_ONLY_DEPENDENCY,
        "Dependency '$specifier' exposes only an ESM entry; run this project with the Node profile.",
    )

    private fun invalidPackage(specifier: String, reason: String) = runtimeError(
        TypeScriptR2Contract.ERROR_RHINO_DEPENDENCY_PACKAGE_INVALID,
        "Dependency '$specifier' has $reason.",
    )

    private fun runtimeError(code: String, message: String) =
        TypeScriptRuntimeDependencyException(code, message)

    private fun relative(path: String): String = File(path)
        .absoluteFile
        .normalize()
        .path
        .removePrefix(root.path + File.separator)
        .replace(File.separatorChar, '/')

    private data class PackageRequest(
        val specifier: String,
        val packageName: String,
        val subpath: String,
    ) {
        val exportKey: String
            get() = if (subpath.isEmpty()) "." else "./$subpath"

        companion object {
            fun parse(value: String): PackageRequest? {
                if (
                    value.isBlank() || value.startsWith('.') || value.startsWith('/') ||
                    '\\' in value || '\u0000' in value || ':' in value || value.startsWith('#')
                ) {
                    return null
                }
                val segments = value.split('/')
                val packageSegmentCount = if (value.startsWith('@')) 2 else 1
                if (
                    segments.size < packageSegmentCount ||
                    segments.take(packageSegmentCount).any(String::isBlank) ||
                    segments.drop(packageSegmentCount).any { segment ->
                        segment.isBlank() || segment == "." || segment == ".."
                    }
                ) {
                    return null
                }
                return PackageRequest(
                    specifier = value,
                    packageName = segments.take(packageSegmentCount).joinToString("/"),
                    subpath = segments.drop(packageSegmentCount).joinToString("/"),
                )
            }
        }
    }

    private sealed interface PackageTarget {
        data class Target(val value: String) : PackageTarget
        data object NoMatch : PackageTarget
        data object EsmOnly : PackageTarget
        data object Blocked : PackageTarget
    }

    private companion object {
        const val NODE_MODULES_DIRECTORY = "node_modules"
        const val PACKAGE_MANIFEST_NAME = "package.json"
        val RHINO_ACTIVE_EXPORT_CONDITIONS = setOf("require", "node", "default")
    }
}

/** Rewrites only package requests that the current TypeScript dependency snapshot can resolve. */
internal class TypeScriptRhinoCommonJsRequire(
    context: Context,
    nativeScope: TopLevel,
    moduleScriptProvider: ModuleScriptProvider,
    private val runtimeProject: () -> TypeScriptMaterializedRhinoProject?,
) : Require(context, nativeScope, moduleScriptProvider, null, null, true) {

    override fun call(
        cx: Context,
        scope: VarScope,
        thisObj: Scriptable?,
        args: Array<out Any?>,
    ): Any? {
        val specifier = args.firstOrNull() as? String
        val moduleScope = ModuleScope.findModuleScope(scope)
        val resolved = specifier?.let { request ->
            runtimeProject()?.resolveCommonJsDependency(request, moduleScope?.uri)
        }
        if (resolved == null) return super.call(cx, scope, thisObj, args)
        val rewritten = Array<Any?>(args.size) { index ->
            if (index == 0) resolved.toURI().toString() else args[index]
        }
        return super.call(cx, scope, thisObj, rewritten)
    }
}

private fun JsonObject.stringKeys(): List<String> = entrySet().map(Map.Entry<String, JsonElement>::key)

private fun JsonObject.stringValue(name: String): String? = get(name)
    ?.takeIf { value -> value.isJsonPrimitive && value.asJsonPrimitive.isString }
    ?.asString

private fun File.isInsideOrSame(root: File): Boolean {
    val rootPath = root.absolutePath.trimEnd(File.separatorChar)
    val candidatePath = absolutePath
    return candidatePath == rootPath || candidatePath.startsWith("$rootPath${File.separator}")
}
