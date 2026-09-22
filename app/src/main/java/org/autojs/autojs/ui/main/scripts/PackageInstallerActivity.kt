package org.autojs.autojs.ui.main.scripts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

class PackageInstallerActivity : BaseActivity() {

    override val handleContentViewFromHorizontalNavigationBarAutomatically = false

    private var incomingDirectory: File? = null
    private var pendingArchive: AndroidPackageArchive? = null
    private var installationInProgress = false
    private var ownsOperationSlot = false
    private var progressDialog: MaterialDialog? = null

    private val unknownSourcesSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val archive = pendingArchive ?: return@registerForActivityResult
        if (canRequestPackageInstalls()) {
            commitInstallation(archive)
        } else {
            installationInProgress = false
            showErrorAndFinish(getString(R.string.error_request_install_packages_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // Replaying the original Intent after process death could create a duplicate install
            // session. Stale private files are pruned the next time an import starts.
            finish()
            return
        }
        if (!OPERATION_ACTIVE.compareAndSet(false, true)) {
            Toast.makeText(
                this,
                R.string.error_android_package_operation_in_progress,
                Toast.LENGTH_LONG,
            ).show()
            finish()
            return
        }
        ownsOperationSlot = true
        when {
            intent.action == ACTION_INSTALL_URI && intent.data != null -> {
                importExternalPackage(
                    uri = intent.data!!,
                    packageMimeType = intent.type,
                    showDetails = false,
                )
            }
            intent.action == ACTION_VIEW_EXTERNAL && intent.data != null -> {
                importExternalPackage(
                    uri = intent.data!!,
                    packageMimeType = intent.type,
                )
            }
            intent.action == ACTION_INSTALL_LOCAL -> {
                val path = intent.getStringExtra(EXTRA_LOCAL_PATH)
                val file = path?.let(::File)?.takeIf { it.isFile }
                val expectedSha256 = intent.getStringExtra(EXTRA_EXPECTED_SOURCE_SHA256)
                if (file == null) {
                    showErrorAndFinish(getString(R.string.error_android_package_file_unavailable))
                } else {
                    importLocalPackage(file, expectedSha256)
                }
            }
            else -> showErrorAndFinish(getString(R.string.error_android_package_file_unavailable))
        }
    }

    override fun onDestroy() {
        progressDialog?.dismiss()
        progressDialog = null
        if (isFinishing && !installationInProgress) {
            cleanupIncomingDirectory()
        }
        if (ownsOperationSlot) {
            ownsOperationSlot = false
            OPERATION_ACTIVE.set(false)
        }
        super.onDestroy()
    }

    private fun importExternalPackage(
        uri: Uri,
        packageMimeType: String?,
        showDetails: Boolean = true,
    ) {
        showProgress(R.string.text_reading_android_package)
        lifecycleScope.launch {
            val imported = runCatching {
                withTimeoutOrNull(MAX_EXTERNAL_IMPORT_DURATION_MILLIS) {
                    withContext(Dispatchers.IO) {
                        copyExternalPackage(uri, packageMimeType)
                    }
                } ?: throw IOException(getString(R.string.error_android_package_file_unavailable))
            }
            dismissProgress()
            imported.onSuccess { file ->
                if (showDetails) {
                    showPackageDetails(file)
                } else {
                    inspectAndInstall(file)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                showErrorAndFinish(error.message ?: getString(R.string.error_android_package_file_unavailable))
            }
        }
    }

    private fun importLocalPackage(file: File, expectedSha256: String?) {
        showProgress(R.string.text_reading_android_package)
        lifecycleScope.launch {
            val imported = runCatching {
                withContext(Dispatchers.IO) {
                    copyLocalPackage(file, expectedSha256)
                }
            }
            imported.onSuccess { privateFile ->
                inspectAndInstall(privateFile)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                dismissProgress()
                showErrorAndFinish(error.message ?: getString(R.string.error_android_package_file_unavailable))
            }
        }
    }

    private fun showPackageDetails(file: File) {
        ApkInfoDialogManager.showPackageInfoDialog(
            context = this,
            packageFile = file,
            builderApplier = null,
            onDismissListener = {
                if (!installationInProgress) {
                    cleanupIncomingDirectory()
                    finish()
                }
            },
            installAction = { _, _ ->
                installationInProgress = true
                inspectAndInstall(file)
            },
        )
    }

    private fun inspectAndInstall(file: File) {
        showProgress(R.string.text_preparing)
        lifecycleScope.launch {
            val archiveResult = runCatching {
                withContext(Dispatchers.IO) {
                    AndroidPackageArchiveInspector.inspect(
                        file = file,
                        device = PackageDeviceSpec.from(this@PackageInstallerActivity),
                        prepareForInstallation = true,
                    )
                }
            }
            archiveResult.onSuccess { archive ->
                pendingArchive = archive
                if (!archive.canInstall) {
                    dismissProgress()
                    installationInProgress = false
                    val message = when (archive.installationState) {
                        ArchiveInstallationState.AAB_REQUIRES_CONVERSION ->
                            getString(R.string.apk_info_installation_aab)
                        ArchiveInstallationState.INCOMPATIBLE ->
                            getString(R.string.apk_info_installation_incompatible)
                        else -> archive.problems.firstOrNull { it.blocking }?.detail
                            ?: getString(R.string.apk_info_installation_invalid)
                    }
                    showErrorAndFinish(message)
                    return@onSuccess
                }
                if (!canRequestPackageInstalls()) {
                    dismissProgress()
                    requestUnknownSourcesPermission()
                } else {
                    commitInstallation(archive)
                }
            }.onFailure { error ->
                dismissProgress()
                installationInProgress = false
                showErrorAndFinish(error.message ?: getString(R.string.apk_info_installation_invalid))
            }
        }
    }

    private fun commitInstallation(archive: AndroidPackageArchive) {
        installationInProgress = true
        showProgress(R.string.text_preparing)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val staging = createWorkingDirectory("session")
                    try {
                        AndroidPackageSessionInstaller.install(
                            context = applicationContext,
                            archive = archive,
                            stagingDirectory = staging,
                        )
                    } finally {
                        staging.deleteRecursively()
                    }
                }
            }
            result.onSuccess { sessionId ->
                // The callback is durable even if this page has already left the foreground.
                pendingArchive = null
                installationInProgress = false
                cleanupIncomingDirectory()
                dismissProgress()
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    startActivity(PackageInstallStatusActivity.showIntent(this@PackageInstallerActivity, sessionId))
                }
                finish()
            }.onFailure { error ->
                dismissProgress()
                installationInProgress = false
                showErrorAndFinish(
                    getString(
                        R.string.error_android_package_installation_failed,
                        error.message.orEmpty(),
                    ),
                )
            }
        }
    }

    private fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun requestUnknownSourcesPermission() {
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:$packageName"),
        )
        if (!settingsIntent.resolveActivity(packageManager).let { it != null }) {
            installationInProgress = false
            showErrorAndFinish(getString(R.string.error_request_install_packages_permission_denied))
            return
        }
        unknownSourcesSettings.launch(settingsIntent)
    }

    private suspend fun copyExternalPackage(uri: Uri, packageMimeType: String?): File {
        if (!hasSupportedExternalPackageScheme(uri)) {
            throw IOException("Unsupported package URI scheme")
        }
        val resolver = contentResolver
        val cancellationSignal = CancellationSignal()
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion {
            cancellationSignal.cancel()
        }
        try {
            val metadata = runCatching {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                    cancellationSignal,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)
                    val size = sizeIndex
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let(cursor::getLong)
                        ?.takeIf { it >= 0L }
                    name to size
                }
            }.getOrNull()
            currentCoroutineContext().ensureActive()
            val displayName = metadata
                ?.first
                ?.takeIf(String::isNotBlank)
                ?: uri.lastPathSegment?.takeIf(String::isNotBlank)
            val declaredSize = metadata?.second
            val resolverMimeType = if (
                supportedPackageExtension(displayName) == null &&
                supportedPackageExtension(uri.lastPathSegment) == null &&
                supportedMimeType(packageMimeType) == null
            ) {
                runCatching { resolver.getType(uri) }.getOrNull()
            } else {
                null
            }
            currentCoroutineContext().ensureActive()
            val acceptedMimeType = sequenceOf(packageMimeType, resolverMimeType)
                .mapNotNull(::supportedMimeType)
                .firstOrNull()
            if (
                !isSupportedExternalPackageReference(
                    uri = uri,
                    displayName = displayName,
                    mimeTypes = listOf(packageMimeType, resolverMimeType),
                )
            ) {
                throw IOException("Unsupported Android package file")
            }

            val extension = supportedPackageExtension(displayName)
                ?: acceptedMimeType?.let(SUPPORTED_MIME_TYPES::get)
                ?: "bin"
            val directory = createWorkingDirectory("incoming").also { incomingDirectory = it }
            val copyLimit = calculateIncomingCopyLimit(declaredSize)
            val outputFile = File(directory, "package.$extension")
            val descriptor = resolver.openAssetFileDescriptor(uri, "r", cancellationSignal)
                ?: throw IOException("Unable to open the package file")
            descriptor.use {
                val input = it.createInputStream().buffered()
                val closeHandle = currentCoroutineContext().job.invokeOnCompletion {
                    runCatching { input.close() }
                }
                try {
                    input.use { stream ->
                        copyIncomingStream(stream, outputFile, copyLimit)
                    }
                } finally {
                    closeHandle.dispose()
                }
            }
            return outputFile
        } finally {
            cancellationHandle.dispose()
        }
    }

    private suspend fun copyLocalPackage(file: File, expectedSha256: String?): File {
        val declaredSize = file.length().takeIf { it >= 0L }
        val extension = file.extension
            .lowercase(Locale.ROOT)
            .takeIf { it in SUPPORTED_EXTENSIONS }
            ?: "bin"
        val directory = createWorkingDirectory("incoming").also { incomingDirectory = it }
        val copyLimit = calculateIncomingCopyLimit(declaredSize)
        val outputFile = File(directory, "package.$extension")
        file.inputStream().buffered().use { input ->
            val actualSha256 = copyIncomingStream(input, outputFile, copyLimit)
            if (expectedSha256 != null &&
                !actualSha256.equals(expectedSha256, ignoreCase = true)
            ) {
                runCatching { outputFile.delete() }
                throw IOException("Package file changed after its details were displayed")
            }
        }
        return outputFile
    }

    private suspend fun copyIncomingStream(
        input: java.io.InputStream,
        outputFile: File,
        copyLimit: Long,
    ): String {
        val coroutineContext = currentCoroutineContext()
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            outputFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                var nextSpaceCheck = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    coroutineContext.ensureActive()
                    if (read < 0) break
                    if (read == 0) continue
                    if (read.toLong() > copyLimit - total) {
                        val message = if (copyLimit < MAX_INCOMING_PACKAGE_BYTES) {
                            "Insufficient cache space to import the package"
                        } else {
                            "Package file exceeds the import size limit"
                        }
                        throw IOException(message)
                    }
                    total += read
                    digest.update(buffer, 0, read)
                    if (total >= nextSpaceCheck) {
                        if (cacheDir.usableSpace <= MINIMUM_CACHE_RESERVE_BYTES) {
                            throw IOException("Insufficient cache space to import the package")
                        }
                        nextSpaceCheck = total + CACHE_SPACE_RECHECK_INTERVAL_BYTES
                    }
                    output.write(buffer, 0, read)
                }
            }
        } catch (error: Throwable) {
            runCatching { outputFile.delete() }
            throw error
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    private fun calculateIncomingCopyLimit(declaredSize: Long?): Long {
        if (declaredSize != null && declaredSize > MAX_INCOMING_PACKAGE_BYTES) {
            throw IOException("Package file exceeds the import size limit")
        }
        val availableForPackage = (cacheDir.usableSpace - MINIMUM_CACHE_RESERVE_BYTES)
            .coerceAtLeast(0L)
        if (declaredSize != null && declaredSize > availableForPackage) {
            throw IOException("Insufficient cache space to import the package")
        }
        return minOf(MAX_INCOMING_PACKAGE_BYTES, availableForPackage).also { copyLimit ->
            if (copyLimit <= 0L) {
                throw IOException("Insufficient cache space to import the package")
            }
        }
    }

    private fun createWorkingDirectory(prefix: String): File {
        val root = File(cacheDir, CACHE_DIRECTORY).apply {
            if (!exists() && !mkdirs()) throw IOException("Unable to create the package cache")
        }
        pruneStaleWorkingDirectories(root)
        return File(root, "$prefix-${UUID.randomUUID()}").apply {
            if (!mkdirs()) throw IOException("Unable to create the package working directory")
        }
    }

    private fun pruneStaleWorkingDirectories(root: File) {
        val cutoff = System.currentTimeMillis() - STALE_DIRECTORY_AGE_MILLIS
        root.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.filter { it.name.startsWith("incoming-") || it.name.startsWith("session-") }
            ?.filter { it.lastModified() in 1 until cutoff }
            ?.forEach { directory ->
                runCatching {
                    if (directory.canonicalFile.parentFile == root.canonicalFile) {
                        directory.deleteRecursively()
                    }
                }
            }
    }

    private fun cleanupIncomingDirectory() {
        val directory = incomingDirectory ?: return
        incomingDirectory = null
        val root = File(cacheDir, CACHE_DIRECTORY).canonicalFile
        val target = runCatching { directory.canonicalFile }.getOrNull() ?: return
        if (target.parentFile == root) {
            target.deleteRecursively()
        }
    }

    private fun showProgress(contentRes: Int) {
        if (isFinishing || isDestroyed) return
        dismissProgress()
        progressDialog = MaterialDialog.Builder(this)
            .content(contentRes)
            .progress(true, 0)
            .cancelable(false)
            .canceledOnTouchOutside(false)
            .show()
    }

    private fun dismissProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun showErrorAndFinish(message: String) {
        if (isFinishing || isDestroyed) return
        dismissProgress()
        ABORT_LISTENERS.forEach { listener -> runCatching { listener(message) } }
        MaterialDialog.Builder(this)
            .title(R.string.text_failed)
            .content(message)
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .dismissListener {
                cleanupIncomingDirectory()
                finish()
            }
            .show()
    }

    companion object {
        private const val ACTION_INSTALL_LOCAL = "org.autojs.autojs.action.INSTALL_LOCAL_PACKAGE"
        private const val ACTION_INSTALL_URI = "org.autojs.autojs.action.INSTALL_PACKAGE_URI"
        private const val ACTION_VIEW_EXTERNAL = "org.autojs.autojs.action.VIEW_EXTERNAL_PACKAGE"
        private const val EXTRA_LOCAL_PATH = "local_package_path"
        private const val EXTRA_EXPECTED_SOURCE_SHA256 = "expected_package_source_sha256"
        private const val CACHE_DIRECTORY = "package-installer"
        private const val MAX_INCOMING_PACKAGE_BYTES = 4L * 1024L * 1024L * 1024L
        private const val MINIMUM_CACHE_RESERVE_BYTES = 128L * 1024L * 1024L
        private const val CACHE_SPACE_RECHECK_INTERVAL_BYTES = 8L * 1024L * 1024L
        private const val MAX_EXTERNAL_IMPORT_DURATION_MILLIS = 30L * 60L * 1000L
        private const val STALE_DIRECTORY_AGE_MILLIS = 24L * 60L * 60L * 1000L
        private val OPERATION_ACTIVE = AtomicBoolean(false)
        private val ABORT_LISTENERS = CopyOnWriteArraySet<(String) -> Unit>()
        private val SUPPORTED_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm", "apkz", "aab")
        private val SUPPORTED_URI_SCHEMES = setOf("content", "file")
        private val SUPPORTED_MIME_TYPES = mapOf(
            "application/vnd.android.package-archive" to "apk",
            "application/x-apks" to "apks",
            "application/vnd.apkm" to "apkm",
            "application/xapk-package-archive" to "xapk",
            "application/x-apkz" to "apkz",
            "application/x-aab" to "aab",
            "application/vnd.android.aab" to "aab",
        )
        /**
         * Observes installations this page aborts with an error dialog. Only the message is
         * reported because installations run one at a time.
         *
         * zh-CN: 监听本页面以错误对话框中止的安装. 安装串行进行, 因此只上报消息.
         */
        internal fun addAbortListener(listener: (String) -> Unit) {
            ABORT_LISTENERS += listener
        }

        internal fun removeAbortListener(listener: (String) -> Unit) {
            ABORT_LISTENERS -= listener
        }

        internal fun supportedMimeType(mimeType: String?): String? = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(SUPPORTED_MIME_TYPES::containsKey)

        private fun supportedPackageExtension(displayName: String?): String? = displayName
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it in SUPPORTED_EXTENSIONS }

        private fun hasSupportedExternalPackageScheme(uri: Uri): Boolean =
            uri.scheme?.lowercase(Locale.ROOT) in SUPPORTED_URI_SCHEMES

        internal fun isSupportedExternalPackageReference(
            uri: Uri,
            displayName: String?,
            mimeTypes: Iterable<String?>,
        ): Boolean =
            hasSupportedExternalPackageScheme(uri) &&
                (
                    supportedPackageExtension(displayName) != null ||
                        supportedPackageExtension(uri.lastPathSegment) != null ||
                        mimeTypes.any { supportedMimeType(it) != null }
                    )

        internal fun openExternalPackage(
            context: Context,
            packageUri: Uri,
            packageMimeType: String? = null,
        ): Boolean =
            Intent(context, PackageInstallerActivity::class.java).apply {
                action = ACTION_VIEW_EXTERNAL
                supportedMimeType(packageMimeType)?.let { mimeType ->
                    setDataAndType(packageUri, mimeType)
                } ?: run {
                    data = packageUri
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.startSafely(context)

        @JvmStatic
        fun install(context: Context, packageFile: File): Boolean =
            install(context, packageFile, expectedSha256 = null)

        internal fun install(
            context: Context,
            packageFile: File,
            expectedSha256: String?,
        ): Boolean =
            Intent(context, PackageInstallerActivity::class.java).apply {
                action = ACTION_INSTALL_LOCAL
                putExtra(EXTRA_LOCAL_PATH, packageFile.absolutePath)
                expectedSha256?.let { putExtra(EXTRA_EXPECTED_SOURCE_SHA256, it) }
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.startSafely(context) {
                Toast.makeText(
                    context,
                    R.string.error_no_applications_available_for_installing_this_file,
                    Toast.LENGTH_LONG,
                ).show()
            }

        @JvmStatic
        fun install(context: Context, packageUri: Uri): Boolean =
            Intent(context, PackageInstallerActivity::class.java).apply {
                action = ACTION_INSTALL_URI
                data = packageUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.startSafely(context) {
                Toast.makeText(
                    context,
                    R.string.error_no_applications_available_for_installing_this_file,
                    Toast.LENGTH_LONG,
                ).show()
            }
    }
}
