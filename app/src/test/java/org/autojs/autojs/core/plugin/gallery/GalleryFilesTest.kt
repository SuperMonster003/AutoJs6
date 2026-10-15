package org.autojs.autojs.core.plugin.gallery

import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GalleryFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun item(path: String, name: String = File(path).name) =
        """{"path":${quote(path)},"name":${quote(name)},"mimeType":"image/jpeg","width":40,"height":30,"size":120,"dateTaken":1792065600000}"""

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun expectCode(code: String, block: () -> Unit) {
        try {
            block()
            fail("expected $code")
        } catch (e: GalleryPluginException) {
            assertEquals(code, e.code)
        }
    }

    @Test
    fun thePickPolicyKeepsReadableAbsolutePathsOnce() {
        val a = folder.newFile("a.jpg")
        val b = folder.newFile("b.jpg")
        val json = "[" + listOf(item(a.path), item(b.path), item(a.path), item("relative.jpg"), item(File(folder.root, "gone.jpg").path)).joinToString(",") + "]"
        val kept = GalleryPickPolicy.accept(json)
        assertEquals(listOf(a.absolutePath, b.absolutePath), kept.map { it.path })
        assertEquals(GalleryPickedImage(a.absolutePath, "a.jpg", "image/jpeg", 40, 30, 120L, 1_792_065_600_000L), kept.first())
    }

    @Test
    fun anEmptyChoiceIsEmptyButUnreadableChoicesAreRefused() {
        assertEquals(emptyList<GalleryPickedImage>(), GalleryPickPolicy.accept("[]"))
        val a = folder.newFile("a.jpg")
        expectCode(GalleryErrorCodes.ACCESS_DENIED) { GalleryPickPolicy.accept("[" + item(a.path) + "]", readable = { false }) }
        expectCode(GalleryErrorCodes.INTERNAL) { GalleryPickPolicy.accept("{}") }
        expectCode(GalleryErrorCodes.INTERNAL) { GalleryPickPolicy.accept("[not json") }
    }

    @Test
    fun thePickPolicyStopsAtTheContractCeiling() {
        val files = (0..GalleryContract.MAX_PICK_COUNT).map { folder.newFile("p$it.jpg") }
        val json = "[" + files.joinToString(",") { item(it.path) } + "]"
        assertEquals(GalleryContract.MAX_PICK_COUNT, GalleryPickPolicy.accept(json).size)
    }

    @Test
    fun sourcesAreValidatedBeforeAnythingIsOpened() {
        expectCode(GalleryErrorCodes.INVALID_ARGUMENT) { GallerySources.validate(" ") }
        expectCode(GalleryErrorCodes.FILE_NOT_FOUND) { GallerySources.validate(File(folder.root, "missing.png").path) }
        expectCode(GalleryErrorCodes.FILE_NOT_FOUND) { GallerySources.validate(folder.root.path) }
        val file = folder.newFile("ok.png")
        assertEquals(file, GallerySources.validate(file.path))
    }

    @Test
    fun defaultOutputsGoNextToTheSourceWithTheWrittenExtensionAndACounter() {
        val source = folder.newFile("photo.jpg")
        assertEquals(File(folder.root, "photo_edited.png"), GalleryOutput.defaultTarget(source, GalleryOutput.SUFFIX_EDITED, "image/png"))
        folder.newFile("photo_converted.webp")
        folder.newFile("photo_converted (1).webp")
        assertEquals(File(folder.root, "photo_converted (2).webp"), GalleryOutput.defaultTarget(source, GalleryOutput.SUFFIX_CONVERTED, "image/webp"))
        assertEquals(File(folder.root, "photo_edited.jpg"), GalleryOutput.defaultTarget(source, GalleryOutput.SUFFIX_EDITED, "image/heic"))
    }

    @Test
    fun aStagedOutputIsPublishedOnlyWhenItReadsBackAsReported() {
        val source = folder.newFile("photo.jpg")
        val output = GalleryOutput.stage(source, GalleryOutputRequest(), GalleryOutput.SUFFIX_EDITED)
        assertTrue(output.staging.exists())
        assertTrue(output.staging.name.startsWith(".gallery-"))
        output.staging.writeText("pixels")
        val written = output.publish("image/png", 40, 30) { GalleryImageProbe("image/png", 40, 30) }
        assertEquals(File(folder.root, "photo_edited.png").absolutePath, written.path)
        assertEquals(6L, written.size)
        assertFalse(output.staging.exists())

        val wrong = GalleryOutput.stage(source, GalleryOutputRequest(), GalleryOutput.SUFFIX_EDITED)
        wrong.staging.writeText("pixels")
        expectCode(GalleryErrorCodes.IO) { wrong.publish("image/png", 40, 30) { GalleryImageProbe("image/png", 41, 30) } }
        assertFalse(wrong.staging.exists())

        val empty = GalleryOutput.stage(source, GalleryOutputRequest(), GalleryOutput.SUFFIX_EDITED)
        expectCode(GalleryErrorCodes.IO) { empty.publish("image/png", 40, 30) { GalleryImageProbe("image/png", 40, 30) } }
        assertFalse(empty.staging.exists())
    }

    @Test
    fun explicitOutputsRefuseToReplaceUnlessAsked() {
        val source = folder.newFile("photo.jpg")
        val existing = folder.newFile("taken.png")
        expectCode(GalleryErrorCodes.OUTPUT_EXISTS) { GalleryOutput.stage(source, GalleryOutputRequest(existing.path), GalleryOutput.SUFFIX_EDITED) }

        existing.writeText("old")
        val replacing = GalleryOutput.stage(source, GalleryOutputRequest(existing.path, overwrite = true), GalleryOutput.SUFFIX_EDITED)
        replacing.staging.writeText("new image")
        val written = replacing.publish("image/png", 1, 1) { GalleryImageProbe("image/png", 1, 1) }
        assertEquals(existing.absolutePath, written.path)
        assertEquals("new image", existing.readText())
        assertEquals(listOf("photo.jpg", "taken.png"), folder.root.list()!!.sorted())

        val nested = File(folder.root, "out/deeper/result.png")
        val created = GalleryOutput.stage(source, GalleryOutputRequest(nested.path), GalleryOutput.SUFFIX_EDITED)
        assertTrue(nested.parentFile!!.isDirectory)
        created.discard()
        assertFalse(created.staging.exists())
    }

    @Test
    fun overwriteWithoutAnOutputReplacesTheSource() {
        val source = folder.newFile("photo.jpg")
        source.writeText("before")
        val output = GalleryOutput.stage(source, GalleryOutputRequest(overwrite = true), GalleryOutput.SUFFIX_EDITED)
        output.staging.writeText("after")
        val written = output.publish("image/jpeg", 2, 2) { GalleryImageProbe("image/jpeg", 2, 2) }
        assertEquals(source.absolutePath, written.path)
        assertEquals("after", source.readText())
        assertEquals(listOf("photo.jpg"), folder.root.list()!!.toList())
    }
}
