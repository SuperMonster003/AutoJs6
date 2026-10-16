package org.autojs.autojs.core.plugin.explorer.session

import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.batchrename.BatchRenameSampleImages
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerPhotoMetadataKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Explorer Action v26 `readMetadata` (docs/dev/explorer-action-protocol-v26.md, Batch Rename roadmap P6.4): request
 * targets and listed descendants answer their photo metadata in request order, other files and directories answer
 * nothing, unlisted entries and oversized requests are refused, and actions without `targetRename` get no access.
 * zh-CN: Explorer Action v26 `readMetadata`: 请求目标与已列出的后代按请求顺序应答照片元数据, 其他文件与目录不应答, 未列出的条目与超量
 * 请求被拒绝, 未声明 `targetRename` 的动作无权访问.
 */
class ExplorerActionMetadataV26InstrumentationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "explorer-action-v26-${System.nanoTime()}").apply { mkdirs() }
    private val sessions = ArrayList<ExplorerActionHostSession>()

    @After
    fun cleanUp() {
        sessions.forEach { runCatching { it.close() } }
        root.deleteRecursively()
    }

    @Test
    fun targetsAndListedImagesAnswerTheirMetadata() {
        val album = File(root, "album").apply { mkdirs() }
        BatchRenameSampleImages.write(File(album, "img.jpg"), exif = mapOf(ExifInterface.TAG_MODEL to "Album Camera"))
        File(album, "notes.txt").writeText("notes")
        File(album, "sub").mkdirs()
        val photo = BatchRenameSampleImages.write(File(root, "photo.jpg"), exif = mapOf(ExifInterface.TAG_MODEL to "Target Camera"))
        val session = session(mapOf("album" to album, "photo" to photo), rename = true)

        // Descendants the session has not listed cannot be read. zh-CN: 本会话未列出的后代不可读取.
        expect<IllegalArgumentException> { session.readMetadata(request(entry("album", "img.jpg"))) }
        session.listChildren("album", "", 0, 10)
        val items = BundleCompat.getParcelableArrayList(
            session.readMetadata(request(entry("photo", ""), entry("album", "img.jpg"), entry("album", "notes.txt"), entry("album", "sub"), entry("album", ""))),
            ExplorerActionHostSessionKeys.METADATA_ITEMS,
            Bundle::class.java,
        )!!
        assertEquals(
            listOf("Target Camera", "Album Camera", null, null, null),
            items.map { it.getBundle(ExplorerActionHostSessionKeys.METADATA)?.getString(ExplorerPhotoMetadataKeys.CAMERA_MODEL) },
        )
        assertEquals(64, items[0].getBundle(ExplorerActionHostSessionKeys.METADATA)!!.getInt(ExplorerPhotoMetadataKeys.WIDTH))
        assertTrue(items.drop(2).all { it.isEmpty })

        expect<IllegalArgumentException> { session.readMetadata(request()) }
        expect<IllegalArgumentException> { session.readMetadata(request(*Array(129) { entry("photo", "") })) }
        expect<IllegalArgumentException> { session.readMetadata(request(entry("missing", ""))) }
    }

    @Test
    fun actionsWithoutTargetRenameCannotReadMetadata() {
        val photo = BatchRenameSampleImages.write(File(root, "photo.jpg"))
        val session = session(mapOf("photo" to photo), rename = false)
        expect<IllegalStateException> { session.readMetadata(request(entry("photo", ""))) }
        val closed = session(mapOf("photo" to photo), rename = true)
        closed.close()
        expect<IllegalStateException> { closed.readMetadata(request(entry("photo", ""))) }
    }

    private fun session(targets: Map<String, File>, rename: Boolean): ExplorerActionHostSession = ExplorerActionHostSession.create(
        context = context,
        pluginPackageName = context.packageName,
        commonParent = root,
        targets = targets.map { (id, file) -> ExplorerActionHostSessionTarget(id, file) },
        allowCreateOutput = false,
        allowTargetRename = rename,
    ).also(sessions::add)

    private fun entry(targetId: String, relativePath: String) = Bundle().apply {
        putString(ExplorerActionHostSessionKeys.RENAME_TARGET_ID, targetId)
        putString(ExplorerActionHostSessionKeys.RELATIVE_PATH, relativePath)
    }

    private fun request(vararg entries: Bundle) = Bundle().apply {
        putParcelableArrayList(ExplorerActionHostSessionKeys.METADATA_ENTRIES, arrayListOf(*entries))
    }

    private inline fun <reified T : Throwable> expect(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return
            throw error
        }
        fail("Expected ${T::class.java.simpleName}")
    }
}
