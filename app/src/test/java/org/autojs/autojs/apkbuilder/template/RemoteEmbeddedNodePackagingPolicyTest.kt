package org.autojs.autojs.apkbuilder.template

import org.autojs.autojs.apkbuilder.ApkBuildLibrary
import org.autojs.autojs.script.JavaScriptSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteEmbeddedNodePackagingPolicyTest {

    @Test
    fun canonicalNameAndEveryLegacyAliasAreDetected() {
        val embeddedNode = ApkBuildLibrary.EMBEDDED_NODE_JS
        val selections = buildList {
            add(embeddedNode.label)
            addAll(embeddedNode.aliases)
            add("  ${embeddedNode.label.uppercase()}  ")
        }

        selections.forEach { selection ->
            assertTrue(
                "Embedded Node selection was not detected: $selection",
                RemoteApkBuildRequestPreparer.hasLegacyEmbeddedNodeLibrary(listOf(selection)),
            )
        }
    }

    @Test
    fun everyNodeProjectSignalIsDetected() {
        assertTrue(requests(selectedLibraries = listOf(ApkBuildLibrary.NODEJS_RUNTIME.label)))
        assertTrue(requests(selectedLibraries = listOf("nodejs")))
        assertTrue(requests(projectType = " NoDe "))
        assertTrue(requests(hasNodeConfig = true))
        listOf("main.mjs", "main.cjs", "main.mts", "main.cts", "main.node.js").forEach { name ->
            assertTrue("Node entry extension was not detected: $name", requests(configuredEntryName = name))
            assertTrue("Node source extension was not detected: $name", requests(sourceName = name))
        }
        assertTrue(requests(entryExecutionMode = JavaScriptSource.EXECUTION_MODE_NODEJS))
    }

    @Test
    fun supportedRhinoSelectionsRemainAllowed() {
        assertFalse(requests())
        assertFalse(requests(selectedLibraries = listOf(ApkBuildLibrary.IMAGE_QUANT.label)))
        assertFalse(
            requests(
                selectedLibraries = listOf(ApkBuildLibrary.OPENCV.label, ApkBuildLibrary.MLKIT_OCR.label),
                projectType = "rhino",
                configuredEntryName = "main.js",
                sourceName = "main.js",
                entryExecutionMode = JavaScriptSource.EXECUTION_MODE_UI,
            ),
        )
    }

    private fun requests(
        selectedLibraries: List<String> = emptyList(),
        projectType: String? = null,
        hasNodeConfig: Boolean = false,
        configuredEntryName: String? = null,
        sourceName: String? = null,
        entryExecutionMode: Int? = null,
    ) = RemoteApkBuildRequestPreparer.requiresNodeRuntime(
        selectedLibraries = selectedLibraries,
        projectType = projectType,
        hasNodeConfig = hasNodeConfig,
        configuredEntryName = configuredEntryName,
        sourceName = sourceName,
        entryExecutionMode = entryExecutionMode,
    )
}
