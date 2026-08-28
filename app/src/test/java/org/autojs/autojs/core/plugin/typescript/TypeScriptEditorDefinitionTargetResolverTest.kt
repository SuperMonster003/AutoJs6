package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.nio.file.Files
import org.autojs.plugin.editor.api.EditorPluginDefinitionTarget
import org.autojs.plugin.editor.api.EditorPluginDefinitionTargetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TypeScriptEditorDefinitionTargetResolverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun resolvesCurrentProjectSourceOnlyFromTheFreshSourceInventory() {
        val fixture = projectFixture()
        val snapshot = requireNotNull(
            TypeScriptEditorProjectSnapshotProvider.captureRequired(fixture.document.path),
        )
        val source = snapshot.sourceFiles.single { it.relativePath == "src/shared.ts" }
        val target = EditorPluginDefinitionTarget(
            kind = EditorPluginDefinitionTargetKind.PROJECT_SOURCE,
            projectRootPath = snapshot.projectRootPath,
            relativePath = source.relativePath,
            line = 0,
            column = 16,
            endLine = 0,
            endColumn = 22,
            contentSha256 = source.sha256,
            projectSourceInventoryFingerprint = snapshot.sourceInventoryFingerprint,
        )

        val resolved = requireNotNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(fixture.document.path, target),
        )
        assertEquals(fixture.shared.canonicalPath, resolved.targetPath)
        assertEquals(fixture.document.canonicalPath, resolved.documentPath)
        assertFalse(resolved.isCurrentDocument)
        assertEquals(0, resolved.line)
        assertEquals(16, resolved.column)

        assertNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(
                fixture.document.path,
                target.copy(projectRootPath = requireNotNull(fixture.root.parentFile).path),
            ),
        )
        assertNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(
                fixture.document.path,
                target.copy(projectSourceInventoryFingerprint = hash('0')),
            ),
        )
        assertNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(
                fixture.document.path,
                target.copy(contentSha256 = hash('1')),
            ),
        )
        assertNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(
                fixture.document.path,
                target.copy(line = 20, endLine = 20),
            ),
        )
    }

    @Test
    fun resolvesCurrentDocumentOnlyWhenTheLiveTextMatchesTheAuthorizedContent() {
        val fixture = projectFixture()
        val snapshot = requireNotNull(
            TypeScriptEditorProjectSnapshotProvider.captureRequired(fixture.document.path),
        )
        val source = snapshot.sourceFiles.single { it.relativePath == "src/main.ts" }
        val target = EditorPluginDefinitionTarget(
            kind = EditorPluginDefinitionTargetKind.PROJECT_SOURCE,
            projectRootPath = snapshot.projectRootPath,
            relativePath = source.relativePath,
            line = 0,
            column = 9,
            endLine = 0,
            endColumn = 15,
            contentSha256 = source.sha256,
            projectSourceInventoryFingerprint = snapshot.sourceInventoryFingerprint,
        )

        val resolved = requireNotNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(
                documentPath = fixture.document.path,
                target = target,
                currentDocumentText = fixture.document.readText(),
            ),
        )
        assertTrue(resolved.isCurrentDocument)
        assertNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(fixture.document.path, target),
        )
        assertNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(
                documentPath = fixture.document.path,
                target = target,
                currentDocumentText = fixture.document.readText() + "// unsaved\n",
            ),
        )
    }

    @Test
    fun resolvesDependencyDeclarationOnlyFromTheFreshBoundedInventory() {
        val fixture = projectFixture()
        val inventory = requireNotNull(
            TypeScriptEditorDependencySnapshotProvider.captureRequired(fixture.root.path),
        )
        val relativePath = "node_modules/pkg/index.d.ts"
        val target = EditorPluginDefinitionTarget(
            kind = EditorPluginDefinitionTargetKind.DEPENDENCY_DECLARATION,
            projectRootPath = fixture.root.canonicalPath,
            relativePath = relativePath,
            line = 0,
            column = 21,
            endLine = 0,
            endColumn = 27,
            contentSha256 = requireNotNull(inventory.contentSha256ByRelativePath[relativePath]),
            dependencyInventoryFingerprint = inventory.inventoryFingerprint,
        )

        val resolved = requireNotNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(fixture.document.path, target),
        )
        assertEquals(fixture.declaration.canonicalPath, resolved.targetPath)
        assertEquals(EditorPluginDefinitionTargetKind.DEPENDENCY_DECLARATION, resolved.kind)

        assertNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(
                fixture.document.path,
                target.copy(dependencyInventoryFingerprint = hash('2')),
            ),
        )
        fixture.declaration.appendText("\nexport declare const changed: true;")
        assertNull(
            TypeScriptEditorDefinitionTargetResolver.resolve(fixture.document.path, target),
        )
    }

    @Test
    fun dependencyInventoryIsDeterministicAndExcludesNonCompilationAssets() {
        val fixture = projectFixture()
        val first = requireNotNull(
            TypeScriptEditorDependencySnapshotProvider.captureRequired(fixture.root.path),
        )
        val second = requireNotNull(
            TypeScriptEditorDependencySnapshotProvider.captureRequired(fixture.root.path),
        )

        assertEquals(first.inventoryFingerprint, second.inventoryFingerprint)
        assertEquals(2, first.contentSha256ByRelativePath.size)
        assertTrue("node_modules/pkg/index.d.ts" in first.contentSha256ByRelativePath)
        assertTrue("node_modules/pkg/package.json" in first.contentSha256ByRelativePath)
        assertFalse(first.contentSha256ByRelativePath.keys.any { path -> path.endsWith("README.md") })
    }

    @Test
    fun rejectsDependencyTargetThatTraversesASymbolicLink() {
        val fixture = projectFixture()
        val linked = File(fixture.root, "node_modules/pkg/linked.d.ts")
        val created = runCatching {
            Files.createSymbolicLink(linked.toPath(), fixture.shared.toPath())
        }.isSuccess
        assumeTrue("Symbolic links are unavailable on this test host", created)

        assertNull(TypeScriptEditorDependencySnapshotProvider.capture(fixture.root.path))
    }

    private fun projectFixture(): Fixture {
        val root = temporaryFolder.newFolder("definition-project").canonicalFile
        File(root, "tsconfig.json").writeText("{\"compilerOptions\":{\"strict\":true}}")
        File(root, "package-lock.json").writeText("{\"lockfileVersion\":3}")
        val document = File(root, "src/main.ts").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("import { answer } from './shared'; void answer;\n")
        }.canonicalFile
        val shared = File(root, "src/shared.ts").apply {
            writeText("export function answer(value: string): number { return value.length; }\n")
        }.canonicalFile
        val dependency = File(root, "node_modules/pkg").apply { mkdirs() }
        File(dependency, "package.json").writeText(
            "{\"name\":\"pkg\",\"types\":\"index.d.ts\"}",
        )
        val declaration = File(dependency, "index.d.ts").apply {
            writeText("export declare const answer: number;\n")
        }.canonicalFile
        File(dependency, "docs").apply { mkdirs() }
            .resolve("README.md")
            .writeText("not part of the dependency inventory")
        return Fixture(root, document, shared, declaration)
    }

    private fun hash(character: Char): String = character.toString().repeat(64)

    private data class Fixture(
        val root: File,
        val document: File,
        val shared: File,
        val declaration: File,
    )
}
