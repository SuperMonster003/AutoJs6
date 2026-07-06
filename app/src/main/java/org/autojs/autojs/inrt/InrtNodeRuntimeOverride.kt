package org.autojs.autojs.inrt

import android.content.Context
import android.util.Log
import org.autojs.autojs.project.ProjectConfig
import java.util.Locale

object InrtNodeRuntimeOverride {

    fun apply(context: Context) {
        val projectConfig = ProjectConfig.fromAssets(
            context,
            ProjectConfig.configFileOfDir(ASSETS_PROJECT_DIR),
        ) ?: return

        if (!projectConfig.requestsEmbeddedNodeRuntime()) {
            return
        }

        val previous = System.getProperty(NODE_BACKEND_PROPERTY)
        if (previous.isNullOrBlank() || previous.equals(RHINO_BACKEND, ignoreCase = true)) {
            System.setProperty(NODE_BACKEND_PROPERTY, EMBEDDED_BACKEND)
            Log.i(TAG, "Embedded Node.js selected; overriding packaged Node.js backend to embedded.")
        }
    }

    private fun ProjectConfig.requestsEmbeddedNodeRuntime(): Boolean {
        if (libs.any { it.equals(EMBEDDED_NODE_LIBRARY_LABEL, ignoreCase = true) }) {
            return true
        }
        return nodeConfig
            ?.backend
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .let { it == EMBEDDED_BACKEND || it == EMBED_BACKEND_ALIAS }
    }

    private const val TAG = "InrtNodeRuntimeOverride"
    private const val ASSETS_PROJECT_DIR = "project"
    private const val NODE_BACKEND_PROPERTY = "autojs.nodejs.backend"
    private const val EMBEDDED_NODE_LIBRARY_LABEL = "Embedded Node.js"
    private const val RHINO_BACKEND = "rhino"
    private const val EMBEDDED_BACKEND = "embedded"
    private const val EMBED_BACKEND_ALIAS = "embed"
}
