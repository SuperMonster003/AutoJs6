package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import org.autojs.autojs.ui.edit.editor.ace.font.AceFontIds
import org.autojs.autojs.ui.edit.editor.ace.font.EcdsaP256CatalogSignatureVerifier
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalog
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogRepository
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadEvent
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadListener
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadSubscription
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloader
import org.autojs.autojs.ui.edit.editor.ace.font.FontFeature
import org.autojs.autojs.ui.edit.editor.ace.font.FontInstallationState
import org.autojs.autojs.ui.edit.editor.ace.font.FontInstallationStatus
import org.autojs.autojs.ui.edit.editor.ace.font.FontStore
import org.autojs.autojs.ui.edit.editor.ace.font.OpenedInstalledFont
import org.autojs.autojs.ui.edit.editor.ace.font.RemoteFont
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class AceEditorFontOption(
    val id: String,
    val displayName: String,
    val family: String,
    val author: String,
    val licenseName: String,
    val order: Int,
    val delivery: Delivery,
    val type: AceEditorFontType,
    val remoteFont: RemoteFont? = null,
    val groupId: String = id,
    val variants: List<RemoteFont> = remoteFont?.let { listOf(it) }.orEmpty(),
    val features: Set<FontFeature> = emptySet(),
    val sizeBytes: Long? = null,
) {
    fun containsFontId(fontId: String?): Boolean = fontId != null &&
        (id == fontId || variants.any { it.id == fontId })

    enum class Delivery {
        SYSTEM,
        BUNDLED,
        DOWNLOADABLE,
    }
}

internal data class AceEditorRemoteFontGroup(
    val groupId: String,
    val variants: List<RemoteFont>,
    val selected: RemoteFont,
)

internal fun resolveRemoteFontGroups(
    fonts: List<RemoteFont>,
    activeFontId: String?,
    preferredVariantId: (String) -> String?,
): List<AceEditorRemoteFontGroup> = fonts.groupBy(RemoteFont::groupId).values.map { group ->
    val variants = group.sortedWith(compareBy<RemoteFont> { it.variantOrder }.thenBy { it.id })
    val groupId = variants.first().groupId
    val preferredId = preferredVariantId(groupId)
    val selected = variants.firstOrNull { it.id == activeFontId }
        ?: variants.firstOrNull { it.id == preferredId }
        ?: variants.singleOrNull(RemoteFont::isDefaultVariant)
        ?: variants.maxWith(compareBy<RemoteFont> { it.variantOrder }.thenBy { it.id })
    AceEditorRemoteFontGroup(groupId, variants, selected)
}

class AceFontCatalogRefreshSubscription internal constructor(
    private val cancelAction: () -> Boolean,
) {
    fun cancel(): Boolean = cancelAction()
}

class AceFontCatalogChangeSubscription internal constructor(
    private val cancelAction: () -> Boolean,
) {
    fun cancel(): Boolean = cancelAction()
}

/** Application-scoped facade joining the trusted catalog, font store and Ace descriptor bridge. */
class AceEditorFontManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkClient = OkHttpClient()
    private val refreshExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AceFontCatalog").apply { isDaemon = true }
    }
    private val refreshLock = Any()
    private val pendingRefreshCallbacks = mutableListOf<PendingRefreshCallback>()
    private var refreshInFlight = false
    private val catalogChangeLock = Any()
    private val catalogChangeCallbacks = linkedSetOf<PendingCatalogChangeCallback>()

    val store = FontStore(appContext)

    private val repository: FontCatalogRepository = FontCatalogRepository(
        context = appContext,
        catalogUrl = CATALOG_URL,
        signatureUrl = SIGNATURE_URL,
        publicKey = EcdsaP256CatalogSignatureVerifier.publicKeyFromDer(
            appContext.resources.openRawResource(R.raw.ace_font_catalog_public_key_v1).use { it.readBytes() },
        ),
        expectedKeyId = CATALOG_KEY_ID,
        hostVersionCode = BuildConfig.VERSION_CODE,
        bootstrapCatalogBytes = appContext.resources.openRawResource(R.raw.ace_font_catalog_v1)
            .use { it.readBytes() },
        client = networkClient,
    )

    private val downloader = FontDownloader(
        store = store,
        source = org.autojs.autojs.ui.edit.editor.ace.font.OkHttpFontDownloadSource(networkClient),
        callbackExecutor = Executor { runnable -> mainHandler.post(runnable) },
        commitValidator = { font ->
            authorizedRemoteFont(font.id, font.artifact.sha256)
        },
    )
    private val retainedDownloadLock = Any()
    private val retainedDownloads = mutableMapOf<String, RetainedDownload>()

    fun catalog(): FontCatalog? = repository.current()

    fun options(
        context: Context = appContext,
        activeFontId: String? = AceEditorFontPreferences.getId(),
    ): List<AceEditorFontOption> {
        val installedFonts = store.installedFonts()
        val installedFontIds = installedFonts.asSequence().mapTo(hashSetOf()) { it.fontId }
        val system = AceEditorFontOption(
            id = AceFontIds.SYSTEM_MONOSPACE,
            displayName = context.getString(R.string.entry_ace_editor_font_system_monospace),
            family = "monospace",
            author = AceEditorFont.SYSTEM_MONOSPACE.author,
            licenseName = AceEditorFont.SYSTEM_MONOSPACE.license,
            order = Int.MIN_VALUE,
            delivery = AceEditorFontOption.Delivery.SYSTEM,
            type = AceEditorFontType.BUILT_IN,
            features = setOf(FontFeature.MONO),
        )
        val bundled = AceEditorFontOption(
            id = AceFontIds.BUNDLED_IOSEVKA,
            displayName = context.getString(R.string.entry_ace_editor_font_iosevka),
            family = AceFontDescriptor.BUNDLED_FONT_FAMILY,
            author = AceEditorFont.IOSEVKA.author,
            licenseName = AceEditorFont.IOSEVKA.license,
            order = Int.MIN_VALUE + 1,
            delivery = AceEditorFontOption.Delivery.BUNDLED,
            type = AceEditorFontType.BUILT_IN,
            features = setOf(FontFeature.MONO),
        )
        val catalogFonts = catalog()?.fonts.orEmpty()
        val catalogOptions = resolveRemoteFontGroups(catalogFonts, activeFontId) { groupId ->
            AceEditorFontUiPreferences.getSelectedVariantId(context, groupId)
        }.map { group ->
            val variants = group.variants
            val groupId = group.groupId
            val font = group.selected
            AceEditorFontOption(
                id = font.id,
                displayName = font.displayName,
                family = font.family,
                author = font.author,
                licenseName = font.licenseName,
                order = variants.minOf(RemoteFont::order),
                delivery = AceEditorFontOption.Delivery.DOWNLOADABLE,
                type = if (font.id in installedFontIds) {
                    AceEditorFontType.INSTALLED
                } else {
                    AceEditorFontType.NOT_INSTALLED
                },
                remoteFont = font,
                groupId = groupId,
                variants = variants,
                features = font.features,
                sizeBytes = font.artifact.sizeBytes,
            )
        }
        val catalogIds = catalogFonts.asSequence().mapTo(hashSetOf(), RemoteFont::id)
        val orphanedInstalled = installedFonts
            .filterNot { it.fontId in catalogIds }
            .mapIndexed { index, installed ->
                val known = AceEditorFont.entries.firstOrNull { it.preferenceValue == installed.fontId }
                AceEditorFontOption(
                    id = installed.fontId,
                    displayName = known?.displayName(context) ?: installed.fontId,
                    family = known?.displayName(context) ?: installed.fontId,
                    author = known?.author.orEmpty(),
                    licenseName = known?.license.orEmpty(),
                    order = Int.MAX_VALUE - 1_000 + index,
                    delivery = AceEditorFontOption.Delivery.DOWNLOADABLE,
                    type = AceEditorFontType.INSTALLED,
                    sizeBytes = installed.sizeBytes,
                )
            }
        return buildList {
            add(system)
            add(bundled)
            addAll(catalogOptions)
            addAll(orphanedInstalled)
        }
    }

    fun option(fontId: String, context: Context = appContext): AceEditorFontOption? {
        return options(context, fontId).firstOrNull { it.id == fontId }
    }

    fun downloadOptions(context: Context = appContext): List<AceEditorFontOption> =
        options(context, activeFontId = null)

    fun selectVariant(groupId: String, fontId: String): Boolean {
        val belongsToGroup = catalog()?.fonts.orEmpty().any {
            it.groupId == groupId && it.id == fontId
        }
        if (!belongsToGroup) return false
        AceEditorFontUiPreferences.setSelectedVariantId(appContext, groupId, fontId)
        return true
    }

    fun displayName(fontId: String, context: Context = appContext): String {
        AceEditorFont.entries.firstOrNull { it.preferenceValue == fontId }
            ?.let { return it.displayName(context) }
        val catalog = catalog()
        val font = catalog?.find(fontId) ?: return fontId
        val isGrouped = catalog.fonts.count { it.groupId == font.groupId } > 1
        return font.variantName?.takeIf { isGrouped }
            ?.let { "${font.displayName} · $it" }
            ?: font.displayName
    }

    fun installationState(option: AceEditorFontOption): FontInstallationState? {
        if (option.delivery != AceEditorFontOption.Delivery.DOWNLOADABLE) return null
        val installed = store.installedFont(option.id)
        return option.remoteFont?.let(::installationState)
            ?: FontInstallationState(
                if (installed == null) FontInstallationStatus.NOT_INSTALLED else FontInstallationStatus.INSTALLED,
                installed,
            )
    }

    fun installationState(font: RemoteFont): FontInstallationState {
        val installed = store.installedFont(font.id)
        val catalog = catalog()
        if (catalog?.remoteArtifactsAllowed != true) {
            return FontInstallationState(FontInstallationStatus.REVOKED, installed)
        }
        if (installed != null && installed.sha256 in catalog.revokedSha256) {
            return FontInstallationState(FontInstallationStatus.REVOKED, installed)
        }
        return store.installationState(font)
    }

    fun descriptor(fontId: String): AceFontDescriptor {
        return when (fontId) {
            AceFontIds.SYSTEM_MONOSPACE -> AceFontDescriptor.system()
            AceFontIds.BUNDLED_IOSEVKA -> AceFontDescriptor.bundledIosevka()
            else -> {
                val catalog = catalog()
                if (catalog?.remoteArtifactsAllowed != true) {
                    return unavailableSelectionFallback()
                }
                val installed = runCatching { store.installedFont(fontId) }.getOrNull()
                    ?: return unavailableSelectionFallback()
                if (installed.sha256 in catalog.revokedSha256) {
                    return unavailableSelectionFallback()
                }
                val family = catalog.find(fontId)?.family
                    ?: AceEditorFont.entries.firstOrNull { it.preferenceValue == fontId }
                        ?.displayName(appContext)
                    ?: fontId
                AceFontDescriptor.installed(fontId, family, installed.sha256)
            }
        }
    }

    private fun unavailableSelectionFallback(): AceFontDescriptor = AceFontDescriptor.bundledIosevka()

    /** Authorizes and opens the exact installed digest while catalog/store process locks are held. */
    fun openInstalledFont(fontId: String, sha256: String): OpenedInstalledFont? = runCatching {
        store.withCatalogAuthorizationLock {
            val catalog = catalog()
            if (catalog?.remoteArtifactsAllowed != true) return@withCatalogAuthorizationLock null
            if (sha256.lowercase() in catalog.revokedSha256) return@withCatalogAuthorizationLock null
            store.openInstalledFont(fontId, sha256)
        }
    }.getOrNull()

    /** Accepts only an id; URLs and digests always come from the active trusted catalog. */
    fun download(
        fontId: String,
        expectedSha256: String,
        listener: FontDownloadListener,
    ): FontDownloadSubscription {
        return store.withCatalogAuthorizationLock {
            val font = authorizedRemoteFont(fontId, expectedSha256)
            synchronized(retainedDownloadLock) {
                retainDownloadLocked(font)
                downloader.enqueue(font, listener)
            }
        }
    }

    fun isDownloading(fontId: String): Boolean = downloader.isDownloading(fontId)

    fun activeDownloadSha256(fontId: String): String? = synchronized(retainedDownloadLock) {
        retainedDownloads[fontId]?.sha256?.takeIf { downloader.isDownloading(fontId) }
    }

    /**
     * Observes an already active transfer without creating or retaining a new transfer.
     * The returned subscription receives Queued and the latest Progress atomically before
     * subsequent events. Cancelling it only detaches this observer.
     */
    fun observeActiveDownload(
        fontId: String,
        listener: FontDownloadListener,
    ): FontDownloadSubscription? = synchronized(retainedDownloadLock) {
        retainedDownloads[fontId] ?: return@synchronized null
        downloader.observeActive(fontId, listener)
    }

    /** Explicit user cancellation stops both the retained background job and its transfer. */
    fun cancelDownload(fontId: String): Boolean = synchronized(retainedDownloadLock) {
        val cancelled = downloader.cancel(fontId)
        if (cancelled) {
            retainedDownloads.remove(fontId)
        }
        cancelled
    }

    private fun retainDownloadLocked(font: RemoteFont) {
        retainedDownloads[font.id]?.let { current ->
            if (current.sha256 == font.artifact.sha256) return
            check(downloader.cancel(font.id) || !downloader.isDownloading(font.id)) {
                "A previous version of font '${font.id}' is being committed"
            }
            retainedDownloads.remove(font.id)
        }
        val retained = RetainedDownload(font.artifact.sha256)
        downloader.enqueue(font) { event ->
            if (event is FontDownloadEvent.Success || event is FontDownloadEvent.Failure ||
                event is FontDownloadEvent.Cancelled
            ) {
                synchronized(retainedDownloadLock) {
                    if (retainedDownloads[font.id] === retained) {
                        retainedDownloads.remove(font.id)
                    }
                }
            }
        }
        retainedDownloads[font.id] = retained
    }

    private fun authorizedRemoteFont(fontId: String, expectedSha256: String? = null): RemoteFont {
        val catalog = catalog()
        check(catalog?.remoteArtifactsAllowed == true) {
            "Remote font artifacts are unavailable"
        }
        val font = checkNotNull(catalog.find(fontId)) {
            "Font '$fontId' is not authorized by the active catalog"
        }
        check(expectedSha256 == null || expectedSha256 == font.artifact.sha256) {
            "Font '$fontId' changed while it was downloading"
        }
        check(!font.artifact.revoked && font.artifact.sha256 !in catalog.revokedSha256) {
            "Font '$fontId' is revoked"
        }
        return font
    }

    fun delete(fontId: String): Boolean = store.delete(fontId)

    /** Deletes a browser-rejected cache only if the same digest is still installed. */
    fun invalidateInstalled(fontId: String, sha256: String): Boolean =
        runCatching { store.deleteIfCurrent(fontId, sha256) }.getOrDefault(false)

    fun refreshAsync(callback: (Result<FontCatalog>) -> Unit): AceFontCatalogRefreshSubscription {
        val pending = PendingRefreshCallback(callback)
        val shouldStart = synchronized(refreshLock) {
            pendingRefreshCallbacks += pending
            (!refreshInFlight).also { start ->
                if (start) refreshInFlight = true
            }
        }
        if (shouldStart) {
            runCatching {
                refreshExecutor.execute {
                    finishRefresh(runCatching { repository.refresh() })
                }
            }.onFailure { finishRefresh(Result.failure(it)) }
        }
        return AceFontCatalogRefreshSubscription {
            synchronized(refreshLock) {
                if (pending.callback == null) {
                    false
                } else {
                    pending.callback = null
                    pendingRefreshCallbacks.remove(pending)
                    true
                }
            }
        }
    }

    /** Keeps active editors synchronized with a newly accepted catalog, including revocations. */
    fun observeCatalogChanges(
        callback: (FontCatalog) -> Unit,
    ): AceFontCatalogChangeSubscription {
        val pending = PendingCatalogChangeCallback(callback)
        synchronized(catalogChangeLock) { catalogChangeCallbacks += pending }
        return AceFontCatalogChangeSubscription {
            synchronized(catalogChangeLock) {
                if (pending.callback == null) {
                    false
                } else {
                    pending.callback = null
                    catalogChangeCallbacks.remove(pending)
                    true
                }
            }
        }
    }

    private fun finishRefresh(result: Result<FontCatalog>) {
        val acceptedCatalog = result.getOrNull()
        acceptedCatalog?.let(::cancelDownloadsNoLongerAuthorized)
        val callbacks = synchronized(refreshLock) {
            refreshInFlight = false
            pendingRefreshCallbacks.toList().also { pendingRefreshCallbacks.clear() }
        }
        val catalogCallbacks = if (acceptedCatalog == null) {
            emptyList()
        } else {
            synchronized(catalogChangeLock) { catalogChangeCallbacks.toList() }
        }
        mainHandler.post {
            callbacks.forEach { pending ->
                val callback = synchronized(refreshLock) {
                    pending.callback.also { pending.callback = null }
                }
                runCatching { callback?.invoke(result) }
            }
            if (acceptedCatalog != null) {
                catalogCallbacks.forEach { pending ->
                    val callback = synchronized(catalogChangeLock) { pending.callback }
                    runCatching { callback?.invoke(acceptedCatalog) }
                }
            }
        }
    }

    private fun cancelDownloadsNoLongerAuthorized(catalog: FontCatalog) {
        synchronized(retainedDownloadLock) {
            val invalidIds = retainedDownloads.filter { (fontId, retained) ->
                val current = catalog.find(fontId)
                !catalog.remoteArtifactsAllowed || current == null || current.artifact.revoked ||
                    current.artifact.sha256 != retained.sha256 || retained.sha256 in catalog.revokedSha256
            }.keys.toList()
            invalidIds.forEach { fontId ->
                if (downloader.cancel(fontId)) retainedDownloads.remove(fontId)
            }
        }
    }

    private class PendingRefreshCallback(
        @Volatile var callback: ((Result<FontCatalog>) -> Unit)?,
    )

    private class PendingCatalogChangeCallback(
        @Volatile var callback: ((FontCatalog) -> Unit)?,
    )

    private class RetainedDownload(val sha256: String)

    companion object {
        private const val CATALOG_KEY_ID = "p256-fc433f8ba81333f7"
        private const val CATALOG_URL =
            "https://raw.githubusercontent.com/SuperMonster003/AutoJs6-Shared-Assets/main/ace-fonts/catalogs/catalog-v1.json"
        private const val SIGNATURE_URL =
            "https://raw.githubusercontent.com/SuperMonster003/AutoJs6-Shared-Assets/main/ace-fonts/catalogs/catalog-v1.sig.json"

        @Volatile
        private var instance: AceEditorFontManager? = null

        @JvmStatic
        fun get(context: Context): AceEditorFontManager {
            return instance ?: synchronized(this) {
                instance ?: AceEditorFontManager(context).also { instance = it }
            }
        }
    }
}
