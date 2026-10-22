package org.autojs.autojs.core.pref.deploy

import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.SettingEffect
import org.autojs.autojs.core.pref.archive.ImportEnvironment
import org.autojs.autojs.core.pref.archive.ImportPlan
import org.autojs.autojs.core.pref.archive.ImportPlan.Prerequisite
import org.autojs.autojs.core.pref.archive.ImportPlan.SkipReason
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * docs/settings/ROADMAP.md P7.6: the confirmations of the deployment items (T18) follow what the environment probe says
 * now, given up and blocked items are `DECLINED` (R8), confirmed ones run and only then write their keys; the two items
 * of the first phase create a directory and install a font; every deployment kind has an implementation (guard e).
 *
 * zh-CN: ROADMAP P7.6: 部署项 (T18) 的确认依据当前的环境探测, 放弃与被阻止的项计为 `DECLINED` (R8), 确认的项运行成功后才写入其键;
 * 第一期的两种部署项分别创建目录与安装字体; 每个部署种类都有实现 (守卫 e).
 */
class DeploymentPlannerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val directory = ImportPlan.Deployment(DeploymentKind.CREATE_DIRECTORY, "explorer", StoreId.DEFAULT, "key_\$_working_directory", "Projects/Imported", SettingEffect.NONE)

    private val font = ImportPlan.Deployment(
        DeploymentKind.DOWNLOAD_EDITOR_FONT, "editor_appearance", StoreId.DEFAULT, "key_\$_ace_editor_font", "jetbrains_mono", SettingEffect.NONE,
        setOf(Prerequisite.ACE_EDITOR_PLUGIN),
    )

    private val plan = ImportPlan(listOf("explorer", "editor_appearance"), emptyList(), emptyList(), emptyList(), listOf(directory, font))

    private fun environment(ace: Boolean, deployed: Set<DeploymentKind> = emptySet()) =
        ImportEnvironment(emptySet(), ace) { kind, _ -> kind in deployed }

    @Test
    fun theEnvironmentProbeGivesTheConfirmations() {
        val pending = DeploymentPlanner.pending(plan, environment(ace = false))
        assertEquals(listOf(directory, font), pending.map { it.deployment })
        assertEquals(listOf(false, true), pending.map { it.blocked })
        assertEquals(listOf(emptySet(), setOf(Prerequisite.ACE_EDITOR_PLUGIN)), pending.map { it.prerequisites })
        assertEquals(listOf("default:key_\$_working_directory", "default:key_\$_ace_editor_font"), pending.map { it.key })
        // The plugin installed meanwhile unblocks the font; a directory created meanwhile needs no confirmation.
        val later = DeploymentPlanner.pending(plan, environment(ace = true, deployed = setOf(DeploymentKind.CREATE_DIRECTORY)))
        assertEquals(listOf(font), later.map { it.deployment })
        assertFalse(later.single().blocked)
    }

    @Test
    fun givenUpAndBlockedItemsAreDeclined() {
        val keys = DeploymentPlanner.pending(plan, environment(ace = false)).map { it.key }
        // Both confirmed, but the font waits for the Ace editor plugin.
        val decided = DeploymentPlanner.decide(plan, keys, environment(ace = false))
        assertEquals(listOf(directory), decided.deployments)
        assertEquals(listOf(ImportPlan.Skip("editor_appearance", "default", "key_\$_ace_editor_font", SkipReason.DECLINED)), decided.skips)
        // Nothing confirmed: both are given up and keep their current values.
        val declined = DeploymentPlanner.decide(plan, emptyList(), environment(ace = true))
        assertTrue(declined.deployments.isEmpty())
        assertEquals(mapOf(SkipReason.DECLINED to 2), declined.skipCounts.filterValues { it > 0 })
        assertTrue(declined.puts.isEmpty())
        // A deployment satisfied meanwhile is written directly, whatever the choice.
        val settled = DeploymentPlanner.decide(plan, emptyList(), environment(ace = true, deployed = setOf(DeploymentKind.DOWNLOAD_EDITOR_FONT)))
        assertEquals(listOf(font.toPut()), settled.puts)
        assertEquals(listOf("key_\$_working_directory"), settled.skips.map { it.name })
        // Once an item has run, the plan of its key writes it.
        val completed = DeploymentPlanner.completed(decided, listOf(directory))
        assertEquals(listOf(directory.toPut()), completed.puts)
        assertTrue(completed.removes.isEmpty() && completed.deployments.isEmpty())
    }

    @Test
    fun theWorkingDirectoryItemCreatesTheDirectory() = runBlocking {
        val target = File(folder.root, "Projects/Imported")
        var access = true
        val item = CreateWorkingDirectoryItem(directory, target) { access }
        assertFalse(item.check())
        assertEquals(DeploymentItem.Description(DeploymentKind.CREATE_DIRECTORY, target.path), item.describe())
        val progress = ArrayList<Float?>()
        item.run { progress.add(it) }
        assertTrue(target.isDirectory)
        assertTrue(item.check())
        assertEquals(listOf(null, 1f), progress)
        // A path under a file cannot be created: IO with storage access, PERMISSION without it.
        val blocker = folder.newFile("blocker")
        val broken = CreateWorkingDirectoryItem(directory, File(blocker, "child")) { access }
        assertEquals(DeploymentException.Reason.IO, assertThrows(DeploymentException::class.java) { runBlocking { broken.run {} } }.reason)
        access = false
        assertEquals(setOf(DeploymentItem.Permission.ALL_FILES_ACCESS), broken.describe().permissions)
        assertEquals(DeploymentException.Reason.PERMISSION, assertThrows(DeploymentException::class.java) { runBlocking { broken.run {} } }.reason)
    }

    @Test
    fun theFontItemNeedsTheAcePluginAndInstallsTheFont() = runBlocking {
        val installed = HashSet<String>()
        val fonts = object : EditorFontInstaller {
            override fun isInstalled(fontId: String) = fontId in installed
            override fun displayName(fontId: String) = "JetBrains Mono"
            override suspend fun install(fontId: String, progress: (Float?) -> Unit) {
                if (fontId != "jetbrains_mono") throw DeploymentException(DeploymentException.Reason.UNAVAILABLE, fontId)
                progress(0.5f)
                installed.add(fontId)
            }
        }
        var ace = false
        val item = DownloadEditorFontItem(font, fonts) { ace }
        assertEquals(setOf(Prerequisite.ACE_EDITOR_PLUGIN), item.describe().prerequisites)
        assertEquals("JetBrains Mono", item.describe().target)
        assertEquals(DeploymentException.Reason.PREREQUISITE, assertThrows(DeploymentException::class.java) { runBlocking { item.run {} } }.reason)
        ace = true
        val progress = ArrayList<Float?>()
        item.run { progress.add(it) }
        assertTrue(item.check())
        assertEquals(listOf(0.5f, 1f), progress)
        val missing = DownloadEditorFontItem(font.copy(value = "no_such_font"), fonts) { true }
        assertEquals(DeploymentException.Reason.UNAVAILABLE, assertThrows(DeploymentException::class.java) { runBlocking { missing.run {} } }.reason)
    }

    @Test
    fun everyDeploymentKindHasAnImplementation() {
        assertEquals(DeploymentKind.entries.toSet(), DeploymentItems.KINDS)
        val device = object : DeploymentItems.Device {
            override fun directory(stored: String) = File(folder.root, stored)
            override fun hasStorageAccess() = true
            override fun hasAceEditorPlugin() = true
            override val fonts = object : EditorFontInstaller {
                override fun isInstalled(fontId: String) = false
                override fun displayName(fontId: String) = fontId
                override suspend fun install(fontId: String, progress: (Float?) -> Unit) = Unit
            }
        }
        assertTrue(DeploymentItems.of(directory, device) is CreateWorkingDirectoryItem)
        assertTrue(DeploymentItems.of(font, device) is DownloadEditorFontItem)
        assertEquals(File(folder.root, "Projects/Imported").path, DeploymentItems.of(directory, device).describe().target)
    }
}
