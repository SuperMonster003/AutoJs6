package org.autojs.autojs.core.plugin.explorer.output

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.explorer.ExplorerActionLauncher
import org.autojs.autojs.core.plugin.explorer.ExplorerActionOutputPolicy
import org.autojs.autojs.core.plugin.explorer.ExplorerActionPolicy
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.explorer.api.ExplorerActionIntentExtras
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionValues
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal sealed interface ExplorerActionOutputCompletion {
    data class Committed(val file: File) : ExplorerActionOutputCompletion
    data object Cancelled : ExplorerActionOutputCompletion
    data class Failed(val error: Throwable) : ExplorerActionOutputCompletion
}

internal object ExplorerActionOutputCoordinator {

    fun supports(action: ResolvedExplorerAction): Boolean =
        action.descriptor.outputMode == ExplorerActionValues.OUTPUT_CREATE_SIBLING

    /**
     * Prepares a durable transaction and starts the internal result proxy. The caller only
     * learns whether the proxy was accepted; commit or rollback completes asynchronously.
     */
    fun launch(
        context: Context,
        surface: ExplorerSurface,
        item: ExplorerItem,
        action: ResolvedExplorerAction,
    ): Result<Unit> = runCatching {
        require(supports(action)) { "Explorer Action does not declare a transactional output" }
        require(action.descriptor.placement == ExplorerActionValues.PLACEMENT_OVERFLOW) {
            "Explorer Action transactional outputs are limited to overflow actions"
        }
        val sourceSurface = ExplorerActionPolicy.sourceSurfaceValue(surface)
            ?: error("Explorer Action output surface is not allowed: $surface")
        val descriptor = action.descriptor
        require(
            descriptor.outputMimeTypes.isNotEmpty() &&
                descriptor.outputMimeTypes.all(ExplorerActionOutputPolicy.SUPPORTED_IMAGE_MIME_TYPES::contains)
        ) {
            "Explorer Action output MIME types are not supported by the host validator"
        }
        val maxOutputBytes = descriptor.maxOutputBytes
            ?: error("Explorer Action output size limit is missing")
        require(maxOutputBytes in 1L..ExplorerActionBoundedOutputPolicy.HOST_MAX_BYTES) {
            "Explorer Action output size limit exceeds the host image limit"
        }
        val requiredUsableBytes = maxOutputBytes * 2L +
            ExplorerActionBoundedOutputPolicy.MINIMUM_USABLE_BYTES
        require(context.cacheDir.usableSpace >= requiredUsableBytes) {
            "Insufficient storage for an Explorer Action output transaction"
        }
        requirePluginAllowed(context, action.packageName)
        val pluginIdentity = ExplorerActionPluginIdentityResolver.resolve(context, action.packageName)
        val source = File(item.path).canonicalFile
        val sourceParent = source.parentFile?.canonicalFile
            ?: error("Explorer Action output source has no parent directory")
        val sourceParentIdentity = ExplorerActionAndroidFileIdentityReader.stat(sourceParent)
        val transaction = store(context).prepare(
            ExplorerActionOutputPrepareRequest(
                pluginPackageName = action.packageName,
                pluginActivityClassName = action.component.className,
                pluginUid = pluginIdentity.uid,
                pluginVersionCode = pluginIdentity.versionCode,
                pluginLastUpdateTime = pluginIdentity.lastUpdateTime,
                pluginSigningDigests = pluginIdentity.signingDigests,
                actionId = descriptor.id,
                protocolVersion = descriptor.protocolVersion,
                sourcePath = source.path,
                sourceParentDevice = sourceParentIdentity.device,
                sourceParentInode = sourceParentIdentity.inode,
                sourceDisplayName = item.name,
                sourceMimeType = Mime.fromFileOrWildcard(item.path),
                sourceSurface = sourceSurface,
                allowedOutputMimeTypes = descriptor.outputMimeTypes,
                outputNameSuffix = descriptor.outputNameSuffix
                    ?: error("Explorer Action output suffix is missing"),
                maxOutputBytes = maxOutputBytes,
            ),
        )
        try {
            requirePluginIdentity(context, transaction)
            validateSourceIdentity(transaction)
            context.startActivity(ExplorerActionOutputProxyActivity.createIntent(context, transaction.id))
        } catch (error: Throwable) {
            rollback(context, transaction.id)
            throw error
        }
    }

    fun buildPluginIntent(
        context: Context,
        transactionId: String,
    ): Result<Intent> = runCatching {
        val transactionStore = store(context)
        val transaction = transactionStore.load(transactionId)
            ?: error("Explorer Action output transaction is unavailable")
        require(transaction.state == ExplorerActionOutputTransactionState.PREPARED) {
            "Explorer Action output transaction is not ready to launch"
        }
        requirePluginAllowed(context, transaction.pluginPackageName)
        requirePluginIdentity(context, transaction)
        val expectedComponent = ComponentName(
            transaction.pluginPackageName,
            transaction.pluginActivityClassName,
        )
        val actualComponent = ExplorerActionLauncher.resolveAndValidateActivity(
            context = context.applicationContext,
            packageName = transaction.pluginPackageName,
            activityClassName = transaction.pluginActivityClassName,
        ) ?: error("Explorer Action output activity is unavailable")
        check(actualComponent == expectedComponent) { "Explorer Action output activity changed after discovery" }

        val source = validateSourceIdentity(transaction)
        val sourceUri = sourceUri(context, source)
        val outputUri = outputUri(context, transaction.id)
        ExplorerActionOutputGrantManager.grant(
            context = context,
            packageName = transaction.pluginPackageName,
            outputUri = outputUri,
        )
        try {
            Intent(ExplorerActionPluginActions.EXECUTE).apply {
                component = expectedComponent
                setDataAndType(sourceUri, transaction.sourceMimeType)
                // v3 deliberately omits the parent URI. The output URI is an extra with an
                // explicit exact grant, so WRITE can never flow to the input ClipData.
                clipData = ClipData.newRawUri(transaction.sourceDisplayName, sourceUri)
                putExtra(ExplorerActionIntentExtras.ACTION_ID, transaction.actionId)
                putExtra(ExplorerActionIntentExtras.PROTOCOL_VERSION, transaction.protocolVersion)
                putExtra(ExplorerActionIntentExtras.DISPLAY_NAME, transaction.sourceDisplayName)
                putExtra(ExplorerActionIntentExtras.SIZE, transaction.sourceSize)
                putExtra(ExplorerActionIntentExtras.HOST_PACKAGE_NAME, context.packageName)
                putExtra(ExplorerActionIntentExtras.HOST_VERSION_NAME, BuildConfig.VERSION_NAME)
                putExtra(ExplorerActionIntentExtras.HOST_VERSION_CODE, BuildConfig.VERSION_CODE.toLong())
                putExtra(ExplorerActionIntentExtras.SOURCE_SURFACE, transaction.sourceSurface)
                putExtra(ExplorerActionIntentExtras.OUTPUT_TRANSACTION_ID, transaction.id)
                putExtra(ExplorerActionIntentExtras.OUTPUT_URI, outputUri)
                putStringArrayListExtra(
                    ExplorerActionIntentExtras.OUTPUT_MIME_TYPES,
                    ArrayList(transaction.allowedOutputMimeTypes.sorted()),
                )
                putExtra(ExplorerActionIntentExtras.MAX_OUTPUT_BYTES, transaction.maxOutputBytes)
                putExtra(ExplorerActionIntentExtras.OUTPUT_NAME_SUFFIX, transaction.outputNameSuffix)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (error: Throwable) {
            ExplorerActionOutputGrantManager.revokeOutput(
                context,
                transaction.pluginPackageName,
                outputUri,
            )
            throw error
        }
    }

    fun markLaunching(context: Context, transactionId: String): Result<Unit> = runCatching {
        store(context).transition(transactionId, ExplorerActionOutputTransactionState.LAUNCHING)
    }

    fun markLaunched(context: Context, transactionId: String): Result<Unit> = runCatching {
        store(context).transition(transactionId, ExplorerActionOutputTransactionState.LAUNCHED)
    }

    fun openOutputWriter(
        context: Context,
        uri: Uri,
        mode: String,
        callingPackage: String?,
    ): ParcelFileDescriptor {
        val expectedAuthority = ExplorerActionOutputProvider.authority(context.packageName)
        val transactionId = uri.pathSegments.singleOrNull()
            ?.takeIf(ExplorerActionOutputSessionPolicy::isValidTransactionId)
            ?: throw FileNotFoundException("Explorer Action output transaction URI is invalid")
        val transaction = store(context).load(transactionId)
            ?: throw FileNotFoundException("Explorer Action output transaction is unavailable")
        val decision = ExplorerActionOutputSessionPolicy.evaluate(
            scheme = uri.scheme,
            authority = uri.authority,
            expectedAuthority = expectedAuthority,
            pathSegments = uri.pathSegments,
            hasQueryOrFragment = uri.encodedQuery != null || uri.encodedFragment != null,
            mode = mode,
            callingPackage = callingPackage,
            expectedPackage = transaction.pluginPackageName,
            transactionState = transaction.state,
            writerAlreadyOpened = ExplorerActionOutputPipeSessions.hasSession(transactionId),
        )
        if (decision is ExplorerActionOutputOpenDecision.Reject) {
            throw SecurityException("Explorer Action output writer rejected: ${decision.reason}")
        }
        requirePluginAllowed(context, transaction.pluginPackageName)
        requirePluginIdentity(context, transaction)
        require(transaction.maxOutputBytes in 1L..ExplorerActionBoundedOutputPolicy.HOST_MAX_BYTES) {
            "Explorer Action output transaction has an invalid byte limit"
        }
        val stagingFile = store(context).stagingFile(transactionId)
        if (!stagingFile.isFile) {
            throw FileNotFoundException("Explorer Action output staging file is unavailable")
        }
        val writer = ExplorerActionOutputPipeSessions.open(
            transactionId = transactionId,
            stagingFile = stagingFile,
            maxOutputBytes = transaction.maxOutputBytes,
        )
        val stillWritable = runCatching {
            val current = store(context).load(transactionId) ?: return@runCatching false
            current.pluginPackageName == transaction.pluginPackageName &&
                ExplorerActionPluginIdentityResolver.matches(context, current) &&
                current.state in setOf(
                    ExplorerActionOutputTransactionState.LAUNCHING,
                    ExplorerActionOutputTransactionState.LAUNCHED,
                ) &&
                stagingFile.isFile
        }.getOrDefault(false)
        if (!stillWritable) {
            runCatching { writer.closeWithError(WRITER_RACE_CLOSE_REASON) }
            ExplorerActionOutputPipeSessions.cancelAndRemove(
                transactionId,
                WRITER_RACE_CLOSE_REASON,
            )
            throw SecurityException("Explorer Action output transaction stopped before its writer opened")
        }
        return writer
    }

    fun transactionState(
        context: Context,
        transactionId: String,
    ): ExplorerActionOutputTransactionState? = runCatching {
        store(context).load(transactionId)?.state
    }.getOrNull()

    fun abortLaunch(context: Context, transactionId: String) {
        rollback(context, transactionId)
    }

    fun complete(
        context: Context,
        transactionId: String,
        resultCode: Int,
        resultData: Intent?,
    ): ExplorerActionOutputCompletion {
        val transactionStore = store(context)
        val transaction = transactionStore.load(transactionId)
            ?: return ExplorerActionOutputCompletion.Failed(
                IOException("Explorer Action output transaction is unavailable"),
            )
        runCatching {
            requirePluginAllowed(context, transaction.pluginPackageName)
            requirePluginIdentity(context, transaction)
            requireRuntimePolicy(transaction)
        }
            .onFailure { error ->
                rollback(context, transaction.id)
                return ExplorerActionOutputCompletion.Failed(error)
            }
        val decision = ExplorerActionOutputResultPolicy.evaluate(
            expectedTransactionId = transaction.id,
            resultOk = resultCode == Activity.RESULT_OK,
            returnedTransactionId = resultData?.getStringExtra(ExplorerActionIntentExtras.OUTPUT_TRANSACTION_ID),
        )
        if (decision == ExplorerActionOutputResultDecision.Cancel) {
            rollback(context, transaction.id)
            return ExplorerActionOutputCompletion.Cancelled
        }
        if (decision is ExplorerActionOutputResultDecision.Reject) {
            rollback(context, transaction.id)
            return ExplorerActionOutputCompletion.Failed(SecurityException(decision.reason))
        }

        return runCatching {
            require(transaction.state == ExplorerActionOutputTransactionState.LAUNCHED) {
                "Explorer Action output transaction cannot accept a result in state ${transaction.state}"
            }
            when (val drain = ExplorerActionOutputPipeSessions.await(transaction.id, OUTPUT_DRAIN_TIMEOUT_MILLIS)) {
                is ExplorerActionOutputDrainResult.Completed -> require(drain.bytes > 0L) {
                    "Explorer Action output writer produced no content"
                }
                is ExplorerActionOutputDrainResult.Failed -> throw IOException(
                    "Explorer Action output writer failed",
                    drain.error,
                )
                is ExplorerActionOutputDrainResult.Cancelled -> throw IOException(
                    "Explorer Action output writer was cancelled: ${drain.reason}",
                )
                ExplorerActionOutputDrainResult.Missing -> throw IOException(
                    "Explorer Action output writer was never opened",
                )
                ExplorerActionOutputDrainResult.TimedOut -> throw IOException(
                    "Explorer Action output writer did not finish in time",
                )
            }
            transactionStore.transition(transaction.id, ExplorerActionOutputTransactionState.RESULT_RECEIVED)
            revokeGrants(context, transaction)
            ExplorerActionOutputPipeSessions.releaseCompleted(transaction.id)
            continuePostResult(context, transaction.id)
        }.getOrElse { error ->
            resolvePostResultFailure(context, transaction.id, error)
        }
    }

    fun recover(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val transactionStore = store(context)
        val publisher = ExplorerActionOutputPublisher()
        transactionStore.list().forEach { transaction ->
            val committedFileVerified = verifiedPublication(transaction, publisher) != null
            when (
                ExplorerActionOutputRecoveryPolicy.decide(
                    transaction = transaction,
                    nowMillis = nowMillis,
                    stagingExists = transactionStore.stagingFile(transaction.id).isFile,
                    snapshotExists = transactionStore.snapshotFile(transaction.id).isFile,
                    committedFileVerified = committedFileVerified,
                )
            ) {
                ExplorerActionOutputRecoveryAction.KEEP -> Unit
                ExplorerActionOutputRecoveryAction.RESUME -> {
                    closeRecoveredSessionAndGrants(context, transaction)
                    runCatching { continuePostResult(context, transaction.id) }
                        .onFailure { error -> resolvePostResultFailure(context, transaction.id, error) }
                }
                ExplorerActionOutputRecoveryAction.COMPLETE_COMMIT -> {
                    closeRecoveredSessionAndGrants(context, transaction)
                    verifiedPublication(transaction, publisher)?.let { published ->
                        val finalized = finalizeVerifiedPublication(
                            transactionStore,
                            transaction.id,
                            published,
                            nowMillis,
                        )
                        if (finalized) finishCommittedTransaction(context, transaction.id, published.destination)
                    }
                }
                ExplorerActionOutputRecoveryAction.ROLLBACK -> rollback(context, transaction.id)
                ExplorerActionOutputRecoveryAction.CLEANUP -> {
                    closeRecoveredSessionAndGrants(context, transaction)
                    if (transaction.state == ExplorerActionOutputTransactionState.COMMITTED) {
                        verifiedPublication(transaction, publisher)?.destination?.let(::notifyCreated)
                    }
                    cleanupTransactionArtifacts(transactionStore, transaction, publisher)
                }
            }
        }
    }

    private fun continuePostResult(
        context: Context,
        transactionId: String,
    ): ExplorerActionOutputCompletion.Committed {
        val transactionStore = store(context)
        val publisher = ExplorerActionOutputPublisher()
        for (step in 0 until MAX_POST_RESULT_STEPS) {
            val transaction = transactionStore.load(transactionId)
                ?: throw IOException("Explorer Action output transaction is unavailable")
            if (transaction.state != ExplorerActionOutputTransactionState.COMMITTED) {
                requirePluginAllowed(context, transaction.pluginPackageName)
                requirePluginIdentity(context, transaction)
                requireRuntimePolicy(transaction)
            }
            when (transaction.state) {
                ExplorerActionOutputTransactionState.RESULT_RECEIVED -> {
                    transactionStore.createSnapshot(transaction.id)
                }
                ExplorerActionOutputTransactionState.SNAPSHOTTED -> {
                    val validation = validateSnapshot(transactionStore.snapshotFile(transaction.id), transaction)
                    transactionStore.update(transaction.id) { current ->
                        require(current.state == ExplorerActionOutputTransactionState.SNAPSHOTTED) {
                            "Explorer Action output transaction changed during validation"
                        }
                        current.transitionTo(
                            ExplorerActionOutputTransactionState.VALIDATED,
                            System.currentTimeMillis(),
                        ).copy(
                            detectedOutputMimeType = requireNotNull(validation.mimeType),
                            snapshotSha256 = requireNotNull(validation.sha256),
                        )
                    }
                }
                ExplorerActionOutputTransactionState.VALIDATED -> {
                    val snapshot = transactionStore.snapshotFile(transaction.id)
                    validateStoredSnapshot(snapshot, transaction)
                    val source = validateSourceIdentity(transaction)
                    requirePluginAllowed(context, transaction.pluginPackageName)
                    requirePluginIdentity(context, transaction)
                    val plan = publisher.plan(
                        source = source,
                        outputNameSuffix = transaction.outputNameSuffix,
                        mimeType = requireNotNull(transaction.detectedOutputMimeType),
                        expectedParentIdentity = sourceParentIdentity(transaction),
                    )
                    transactionStore.update(transaction.id) { current ->
                        require(current.state == ExplorerActionOutputTransactionState.VALIDATED) {
                            "Explorer Action output transaction changed while planning publication"
                        }
                        current.transitionTo(
                            ExplorerActionOutputTransactionState.COMMITTING,
                            System.currentTimeMillis(),
                        ).copy(
                            finalPath = plan.destination.path,
                            publishTemporaryPath = plan.temporary.path,
                        )
                    }
                }
                ExplorerActionOutputTransactionState.COMMITTING -> {
                    val snapshot = transactionStore.snapshotFile(transaction.id)
                    validateStoredSnapshot(snapshot, transaction)
                    val source = validateSourceIdentity(transaction)
                    requirePluginAllowed(context, transaction.pluginPackageName)
                    requirePluginIdentity(context, transaction)
                    val plan = restorePublishPlan(publisher, source, transaction)
                    if (plan.destination.exists()) {
                        val verified = verifiedPublication(transaction, publisher)
                        if (verified != null) {
                            if (!finalizeVerifiedPublication(transactionStore, transaction.id, verified)) {
                                throw IOException("Unable to finalize the verified Explorer Action output")
                            }
                            continue
                        }
                        publisher.cleanupTemporary(plan)
                        val replacement = publisher.plan(
                            source = source,
                            outputNameSuffix = transaction.outputNameSuffix,
                            mimeType = plan.mimeType,
                            expectedParentIdentity = plan.parentIdentity,
                        )
                        transactionStore.update(transaction.id) { current ->
                            require(current.state == ExplorerActionOutputTransactionState.COMMITTING) {
                                "Explorer Action output transaction changed while replanning publication"
                            }
                            current.copy(
                                finalPath = replacement.destination.path,
                                publishTemporaryPath = replacement.temporary.path,
                                updatedAtMillis = System.currentTimeMillis(),
                            )
                        }
                        continue
                    }
                    publisher.cleanupTemporary(plan)
                    val published = publisher.publish(
                        snapshot = snapshot,
                        plan = plan,
                        expectedSha256 = requireNotNull(transaction.snapshotSha256),
                        maxOutputBytes = transaction.maxOutputBytes,
                    )
                    if (!finalizeVerifiedPublication(transactionStore, transaction.id, published)) {
                        throw IOException("Unable to journal the published Explorer Action output")
                    }
                }
                ExplorerActionOutputTransactionState.COMMITTED -> {
                    val published = verifiedPublication(transaction, publisher)
                        ?: throw IOException("Committed Explorer Action output failed verification")
                    finishCommittedTransaction(context, transaction.id, published.destination)
                    return ExplorerActionOutputCompletion.Committed(published.destination)
                }
                ExplorerActionOutputTransactionState.PREPARED,
                ExplorerActionOutputTransactionState.LAUNCHING,
                ExplorerActionOutputTransactionState.LAUNCHED,
                ExplorerActionOutputTransactionState.ROLLED_BACK,
                -> error("Explorer Action output cannot resume from state ${transaction.state}")
            }
        }
        throw IOException("Explorer Action output transaction exceeded its recovery step limit")
    }

    private fun rollback(context: Context, transactionId: String) {
        val transactionStore = store(context)
        val transaction = transactionStore.load(transactionId)
        if (transaction == null) {
            ExplorerActionOutputPipeSessions.cancelAndRemove(transactionId, ROLLBACK_SESSION_CLOSE_REASON)
            return
        }
        if (
            transaction.state != ExplorerActionOutputTransactionState.COMMITTED &&
            transaction.state != ExplorerActionOutputTransactionState.ROLLED_BACK
        ) {
            runCatching {
                transactionStore.transition(transaction.id, ExplorerActionOutputTransactionState.ROLLED_BACK)
            }
        }
        revokeGrants(context, transaction)
        ExplorerActionOutputPipeSessions.cancelAndRemove(
            transaction.id,
            ROLLBACK_SESSION_CLOSE_REASON,
        )
        cleanupTransactionArtifacts(transactionStore, transactionStore.load(transaction.id) ?: transaction)
    }

    private fun finalizeVerifiedPublication(
        transactionStore: ExplorerActionOutputTransactionStore,
        transactionId: String,
        published: ExplorerActionOutputPublishResult,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = runCatching {
        when (val current = transactionStore.load(transactionId)?.state) {
            ExplorerActionOutputTransactionState.COMMITTING -> {
                transactionStore.update(transactionId) { transaction ->
                    require(transaction.finalPath == published.destination.path) {
                        "Explorer Action output publication path changed"
                    }
                    transaction.transitionTo(
                        ExplorerActionOutputTransactionState.COMMITTED,
                        nowMillis,
                    ).copy(finalSha256 = published.sha256)
                }
            }
            ExplorerActionOutputTransactionState.COMMITTED -> Unit
            else -> error("Explorer Action output cannot finalize publication from state $current")
        }
    }.isSuccess

    private fun resolvePostResultFailure(
        context: Context,
        transactionId: String,
        error: Throwable,
    ): ExplorerActionOutputCompletion {
        val transactionStore = store(context)
        val transaction = runCatching { transactionStore.load(transactionId) }.getOrNull()
        val publisher = ExplorerActionOutputPublisher()
        val durableOutput = transaction?.let { verifiedPublication(it, publisher) }
        if (durableOutput != null) {
            val finalized = finalizeVerifiedPublication(
                transactionStore,
                transactionId,
                durableOutput,
            )
            notifyCreated(durableOutput.destination)
            if (finalized) {
                cleanupTransactionArtifacts(
                    transactionStore,
                    transactionStore.load(transactionId) ?: transaction,
                    publisher,
                )
            }
            return ExplorerActionOutputCompletion.Committed(durableOutput.destination)
        }
        rollback(context, transactionId)
        return ExplorerActionOutputCompletion.Failed(error)
    }

    private fun finishCommittedTransaction(
        context: Context,
        transactionId: String,
        destination: File,
    ) {
        val transactionStore = store(context)
        val transaction = transactionStore.load(transactionId) ?: return
        notifyCreated(destination)
        cleanupTransactionArtifacts(transactionStore, transaction)
    }

    private fun cleanupTransactionArtifacts(
        transactionStore: ExplorerActionOutputTransactionStore,
        transaction: ExplorerActionOutputTransaction,
        publisher: ExplorerActionOutputPublisher = ExplorerActionOutputPublisher(),
    ) {
        val externalTemporaryCleaned = cleanupRecordedTemporary(transaction, publisher)
        transactionStore.cleanupArtifacts(transaction.id, keepJournal = !externalTemporaryCleaned)
    }

    private fun cleanupRecordedTemporary(
        transaction: ExplorerActionOutputTransaction,
        publisher: ExplorerActionOutputPublisher,
    ): Boolean {
        val temporaryPath = transaction.publishTemporaryPath ?: return true
        if (!File(temporaryPath).exists()) return true
        return runCatching {
            val plan = restorePublishPlan(
                publisher,
                File(transaction.sourcePath).canonicalFile,
                transaction,
            )
            publisher.cleanupTemporary(plan)
        }.isSuccess
    }

    private fun closeRecoveredSessionAndGrants(
        context: Context,
        transaction: ExplorerActionOutputTransaction,
    ) {
        ExplorerActionOutputPipeSessions.cancelAndRemove(
            transaction.id,
            RECOVERY_SESSION_CLOSE_REASON,
        )
        revokeGrants(context, transaction)
    }

    private fun revokeGrants(context: Context, transaction: ExplorerActionOutputTransaction) {
        runCatching { outputUri(context, transaction.id) }.getOrNull()?.let { outputUri ->
            ExplorerActionOutputGrantManager.revokeOutput(
                context,
                transaction.pluginPackageName,
                outputUri,
            )
        }
    }

    private fun validateSourceIdentity(transaction: ExplorerActionOutputTransaction): File {
        val source = File(transaction.sourcePath).canonicalFile
        require(source.isFile && source.canRead()) { "Explorer Action output source is no longer readable" }
        val expectedParent = File(transaction.sourceParentPath).canonicalFile
        val actualParent = source.parentFile?.canonicalFile
        require(actualParent == expectedParent) {
            "Explorer Action output source directory changed"
        }
        require(ExplorerActionAndroidFileIdentityReader.stat(expectedParent) == sourceParentIdentity(transaction)) {
            "Explorer Action output source directory identity changed"
        }
        require(source.length() == transaction.sourceSize && source.lastModified() == transaction.sourceLastModified) {
            "Explorer Action output source changed while the plugin was running"
        }
        require(expectedParent.canWrite()) { "Explorer Action output source directory is no longer writable" }
        return source
    }

    private fun validateSnapshot(
        snapshot: File,
        transaction: ExplorerActionOutputTransaction,
    ): ExplorerActionImageOutputValidationResult {
        val validation = ExplorerActionImageOutputValidator.validate(
            file = snapshot,
            allowedMimeTypes = transaction.allowedOutputMimeTypes,
            maxOutputBytes = transaction.maxOutputBytes,
        )
        if (!validation.isValid) {
            throw IOException("Explorer Action output validation failed: ${validation.failure}")
        }
        require(ExplorerActionAndroidImageOutputVerifier.matches(snapshot, validation)) {
            "Explorer Action output failed native image verification"
        }
        return validation
    }

    private fun validateStoredSnapshot(
        snapshot: File,
        transaction: ExplorerActionOutputTransaction,
    ) {
        val validation = validateSnapshot(snapshot, transaction)
        require(validation.mimeType == transaction.detectedOutputMimeType) {
            "Explorer Action output snapshot MIME type changed after validation"
        }
        require(validation.sha256 == transaction.snapshotSha256) {
            "Explorer Action output snapshot changed after validation"
        }
    }

    private fun restorePublishPlan(
        publisher: ExplorerActionOutputPublisher,
        source: File,
        transaction: ExplorerActionOutputTransaction,
    ): ExplorerActionOutputPublishPlan = publisher.restorePlan(
        source = source,
        destination = File(requireNotNull(transaction.finalPath) {
            "Explorer Action output final path is missing"
        }),
        temporary = File(requireNotNull(transaction.publishTemporaryPath) {
            "Explorer Action output temporary path is missing"
        }),
        mimeType = requireNotNull(transaction.detectedOutputMimeType) {
            "Explorer Action output detected MIME type is missing"
        },
        expectedParentIdentity = sourceParentIdentity(transaction),
    )

    private fun verifiedPublication(
        transaction: ExplorerActionOutputTransaction,
        publisher: ExplorerActionOutputPublisher,
    ): ExplorerActionOutputPublishResult? {
        val finalFile = transaction.finalPath?.let(::File) ?: return null
        val expectedHash = transaction.finalSha256 ?: transaction.snapshotSha256 ?: return null
        if (!isSafeFinalPath(transaction, finalFile)) return null
        if (
            !publisher.verifyPublishedFile(
                file = finalFile,
                expectedSha256 = expectedHash,
                maxOutputBytes = transaction.maxOutputBytes,
                expectedParentIdentity = sourceParentIdentity(transaction),
            )
        ) {
            return null
        }
        return ExplorerActionOutputPublishResult(
            destination = finalFile.canonicalFile,
            size = finalFile.length(),
            sha256 = expectedHash,
        )
    }

    private fun sourceParentIdentity(transaction: ExplorerActionOutputTransaction) =
        ExplorerActionFileIdentity(
            device = transaction.sourceParentDevice,
            inode = transaction.sourceParentInode,
        )

    private fun requirePluginAllowed(context: Context, packageName: String) {
        check(PluginEnableStore.isEnabled(context, packageName, defaultEnabled = true)) {
            "Explorer Action plugin is disabled"
        }
        check(PluginTrustManager.isAuthorized(context, packageName)) {
            "Explorer Action plugin is not trusted"
        }
    }

    private fun requirePluginIdentity(
        context: Context,
        transaction: ExplorerActionOutputTransaction,
    ) {
        check(ExplorerActionPluginIdentityResolver.matches(context, transaction)) {
            "Explorer Action plugin identity changed after the output transaction started"
        }
    }

    private fun requireRuntimePolicy(transaction: ExplorerActionOutputTransaction) {
        require(
            transaction.allowedOutputMimeTypes.isNotEmpty() &&
                transaction.allowedOutputMimeTypes.all(
                    ExplorerActionOutputPolicy.SUPPORTED_IMAGE_MIME_TYPES::contains,
                )
        ) { "Explorer Action output transaction has unsupported MIME types" }
        require(transaction.maxOutputBytes in 1L..ExplorerActionOutputPolicy.MAX_HOST_OUTPUT_BYTES) {
            "Explorer Action output transaction has an invalid byte limit"
        }
    }

    private fun isSafeFinalPath(
        transaction: ExplorerActionOutputTransaction,
        finalFile: File,
    ): Boolean = runCatching {
        val canonical = finalFile.canonicalFile
        val source = File(transaction.sourcePath).canonicalFile
        val parent = File(transaction.sourceParentPath).canonicalFile
        canonical != source &&
            canonical.parentFile == parent &&
            ExplorerActionAndroidFileIdentityReader.stat(parent) == sourceParentIdentity(transaction) &&
            canonical.name.contains("_${transaction.outputNameSuffix}-") &&
            ExplorerActionImageOutputValidator.extensionForMimeType(
                requireNotNull(transaction.detectedOutputMimeType),
            )?.let { extension -> canonical.extension.equals(extension, ignoreCase = true) } == true
    }.getOrDefault(false)

    private fun outputUri(context: Context, transactionId: String): Uri =
        ExplorerActionOutputProvider.uri(context.packageName, transactionId)

    private fun sourceUri(context: Context, source: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.$SOURCE_PROVIDER_AUTHORITY_SUFFIX",
            source,
        )

    private fun notifyCreated(file: File) {
        val notify = {
            Explorers.workspace().notifyItemCreated(
                ExplorerFileItem(file, ExplorerDirPage(file.parentFile, null)),
            )
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notify()
        } else {
            Handler(Looper.getMainLooper()).post(notify)
        }
    }

    private fun store(context: Context): ExplorerActionOutputTransactionStore {
        val appContext = context.applicationContext
        val key = appContext.packageName
        return synchronized(storeLock) {
            stores.getOrPut(key) {
                ExplorerActionOutputTransactionStore(
                    journalRoot = File(
                        appContext.noBackupFilesDir,
                        ExplorerActionOutputTransactionStore.JOURNAL_ROOT_DIRECTORY_NAME,
                    ),
                    stagingRoot = File(
                        appContext.cacheDir,
                        ExplorerActionOutputTransactionStore.STAGING_ROOT_DIRECTORY_NAME,
                    ),
                )
            }
        }
    }

    private const val SOURCE_PROVIDER_AUTHORITY_SUFFIX = "fileprovider"
    private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 15_000L
    private const val MAX_POST_RESULT_STEPS = 12
    private const val ROLLBACK_SESSION_CLOSE_REASON = "Explorer Action output transaction rolled back"
    private const val RECOVERY_SESSION_CLOSE_REASON = "Explorer Action output recovery closed the writer"
    private const val WRITER_RACE_CLOSE_REASON = "Explorer Action output transaction is no longer writable"
    private val storeLock = Any()
    private val stores = mutableMapOf<String, ExplorerActionOutputTransactionStore>()
}
