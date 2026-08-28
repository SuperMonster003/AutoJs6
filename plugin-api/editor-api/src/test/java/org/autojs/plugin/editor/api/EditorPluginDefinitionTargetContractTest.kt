package org.autojs.plugin.editor.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPluginDefinitionTargetContractTest {

    @Test
    fun contractThreeAcceptsBoundedProjectAndDependencyTargets() {
        assertTrue(EditorPluginContract.MAX_SUPPORTED_VERSION >= 3)
        assertTrue(EditorPluginDefinitionTargetContract.isSupported(projectTarget()))
        assertTrue(
            EditorPluginDefinitionTargetContract.isSupported(
                EditorPluginDefinitionTarget(
                    kind = EditorPluginDefinitionTargetKind.DEPENDENCY_DECLARATION,
                    projectRootPath = "/project",
                    relativePath = "node_modules/dayjs/index.d.ts",
                    line = 2,
                    column = 4,
                    endLine = 2,
                    endColumn = 10,
                    contentSha256 = hash('b'),
                    dependencyInventoryFingerprint = hash('c'),
                ),
            ),
        )
    }

    @Test
    fun rejectsTraversalWrongAuthorityAndInvalidRanges() {
        assertFalse(
            EditorPluginDefinitionTargetContract.isSupported(
                projectTarget().copy(relativePath = "../outside.ts"),
            ),
        )
        assertFalse(
            EditorPluginDefinitionTargetContract.isSupported(
                projectTarget().copy(relativePath = "node_modules/pkg/index.ts"),
            ),
        )
        assertFalse(
            EditorPluginDefinitionTargetContract.isSupported(
                projectTarget().copy(dependencyInventoryFingerprint = hash('d')),
            ),
        )
        assertFalse(
            EditorPluginDefinitionTargetContract.isSupported(
                projectTarget().copy(endLine = 0, endColumn = 0),
            ),
        )
        assertFalse(
            EditorPluginDefinitionTargetContract.isSupported(
                projectTarget().copy(contentSha256 = hash('A')),
            ),
        )
    }

    @Test
    fun dependencyAuthorityOnlyAdmitsDeclarationFilesBelowNodeModules() {
        val target = EditorPluginDefinitionTarget(
            kind = EditorPluginDefinitionTargetKind.DEPENDENCY_DECLARATION,
            projectRootPath = "/project",
            relativePath = "node_modules/@types/pkg/index.d.mts",
            line = 0,
            column = 0,
            contentSha256 = hash('1'),
            dependencyInventoryFingerprint = hash('2'),
        )
        assertTrue(EditorPluginDefinitionTargetContract.isSupported(target))
        assertFalse(
            EditorPluginDefinitionTargetContract.isSupported(
                target.copy(relativePath = "node_modules/pkg/index.js"),
            ),
        )
        assertFalse(
            EditorPluginDefinitionTargetContract.isSupported(
                target.copy(relativePath = "vendor/pkg/index.d.ts"),
            ),
        )
        assertFalse(
            EditorPluginDefinitionTargetContract.isSupported(
                target.copy(projectSourceInventoryFingerprint = hash('3')),
            ),
        )
    }

    private fun projectTarget(): EditorPluginDefinitionTarget = EditorPluginDefinitionTarget(
        kind = EditorPluginDefinitionTargetKind.PROJECT_SOURCE,
        projectRootPath = "/project",
        relativePath = "src/shared.ts",
        line = 3,
        column = 2,
        endLine = 3,
        endColumn = 8,
        contentSha256 = hash('a'),
        projectSourceInventoryFingerprint = hash('f'),
    )

    private fun hash(character: Char): String = character.toString().repeat(64)
}
