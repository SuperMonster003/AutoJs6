package org.autojs.autojs.engine.module

import android.content.Context as AndroidContext
import org.mozilla.javascript.Context
import org.mozilla.javascript.Script
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.commonjs.module.ModuleScript
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.io.Reader
import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads CommonJS modules only from this APK's assets and shares their immutable Rhino compilation
 * products across engines in the current process.
 *
 * Require instances, module exports, scopes, and all other mutable execution state remain owned by
 * each [org.mozilla.javascript.commonjs.module.Require]. File and network modules are deliberately
 * outside this provider and must be handled by an engine-local fallback provider.
 */
class ProcessWideAssetModuleScriptProvider internal constructor(
    private val assetLoader: RhinoAssetLoader,
    assetDirPath: String,
) : ModuleScriptProvider {

    constructor(context: AndroidContext, assetDirPath: String) : this(
        AndroidRhinoAssetLoader(context),
        assetDirPath,
    )

    private val assetRoot = normalizeAssetRoot(assetDirPath)
    private val assetBasePath = "/android_asset/$assetRoot/"
    private val assetBaseUri = assetUri("$assetRoot/")

    override fun getModuleScript(
        context: Context,
        moduleId: String,
        uri: URI?,
        base: URI?,
        paths: Scriptable?,
    ): ModuleScript? {
        val resolved = when (uri) {
            null -> resolveModuleId(moduleId)
            else -> resolveModuleUri(uri)
        } ?: return null

        val key = ModuleCacheKey(
            assetLoader.namespace,
            RhinoCompilationProfile.from(context),
            resolved.uri,
        )
        val compileModule = {
            val reader = try {
                assetLoader.open(resolved.assetPath)
            } catch (_: FileNotFoundException) {
                null
            }
            reader?.use {
                ModuleScript(
                    context.compileReader(it, resolved.uri.toString(), 1, null),
                    resolved.uri,
                    assetBaseUri,
                )
            }
        }
        return softModuleScripts.getOrPut(key, compileModule)
    }

    private fun resolveModuleId(moduleId: String): ResolvedAsset? {
        val unixId = moduleId.replace('\\', '/')
        if (unixId.startsWith('/') || URI_SCHEME.matchesAt(unixId, 0)) {
            return null
        }
        val relativePath = when {
            unixId.startsWith("$assetRoot/") -> unixId.substring(assetRoot.length + 1)
            else -> unixId
        }
        return resolveRelativePath(relativePath)
    }

    private fun resolveModuleUri(uri: URI): ResolvedAsset? {
        if (!uri.scheme.equals("file", ignoreCase = true) || !uri.authority.isNullOrEmpty()) {
            return null
        }
        val normalizedPath = uri.normalize().path ?: return null
        if (!normalizedPath.startsWith(assetBasePath)) {
            return null
        }
        return resolveRelativePath(normalizedPath.substring(assetBasePath.length))
    }

    private fun resolveRelativePath(relativePath: String): ResolvedAsset? {
        val segments = ArrayDeque<String>()
        relativePath.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        if (segments.isEmpty()) {
            return null
        }
        var normalized = segments.joinToString("/")
        if (!normalized.endsWith(JAVASCRIPT_EXTENSION, ignoreCase = true)) {
            normalized += JAVASCRIPT_EXTENSION
        }
        val assetPath = "$assetRoot/$normalized"
        return ResolvedAsset(normalized, assetPath, assetUri(assetPath))
    }

    private data class ResolvedAsset(val relativePath: String, val assetPath: String, val uri: URI)

    companion object {

        private const val JAVASCRIPT_EXTENSION = ".js"
        private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
        private fun normalizeAssetRoot(assetDirPath: String): String {
            return assetDirPath.replace('\\', '/').trim('/')
                .also { require(it.isNotEmpty()) { "Asset module root must not be empty" } }
        }
    }
}

/** A reader namespace is stable for the lifetime of one installed APK process. */
internal interface RhinoAssetLoader {

    val namespace: String

    fun open(assetPath: String): Reader
}

internal class AndroidRhinoAssetLoader(context: AndroidContext) : RhinoAssetLoader {

    private val applicationContext = context.applicationContext ?: context

    override val namespace: String = applicationContext.applicationInfo.let { appInfo ->
        buildString {
            append(applicationContext.packageName)
            append('|')
            append(appInfo.sourceDir.orEmpty())
            appInfo.splitSourceDirs?.forEach {
                append('|')
                append(it)
            }
        }
    }

    override fun open(assetPath: String): Reader {
        return InputStreamReader(applicationContext.assets.open(assetPath), StandardCharsets.UTF_8)
    }
}

internal object ProcessWideRhinoAssetCache {

    private val bootstrapScripts = ConcurrentHashMap<BootstrapCacheKey, Script>()
    private val bootstrapLocks = Array(LOCK_COUNT) { Any() }

    fun getBootstrapScript(
        context: Context,
        assetLoader: RhinoAssetLoader,
        assetPath: String,
        sourceName: String,
    ): Script {
        val normalizedAssetPath = normalizeBootstrapPath(assetPath)
        val key = BootstrapCacheKey(
            assetLoader.namespace,
            RhinoCompilationProfile.from(context),
            assetUri(normalizedAssetPath),
            sourceName,
        )
        bootstrapScripts[key]?.let { return it }
        synchronized(bootstrapLocks[stripeIndex(key)]) {
            bootstrapScripts[key]?.let { return it }
            return assetLoader.open(normalizedAssetPath).use {
                context.compileReader(it, sourceName, 1, null)
            }.also { bootstrapScripts[key] = it }
        }
    }

    internal fun clearForTests() {
        bootstrapScripts.clear()
        softModuleScripts.clear()
    }

    private fun normalizeBootstrapPath(assetPath: String): String {
        val segments = ArrayDeque<String>()
        assetPath.replace('\\', '/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) {
                    throw IllegalArgumentException("Asset path escapes the APK assets root: $assetPath")
                } else {
                    segments.removeLast()
                }
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/")
            .also { require(it.isNotEmpty()) { "Asset path must not be empty" } }
    }
}

internal data class RhinoCompilationProfile(
    val languageVersion: Int,
    val optimizationLevel: Int,
    val interpretedMode: Boolean,
    val generatingDebug: Boolean,
    val generatingSource: Boolean,
) {

    companion object {

        fun from(context: Context) = RhinoCompilationProfile(
            context.languageVersion,
            context.evaluationMethod.level(),
            context.isInterpretedMode,
            context.isGeneratingDebug,
            context.isGeneratingSource,
        )
    }
}

private data class BootstrapCacheKey(
    val namespace: String,
    val profile: RhinoCompilationProfile,
    val uri: URI,
    val sourceName: String,
)

private data class ModuleCacheKey(
    val namespace: String,
    val profile: RhinoCompilationProfile,
    val uri: URI,
)

private val softModuleScripts = SingleFlightSoftCache<ModuleCacheKey, ModuleScript>()

private class SingleFlightSoftCache<K : Any, V : Any> {

    private val queue = ReferenceQueue<V>()
    private val values = ConcurrentHashMap<K, CacheReference<K, V>>()
    private val locks = Array(LOCK_COUNT) { Any() }

    fun getOrPut(key: K, producer: () -> V?): V? {
        drainQueue()
        values[key]?.get()?.let { return it }
        synchronized(locks[stripeIndex(key)]) {
            values[key]?.get()?.let { return it }
            val value = producer() ?: return null
            values[key] = CacheReference(key, value, queue)
            return value
        }
    }

    fun clear() {
        values.clear()
        drainQueue()
    }

    private fun drainQueue() {
        while (true) {
            @Suppress("UNCHECKED_CAST")
            val reference = queue.poll() as CacheReference<K, V>? ?: return
            values.remove(reference.key, reference)
        }
    }

    private class CacheReference<K : Any, V : Any>(
        val key: K,
        value: V,
        queue: ReferenceQueue<V>,
    ) : SoftReference<V>(value, queue)
}

private const val LOCK_COUNT = 32

private fun stripeIndex(key: Any): Int = (key.hashCode() and Int.MAX_VALUE) % LOCK_COUNT

private fun assetUri(assetPath: String): URI {
    return URI("file", "", "/android_asset/$assetPath", null, null)
}
