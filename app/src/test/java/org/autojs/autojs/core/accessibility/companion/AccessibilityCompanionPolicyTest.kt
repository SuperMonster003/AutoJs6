package org.autojs.autojs.core.accessibility.companion

import org.autojs.autojs.core.accessibility.companion.AccessibilityCompanionPolicy.Decision
import org.autojs.plugin.common.api.AutoJs6AccessibilityCompanionContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityCompanionPolicyTest {

    private val packageName = "io.example.companion"
    private val component = "$packageName/com.example.CompanionService"

    @Test
    fun companionRequiresAKnownPolicyAndAComponentOfItsOwnPackage() {
        val companion = AccessibilityCompanionPolicy.companionOf(packageName, " $component ", "follow-host")
        assertNotNull(companion)
        assertEquals(component, companion!!.serviceComponent)
        assertTrue(companion.followsHost)
        assertFalse(AccessibilityCompanionPolicy.companionOf(packageName, component, "enabled")!!.followsHost)

        assertNull("pre-contract plugin", AccessibilityCompanionPolicy.companionOf(packageName, component, null))
        assertNull("unknown policy", AccessibilityCompanionPolicy.companionOf(packageName, component, "FOLLOW_HOST"))
        assertNull("foreign package", AccessibilityCompanionPolicy.companionOf(packageName, "other.package/com.example.Service", "follow-host"))
        assertNull("not a component", AccessibilityCompanionPolicy.companionOf(packageName, packageName, "follow-host"))
        assertNull("blank component", AccessibilityCompanionPolicy.companionOf(packageName, " ", "follow-host"))
    }

    @Test
    fun followHostMirrorsTheListedStateOfTheHostService() {
        val companion = AccessibilityCompanionPolicy.companionOf(packageName, component, "follow-host")!!
        assertEquals(Decision.ATTACH, AccessibilityCompanionPolicy.decide(companion, hostServiceEnabled = true, companionServiceEnabled = false))
        assertEquals(Decision.DETACH, AccessibilityCompanionPolicy.decide(companion, hostServiceEnabled = false, companionServiceEnabled = true))
        assertEquals(Decision.NONE, AccessibilityCompanionPolicy.decide(companion, hostServiceEnabled = true, companionServiceEnabled = true))
        assertEquals(Decision.NONE, AccessibilityCompanionPolicy.decide(companion, hostServiceEnabled = false, companionServiceEnabled = false))
    }

    @Test
    fun explicitCompanionChoicesAreNeverOverridden() {
        listOf("enabled", "disabled").forEach { policy ->
            val companion = AccessibilityCompanionPolicy.companionOf(packageName, component, policy)!!
            assertEquals(policy, Decision.NONE, AccessibilityCompanionPolicy.decide(companion, hostServiceEnabled = true, companionServiceEnabled = false))
            assertEquals(policy, Decision.NONE, AccessibilityCompanionPolicy.decide(companion, hostServiceEnabled = false, companionServiceEnabled = true))
        }
    }

    @Test
    fun knownPoliciesMatchTheSharedContract() {
        assertEquals(
            setOf(
                AutoJs6AccessibilityCompanionContract.SERVICE_POLICY_FOLLOW_HOST,
                AutoJs6AccessibilityCompanionContract.SERVICE_POLICY_ENABLED,
                AutoJs6AccessibilityCompanionContract.SERVICE_POLICY_DISABLED,
            ),
            AccessibilityCompanionPolicy.KNOWN_POLICIES,
        )
        assertEquals("org.autojs.plugin.category.ACCESSIBILITY_COMPANION", AutoJs6AccessibilityCompanionContract.INFO_CATEGORY)
        assertEquals(
            "org.autojs.autojs6/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher",
            AutoJs6AccessibilityCompanionContract.HOST_SERVICE_COMPONENT,
        )
    }
}
