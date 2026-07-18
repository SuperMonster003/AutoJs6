package org.autojs.autojs.ui.edit.editor.ace.font

import java.io.File
import java.io.InputStream

/** Stable identifiers owned by the host application, never by a remote catalog. */
object AceFontIds {
    const val SYSTEM_MONOSPACE = "system_monospace"
    const val BUNDLED_IOSEVKA = "iosevka"

    val reservedRemoteIds: Set<String> = setOf(SYSTEM_MONOSPACE, BUNDLED_IOSEVKA)

    internal val validId = Regex("[a-z0-9][a-z0-9_]{0,63}")
    internal val validSha256 = Regex("[0-9a-f]{64}")
}

data class FontCatalog(
    val schemaVersion: Int,
    val catalogVersion: Long,
    val minHostVersionCode: Int,
    val releaseTag: String?,
    val generatedAt: String?,
    val revokedSha256: Set<String>,
    val fonts: List<RemoteFont>,
    /** False means bootstrap metadata is display-only after a newer trusted catalog became unavailable. */
    val remoteArtifactsAllowed: Boolean = true,
) {
    fun find(fontId: String): RemoteFont? = fonts.firstOrNull { it.id == fontId }
}

/** Optional, catalog-owned capabilities used to filter downloadable font variants. */
enum class FontFeature(val catalogValue: String) {
    NERD("nerd"),
    VARIABLE("variable"),
    CN("cn"),
    ;

    companion object {
        fun fromCatalogValue(value: String): FontFeature? = entries.firstOrNull {
            it.catalogValue == value
        }
    }
}

data class RemoteFont(
    val id: String,
    val displayName: String,
    val family: String,
    val order: Int,
    val author: String,
    val license: FontLicense,
    val source: FontSource,
    val artifact: FontArtifact,
    val features: Set<FontFeature> = emptySet(),
) {
    val licenseName: String get() = license.name
    val licenseUrl: String? get() = license.files.firstNotNullOfOrNull { it.url }
    val version: String get() = artifact.version
}

data class FontLicense(
    val name: String,
    val spdx: String?,
    val files: List<FontLicenseFile>,
)

data class FontLicenseFile(
    val name: String?,
    val path: String?,
    val url: String?,
)

data class FontSource(
    val repository: String,
    val upstreamVersion: String?,
    val autoJs6Snapshot: AutoJs6FontSnapshot?,
)

data class AutoJs6FontSnapshot(
    val versionCode: Int?,
    val commit: String?,
    val path: String?,
)

data class FontArtifact(
    val version: String,
    val fileName: String,
    val format: String,
    val mimeType: String,
    val weight: Int,
    val style: String,
    val sizeBytes: Long,
    val sha256: String,
    val urls: List<String>,
    val revoked: Boolean,
) {
    val primaryUrl: String get() = urls.first()
    val url: String get() = primaryUrl
    val size: Long get() = sizeBytes
}

data class InstalledFont(
    val fontId: String,
    val version: String?,
    val sha256: String,
    val sizeBytes: Long,
    val file: File,
    val installedAtMillis: Long,
)

/** A verified font descriptor opened while the inter-process store lock is still held. */
data class OpenedInstalledFont(
    val inputStream: InputStream,
    val sizeBytes: Long,
)

enum class FontInstallationStatus {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE,
    REVOKED,
}

data class FontInstallationState(
    val status: FontInstallationStatus,
    val installed: InstalledFont?,
)

open class FontCatalogException(message: String, cause: Throwable? = null) : Exception(message, cause)

class FontCatalogParseException(message: String, cause: Throwable? = null) :
    FontCatalogException(message, cause)

class FontCatalogCompatibilityException(message: String) : FontCatalogException(message)

class FontCatalogSignatureException(message: String, cause: Throwable? = null) :
    FontCatalogException(message, cause)

class FontCatalogRollbackException(message: String) : FontCatalogException(message)

class FontCatalogNetworkException(message: String, cause: Throwable? = null) :
    FontCatalogException(message, cause)
