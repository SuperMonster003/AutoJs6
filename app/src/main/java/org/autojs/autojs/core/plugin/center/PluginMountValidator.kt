package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import java.lang.reflect.Modifier
import java.util.Locale

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 22, 2026.
 */
object PluginMountValidator {

    data class ValidationResult(
        val objectName: String,
        val mountPoint: String?,
        val conflict: Conflict? = null,
    ) {
        val fullPath: String
            get() = if (mountPoint.isNullOrBlank()) objectName else "$mountPoint.$objectName"
    }

    data class Conflict(
        val type: String,
        val source: ConflictSource,
        val occupiedByPackageName: String? = null,
    )

    enum class ConflictSource {
        RUNTIME_OBJECT,
        MOUNT_CONFIG,
    }

    private data class RuntimeSymbol(
        val type: String,
        val javaClass: Class<*>?,
    )

    private val identifierRegex = Regex("^[A-Za-z_\\$][A-Za-z0-9_\\$]*$")
    private val mountPointRegex = Regex("^[A-Za-z_\\$][A-Za-z0-9_\\$]*(?:\\.[A-Za-z_\\$][A-Za-z0-9_\\$]*)*$")

    private val runtimeRootSymbols by lazy {
        buildMap {
            put("global", RuntimeSymbol(type = "object", javaClass = Any::class.java))
            RUNTIME_ROOT_CANDIDATES.forEach { name ->
                val resolvedClass = resolveRuntimeRootClass(name)
                put(
                    name,
                    RuntimeSymbol(
                        type = "object",
                        javaClass = resolvedClass,
                    ),
                )
            }
        }
    }

    fun validate(
        packageName: String,
        objectNameInput: String?,
        mountPointInput: String?,
    ): ValidationResult {
        val objectName = objectNameInput?.trim().orEmpty()
            .ifBlank { PluginMountStore.defaultObjectName(packageName) }
        val mountPoint = PluginMountStore.normalizeMountPoint(mountPointInput)

        require(objectName.matches(identifierRegex)) { "invalid_object_name" }
        require(mountPoint.isNullOrBlank() || mountPoint.matches(mountPointRegex)) { "invalid_mount_point" }

        val fullPath = if (mountPoint.isNullOrBlank()) objectName else "$mountPoint.$objectName"
        val configConflict = PluginMountStore.listEnabledConfigs()
            .firstOrNull { cfg ->
                cfg.packageName != packageName && resolveFullPath(cfg.packageName, cfg.objectName, cfg.mountPoint) == fullPath
            }
        if (configConflict != null) {
            return ValidationResult(
                objectName = objectName,
                mountPoint = mountPoint,
                conflict = Conflict(
                    type = "plugin",
                    source = ConflictSource.MOUNT_CONFIG,
                    occupiedByPackageName = configConflict.packageName,
                ),
            )
        }

        val runtimeType = detectRuntimeType(mountPoint, objectName)
        val conflict = runtimeType
            ?.takeIf { it != "undefined" }
            ?.let { Conflict(type = it, source = ConflictSource.RUNTIME_OBJECT) }
        return ValidationResult(
            objectName = objectName,
            mountPoint = mountPoint,
            conflict = conflict,
        )
    }

    private fun detectRuntimeType(mountPoint: String?, objectName: String): String? {
        if (mountPoint.isNullOrBlank() && objectName in GLOBAL_RESERVED_FUNCTIONS) {
            return "function"
        }
        if (mountPoint.isNullOrBlank()) {
            runtimeRootSymbols[objectName]?.let { return it.type }
        }

        detectTypeByReflection(mountPoint, objectName)?.let { return it }

        val segments = buildList {
            if (!mountPoint.isNullOrBlank()) {
                addAll(mountPoint.split('.').map { it.trim() }.filter { it.isNotEmpty() })
            }
            add(objectName)
        }
        if (segments.isEmpty()) return null

        val jsArrayLiteral = segments.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
        ) { "'${it.replace("'", "\\'")}'" }
        val script = """
            (function () {
              var ctx = this;
              var path = $jsArrayLiteral;
              for (var i = 0; i < path.length; i++) {
                var k = path[i];
                if (ctx == null || typeof ctx[k] === 'undefined') {
                  return 'undefined';
                }
                ctx = ctx[k];
              }
              return typeof ctx;
            })()
        """.trimIndent()
        return runCatching { RhinoUtils.runJavaScript(script)?.toString() }.getOrNull()
    }

    private fun detectTypeByReflection(mountPoint: String?, objectName: String): String? {
        val segments = buildList {
            if (!mountPoint.isNullOrBlank()) {
                addAll(mountPoint.split('.').map { it.trim() }.filter { it.isNotEmpty() })
            }
            add(objectName)
        }
        if (segments.isEmpty()) return null

        val root = runtimeRootSymbols[segments.first()] ?: return null
        if (segments.size == 1) {
            return root.type
        }

        var currentClass: Class<*>? = root.javaClass
        for (index in 1 until segments.size) {
            val segment = segments[index]
            val member = resolveMember(currentClass, segment) ?: return null
            if (index == segments.lastIndex) {
                return member.type
            }
            currentClass = member.javaClass
        }
        return null
    }

    private fun resolveMember(ownerClass: Class<*>?, name: String): RuntimeSymbol? {
        if (ownerClass == null) return null

        ownerClass.methods
            .firstOrNull { it.name == name && Modifier.isPublic(it.modifiers) }
            ?.let { method ->
                val returnType = method.returnType.takeUnless { it == Void.TYPE }
                return RuntimeSymbol(type = "function", javaClass = returnType)
            }

        ownerClass.fields
            .firstOrNull { it.name == name && Modifier.isPublic(it.modifiers) }
            ?.let { field ->
                return RuntimeSymbol(type = guessType(field.type), javaClass = field.type)
            }

        val getterSuffix = name.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase() else c.toString()
        }
        ownerClass.methods
            .firstOrNull {
                Modifier.isPublic(it.modifiers) &&
                    it.parameterCount == 0 &&
                    (it.name == "get$getterSuffix" || it.name == "is$getterSuffix")
            }?.let { getter ->
                val returnType = getter.returnType.takeUnless { it == Void.TYPE }
                return RuntimeSymbol(type = guessType(returnType), javaClass = returnType)
            }

        return null
    }

    private fun guessType(clazz: Class<*>?): String {
        if (clazz == null) return "object"
        val className = clazz.name
        if (className.contains("Function", ignoreCase = true) ||
            className.contains("Lambda", ignoreCase = true) ||
            className.contains("Callable", ignoreCase = true)
        ) {
            return "function"
        }
        if (clazz == java.lang.Boolean.TYPE || clazz == Boolean::class.java) {
            return "boolean"
        }
        if (clazz == String::class.java || clazz == Character.TYPE || clazz == Char::class.java) {
            return "string"
        }
        if (clazz.isPrimitive || Number::class.java.isAssignableFrom(clazz)) {
            return "number"
        }
        return "object"
    }

    private fun resolveFullPath(packageName: String, objectName: String?, mountPoint: String?): String {
        val obj = objectName?.trim().takeUnless { it.isNullOrBlank() } ?: PluginMountStore.defaultObjectName(packageName)
        val mp = PluginMountStore.normalizeMountPoint(mountPoint)
        return if (mp.isNullOrBlank()) obj else "$mp.$obj"
    }

    private fun resolveRuntimeRootClass(name: String): Class<*>? {
        val candidateNames = linkedSetOf<String>().apply {
            add(name)
            addAll(ROOT_FIELD_ALIASES[name].orEmpty())
        }

        ScriptRuntime::class.java.declaredFields
            .firstOrNull { field ->
                !Modifier.isStatic(field.modifiers) &&
                    Modifier.isPublic(field.modifiers) &&
                    !field.isSynthetic &&
                    field.name in candidateNames
            }?.let { return it.type }

        val getterCandidates = candidateNames.map { "get${it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString() }}" }
        ScriptRuntime::class.java.methods
            .firstOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    Modifier.isPublic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.name in getterCandidates
            }?.let { return it.returnType.takeUnless { type -> type == Void.TYPE } }

        return null
    }

    private val ROOT_FIELD_ALIASES = mapOf(
        "image" to listOf("images"),
    )

    private val RUNTIME_ROOT_CANDIDATES = listOf(
        "app",
        "autojs",
        "automator",
        "canvas",
        "color",
        "console",
        "device",
        "dialogs",
        "engines",
        "events",
        "files",
        "floaty",
        "http",
        "image",
        "media",
        "notice",
        "ocr",
        "plugins",
        "shell",
        "sqlite",
        "storages",
        "tasks",
        "threads",
        "timers",
        "ui",
        "util",
        "web",
    )

    private val GLOBAL_RESERVED_FUNCTIONS = setOf(
        "startActivity",
        "startDualActivity",
        "startService",
        "sendEmail",
        "sendBroadcast",
        "sendLocalBroadcastSync",
        "launch",
        "launchDual",
        "launchPackage",
        "launchDualPackage",
        "launchApp",
        "launchDualApp",
        "getPackageName",
        "getAppName",
        "launchAppDetailsSettings",
        "launchSettings",
        "openAppSetting",
        "openAppSettings",
        "launchDualAppDetailsSettings",
        "launchDualSettings",
        "openDualAppSetting",
        "openDualAppSettings",
        "isInstalled",
        "isDualInstalled",
        "uninstall",
        "uninstallDual",
        "kill",
        "killDual",
    )
}
