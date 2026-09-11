package org.autojs.autojs.apkbuilder.template

import org.autojs.autojs.script.JavaScriptSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteQuickJsPackagingEntryPolicyTest {

    @Test
    fun everyQuickJsSelectorRequiresTheExternalRuntime() {
        assertTrue(
            RemoteApkBuildRequestPreparer.requestsExternalQuickJsRuntime(
                projectType = "QuickJS",
                configuredEntryName = "main.js",
                sourceName = "project",
                entryExecutionMode = 0,
            ),
        )
        assertTrue(
            RemoteApkBuildRequestPreparer.requestsExternalQuickJsRuntime(
                projectType = null,
                configuredEntryName = "main.quickjs.js",
                sourceName = "project",
                entryExecutionMode = 0,
            ),
        )
        assertTrue(
            RemoteApkBuildRequestPreparer.requestsExternalQuickJsRuntime(
                projectType = null,
                configuredEntryName = "main.js",
                sourceName = "entry.quickjs.mjs",
                entryExecutionMode = JavaScriptSource.EXECUTION_MODE_QUICKJS,
            ),
        )
    }

    @Test
    fun ordinaryAndNodeEntriesDoNotPretendToUseQuickJs() {
        assertFalse(
            RemoteApkBuildRequestPreparer.requestsExternalQuickJsRuntime(
                projectType = "node",
                configuredEntryName = "main.mjs",
                sourceName = "project",
                entryExecutionMode = JavaScriptSource.EXECUTION_MODE_NODEJS,
            ),
        )
        assertFalse(
            RemoteApkBuildRequestPreparer.requestsExternalQuickJsRuntime(
                projectType = null,
                configuredEntryName = "main.js",
                sourceName = "project",
                entryExecutionMode = 0,
            ),
        )
    }
}
