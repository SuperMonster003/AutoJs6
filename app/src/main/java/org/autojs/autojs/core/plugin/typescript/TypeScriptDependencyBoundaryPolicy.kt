package org.autojs.autojs.core.plugin.typescript

import java.util.Locale
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal data class TypeScriptDependencyBoundary(
    val code: String,
    val signal: String,
    val dependencyPath: String,
    val packageName: String,
    val priority: Int,
)

internal class TypeScriptDependencyBoundaryException(
    val code: String,
    val signal: String,
    val dependencyPath: String,
    val packageName: String,
    val detail: String,
) : IllegalArgumentException("$code: $detail")

/** Mirrors dependency-layer policy revision 2 before filtered files enter the compiler archive. */
internal object TypeScriptDependencyBoundaryPolicy {

    const val POLICY_REVISION = 2
    const val POLICY_FINGERPRINT =
        "c159ae179de24307c456c69ae4e456ed9d7dba71dc965c22e6114f632af3834a"

    val installLifecycleKeys: Set<String> = setOf("preinstall", "install", "postinstall")

    fun inspectPath(relativePath: String): TypeScriptDependencyBoundary? {
        val foldedName = relativePath.substringAfterLast('/').lowercase(Locale.ROOT)
        return when {
            foldedName.endsWith(".node") -> boundary(
                code = TypeScriptR2Contract.ERROR_NATIVE_DEPENDENCY_UNSUPPORTED,
                signal = "native-binary",
                relativePath = relativePath,
                priority = 0,
            )
            foldedName == "binding.gyp" -> boundary(
                code = TypeScriptR2Contract.ERROR_NATIVE_DEPENDENCY_UNSUPPORTED,
                signal = "binding-gyp",
                relativePath = relativePath,
                priority = 1,
            )
            else -> null
        }
    }

    fun inspectPackageJson(
        relativePath: String,
        bytes: ByteArray,
    ): TypeScriptDependencyBoundary? {
        if (!relativePath.substringAfterLast('/').equals("package.json", true)) return null
        val root = TypeScriptStrictJson.parseObject(
            bytes = bytes,
            maximumDepth = MAXIMUM_PACKAGE_JSON_DEPTH,
            maximumNodes = MAXIMUM_PACKAGE_JSON_NODES,
        )
        val declaredName = (root["name"] as? String)?.takeIf(String::isNotBlank)
        if (root["gypfile"] == true) {
            return boundary(
                code = TypeScriptR2Contract.ERROR_NATIVE_DEPENDENCY_UNSUPPORTED,
                signal = "gypfile",
                relativePath = relativePath,
                priority = 2,
                declaredPackageName = declaredName,
            )
        }
        val scripts = root["scripts"] as? Map<*, *> ?: return null
        val lifecycle = installLifecycleKeys.sorted().firstOrNull(scripts::containsKey)
            ?: return null
        return boundary(
            code = TypeScriptR2Contract.ERROR_DEPENDENCY_INSTALL_SCRIPT_UNSUPPORTED,
            signal = "scripts.$lifecycle",
            relativePath = relativePath,
            priority = 3,
            declaredPackageName = declaredName,
        )
    }

    fun requireSupported(boundaries: Collection<TypeScriptDependencyBoundary>) {
        val selected = boundaries.minWithOrNull(
            compareBy<TypeScriptDependencyBoundary>(
                TypeScriptDependencyBoundary::priority,
                TypeScriptDependencyBoundary::dependencyPath,
                TypeScriptDependencyBoundary::signal,
            ),
        ) ?: return
        val reason = when (selected.code) {
            TypeScriptR2Contract.ERROR_NATIVE_DEPENDENCY_UNSUPPORTED ->
                "Dependency '${selected.packageName}' contains native addon signal " +
                    "'${selected.signal}' at ${selected.dependencyPath}; Android TypeScript " +
                    "projects do not load npm native addons. Use a pure JavaScript or WASM " +
                    "alternative."
            else ->
                "Dependency '${selected.packageName}' declares install lifecycle signal " +
                    "'${selected.signal}' at ${selected.dependencyPath}; Android dependency " +
                    "snapshots never run preinstall, install, or postinstall scripts. Replace the " +
                    "package with a published pure JavaScript/TypeScript snapshot."
        }
        throw TypeScriptDependencyBoundaryException(
            code = selected.code,
            signal = selected.signal,
            dependencyPath = selected.dependencyPath,
            packageName = selected.packageName,
            detail = reason,
        )
    }

    private fun boundary(
        code: String,
        signal: String,
        relativePath: String,
        priority: Int,
        declaredPackageName: String? = null,
    ) = TypeScriptDependencyBoundary(
        code = code,
        signal = signal,
        dependencyPath = relativePath,
        packageName = declaredPackageName ?: inferPackageName(relativePath),
        priority = priority,
    )

    private fun inferPackageName(relativePath: String): String {
        val segments = relativePath.split('/')
        val nestedMarker = segments.indexOfLast { segment -> segment.equals("node_modules", true) }
        val packageStart = nestedMarker + 1
        val first = segments.getOrNull(packageStart).orEmpty()
        if (first.isBlank()) return "unknown"
        return if (first.startsWith('@')) {
            listOfNotNull(first, segments.getOrNull(packageStart + 1)).joinToString("/")
        } else {
            first
        }
    }

    private const val MAXIMUM_PACKAGE_JSON_DEPTH = 32
    private const val MAXIMUM_PACKAGE_JSON_NODES = 64 * 1024
}
