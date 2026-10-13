package org.autojs.autojs.core.plugin.editor

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import dalvik.system.PathClassLoader
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.editor.api.EditorPluginCallback
import org.autojs.plugin.editor.api.EditorPluginContract
import org.autojs.plugin.editor.api.EditorPluginEntrypoint
import org.autojs.plugin.editor.api.EditorPluginSession
import org.autojs.plugin.editor.api.EditorPluginSessionConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Discovers and loads the official Ace editor plugin into the host process. */
object AceEditorPluginHost {

    private const val TAG = "AceEditorPluginHost"

    data class Candidate(
        val serviceInfo: ServiceInfo,
        val applicationInfo: ApplicationInfo,
        val packageVersionCode: Long,
        val packageLastUpdateTime: Long,
        val entrypointClassName: String,
        val contractVersion: Int,
        val requiresHostVersion: Long?,
        val maxHostVersion: Long?,
        val enabled: Boolean,
        val authorizedState: PluginAuthorizedState,
        val sameSignerAsHost: Boolean,
    )

    data class Discovered(
        val serviceInfo: ServiceInfo,
        val candidate: Candidate?,
        val error: EditorPluginLoadException?,
    )

    /**
     * The plugin code a class loader was created for. Installing or updating the plugin changes its version, update
     * time or APK paths.
     *
     * zh-CN: 类加载器所对应的插件代码. 安装或更新插件会改变其版本, 更新时间或 APK 路径.
     */
    private data class CodeIdentity(
        val packageName: String,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val codePaths: List<String>,
        val nativeLibraryDir: String?,
        val entrypointClassName: String,
    )

    private class LoadedCode(
        val identity: CodeIdentity,
        val classLoader: ClassLoader,
        val entrypoint: EditorPluginEntrypoint,
    )

    // The code of the last loaded plugin, reused while its identity stays the same; eligibility and package state are
    // still checked on every load. A new PathClassLoader for every editor opened the plugin's dex files again each
    // time: the plugin's compiled code does not match the host's class loader context, so ART extracted the dex from
    // the APK and verified the plugin classes again, about 270 ms of a 900 ms editor start on a Helio G85.
    // zh-CN: 最近一次加载的插件代码, 标识不变时复用; 每次加载仍会检查插件的资格与包状态. 每个编辑器都新建 PathClassLoader 时,
    // 插件的 dex 每次都要重新打开: 插件的预编译代码与宿主的类加载器上下文不匹配, ART 从 APK 中解压 dex 并重新校验插件类,
    // 在 Helio G85 上约占 900 ms 编辑器启动时间中的 270 ms.
    @Volatile
    private var loadedCode: LoadedCode? = null

    class LoadedPlugin internal constructor(
        val candidate: Candidate,
        val packageContext: Context,
        val pluginContext: Context,
        val classLoader: ClassLoader,
        val entrypoint: EditorPluginEntrypoint,
    ) {
        fun createSession(
            hostContext: Context,
            config: EditorPluginSessionConfig,
            callback: EditorPluginCallback,
        ): LoadedSession {
            ensureMainThread(candidate.serviceInfo.packageName)
            val session = try {
                entrypoint.createSession(hostContext, pluginContext, config, callback)
            } catch (e: Exception) {
                throw EditorPluginLoadException(
                    code = EditorPluginLoadError.SESSION_CREATION_FAILED,
                    packageName = candidate.serviceInfo.packageName,
                    message = "Editor plugin failed to create a session.",
                    cause = e,
                )
            } catch (e: LinkageError) {
                throw EditorPluginLoadException(
                    code = EditorPluginLoadError.SESSION_CREATION_FAILED,
                    packageName = candidate.serviceInfo.packageName,
                    message = "Editor plugin session has an incompatible binary dependency.",
                    cause = e,
                )
            }
            return LoadedSession(this, session)
        }
    }

    class LoadedSession internal constructor(
        val plugin: LoadedPlugin,
        val session: EditorPluginSession,
    ) {
        private val destroyed = AtomicBoolean(false)

        fun destroy() {
            ensureMainThread(plugin.candidate.serviceInfo.packageName)
            if (destroyed.compareAndSet(false, true)) {
                try {
                    session.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Editor plugin failed while destroying its session.", e)
                } catch (e: LinkageError) {
                    Log.e(TAG, "Editor plugin linkage failed while destroying its session.", e)
                }
            }
        }
    }

    fun discover(
        context: Context,
        packageName: String? = null,
    ): List<Discovered> {
        val targetPackage = packageName ?: EditorPluginContract.DEFAULT_PACKAGE_NAME
        val services = queryServices(context.packageManager, targetPackage)
        return services.map { serviceInfo ->
            try {
                val candidate = resolveCandidate(context, serviceInfo)
                Discovered(
                    serviceInfo = serviceInfo,
                    candidate = candidate,
                    error = candidateEligibilityError(candidate),
                )
            } catch (e: EditorPluginLoadException) {
                Discovered(serviceInfo = serviceInfo, candidate = null, error = e)
            } catch (e: Exception) {
                Discovered(
                    serviceInfo = serviceInfo,
                    candidate = null,
                    error = EditorPluginLoadException(
                        code = EditorPluginLoadError.DISCOVERY_FAILED,
                        packageName = serviceInfo.packageName,
                        message = "Unable to inspect the Ace editor plugin candidate.",
                        cause = e,
                    ),
                )
            }
        }
    }

    fun isAvailable(
        context: Context,
        packageName: String? = null,
    ): Boolean = runCatching {
        discover(context, packageName).any { it.candidate != null && it.error == null }
    }.getOrDefault(false)

    @Throws(EditorPluginLoadException::class)
    fun load(
        context: Context,
        packageName: String? = null,
    ): LoadedPlugin {
        val targetPackage = packageName ?: EditorPluginContract.DEFAULT_PACKAGE_NAME
        val discovered = try {
            discover(context, targetPackage)
        } catch (e: Exception) {
            throw EditorPluginLoadException(
                code = EditorPluginLoadError.DISCOVERY_FAILED,
                packageName = targetPackage,
                message = "Unable to query the Ace editor plugin.",
                cause = e,
            )
        }
        val candidate = discovered
            .asSequence()
            .filter { it.error == null }
            .mapNotNull { it.candidate }
            .maxByOrNull { it.packageVersionCode }
            ?: throw discovered.firstNotNullOfOrNull { it.error }
            ?: EditorPluginLoadException(
                code = EditorPluginLoadError.PLUGIN_NOT_FOUND,
                packageName = targetPackage,
                message = "No compatible Ace editor plugin was found.",
            )

        // Re-resolve package, component, metadata, version and signer immediately before executing
        // foreign code. This avoids carrying mutable policy or package state across discovery.
        val refreshed = try {
            discover(context, targetPackage)
        } catch (e: Exception) {
            throw EditorPluginLoadException(
                code = EditorPluginLoadError.DISCOVERY_FAILED,
                packageName = targetPackage,
                message = "Unable to revalidate the Ace editor plugin.",
                cause = e,
            )
        }.firstOrNull {
            it.serviceInfo.name == candidate.serviceInfo.name
        } ?: throw EditorPluginLoadException(
            code = EditorPluginLoadError.PLUGIN_NOT_FOUND,
            packageName = targetPackage,
            message = "The Ace editor plugin changed during discovery.",
        )
        refreshed.error?.let { throw it }
        val currentCandidate = refreshed.candidate ?: throw EditorPluginLoadException(
            code = EditorPluginLoadError.DISCOVERY_FAILED,
            packageName = targetPackage,
            message = "Unable to revalidate the Ace editor plugin candidate.",
        )
        candidateEligibilityError(currentCandidate)?.let { throw it }
        return loadCandidate(context, currentCandidate)
    }

    @Throws(EditorPluginLoadException::class)
    fun createSession(
        context: Context,
        config: EditorPluginSessionConfig,
        callback: EditorPluginCallback,
        packageName: String? = null,
    ): LoadedSession = load(context, packageName).createSession(context, config, callback)

    private fun resolveCandidate(context: Context, serviceInfo: ServiceInfo): Candidate {
        val packageName = serviceInfo.packageName
        val packageManager = context.packageManager
        val applicationInfo = getApplicationInfo(packageManager, packageName)
        val packageInfo = getPackageInfo(packageManager, packageName)
        val serviceMetaData = serviceInfo.metaData
        val applicationMetaData = applicationInfo.metaData

        val rawEntrypoint = serviceMetaData?.getString(EditorPluginContract.META_DATA_ENTRYPOINT)
            ?.takeIf(String::isNotBlank)
            ?: applicationMetaData?.getString(EditorPluginContract.META_DATA_ENTRYPOINT)
                ?.takeIf(String::isNotBlank)
            ?: throw EditorPluginLoadException(
                code = EditorPluginLoadError.ENTRYPOINT_METADATA_MISSING,
                packageName = packageName,
                message = "Missing ${EditorPluginContract.META_DATA_ENTRYPOINT} metadata.",
            )
        val contractVersion = metadataLong(
            serviceInfo,
            applicationInfo,
            EditorPluginContract.META_DATA_CONTRACT_VERSION,
        )?.takeIf { it <= Int.MAX_VALUE }?.toInt()
            ?: throw EditorPluginLoadException(
                code = EditorPluginLoadError.CONTRACT_METADATA_INVALID,
                packageName = packageName,
                message = "Missing or invalid ${EditorPluginContract.META_DATA_CONTRACT_VERSION} metadata.",
            )
        val requiresHostVersion = metadataLong(
            serviceInfo,
            applicationInfo,
            EditorPluginContract.META_DATA_REQUIRES_HOST_VERSION,
        )
        val maxHostVersion = metadataLong(
            serviceInfo,
            applicationInfo,
            EditorPluginContract.META_DATA_MAX_HOST_VERSION,
        )
        val trustInfo = PluginTrustManager.resolveTrustInfo(context, packageName)

        return Candidate(
            serviceInfo = serviceInfo,
            applicationInfo = applicationInfo,
            packageVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            packageLastUpdateTime = packageInfo.lastUpdateTime,
            entrypointClassName = normalizeClassName(packageName, rawEntrypoint),
            contractVersion = contractVersion,
            requiresHostVersion = requiresHostVersion,
            maxHostVersion = maxHostVersion,
            enabled = applicationInfo.enabled && serviceInfo.enabled &&
                    PluginEnableStore.isEnabled(context, packageName, true),
            authorizedState = trustInfo.authorizedState,
            sameSignerAsHost = packageManager.checkSignatures(context.packageName, packageName) ==
                    PackageManager.SIGNATURE_MATCH,
        )
    }

    private fun candidateEligibilityError(candidate: Candidate): EditorPluginLoadException? {
        val code = AceEditorPluginPolicy.rejectionCode(
            enabled = candidate.enabled,
            authorizedState = candidate.authorizedState,
            sameSignerAsHost = candidate.sameSignerAsHost,
            contractVersion = candidate.contractVersion,
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            requiresHostVersion = candidate.requiresHostVersion,
            maxHostVersion = candidate.maxHostVersion,
        ) ?: return null
        val packageName = candidate.serviceInfo.packageName
        val message = when (code) {
            EditorPluginLoadError.PLUGIN_DISABLED -> "The Ace editor plugin is disabled."
            EditorPluginLoadError.IN_PROCESS_TRUST_REQUIRED ->
                "In-process editor plugins must share the host signer or be OFFICIAL/TRUSTED; " +
                    "${candidate.authorizedState} is not sufficient."
            EditorPluginLoadError.CONTRACT_VERSION_UNSUPPORTED ->
                "Unsupported editor contract ${candidate.contractVersion}; host supports " +
                    "${EditorPluginContract.MIN_SUPPORTED_VERSION}..${EditorPluginContract.MAX_SUPPORTED_VERSION}."
            EditorPluginLoadError.HOST_VERSION_TOO_LOW ->
                "Editor plugin requires host version ${candidate.requiresHostVersion}; current version is ${BuildConfig.VERSION_CODE}."
            EditorPluginLoadError.HOST_VERSION_TOO_HIGH ->
                "Editor plugin supports host version ${candidate.maxHostVersion} or lower; current version is ${BuildConfig.VERSION_CODE}."
            else -> "The Ace editor plugin is not eligible for in-process loading."
        }
        return EditorPluginLoadException(code, packageName, message)
    }

    private fun loadCandidate(context: Context, candidate: Candidate): LoadedPlugin {
        val packageName = candidate.serviceInfo.packageName
        val packageContext = try {
            context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        } catch (e: PackageManager.NameNotFoundException) {
            throw EditorPluginLoadException(
                EditorPluginLoadError.PACKAGE_CONTEXT_FAILED,
                packageName,
                "Unable to create the editor plugin resource context.",
                e,
            )
        }
        val codePaths = buildList {
            candidate.applicationInfo.sourceDir?.takeIf(String::isNotBlank)?.let(::add)
            candidate.applicationInfo.splitSourceDirs?.filter(String::isNotBlank)?.let(::addAll)
        }.distinct()
        if (codePaths.isEmpty() || codePaths.any { !File(it).isFile }) {
            throw EditorPluginLoadException(
                EditorPluginLoadError.CODE_PATH_INVALID,
                packageName,
                "The editor plugin APK path is missing or invalid.",
            )
        }
        val identity = CodeIdentity(
            packageName = packageName,
            versionCode = candidate.packageVersionCode,
            lastUpdateTime = candidate.packageLastUpdateTime,
            codePaths = codePaths,
            nativeLibraryDir = candidate.applicationInfo.nativeLibraryDir,
            entrypointClassName = candidate.entrypointClassName,
        )
        val code = synchronized(this) {
            loadedCode?.takeIf { it.identity == identity }
                ?: loadCode(context, identity).also { loadedCode = it }
        }
        val classLoader = code.classLoader
        val entrypoint = code.entrypoint
        if (entrypoint.contractVersion != candidate.contractVersion ||
            entrypoint.contractVersion !in EditorPluginContract.MIN_SUPPORTED_VERSION..
                EditorPluginContract.MAX_SUPPORTED_VERSION
        ) {
            throw EditorPluginLoadException(
                EditorPluginLoadError.ENTRYPOINT_CONTRACT_MISMATCH,
                packageName,
                "Entrypoint contract ${entrypoint.contractVersion} does not match its manifest or " +
                    "host range ${EditorPluginContract.MIN_SUPPORTED_VERSION}.." +
                    "${EditorPluginContract.MAX_SUPPORTED_VERSION}.",
            )
        }
        val localizedPackageContext = packageContext.createConfigurationContext(
            Configuration(context.resources.configuration),
        )
        val pluginContext = PluginResourceContext(localizedPackageContext, classLoader)
        return LoadedPlugin(candidate, packageContext, pluginContext, classLoader, entrypoint)
    }

    private fun loadCode(context: Context, identity: CodeIdentity): LoadedCode {
        val packageName = identity.packageName
        val parent = EditorPluginEntrypoint::class.java.classLoader
            ?: context.classLoader
            ?: ClassLoader.getSystemClassLoader()
        val classLoader = try {
            PathClassLoader(
                identity.codePaths.joinToString(File.pathSeparator),
                identity.nativeLibraryDir,
                parent,
            )
        } catch (e: RuntimeException) {
            throw EditorPluginLoadException(
                EditorPluginLoadError.CLASS_LOADER_FAILED,
                packageName,
                "Unable to create the editor plugin classloader.",
                e,
            )
        }
        val entrypointClass = try {
            Class.forName(identity.entrypointClassName, true, classLoader)
        } catch (e: ClassNotFoundException) {
            throw EditorPluginLoadException(
                EditorPluginLoadError.ENTRYPOINT_CLASS_NOT_FOUND,
                packageName,
                "Editor plugin entrypoint was not found: ${identity.entrypointClassName}.",
                e,
            )
        } catch (e: LinkageError) {
            throw EditorPluginLoadException(
                EditorPluginLoadError.ENTRYPOINT_LINKAGE_FAILED,
                packageName,
                "Editor plugin entrypoint has an incompatible binary dependency.",
                e,
            )
        }
        if (!EditorPluginEntrypoint::class.java.isAssignableFrom(entrypointClass)) {
            throw EditorPluginLoadException(
                EditorPluginLoadError.ENTRYPOINT_TYPE_MISMATCH,
                packageName,
                "Entrypoint must implement EditorPluginEntrypoint and package editor-api as compileOnly.",
            )
        }
        return LoadedCode(identity, classLoader, instantiateEntrypoint(entrypointClass, packageName))
    }

    private fun instantiateEntrypoint(
        entrypointClass: Class<*>,
        packageName: String,
    ): EditorPluginEntrypoint {
        val instance = try {
            runCatching { entrypointClass.getField("INSTANCE").get(null) }
                .getOrElse { entrypointClass.getDeclaredConstructor().newInstance() }
        } catch (e: ReflectiveOperationException) {
            throw EditorPluginLoadException(
                EditorPluginLoadError.ENTRYPOINT_INSTANTIATION_FAILED,
                packageName,
                "Editor plugin entrypoint must be a Kotlin object or expose a public no-argument constructor.",
                e,
            )
        }
        return instance as EditorPluginEntrypoint
    }

    private fun queryServices(packageManager: PackageManager, packageName: String): List<ServiceInfo> {
        val intent = Intent(EditorPluginContract.ACTION_EDITOR).apply {
            addCategory(EditorPluginContract.CATEGORY_ACE)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(packageName)
        }
        val flags = (PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, flags.toInt())
        }
        return resolved.mapNotNull { it.serviceInfo }
    }

    private fun getApplicationInfo(packageManager: PackageManager, packageName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(
                    (PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS).toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }
    }

    private fun getPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun metadataLong(
        serviceInfo: ServiceInfo,
        applicationInfo: ApplicationInfo,
        key: String,
    ): Long? = PluginCapabilityResolver.coerceLong(
        serviceInfo.metaData?.get(key) ?: applicationInfo.metaData?.get(key),
    )

    private fun normalizeClassName(packageName: String, className: String): String = when {
        className.startsWith('.') -> packageName + className
        '.' !in className -> "$packageName.$className"
        else -> className
    }

    private fun ensureMainThread(packageName: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw EditorPluginLoadException(
                EditorPluginLoadError.MAIN_THREAD_REQUIRED,
                packageName,
                "Editor plugin sessions must be created on the Android main thread.",
            )
        }
    }

    private class PluginResourceContext(
        base: Context,
        private val pluginClassLoader: ClassLoader,
    ) : ContextWrapper(base) {
        override fun getClassLoader(): ClassLoader = pluginClassLoader
    }
}

enum class EditorPluginLoadError {
    PLUGIN_NOT_FOUND,
    DISCOVERY_FAILED,
    PLUGIN_DISABLED,
    IN_PROCESS_TRUST_REQUIRED,
    ENTRYPOINT_METADATA_MISSING,
    CONTRACT_METADATA_INVALID,
    CONTRACT_VERSION_UNSUPPORTED,
    HOST_VERSION_TOO_LOW,
    HOST_VERSION_TOO_HIGH,
    PACKAGE_CONTEXT_FAILED,
    CODE_PATH_INVALID,
    CLASS_LOADER_FAILED,
    ENTRYPOINT_CLASS_NOT_FOUND,
    ENTRYPOINT_LINKAGE_FAILED,
    ENTRYPOINT_TYPE_MISMATCH,
    ENTRYPOINT_INSTANTIATION_FAILED,
    ENTRYPOINT_CONTRACT_MISMATCH,
    MAIN_THREAD_REQUIRED,
    SESSION_CREATION_FAILED,
}

class EditorPluginLoadException(
    val code: EditorPluginLoadError,
    val packageName: String?,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal object AceEditorPluginPolicy {

    fun rejectionCode(
        enabled: Boolean,
        authorizedState: PluginAuthorizedState,
        sameSignerAsHost: Boolean,
        contractVersion: Int,
        hostVersionCode: Long,
        requiresHostVersion: Long?,
        maxHostVersion: Long?,
    ): EditorPluginLoadError? = when {
        !enabled -> EditorPluginLoadError.PLUGIN_DISABLED
        !sameSignerAsHost &&
                authorizedState != PluginAuthorizedState.OFFICIAL &&
                authorizedState != PluginAuthorizedState.TRUSTED ->
            EditorPluginLoadError.IN_PROCESS_TRUST_REQUIRED
        contractVersion !in EditorPluginContract.MIN_SUPPORTED_VERSION..
            EditorPluginContract.MAX_SUPPORTED_VERSION ->
            EditorPluginLoadError.CONTRACT_VERSION_UNSUPPORTED
        !PluginCapabilityResolver.isHostVersionCompatible(hostVersionCode, requiresHostVersion) ->
            EditorPluginLoadError.HOST_VERSION_TOO_LOW
        maxHostVersion != null && hostVersionCode > maxHostVersion ->
            EditorPluginLoadError.HOST_VERSION_TOO_HIGH
        else -> null
    }
}
