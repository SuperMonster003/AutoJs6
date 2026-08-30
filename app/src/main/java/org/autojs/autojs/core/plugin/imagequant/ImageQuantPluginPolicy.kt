package org.autojs.autojs.core.plugin.imagequant

import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.plugin.imagequant.api.ImageQuantPluginContract

internal object ImageQuantPluginPolicy {

    fun rejectionCode(
        enabled: Boolean,
        authorizedState: PluginAuthorizedState,
        sameSignerAsHost: Boolean,
        contractVersion: Int,
        libimagequantVersion: String,
        libpngVersion: String,
        nativeLibraryName: String,
        nativeNdkVersion: String,
        nativeStl: String,
        nativeAbiVersion: Int,
        nativeAbis: String,
        hostVersionCode: Long,
        requiresHostVersion: Long?,
        maxHostVersion: Long?,
    ): ImageQuantPluginLoadError? = when {
        !enabled -> ImageQuantPluginLoadError.PLUGIN_DISABLED
        !sameSignerAsHost &&
                authorizedState != PluginAuthorizedState.OFFICIAL &&
                authorizedState != PluginAuthorizedState.TRUSTED ->
            ImageQuantPluginLoadError.IN_PROCESS_TRUST_REQUIRED
        contractVersion !in ImageQuantPluginContract.MIN_SUPPORTED_VERSION..ImageQuantPluginContract.MAX_SUPPORTED_VERSION ->
            ImageQuantPluginLoadError.CONTRACT_VERSION_UNSUPPORTED
        libimagequantVersion != ImageQuantPluginContract.EXPECTED_LIBIMAGEQUANT_VERSION ->
            ImageQuantPluginLoadError.LIBIMAGEQUANT_VERSION_UNSUPPORTED
        libpngVersion != ImageQuantPluginContract.EXPECTED_LIBPNG_VERSION ->
            ImageQuantPluginLoadError.LIBPNG_VERSION_UNSUPPORTED
        nativeLibraryName != ImageQuantPluginContract.NATIVE_LIBRARY_NAME ->
            ImageQuantPluginLoadError.NATIVE_LIBRARY_UNSUPPORTED
        nativeNdkVersion != ImageQuantPluginContract.EXPECTED_NATIVE_NDK_VERSION ->
            ImageQuantPluginLoadError.NATIVE_NDK_VERSION_MISMATCH
        nativeStl != ImageQuantPluginContract.EXPECTED_NATIVE_STL ->
            ImageQuantPluginLoadError.NATIVE_STL_MISMATCH
        nativeAbiVersion != ImageQuantPluginContract.EXPECTED_NATIVE_ABI_VERSION ->
            ImageQuantPluginLoadError.NATIVE_ABI_VERSION_MISMATCH
        normalizedAbis(nativeAbis) != ImageQuantPluginContract.SUPPORTED_ABIS.toSet() ->
            ImageQuantPluginLoadError.NATIVE_ABIS_MISMATCH
        requiresHostVersion == null || requiresHostVersion < ImageQuantPluginContract.REQUIRED_HOST_VERSION_CODE ->
            ImageQuantPluginLoadError.CONTRACT_METADATA_INVALID
        !PluginCapabilityResolver.isHostVersionCompatible(hostVersionCode, requiresHostVersion) ->
            ImageQuantPluginLoadError.HOST_VERSION_TOO_LOW
        maxHostVersion != null && hostVersionCode > maxHostVersion ->
            ImageQuantPluginLoadError.HOST_VERSION_TOO_HIGH
        else -> null
    }

    private fun normalizedAbis(value: String): Set<String> = value
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
}

internal object ImageQuantProcessAbiPolicy {

    fun select(
        is64Bit: Boolean,
        supported32BitAbis: List<String>,
        supported64BitAbis: List<String>,
    ): List<String> = if (is64Bit) supported64BitAbis else supported32BitAbis
}

internal object ImageQuantOptionsApiPolicy {

    fun supports(optionsApiVersion: Int): Boolean =
        optionsApiVersion >= ImageQuantPluginContract.MIN_OPTIONS_API_VERSION

    fun supportsQuantizationResult(optionsApiVersion: Int): Boolean =
        optionsApiVersion >= ImageQuantPluginContract.QUANTIZATION_RESULT_API_VERSION

    fun supportsColorIo(optionsApiVersion: Int): Boolean =
        optionsApiVersion >= ImageQuantPluginContract.COLOR_IO_API_VERSION
}

internal object ImageQuantRuntimeLifecyclePolicy {

    fun rejectionCode(
        bundled: Boolean,
        loadedPackageIdentity: String?,
        currentPackageIdentity: String?,
        currentError: ImageQuantPluginLoadError?,
    ): ImageQuantPluginLoadError? = when {
        bundled -> null
        currentError != null -> currentError
        currentPackageIdentity == null -> ImageQuantPluginLoadError.PLUGIN_NOT_FOUND
        loadedPackageIdentity != currentPackageIdentity ->
            ImageQuantPluginLoadError.PLUGIN_CHANGED_RESTART_REQUIRED
        else -> null
    }
}
