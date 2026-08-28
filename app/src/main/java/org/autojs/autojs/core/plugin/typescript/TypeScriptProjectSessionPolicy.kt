package org.autojs.autojs.core.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * Additive host negotiation for resident compiler sessions.
 *
 * Missing and unknown revisions deliberately stay on the historical one-job/one-realm path.
 */
internal object TypeScriptProjectSessionPolicy {

    fun negotiatedRevision(advertisedRevision: Int?): Int? =
        advertisedRevision?.takeIf(TypeScriptR2Contract.PROJECT_SESSION_REVISION::equals)

    fun shouldPrewarmForEditor(candidates: Iterable<String?>): Boolean =
        candidates.filterNotNull().any { candidate ->
            EDITOR_EXTENSIONS.any { extension ->
                candidate.endsWith(extension, ignoreCase = true)
            }
        }

    private val EDITOR_EXTENSIONS = listOf(
        ".d.mts",
        ".d.cts",
        ".d.ts",
        ".tsx",
        ".mts",
        ".cts",
        ".ts",
    )
}
