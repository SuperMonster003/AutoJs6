package org.autojs.autojs.core.plugin.docs

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.AssetManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Discovers the trusted static-content provider used by the offline documentation surface. */
object OfflineDocsPluginHost {

    private val verificationLock = Any()
    private val verifiedContentCache =
        OfflineDocsVerificationCache<OfflineDocsVerifiedInventory>()

    data class Candidate(
        val serviceInfo: ServiceInfo,
        val applicationInfo: ApplicationInfo,
        val packageVersionCode: Long,
        val packageLastUpdateTime: Long,
        val contractVersion: Int,
        val contentVersion: String,
        val contentFormat: String,
        val assetRoot: String,
        val entryPoint: String,
        val fileCount: Long,
        val totalBytes: Long,
        val contentSha256: String,
        val requiresHostVersion: Long?,
        val maxHostVersion: Long?,
        /** The package and its service are enabled in the system. zh-CN: 包及其服务在系统中处于启用状态. */
        val systemEnabled: Boolean,
        /** The saved Plugin Center switch. zh-CN: 插件中心已保存的开关状态. */
        val centerEnabled: Boolean,
        val authorizedState: PluginAuthorizedState,
        val sameSignerAsHost: Boolean,
    ) {
        val enabled: Boolean
            get() = systemEnabled && centerEnabled

        val packageIdentity: String
            get() = buildString {
                append(serviceInfo.packageName).append('-')
                append(packageVersionCode).append('-')
                append(packageLastUpdateTime).append('-')
                append(contractVersion).append('-')
                append(contentVersion).append('-')
                append(contentFormat).append('-')
                append(assetRoot).append('-')
                append(entryPoint).append('-')
                append(fileCount).append('-')
                append(totalBytes).append('-')
                append(contentSha256)
            }

        val entryPointAssetPath: String
            get() = "$assetRoot/$entryPoint"
    }

    data class Discovered(
        val serviceInfo: ServiceInfo,
        val candidate: Candidate?,
        val error: OfflineDocsPluginLoadException?,
    )

    data class LoadedPlugin(
        val candidate: Candidate,
        val packageContext: Context,
        val verifiedInventory: OfflineDocsVerifiedInventory,
    ) {
        val assetManager: AssetManager
            get() = packageContext.assets

        val verifiedAssetPaths: Set<String>
            get() = verifiedInventory.assetPaths
    }

    fun discover(
        context: Context,
        packageName: String = OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME,
    ): List<Discovered> = discover(context, packageName, OfflineDocsEligibilityCheck.LOAD)

    private fun discover(
        context: Context,
        packageName: String,
        check: OfflineDocsEligibilityCheck,
    ): List<Discovered> {
        if (packageName != OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME) return emptyList()
        return queryServices(context.packageManager).map { serviceInfo ->
            runCatching { resolveCandidate(context, serviceInfo) }
                .fold(
                    onSuccess = { candidate ->
                        Discovered(
                            serviceInfo = serviceInfo,
                            candidate = candidate,
                            error = candidateEligibilityError(candidate, check),
                        )
                    },
                    onFailure = { error ->
                        Discovered(
                            serviceInfo = serviceInfo,
                            candidate = null,
                            error = error as? OfflineDocsPluginLoadException
                                ?: OfflineDocsPluginLoadException(
                                    code = OfflineDocsPluginLoadError.DISCOVERY_FAILED,
                                    packageName = serviceInfo.packageName,
                                    message = "Unable to inspect the Offline Documentation plugin.",
                                    cause = error,
                                ),
                        )
                    },
                )
        }
    }

    fun isAvailable(context: Context): Boolean = runCatching {
        load(context)
        true
    }.getOrDefault(false)

    @Throws(OfflineDocsPluginLoadException::class)
    fun load(
        context: Context,
        packageName: String = OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME,
    ): LoadedPlugin = load(context, packageName, OfflineDocsEligibilityCheck.LOAD)

    /**
     * The Plugin Center enable handshake: the full load, with the saved switch (still off at this point) left out of
     * the eligibility check. The loaded plugin is not handed out, so a switched-off plugin stays unavailable elsewhere.
     * zh-CN: 插件中心启用握手: 完整加载, 但资格判定不看已保存的开关 (此时仍为关). 加载结果不对外提供, 因此关闭的插件在其他路径上仍不可用.
     */
    @Throws(OfflineDocsPluginLoadException::class)
    fun probe(
        context: Context,
        packageName: String = OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME,
    ): String = load(context, packageName, OfflineDocsEligibilityCheck.ENABLE_HANDSHAKE).candidate.contentVersion

    private fun load(
        context: Context,
        packageName: String,
        check: OfflineDocsEligibilityCheck,
    ): LoadedPlugin {
        requireSupportedPackage(packageName)
        val verificationGeneration = verifiedContentCache.snapshotGeneration()
        val candidate = selectCandidate(context, check)
        // Resolve mutable package state once more before exposing its executable web content.
        val currentCandidate = revalidateCandidate(context, candidate, check)

        val packageContext = try {
            context.createPackageContext(currentCandidate.serviceInfo.packageName, 0)
        } catch (error: PackageManager.NameNotFoundException) {
            throw loadException(
                OfflineDocsPluginLoadError.PACKAGE_CONTEXT_FAILED,
                "Unable to create the Offline Documentation plugin resource context.",
                error,
            )
        } catch (error: SecurityException) {
            throw loadException(
                OfflineDocsPluginLoadError.PACKAGE_CONTEXT_FAILED,
                "Android denied access to the Offline Documentation plugin resources.",
                error,
            )
        }
        val verifiedInventory = verifyContent(
            currentCandidate,
            packageContext.assets,
            verificationGeneration,
        )
        val revalidatedCandidate = revalidateCandidate(context, currentCandidate, check)
        ensureVerificationGenerationCurrent(verificationGeneration, currentCandidate)
        return LoadedPlugin(revalidatedCandidate, packageContext, verifiedInventory)
    }

    internal fun invalidateVerificationCache() {
        verifiedContentCache.invalidate()
    }

    private fun revalidateCandidate(context: Context, candidate: Candidate, check: OfflineDocsEligibilityCheck): Candidate {
        val current = discover(context, OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME, check)
            .firstOrNull { discovered ->
                discovered.serviceInfo.name == candidate.serviceInfo.name
            }
            ?: throw loadException(
                OfflineDocsPluginLoadError.PLUGIN_CHANGED_DURING_LOAD,
                "The Offline Documentation plugin changed during discovery.",
            )
        current.error?.let { throw it }
        val currentCandidate = current.candidate
            ?: throw loadException(
                OfflineDocsPluginLoadError.DISCOVERY_FAILED,
                "Unable to revalidate the Offline Documentation plugin.",
            )
        if (currentCandidate.packageIdentity != candidate.packageIdentity) {
            invalidateVerificationCache()
            throw loadException(
                OfflineDocsPluginLoadError.PLUGIN_CHANGED_DURING_LOAD,
                "The Offline Documentation plugin changed during discovery.",
            )
        }
        return currentCandidate
    }

    private fun verifyContent(
        candidate: Candidate,
        assetManager: AssetManager,
        verificationGeneration: Long,
    ): OfflineDocsVerifiedInventory {
        ensureVerificationGenerationCurrent(verificationGeneration, candidate)
        verifiedContentCache
            .get(verificationGeneration, candidate.packageIdentity)
            ?.let { return it }
        synchronized(verificationLock) {
            verifiedContentCache
                .get(verificationGeneration, candidate.packageIdentity)
                ?.let { return it }
            ensureVerificationGenerationCurrent(verificationGeneration, candidate)
            val inventoryBytes = try {
                assetManager.open(
                    OfflineDocsPluginContract.INVENTORY_FILE,
                    AssetManager.ACCESS_STREAMING,
                ).use(::readInventoryBytes)
            } catch (error: OfflineDocsPluginLoadException) {
                throw error
            } catch (error: Exception) {
                throw loadException(
                    OfflineDocsPluginLoadError.INVENTORY_MISSING,
                    "Offline Documentation plugin inventory is missing or unreadable.",
                    error,
                    candidate.serviceInfo.packageName,
                )
            }
            val inventory = try {
                OfflineDocsContentVerifier.verify(
                    inventoryBytes = inventoryBytes,
                    expectedFileCount = candidate.fileCount,
                    expectedTotalBytes = candidate.totalBytes,
                    expectedContentSha256 = candidate.contentSha256,
                    openAsset = { relativePath ->
                        assetManager.open(
                            "${candidate.assetRoot}/$relativePath",
                            AssetManager.ACCESS_STREAMING,
                        )
                    },
                )
            } catch (error: OfflineDocsPluginLoadException) {
                throw OfflineDocsPluginLoadException(
                    code = error.code,
                    packageName = candidate.serviceInfo.packageName,
                    message = error.message.orEmpty(),
                    cause = error,
                )
            }
            if (!verifiedContentCache.putIfCurrent(
                    verificationGeneration,
                    candidate.packageIdentity,
                    inventory,
                )
            ) {
                throw verificationInvalidatedException(candidate)
            }
            return inventory
        }
    }

    private fun ensureVerificationGenerationCurrent(
        verificationGeneration: Long,
        candidate: Candidate,
    ) {
        if (!verifiedContentCache.isCurrent(verificationGeneration)) {
            throw verificationInvalidatedException(candidate)
        }
    }

    private fun verificationInvalidatedException(candidate: Candidate) = loadException(
        OfflineDocsPluginLoadError.PLUGIN_CHANGED_DURING_LOAD,
        "The Offline Documentation plugin changed during content verification.",
        packageName = candidate.serviceInfo.packageName,
    )

    private fun readInventoryBytes(stream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > OfflineDocsPluginContract.MAX_INVENTORY_BYTES) {
                throw loadException(
                    OfflineDocsPluginLoadError.INVENTORY_INVALID,
                    "Offline Documentation plugin inventory exceeds the supported size.",
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun selectCandidate(context: Context, check: OfflineDocsEligibilityCheck): Candidate {
        val discovered = try {
            discover(context, OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME, check)
        } catch (error: Exception) {
            throw loadException(
                OfflineDocsPluginLoadError.DISCOVERY_FAILED,
                "Unable to query the Offline Documentation plugin.",
                error,
            )
        }
        return discovered
            .asSequence()
            .filter { it.error == null }
            .mapNotNull(Discovered::candidate)
            .maxWithOrNull(compareBy<Candidate> { it.packageVersionCode }.thenBy { it.packageLastUpdateTime })
            ?: throw discovered.firstNotNullOfOrNull(Discovered::error)
            ?: loadException(
                OfflineDocsPluginLoadError.PLUGIN_NOT_FOUND,
                "No compatible Offline Documentation plugin was found.",
            )
    }

    private fun resolveCandidate(context: Context, serviceInfo: ServiceInfo): Candidate {
        val packageName = serviceInfo.packageName
        if (packageName != OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME) {
            throw loadException(
                OfflineDocsPluginLoadError.PACKAGE_NAME_UNSUPPORTED,
                "Offline Documentation content must be supplied by the official package.",
                packageName = packageName,
            )
        }
        val packageManager = context.packageManager
        val applicationInfo = getApplicationInfo(packageManager, packageName)
        val packageInfo = getPackageInfo(packageManager, packageName)
        val requiresHostVersion = requiredLongMetadata(
            serviceInfo,
            applicationInfo,
            OfflineDocsPluginContract.META_DATA_REQUIRES_HOST_VERSION,
            "required host version",
        )
        val trustInfo = PluginTrustManager.resolveTrustInfo(context, packageName)
        return Candidate(
            serviceInfo = serviceInfo,
            applicationInfo = applicationInfo,
            packageVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            packageLastUpdateTime = packageInfo.lastUpdateTime,
            contractVersion = requiredIntMetadata(
                serviceInfo,
                applicationInfo,
                OfflineDocsPluginContract.META_DATA_CONTRACT_VERSION,
                "contract version",
            ),
            contentVersion = requiredStringMetadata(
                serviceInfo,
                applicationInfo,
                OfflineDocsPluginContract.META_DATA_CONTENT_VERSION,
                "content version",
            ),
            contentFormat = requiredStringMetadata(
                serviceInfo,
                applicationInfo,
                OfflineDocsPluginContract.META_DATA_CONTENT_FORMAT,
                "content format",
            ),
            assetRoot = requiredStringMetadata(
                serviceInfo,
                applicationInfo,
                OfflineDocsPluginContract.META_DATA_ASSET_ROOT,
                "asset root",
            ),
            entryPoint = requiredStringMetadata(
                serviceInfo,
                applicationInfo,
                OfflineDocsPluginContract.META_DATA_ENTRY_POINT,
                "entry point",
            ),
            fileCount = requiredLongMetadata(
                serviceInfo,
                applicationInfo,
                OfflineDocsPluginContract.META_DATA_FILE_COUNT,
                "file count",
            ),
            totalBytes = requiredLongMetadata(
                serviceInfo,
                applicationInfo,
                OfflineDocsPluginContract.META_DATA_TOTAL_BYTES,
                "total byte count",
            ),
            contentSha256 = requiredStringMetadata(
                serviceInfo,
                applicationInfo,
                OfflineDocsPluginContract.META_DATA_CONTENT_SHA256,
                "content SHA-256",
            ),
            requiresHostVersion = requiresHostVersion,
            maxHostVersion = optionalLongMetadata(
                serviceInfo,
                applicationInfo,
                OfflineDocsPluginContract.META_DATA_MAX_HOST_VERSION,
                "maximum host version",
            ),
            systemEnabled = applicationInfo.enabled && serviceInfo.enabled,
            centerEnabled = PluginEnableStore.isEnabled(context, packageName, true),
            authorizedState = trustInfo.authorizedState,
            sameSignerAsHost = packageManager.checkSignatures(context.packageName, packageName) ==
                    PackageManager.SIGNATURE_MATCH,
        )
    }

    private fun candidateEligibilityError(
        candidate: Candidate,
        check: OfflineDocsEligibilityCheck,
    ): OfflineDocsPluginLoadException? {
        val code = OfflineDocsPluginPolicy.rejectionCode(
            check = check,
            systemEnabled = candidate.systemEnabled,
            centerEnabled = candidate.centerEnabled,
            authorizedState = candidate.authorizedState,
            sameSignerAsHost = candidate.sameSignerAsHost,
            contractVersion = candidate.contractVersion,
            contentVersion = candidate.contentVersion,
            contentFormat = candidate.contentFormat,
            assetRoot = candidate.assetRoot,
            entryPoint = candidate.entryPoint,
            fileCount = candidate.fileCount,
            totalBytes = candidate.totalBytes,
            contentSha256 = candidate.contentSha256,
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            requiresHostVersion = candidate.requiresHostVersion,
            maxHostVersion = candidate.maxHostVersion,
        ) ?: return null
        return loadException(
            code = code,
            message = "Offline Documentation plugin is not eligible ($code).",
            packageName = candidate.serviceInfo.packageName,
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
        ?: throw invalidMetadata(serviceInfo.packageName, label)

    private fun requiredIntMetadata(
        serviceInfo: ServiceInfo,
        applicationInfo: ApplicationInfo,
        key: String,
        label: String,
    ): Int = PluginCapabilityResolver.coerceLong(metadataValue(serviceInfo, applicationInfo, key))
        ?.takeIf { it <= Int.MAX_VALUE }
        ?.toInt()
        ?: throw invalidMetadata(serviceInfo.packageName, label)

    private fun requiredLongMetadata(
        serviceInfo: ServiceInfo,
        applicationInfo: ApplicationInfo,
        key: String,
        label: String,
    ): Long = PluginCapabilityResolver.coerceLong(metadataValue(serviceInfo, applicationInfo, key))
        ?: throw invalidMetadata(serviceInfo.packageName, label)

    private fun optionalLongMetadata(
        serviceInfo: ServiceInfo,
        applicationInfo: ApplicationInfo,
        key: String,
        label: String,
    ): Long? {
        val value = metadataValue(serviceInfo, applicationInfo, key) ?: return null
        return PluginCapabilityResolver.coerceLong(value)
            ?: throw invalidMetadata(serviceInfo.packageName, label)
    }

    private fun invalidMetadata(packageName: String, label: String) = loadException(
        OfflineDocsPluginLoadError.CONTRACT_METADATA_INVALID,
        "Offline Documentation plugin $label metadata is missing or invalid.",
        packageName = packageName,
    )

    private fun requireSupportedPackage(packageName: String) {
        if (packageName != OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME) {
            throw loadException(
                OfflineDocsPluginLoadError.PACKAGE_NAME_UNSUPPORTED,
                "Offline Documentation content must be supplied by the official package.",
                packageName = packageName,
            )
        }
    }

    private fun loadException(
        code: OfflineDocsPluginLoadError,
        message: String,
        cause: Throwable? = null,
        packageName: String = OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME,
    ) = OfflineDocsPluginLoadException(code, packageName, message, cause)

    private fun queryServices(packageManager: PackageManager): List<ServiceInfo> {
        val intent = Intent(OfflineDocsPluginContract.ACTION).apply {
            addCategory(OfflineDocsPluginContract.CATEGORY)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME)
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

    private fun getApplicationInfo(
        packageManager: PackageManager,
        packageName: String,
    ): ApplicationInfo {
        val flags = (PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, flags.toInt())
        }
    }

    private fun getPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo =
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
