package org.autojs.autojs.runtime.api.modules.tts

import org.autojs.autojs.runtime.api.augment.tts.Tts
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsSurfaceTest {

    @Test
    fun canonicalFunctionsAndCompatibilityAliasesStayExported() {
        val exported = Tts.FUNCTION_ASSIGNMENTS.flatMap { declaration ->
            when (declaration) {
                is String -> listOf(declaration)
                is Pair<*, *> -> when (val aliases = declaration.second) {
                    is String -> listOf(aliases)
                    is List<*> -> aliases.filterIsInstance<String>()
                    else -> emptyList()
                }
                else -> emptyList()
            }
        }.toSet()

        assertEquals(
            setOf(
                "speak",
                "enqueue",
                "utterance",
                "speakTask",
                "enqueueUtterance",
                "enqueueTask",
                "synthesize",
                "synthesizeToFile",
                "synthesis",
                "synthesizeTask",
                "synthesisTask",
                "silence",
                "silenceTask",
                "ready",
                "engines",
                "voices",
                "languages",
                "isLanguageAvailable",
                "languageAvailability",
                "stop",
                "stopAll",
                "reset",
                "isSpeaking",
                "isBusy",
                "status",
                "defaults",
                "config",
                "configure",
                "getMaxInputLength",
                "openSettings",
                "installData",
            ),
            exported,
        )
    }
}
