package org.autojs.autojs.ui.settings.codegen

import android.app.UiAutomation
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.store.InstalledModel
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogConfiguration
import org.autojs.autojs.core.accessibility.codegen.store.ModelDownloadSubscription
import org.autojs.autojs.core.accessibility.codegen.store.ModelSource
import org.autojs.autojs.ui.floating.codegen.ModelComparisonView
import org.autojs.autojs.ui.floating.codegen.ModelTransferDialog
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Opt in with -e codegenModelsDevice true, using a debug APK whose source-only distribution
 * switch is enabled. Exercises the actual Activity, ContentResolver import and signed HTTPS
 * catalog/download on API 24+. The system document picker is a separate manual acceptance item.
 * Existing packages are never cleared: each test tracks its own content and restores selection.
 */
class CodeGenModelsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var activity: CodeGenModelsActivity? = null
    private var services: CodegenModelServices? = null
    private var baseline = emptyMap<String, InstalledModel>()
    private var previousCurrent: String? = null
    private var selectionTouched = false
    private var baselineStages = emptySet<String>()
    private var temporary: File? = null
    private val createdFiles = ArrayList<File>()
    private val ownedHashes = HashSet<String>()
    private val workerReleases = ArrayList<CountDownLatch>()
    private val workerCompletions = ArrayList<CountDownLatch>()
    private val runId = UUID.randomUUID().toString().replace("-", "").take(12)
    private val customId = "autojs6.codegen.device-test-$runId"

    @Before
    fun openTheRealManagementScreenInTheExplicitTestNamespace() {
        assumeTrue("Pass -e codegenModelsDevice true to run model-management device acceptance",
            InstrumentationRegistry.getArguments().getString("codegenModelsDevice") == "true")
        assertTrue("Model-management device acceptance requires a debug APK", BuildConfig.DEBUG)
        val expectedRoot = File(context.noBackupFilesDir, "codegen_models-debug-distribution-test").canonicalFile
        // Check the build selection before constructing services: opening a production store can
        // reconcile it, so detecting the wrong root only after construction would be too late.
        val configuration = selectedConfiguration()
        assertEquals(TEST_REPOSITORY, configuration.expectedRepository)
        assertEquals("p256-fc44588d1cb789dc", configuration.expectedKeyId)
        assertEquals(expectedRoot, configuration.modelDirectory(context.noBackupFilesDir).canonicalFile)
        val shared = CodegenModelServices.get(context)
        assertEquals("Refusing to exercise a production model registry", expectedRoot, shared.store.rootDirectory)
        services = shared
        baseline = shared.store.listInstalled().associateBy { it.key }
        previousCurrent = shared.store.current()?.key
        baselineStages = stageNames()
        Log.i(TAG, "Preserving ${baseline.size} pre-existing packages; current=$previousCurrent")
        temporary = File(context.cacheDir, "codegen-model-device-$runId").apply { check(mkdir()) }

        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        activity = instrumentation.startActivitySync(Intent(context, CodeGenModelsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }) as CodeGenModelsActivity
        waitReady()
    }

    @After
    fun closeOnlyTestWorkAndRestoreTheOriginalSelection() {
        val shared = services ?: return
        workerReleases.forEach { it.countDown() }
        workerCompletions.forEach { assertTrue("A controlled store operation did not finish", it.await(20, TimeUnit.SECONDS)) }
        main {
            activity?.let { owner ->
                activeTransfer(owner)?.let { transfer ->
                    (field(transfer, "subscription") as? ModelDownloadSubscription)?.cancel()
                    (field(transfer, "dialog") as ModelTransferDialog).dispose()
                }
                owner.finish()
            }
        }
        instrumentation.waitForIdleSync()
        // A cancellation can signal before its worker releases staging. Never delete a model
        // directory or staging entry directly, and do not race a late installation commit.
        waitFor("Test import/download did not release staging", 20_000) { stageNames() == baselineStages }
        val ownModels = shared.store.listInstalled().filter { it.key !in baseline && it.sha256 in ownedHashes }
        ownModels.forEach { shared.store.delete(it.key) }
        if (selectionTouched) {
            assertTrue("The original model selection could not be restored", shared.store.select(previousCurrent))
        }
        assertEquals("A device test changed a pre-existing package", baseline,
            shared.store.listInstalled().filter { it.key in baseline }.associateBy { it.key })
        assertEquals("The device test did not restore the original current model", previousCurrent, shared.store.current()?.key)
        assertFalse("A package installed by this test remained registered",
            shared.store.listInstalled().any { it.key !in baseline && it.sha256 in ownedHashes })
        createdFiles.forEach { assertTrue("Unable to remove a test cache input: ${it.name}", !it.exists() || it.delete()) }
        temporary?.let { assertTrue("Unexpected file in the test's own cache directory", it.delete()) }
        activity = null
    }

    @Test
    fun damagedImportShowsAReasonAndLeavesRegistryAndStagingUnchanged() {
        val original = officialInput()
        val bytes = original.readBytes()
        val binary = ZipFile(original).use { archive -> archive.getInputStream(archive.getEntry("model.bin")).use { it.readBytes() } }
        // Mutate exactly one model byte in the genuine published ZIP. Keep the archive, manifest,
        // declared hash and ZIP CRC untouched so the actual import verifier must reject it.
        val start = (0..bytes.size - binary.size).single { offset ->
            bytes[offset] == binary[0] && binary.indices.all { bytes[offset + it] == binary[it] }
        }
        val damaged = bytes.copyOf().apply { this[start + binary.size / 2] = (this[start + binary.size / 2].toInt() xor 1).toByte() }
        assertEquals(1, bytes.indices.count { bytes[it] != damaged[it] })
        val invalid = writeInput("damaged.ajcgm", damaged)
        importThroughContentResolver(invalid)
        val failure = waitDialog(R.string.text_codegen_model_import_failed)
        main {
            assertTrue(texts(failure.view).contains(requireActivity().getString(R.string.text_codegen_model_reason_invalid_package)))
            clickAction(failure, DialogAction.POSITIVE, R.string.text_close)
        }
        waitReady()
        waitNoNewStages()
        assertEquals(baseline, store().listInstalled().associateBy { it.key })
    }

    @Test
    fun customImportCoversIdenticalCancelKeepReplaceCardAndCurrentDeletion() {
        val firstInput = customPackage(1)
        importThroughContentResolver(firstInput)
        val first = waitInstalled(firstInput)
        assertEquals(ModelSource.CUSTOM, first.source)
        chooseCurrent(first)

        importThroughContentResolver(firstInput)
        val identical = waitDialog(R.string.text_codegen_model_identical)
        main {
            assertFalse(descendants(identical.view).any { it is ModelComparisonView })
            assertFalse(identical.getActionButton(DialogAction.NEUTRAL).isShown)
            clickAction(identical, DialogAction.POSITIVE, R.string.text_codegen_model_got_it)
        }
        waitReady()
        waitNoNewStages()
        assertEquals(listOf(first), ownInstalled())

        val secondInput = customPackage(2)
        importThroughContentResolver(secondInput)
        val cancel = waitDialog(R.string.text_codegen_model_same_name)
        assertComparison(cancel)
        main { clickAction(cancel, DialogAction.NEGATIVE, R.string.dialog_button_cancel) }
        waitReady()
        waitNoNewStages()
        assertEquals(listOf(first), ownInstalled())
        assertEquals(first.key, store().current()?.key)

        importThroughContentResolver(secondInput)
        val keep = waitDialog(R.string.text_codegen_model_same_name)
        assertComparison(keep)
        main { clickAction(keep, DialogAction.NEUTRAL, R.string.text_codegen_model_keep_both) }
        val second = waitInstalled(secondInput)
        assertEquals(setOf(first.key, second.key), ownInstalled().map { it.key }.toSet())
        assertEquals("Keep both must preserve the current model", first.key, store().current()?.key)

        val thirdInput = customPackage(3)
        importThroughContentResolver(thirdInput)
        val replace = waitDialog(R.string.text_codegen_model_same_name)
        assertComparison(replace)
        main { clickAction(replace, DialogAction.POSITIVE, R.string.text_codegen_model_replace) }
        val third = waitInstalled(thirdInput)
        waitFor("Replace did not remove the comparison model") { store().listInstalled().none { it.key == first.key } }
        assertEquals(setOf(second.key, third.key), ownInstalled().map { it.key }.toSet())
        assertEquals("Replacing the current version must select its replacement", third.key, store().current()?.key)

        showRowAction(third, R.string.text_codegen_model_view_card)
        val card = waitDialog(R.string.text_codegen_model_view_card)
        main {
            val content = descendants(card.view).filterIsInstance<TextView>().single { it.isTextSelectable }
            assertEquals(customCard(3), content.text.toString())
            assertTrue(content.text.toString().contains("**Literal markdown**"))
            assertTrue(content.text.toString().contains("<script>"))
            assertFalse("Model cards must use a plain text view", descendants(card.view).any { it is WebView })
            clickAction(card, DialogAction.POSITIVE, R.string.text_close)
        }
        waitReady()

        showRowAction(third, R.string.text_delete)
        val deletion = waitDialog(R.string.text_delete)
        main {
            val label = third.manifest.displayName.getValue("en") + " " + third.manifest.versionName
            assertTrue("Deleting the current version must explain rule ranking",
                texts(deletion.view).contains(requireActivity().getString(R.string.text_codegen_model_delete_current_message, label)))
            clickAction(deletion, DialogAction.POSITIVE, R.string.text_delete)
        }
        waitFor("The current version was not deleted") { store().listInstalled().none { it.key == third.key } }
        waitReady()
        assertNull("Deleting the current model must clear selection", store().current())
        assertEquals(listOf(second), ownInstalled())
        waitNoNewStages()
    }

    @Test
    fun officialV1UpdatesDirectlyFromPublishedV2AndRetainsOldUntilExplicitDeletion() {
        assumeTrue("Existing tiny-test packages are preserved; use a device without that test model for update acceptance",
            baseline.values.none { it.modelId == OFFICIAL_ID || it.sha256 in setOf(V1_SHA, V2_SHA) })
        main {
            val toolbar = requireActivity().findViewById<Toolbar>(R.id.toolbar)
            assertTrue("Check updates menu is unavailable", toolbar.menu.performIdentifierAction(R.id.action_codegen_check_updates, 0))
        }
        waitReady()
        val catalog = requireNotNull(requireNotNull(services).catalog.current())
        assertEquals(TEST_REPOSITORY, catalog.repository)
        assertTrue("The real signed remote catalog must authorize downloads", catalog.remoteArtifactsAllowed)
        assertTrue("Publish and promote the test v2 catalog before running this test", catalog.catalogVersion >= 2)
        val catalogModel = requireNotNull(catalog.find(OFFICIAL_ID))
        assertEquals("This acceptance case is pinned to the reviewed v2 update", 2, catalogModel.versions.maxOf { it.versionCode })
        val version = catalogModel.versions.single { it.versionCode == 2 }
        assertEquals(V2_SHA, version.artifact.sha256)
        assertEquals(23_633L, version.artifact.size)
        assertTrue(version.artifact.url.startsWith("https://github.com/$TEST_REPOSITORY/releases/download/codegen-models-v2/"))

        val original = officialInput()
        assertEquals(V1_SHA, sha256(original))
        importThroughContentResolver(original)
        val old = waitInstalled(original)
        assertEquals(ModelSource.OFFICIAL, old.source)
        chooseCurrent(old)
        val row = installedRow(old)
        ownedHashes += V2_SHA
        val progress = main {
            assertTrue(texts(row).any { it.contains(requireActivity().getString(R.string.text_codegen_model_update_available)) })
            clickText(row, R.string.text_update)
            // This check runs in the same main-thread turn as the button click. A confirmation
            // dialog or delayed second action cannot satisfy the immediate transfer assertion.
            val transfer = requireNotNull(activeTransfer(requireActivity())) { "Update did not immediately create a transfer" }
            val dialog = transferDialog(transfer)
            assertTrue(dialog.isShowing)
            assertTrue(texts(dialog.view).contains(requireActivity().getString(R.string.text_codegen_model_updating)))
            assertTrue(descendants(dialog.view).any { it is ProgressBar && it.isShown })
            assertTrue(dialog.getActionButton(DialogAction.NEGATIVE).isShown)
            assertFalse("Update must not ask for duplicate confirmation", activeDialog()?.isShowing == true)
            dialog
        }
        assertComparison(progress)
        waitFor("The real GitHub v2 transfer did not finish", 90_000) {
            val installed = store().listInstalled().firstOrNull { it.sha256 == V2_SHA }
            installed != null && store().current()?.key == installed.key && main {
                progress.isShowing && texts(progress.view).contains(requireActivity().getString(R.string.text_codegen_model_updated, "1.0.1"))
            }
        }
        val newer = store().listInstalled().single { it.sha256 == V2_SHA }
        assertEquals(2, newer.versionCode)
        assertEquals(ModelSource.OFFICIAL, newer.source)
        assertEquals(setOf(old.key, newer.key), ownInstalled().map { it.key }.toSet())
        assertEquals(newer.key, store().current()?.key)
        assertComparison(progress)
        main {
            assertEquals(requireActivity().getString(R.string.text_done), progress.getActionButton(DialogAction.POSITIVE).text.toString())
            clickAction(progress, DialogAction.NEUTRAL, R.string.text_codegen_model_delete_old)
        }
        waitFor("Delete old version did not remove v1") { store().listInstalled().none { it.key == old.key } }
        waitReady()
        assertEquals(newer.key, store().current()?.key)
        assertEquals(listOf(newer), ownInstalled())
        waitNoNewStages()
        Log.i(TAG, "Verified signed GitHub v2 download $V2_SHA; old version removed only by completion action")
    }

    @Test
    fun aStoreOperationCompletingAfterActivityRecreationUpdatesTheNewScreenWithoutRefresh() {
        val input = customPackage(1)
        importThroughContentResolver(input)
        val installed = waitInstalled(input)
        val shared = requireNotNull(services)
        val beforeCurrent = shared.store.current()?.key
        assertFalse("The imported test version should not already be current", installed.key == beforeCurrent)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val action: () -> Unit = {
            entered.countDown()
            try {
                // Wait before entering the store: the new Activity must remain free to read its
                // initial snapshot, otherwise this would only test lock contention.
                check(release.await(60, TimeUnit.SECONDS)) { "Recreated Activity did not release the controlled operation" }
                check(shared.store.select(installed.key))
            } finally {
                completed.countDown()
            }
        }
        val onSuccess: (Unit) -> Unit = {}
        selectionTouched = true
        workerReleases += release
        workerCompletions += completed
        val old = requireActivity()
        main {
            val method = CodeGenModelsActivity::class.java.getDeclaredMethod(
                "performOperation", Function0::class.java, Function1::class.java,
            )
            method.isAccessible = true
            method.invoke(old, action, onSuccess)
        }
        assertTrue("The Activity did not submit the controlled background operation", entered.await(10, TimeUnit.SECONDS))
        main { old.finish() }
        instrumentation.waitForIdleSync()
        activity = instrumentation.startActivitySync(Intent(context, CodeGenModelsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }) as CodeGenModelsActivity
        waitReady()
        assertFalse("Reopening management reused the original Activity instance", old === requireActivity())
        // finish() requests a lifecycle transition; an idle queue or a ready replacement does
        // not mean the old window's asynchronous stop/destroy transaction has completed yet.
        // Keep the commit blocked until destruction actually occurs, so this still proves the
        // process-owned operation survives the disappearance of its originating Activity.
        waitDestroyed(old)
        main {
            assertEquals("The new Activity must first display the state before the commit", beforeCurrent, snapshotCurrent())
        }
        val oldSelectionRow = installedRow(installed)
        main { assertFalse(descendants(oldSelectionRow).filterIsInstance<RadioButton>().single().isChecked) }

        release.countDown()
        assertTrue("The old Activity's persistent operation did not complete", completed.await(20, TimeUnit.SECONDS))
        waitFor("The recreated Activity did not observe the committed current model") {
            main { snapshotCurrent() == installed.key }
        }
        val updatedRow = installedRow(installed)
        waitFor("The recreated screen did not update its radio button") {
            main { descendants(updatedRow).filterIsInstance<RadioButton>().singleOrNull()?.isChecked == true }
        }
        assertEquals(installed.key, shared.store.current()?.key)
        waitNoNewStages()
    }

    private fun selectedConfiguration(): ModelCatalogConfiguration {
        val type = Class.forName("org.autojs.autojs.ui.settings.codegen.CodegenCatalogBuildConfiguration")
        val singleton = type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        val selected = type.getDeclaredMethod("resources").apply { isAccessible = true }.invoke(singleton)
        return field(requireNotNull(selected), "configuration") as ModelCatalogConfiguration
    }

    private fun officialInput(): File = createdFiles.firstOrNull { it.name == "official-v1.ajcgm" }
        ?: writeInput("official-v1.ajcgm", instrumentation.context.assets.open(OFFICIAL_ASSET).use { it.readBytes() })

    private fun customPackage(version: Int): File {
        val result = File(requireNotNull(temporary), "custom-v$version.ajcgm")
        check(!result.exists())
        ZipFile(officialInput()).use { original ->
            ZipOutputStream(result.outputStream()).use { output ->
                val entries = original.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    var bytes = original.getInputStream(entry).use { it.readBytes() }
                    if (entry.name == "manifest.json") bytes = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject.apply {
                        addProperty("modelId", customId)
                        addProperty("versionCode", version)
                        addProperty("versionName", "device.$version")
                        getAsJsonObject("displayName").addProperty("en", "Codegen device import fixture")
                    }.toString().toByteArray(Charsets.UTF_8)
                    if (entry.name == "MODEL_CARD.md") bytes = customCard(version).toByteArray(Charsets.UTF_8)
                    output.putNextEntry(ZipEntry(entry.name).apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = size
                        crc = CRC32().apply { update(bytes) }.value
                        time = 315_532_800_000L
                    })
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        createdFiles += result
        return result
    }

    private fun customCard(version: Int): String = "# Device import fixture\n\nModel $customId, device.$version.\n\n" +
        "**Literal markdown**\n<script>window.codegenTest = true</script>\n[Plain text link](https://example.invalid/codegen)\n"

    private fun writeInput(name: String, bytes: ByteArray): File = File(requireNotNull(temporary), name).also {
        check(it.createNewFile())
        it.writeBytes(bytes)
        createdFiles += it
    }

    /** File URI still goes through the Activity's actual ContentResolver and bounded copy code. */
    private fun importThroughContentResolver(file: File) {
        waitReady()
        ownedHashes += sha256(file)
        main {
            val method = CodeGenModelsActivity::class.java.getDeclaredMethod("prepareImport", Uri::class.java)
            method.isAccessible = true
            method.invoke(requireActivity(), Uri.fromFile(file))
        }
    }

    private fun waitInstalled(input: File): InstalledModel {
        val digest = sha256(input)
        waitFor("Imported model was not installed: ${input.name}") { store().listInstalled().any { it.sha256 == digest } }
        waitReady()
        waitNoNewStages()
        return store().listInstalled().single { it.sha256 == digest }
    }

    private fun chooseCurrent(installed: InstalledModel) {
        val row = installedRow(installed)
        selectionTouched = true
        main {
            val current = descendants(row).filterIsInstance<RadioButton>().single()
            assertTrue("Current model selector is disabled", current.isEnabled)
            assertTrue(current.performClick())
        }
        waitFor("Model selection was not persisted") { store().current()?.key == installed.key }
        waitReady()
        val selectedRow = installedRow(installed)
        waitFor("The selected model was not reflected in its radio button") {
            main { descendants(selectedRow).filterIsInstance<RadioButton>().singleOrNull()?.isChecked == true }
        }
    }

    private fun showRowAction(installed: InstalledModel, action: Int) {
        val row = installedRow(installed)
        main { clickText(row, R.string.text_more) }
        val menu = waitDialogText(requireActivity().getString(R.string.text_codegen_model_set_current))
        main { clickText(menu.view, action) }
    }

    private fun installedRow(installed: InstalledModel): View {
        var result: View? = null
        waitFor("No visible row for ${installed.key}") {
            main {
                val owner = requireActivity()
                val list = owner.findViewById<RecyclerView>(R.id.models)
                val rows = field(requireNotNull(field(owner, "adapter")), "rows") as List<*>
                val position = rows.indexOfFirst { item ->
                    item != null && item.javaClass.simpleName == "Installed" &&
                        (field(item, "value") as CodeGenModelsViewModel.InstalledRow).key == installed.key
                }
                if (position < 0) return@main false
                val holder = list.findViewHolderForAdapterPosition(position)
                if (holder == null) {
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
                    false
                } else {
                    result = holder.itemView
                    !list.isComputingLayout && !list.isLayoutRequested
                }
            }
        }
        return requireNotNull(result)
    }

    private fun waitDialog(resource: Int): MaterialDialog = waitDialogText(requireActivity().getString(resource))

    private fun waitDialogText(value: String): MaterialDialog {
        var found: MaterialDialog? = null
        waitFor("Expected dialog text: $value") {
            main {
                activeDialog()?.takeIf { it.isShowing && texts(it.view).contains(value) }?.also { found = it } != null
            }
        }
        return requireNotNull(found)
    }

    private fun assertComparison(dialog: MaterialDialog) = main {
        val comparison = descendants(dialog.view).filterIsInstance<ModelComparisonView>().single()
        assertTrue(comparison.visibility == View.VISIBLE)
        assertTrue("Version/content differences must have a non-color marker", texts(comparison).any { it.contains("≠") })
    }

    private fun clickText(root: View, resource: Int) {
        val label = requireActivity().getString(resource)
        val button = descendants(root).filterIsInstance<TextView>().single { it.text.toString() == label && it.isClickable }
        assertTrue("Disabled action: $label", button.isEnabled)
        assertTrue("Action was not handled: $label", button.performClick())
    }

    private fun clickAction(dialog: MaterialDialog, action: DialogAction, resource: Int) {
        val button = dialog.getActionButton(action)
        assertEquals(requireActivity().getString(resource), button.text.toString())
        assertTrue("Dialog action is hidden: $action", button.isShown)
        assertTrue("Dialog action is disabled: $action", button.isEnabled)
        assertTrue(button.performClick())
    }

    private fun waitReady() {
        waitFor("Model management did not become ready", 20_000) {
            main {
                val owner = requireActivity()
                field(owner, "services") != null && field(owner, "initializing") == false &&
                    field(owner, "refreshing") == false && field(owner, "operationBusy") == false
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitNoNewStages() = waitFor("Import/decision left a staging file") { stageNames() == baselineStages }

    private fun waitDestroyed(old: CodeGenModelsActivity) {
        val started = SystemClock.uptimeMillis()
        try {
            waitFor("The original Activity did not finish its destruction", 12_000) { main { old.isDestroyed } }
        } catch (failure: AssertionError) {
            throw AssertionError("Original Activity destruction timed out: ${main { destructionState(old) }}; " +
                "replacement=${main { destructionState(requireActivity()) }}", failure)
        }
        Log.i(TAG, "Original Activity destroyed after ${SystemClock.uptimeMillis() - started} ms: ${main { destructionState(old) }}")
    }

    private fun destructionState(owner: CodeGenModelsActivity): String =
        "identity=${System.identityHashCode(owner)}, finishing=${owner.isFinishing}, " +
            "isDestroyed=${owner.isDestroyed}, lifecycle=${owner.lifecycle.currentState}, " +
            "onDestroyEntered=${(field(owner, "destroyed") as AtomicBoolean).get()}"

    private fun stageNames(): Set<String> = File(store().rootDirectory, "staging").listFiles().orEmpty().map { it.name }.toSet()
    private fun ownInstalled(): List<InstalledModel> = store().listInstalled().filter { it.key !in baseline && it.sha256 in ownedHashes }
    private fun store() = requireNotNull(services).store
    private fun requireActivity() = requireNotNull(activity)
    private fun activeDialog(): MaterialDialog? = field(requireActivity(), "activeDialog") as? MaterialDialog
    private fun snapshotCurrent(): String? = field(requireNotNull(field(requireActivity(), "snapshot")), "currentKey") as? String
    private fun activeTransfer(owner: CodeGenModelsActivity): Any? = field(owner, "activeTransfer")
    private fun transferDialog(transfer: Any): MaterialDialog = field(requireNotNull(field(transfer, "dialog")), "dialog") as MaterialDialog
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun texts(root: View): List<String> = descendants(root).filterIsInstance<TextView>().map { it.text.toString() }

    private fun descendants(root: View): List<View> = buildList {
        val queue = ArrayDeque<View>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) queue.addLast(view.getChildAt(index))
        }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun waitFor(message: String, timeout: Long = 12_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue(message, condition())
    }

    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }

    companion object {
        private const val TAG = "CodegenModelsDeviceTest"
        private const val OFFICIAL_ASSET = "codegen/management/official-tiny-v1.ajcgm"
        private const val OFFICIAL_ID = "autojs6.codegen.tiny-test"
        private const val TEST_REPOSITORY = "SuperMonster003/AutoJs6-Codegen-Distribution-Test"
        private const val V1_SHA = "9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17"
        private const val V2_SHA = "908bd23ff3efc04cfe8fc21f83dc0a8fd1713044d3a449f1e778e559cbc91591"
    }
}
