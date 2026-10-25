package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.junit.Assert.*
import org.junit.Test
import org.mozilla.javascript.Undefined

class ComposeDialogOptionsTest {
    @Test fun nullishContainersUseDefaultsAndThemesRemainInTheCommonThemePath() {
        listOf<Any?>(null, Undefined.instance, emptyMap<String, Any>(), mapOf("theme" to null)).forEach {
            assertEquals(ComposeDialogOptions(), ComposeDialogOptions.parse(it))
        }
        assertEquals(ComposeDialogOptions("bottomSheet", false), ComposeDialogOptions.parse(mapOf("type" to "bottomSheet", "cancelable" to false)))
    }
    @Test fun explicitNullishAndCoercedMembersAreRejected() {
        for (value in listOf<Any?>(null, Undefined.instance, 0, true, "bottomsheet", "dialog")) {
            val error=assertThrows(ComposeUiContractException::class.java) { ComposeDialogOptions.parse(mapOf("type" to value)) }
            assertEquals(ComposeUiErrorCodes.INVALID_ARGUMENT,error.code); assertEquals("type",error.prop)
        }
        for (value in listOf<Any?>(null, Undefined.instance, 0, 1, "true")) {
            val error=assertThrows(ComposeUiContractException::class.java) { ComposeDialogOptions.parse(mapOf("cancelable" to value)) }
            assertEquals(ComposeUiErrorCodes.INVALID_ARGUMENT,error.code); assertEquals("cancelable",error.prop)
        }
    }
    @Test fun scriptsCannotSelectWindowTypesOrTokens() {
        for (name in listOf("windowType", "windowToken", "raw", "width", "onDismiss")) {
            val error=assertThrows(ComposeUiContractException::class.java) { ComposeDialogOptions.parse(mapOf(name to true)) }
            assertEquals(ComposeUiErrorCodes.UNKNOWN_PROP,error.code); assertEquals(name,error.prop)
        }
    }
}
