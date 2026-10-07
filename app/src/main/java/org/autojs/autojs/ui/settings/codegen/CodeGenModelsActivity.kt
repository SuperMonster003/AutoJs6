package org.autojs.autojs.ui.settings.codegen

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal as AndroidCancellationSignal
import android.os.OperationCanceledException
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageRejectionReason
import org.autojs.autojs.core.accessibility.codegen.store.CatalogModel
import org.autojs.autojs.core.accessibility.codegen.store.CatalogModelVersion
import org.autojs.autojs.core.accessibility.codegen.store.CodegenModelOperations
import org.autojs.autojs.core.accessibility.codegen.store.DuplicateCheck
import org.autojs.autojs.core.accessibility.codegen.store.IncomingModel
import org.autojs.autojs.core.accessibility.codegen.store.InstalledModel
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalog
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogNetworkException
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogParseException
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogRollbackException
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogSignatureException
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogStorageException
import org.autojs.autojs.core.accessibility.codegen.store.ModelDownloadAuthorizationException
import org.autojs.autojs.core.accessibility.codegen.store.ModelDownloadEvent
import org.autojs.autojs.core.accessibility.codegen.store.ModelDownloadIntegrityException
import org.autojs.autojs.core.accessibility.codegen.store.ModelDownloadSubscription
import org.autojs.autojs.core.accessibility.codegen.store.ModelImportException
import org.autojs.autojs.core.accessibility.codegen.store.ModelInstallException
import org.autojs.autojs.core.accessibility.codegen.store.ModelMirrorsExhaustedException
import org.autojs.autojs.core.accessibility.codegen.store.ModelSource
import org.autojs.autojs.core.accessibility.codegen.store.ModelStoreException
import org.autojs.autojs.core.accessibility.codegen.store.ModelUpdates
import org.autojs.autojs.core.accessibility.codegen.store.ModelVersionInfo
import org.autojs.autojs.core.accessibility.codegen.store.PreparedModelImport
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.floating.codegen.ModelComparisonFormatter
import org.autojs.autojs.ui.floating.codegen.ModelDuplicateDialog
import org.autojs.autojs.ui.floating.codegen.ModelTransferDialog
import org.autojs.autojs.ui.floating.codegen.modelDialogBuilder
import org.autojs.autojs.ui.floating.layoutinspector.InspectorTheme
import org.autojs.autojs.ui.settings.codegen.CodeGenModelsViewModel.RowStatus
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityCodegenModelsBinding
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** Local model management. Network refresh is explicit and never part of selection/deletion. */
class CodeGenModelsActivity : BaseActivity() {
    private lateinit var binding: ActivityCodegenModelsBinding
    private lateinit var colors: InspectorTheme
    private lateinit var formatter: ModelComparisonFormatter
    private val adapter = ModelsAdapter()
    private var services: CodegenModelServices? = null
    private var operations: CodegenModelOperations? = null
    private var snapshot = LocalSnapshot()
    private var catalogError: Throwable? = null
    private var initializing = false
    private var refreshing = false
    private var operationBusy = false
    private var loadGeneration = 0L
    private var transferSequence = 0L
    private var activeTransfer: ActiveTransfer? = null
    private var activeDialog: MaterialDialog? = null
    private var importJob: Job? = null
    private var storeChangesJob: Job? = null
    private var pendingUri: Uri? = null
    private val destroyed = AtomicBoolean()
    private val input = AtomicReference<InputStream?>()
    private val importSignal = AtomicReference<AndroidCancellationSignal?>()
    private val preparedImport = AtomicReference<PreparedModelImport?>()
    private var importCancelled = AtomicBoolean(true)

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            consumePendingUri()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        colors = InspectorTheme(this)
        formatter = ModelComparisonFormatter(this)
        binding = ActivityCodegenModelsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(R.string.text_codegen_models)
        binding.models.layoutManager = LinearLayoutManager(this)
        binding.models.adapter = adapter
        binding.models.itemAnimator = null
        initThemeColors()
        initialize()
    }

    override fun initThemeColors() {
        if (!::binding.isInitialized) return
        colors = InspectorTheme(this)
        binding.root.setBackgroundColor(colors.background)
        binding.progress.indeterminateTintList = ColorStateList.valueOf(colors.control)
        render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_codegen_models, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_codegen_import)?.isEnabled = services != null && !operationBusy
        menu.findItem(R.id.action_codegen_check_updates)?.isEnabled = services != null && !refreshing && !operationBusy
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_codegen_import -> { if (canOperate()) pickModel.launch(arrayOf("*/*")); true }
        R.id.action_codegen_check_updates -> { refreshCatalog(force = true); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun initialize() {
        if (initializing || destroyed.get()) return
        initializing = true
        render()
        val app = applicationContext
        lifecycleScope.launch {
            try {
                val initialized = withContext(Dispatchers.IO) {
                    val shared = CodegenModelServices.get(app)
                    val actions = CodegenModelOperations(shared.store, shared.catalog, BuildConfig.VERSION_CODE, app.cacheDir)
                    Triple(shared, actions, readLocal(shared))
                }
                services = initialized.first
                operations = initialized.second
                snapshot = initialized.third
                observeStoreChanges(initialized.first)
                catalogError = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logFailure("initialize", failure)
                catalogError = failure
            } finally {
                initializing = false
                if (!destroyed.get()) render()
            }
            if (services != null) {
                refreshCatalog(force = false)
                consumePendingUri()
            }
        }
    }

    private fun observeStoreChanges(shared: CodegenModelServices) {
        storeChangesJob?.cancel()
        storeChangesJob = lifecycleScope.launch {
            // Do not skip the initial emission: a commit can finish between readLocal() during
            // initialization and subscription, including one started by the previous Activity.
            shared.storeRevision.collect { if (!destroyed.get()) reloadLocal() }
        }
    }

    private fun refreshCatalog(force: Boolean) {
        val shared = services ?: return
        if (refreshing || destroyed.get()) return
        refreshing = true
        render()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { shared.catalog.refresh(force) }
                catalogError = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logFailure("catalog refresh", failure)
                catalogError = failure
            } finally {
                refreshing = false
                if (!destroyed.get()) reloadLocal()
            }
        }
    }

    /** Called after local operations: this reads cached metadata and performs no refresh. */
    private fun reloadLocal() {
        val shared = services ?: return
        val generation = ++loadGeneration
        lifecycleScope.launch {
            try {
                val local = withContext(Dispatchers.IO) { readLocal(shared) }
                if (generation == loadGeneration) snapshot = local
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logFailure("read local models", failure)
                showError(R.string.text_codegen_model_operation_failed, failure)
            } finally {
                if (!destroyed.get() && generation == loadGeneration) render()
            }
        }
    }

    private fun render() {
        if (!::binding.isInitialized || destroyed.get()) return
        val busyKeys = if (operationBusy) buildSet {
            snapshot.installed.forEach { add(it.key) }
            snapshot.catalog?.models?.forEach { model ->
                model.versions.forEach { add(CodeGenModelsViewModel.downloadKey(model.modelId, it)) }
            }
        } else emptySet()
        val state = CodeGenModelsViewModel.build(snapshot.installed, snapshot.currentKey, snapshot.catalog,
            BuildConfig.VERSION_CODE, busyKeys, initializing || refreshing, catalogError)
        binding.progress.visibility = if (state.loading || operationBusy) View.VISIBLE else View.GONE
        adapter.rows = buildList {
            catalogError?.let { failure ->
                val message = buildList {
                    add(getString(if (services == null) R.string.text_codegen_model_operation_failed else R.string.text_codegen_model_refresh_failed))
                    add(errorReason(failure))
                    if (services != null) add(getString(R.string.text_codegen_model_offline))
                }.joinToString("\n\n")
                add(ListRow.CatalogError(message, !initializing && !refreshing && !operationBusy))
            }
            add(ListRow.Heading(getString(R.string.text_codegen_model_installed_group),
                getString(R.string.text_codegen_model_total_size, formatter.size(state.totalInstalledBytes))))
            if (state.installedGroups.isEmpty()) add(ListRow.Message(if (initializing) R.string.text_codegen_model_loading else R.string.text_codegen_model_no_installed))
            state.installedGroups.forEach { group ->
                add(ListRow.Group(displayName(group.displayName, group.modelId), group.modelId))
                group.rows.forEach { add(ListRow.Installed(it)) }
            }
            add(ListRow.Heading(getString(R.string.text_codegen_model_available_group)))
            if (state.downloadableGroups.isEmpty()) add(ListRow.Message(if (state.loading) R.string.text_codegen_model_loading else R.string.text_codegen_model_no_available))
            state.downloadableGroups.forEach { group ->
                add(ListRow.Group(displayName(group.model.displayName, group.model.modelId), group.model.modelId))
                group.rows.forEach { add(ListRow.Downloadable(it)) }
            }
        }
        invalidateOptionsMenu()
    }

    private fun canOperate(): Boolean {
        if (destroyed.get() || services == null) return false
        if (operationBusy) {
            ViewUtils.showToast(this, R.string.text_codegen_model_busy)
            return false
        }
        return true
    }

    private fun showActions(row: CodeGenModelsViewModel.InstalledRow) {
        if (!canOperate()) return
        val content = column()
        val dialog = modelDialogBuilder(this, false)
            .title(versionLabel(row.installed))
            .customView(content, false)
            .negativeText(R.string.text_close)
            .build()
        fun action(label: Int, enabled: Boolean, run: () -> Unit) {
            content.addView(button(label).apply {
                isEnabled = enabled
                setOnClickListener { dialog.dismiss(); run() }
            }, fullWidth())
        }
        action(R.string.text_codegen_model_set_current, row.canSetCurrent) { setCurrent(row.installed) }
        action(R.string.text_codegen_model_view_card, row.canViewModelCard) { showModelCard(row.installed) }
        action(R.string.text_delete, row.canDelete) { confirmDelete(row.installed) }
        activeDialog = dialog.showAdaptive()
    }

    private fun setCurrent(installed: InstalledModel) {
        if (!canOperate()) return
        val store = requireNotNull(services).store
        performOperation({ check(store.select(installed.key)) }) {}
    }

    private fun confirmDelete(installed: InstalledModel) {
        if (!canOperate()) return
        val label = displayName(installed.manifest.displayName, installed.modelId) + " " + installed.manifest.versionName
        activeDialog = modelDialogBuilder(this, false)
            .title(R.string.text_delete)
            .content(getString(if (snapshot.currentKey == installed.key) R.string.text_codegen_model_delete_current_message else R.string.text_codegen_model_delete_message, label))
            .positiveText(R.string.text_delete)
            .negativeText(R.string.dialog_button_cancel)
            .onPositive { _, _ -> deleteModel(installed.key) }
            .showAdaptive()
    }

    private fun deleteModel(key: String) {
        if (!canOperate()) return
        val store = requireNotNull(services).store
        performOperation({ store.delete(key) }) {
            ViewUtils.showToast(this, R.string.text_codegen_model_deleted_notice)
        }
    }

    private fun showModelCard(installed: InstalledModel) {
        if (!canOperate()) return
        val actions = requireNotNull(operations)
        performOperation({ actions.readModelCard(installed.key) }) { card ->
            activeDialog = modelDialogBuilder(this, false)
                .title(R.string.text_codegen_model_view_card)
                .customView(ScrollView(this).apply {
                    addView(text(card, 14f).apply {
                        setPadding(dp(20), dp(4), dp(20), dp(12))
                        setTextIsSelectable(true)
                    }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }, false)
                .positiveText(R.string.text_close)
                .showAdaptive()
        }
    }

    /** Once an explicit mutation is submitted, Activity destruction only detaches its UI observer. */
    private fun <T> performOperation(action: () -> T, onSuccess: (T) -> Unit) {
        val shared = requireNotNull(services)
        operationBusy = true
        render()
        val future = CompletableFuture.supplyAsync({
            try {
                action()
            } finally {
                shared.notifyStoreChanged()
            }
        }, OPERATIONS)
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { future.get() }
                operationBusy = false
                onSuccess(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showError(R.string.text_codegen_model_operation_failed, unwrap(failure))
            } finally {
                operationBusy = false
                if (!destroyed.get()) render()
            }
        }
    }

    private fun download(row: CodeGenModelsViewModel.DownloadableRow) {
        if (!row.canDownload || !canOperate()) return
        val shared = requireNotNull(services)
        operationBusy = true
        render()
        lifecycleScope.launch {
            try {
                val duplicates = withContext(Dispatchers.IO) {
                    DuplicateCheck.evaluate(IncomingModel.fromCatalog(row.model, row.version), shared.store.listInstalled(), shared.store.current()?.key)
                }
                chooseDuplicate(duplicates, ModelVersionInfo.fromCatalog(row.model, row.version),
                    onInstall = { replaceKey, old -> startTransfer(row.model, row.version, old, replaceKey) },
                    onCancel = { operationBusy = false; render() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                operationBusy = false
                showError(R.string.text_codegen_model_operation_failed, failure)
                render()
            }
        }
    }

    private fun update(row: CodeGenModelsViewModel.InstalledRow) {
        if (!row.canUpdate || !canOperate()) return
        val update = row.update ?: return
        startTransfer(update.model, update.version, update.installed, replaceKey = null, update = update)
    }

    private fun chooseDuplicate(
        result: DuplicateCheck.Result,
        incoming: ModelVersionInfo,
        onInstall: (replaceKey: String?, compareWith: InstalledModel?) -> Unit,
        onCancel: () -> Unit,
    ) {
        when (result) {
            DuplicateCheck.None -> onInstall(null, null)
            is DuplicateCheck.Identical -> activeDialog = ModelDuplicateDialog.showIdentical(this, onClosed = onCancel)
            is DuplicateCheck.SameModel -> activeDialog = ModelDuplicateDialog.showSameModel(this, installedInfo(result.compareWith), incoming) { choice ->
                when (choice) {
                    ModelDuplicateDialog.Choice.REPLACE -> onInstall(result.compareWith.key, result.compareWith)
                    ModelDuplicateDialog.Choice.KEEP_BOTH -> onInstall(null, result.compareWith)
                    ModelDuplicateDialog.Choice.CANCEL -> onCancel()
                }
            }
        }
    }

    private fun startTransfer(
        model: CatalogModel,
        version: CatalogModelVersion,
        old: InstalledModel?,
        replaceKey: String?,
        update: ModelUpdates.Update? = null,
    ) {
        if (destroyed.get()) return
        val shared = requireNotNull(services)
        val transferId = ++transferSequence
        operationBusy = true
        val progress = ModelTransferDialog(this, ModelVersionInfo.fromCatalog(model, version), old?.let(::installedInfo),
            isUpdate = update != null, onCancel = { activeTransfer?.takeIf { it.id == transferId }?.subscription?.cancel() })
        val transfer = ActiveTransfer(transferId, progress, old?.takeIf { update != null })
        activeTransfer = transfer
        render()
        progress.show()
        val subscription = shared.downloader.enqueue(model.modelId, version, { event ->
            if (!destroyed.get() && activeTransfer?.id == transferId) {
                when (event) {
                    is ModelDownloadEvent.Progress -> progress.updateProgress(event.bytesDownloaded, event.totalBytes)
                    ModelDownloadEvent.Cancelled -> {
                        // The future cancels early. This event follows staging cleanup and removal
                        // from the shared downloader, so a retry can now create a fresh transfer.
                        transfer.cancellationCleanupFinished = true
                        finishTransfer(transferId, null, CancellationException())
                    }
                    else -> Unit // Successful updates still wait for their durable selection change.
                }
            }
        }, replaceKey)
        transfer.subscription = subscription
        val store = shared.store
        // This stage belongs to the process. It still switches the current model if the Activity
        // goes away after the downloader has entered its durable installation commit.
        val completed = subscription.future.handleAsync({ installed, error ->
            try {
                if (error != null) throw CompletionException(unwrap(error))
                val completedInstall = requireNotNull(installed)
                if (update != null) ModelUpdates.complete(store, update, completedInstall)
                completedInstall
            } finally {
                shared.notifyStoreChanged()
            }
        }, OPERATIONS)
        val owner = WeakReference(this)
        val main = ContextCompat.getMainExecutor(applicationContext)
        completed.whenComplete { installed, error ->
            main.execute { owner.get()?.finishTransfer(transferId, installed, error) }
        }
    }

    private fun finishTransfer(id: Long, installed: InstalledModel?, error: Throwable?) {
        if (destroyed.get()) return
        val transfer = activeTransfer?.takeIf { it.id == id } ?: return
        if (transfer.finished) return
        val failure = error?.let(::unwrap)
        val cancelledDownload = failure is CancellationException &&
            (transfer.cancellationCleanupFinished || transfer.subscription?.isCancelled == true)
        if (cancelledDownload && !transfer.cancellationCleanupFinished) return
        transfer.finished = true
        transfer.subscription = null
        operationBusy = false
        if (error == null && installed != null) {
            transfer.dialog.showSuccess(installed.manifest.versionName, transfer.old?.let { old -> { deleteModel(old.key) } })
        } else {
            if (cancelledDownload) transfer.dialog.dispose()
            else {
                val actualFailure = requireNotNull(failure)
                logFailure("download", actualFailure)
                transfer.dialog.showFailure(errorReason(actualFailure))
            }
        }
        render()
    }

    private fun consumePendingUri() {
        if (operations == null || operationBusy || destroyed.get()) return
        val uri = pendingUri ?: return
        pendingUri = null
        prepareImport(uri)
    }

    private fun prepareImport(uri: Uri) {
        val actions = requireNotNull(operations)
        val cancellation = AtomicBoolean(false)
        val signal = AndroidCancellationSignal()
        importCancelled = cancellation
        importSignal.set(signal)
        operationBusy = true
        render()
        val loadingDialog = modelDialogBuilder(this, false)
            .title(R.string.text_codegen_model_importing)
            .progress(true, 0)
            .negativeText(R.string.dialog_button_cancel)
            .onNegative { dialog, _ -> cancelImport(); dialog.dismiss() }
            .cancelListener { cancelImport() }
            .showAdaptive()
        activeDialog = loadingDialog
        importJob = lifecycleScope.launch {
            var handedToDecision = false
            try {
                val preview = runInterruptible(Dispatchers.IO) {
                    val displayName = try {
                        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, signal)?.use { cursor ->
                            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
                        }
                    } catch (cancelled: OperationCanceledException) {
                        throw CancellationException("Model import cancelled", cancelled)
                    } catch (failure: Exception) {
                        if (cancellation.get() || destroyed.get() || signal.isCanceled || Thread.currentThread().isInterrupted) {
                            throw CancellationException("Model import cancelled", failure)
                        }
                        null // The filename is optional; inability to read the actual file is handled below.
                    }
                    val origin = displayName?.filterNot(Char::isISOControl)?.substringAfterLast('/')
                        ?.substringAfterLast('\\')?.take(256)?.takeIf { it.isNotBlank() } ?: "model.ajcgm"
                    val descriptor = contentResolver.openAssetFileDescriptor(uri, "r", signal) ?: throw FileNotFoundException()
                    descriptor.use { asset ->
                        val opened = asset.createInputStream()
                        input.set(opened)
                        try {
                            if (cancellation.get() || destroyed.get()) throw CancellationException()
                            val prepared = actions.prepareImport(opened, origin)
                            // Publish before returning across the cancellable dispatcher handoff.
                            preparedImport.set(prepared)
                            if (cancellation.get() || destroyed.get()) throw CancellationException()
                            prepared to actions.duplicates(prepared)
                        } finally {
                            input.compareAndSet(opened, null)
                            opened.close()
                        }
                    }
                }
                loadingDialog.dismiss()
                val prepared = preview.first
                val info = ModelVersionInfo.fromManifest(prepared.manifest, prepared.sha256, prepared.sizeBytes,
                    prepared.source, BuildConfig.VERSION_CODE)
                handedToDecision = true
                chooseDuplicate(preview.second, info,
                    onInstall = { replaceKey, _ -> installPrepared(replaceKey) },
                    onCancel = { discardPrepared(); operationBusy = false; render() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (cancellation.get() || destroyed.get() || failure is OperationCanceledException) {
                    throw CancellationException("Model import cancelled", failure)
                }
                loadingDialog.dismiss()
                showError(R.string.text_codegen_model_import_failed, failure)
            } finally {
                importSignal.compareAndSet(signal, null)
                if (!handedToDecision) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { preparedImport.getAndSet(null)?.close() }.onFailure { logFailure("discard import", it) }
                    }
                    operationBusy = false
                }
                loadingDialog.dismiss()
                importJob = null
                if (!destroyed.get()) render()
            }
        }
    }

    private fun installPrepared(replaceKey: String?) {
        val prepared = preparedImport.getAndSet(null) ?: return
        val actions = requireNotNull(operations)
        performOperation({ prepared.use { actions.install(it, replaceKey) } }) {
            ViewUtils.showToast(this, R.string.text_codegen_model_installed_notice)
        }
    }

    private fun cancelImport() {
        importCancelled.set(true)
        importJob?.cancel()
        val signal = importSignal.getAndSet(null)
        val opened = input.getAndSet(null)
        if (signal != null) OPERATIONS.execute { runCatching { signal.cancel() } }
        if (opened != null) OPERATIONS.execute { runCatching { opened.close() } }
        discardPrepared()
    }

    private fun discardPrepared() {
        val prepared = preparedImport.getAndSet(null)
        if (prepared != null) OPERATIONS.execute { runCatching { prepared.close() } }
    }

    private fun showError(title: Int, failure: Throwable) {
        if (destroyed.get()) return
        logFailure("model operation", failure)
        activeDialog = modelDialogBuilder(this, false)
            .title(title).content(errorReason(failure)).positiveText(R.string.text_close).showAdaptive()
    }

    private fun errorReason(error: Throwable): String {
        val failure = unwrap(error)
        val chain = generateSequence(failure) { it.cause?.takeUnless { next -> next === it } }.take(12).toList()
        val imported = chain.filterIsInstance<ModelImportException>().firstOrNull()
        val rejected = chain.filterIsInstance<ModelInstallException>().firstOrNull()?.rejection?.reason
        val resource = when {
            imported?.reason == ModelImportException.Reason.TOO_LARGE || rejected == ModelPackageRejectionReason.PACKAGE_TOO_LARGE -> R.string.text_codegen_model_reason_too_large
            imported?.reason == ModelImportException.Reason.INCOMPATIBLE || rejected in setOf(ModelPackageRejectionReason.INCOMPATIBLE_SCHEMA, ModelPackageRejectionReason.FEATURE_MISMATCH, ModelPackageRejectionReason.HOST_TOO_OLD) -> R.string.text_codegen_model_reason_incompatible
            imported?.reason == ModelImportException.Reason.INVALID_PACKAGE || rejected != null && rejected != ModelPackageRejectionReason.IO_ERROR || chain.any { it is ModelDownloadIntegrityException } -> R.string.text_codegen_model_reason_invalid_package
            chain.any { it is ModelDownloadAuthorizationException } -> R.string.text_codegen_model_reason_catalog_changed
            chain.any { it is ModelCatalogSignatureException || it is ModelCatalogParseException || it is ModelCatalogRollbackException } -> R.string.text_codegen_model_reason_untrusted_catalog
            chain.any { it is SecurityException } -> R.string.text_codegen_model_reason_permission
            chain.any { it is FileNotFoundException } -> R.string.text_codegen_model_reason_missing
            chain.any { it is ModelCatalogNetworkException || it is ModelMirrorsExhaustedException } -> R.string.text_codegen_model_reason_network
            chain.any { it is ModelCatalogStorageException || it is ModelStoreException || it is IOException } -> R.string.text_codegen_model_reason_storage
            else -> R.string.text_codegen_model_reason_unknown
        }
        return getString(resource)
    }

    private fun installedInfo(installed: InstalledModel): ModelVersionInfo = ModelVersionInfo.fromInstalled(
        installed, BuildConfig.VERSION_CODE, snapshot.currentKey, snapshot.catalog,
    )

    private fun displayName(names: Map<String, String>, fallback: String): String {
        val tag = formatter.locale.toLanguageTag()
        return names.entries.firstOrNull { it.key.equals(tag, true) }?.value
            ?: names["en"] ?: fallback
    }

    private fun versionLabel(installed: InstalledModel): String = versionLabel(installed.manifest.versionName, installed.versionCode)
    private fun versionLabel(name: String, code: Int): String = getString(R.string.text_codegen_model_version_label, name, formatter.integer(code))

    private fun statusLabel(status: RowStatus): String = getString(when (status) {
        RowStatus.AVAILABLE -> R.string.text_codegen_model_compatible
        RowStatus.UPDATE_AVAILABLE -> R.string.text_codegen_model_update_available
        RowStatus.INCOMPATIBLE -> R.string.text_codegen_model_incompatible
        RowStatus.NEEDS_HOST_UPDATE -> R.string.text_codegen_model_needs_host_update
        RowStatus.REVOKED -> R.string.text_codegen_model_revoked
    })

    override fun onDestroy() {
        destroyed.set(true)
        cancelImport()
        activeTransfer?.dialog?.dispose()
        activeTransfer = null
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroy()
    }

    private inner class ModelsAdapter : RecyclerView.Adapter<RowHolder>() {
        var rows: List<ListRow> = emptyList()
            set(value) { field = value; notifyDataSetChanged() }

        override fun getItemCount(): Int = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder = RowHolder(column().apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val content = holder.content
            content.removeAllViews()
            content.setPaddingRelative(dp(20), dp(8), dp(20), dp(8))
            content.setBackgroundColor(colors.background)
            when (val row = rows[position]) {
                is ListRow.CatalogError -> {
                    content.setBackgroundColor(colors.selectionBackground)
                    content.addView(text(row.message, 14f).apply {
                        setTextColor(colors.selectionText)
                        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                    })
                    content.addView(button(R.string.text_codegen_model_retry).apply {
                        setTextColor(colors.selectionAccent)
                        isEnabled = row.canRetry
                        setOnClickListener { if (services == null) initialize() else refreshCatalog(force = true) }
                    }, fullWidth())
                }
                is ListRow.Heading -> {
                    content.setPaddingRelative(dp(16), dp(24), dp(16), dp(8))
                    content.addView(text(row.title, 18f, bold = true).apply {
                        setTextColor(colors.accent)
                        ViewCompat.setAccessibilityHeading(this, true)
                    })
                    row.subtitle?.let { content.addView(text(it, 13f)) }
                }
                is ListRow.Group -> {
                    content.addView(text(row.name, 16f, bold = true))
                    content.addView(text(row.id, 12f).apply { setTextColor(colors.secondaryText) })
                }
                is ListRow.Message -> content.addView(text(getString(row.resource), 14f))
                is ListRow.Installed -> bindInstalled(content, row.value)
                is ListRow.Downloadable -> bindDownloadable(content, row.value)
            }
        }

        private fun bindInstalled(content: LinearLayout, row: CodeGenModelsViewModel.InstalledRow) {
            val installed = row.installed
            content.addView(text(versionLabel(installed), 15f, bold = true))
            if (snapshot.installed.count { it.modelId == installed.modelId && it.versionCode == installed.versionCode } > 1) {
                content.addView(text(installed.sha256.take(12), 12f).apply { setTextColor(colors.secondaryText) })
            }
            content.addView(text(listOf(formatter.source(installed.source), formatter.size(installed.sizeBytes),
                if (row.isBusy) getString(R.string.text_codegen_model_busy) else statusLabel(row.status)).joinToString("\n"), 14f))
            content.addView(AppCompatRadioButton(this@CodeGenModelsActivity).apply {
                text = getString(R.string.text_codegen_model_current)
                textSize = 14f
                setTextColor(colors.text)
                buttonTintList = colors.controls
                isChecked = row.isCurrent
                isEnabled = row.canSetCurrent || row.isCurrent && !row.isBusy
                minHeight = dp(48)
                setOnClickListener {
                    isChecked = row.isCurrent
                    if (row.canSetCurrent) setCurrent(installed)
                }
            }, fullWidth())
            if (row.update != null) content.addView(button(R.string.text_update).apply {
                isEnabled = row.canUpdate
                setOnClickListener { update(row) }
            }, fullWidth())
            content.addView(button(R.string.text_more).apply {
                isEnabled = !row.isBusy
                setOnClickListener { showActions(row) }
            }, fullWidth())
        }

        private fun bindDownloadable(content: LinearLayout, row: CodeGenModelsViewModel.DownloadableRow) {
            content.addView(text(versionLabel(row.version.versionName, row.version.versionCode), 15f, bold = true))
            content.addView(text(listOf(formatter.source(ModelSource.OFFICIAL), formatter.size(row.version.artifact.size),
                if (row.isBusy) getString(R.string.text_codegen_model_busy) else statusLabel(row.status)).joinToString("\n"), 14f))
            content.addView(button(R.string.text_download).apply {
                isEnabled = row.canDownload
                setOnClickListener { download(row) }
            }, fullWidth())
        }
    }

    private class RowHolder(val content: LinearLayout) : RecyclerView.ViewHolder(content)
    private sealed interface ListRow {
        data class CatalogError(val message: String, val canRetry: Boolean) : ListRow
        data class Heading(val title: String, val subtitle: String? = null) : ListRow
        data class Group(val name: String, val id: String) : ListRow
        data class Message(val resource: Int) : ListRow
        data class Installed(val value: CodeGenModelsViewModel.InstalledRow) : ListRow
        data class Downloadable(val value: CodeGenModelsViewModel.DownloadableRow) : ListRow
    }

    private data class LocalSnapshot(
        val installed: List<InstalledModel> = emptyList(),
        val currentKey: String? = null,
        val catalog: ModelCatalog? = null,
    )

    private data class ActiveTransfer(
        val id: Long,
        val dialog: ModelTransferDialog,
        val old: InstalledModel?,
        var subscription: ModelDownloadSubscription? = null,
        var cancellationCleanupFinished: Boolean = false,
        var finished: Boolean = false,
    )

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float, bold: Boolean = false) = AppCompatTextView(this).apply {
        text = value
        textSize = size
        setTextColor(colors.text)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPaddingRelative(0, dp(2), 0, dp(2))
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

    private fun button(resource: Int) = AppCompatButton(this).apply {
        setText(resource)
        isAllCaps = false
        setTextColor(colors.accent)
        gravity = Gravity.CENTER
        minHeight = dp(48)
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "CodeGenModels"
        private val OPERATIONS = Executors.newCachedThreadPool { task -> Thread(task, "CodegenModelOperations").apply { isDaemon = true } }

        @JvmStatic
        @JvmOverloads
        fun launch(context: Context = GlobalAppContext.get()) {
            Intent(context, CodeGenModelsActivity::class.java).startSafely(context)
        }

        private fun readLocal(services: CodegenModelServices): LocalSnapshot = LocalSnapshot(
            services.store.listInstalled(), services.store.current()?.key, services.catalog.current(),
        )

        private fun unwrap(error: Throwable): Throwable = when (error) {
            is CompletionException, is ExecutionException -> error.cause?.let(::unwrap) ?: error
            else -> error
        }

        private fun logFailure(operation: String, failure: Throwable) { Log.w(TAG, operation, failure) }
    }
}
