package org.autojs.autojs.core.plugin.opencv

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
import org.autojs.autojs.core.plugin.nativealign.NativeLibraryAlignment
import org.autojs.autojs.core.plugin.nativealign.NativePageAlignmentException
import org.autojs.autojs.core.plugin.nativealign.NativePageSize
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.opencv.api.OpenCvPluginContract
import org.opencv.core.Core
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.zip.ZipFile
import java.util.concurrent.atomic.AtomicBoolean

/** Loads the version-locked OpenCV JNI runtime supplied by the official plugin. */
object OpenCvPluginHost {

    private const val TAG = "OpenCvPluginHost"
    private const val PLUGIN_LABEL = "OpenCV"
    private val loadLock = Any()
    private val preloadScheduled = AtomicBoolean(false)
    private val preloadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoJs6-OpenCV-Preload")
    }

    @Volatile
    private var loadedRuntime: LoadedRuntime? = null

    @Volatile
    private var preparedRuntime: PreparedRuntime? = null

    data class Candidate(
        val serviceInfo: ServiceInfo,
        val applicationInfo: ApplicationInfo,
        val packageVersionCode: Long,
        val packageLastUpdateTime: Long,
        val contractVersion: Int,
        val openCvVersion: String,
        val nativeLibraryName: String,
        val nativeNdkVersion: String,
        val javaApiSha256: String,
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
        val error: OpenCvPluginLoadException?,
    )

    data class LoadedRuntime(
        val origin: Origin,
        val openCvVersion: String,
        val packageName: String? = null,
        val packageVersionCode: Long? = null,
        val abi: String? = null,
        val nativeLibraryPath: String? = null,
        val nativeNdkVersion: String? = null,
        val nativeMinLoadAlign: Long? = null,
    )

    private data class PreparedRuntime(
        val packageIdentity: String,
        val stagedLibrary: OpenCvNativeLibraryStager.StagedLibrary,
    )

    enum class Origin {
        BUNDLED,
        PLUGIN,
    }

    fun discover(
        context: Context,
        packageName: String = OpenCvPluginContract.DEFAULT_PACKAGE_NAME,
    ): List<Discovered> = queryServices(context.packageManager, packageName).map { serviceInfo ->
        runCatching { resolveCandidate(context, serviceInfo) }
            .fold(
                onSuccess = { candidate ->
                    Discovered(
                        serviceInfo,
                        candidate,
                        OpenCvPluginPolicy.rejectionCode(
                            enabled = candidate.enabled,
                            authorizedState = candidate.authorizedState,
                            sameSignerAsHost = candidate.sameSignerAsHost,
                            contractVersion = candidate.contractVersion,
                            openCvVersion = candidate.openCvVersion,
                            nativeLibraryName = candidate.nativeLibraryName,
                            nativeNdkVersion = candidate.nativeNdkVersion,
                            javaApiSha256 = candidate.javaApiSha256,
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
                        error as? OpenCvPluginLoadException ?: OpenCvPluginLoadException(
                            OpenCvPluginLoadError.DISCOVERY_FAILED,
                            "Unable to inspect the OpenCV plugin",
                            error,
                        ),
                    )
                },
            )
    }

    fun isAvailable(context: Context): Boolean = discover(context).any { discovered ->
        val candidate = discovered.candidate
        discovered.error == null && candidate != null && OpenCvNativeLibraryStager.hasCompatibleLibrary(
            apkPaths = candidate.codePaths,
            supportedAbis = processSupportedAbis(),
            nativeLibraryFileName = OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME,
        )
    }

    /**
     * Keeps APK Builder on the same fixed-package, metadata, host-version, enablement, and
     * in-process-native trust policy as runtime loading. ABI filtering is intentionally deferred
     * to the builder because it may package ABIs other than the current process ABI.
     */
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
            serviceInfo.packageName == OpenCvPluginContract.DEFAULT_PACKAGE_NAME &&
                    (serviceInfo.packageName to serviceInfo.name) in eligibleComponents
        }
    }

    fun preload(context: Context) {
        if (loadedRuntime != null || !preloadScheduled.compareAndSet(false, true)) return
        val applicationContext = context.applicationContext
        preloadExecutor.execute {
            try {
                synchronized(loadLock) {
                    if (loadedRuntime == null) {
                        val candidate = selectCandidate(applicationContext)
                        preparedRuntime = PreparedRuntime(
                            packageIdentity = candidate.packageIdentity,
                            stagedLibrary = stageCandidate(applicationContext, candidate),
                        )
                    }
                }
            } catch (error: RuntimeException) {
                Log.i(TAG, "OpenCV runtime was not prepared: ${error.message}")
            } finally {
                preloadScheduled.set(false)
            }
        }
    }

    @JvmStatic
    fun ensureLoaded(context: Context): LoadedRuntime {
        loadedRuntime?.let { return it }
        synchronized(loadLock) {
            loadedRuntime?.let { return it }
            if (AbstractAutoJs.isInrt) {
                tryLoadBundledRuntime(context)?.let { bundled ->
                    loadedRuntime = bundled
                    return bundled
                }
                throw OpenCvPluginLoadException(
                    OpenCvPluginLoadError.BUNDLED_RUNTIME_MISSING,
                    "OpenCV was not included in this packaged application",
                )
            }

            val candidate = selectCandidate(context)
            val staged = preparedRuntime
                ?.takeIf { prepared ->
                    prepared.packageIdentity == candidate.packageIdentity && prepared.stagedLibrary.file.isFile
                }
                ?.stagedLibrary
                ?: stageCandidate(context, candidate).also { stagedLibrary ->
                    preparedRuntime = PreparedRuntime(candidate.packageIdentity, stagedLibrary)
                }

            try {
                System.loadLibrary("c++_shared")
            } catch (error: UnsatisfiedLinkError) {
                Log.d(TAG, "Host C++ runtime was not preloaded; OpenCV loader will resolve it", error)
            }
            val nativeMinLoadAlign = validateNativeAlignment(context, staged.file, candidate.serviceInfo.packageName)
            try {
                System.load(staged.file.absolutePath)
            } catch (error: LinkageError) {
                throw OpenCvPluginLoadException(
                    OpenCvPluginLoadError.NATIVE_LIBRARY_LOAD_FAILED,
                    "Unable to load the OpenCV plugin runtime for ${staged.abi}",
                    error,
                )
            } catch (error: SecurityException) {
                throw OpenCvPluginLoadException(
                    OpenCvPluginLoadError.NATIVE_LIBRARY_LOAD_FAILED,
                    "Android denied loading the OpenCV plugin runtime for ${staged.abi}",
                    error,
                )
            }
            val actualVersion = verifyLoadedVersion()
            if (actualVersion != candidate.openCvVersion) {
                throw OpenCvPluginLoadException(
                    OpenCvPluginLoadError.RUNTIME_VERSION_MISMATCH,
                    "OpenCV plugin metadata declares ${candidate.openCvVersion}, but native runtime reports $actualVersion",
                )
            }
            return LoadedRuntime(
                origin = Origin.PLUGIN,
                openCvVersion = actualVersion,
                packageName = candidate.serviceInfo.packageName,
                packageVersionCode = candidate.packageVersionCode,
                abi = staged.abi,
                nativeLibraryPath = staged.file.absolutePath,
                nativeMinLoadAlign = nativeMinLoadAlign,
                nativeNdkVersion = candidate.nativeNdkVersion,
            ).also { runtime ->
                loadedRuntime = runtime
                Log.i(
                    TAG,
                    "Loaded OpenCV ${runtime.openCvVersion} built with NDK ${runtime.nativeNdkVersion} " +
                            "from ${runtime.packageName} for ${runtime.abi}",
                )
            }
        }
    }

    /**
     * Loads the OpenCV runtime of a packaged APK only when the APK builder recorded it as the
     * OpenCV plugin's copy. A same-named library that another plugin brought in is ignored.
     */
    private fun tryLoadBundledRuntime(context: Context): LoadedRuntime? {
        val record = readBundledRuntimeRecord(context) ?: return null
        val actualSha256 = bundledLibrarySha256(context) ?: return null
        if (!record.matches(actualSha256)) {
            throw OpenCvPluginLoadException(
                OpenCvPluginLoadError.BUNDLED_RUNTIME_UNVERIFIED,
                "Bundled ${OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME} (SHA-256 $actualSha256) does not match " +
                        "the OpenCV runtime packaged from ${record.packageName} ${record.versionName.orEmpty()}".trim(),
            )
        }
        val library = File(context.applicationInfo.nativeLibraryDir, OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME)
        val nativeMinLoadAlign = library.takeIf(File::isFile)?.let { validateNativeAlignment(context, it, context.packageName) }
        try {
            System.loadLibrary(OpenCvPluginContract.NATIVE_LIBRARY_NAME)
        } catch (_: UnsatisfiedLinkError) {
            return null
        }
        val actualVersion = verifyLoadedVersion()
        if (actualVersion != OpenCvPluginContract.EXPECTED_OPENCV_VERSION) {
            throw OpenCvPluginLoadException(
                OpenCvPluginLoadError.RUNTIME_VERSION_MISMATCH,
                "Bundled OpenCV runtime reports $actualVersion; expected ${OpenCvPluginContract.EXPECTED_OPENCV_VERSION}",
            )
        }
        return LoadedRuntime(Origin.BUNDLED, actualVersion, nativeMinLoadAlign = nativeMinLoadAlign).also {
            Log.i(TAG, "Loaded bundled OpenCV ${it.openCvVersion}")
        }
    }

    private fun readBundledRuntimeRecord(context: Context): BundledOpenCvRuntime.Record? = try {
        context.assets.open(BundledOpenCvRuntime.ASSET_PATH).use { BundledOpenCvRuntime.decode(it.readBytes()) }
    } catch (_: IOException) {
        null
    }

    /** Hashes the library that System.loadLibrary resolves: the extracted file or the APK entry. */
    private fun bundledLibrarySha256(context: Context): String? {
        val name = OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME
        File(context.applicationInfo.nativeLibraryDir, name).takeIf(File::isFile)?.let { extracted ->
            return extracted.inputStream().use(BundledOpenCvRuntime::sha256)
        }
        val apkPaths = buildList {
            context.applicationInfo.sourceDir?.let(::add)
            context.applicationInfo.splitSourceDirs?.let(::addAll)
        }
        processSupportedAbis().forEach { abi ->
            apkPaths.forEach { apkPath ->
                runCatching {
                    ZipFile(apkPath).use { apk ->
                        apk.getEntry("lib/$abi/$name")?.let { entry -> apk.getInputStream(entry).use(BundledOpenCvRuntime::sha256) }
                    }
                }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun validateNativeAlignment(context: Context, file: File, packageName: String): Long = try {
        NativeLibraryAlignment.requireCompatible(file, NativePageSize.current())
    } catch (error: NativePageAlignmentException) {
        throw OpenCvPluginLoadException(
            OpenCvPluginLoadError.NATIVE_LIBRARY_PAGE_ALIGNMENT_UNSUPPORTED,
            context.getString(R.string.error_plugin_native_page_alignment_unsupported, packageName, error.minLoadAlign, error.pageSize),
            error,
        )
    } catch (error: Exception) {
        throw OpenCvPluginLoadException(OpenCvPluginLoadError.NATIVE_LIBRARY_INVALID, "Invalid native ELF: ${file.name}", error)
    }

    private fun selectCandidate(context: Context): Candidate {
        val discovered = discover(context)
        return discovered
            .asSequence()
            .filter { it.error == null }
            .mapNotNull(Discovered::candidate)
            .maxByOrNull(Candidate::packageVersionCode)
            ?: throw discovered.firstNotNullOfOrNull(Discovered::error)
                ?: buildLoadException(context, OpenCvPluginLoadError.PLUGIN_NOT_FOUND)
    }

    private fun stageCandidate(
        context: Context,
        candidate: Candidate,
    ): OpenCvNativeLibraryStager.StagedLibrary = try {
        OpenCvNativeLibraryStager.stage(
            apkPaths = candidate.codePaths,
            destinationRoot = runtimeCacheRoot(context),
            packageIdentity = candidate.packageIdentity,
            supportedAbis = processSupportedAbis(),
            nativeLibraryFileName = OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME,
        ).also { validateNativeAlignment(context, it.file, candidate.serviceInfo.packageName) }
    } catch (error: OpenCvPluginLoadException) {
        throw error
    } catch (error: Exception) {
        throw OpenCvPluginLoadException(
            OpenCvPluginLoadError.NATIVE_LIBRARY_EXTRACTION_FAILED,
            "Unable to stage the OpenCV plugin runtime",
            error,
        )
    }

    private fun verifyLoadedVersion(): String = try {
        Core.getVersionString()
    } catch (error: LinkageError) {
        throw OpenCvPluginLoadException(
            OpenCvPluginLoadError.NATIVE_LIBRARY_LOAD_FAILED,
            "OpenCV native runtime did not expose the expected Java/JNI ABI",
            error,
        )
    }

    private fun processSupportedAbis(): List<String> = OpenCvProcessAbiPolicy.select(
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
        return File(context.codeCacheDir, "opencv-runtime/$safeProcessName-$bitness")
    }

    private fun resolveCandidate(context: Context, serviceInfo: ServiceInfo): Candidate {
        val packageManager = context.packageManager
        val packageName = serviceInfo.packageName
        val applicationInfo = getApplicationInfo(packageManager, packageName)
        val packageInfo = getPackageInfo(packageManager, packageName)
        val contractVersion = metadataValue(serviceInfo, applicationInfo, OpenCvPluginContract.META_DATA_CONTRACT_VERSION)
            ?.toString()?.toIntOrNull()
            ?: throw OpenCvPluginLoadException(
                OpenCvPluginLoadError.CONTRACT_METADATA_INVALID,
                "OpenCV plugin contract metadata is missing or invalid",
            )
        val openCvVersion = metadataValue(serviceInfo, applicationInfo, OpenCvPluginContract.META_DATA_OPENCV_VERSION)
            ?.toString()?.takeIf(String::isNotBlank)
            ?: throw OpenCvPluginLoadException(
                OpenCvPluginLoadError.CONTRACT_METADATA_INVALID,
                "OpenCV plugin version metadata is missing",
            )
        val nativeLibraryName = metadataValue(serviceInfo, applicationInfo, OpenCvPluginContract.META_DATA_NATIVE_LIBRARY)
            ?.toString()?.takeIf(String::isNotBlank)
            ?: throw OpenCvPluginLoadException(
                OpenCvPluginLoadError.CONTRACT_METADATA_INVALID,
                "OpenCV plugin native library metadata is missing",
            )
        val nativeNdkVersion = metadataValue(serviceInfo, applicationInfo, OpenCvPluginContract.META_DATA_NATIVE_NDK_VERSION)
            ?.toString()?.trim().orEmpty()
        val javaApiSha256 = metadataValue(serviceInfo, applicationInfo, OpenCvPluginContract.META_DATA_JAVA_API_SHA256)
            ?.toString()?.trim()?.lowercase()?.takeIf(String::isNotBlank)
            ?: throw OpenCvPluginLoadException(
                OpenCvPluginLoadError.CONTRACT_METADATA_INVALID,
                "OpenCV plugin Java API fingerprint metadata is missing",
            )
        val trustInfo = PluginTrustManager.resolveTrustInfo(context, packageName)
        return Candidate(
            serviceInfo = serviceInfo,
            applicationInfo = applicationInfo,
            packageVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            packageLastUpdateTime = packageInfo.lastUpdateTime,
            contractVersion = contractVersion,
            openCvVersion = openCvVersion,
            nativeLibraryName = nativeLibraryName,
            nativeNdkVersion = nativeNdkVersion,
            javaApiSha256 = javaApiSha256,
            requiresHostVersion = PluginCapabilityResolver.coerceLong(
                metadataValue(serviceInfo, applicationInfo, OpenCvPluginContract.META_DATA_REQUIRES_HOST_VERSION),
            ),
            maxHostVersion = PluginCapabilityResolver.coerceLong(
                metadataValue(serviceInfo, applicationInfo, OpenCvPluginContract.META_DATA_MAX_HOST_VERSION),
            ),
            enabled = applicationInfo.enabled && serviceInfo.enabled &&
                    PluginEnableStore.isEnabled(context, packageName, true),
            authorizedState = trustInfo.authorizedState,
            sameSignerAsHost = packageManager.checkSignatures(context.packageName, packageName) ==
                    PackageManager.SIGNATURE_MATCH,
        )
    }

    private fun buildLoadException(
        context: Context,
        code: OpenCvPluginLoadError,
        packageName: String = OpenCvPluginContract.DEFAULT_PACKAGE_NAME,
        requiresHostVersion: Long? = null,
        maxHostVersion: Long? = null,
    ): OpenCvPluginLoadException {
        val message = when (code) {
            OpenCvPluginLoadError.PLUGIN_NOT_FOUND -> context.getString(
                R.string.error_official_plugin_missing,
                "\"$PLUGIN_LABEL\"",
            )
            OpenCvPluginLoadError.PLUGIN_DISABLED -> context.getString(
                R.string.error_plugin_is_not_enabled_in_plugin_center,
                packageName,
            )
            OpenCvPluginLoadError.IN_PROCESS_TRUST_REQUIRED -> context.getString(
                R.string.error_plugin_is_not_authorized_in_plugin_center,
                packageName,
            )
            OpenCvPluginLoadError.HOST_VERSION_TOO_LOW -> context.getString(
                R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                requiresHostVersion ?: BuildConfig.VERSION_CODE.toLong() + 1L,
                BuildConfig.VERSION_CODE.toLong(),
            )
            OpenCvPluginLoadError.HOST_VERSION_TOO_HIGH ->
                "OpenCV plugin supports host version ${maxHostVersion ?: "unknown"} or lower; current version is ${BuildConfig.VERSION_CODE}"
            else -> "OpenCV plugin is incompatible or its native runtime is unavailable ($code)"
        }
        return OpenCvPluginLoadException(code, message)
    }

    private fun queryServices(packageManager: PackageManager, packageName: String): List<ServiceInfo> {
        val intent = Intent(OpenCvPluginContract.ACTION_RUNTIME).apply {
            addCategory(OpenCvPluginContract.CATEGORY_RUNTIME)
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

enum class OpenCvPluginLoadError {
    PLUGIN_NOT_FOUND,
    DISCOVERY_FAILED,
    PLUGIN_DISABLED,
    IN_PROCESS_TRUST_REQUIRED,
    CONTRACT_METADATA_INVALID,
    CONTRACT_VERSION_UNSUPPORTED,
    OPENCV_VERSION_UNSUPPORTED,
    NATIVE_LIBRARY_UNSUPPORTED,
    NATIVE_NDK_VERSION_MISMATCH,
    JAVA_API_MISMATCH,
    HOST_VERSION_TOO_LOW,
    HOST_VERSION_TOO_HIGH,
    ABI_NOT_SUPPORTED,
    NATIVE_LIBRARY_INVALID,
    NATIVE_LIBRARY_EXTRACTION_FAILED,
    NATIVE_LIBRARY_LOAD_FAILED,
    NATIVE_LIBRARY_PAGE_ALIGNMENT_UNSUPPORTED,
    RUNTIME_VERSION_MISMATCH,
    BUNDLED_RUNTIME_MISSING,
    BUNDLED_RUNTIME_UNVERIFIED,
}

class OpenCvPluginLoadException(
    val code: OpenCvPluginLoadError,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal object OpenCvPluginPolicy {

    fun rejectionCode(
        enabled: Boolean,
        authorizedState: PluginAuthorizedState,
        sameSignerAsHost: Boolean,
        contractVersion: Int,
        openCvVersion: String,
        nativeLibraryName: String,
        nativeNdkVersion: String = OpenCvPluginContract.EXPECTED_NATIVE_NDK_VERSION,
        javaApiSha256: String = OpenCvPluginContract.JAVA_API_SHA256,
        hostVersionCode: Long,
        requiresHostVersion: Long?,
        maxHostVersion: Long?,
    ): OpenCvPluginLoadError? = when {
        !enabled -> OpenCvPluginLoadError.PLUGIN_DISABLED
        !sameSignerAsHost &&
                authorizedState != PluginAuthorizedState.OFFICIAL &&
                authorizedState != PluginAuthorizedState.TRUSTED ->
            OpenCvPluginLoadError.IN_PROCESS_TRUST_REQUIRED
        contractVersion !in OpenCvPluginContract.MIN_SUPPORTED_VERSION..OpenCvPluginContract.MAX_SUPPORTED_VERSION ->
            OpenCvPluginLoadError.CONTRACT_VERSION_UNSUPPORTED
        openCvVersion != OpenCvPluginContract.EXPECTED_OPENCV_VERSION ->
            OpenCvPluginLoadError.OPENCV_VERSION_UNSUPPORTED
        nativeLibraryName != OpenCvPluginContract.NATIVE_LIBRARY_NAME ->
            OpenCvPluginLoadError.NATIVE_LIBRARY_UNSUPPORTED
        nativeNdkVersion !in OpenCvPluginContract.SUPPORTED_NATIVE_NDK_VERSIONS ->
            OpenCvPluginLoadError.NATIVE_NDK_VERSION_MISMATCH
        javaApiSha256 != OpenCvPluginContract.JAVA_API_SHA256 ->
            OpenCvPluginLoadError.JAVA_API_MISMATCH
        !PluginCapabilityResolver.isHostVersionCompatible(hostVersionCode, requiresHostVersion) ->
            OpenCvPluginLoadError.HOST_VERSION_TOO_LOW
        maxHostVersion != null && hostVersionCode > maxHostVersion ->
            OpenCvPluginLoadError.HOST_VERSION_TOO_HIGH
        else -> null
    }
}

internal object OpenCvProcessAbiPolicy {

    fun select(
        is64Bit: Boolean,
        supported32BitAbis: List<String>,
        supported64BitAbis: List<String>,
    ): List<String> = if (is64Bit) supported64BitAbis else supported32BitAbis
}
