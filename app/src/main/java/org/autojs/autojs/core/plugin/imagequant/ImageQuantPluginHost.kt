package org.autojs.autojs.core.plugin.imagequant

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.AbstractAutoJs
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.runtime.api.PngQuantBridge
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.imagequant.api.ImageQuantPluginContract
import java.io.File

/** Loads the version-locked Image Quantization JNI runtime supplied by the official plugin. */
object ImageQuantPluginHost {

    private const val TAG = "ImageQuantPluginHost"
    private const val PLUGIN_LABEL = "Image Quantization"
    private val loadLock = Any()

    @Volatile
    private var loadedRuntime: LoadedRuntime? = null

    data class Candidate(
        val serviceInfo: ServiceInfo,
        val applicationInfo: ApplicationInfo,
        val packageVersionCode: Long,
        val packageLastUpdateTime: Long,
        val contractVersion: Int,
        val libimagequantVersion: String,
        val libpngVersion: String,
        val nativeLibraryName: String,
        val nativeNdkVersion: String,
        val nativeStl: String,
        val nativeAbiVersion: Int,
        val nativeAbis: String,
        val requiresHostVersion: Long?,
        val maxHostVersion: Long?,
        val enabled: Boolean,
        val authorizedState: PluginAuthorizedState,
        val sameSignerAsHost: Boolean,
    ) {
        val packageIdentity: String
            get() = "${serviceInfo.packageName}-$packageVersionCode-$packageLastUpdateTime"

        val codePaths: List<String>
            get() = buildList {
                applicationInfo.sourceDir?.takeIf(String::isNotBlank)?.let(::add)
                applicationInfo.splitSourceDirs?.filter(String::isNotBlank)?.let(::addAll)
            }.distinct()
    }

    data class Discovered(
        val serviceInfo: ServiceInfo,
        val candidate: Candidate?,
        val error: ImageQuantPluginLoadException?,
    )

    data class LoadedRuntime(
        val origin: Origin,
        val libimagequantVersion: String,
        val libpngVersion: String,
        val runtimeProbe: String,
        val packageName: String? = null,
        val packageIdentity: String? = null,
        val packageVersionCode: Long? = null,
        val abi: String? = null,
        val nativeLibraryPath: String? = null,
        val nativeNdkVersion: String? = null,
        val nativeStl: String? = null,
    )

    enum class Origin {
        BUNDLED,
        PLUGIN,
    }

    fun discover(
        context: Context,
        packageName: String = ImageQuantPluginContract.DEFAULT_PACKAGE_NAME,
    ): List<Discovered> = queryServices(context.packageManager, packageName).map { serviceInfo ->
        runCatching { resolveCandidate(context, serviceInfo) }
            .fold(
                onSuccess = { candidate ->
                    Discovered(
                        serviceInfo,
                        candidate,
                        ImageQuantPluginPolicy.rejectionCode(
                            enabled = candidate.enabled,
                            authorizedState = candidate.authorizedState,
                            sameSignerAsHost = candidate.sameSignerAsHost,
                            contractVersion = candidate.contractVersion,
                            libimagequantVersion = candidate.libimagequantVersion,
                            libpngVersion = candidate.libpngVersion,
                            nativeLibraryName = candidate.nativeLibraryName,
                            nativeNdkVersion = candidate.nativeNdkVersion,
                            nativeStl = candidate.nativeStl,
                            nativeAbiVersion = candidate.nativeAbiVersion,
                            nativeAbis = candidate.nativeAbis,
                            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                            requiresHostVersion = candidate.requiresHostVersion,
                            maxHostVersion = candidate.maxHostVersion,
                        )?.let { code ->
                            buildLoadException(
                                context = context,
                                code = code,
                                packageName = candidate.serviceInfo.packageName,
                                requiresHostVersion = candidate.requiresHostVersion,
                                maxHostVersion = candidate.maxHostVersion,
                            )
                        },
                    )
                },
                onFailure = { error ->
                    Discovered(
                        serviceInfo,
                        null,
                        error as? ImageQuantPluginLoadException ?: ImageQuantPluginLoadException(
                            ImageQuantPluginLoadError.DISCOVERY_FAILED,
                            "Unable to inspect the Image Quantization plugin",
                            error,
                        ),
                    )
                },
            )
    }

    fun isAvailable(context: Context): Boolean = discover(context).any { discovered ->
        val candidate = discovered.candidate
        discovered.error == null && candidate != null && ImageQuantNativeLibraryStager.hasCompatibleLibrary(
            apkPaths = candidate.codePaths,
            supportedAbis = processSupportedAbis(),
            nativeLibraryFileName = ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME,
        )
    }

    /** Applies runtime policy to APK Builder without limiting it to the current process ABI. */
    fun filterEligiblePackagingServices(
        context: Context,
        services: List<ServiceInfo>,
    ): List<ServiceInfo> {
        val eligibleComponents = discover(context)
            .asSequence()
            .filter { it.error == null }
            .map { it.serviceInfo.packageName to it.serviceInfo.name }
            .toSet()
        return services.filter { serviceInfo ->
            serviceInfo.packageName == ImageQuantPluginContract.DEFAULT_PACKAGE_NAME &&
                    (serviceInfo.packageName to serviceInfo.name) in eligibleComponents
        }
    }

    /**
     * Revalidates a loaded plugin before every JNI call. Native libraries cannot be unloaded, so
     * an uninstall, disable, authorization change, or package replacement rejects new work while
     * allowing the already mapped code to remain untouched until process exit.
     */
    private fun enforceLifecycle(context: Context, runtime: LoadedRuntime) {
        if (AbstractAutoJs.isInrt) return
        if (runtime.origin == Origin.BUNDLED) return

        val discovered = discover(context)
        val current = discovered
            .asSequence()
            .filter { it.error == null }
            .mapNotNull(Discovered::candidate)
            .maxByOrNull(Candidate::packageVersionCode)
        val discoveryError = discovered.firstNotNullOfOrNull(Discovered::error)
        val rejection = ImageQuantRuntimeLifecyclePolicy.rejectionCode(
            bundled = false,
            loadedPackageIdentity = runtime.packageIdentity,
            currentPackageIdentity = current?.packageIdentity,
            currentError = discoveryError?.code,
        ) ?: return

        if (discoveryError?.code == rejection) throw discoveryError
        throw buildLoadException(
            context = context,
            code = rejection,
            packageName = runtime.packageName ?: ImageQuantPluginContract.DEFAULT_PACKAGE_NAME,
        )
    }

    @JvmStatic
    fun ensureLoaded(context: Context): LoadedRuntime {
        loadedRuntime?.let { runtime ->
            enforceLifecycle(context, runtime)
            return runtime
        }
        synchronized(loadLock) {
            loadedRuntime?.let { runtime ->
                enforceLifecycle(context, runtime)
                return runtime
            }
            if (AbstractAutoJs.isInrt) {
                tryLoadBundledRuntime()?.let { bundled ->
                    loadedRuntime = bundled
                    return bundled
                }
                throw ImageQuantPluginLoadException(
                    ImageQuantPluginLoadError.BUNDLED_RUNTIME_MISSING,
                    "Image Quantization was not included in this packaged application",
                )
            }

            val candidate = selectCandidate(context)
            val staged = stageCandidate(context, candidate)

            try {
                System.load(staged.file.absolutePath)
            } catch (error: LinkageError) {
                throw ImageQuantPluginLoadException(
                    ImageQuantPluginLoadError.NATIVE_LIBRARY_LOAD_FAILED,
                    "Unable to load the Image Quantization plugin runtime for ${staged.abi}",
                    error,
                )
            } catch (error: SecurityException) {
                throw ImageQuantPluginLoadException(
                    ImageQuantPluginLoadError.NATIVE_LIBRARY_LOAD_FAILED,
                    "Android denied loading the Image Quantization plugin runtime for ${staged.abi}",
                    error,
                )
            }
            val runtimeProbe = verifyRuntimeProbe()
            return LoadedRuntime(
                origin = Origin.PLUGIN,
                libimagequantVersion = candidate.libimagequantVersion,
                libpngVersion = candidate.libpngVersion,
                runtimeProbe = runtimeProbe,
                packageName = candidate.serviceInfo.packageName,
                packageIdentity = candidate.packageIdentity,
                packageVersionCode = candidate.packageVersionCode,
                abi = staged.abi,
                nativeLibraryPath = staged.file.absolutePath,
                nativeNdkVersion = candidate.nativeNdkVersion,
                nativeStl = candidate.nativeStl,
            ).also { runtime ->
                loadedRuntime = runtime
                Log.i(
                    TAG,
                    "Loaded Image Quantization ${runtime.libimagequantVersion} with libpng ${runtime.libpngVersion} " +
                            "from ${runtime.packageName} for ${runtime.abi}",
                )
            }
        }
    }

    private fun tryLoadBundledRuntime(): LoadedRuntime? {
        try {
            System.loadLibrary(ImageQuantPluginContract.NATIVE_LIBRARY_NAME)
        } catch (_: UnsatisfiedLinkError) {
            return null
        }
        val runtimeProbe = verifyRuntimeProbe()
        return LoadedRuntime(
            origin = Origin.BUNDLED,
            libimagequantVersion = ImageQuantPluginContract.EXPECTED_LIBIMAGEQUANT_VERSION,
            libpngVersion = ImageQuantPluginContract.EXPECTED_LIBPNG_VERSION,
            runtimeProbe = runtimeProbe,
        ).also {
            Log.i(TAG, "Loaded bundled Image Quantization ${it.libimagequantVersion}")
        }
    }

    private fun selectCandidate(context: Context): Candidate {
        val discovered = discover(context)
        return discovered
            .asSequence()
            .filter { it.error == null }
            .mapNotNull(Discovered::candidate)
            .maxByOrNull(Candidate::packageVersionCode)
            ?: throw discovered.firstNotNullOfOrNull(Discovered::error)
                ?: buildLoadException(context, ImageQuantPluginLoadError.PLUGIN_NOT_FOUND)
    }

    private fun stageCandidate(
        context: Context,
        candidate: Candidate,
    ): ImageQuantNativeLibraryStager.StagedLibrary = try {
        ImageQuantNativeLibraryStager.stage(
            apkPaths = candidate.codePaths,
            destinationRoot = runtimeCacheRoot(context),
            packageIdentity = candidate.packageIdentity,
            supportedAbis = processSupportedAbis(),
            nativeLibraryFileName = ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME,
        )
    } catch (error: ImageQuantPluginLoadException) {
        throw error
    } catch (error: Exception) {
        throw ImageQuantPluginLoadException(
            ImageQuantPluginLoadError.NATIVE_LIBRARY_EXTRACTION_FAILED,
            "Unable to stage the Image Quantization plugin runtime",
            error,
        )
    }

    private fun verifyRuntimeProbe(): String {
        val actual = try {
            PngQuantBridge.probeLoadedRuntime()
        } catch (error: LinkageError) {
            throw ImageQuantPluginLoadException(
                ImageQuantPluginLoadError.NATIVE_LIBRARY_LOAD_FAILED,
                "Image Quantization native runtime did not expose the expected Java/JNI ABI",
                error,
            )
        }
        if (actual != ImageQuantPluginContract.RUNTIME_PROBE_VALUE) {
            throw ImageQuantPluginLoadException(
                ImageQuantPluginLoadError.RUNTIME_PROBE_MISMATCH,
                "Image Quantization runtime reports '$actual'; expected '${ImageQuantPluginContract.RUNTIME_PROBE_VALUE}'",
            )
        }
        return actual
    }

    private fun processSupportedAbis(): List<String> = ImageQuantProcessAbiPolicy.select(
        is64Bit = android.os.Process.is64Bit(),
        supported32BitAbis = Build.SUPPORTED_32_BIT_ABIS.toList(),
        supported64BitAbis = Build.SUPPORTED_64_BIT_ABIS.toList(),
    )

    private fun runtimeCacheRoot(context: Context): File {
        val processName = (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { Application.getProcessName() }.getOrNull()
                } else {
                    null
                }
                )
            ?.takeIf(String::isNotBlank)
            ?: runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000') }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
            ?: context.packageName
        val safeProcessName = processName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val bitness = if (android.os.Process.is64Bit()) "64" else "32"
        return File(context.codeCacheDir, "imagequant-runtime/$safeProcessName-$bitness")
    }

    private fun resolveCandidate(context: Context, serviceInfo: ServiceInfo): Candidate {
        val packageManager = context.packageManager
        val packageName = serviceInfo.packageName
        val applicationInfo = getApplicationInfo(packageManager, packageName)
        val packageInfo = getPackageInfo(packageManager, packageName)
        val contractVersion = requiredIntMetadata(
            serviceInfo,
            applicationInfo,
            ImageQuantPluginContract.META_DATA_CONTRACT_VERSION,
            "contract version",
        )
        val libimagequantVersion = requiredStringMetadata(
            serviceInfo,
            applicationInfo,
            ImageQuantPluginContract.META_DATA_LIBIMAGEQUANT_VERSION,
            "libimagequant version",
        )
        val libpngVersion = requiredStringMetadata(
            serviceInfo,
            applicationInfo,
            ImageQuantPluginContract.META_DATA_LIBPNG_VERSION,
            "libpng version",
        )
        val nativeLibraryName = requiredStringMetadata(
            serviceInfo,
            applicationInfo,
            ImageQuantPluginContract.META_DATA_NATIVE_LIBRARY,
            "native library",
        )
        val nativeNdkVersion = requiredStringMetadata(
            serviceInfo,
            applicationInfo,
            ImageQuantPluginContract.META_DATA_NATIVE_NDK_VERSION,
            "native NDK version",
        )
        val nativeStl = requiredStringMetadata(
            serviceInfo,
            applicationInfo,
            ImageQuantPluginContract.META_DATA_NATIVE_STL,
            "native STL",
        )
        val nativeAbiVersion = requiredIntMetadata(
            serviceInfo,
            applicationInfo,
            ImageQuantPluginContract.META_DATA_NATIVE_ABI_VERSION,
            "native ABI version",
        )
        val nativeAbis = requiredStringMetadata(
            serviceInfo,
            applicationInfo,
            ImageQuantPluginContract.META_DATA_NATIVE_ABIS,
            "native ABIs",
        )
        val requiresHostVersion = PluginCapabilityResolver.coerceLong(
            metadataValue(serviceInfo, applicationInfo, ImageQuantPluginContract.META_DATA_REQUIRES_HOST_VERSION),
        ) ?: throw ImageQuantPluginLoadException(
            ImageQuantPluginLoadError.CONTRACT_METADATA_INVALID,
            "Image Quantization plugin required host version metadata is missing or invalid",
        )
        val trustInfo = PluginTrustManager.resolveTrustInfo(context, packageName)
        return Candidate(
            serviceInfo = serviceInfo,
            applicationInfo = applicationInfo,
            packageVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            packageLastUpdateTime = packageInfo.lastUpdateTime,
            contractVersion = contractVersion,
            libimagequantVersion = libimagequantVersion,
            libpngVersion = libpngVersion,
            nativeLibraryName = nativeLibraryName,
            nativeNdkVersion = nativeNdkVersion,
            nativeStl = nativeStl,
            nativeAbiVersion = nativeAbiVersion,
            nativeAbis = nativeAbis,
            requiresHostVersion = requiresHostVersion,
            maxHostVersion = PluginCapabilityResolver.coerceLong(
                metadataValue(serviceInfo, applicationInfo, ImageQuantPluginContract.META_DATA_MAX_HOST_VERSION),
            ),
            enabled = applicationInfo.enabled && serviceInfo.enabled &&
                    PluginEnableStore.isEnabled(context, packageName, true),
            authorizedState = trustInfo.authorizedState,
            sameSignerAsHost = packageManager.checkSignatures(context.packageName, packageName) ==
                    PackageManager.SIGNATURE_MATCH,
        )
    }

    private fun requiredStringMetadata(
        serviceInfo: ServiceInfo,
        applicationInfo: ApplicationInfo,
        key: String,
        label: String,
    ): String = metadataValue(serviceInfo, applicationInfo, key)
        ?.toString()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: throw ImageQuantPluginLoadException(
            ImageQuantPluginLoadError.CONTRACT_METADATA_INVALID,
            "Image Quantization plugin $label metadata is missing or invalid",
        )

    private fun requiredIntMetadata(
        serviceInfo: ServiceInfo,
        applicationInfo: ApplicationInfo,
        key: String,
        label: String,
    ): Int = metadataValue(serviceInfo, applicationInfo, key)
        ?.toString()
        ?.toIntOrNull()
        ?: throw ImageQuantPluginLoadException(
            ImageQuantPluginLoadError.CONTRACT_METADATA_INVALID,
            "Image Quantization plugin $label metadata is missing or invalid",
        )

    private fun buildLoadException(
        context: Context,
        code: ImageQuantPluginLoadError,
        packageName: String = ImageQuantPluginContract.DEFAULT_PACKAGE_NAME,
        requiresHostVersion: Long? = null,
        maxHostVersion: Long? = null,
    ): ImageQuantPluginLoadException {
        val message = when (code) {
            ImageQuantPluginLoadError.PLUGIN_NOT_FOUND -> context.getString(
                R.string.error_official_plugin_missing,
                "\"$PLUGIN_LABEL\"",
            )
            ImageQuantPluginLoadError.PLUGIN_DISABLED -> context.getString(
                R.string.error_plugin_is_not_enabled_in_plugin_center,
                packageName,
            )
            ImageQuantPluginLoadError.IN_PROCESS_TRUST_REQUIRED -> context.getString(
                R.string.error_plugin_is_not_authorized_in_plugin_center,
                packageName,
            )
            ImageQuantPluginLoadError.HOST_VERSION_TOO_LOW -> context.getString(
                R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                requiresHostVersion ?: ImageQuantPluginContract.REQUIRED_HOST_VERSION_CODE,
                BuildConfig.VERSION_CODE.toLong(),
            )
            ImageQuantPluginLoadError.HOST_VERSION_TOO_HIGH ->
                "Image Quantization plugin supports host version ${maxHostVersion ?: "unknown"} or lower; " +
                        "current version is ${BuildConfig.VERSION_CODE}"
            ImageQuantPluginLoadError.PLUGIN_CHANGED_RESTART_REQUIRED ->
                "Image Quantization plugin changed while its native runtime was loaded; restart AutoJs6 before using it again"
            else -> "Image Quantization plugin is incompatible or its native runtime is unavailable ($code)"
        }
        return ImageQuantPluginLoadException(code, message)
    }

    private fun queryServices(packageManager: PackageManager, packageName: String): List<ServiceInfo> {
        val intent = Intent(ImageQuantPluginContract.ACTION_RUNTIME).apply {
            addCategory(ImageQuantPluginContract.CATEGORY_RUNTIME)
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
        val flags = (PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, flags.toInt())
        }
    }

    private fun getPackageInfo(packageManager: PackageManager, packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }

    @Suppress("DEPRECATION")
    private fun metadataValue(
        serviceInfo: ServiceInfo,
        applicationInfo: ApplicationInfo,
        key: String,
    ): Any? = serviceInfo.metaData?.get(key) ?: applicationInfo.metaData?.get(key)
}
