package org.autojs.autojs.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.javascript.Context

class RhinoFromEntriesTest {

    @Test
    fun numericKeysRemainAccessibleAndHaveDataDescriptors() = assertScript("true", """
        var obj = Object.fromEntries([0, 6].map(v => [v, true]));
        var desc = Object.getOwnPropertyDescriptor(obj, "6");
        obj[0] === true && obj[6] === true && obj["6"] === true
            && Object.prototype.hasOwnProperty.call(obj, "6")
            && desc.value === true && desc.writable && desc.enumerable && desc.configurable
            && JSON.stringify(obj) === '{"0":true,"6":true}';
    """.trimIndent())

    @Test
    fun numericStringKeysAndDuplicateKeysUseTheSameProperty() = assertScript("true", """
        var obj = Object.fromEntries([["0", true], [6, "first"], ["6", "last"]]);
        obj[0] === true && obj[6] === "last" && obj["6"] === "last"
            && Object.keys(obj).join() === "0,6"
            && Object.getOwnPropertyDescriptor(obj, "6").value === "last";
    """.trimIndent())

    @Test
    fun propertyKeyCoercionPreservesSymbolsAndRunsOnce() = assertScript("true", """
        var symbol = Symbol("key");
        var hints = [];
        var key = { [Symbol.toPrimitive]: function(hint) { hints.push(hint); return symbol; } };
        var obj = Object.fromEntries([[Object(symbol), "first"], [key, "last"]]);
        obj[symbol] === "last" && Object.getOwnPropertySymbols(obj)[0] === symbol
            && Object.getOwnPropertySymbols(obj).length === 1 && hints.join() === "string";
    """.trimIndent())

    private fun assertScript(expected: String, script: String) {
        Context.enter().use { context ->
            context.languageVersion = Context.VERSION_ES6
            context.setEvaluationMethod(Context.EvaluationMethod.Interpreter)
            val scope = context.initStandardObjects()
            assertEquals(expected, Context.toString(context.evaluateString(scope, script, "fromEntries", 1, null)))
        }
    }
}
