package org.autojs.autojs.ui.explorer

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerFileItem
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerItem
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerPage
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerProvider
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveEntryTooLargeException
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveExtractionFailure
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveExtractionListener
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveExtractionProgress
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveExtractionRequestOptions
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveExtractionResult
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveMutationFailure
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveMutationListener
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveMutationProgress
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveMutationResult
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenInteractionException
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenRequestOptions
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveSessionConnection
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerArchiveOpenValues
import org.autojs.plugin.explorer.api.ExplorerArchiveSessionValues
import java.io.File
import java.io.InterruptedIOException

/** Result of rebuilding an archive index with a different filename charset. */
internal data class ExplorerArchiveReindexResult(
    val restoredPages: List<ArchiveExplorerPage>,
    val restoredSelection: List<ArchiveExplorerItem>,
    val exactPageRestored: Boolean,
    val previousScrollY: Int,
    val previousDirsCollapsed: Boolean,
    val previousFilesCollapsed: Boolean,
)

/**
 * Narrow bridge from the archive workflow into [ExplorerView]. Navigation remains owned by the
 * view, while jobs, dialogs, password lifetime and extraction retry state remain owned here.
 */
internal interface ExplorerArchiveCoordinatorHost {
    val archiveContext: Context
    val archiveLifecycleOwner: LifecycleOwner?

    fun setArchiveBusy(busy: Boolean)
    fun postArchiveUi(action: () -> Unit)
    fun onArchiveOpened(connection: ExplorerArchiveSessionConnection)
    fun isArchiveProviderActive(provider: ArchiveExplorerProvider): Boolean
    fun cancelExplorerRefresh()
    fun applyArchiveReindex(result: ExplorerArchiveReindexResult)
    fun refreshArchivePage()
    fun openMaterializedArchiveEntry(file: File, displayName: String, action: ExplorerPrimaryAction)
    fun onArchiveWorkflowStateChanged()
}

/**
 * Coordinates archive-specific asynchronous UI workflows.
 *
 * This component intentionally does not own Explorer page navigation. Its responsibility is the
 * lifecycle of one archive operation: start, user interaction, retry, cancellation and terminal
 * cleanup. Keeping that state out of [ExplorerView] prevents unrelated navigation and row binding
 * code from mutating operation jobs or retaining password buffers.
 */
internal class ExplorerArchiveCoordinator(
    private val host: ExplorerArchiveCoordinatorHost,
) {

    private var openJob: Job? = null
    private var openUiState: ArchiveOpenUiState? = null
    private var entryOpenJob: Job? = null
    private var filenameReindexJob: Job? = null
    private var extractionStartJob: Job? = null
    private var extractionUiState: ArchiveExtractionUiState? = null
    private var mutationStartJob: Job? = null
    private var mutationRefreshJob: Job? = null
    private var mutationUiState: ArchiveMutationUiState? = null

    val isEntryOpening: Boolean
        get() = entryOpenJob?.isActive == true

    val isFilenameReindexing: Boolean
        get() = filenameReindexJob?.isActive == true

    val isExtracting: Boolean
        get() = extractionUiState != null

    val isMutating: Boolean
        get() = mutationUiState != null

    fun openArchive(
        provider: ExplorerPluginActionProvider,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        action: ResolvedExplorerAction,
    ): Result<Unit> {
        if (host.archiveLifecycleOwner == null) {
            return Result.failure(IllegalStateException("Explorer lifecycle is unavailable"))
        }
        if (openUiState != null || openJob?.isActive == true) return Result.success(Unit)
        val uiState = ArchiveOpenUiState(surface, entryPoint, item, action)
        openUiState = uiState
        startArchiveOpenAttempt(provider, uiState)
        return Result.success(Unit)
    }

    fun openEntry(
        provider: ArchiveExplorerProvider,
        item: ArchiveExplorerFileItem,
        action: ExplorerPrimaryAction?,
    ) {
        if (action == null || !item.canOpenEntry) {
            ViewUtils.showToast(host.archiveContext, R.string.text_unavailable)
            return
        }
        val maxBytes = when (action) {
            ExplorerPrimaryAction.DOCUMENT_PREVIEWER ->
                ExplorerDocumentPreviewerPluginUi.maximumArchiveDocumentBytes()
            ExplorerPrimaryAction.IMAGE_VIEW,
            ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL,
            -> MAX_ARCHIVE_IMAGE_PREVIEW_BYTES
            ExplorerPrimaryAction.MEDIA_PLAY -> MAX_ARCHIVE_MEDIA_PREVIEW_BYTES
        }
        if (item.size > maxBytes) {
            ViewUtils.showToast(
                host.archiveContext,
                host.archiveContext.getString(
                    R.string.error_document_preview_file_too_large,
                    PFiles.formatSizeWithUnit(maxBytes),
                ),
                true,
            )
            return
        }
        if (entryOpenJob?.isActive == true) return
        val lifecycleOwner = host.archiveLifecycleOwner ?: return
        entryOpenJob = lifecycleOwner.lifecycleScope.launch {
            val openingJob = coroutineContext[Job]
            try {
                host.setArchiveBusy(true)
                val materialized = withContext(Dispatchers.IO) {
                    provider.materializeEntry(item, maxBytes)
                }
                if (!host.isArchiveProviderActive(provider)) return@launch
                host.openMaterializedArchiveEntry(materialized, item.name, action)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (host.isArchiveProviderActive(provider)) {
                    Log.e(LOG_TAG, "Failed to open archive entry", error)
                    val message = if (error is ArchiveEntryTooLargeException) {
                        host.archiveContext.getString(
                            R.string.error_document_preview_file_too_large,
                            PFiles.formatSizeWithUnit(error.limitBytes),
                        )
                    } else {
                        host.archiveContext.getString(R.string.text_cannot_read_file)
                    }
                    ViewUtils.showToast(host.archiveContext, message, true)
                }
            } finally {
                if (entryOpenJob === openingJob) {
                    host.setArchiveBusy(false)
                    entryOpenJob = null
                    stateChanged()
                }
            }
        }
        stateChanged()
    }

    fun showFilenameEncodingDialog(
        provider: ArchiveExplorerProvider,
        page: ArchiveExplorerPage,
        selectedItems: List<ArchiveExplorerItem>,
        previousScrollY: Int,
        previousDirsCollapsed: Boolean,
        previousFilesCollapsed: Boolean,
    ) {
        val charsetNames = provider.filenameCharsetNames
        if (charsetNames.isEmpty() || isFilenameReindexing || isEntryOpening || isExtracting) return
        val defaultLabel = buildString {
            append(host.archiveContext.getString(R.string.dialog_button_use_default))
            if (provider.filenameCharsetOverride == null) {
                provider.filenameCharsetName?.let { detected ->
                    append(" (")
                    append(detected)
                    append(')')
                }
            }
        }
        val choices = listOf(defaultLabel) + charsetNames
        val selectedIndex = provider.filenameCharsetOverride
            ?.let { selected ->
                charsetNames.indexOfFirst { name -> name.equals(selected, ignoreCase = true) }
                    .takeIf { it >= 0 }
                    ?.plus(1)
            }
            ?: 0
        MaterialDialog.Builder(host.archiveContext)
            .title(R.string.text_file_encoding)
            .items(choices)
            .itemsCallbackSingleChoice(selectedIndex) { _, _, _, _ -> true }
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .onPositive { dialog, _ ->
                val requested = dialog.selectedIndex
                    .takeIf { it > 0 }
                    ?.let { index -> charsetNames.getOrNull(index - 1) }
                if (requested != provider.filenameCharsetOverride) {
                    reindexFilenameCharset(
                        provider = provider,
                        page = page,
                        selectedItems = selectedItems,
                        filenameCharsetName = requested,
                        previousScrollY = previousScrollY,
                        previousDirsCollapsed = previousDirsCollapsed,
                        previousFilesCollapsed = previousFilesCollapsed,
                    )
                }
            }
            .show()
    }

    fun startExtraction(
        provider: ArchiveExplorerProvider,
        items: List<ArchiveExplorerItem>,
    ) {
        if (extractionUiState != null || items.isEmpty() || host.archiveLifecycleOwner == null) return
        val uiState = ArchiveExtractionUiState(provider, items.toList())
        extractionUiState = uiState
        stateChanged()
        startExtractionAttempt(uiState)
    }

    fun confirmDeletion(
        provider: ArchiveExplorerProvider,
        items: List<ArchiveExplorerItem>,
        page: ArchiveExplorerPage,
        selectedItems: List<ArchiveExplorerItem>,
        previousScrollY: Int,
        previousDirsCollapsed: Boolean,
        previousFilesCollapsed: Boolean,
    ) {
        if (items.isEmpty() || isMutating || isExtracting || isFilenameReindexing) return
        MaterialDialog.Builder(host.archiveContext)
            .title(if (items.size == 1) R.string.text_delete else R.string.text_delete_items)
            .content(R.string.text_confirm_to_delete)
            .positiveText(R.string.text_delete)
            .negativeText(R.string.dialog_button_cancel)
            .onPositive { _, _ ->
                startMutation(
                    provider = provider,
                    command = ArchiveMutationCommand.Delete(items.toList()),
                    navigation = mutationNavigationSnapshot(
                        provider,
                        page,
                        selectedItems,
                        previousScrollY,
                        previousDirsCollapsed,
                        previousFilesCollapsed,
                    ),
                )
            }
            .show()
    }

    fun showRenameDialog(
        provider: ArchiveExplorerProvider,
        item: ArchiveExplorerItem,
        page: ArchiveExplorerPage,
        selectedItems: List<ArchiveExplorerItem>,
        previousScrollY: Int,
        previousDirsCollapsed: Boolean,
        previousFilesCollapsed: Boolean,
    ) {
        if (isMutating || isExtracting || isFilenameReindexing || !item.canRenameEntry) return
        val dialog = MaterialDialog.Builder(host.archiveContext)
            .title(R.string.text_rename)
            .input(host.archiveContext.getString(R.string.text_name), item.name, false) { _, input ->
                val newName = input?.toString().orEmpty()
                if (newName != item.name) {
                    startMutation(
                        provider = provider,
                        command = ArchiveMutationCommand.Rename(item, newName),
                        navigation = mutationNavigationSnapshot(
                            provider,
                            page,
                            selectedItems,
                            previousScrollY,
                            previousDirsCollapsed,
                            previousFilesCollapsed,
                        ),
                    )
                }
            }
            .positiveText(R.string.dialog_button_confirm)
            .negativeText(R.string.dialog_button_cancel)
            .show()
        dialog.inputEditText?.filters = arrayOf(
            InputFilter.LengthFilter(ExplorerActionProtocol.MAX_ARCHIVE_MUTATION_NAME_LENGTH),
        )
    }

    fun showCreateDirectoryDialog(
        provider: ArchiveExplorerProvider,
        page: ArchiveExplorerPage,
        selectedItems: List<ArchiveExplorerItem>,
        previousScrollY: Int,
        previousDirsCollapsed: Boolean,
        previousFilesCollapsed: Boolean,
    ) {
        if (
            isMutating ||
            isExtracting ||
            isFilenameReindexing ||
            !provider.canCreateDirectory ||
            !page.canCreateChildren
        ) {
            return
        }
        val dialog = MaterialDialog.Builder(host.archiveContext)
            .title(R.string.text_new_folder)
            .input(
                host.archiveContext.getString(R.string.text_please_input_name),
                "",
                false,
            ) { _, input ->
                val newName = input?.toString().orEmpty()
                if (newName.isNotBlank()) {
                    startMutation(
                        provider = provider,
                        command = ArchiveMutationCommand.CreateDirectory(page, newName),
                        navigation = mutationNavigationSnapshot(
                            provider,
                            page,
                            selectedItems,
                            previousScrollY,
                            previousDirsCollapsed,
                            previousFilesCollapsed,
                        ),
                    )
                }
            }
            .positiveText(R.string.dialog_button_confirm)
            .negativeText(R.string.dialog_button_cancel)
            .show()
        dialog.inputEditText?.filters = arrayOf(
            InputFilter.LengthFilter(ExplorerActionProtocol.MAX_ARCHIVE_MUTATION_NAME_LENGTH),
        )
    }

    fun showAddEntriesDialog(
        provider: ArchiveExplorerProvider,
        page: ArchiveExplorerPage,
        selectedItems: List<ArchiveExplorerItem>,
        previousScrollY: Int,
        previousDirsCollapsed: Boolean,
        previousFilesCollapsed: Boolean,
    ) {
        if (
            isMutating ||
            isExtracting ||
            isFilenameReindexing ||
            !provider.canAddEntries ||
            !page.canAddChildren
        ) {
            return
        }
        FileChooserDialogBuilder(host.archiveContext)
            .title(R.string.text_add_files_or_folders)
            .chooseFilesAndDirectories()
            .requireExplicitSelection()
            .multiChoice(ExplorerActionProtocol.MAX_ARCHIVE_INPUT_ROOTS) { selected ->
                if (selected.isEmpty()) {
                    ViewUtils.showToast(
                        host.archiveContext,
                        R.string.error_archive_input_selection_empty,
                    )
                    return@multiChoice
                }
                startMutation(
                    provider = provider,
                    command = ArchiveMutationCommand.AddEntries(
                        parent = page,
                        selectedRoots = selected.map { file -> File(file.path) },
                    ),
                    navigation = mutationNavigationSnapshot(
                        provider,
                        page,
                        selectedItems,
                        previousScrollY,
                        previousDirsCollapsed,
                        previousFilesCollapsed,
                    ),
                )
            }
            .show()
    }

    /** Cancels work tied to the currently displayed archive without cancelling a new open request. */
    fun closeArchiveContent() {
        entryOpenJob?.cancel()
        entryOpenJob = null
        filenameReindexJob?.cancel()
        filenameReindexJob = null
        extractionStartJob?.cancel()
        extractionStartJob = null
        dismissExtractionUi()
        mutationStartJob?.cancel()
        mutationStartJob = null
        mutationRefreshJob?.cancel()
        mutationRefreshJob = null
        dismissMutationUi()
        host.setArchiveBusy(false)
        stateChanged()
    }

    /** Releases every plugin workflow when the Explorer view is detached or changes provider. */
    fun release() {
        cancelOpen()
        closeArchiveContent()
    }

    private fun startArchiveOpenAttempt(
        provider: ExplorerPluginActionProvider,
        uiState: ArchiveOpenUiState,
    ) {
        if (openUiState !== uiState || openJob?.isActive == true) return
        val lifecycleOwner = host.archiveLifecycleOwner ?: run {
            finishOpenUi(uiState)
            return
        }
        openJob = lifecycleOwner.lifecycleScope.launch {
            val openingJob = coroutineContext[Job]
            var connection: ExplorerArchiveSessionConnection? = null
            var options: ExplorerArchiveOpenRequestOptions? = null
            try {
                host.setArchiveBusy(true)
                val requestOptions = uiState.requestOptions()
                options = requestOptions
                connection = provider.openArchive(
                    context = host.archiveContext,
                    surface = uiState.surface,
                    entryPoint = uiState.entryPoint,
                    item = uiState.item,
                    action = uiState.action,
                    options = requestOptions,
                )
                host.onArchiveOpened(connection)
                connection = null
                finishOpenUi(uiState)
            } catch (error: CancellationException) {
                finishOpenUi(uiState)
                throw error
            } catch (error: ExplorerArchiveOpenInteractionException) {
                showOpenPasswordDialog(
                    provider = provider,
                    uiState = uiState,
                    wrongPassword = error.interactionKind == ExplorerArchiveOpenValues.ERROR_WRONG_PASSWORD,
                )
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "Failed to open host archive session", error)
                finishOpenUi(uiState)
                ViewUtils.showToast(host.archiveContext, error.message ?: error.toString(), true)
            } finally {
                options?.close()
                connection?.close()
                host.setArchiveBusy(false)
                if (openJob === openingJob) openJob = null
                stateChanged()
            }
        }
        stateChanged()
    }

    private fun showOpenPasswordDialog(
        provider: ExplorerPluginActionProvider,
        uiState: ArchiveOpenUiState,
        wrongPassword: Boolean,
    ) {
        if (openUiState !== uiState) return
        uiState.replacePassword(null)
        val dialog = MaterialDialog.Builder(host.archiveContext)
            .title(R.string.dialog_title_archive_password)
            .content(
                if (wrongPassword) {
                    R.string.error_archive_password_incorrect
                } else {
                    R.string.dialog_message_archive_open_password_required
                },
            )
            .inputType(
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            )
            .input(host.archiveContext.getString(R.string.hint_archive_password), "", false) { current, input ->
                val password = input?.let { value -> CharArray(value.length) { index -> value[index] } }
                current.inputEditText?.text?.clear()
                if (openUiState === uiState && password != null && password.isNotEmpty()) {
                    uiState.replacePassword(password)
                    uiState.interactionDialog = null
                    current.dismiss()
                    startArchiveOpenAttempt(provider, uiState)
                }
                password?.fill('\u0000')
            }
            .positiveText(R.string.dialog_button_continue)
            .negativeText(R.string.dialog_button_cancel)
            .onNegative { current, _ ->
                current.inputEditText?.text?.clear()
                uiState.interactionDialog = null
                current.dismiss()
                finishOpenUi(uiState)
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
        dialog.inputEditText?.filters = arrayOf(
            InputFilter.LengthFilter(ExplorerActionProtocol.MAX_ARCHIVE_PASSWORD_LENGTH),
        )
        uiState.interactionDialog = dialog
    }

    private fun finishOpenUi(uiState: ArchiveOpenUiState) {
        if (openUiState !== uiState) return
        openUiState = null
        uiState.interactionDialog?.inputEditText?.text?.clear()
        uiState.interactionDialog?.dismiss()
        uiState.interactionDialog = null
        uiState.close()
        stateChanged()
    }

    private fun cancelOpen() {
        openJob?.cancel()
        openJob = null
        openUiState?.let(::finishOpenUi)
    }

    private fun reindexFilenameCharset(
        provider: ArchiveExplorerProvider,
        page: ArchiveExplorerPage,
        selectedItems: List<ArchiveExplorerItem>,
        filenameCharsetName: String?,
        previousScrollY: Int,
        previousDirsCollapsed: Boolean,
        previousFilesCollapsed: Boolean,
    ) {
        if (filenameReindexJob?.isActive == true) return
        val lifecycleOwner = host.archiveLifecycleOwner ?: return
        val pageLocator = provider.locatorFor(page)
        val selectionLocators = selectedItems.map(provider::locatorFor)
        host.cancelExplorerRefresh()
        filenameReindexJob = lifecycleOwner.lifecycleScope.launch {
            val reindexingJob = coroutineContext[Job]
            var refreshStarted = false
            try {
                host.setArchiveBusy(true)
                stateChanged()
                val restored = withContext(Dispatchers.IO) {
                    provider.reindexFilenameCharset(filenameCharsetName)
                    val restoredPages = runCatching { provider.restorePage(pageLocator) }
                        .getOrElse { listOf(provider.rootPage) }
                    val restoredSelection = selectionLocators.mapNotNull { locator ->
                        runCatching { provider.restoreItem(locator) }.getOrNull()
                    }
                    Pair(restoredPages, restoredSelection)
                }
                if (!host.isArchiveProviderActive(provider)) return@launch
                val restoredPage = restored.first.last()
                host.applyArchiveReindex(
                    ExplorerArchiveReindexResult(
                        restoredPages = restored.first,
                        restoredSelection = restored.second,
                        exactPageRestored = restoredPage.entryId == pageLocator.lastOrNull(),
                        previousScrollY = previousScrollY,
                        previousDirsCollapsed = previousDirsCollapsed,
                        previousFilesCollapsed = previousFilesCollapsed,
                    ),
                )
                refreshStarted = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (host.isArchiveProviderActive(provider)) {
                    Log.e(LOG_TAG, "Failed to rebuild archive filename index", error)
                    ViewUtils.showToast(host.archiveContext, error.message ?: error.toString(), true)
                    host.refreshArchivePage()
                    refreshStarted = true
                }
            } finally {
                if (filenameReindexJob === reindexingJob) filenameReindexJob = null
                if (!refreshStarted) host.setArchiveBusy(false)
                stateChanged()
            }
        }
        stateChanged()
    }

    private fun mutationNavigationSnapshot(
        provider: ArchiveExplorerProvider,
        page: ArchiveExplorerPage,
        selectedItems: List<ArchiveExplorerItem>,
        previousScrollY: Int,
        previousDirsCollapsed: Boolean,
        previousFilesCollapsed: Boolean,
    ) = ArchiveMutationNavigationSnapshot(
        pageLocator = provider.locatorFor(page),
        selectionLocators = selectedItems.map(provider::locatorFor),
        previousScrollY = previousScrollY,
        previousDirsCollapsed = previousDirsCollapsed,
        previousFilesCollapsed = previousFilesCollapsed,
    )

    private fun startMutation(
        provider: ArchiveExplorerProvider,
        command: ArchiveMutationCommand,
        navigation: ArchiveMutationNavigationSnapshot,
    ) {
        if (mutationUiState != null || host.archiveLifecycleOwner == null) return
        val controller = DialogUtils.OperationController()
        val progressSession = DialogUtils.ProgressDialogSession(controller)
        val uiState = ArchiveMutationUiState(
            provider = provider,
            command = command,
            navigation = navigation,
            controller = controller,
            progressSession = progressSession,
        )
        mutationUiState = uiState
        host.cancelExplorerRefresh()
        host.setArchiveBusy(true)
        stateChanged()
        progressSession.scheduleShow(
            MaterialDialog.Builder(host.archiveContext)
                .title(
                    when (command) {
                        is ArchiveMutationCommand.Delete -> R.string.text_deleting
                        is ArchiveMutationCommand.Rename -> R.string.text_rename
                        is ArchiveMutationCommand.CreateDirectory -> R.string.text_new_folder
                        is ArchiveMutationCommand.AddEntries -> R.string.text_add_files_or_folders
                    },
                )
                .content(R.string.text_preparing)
                .progress(true, 0, true)
                .negativeText(R.string.dialog_button_cancel)
                .onNegative { _, _ -> requestMutationCancellation(uiState) }
                .cancelable(false)
                .autoDismiss(false),
        )
        val listener = object : ExplorerArchiveMutationListener {
            override fun onProgress(progress: ExplorerArchiveMutationProgress) {
                host.postArchiveUi { renderMutationProgress(uiState, progress) }
            }

            override fun onCompleted(result: ExplorerArchiveMutationResult) {
                host.postArchiveUi { completeMutation(uiState, result) }
            }

            override fun onFailed(failure: ExplorerArchiveMutationFailure) {
                host.postArchiveUi { failMutation(uiState, failure) }
            }
        }
        val lifecycleOwner = requireNotNull(host.archiveLifecycleOwner)
        mutationStartJob = lifecycleOwner.lifecycleScope.launch {
            val startingJob = coroutineContext[Job]
            try {
                val operationId = withContext(Dispatchers.IO) {
                    when (command) {
                        is ArchiveMutationCommand.Delete ->
                            provider.startDeletion(command.items, listener)
                        is ArchiveMutationCommand.Rename ->
                            provider.startRename(command.item, command.newName, listener)
                        is ArchiveMutationCommand.CreateDirectory ->
                            provider.startCreateDirectory(
                                command.parent,
                                command.newName,
                                listener,
                            )
                        is ArchiveMutationCommand.AddEntries ->
                            provider.startAddEntries(
                                parent = command.parent,
                                selectedRoots = command.selectedRoots,
                                checkCancelled = {
                                    if (controller.cancelled.get()) {
                                        throw InterruptedIOException(
                                            "Archive input scan was cancelled",
                                        )
                                    }
                                },
                                listener = listener,
                            )
                    }
                }
                uiState.operationId = operationId
                if (controller.cancelled.get()) {
                    withContext(Dispatchers.IO) { provider.cancelMutation(operationId) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "Failed to start host archive mutation", error)
                failMutation(
                    uiState,
                    ExplorerArchiveMutationFailure(
                        if (controller.cancelled.get()) {
                            ExplorerArchiveSessionValues.MUTATION_ERROR_CANCELLED
                        } else {
                            ExplorerArchiveSessionValues.MUTATION_ERROR_UNKNOWN
                        },
                        error.message ?: error.toString(),
                    ),
                )
            } finally {
                if (mutationStartJob === startingJob) mutationStartJob = null
            }
        }
    }

    private fun requestMutationCancellation(uiState: ArchiveMutationUiState) {
        if (mutationUiState !== uiState) return
        uiState.controller.cancel()
        val operationId = uiState.operationId ?: return
        host.archiveLifecycleOwner?.lifecycleScope?.launch(Dispatchers.IO) {
            runCatching { uiState.provider.cancelMutation(operationId) }
                .onFailure { error -> Log.w(LOG_TAG, "Failed to cancel archive mutation", error) }
        }
    }

    private fun renderMutationProgress(
        uiState: ArchiveMutationUiState,
        progress: ExplorerArchiveMutationProgress,
    ) {
        if (mutationUiState !== uiState) return
        val content = buildList {
            add(
                host.archiveContext.getString(
                    if (progress.phase == ExplorerArchiveSessionValues.MUTATION_PHASE_PREPARING) {
                        R.string.text_preparing
                    } else {
                        R.string.text_processing
                    },
                ),
            )
            progress.currentEntry?.let(::add)
        }.toMutableList()
        uiState.progressSession.setContentThrottled(content)
    }

    private fun completeMutation(
        uiState: ArchiveMutationUiState,
        @Suppress("UNUSED_PARAMETER") result: ExplorerArchiveMutationResult,
    ) {
        if (mutationUiState !== uiState || mutationRefreshJob?.isActive == true) return
        val lifecycleOwner = host.archiveLifecycleOwner ?: run {
            finishMutationUi(uiState)
            return
        }
        mutationRefreshJob = lifecycleOwner.lifecycleScope.launch {
            val refreshingJob = coroutineContext[Job]
            var refreshStarted = false
            try {
                val restored = withContext(Dispatchers.IO) {
                    uiState.provider.reloadAfterMutation()
                    val pages = runCatching {
                        uiState.provider.restorePage(uiState.navigation.pageLocator)
                    }.getOrElse { listOf(uiState.provider.rootPage) }
                    val selection = uiState.navigation.selectionLocators.mapNotNull { locator ->
                        runCatching { uiState.provider.restoreItem(locator) }.getOrNull()
                    }
                    pages to selection
                }
                if (!host.isArchiveProviderActive(uiState.provider)) return@launch
                finishMutationUi(uiState, clearBusy = false)
                host.applyArchiveReindex(
                    ExplorerArchiveReindexResult(
                        restoredPages = restored.first,
                        restoredSelection = restored.second,
                        exactPageRestored =
                            restored.first.last().entryId ==
                                uiState.navigation.pageLocator.lastOrNull(),
                        previousScrollY = uiState.navigation.previousScrollY,
                        previousDirsCollapsed = uiState.navigation.previousDirsCollapsed,
                        previousFilesCollapsed = uiState.navigation.previousFilesCollapsed,
                    ),
                )
                refreshStarted = true
                ViewUtils.showToast(host.archiveContext, R.string.text_operation_is_completed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (host.isArchiveProviderActive(uiState.provider)) {
                    Log.e(LOG_TAG, "Failed to reload archive after mutation", error)
                    finishMutationUi(uiState)
                    ViewUtils.showToast(host.archiveContext, error.message ?: error.toString(), true)
                    host.refreshArchivePage()
                    refreshStarted = true
                }
            } finally {
                if (mutationRefreshJob === refreshingJob) mutationRefreshJob = null
                if (!refreshStarted && mutationUiState === uiState) finishMutationUi(uiState)
            }
        }
    }

    private fun failMutation(
        uiState: ArchiveMutationUiState,
        failure: ExplorerArchiveMutationFailure,
    ) {
        if (mutationUiState !== uiState) return
        finishMutationUi(uiState)
        ViewUtils.showToast(
            host.archiveContext,
            if (failure.code == ExplorerArchiveSessionValues.MUTATION_ERROR_CANCELLED) {
                host.archiveContext.getString(R.string.text_operation_aborted)
            } else {
                failure.message
            },
            failure.code != ExplorerArchiveSessionValues.MUTATION_ERROR_CANCELLED,
        )
    }

    private fun finishMutationUi(
        uiState: ArchiveMutationUiState,
        clearBusy: Boolean = true,
    ) {
        if (mutationUiState !== uiState) return
        mutationUiState = null
        uiState.controller.cancel()
        uiState.progressSession.dismissSafely()
        if (clearBusy) host.setArchiveBusy(false)
        stateChanged()
    }

    private fun dismissMutationUi() {
        val uiState = mutationUiState ?: return
        uiState.controller.cancel()
        finishMutationUi(uiState)
    }

    private fun startExtractionAttempt(uiState: ArchiveExtractionUiState) {
        if (extractionUiState !== uiState || uiState.attempt != null) return
        val lifecycleOwner = host.archiveLifecycleOwner ?: run {
            finishExtractionUi(uiState)
            return
        }
        val controller = DialogUtils.OperationController()
        val progressSession = DialogUtils.ProgressDialogSession(controller)
        val attempt = ArchiveExtractionAttempt(controller, progressSession)
        uiState.attempt = attempt

        progressSession.scheduleShow(
            MaterialDialog.Builder(host.archiveContext)
                .title(R.string.text_extracting_archive)
                .content(R.string.text_preparing_archive_extraction)
                .progress(false, DialogUtils.PROGRESS_MAX_LARGE, true)
                .negativeText(R.string.dialog_button_cancel)
                .onNegative { _, _ -> requestExtractionCancellation(uiState, attempt) }
                .cancelable(false)
                .autoDismiss(false),
        )

        val listener = object : ExplorerArchiveExtractionListener {
            override fun onProgress(progress: ExplorerArchiveExtractionProgress) {
                host.postArchiveUi { renderExtractionProgress(uiState, attempt, progress) }
            }

            override fun onCompleted(result: ExplorerArchiveExtractionResult) {
                host.postArchiveUi { completeExtraction(uiState, attempt, result) }
            }

            override fun onFailed(failure: ExplorerArchiveExtractionFailure) {
                host.postArchiveUi { failExtraction(uiState, attempt, failure) }
            }
        }
        extractionStartJob = lifecycleOwner.lifecycleScope.launch {
            val startingJob = coroutineContext[Job]
            var options: ExplorerArchiveExtractionRequestOptions? = null
            try {
                val requestOptions = uiState.requestOptions()
                options = requestOptions
                val operationId = withContext(Dispatchers.IO) {
                    uiState.provider.startExtraction(uiState.items, requestOptions, listener)
                }
                attempt.operationId = operationId
                if (controller.cancelled.get()) {
                    withContext(Dispatchers.IO) { uiState.provider.cancelExtraction(operationId) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "Failed to start host archive extraction", error)
                failExtraction(
                    uiState,
                    attempt,
                    ExplorerArchiveExtractionFailure(
                        ExplorerArchiveSessionValues.EXTRACTION_ERROR_UNKNOWN,
                        error.message ?: error.toString(),
                    ),
                )
            } finally {
                options?.close()
                if (extractionStartJob === startingJob) extractionStartJob = null
            }
        }
    }

    private fun requestExtractionCancellation(
        uiState: ArchiveExtractionUiState,
        attempt: ArchiveExtractionAttempt,
    ) {
        if (extractionUiState !== uiState || uiState.attempt !== attempt) return
        attempt.controller.cancel()
        attempt.progressSession.setContentThrottled(
            mutableListOf(host.archiveContext.getString(R.string.text_cancelling_archive_extraction)),
        )
        val operationId = attempt.operationId ?: return
        host.archiveLifecycleOwner?.lifecycleScope?.launch(Dispatchers.IO) {
            runCatching { uiState.provider.cancelExtraction(operationId) }
                .onFailure { error -> Log.w(LOG_TAG, "Failed to cancel archive extraction", error) }
        }
    }

    private fun renderExtractionProgress(
        uiState: ArchiveExtractionUiState,
        attempt: ArchiveExtractionAttempt,
        progress: ExplorerArchiveExtractionProgress,
    ) {
        if (extractionUiState !== uiState || uiState.attempt !== attempt) return
        val content = when (progress.phase) {
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_PREPARING ->
                mutableListOf(host.archiveContext.getString(R.string.text_preparing_archive_extraction))
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_EXTRACTING -> buildList {
                add(
                    host.archiveContext.getString(
                        R.string.text_archive_extraction_progress,
                        progress.completedEntries,
                        progress.totalEntries,
                    ),
                )
                progress.currentEntry?.let(::add)
            }.toMutableList()
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_COMMITTING ->
                mutableListOf(host.archiveContext.getString(R.string.text_committing_archive_extraction))
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_CLEANING_UP ->
                mutableListOf(host.archiveContext.getString(R.string.text_cleaning_archive_extraction))
            else -> return
        }
        attempt.progressSession.setContentThrottled(content)
        if (progress.totalBytes > 0L) {
            attempt.progressSession.setProgressThrottled(progress.bytesWritten, progress.totalBytes)
        }
    }

    private fun completeExtraction(
        uiState: ArchiveExtractionUiState,
        attempt: ArchiveExtractionAttempt,
        result: ExplorerArchiveExtractionResult,
    ) {
        if (extractionUiState !== uiState || uiState.attempt !== attempt) return
        finishExtractionUi(uiState)
        ViewUtils.showToast(
            host.archiveContext,
            host.archiveContext.getString(R.string.text_archive_extraction_completed, result.outputDisplayPath),
        )
    }

    private fun failExtraction(
        uiState: ArchiveExtractionUiState,
        attempt: ArchiveExtractionAttempt,
        failure: ExplorerArchiveExtractionFailure,
    ) {
        if (extractionUiState !== uiState || uiState.attempt !== attempt) return
        if (
            failure.code == ExplorerArchiveSessionValues.EXTRACTION_ERROR_INTERACTION_REQUIRED &&
            failure.interactionKind != ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_NONE
        ) {
            finishExtractionAttempt(uiState, attempt)
            showExtractionInteraction(uiState, failure.interactionKind)
            return
        }
        finishExtractionUi(uiState)
        val message = when (failure.code) {
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_CANCELLED ->
                host.archiveContext.getString(R.string.text_archive_extraction_cancelled)
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_INTERACTION_REQUIRED ->
                host.archiveContext.getString(R.string.error_archive_extraction_requires_interaction)
            else -> host.archiveContext.getString(R.string.error_archive_extraction_failed, failure.message)
        }
        ViewUtils.showToast(
            host.archiveContext,
            message,
            failure.code != ExplorerArchiveSessionValues.EXTRACTION_ERROR_CANCELLED,
        )
    }

    private fun showExtractionInteraction(uiState: ArchiveExtractionUiState, interactionKind: Int) {
        if (extractionUiState !== uiState) return
        when (interactionKind) {
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_PASSWORD_REQUIRED,
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_WRONG_PASSWORD,
            -> showExtractionPasswordDialog(
                uiState,
                wrongPassword = interactionKind == ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_WRONG_PASSWORD,
            )
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_UNSAFE_PATHS ->
                showExtractionConfirmation(
                    uiState,
                    R.string.dialog_title_archive_unsafe_paths,
                    R.string.dialog_message_archive_skip_unsafe_paths,
                ) { uiState.skipUnsafePaths = true }
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_RESOURCE_BUDGET ->
                showExtractionConfirmation(
                    uiState,
                    R.string.dialog_title_archive_resource_budget,
                    R.string.dialog_message_archive_resource_budget,
                ) { uiState.allowResourceBudgetOverride = true }
            else -> {
                finishExtractionUi(uiState)
                ViewUtils.showToast(
                    host.archiveContext,
                    R.string.error_archive_extraction_requires_interaction,
                    true,
                )
            }
        }
    }

    private fun showExtractionPasswordDialog(
        uiState: ArchiveExtractionUiState,
        wrongPassword: Boolean,
    ) {
        uiState.replacePassword(null)
        val dialog = MaterialDialog.Builder(host.archiveContext)
            .title(R.string.dialog_title_archive_password)
            .content(
                if (wrongPassword) {
                    R.string.error_archive_password_incorrect
                } else {
                    R.string.dialog_message_archive_password_required
                },
            )
            .inputType(
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            )
            .input(host.archiveContext.getString(R.string.hint_archive_password), "", false) { current, input ->
                val password = input?.let { value -> CharArray(value.length) { index -> value[index] } }
                current.inputEditText?.text?.clear()
                if (extractionUiState === uiState && password != null && password.isNotEmpty()) {
                    uiState.replacePassword(password)
                    uiState.interactionDialog = null
                    current.dismiss()
                    startExtractionAttempt(uiState)
                }
                password?.fill('\u0000')
            }
            .positiveText(R.string.dialog_button_continue)
            .negativeText(R.string.dialog_button_cancel)
            .onNegative { current, _ ->
                current.inputEditText?.text?.clear()
                uiState.interactionDialog = null
                current.dismiss()
                finishExtractionUi(uiState)
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
        dialog.inputEditText?.filters = arrayOf(
            InputFilter.LengthFilter(ExplorerActionProtocol.MAX_ARCHIVE_PASSWORD_LENGTH),
        )
        uiState.interactionDialog = dialog
    }

    private fun showExtractionConfirmation(
        uiState: ArchiveExtractionUiState,
        title: Int,
        message: Int,
        confirm: () -> Unit,
    ) {
        val dialog = MaterialDialog.Builder(host.archiveContext)
            .title(title)
            .content(message)
            .positiveText(R.string.dialog_button_continue)
            .negativeText(R.string.dialog_button_cancel)
            .onPositive { current, _ ->
                if (extractionUiState === uiState) {
                    confirm()
                    uiState.interactionDialog = null
                    current.dismiss()
                    startExtractionAttempt(uiState)
                }
            }
            .onNegative { current, _ ->
                uiState.interactionDialog = null
                current.dismiss()
                finishExtractionUi(uiState)
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
        uiState.interactionDialog = dialog
    }

    private fun finishExtractionAttempt(
        uiState: ArchiveExtractionUiState,
        attempt: ArchiveExtractionAttempt,
    ) {
        if (uiState.attempt !== attempt) return
        uiState.attempt = null
        attempt.progressSession.dismissImmediately()
    }

    private fun finishExtractionUi(uiState: ArchiveExtractionUiState) {
        if (extractionUiState !== uiState) return
        extractionUiState = null
        uiState.attempt?.let { attempt ->
            attempt.controller.cancel()
            attempt.progressSession.dismissSafely()
        }
        uiState.attempt = null
        uiState.interactionDialog?.inputEditText?.text?.clear()
        uiState.interactionDialog?.dismiss()
        uiState.interactionDialog = null
        uiState.close()
        stateChanged()
    }

    private fun dismissExtractionUi() {
        val uiState = extractionUiState ?: return
        uiState.attempt?.controller?.cancel()
        finishExtractionUi(uiState)
    }

    private fun stateChanged() = host.onArchiveWorkflowStateChanged()

    private class ArchiveOpenUiState(
        val surface: ExplorerSurface,
        val entryPoint: ExplorerActionEntryPoint,
        val item: ExplorerItem,
        val action: ResolvedExplorerAction,
    ) : AutoCloseable {
        var interactionDialog: MaterialDialog? = null
        private var passwordValue: CharArray? = null

        @Synchronized
        fun replacePassword(password: CharArray?) {
            passwordValue?.fill('\u0000')
            passwordValue = password?.takeIf(CharArray::isNotEmpty)?.clone()
        }

        @Synchronized
        fun requestOptions(): ExplorerArchiveOpenRequestOptions {
            val password = passwordValue?.clone()
            return try {
                ExplorerArchiveOpenRequestOptions(password)
            } finally {
                password?.fill('\u0000')
            }
        }

        @Synchronized
        override fun close() {
            passwordValue?.fill('\u0000')
            passwordValue = null
        }
    }

    private class ArchiveExtractionAttempt(
        val controller: DialogUtils.OperationController,
        val progressSession: DialogUtils.ProgressDialogSession,
    ) {
        @Volatile
        var operationId: String? = null
    }

    private class ArchiveExtractionUiState(
        val provider: ArchiveExplorerProvider,
        val items: List<ArchiveExplorerItem>,
    ) : AutoCloseable {
        var attempt: ArchiveExtractionAttempt? = null
        var interactionDialog: MaterialDialog? = null
        var skipUnsafePaths: Boolean = false
        var allowResourceBudgetOverride: Boolean = false
        private var passwordValue: CharArray? = null

        @Synchronized
        fun replacePassword(password: CharArray?) {
            passwordValue?.fill('\u0000')
            passwordValue = password?.takeIf(CharArray::isNotEmpty)?.clone()
        }

        @Synchronized
        fun requestOptions(): ExplorerArchiveExtractionRequestOptions {
            val password = passwordValue?.clone()
            return try {
                ExplorerArchiveExtractionRequestOptions(
                    password = password,
                    skipUnsafePaths = skipUnsafePaths,
                    allowResourceBudgetOverride = allowResourceBudgetOverride,
                )
            } finally {
                password?.fill('\u0000')
            }
        }

        @Synchronized
        override fun close() {
            passwordValue?.fill('\u0000')
            passwordValue = null
        }
    }

    private sealed interface ArchiveMutationCommand {
        data class Delete(val items: List<ArchiveExplorerItem>) : ArchiveMutationCommand
        data class Rename(
            val item: ArchiveExplorerItem,
            val newName: String,
        ) : ArchiveMutationCommand
        data class CreateDirectory(
            val parent: ArchiveExplorerPage,
            val newName: String,
        ) : ArchiveMutationCommand
        data class AddEntries(
            val parent: ArchiveExplorerPage,
            val selectedRoots: List<File>,
        ) : ArchiveMutationCommand
    }

    private data class ArchiveMutationNavigationSnapshot(
        val pageLocator: List<String>,
        val selectionLocators: List<List<String>>,
        val previousScrollY: Int,
        val previousDirsCollapsed: Boolean,
        val previousFilesCollapsed: Boolean,
    )

    private class ArchiveMutationUiState(
        val provider: ArchiveExplorerProvider,
        val command: ArchiveMutationCommand,
        val navigation: ArchiveMutationNavigationSnapshot,
        val controller: DialogUtils.OperationController,
        val progressSession: DialogUtils.ProgressDialogSession,
    ) {
        @Volatile
        var operationId: String? = null
    }

    private companion object {
        private const val LOG_TAG = "ExplorerArchive"
        private const val MAX_ARCHIVE_IMAGE_PREVIEW_BYTES = 256L * 1024L * 1024L
        private const val MAX_ARCHIVE_MEDIA_PREVIEW_BYTES = 512L * 1024L * 1024L
    }
}
