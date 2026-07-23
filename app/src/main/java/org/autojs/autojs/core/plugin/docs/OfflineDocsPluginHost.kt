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

    @Volatile
    private var verifiedContentCache: VerifiedContentCache? = null

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
        val enabled: Boolean,
        val authorizedState: PluginAuthorizedState,
        val sameSignerAsHost: Boolean,
    ) {
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

    private data class VerifiedContentCache(
        val packageIdentity: String,
        val inventory: OfflineDocsVerifiedInventory,
    )

    fun discover(
        context: Context,
        packageName: String = OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME,
    ): List<Discovered> {
        if (packageName != OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME) return emptyList()
        return queryServices(context.packageManager).map { serviceInfo ->
            runCatching { resolveCandidate(context, serviceInfo) }
                .fold(
                    onSuccess = { candidate ->
                        Discovered(
                            serviceInfo = serviceInfo,
                            candidate = candidate,
                            error = candidateEligibilityError(candidate),
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
    ): LoadedPlugin {
        requireSupportedPackage(packageName)
        val candidate = selectCandidate(context)
        // Resolve mutable package state once more before exposing its executable web content.
        val currentCandidate = revalidateCandidate(context, candidate)

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
        val verifiedInventory = verifyContent(currentCandidate, packageContext.assets)
        revalidateCandidate(context, currentCandidate)
        return LoadedPlugin(currentCandidate, packageContext, verifiedInventory)
    }

    @Throws(OfflineDocsPluginLoadException::class)
    fun probe(
        context: Context,
        packageName: String = OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME,
    ): String = load(context, packageName).candidate.contentVersion

    internal fun invalidateVerificationCache() {
        verifiedContentCache = null
    }

    private fun revalidateCandidate(context: Context, candidate: Candidate): Candidate {
        val current = discover(context)
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
    ): OfflineDocsVerifiedInventory {
        verifiedContentCache
            ?.takeIf { it.packageIdentity == candidate.packageIdentity }
            ?.let { return it.inventory }
        synchronized(verificationLock) {
            verifiedContentCache
                ?.takeIf { it.packageIdentity == candidate.packageIdentity }
                ?.let { return it.inventory }
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
            return inventory.also { verified ->
                verifiedContentCache = VerifiedContentCache(candidate.packageIdentity, verified)
            }
        }
    }

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

    private fun selectCandidate(context: Context): Candidate {
        val discovered = try {
            discover(context)
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
            enabled = applicationInfo.enabled && serviceInfo.enabled &&
                    PluginEnableStore.isEnabled(context, packageName, true),
            authorizedState = trustInfo.authorizedState,
            sameSignerAsHost = packageManager.checkSignatures(context.packageName, packageName) ==
                    PackageManager.SIGNATURE_MATCH,
        )
    }

    private fun candidateEligibilityError(candidate: Candidate): OfflineDocsPluginLoadException? {
        val code = OfflineDocsPluginPolicy.rejectionCode(
            enabled = candidate.enabled,
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
