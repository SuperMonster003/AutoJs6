@file:Suppress("DEPRECATION")

package org.autojs.autojs.apkbuilder

import android.content.Context
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.apkbuilder.template.RemoteApkBuildClient
import org.autojs.autojs.apkbuilder.template.RemoteProjectConfigUpdater
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.autojs.plugin.common.api.PluginInfo
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compatibility adapter for the historic in-process APK builder.
 *
 * The standard prepare/configure/build/sign/commit/clean/finish chain is forwarded to the
 * official APK Builder plugin. Low-level template mutation APIs remain only as migration guards
 * because their incremental workspace semantics cannot be represented by the remote protocol.
 */
open class ApkBuilder
@Deprecated(
    message = "In-process APK building has moved to the APK Builder plugin",
)
constructor(
    apkInputStream: InputStream?,
    private val outApkFile: File,
    @Suppress("UNUSED_PARAMETER") buildPath: String,
) {

    internal fun interface RemoteBuildExecutor {
        fun build(
            context: Context,
            projectConfig: ProjectConfig,
            outApkFile: File,
            cancelSignal: AtomicBoolean?,
            progressListener: RemoteApkBuildClient.ProgressListener,
        ): RemoteApkBuildClient.BuildOutcome
    }

    private var remoteBuildExecutor = DEFAULT_REMOTE_BUILD_EXECUTOR
    private var progressCallback: ProgressCallback? = null
    private var cancelSignal: AtomicBoolean? = null
    private var projectConfig: ProjectConfig? = null
    private var buildOutcome: RemoteApkBuildClient.BuildOutcome? = null
    private var prepared = false
    private var committed = false
    private var legacyTemplateInput: InputStream? = apkInputStream

    init {
        closeLegacyTemplateInput()
    }

    @VisibleForTesting
    internal constructor(
        apkInputStream: InputStream?,
        outApkFile: File,
        buildPath: String,
        remoteBuildExecutor: RemoteBuildExecutor,
    ) : this(apkInputStream, outApkFile, buildPath) {
        this.remoteBuildExecutor = remoteBuildExecutor
    }

    fun setProgressCallback(callback: ProgressCallback?): ApkBuilder = also {
        progressCallback = callback
    }

    fun setCancelSignal(cancelSignal: AtomicBoolean?): ApkBuilder = also {
        this.cancelSignal = cancelSignal
    }

    @Throws(IOException::class)
    fun prepare(context: Context): ApkBuilder = also {
        try {
            ensureNotCancelled()
            prepared = true
            notifyStepChanged(ProgressStep.PREPARE)
            notifyStepProgress(
                context.getString(R.string.text_preparing_workspace),
                ApkBuilderTemplatePluginIds.PACKAGE_NAME,
            )
        } finally {
            closeLegacyTemplateInput()
        }
    }

    @Throws(IOException::class)
    fun withConfig(context: Context, config: ProjectConfig): ApkBuilder = also {
        ensureNotCancelled()
        check(prepared) {
            "ApkBuilder.prepare(context) must be called before withConfig(context, config)."
        }
        projectConfig = config
        buildOutcome = null
        committed = false
        notifyStepChanged(ProgressStep.BUILD)
        notifyStepProgress(
            context.getString(R.string.text_preparing_build_config),
            config.packageName,
        )
    }

    @Throws(Exception::class)
    fun build(context: Context): ApkBuilder = also {
        ensureNotCancelled()
        val config = checkNotNull(projectConfig) {
            "ApkBuilder.withConfig(context, config) must be called before build(context)."
        }
        buildOutcome = null
        committed = false
        val outcome = remoteBuildExecutor.build(
            context = context.applicationContext,
            projectConfig = config,
            outApkFile = outApkFile,
            cancelSignal = cancelSignal,
            progressListener = { progress ->
                notifyStepProgress(
                    progress.title?.takeIf(String::isNotBlank)
                        ?: context.getString(R.string.text_processing),
                    progress.detail,
                )
            },
        )
        when {
            outcome.status == ApkBuildResult.STATUS_CANCELLED -> {
                cancelSignal?.set(true)
                throw CancellationException("Remote APK build was cancelled.")
            }

            !outcome.isSuccess -> throw IOException(formatRemoteBuildFailure(outcome))

            !outApkFile.isFile -> throw IOException(
                "Remote APK build reported success but did not create ${outApkFile.path}."
            )
        }
        buildOutcome = outcome
    }

    /**
     * Signing is completed by the remote build transaction. This method is retained so the
     * historic fluent chain remains source- and binary-compatible.
     */
    @Throws(Exception::class)
    fun sign(context: Context): ApkBuilder = also {
        ensureNotCancelled()
        requireSuccessfulBuild("sign")
        notifyStepChanged(ProgressStep.SIGN)
        notifyStepProgress(
            context.getString(R.string.text_sign_completed),
            outApkFile.path,
        )
    }

    fun commitProjectConfigIfNeeded(context: Context): ApkBuilder = also {
        ensureNotCancelled()
        if (committed) {
            return@also
        }
        val outcome = requireSuccessfulBuild("commitProjectConfigIfNeeded")
        val config = checkNotNull(projectConfig)
        val sourcePath = config.sourcePath
        val updatedJson = outcome.updatedProjectConfigJson
        if (!sourcePath.isNullOrBlank() && File(sourcePath).isDirectory && !updatedJson.isNullOrBlank()) {
            val configFile = File(ProjectConfig.configFileOfDir(sourcePath))
            notifyStepProgress(
                context.getString(R.string.text_writing_project_config),
                configFile.path,
            )
            configFile.writeText(
                RemoteProjectConfigUpdater.mergeBuildInfoJson(config, updatedJson),
                StandardCharsets.UTF_8,
            )
        }
        committed = true
    }

    /**
     * The remote plugin owns and cleans its workspace. The legacy caller-supplied build path is
     * deliberately not deleted because this adapter never creates or owns it.
     */
    fun cleanWorkspace(context: Context): ApkBuilder = also {
        ensureNotCancelled()
        notifyStepChanged(ProgressStep.CLEAN)
        notifyStepProgress(
            context.getString(R.string.text_cleaning_workspace),
            ApkBuilderTemplatePluginIds.PACKAGE_NAME,
        )
    }

    fun finish(): ApkBuilder = also {
        progressCallback?.let { callback ->
            GlobalAppContext.post { callback.onFinished(this) }
        }
    }

    @Throws(IOException::class)
    fun setScriptFile(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") path: String?,
    ): ApkBuilder = unsupportedLegacyMutation("setScriptFile")

    @Throws(IOException::class)
    fun copyDir(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") srcFile: File,
        @Suppress("UNUSED_PARAMETER") relativeDestPath: String,
    ): Unit = unsupportedLegacyMutation("copyDir")

    @Throws(IOException::class)
    fun replaceFile(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") srcPath: String,
        @Suppress("UNUSED_PARAMETER") relativeDestPath: String,
    ): ApkBuilder = unsupportedLegacyMutation("replaceFile")

    @Throws(IOException::class)
    fun replaceFile(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") srcFile: File,
        @Suppress("UNUSED_PARAMETER") relativeDestPath: String,
    ): ApkBuilder = unsupportedLegacyMutation("replaceFile")

    @Throws(FileNotFoundException::class)
    fun editManifest(): ManifestEditor = unsupportedLegacyMutation("editManifest")

    @Throws(IOException::class)
    fun setArscPackageName(
        @Suppress("UNUSED_PARAMETER") packageName: String?,
    ): ApkBuilder = unsupportedLegacyMutation("setArscPackageName")

    private fun ensureNotCancelled() {
        if (cancelSignal?.get() == true || Thread.currentThread().isInterrupted) {
            throw CancellationException("Remote APK build was cancelled.")
        }
    }

    /**
     * The official plugin owns its template, so the historic constructor stream is never read.
     * Detach it before closing so repeated lifecycle calls and exceptional close implementations
     * cannot retain or close it twice.
     */
    private fun closeLegacyTemplateInput() {
        val input = legacyTemplateInput ?: return
        legacyTemplateInput = null
        runCatching { input.close() }
    }

    private fun requireSuccessfulBuild(operation: String): RemoteApkBuildClient.BuildOutcome {
        val outcome = buildOutcome
            ?: throw IllegalStateException("ApkBuilder.build(context) must succeed before $operation().")
        check(outcome.isSuccess) {
            "ApkBuilder.build(context) must succeed before $operation()."
        }
        return outcome
    }

    private fun notifyStepChanged(step: ProgressStep) {
        progressCallback?.let { callback ->
            GlobalAppContext.post {
                when (step) {
                    ProgressStep.PREPARE -> callback.onPrepare(this)
                    ProgressStep.BUILD -> callback.onBuild(this)
                    ProgressStep.SIGN -> callback.onSign(this)
                    ProgressStep.CLEAN -> callback.onClean(this)
                }
            }
        }
    }

    private fun notifyStepProgress(title: String, detail: String?) {
        progressCallback?.let { callback ->
            GlobalAppContext.post {
                callback.onStepProgress(
                    builder = this,
                    title = title,
                    detail = detail?.takeIf(String::isNotBlank),
                )
            }
        }
    }

    private fun formatRemoteBuildFailure(outcome: RemoteApkBuildClient.BuildOutcome): String {
        val details = (outcome.errors + outcome.warnings)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")
        return details.ifBlank {
            "Remote APK build failed with status ${outcome.status}. " +
                    "Install and enable a compatible official APK Builder plugin."
        }
    }

    @Suppress("SpellCheckingInspection")
    enum class Lib(private val delegate: ApkBuildLibrary) {
        TERMINAL_EMULATOR(ApkBuildLibrary.TERMINAL_EMULATOR),
        EMBEDDED_NODE_JS(ApkBuildLibrary.EMBEDDED_NODE_JS),
        NODEJS_RUNTIME(ApkBuildLibrary.NODEJS_RUNTIME),
        OPENCV(ApkBuildLibrary.OPENCV),
        MLKIT_OCR(ApkBuildLibrary.MLKIT_OCR),
        PADDLE_OCR(ApkBuildLibrary.PADDLE_OCR),
        RAPID_OCR(ApkBuildLibrary.RAPID_OCR),
        OPENCC(ApkBuildLibrary.OPENCC),
        PINYIN(ApkBuildLibrary.PINYIN),
        PINYIN4J(ApkBuildLibrary.PINYIN4J),
        MLKIT_BARCODE(ApkBuildLibrary.MLKIT_BARCODE),
        MEDIA_INFO(ApkBuildLibrary.MEDIA_INFO),
        IMAGE_QUANT(ApkBuildLibrary.IMAGE_QUANT),
        ;

        @JvmField
        val label: String = delegate.label

        @JvmField
        val aliases: List<String> = delegate.aliases

        @JvmField
        val enumerable: Boolean = delegate.enumerable

        internal val libsToInclude: List<String>
            get() = delegate.libsToInclude

        internal val assetFilesToInclude: List<String> = emptyList()

        internal val assetDirsToInclude: List<String> = emptyList()

        internal val plugin: PluginLib?
            get() = delegate.plugin?.toCompatibilityPlugin()

        val isPlugin: Boolean
            get() = delegate.isPlugin

        fun toPluginPair(): Pair<Lib, PluginLib> = this to checkNotNull(plugin)

        fun ensureLibFiles(moduleName: String = label) {
            if (!isInrt || libsToInclude.isEmpty()) {
                return
            }
            val nativeLibraries = File(GlobalAppContext.get().applicationInfo.nativeLibraryDir)
                .list()
                ?.toSet()
                .orEmpty()
            if (!nativeLibraries.containsAll(libsToInclude)) {
                throw IllegalStateException(
                    GlobalAppContext.get().getString(
                        R.string.error_module_does_not_work_due_to_the_lack_of_necessary_library_files,
                        moduleName,
                    )
                )
            }
        }

        companion object {
            val defaultLibsToInclude = TERMINAL_EMULATOR.libsToInclude

            val defaultAssetFilesToInclude = listOf(
                "init.js",
                "roboto_medium.ttf",
            )

            val defaultAssetDirsToExclude = listOf(
                "doc",
                "docs",
                "editor",
                "indices",
                "js-beautify",
                "sample",
                "stored-locales",
                "models",
            )
        }
    }

    data class PluginLib(
        val action: String,
        val engine: String? = null,
        val onServiceConnected: (IBinder) -> PluginInfo?,
        val variants: List<PluginLibVariant>,
    )

    data class PluginLibVariant(
        val variant: String? = null,
        val assetsToInclude: Pair<List<String>, List<String>> = emptyList<String>() to emptyList(),
        val libsToInclude: List<String> = emptyList(),
    )

    enum class ProgressStep {
        PREPARE,
        BUILD,
        SIGN,
        CLEAN,
    }

    interface ProgressCallback {
        fun onPrepare(builder: ApkBuilder)
        fun onBuild(builder: ApkBuilder)
        fun onSign(builder: ApkBuilder)
        fun onClean(builder: ApkBuilder)
        fun onStepProgress(builder: ApkBuilder, title: String, detail: String?)
        fun onFinished(builder: ApkBuilder)
    }

    companion object {
        const val ICON_NAME = "ic_launcher"
        const val ICON_RES_DIR = "mipmap"
        const val LIBRARY_DIR = "lib"
        const val TEMPLATE_APK_NAME = "template.apk"
        const val INRT_APP_ID = "org.autojs.autojs6.inrt"

        private val DEFAULT_REMOTE_BUILD_EXECUTOR = RemoteBuildExecutor {
                context,
                projectConfig,
                outApkFile,
                cancelSignal,
                progressListener,
            ->
            RemoteApkBuildClient.buildBlocking(
                context = context,
                projectConfig = projectConfig,
                outApk = outApkFile,
                cancelSignal = cancelSignal,
                progressListener = progressListener,
                preferredPluginPackageName = ApkBuilderTemplatePluginIds.PACKAGE_NAME,
            )
        }

        @JvmStatic
        @VisibleForTesting
        fun <T> selectBestPluginVariantCandidate(
            infos: List<Pair<T, PluginLibVariant>>,
            supportedVariants: List<PluginLibVariant>,
        ): Pair<T, PluginLibVariant>? {
            val supportedVariantKeys = supportedVariants
                .map { it.variant.normalizedPluginVariantKey() }
                .toSet()
            return infos
                .filter { (_, info) -> info.variant.normalizedPluginVariantKey() in supportedVariantKeys }
                .maxByOrNull { (_, info) -> info.variant.pluginVariantNumber() }
                ?: infos.firstOrNull()
        }

        @JvmStatic
        @VisibleForTesting
        internal fun nativeLibraryDirectoryMatchesAbi(
            directoryName: String,
            abiCandidates: List<String>,
        ): Boolean {
            val directoryAbi = directoryName.canonicalNativeLibraryAbiName()
            return abiCandidates.any { candidate ->
                candidate.canonicalNativeLibraryAbiName() == directoryAbi
            }
        }

        val appApkFile: File by lazy {
            File(GlobalAppContext.get().applicationInfo.sourceDir)
        }

        private fun String.canonicalNativeLibraryAbiName(): String = when (trim().lowercase()) {
            "arm64" -> "arm64-v8a"
            "arm" -> "armeabi-v7a"
            else -> trim().lowercase()
        }

        private fun String?.normalizedPluginVariantKey(): String? {
            return this?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        }

        private fun String?.pluginVariantNumber(): Int {
            return normalizedPluginVariantKey()
                ?.replace(Regex("\\D"), "")
                ?.toIntOrNull()
                ?: 0
        }
    }
}

/**
 * Lightweight source/binary compatibility shell for code whose type signatures reference the old
 * in-process manifest editor. Binary AXML mutation now belongs to the APK Builder plugin and cannot
 * be performed incrementally through this object.
 */
@Deprecated(
    message = "Binary manifest editing has moved to the APK Builder plugin",
)
open class ManifestEditor(
    @Suppress("UNUSED_PARAMETER") manifestInputStream: InputStream?,
) {

    open fun setVersionCode(@Suppress("UNUSED_PARAMETER") versionCode: Int): ManifestEditor =
        unsupportedLegacyMutation("ManifestEditor.setVersionCode")

    open fun setVersionName(@Suppress("UNUSED_PARAMETER") versionName: String?): ManifestEditor =
        unsupportedLegacyMutation("ManifestEditor.setVersionName")

    open fun setAppName(@Suppress("UNUSED_PARAMETER") appName: CharSequence?): ManifestEditor =
        unsupportedLegacyMutation("ManifestEditor.setAppName")

    open fun setPackageName(@Suppress("UNUSED_PARAMETER") packageName: String?): ManifestEditor =
        unsupportedLegacyMutation("ManifestEditor.setPackageName")

    open fun addEmbeddedNodeScriptServiceIfMissing(): ManifestEditor =
        unsupportedLegacyMutation("ManifestEditor.addEmbeddedNodeScriptServiceIfMissing")

    open fun addEmbeddedNodeForegroundServicePermissionsIfMissing(): ManifestEditor =
        unsupportedLegacyMutation("ManifestEditor.addEmbeddedNodeForegroundServicePermissionsIfMissing")

    @Throws(IOException::class)
    open fun commit(): ManifestEditor = unsupportedLegacyMutation("ManifestEditor.commit")

    @Throws(IOException::class)
    open fun writeTo(@Suppress("UNUSED_PARAMETER") manifestOutputStream: OutputStream): Unit =
        unsupportedLegacyMutation("ManifestEditor.writeTo")

    open fun isPermissionRequired(@Suppress("UNUSED_PARAMETER") permissionName: String?): Boolean =
        unsupportedLegacyMutation("ManifestEditor.isPermissionRequired")

    protected open fun isEmbeddedNodeForegroundServicePermission(
        @Suppress("UNUSED_PARAMETER") permissionName: String?,
    ): Boolean = unsupportedLegacyMutation("ManifestEditor.isEmbeddedNodeForegroundServicePermission")

    open fun shouldIgnoreComponentNode(
        @Suppress("UNUSED_PARAMETER") nodeName: String?,
        @Suppress("UNUSED_PARAMETER") componentClassName: String?,
    ): Boolean = unsupportedLegacyMutation("ManifestEditor.shouldIgnoreComponentNode")
}

private fun unsupportedLegacyMutation(operation: String): Nothing {
    throw UnsupportedOperationException(
        "$operation is an incremental in-process APK template mutation API and cannot be " +
                "represented by the remote APK Builder protocol. Configure ProjectConfig and use " +
                "ApkBuilder.prepare(...).withConfig(...).build(...) with the enabled official APK " +
                "Builder plugin."
    )
}

private fun ApkPluginLibrary.toCompatibilityPlugin(): ApkBuilder.PluginLib {
    return ApkBuilder.PluginLib(
        action = action,
        engine = engine,
        onServiceConnected = onServiceConnected,
        variants = variants.map { variant ->
            ApkBuilder.PluginLibVariant(
                variant = variant.variant,
                assetsToInclude = variant.assetsToInclude,
                libsToInclude = variant.libsToInclude,
            )
        },
    )
}
