package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionPluginPermissions
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerActionCatalogPolicyTest {

    @Test
    fun validDescriptorIsNormalized() {
        val descriptor = descriptor(
            activityClassName = ".PreviewActivity",
            priority = Int.MAX_VALUE,
            mimeTypes = listOf(" Text/Markdown; charset=UTF-8 "),
            extensions = listOf(".MD", "Markdown"),
        ) ?: error("Expected a valid descriptor")

        assertEquals("com.example.preview.PreviewActivity", descriptor.activityClassName)
        assertEquals(10_000, descriptor.priority)
        assertEquals(setOf("text/markdown"), descriptor.mimeTypes)
        assertEquals(setOf("md", "markdown"), descriptor.extensions)
        assertTrue(descriptor.fileNameSuffixes.isEmpty())
        assertNull(descriptor.probeForActionId)
        assertEquals(ExplorerActionValues.OUTPUT_NONE, descriptor.outputMode)
        assertTrue(descriptor.outputMimeTypes.isEmpty())
        assertNull(descriptor.outputNameSuffix)
        assertNull(descriptor.maxOutputBytes)
    }

    @Test
    fun activityClassNamesSupportRelativeAndShortForms() {
        assertEquals(
            "com.example.preview.PreviewActivity",
            ExplorerActionCatalogPolicy.normalizeActivityClassName(
                "com.example.preview",
                ".PreviewActivity",
            ),
        )
        assertEquals(
            "com.example.preview.PreviewActivity",
            ExplorerActionCatalogPolicy.normalizeActivityClassName(
                "com.example.preview",
                "PreviewActivity",
            ),
        )
        assertEquals(
            "com.example.preview.ui.PreviewActivity",
            ExplorerActionCatalogPolicy.normalizeActivityClassName(
                "com.example.preview",
                "com.example.preview.ui.PreviewActivity",
            ),
        )
    }

    @Test
    fun mimeMatchingSupportsExactTypeWildcardAndGlobalWildcard() {
        val target = ExplorerActionTarget("text/markdown", "md")

        assertTrue(ExplorerActionCatalogPolicy.matches(descriptor(mimeTypes = listOf("text/markdown"))!!, target))
        assertTrue(ExplorerActionCatalogPolicy.matches(descriptor(mimeTypes = listOf("text/*"))!!, target))
        assertTrue(ExplorerActionCatalogPolicy.matches(descriptor(mimeTypes = listOf("*/*"))!!, target))
        assertFalse(ExplorerActionCatalogPolicy.matches(descriptor(mimeTypes = listOf("image/*"))!!, target))
    }

    @Test
    fun mimeAndExtensionMatchersUseOrSemantics() {
        val descriptor = descriptor(
            mimeTypes = listOf("application/pdf"),
            extensions = listOf("md"),
        ) ?: error("Expected a valid descriptor")

        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                descriptor,
                ExplorerActionTarget("text/plain", "MD"),
            ),
        )
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                descriptor,
                ExplorerActionTarget("application/pdf", "bin"),
            ),
        )
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                descriptor,
                ExplorerActionTarget("image/png", "png"),
            ),
        )
    }

    @Test
    fun descriptorRequiresAtLeastOneValidMatcher() {
        assertNull(descriptor(mimeTypes = emptyList(), extensions = emptyList()))
        assertNull(descriptor(mimeTypes = listOf("not-a-mime")))
        assertNull(descriptor(extensions = listOf("bad.extension")))
    }

    @Test
    fun protocolV14CompoundSuffixesMatchOnlyTheDeclaredFirstVolumeNames() {
        val compound = descriptor(
            protocolVersion = 14,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            mimeTypes = emptyList(),
            extensions = emptyList(),
            fileNameSuffixes = listOf(".ZIP.001", "7z.001", "tar.gz"),
        ) ?: error("Expected a valid compound-suffix descriptor")

        assertEquals(setOf("zip.001", "7z.001", "tar.gz"), compound.fileNameSuffixes)
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                compound,
                ExplorerActionTarget(
                    mimeType = "application/octet-stream",
                    extension = "001",
                    fileName = "Backup.ZIP.001",
                ),
            ),
        )
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                compound,
                ExplorerActionTarget(
                    mimeType = "application/octet-stream",
                    extension = "001",
                    fileName = "backup.7z.001",
                ),
            ),
        )
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                compound,
                ExplorerActionTarget(
                    mimeType = "application/octet-stream",
                    extension = "001",
                    fileName = "backup.001",
                ),
            ),
        )
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                compound,
                ExplorerActionTarget(
                    mimeType = "application/octet-stream",
                    extension = "gz",
                    fileName = "backup.TAR.GZ",
                ),
            ),
        )
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                compound,
                ExplorerActionTarget(
                    mimeType = "application/octet-stream",
                    extension = "gz",
                    fileName = "backup.gz",
                ),
            ),
        )
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                compound,
                ExplorerActionTarget(
                    mimeType = "application/octet-stream",
                    extension = "002",
                    fileName = "backup.zip.002",
                ),
            ),
        )

        assertNull(
            descriptor(
                protocolVersion = 13,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                fileNameSuffixes = listOf("zip.001"),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 14,
                targetKind = ExplorerActionValues.TARGET_DIRECTORY,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                mimeTypes = emptyList(),
                extensions = emptyList(),
                fileNameSuffixes = listOf("zip.001"),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 14,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                fileNameSuffixes = listOf("001"),
            ),
        )
    }

    @Test
    fun descriptorRejectsUnsupportedV1ExecutionModes() {
        assertNull(descriptor(targetKind = ExplorerActionValues.TARGET_FILE + 1))
        assertNull(descriptor(accessMode = ExplorerActionValues.ACCESS_READ_ONLY + 1))
        assertNull(descriptor(placement = ExplorerActionValues.PLACEMENT_PRIMARY))
        assertNull(descriptor(placement = ExplorerActionValues.PLACEMENT_PRIMARY + 1))
    }

    @Test
    fun protocolV2AcceptsPrimaryPlacementWithoutChangingMatchingSemantics() {
        val descriptor = descriptor(
            protocolVersion = 2,
            placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            mimeTypes = listOf("video/*"),
        ) ?: error("Expected a valid primary descriptor")

        assertEquals(2, descriptor.protocolVersion)
        assertEquals(ExplorerActionValues.PLACEMENT_PRIMARY, descriptor.placement)
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                descriptor,
                ExplorerActionTarget("video/mp4", "mp4"),
            ),
        )
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                descriptor,
                ExplorerActionTarget("audio/mpeg", "mp3"),
            ),
        )
    }

    /** 3-Maple Image 3.0.0 "View as album": a read-only, single-directory overflow action without matchers. */
    @Test
    fun protocolV12AcceptsAReadOnlyDirectoryOverflowActionWithoutMatchers() {
        val browse = descriptor(
            protocolVersion = 12,
            id = "browse-directory",
            targetKind = ExplorerActionValues.TARGET_DIRECTORY,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            accessMode = ExplorerActionValues.ACCESS_READ_ONLY,
            placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
            mimeTypes = emptyList(),
            extensions = emptyList(),
        ) ?: error("Expected the directory browsing descriptor to be accepted")
        assertEquals(ExplorerActionValues.TARGET_DIRECTORY, browse.targetKind)
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                browse,
                ExplorerActionTarget(
                    mimeType = "inode/directory",
                    extension = "",
                    kind = ExplorerActionValues.TARGET_DIRECTORY,
                ),
            ),
        )
        assertFalse(ExplorerActionCatalogPolicy.matches(browse, ExplorerActionTarget("image/png", "png")))
        // A selection-toolbar placement needs multiple cardinality, so the same declaration there is rejected.
        assertNull(
            descriptor(
                protocolVersion = 12,
                id = "browse-directory",
                targetKind = ExplorerActionValues.TARGET_DIRECTORY,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR,
                mimeTypes = emptyList(),
                extensions = emptyList(),
            ),
        )
    }

    @Test
    fun protocolV4SupportsDirectoryAndOrderedMixedTargets() {
        val directoryAction = descriptor(
            protocolVersion = 4,
            targetKind = ExplorerActionValues.TARGET_DIRECTORY,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            mimeTypes = emptyList(),
        ) ?: error("Expected a valid directory descriptor")
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                directoryAction,
                ExplorerActionTarget(
                    mimeType = "inode/directory",
                    extension = "",
                    kind = ExplorerActionValues.TARGET_DIRECTORY,
                ),
            ),
        )
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                directoryAction,
                ExplorerActionTarget("text/plain", "txt"),
            ),
        )

        val mixedSelectionAction = descriptor(
            protocolVersion = 4,
            targetKind = ExplorerActionValues.TARGET_MIXED,
            cardinality = ExplorerActionValues.CARDINALITY_MULTIPLE,
            placement = ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR,
            mimeTypes = listOf("*/*"),
        ) ?: error("Expected a valid mixed selection descriptor")
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                mixedSelectionAction,
                listOf(
                    ExplorerActionTarget("text/plain", "txt"),
                    ExplorerActionTarget(
                        mimeType = "inode/directory",
                        extension = "",
                        kind = ExplorerActionValues.TARGET_DIRECTORY,
                    ),
                ),
            ),
        )
        assertFalse(ExplorerActionCatalogPolicy.matches(mixedSelectionAction, emptyList()))
    }

    @Test
    fun protocolV4RequiresExplicitCardinalityAndValidPlacementPairing() {
        assertNull(descriptor(protocolVersion = 4))
        assertNull(
            descriptor(
                protocolVersion = 4,
                cardinality = ExplorerActionValues.CARDINALITY_MULTIPLE,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 4,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR,
            ),
        )
    }

    @Test
    fun createInParentAccessIsAvailableOnlyToProtocolV4Actions() {
        assertNull(
            descriptor(
                protocolVersion = 3,
                accessMode = ExplorerActionValues.ACCESS_CREATE_IN_PARENT,
            ),
        )
        val createAction = descriptor(
            protocolVersion = 4,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            accessMode = ExplorerActionValues.ACCESS_CREATE_IN_PARENT,
        ) ?: error("Expected a valid create-in-parent descriptor")
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                createAction,
                ExplorerActionTarget("text/plain", "txt"),
            ),
        )
    }

    @Test
    fun protocolV8TargetReplacementIsSingleFileActivityOnly() {
        assertNull(
            descriptor(
                protocolVersion = 7,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_REPLACE_TARGET,
            ),
        )
        val replacement = descriptor(
            protocolVersion = 8,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            accessMode = ExplorerActionValues.ACCESS_REPLACE_TARGET,
            mimeTypes = listOf("application/zip"),
        ) ?: error("Expected a valid replace-target descriptor")
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                replacement,
                ExplorerActionTarget("application/zip", "zip"),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 8,
                targetKind = ExplorerActionValues.TARGET_DIRECTORY,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_REPLACE_TARGET,
                mimeTypes = emptyList(),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 8,
                cardinality = ExplorerActionValues.CARDINALITY_MULTIPLE,
                accessMode = ExplorerActionValues.ACCESS_REPLACE_TARGET,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 8,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_REPLACE_TARGET,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 8,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_REPLACE_TARGET,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            ),
        )
    }

    @Test
    fun protocolV9ManagedTargetCombinesReplacementAndSiblingCreation() {
        assertNull(
            descriptor(
                protocolVersion = 8,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_MANAGE_TARGET,
                mimeTypes = listOf("application/zip"),
            ),
        )
        val managed = descriptor(
            protocolVersion = 9,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            accessMode = ExplorerActionValues.ACCESS_MANAGE_TARGET,
            mimeTypes = listOf("application/zip"),
        ) ?: error("Expected a valid managed-target descriptor")
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                managed,
                ExplorerActionTarget("application/zip", "zip"),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 9,
                targetKind = ExplorerActionValues.TARGET_DIRECTORY,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_MANAGE_TARGET,
                mimeTypes = emptyList(),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 9,
                cardinality = ExplorerActionValues.CARDINALITY_MULTIPLE,
                accessMode = ExplorerActionValues.ACCESS_MANAGE_TARGET,
            ),
        )
    }

    @Test
    fun protocolV5HostExplorerPresentationIsStrictlyReadOnlyAndPrimary() {
        val embedded = descriptor(
            protocolVersion = 5,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
        ) ?: error("Expected a valid host Explorer descriptor")
        assertEquals(ExplorerActionValues.PRESENTATION_HOST_EXPLORER, embedded.presentation)

        assertNull(
            descriptor(
                protocolVersion = 4,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 5,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 5,
                targetKind = ExplorerActionValues.TARGET_DIRECTORY,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
                mimeTypes = emptyList(),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 5,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_CREATE_IN_PARENT,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            ),
        )
    }

    @Test
    fun protocolV12MediaSessionExtensionsAreOptionalAndStrictlyScoped() {
        val descriptor = descriptor(
            protocolVersion = 12,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            readSiblings = true,
            playbackProgress = true,
        ) ?: error("Expected a valid v12 media-session descriptor")

        assertTrue(descriptor.readSiblings)
        assertTrue(descriptor.playbackProgress)
        assertFalse(
            descriptor(
                protocolVersion = 12,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            )!!.readSiblings,
        )
        assertNull(
            descriptor(
                protocolVersion = 11,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                readSiblings = true,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 12,
                targetKind = ExplorerActionValues.TARGET_DIRECTORY,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                mimeTypes = emptyList(),
                readSiblings = true,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 12,
                cardinality = ExplorerActionValues.CARDINALITY_MULTIPLE,
                placement = ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR,
                playbackProgress = true,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 12,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_CREATE_IN_PARENT,
                playbackProgress = true,
            ),
        )
    }

    @Test
    fun protocolV22RelatedFilesAreBoundedToExactDeclaredSuffixes() {
        val descriptor = descriptor(
            protocolVersion = 22,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            relatedFileSuffixes = listOf(" .IDSIG ", ".proof.sig"),
        ) ?: error("Expected a valid v22 related-file descriptor")

        assertEquals(setOf(".idsig", ".proof.sig"), descriptor.relatedFileSuffixes)
        assertNull(
            descriptor(
                protocolVersion = 21,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                relatedFileSuffixes = listOf(".idsig"),
            ),
        )
        listOf("idsig", "../idsig", ".", ".bad/idsig", ".bad\\idsig", ".sig.").forEach { suffix ->
            assertNull(
                descriptor(
                    protocolVersion = 22,
                    cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                    relatedFileSuffixes = listOf(suffix),
                ),
            )
        }
        assertNull(
            descriptor(
                protocolVersion = 22,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                relatedFileSuffixes = listOf(".idsig", " .IDSIG "),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 22,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                readSiblings = true,
                relatedFileSuffixes = listOf(".idsig"),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 22,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                playbackProgress = true,
                relatedFileSuffixes = listOf(".idsig"),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 22,
                targetKind = ExplorerActionValues.TARGET_DIRECTORY,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                mimeTypes = emptyList(),
                relatedFileSuffixes = listOf(".idsig"),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 22,
                cardinality = ExplorerActionValues.CARDINALITY_MULTIPLE,
                placement = ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR,
                relatedFileSuffixes = listOf(".idsig"),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 22,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_CREATE_IN_PARENT,
                relatedFileSuffixes = listOf(".idsig"),
            ),
        )
    }

    @Test
    fun protocolV23DisclosesAFilesystemPathOnlyForOneReadOnlyDirectory() {
        fun directory(
            protocolVersion: Int = 23,
            cardinality: Int? = ExplorerActionValues.CARDINALITY_SINGLE,
            accessMode: Int = ExplorerActionValues.ACCESS_READ_ONLY,
            placement: Int = ExplorerActionValues.PLACEMENT_OVERFLOW,
            presentation: Int? = null,
            mimeTypes: Collection<String>? = emptyList(),
            targetKind: Int = ExplorerActionValues.TARGET_DIRECTORY,
            filesystemPath: Boolean? = true,
        ) = descriptor(
            protocolVersion = protocolVersion,
            id = "open-in-terminal",
            targetKind = targetKind,
            cardinality = cardinality,
            accessMode = accessMode,
            placement = placement,
            presentation = presentation,
            mimeTypes = mimeTypes,
            filesystemPath = filesystemPath,
        )

        val accepted = directory() ?: error("Expected a valid v23 directory path descriptor")
        assertTrue(accepted.filesystemPath)
        assertEquals(ExplorerActionValues.TARGET_DIRECTORY, accepted.targetKind)
        // Absent or false keeps the action and its pre-v23 meaning.
        assertFalse(directory(filesystemPath = null)!!.filesystemPath)
        assertFalse(directory(filesystemPath = false)!!.filesystemPath)
        assertFalse(directory(protocolVersion = 22, filesystemPath = null)!!.filesystemPath)

        // Every broader shape rejects the action instead of disclosing a path.
        assertNull(directory(protocolVersion = 22))
        assertNull(directory(targetKind = ExplorerActionValues.TARGET_FILE, mimeTypes = listOf("text/*")))
        assertNull(directory(targetKind = ExplorerActionValues.TARGET_MIXED))
        assertNull(
            directory(
                cardinality = ExplorerActionValues.CARDINALITY_MULTIPLE,
                placement = ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR,
            ),
        )
        assertNull(directory(accessMode = ExplorerActionValues.ACCESS_CREATE_IN_PARENT))
        assertNull(directory(placement = ExplorerActionValues.PLACEMENT_PRIMARY))
        assertNull(directory(presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER))
        assertNull(directory(mimeTypes = listOf("inode/directory")))
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                accepted,
                ExplorerActionTarget(mimeType = "inode/directory", extension = "", kind = ExplorerActionValues.TARGET_DIRECTORY),
            ),
        )
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                accepted,
                ExplorerActionTarget(mimeType = "text/plain", extension = "txt", kind = ExplorerActionValues.TARGET_FILE, fileName = "a.txt"),
            ),
        )
        assertEquals(4_096, ExplorerActionProtocol.MAX_FILESYSTEM_PATH_LENGTH)
    }

    @Test
    fun protocolV24TargetTrashIsLimitedToReadOnlyFileActivities() {
        fun viewer(
            protocolVersion: Int = 24,
            cardinality: Int? = ExplorerActionValues.CARDINALITY_SINGLE,
            accessMode: Int = ExplorerActionValues.ACCESS_READ_ONLY,
            placement: Int = ExplorerActionValues.PLACEMENT_PRIMARY,
            presentation: Int? = null,
            targetKind: Int = ExplorerActionValues.TARGET_FILE,
            mimeTypes: Collection<String>? = listOf("image/*"),
            readSiblings: Boolean? = null,
            filesystemPath: Boolean? = null,
            targetTrash: Boolean? = true,
        ) = descriptor(
            protocolVersion = protocolVersion,
            id = "view-image",
            targetKind = targetKind,
            cardinality = cardinality,
            accessMode = accessMode,
            placement = placement,
            presentation = presentation,
            mimeTypes = mimeTypes,
            readSiblings = readSiblings,
            filesystemPath = filesystemPath,
            targetTrash = targetTrash,
        )

        val single = viewer() ?: error("Expected a valid v24 single-file viewer")
        assertTrue(single.targetTrash)
        // The single-file viewer keeps its v12 sibling paging next to target Trash.
        assertTrue(viewer(readSiblings = true)!!.let { it.targetTrash && it.readSiblings })
        assertTrue(
            viewer(
                cardinality = ExplorerActionValues.CARDINALITY_MULTIPLE,
                placement = ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR,
            )!!.targetTrash,
        )
        assertTrue(viewer(placement = ExplorerActionValues.PLACEMENT_OVERFLOW)!!.targetTrash)
        // Absent or false keeps the pre-v24 meaning of the action.
        assertFalse(viewer(targetTrash = null)!!.targetTrash)
        assertFalse(viewer(targetTrash = false)!!.targetTrash)
        assertFalse(viewer(protocolVersion = 23, targetTrash = null)!!.targetTrash)

        // Every broader shape rejects the action instead of granting Trash access.
        assertNull(viewer(protocolVersion = 23))
        assertNull(viewer(targetKind = ExplorerActionValues.TARGET_DIRECTORY, mimeTypes = emptyList()))
        assertNull(viewer(targetKind = ExplorerActionValues.TARGET_MIXED))
        assertNull(
            viewer(
                accessMode = ExplorerActionValues.ACCESS_CREATE_IN_PARENT,
                placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
            ),
        )
        assertNull(
            viewer(
                accessMode = ExplorerActionValues.ACCESS_REPLACE_TARGET,
                placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
            ),
        )
        assertNull(
            viewer(
                accessMode = ExplorerActionValues.ACCESS_MANAGE_TARGET,
                placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
            ),
        )
        assertNull(viewer(presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER))
        assertNull(
            viewer(
                targetKind = ExplorerActionValues.TARGET_DIRECTORY,
                placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
                mimeTypes = emptyList(),
                filesystemPath = true,
            ),
        )
        fun converter(targetTrash: Boolean?) = descriptor(
            protocolVersion = 24,
            id = "convert-image",
            mimeTypes = listOf("image/*"),
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            outputMode = ExplorerActionValues.OUTPUT_CREATE_SIBLING,
            outputMimeTypes = listOf("image/png"),
            outputNameSuffix = ".converted.png",
            maxOutputBytes = 1024L,
            targetTrash = targetTrash,
        )
        assertTrue(converter(targetTrash = null) != null)
        assertNull(converter(targetTrash = true))
    }

    @Test
    fun protocolV17AddsAnOverflowOnlyUnmatchedFileProbe() {
        val probe = descriptor(
            protocolVersion = 17,
            id = "open-as-archive",
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
            presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            mimeTypes = emptyList(),
            extensions = emptyList(),
            fileNameSuffixes = emptyList(),
            probeForActionId = "open-archive",
        ) ?: error("Expected a valid unmatched-file probe")

        assertEquals("open-archive", probe.probeForActionId)
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                probe,
                ExplorerActionTarget("application/octet-stream", "bin", fileName = "archive.bin"),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 16,
                id = "open-as-archive",
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
                mimeTypes = emptyList(),
                extensions = emptyList(),
                probeForActionId = "open-archive",
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 17,
                id = "open-as-archive",
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
                mimeTypes = listOf("*/*"),
                extensions = emptyList(),
                probeForActionId = "open-archive",
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 17,
                id = "open-as-archive",
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
                mimeTypes = emptyList(),
                extensions = emptyList(),
                probeForActionId = "open-archive",
            ),
        )
    }

    @Test
    fun unmatchedFileProbeAppearsOnlyWhenItsPrimaryActionDoesNotMatch() {
        val primary = descriptor(
            protocolVersion = 17,
            id = "open-archive",
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            mimeTypes = listOf("application/zip"),
            extensions = listOf("zip"),
        ) ?: error("Expected a valid primary archive action")
        val probe = descriptor(
            protocolVersion = 17,
            id = "open-as-archive",
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
            presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            mimeTypes = emptyList(),
            extensions = emptyList(),
            probeForActionId = primary.id,
        ) ?: error("Expected a valid unmatched-file probe")
        val descriptors = listOf(primary, probe)

        assertEquals(
            setOf(primary.id),
            ExplorerActionMatchingPolicy.matchingActionIds(
                descriptors,
                listOf(ExplorerActionTarget("application/zip", "zip", fileName = "archive.zip")),
            ),
        )
        assertEquals(
            setOf(probe.id),
            ExplorerActionMatchingPolicy.matchingActionIds(
                descriptors,
                listOf(ExplorerActionTarget("text/plain", "txt", fileName = "archive.txt")),
            ),
        )
        assertTrue(
            ExplorerActionMatchingPolicy.matchingActionIds(
                descriptors,
                listOf(
                    ExplorerActionTarget(
                        mimeType = "inode/directory",
                        extension = "",
                        kind = ExplorerActionValues.TARGET_DIRECTORY,
                    ),
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun unmatchedFileProbeReferencesOneValidPrimaryArchiveAction() {
        val primary = descriptor(
            protocolVersion = 17,
            id = "open-archive",
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            mimeTypes = listOf("application/zip"),
        ) ?: error("Expected a valid primary archive action")
        val firstProbe = descriptor(
            protocolVersion = 17,
            id = "open-as-archive",
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
            presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            mimeTypes = emptyList(),
            extensions = emptyList(),
            probeForActionId = primary.id,
        ) ?: error("Expected a valid unmatched-file probe")
        val duplicateProbe = descriptor(
            protocolVersion = 17,
            id = "try-as-archive",
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
            presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            mimeTypes = emptyList(),
            extensions = emptyList(),
            probeForActionId = primary.id,
        ) ?: error("Expected a second valid unmatched-file probe")
        val unrelatedActivity = descriptor(
            protocolVersion = 17,
            id = "preview-text",
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            mimeTypes = listOf("text/plain"),
        ) ?: error("Expected a valid activity action")
        val invalidTargetProbe = firstProbe.copy(
            id = "probe-preview",
            probeForActionId = unrelatedActivity.id,
        )
        val missingTargetProbe = firstProbe.copy(
            id = "probe-missing",
            probeForActionId = "missing-primary",
        )

        assertEquals(
            listOf(primary, firstProbe, unrelatedActivity),
            ExplorerActionCatalogPolicy.validateDescriptorReferences(
                listOf(
                    primary,
                    firstProbe,
                    duplicateProbe,
                    unrelatedActivity,
                    invalidTargetProbe,
                    missingTargetProbe,
                ),
            ),
        )
    }

    @Test
    fun descriptorRejectsInvalidIdentifiersAndActivityNames() {
        assertNull(descriptor(id = "Preview"))
        assertNull(descriptor(activityClassName = "com.example.preview/PreviewActivity"))
        assertNull(descriptor(activityClassName = "com.example.preview.Bad Activity"))
    }

    @Test
    fun fallbackLabelIsSanitizedAndCanBeReplacedByAResourceName() {
        val sanitized = descriptor(labelFallback = "  Preview\u0000\n document  ")
            ?: error("Expected sanitized fallback label")
        assertEquals("Preview document", sanitized.labelFallback)

        assertTrue(
            descriptor(
                labelResourceName = "action_preview",
                labelFallback = null,
            ) != null,
        )
        assertNull(descriptor(labelResourceName = "ActionPreview", labelFallback = null))
    }

    @Test
    fun protocolAndHostVersionCompatibilityAreStrict() {
        (ExplorerActionProtocol.MIN_SUPPORTED_VERSION..ExplorerActionProtocol.MAX_SUPPORTED_VERSION)
            .forEach { protocolVersion ->
                assertTrue(
                    ExplorerActionAvailabilityPolicy.isProtocolSupported(protocolVersion.toLong()),
                )
            }
        assertFalse(ExplorerActionAvailabilityPolicy.isProtocolSupported(null))
        assertFalse(ExplorerActionAvailabilityPolicy.isProtocolSupported(0L))
        assertFalse(
            ExplorerActionAvailabilityPolicy.isProtocolSupported(
                ExplorerActionProtocol.MAX_SUPPORTED_VERSION.toLong() + 1L,
            ),
        )
        // A v20 host keeps loading existing plugins at their own v1-v19 catalog version.
        assertTrue(ExplorerActionAvailabilityPolicy.isCatalogProtocolCompatible(19L, 19))
        assertTrue(ExplorerActionAvailabilityPolicy.isCatalogProtocolCompatible(20L, 20))
        assertTrue(ExplorerActionAvailabilityPolicy.isCatalogProtocolCompatible(4L, 4))
        assertFalse(ExplorerActionAvailabilityPolicy.isCatalogProtocolCompatible(3L, 4))
        assertFalse(ExplorerActionAvailabilityPolicy.isCatalogProtocolCompatible(null, 4))

        assertTrue(ExplorerActionAvailabilityPolicy.isHostVersionCompatible(5_268L, 5_268L))
        assertTrue(ExplorerActionAvailabilityPolicy.isHostVersionCompatible(5_268L, null))
        assertFalse(ExplorerActionAvailabilityPolicy.isHostVersionCompatible(5_268L, 5_269L))
    }

    @Test
    fun emptyPluginAbisMeanUniversalAndDeclaredAbisMustIntersect() {
        val hostAbis = listOf("arm64-v8a", "armeabi-v7a")

        assertTrue(ExplorerActionAvailabilityPolicy.isAbiCompatible(emptyList(), hostAbis))
        assertTrue(ExplorerActionAvailabilityPolicy.isAbiCompatible(null, hostAbis))
        assertTrue(ExplorerActionAvailabilityPolicy.isAbiCompatible(listOf(" ARM64-V8A "), hostAbis))
        assertFalse(ExplorerActionAvailabilityPolicy.isAbiCompatible(listOf("x86_64"), hostAbis))
        assertFalse(ExplorerActionAvailabilityPolicy.isAbiCompatible(listOf(" "), hostAbis))
    }

    @Test
    fun activityDeclarationMustBeExportedEnabledAndPermissionProtected() {
        fun isValid(
            actualPackageName: String = "com.example.preview",
            exported: Boolean = true,
            activityEnabled: Boolean = true,
            applicationEnabled: Boolean = true,
            permission: String? = ExplorerActionPluginPermissions.PLUGIN,
        ) = ExplorerActionAvailabilityPolicy.isActivityDeclarationValid(
            expectedPackageName = "com.example.preview",
            actualPackageName = actualPackageName,
            exported = exported,
            activityEnabled = activityEnabled,
            applicationEnabled = applicationEnabled,
            requiredPermission = ExplorerActionPluginPermissions.PLUGIN,
            actualPermission = permission,
        )

        assertTrue(isValid())
        assertFalse(isValid(actualPackageName = "com.example.other"))
        assertFalse(isValid(exported = false))
        assertFalse(isValid(activityEnabled = false))
        assertFalse(isValid(applicationEnabled = false))
        assertFalse(isValid(permission = null))
        assertFalse(isValid(permission = "com.example.UNSAFE"))
    }

    @Suppress("LongParameterList")
    private fun descriptor(
        protocolVersion: Int = 1,
        id: String = "document-preview",
        labelResourceName: String? = null,
        labelFallback: String? = "Preview",
        activityClassName: String = ".PreviewActivity",
        priority: Int = 0,
        targetKind: Int = ExplorerActionValues.TARGET_FILE,
        cardinality: Int? = null,
        accessMode: Int = ExplorerActionValues.ACCESS_READ_ONLY,
        placement: Int = ExplorerActionValues.PLACEMENT_OVERFLOW,
        presentation: Int? = null,
        mimeTypes: Collection<String>? = listOf("text/*"),
        extensions: Collection<String>? = emptyList(),
        fileNameSuffixes: Collection<String>? = null,
        probeForActionId: String? = null,
        readSiblings: Boolean? = null,
        playbackProgress: Boolean? = null,
        relatedFileSuffixes: Collection<String>? = null,
        filesystemPath: Boolean? = null,
        targetTrash: Boolean? = null,
        outputMode: Int? = null,
        outputMimeTypes: Collection<String>? = null,
        outputNameSuffix: String? = null,
        maxOutputBytes: Long? = null,
    ): ExplorerActionDescriptor? {
        return ExplorerActionCatalogPolicy.normalizeDescriptor(
            protocolVersion = protocolVersion,
            packageName = "com.example.preview",
            id = id,
            labelResourceName = labelResourceName,
            labelFallback = labelFallback,
            activityClassName = activityClassName,
            priority = priority,
            targetKind = targetKind,
            cardinality = cardinality,
            accessMode = accessMode,
            placement = placement,
            presentation = presentation,
            mimeTypes = mimeTypes,
            extensions = extensions,
            fileNameSuffixes = fileNameSuffixes,
            probeForActionId = probeForActionId,
            readSiblings = readSiblings,
            playbackProgress = playbackProgress,
            relatedFileSuffixes = relatedFileSuffixes,
            filesystemPath = filesystemPath,
            targetTrash = targetTrash,
            outputMode = outputMode,
            outputMimeTypes = outputMimeTypes,
            outputNameSuffix = outputNameSuffix,
            maxOutputBytes = maxOutputBytes,
        )
    }
}
