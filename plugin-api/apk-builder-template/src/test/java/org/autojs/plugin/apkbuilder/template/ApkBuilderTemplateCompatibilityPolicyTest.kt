package org.autojs.plugin.apkbuilder.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkBuilderTemplateCompatibilityPolicyTest {

    @Test
    fun exactBuiltForHostIsAccepted() {
        val decision = evaluate(actualHostVersionCode = 5_276)

        assertEquals(ApkBuilderTemplateCompatibilityPolicy.Level.EXACT, decision.level)
        assertEquals(ApkBuilderTemplateCompatibilityPolicy.Reason.EXACT_HOST, decision.reason)
        assertTrue(decision.isCompatible)
    }

    @Test
    fun declaredPatchRangeWarnsForNonExactHost() {
        val decision = evaluate(actualHostVersionCode = 5_277)

        assertEquals(ApkBuilderTemplateCompatibilityPolicy.Level.PATCH_COMPATIBLE, decision.level)
        assertEquals(ApkBuilderTemplateCompatibilityPolicy.Reason.DECLARED_PATCH_RANGE, decision.reason)
        assertTrue(decision.isCompatible)
    }

    @Test
    fun sameRangeWithoutExplicitPermissionIsBlocked() {
        val decision = evaluate(actualHostVersionCode = 5_277, allowPatchMismatch = false)

        assertEquals(ApkBuilderTemplateCompatibilityPolicy.Level.BLOCKED, decision.level)
        assertEquals(
            ApkBuilderTemplateCompatibilityPolicy.Reason.PATCH_MISMATCH_NOT_ALLOWED,
            decision.reason,
        )
        assertFalse(decision.isCompatible)
    }

    @Test
    fun hostBelowRangeIsBlocked() {
        val decision = evaluate(actualHostVersionCode = 5_275)

        assertEquals(ApkBuilderTemplateCompatibilityPolicy.Reason.HOST_BELOW_MINIMUM, decision.reason)
        assertFalse(decision.isCompatible)
    }

    @Test
    fun hostAboveRangeIsBlocked() {
        val decision = evaluate(actualHostVersionCode = 5_279)

        assertEquals(ApkBuilderTemplateCompatibilityPolicy.Reason.HOST_ABOVE_MAXIMUM, decision.reason)
        assertFalse(decision.isCompatible)
    }

    @Test
    fun malformedDeclarationIsBlocked() {
        val decision = ApkBuilderTemplateCompatibilityPolicy.evaluate(
            declaration = declaration(minimum = 5_278, maximum = 5_277),
            actualHostVersionCode = 5_276,
        )

        assertEquals(ApkBuilderTemplateCompatibilityPolicy.Reason.INVALID_DECLARATION, decision.reason)
        assertFalse(decision.isCompatible)
    }

    @Test
    fun unknownHostVersionIsBlocked() {
        val decision = evaluate(actualHostVersionCode = 0)

        assertEquals(ApkBuilderTemplateCompatibilityPolicy.Reason.INVALID_HOST_VERSION, decision.reason)
        assertFalse(decision.isCompatible)
    }

    private fun evaluate(
        actualHostVersionCode: Long,
        allowPatchMismatch: Boolean = true,
    ) = ApkBuilderTemplateCompatibilityPolicy.evaluate(
        declaration = declaration(allowPatchMismatch = allowPatchMismatch),
        actualHostVersionCode = actualHostVersionCode,
    )

    private fun declaration(
        minimum: Long = 5_276,
        maximum: Long = 5_278,
        allowPatchMismatch: Boolean = true,
    ) = ApkBuilderTemplateCompatibilityPolicy.Declaration(
        builtForHostVersionCode = 5_276,
        minHostVersionCode = minimum,
        maxHostVersionCode = maximum,
        allowPatchVersionMismatch = allowPatchMismatch,
    )
}
