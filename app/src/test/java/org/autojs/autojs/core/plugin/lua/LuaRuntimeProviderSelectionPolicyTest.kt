package org.autojs.autojs.core.plugin.lua

import org.autojs.plugin.lua.runtime.api.LuaProtocolVersion
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaRuntimeProviderSelectionPolicyTest {

    @Test
    fun discoveryIdentityAndPackageBoundariesFailClosed() {
        assertEquals(LuaRuntimeProviderRejection.WRONG_INFO_ACTION, preflight(descriptor(infoAction = "wrong")))
        assertEquals(LuaRuntimeProviderRejection.WRONG_RUNTIME_ACTION, preflight(descriptor(runtimeAction = "wrong")))
        assertEquals(LuaRuntimeProviderRejection.WRONG_PLUGIN_ID, preflight(descriptor(pluginId = "wrong")))
        assertEquals(LuaRuntimeProviderRejection.WRONG_ENGINE, preflight(descriptor(engine = "wrong")))
        assertEquals(LuaRuntimeProviderRejection.WRONG_VARIANT, preflight(descriptor(variant = "wrong")))
        assertEquals(LuaRuntimeProviderRejection.NOT_EXPORTED, preflight(descriptor(exported = false)))
        assertEquals(LuaRuntimeProviderRejection.DISABLED, preflight(descriptor(applicationEnabled = false)))
        assertEquals(LuaRuntimeProviderRejection.DISABLED, preflight(descriptor(serviceEnabled = false)))
        assertEquals(LuaRuntimeProviderRejection.WRONG_PERMISSION, preflight(descriptor(requiredPermission = null)))
        assertEquals(LuaRuntimeProviderRejection.INVALID_PACKAGE_IDENTITY, preflight(descriptor(packageUid = -1)))
        assertEquals(LuaRuntimeProviderRejection.INVALID_PACKAGE_IDENTITY, preflight(descriptor(versionCode = 0)))
        assertEquals(LuaRuntimeProviderRejection.INVALID_PACKAGE_IDENTITY, preflight(descriptor(lastUpdateTime = -1)))
        assertEquals(
            LuaRuntimeProviderRejection.INVALID_PACKAGE_IDENTITY,
            preflight(descriptor(signerSha256 = emptyList())),
        )
        assertEquals(
            LuaRuntimeProviderRejection.INVALID_PACKAGE_IDENTITY,
            preflight(descriptor(signerSha256 = listOf("not-a-sha256"))),
        )
        assertEquals(
            LuaRuntimeProviderRejection.INVALID_PACKAGE_IDENTITY,
            preflight(descriptor(signerSha256 = listOf(SIGNER_A, SIGNER_A.uppercase()))),
        )
    }

    @Test
    fun infoPackageAndRuntimeMetadataMustBeConsistent() {
        assertEquals(
            LuaRuntimeProviderRejection.INFO_PACKAGE_VERSION_MISMATCH,
            preflight(descriptor(infoVersionCode = 8L)),
        )
        assertEquals(
            LuaRuntimeProviderRejection.INFO_ABI_INVENTORY_INVALID,
            preflight(descriptor(infoSupportedAbis = emptyList())),
        )
        assertEquals(
            LuaRuntimeProviderRejection.INFO_ABI_INVENTORY_INVALID,
            preflight(descriptor(infoSupportedAbis = listOf("arm64-v8a", "arm64-v8a"))),
        )
        assertEquals(
            LuaRuntimeProviderRejection.INFO_RUNTIME_VERSION_MISMATCH,
            rejection(runtimeInfo = runtimeInfo(providerVersionCode = 8L)),
        )
        assertEquals(
            LuaRuntimeProviderRejection.INFO_RUNTIME_ABI_MISMATCH,
            rejection(
                descriptor = descriptor(infoSupportedAbis = listOf("arm64-v8a", "x86_64")),
                runtimeInfo = runtimeInfo(supportedAbis = listOf("arm64-v8a")),
            ),
        )

        assertTrue(
            select(
                descriptor = descriptor(infoSupportedAbis = listOf("x86_64", "arm64-v8a")),
                runtimeInfo = runtimeInfo(supportedAbis = listOf("arm64-v8a", "x86_64")),
            ) is LuaRuntimeProviderSelectionResult.Selected,
        )
    }

    /** INFO lists the ABIs packed in the installed APK, a subset of the build's ABIs (docs/memory-phase2/ROADMAP.md C40). */
    @Test
    fun infoAbisMayBeASubsetOfRuntimeAbisThatTheDeviceSupports() {
        val buildAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        val device = environment(supportedProcessAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"))

        // Universal APK installed as is.
        assertTrue(
            select(
                descriptor = descriptor(infoSupportedAbis = buildAbis.reversed()),
                runtimeInfo = runtimeInfo(supportedAbis = buildAbis),
                environment = device,
            ) is LuaRuntimeProviderSelectionResult.Selected,
        )
        // Single-ABI split APK, or a universal APK whose foreign ABIs the installer stripped.
        assertTrue(
            select(
                descriptor = descriptor(infoSupportedAbis = listOf("arm64-v8a")),
                runtimeInfo = runtimeInfo(supportedAbis = buildAbis),
                environment = device,
            ) is LuaRuntimeProviderSelectionResult.Selected,
        )
        // A split APK for another device packs no ABI this device can load.
        assertEquals(
            LuaRuntimeProviderRejection.INFO_ABI_UNSUPPORTED,
            rejection(
                descriptor = descriptor(infoSupportedAbis = listOf("x86_64")),
                runtimeInfo = runtimeInfo(supportedAbis = buildAbis),
                environment = device,
            ),
        )
        // INFO claims an ABI the runtime was not built for.
        assertEquals(
            LuaRuntimeProviderRejection.INFO_RUNTIME_ABI_MISMATCH,
            rejection(
                descriptor = descriptor(infoSupportedAbis = listOf("arm64-v8a", "riscv64")),
                runtimeInfo = runtimeInfo(supportedAbis = buildAbis),
                environment = device,
            ),
        )
    }

    @Test
    fun trustRequiresAnExplicitHostAcceptedSource() {
        assertEquals(
            LuaRuntimeProviderRejection.TRUST_REQUIRED,
            preflight(descriptor(trustLevel = LuaRuntimeProviderTrustLevel.UNTRUSTED)),
        )
        assertEquals(
            LuaRuntimeProviderRejection.USER_APPROVAL_REQUIRED,
            preflight(descriptor(trustLevel = LuaRuntimeProviderTrustLevel.USER_APPROVED)),
        )
        assertEquals(
            LuaRuntimeProviderRejection.DEBUG_TRUST_FORBIDDEN,
            preflight(descriptor(trustLevel = LuaRuntimeProviderTrustLevel.DEBUG_ONLY)),
        )
        assertEquals(
            null,
            preflight(
                descriptor(trustLevel = LuaRuntimeProviderTrustLevel.USER_APPROVED),
                environment(allowUserApprovedProviders = true),
            ),
        )
        assertEquals(
            null,
            preflight(
                descriptor(trustLevel = LuaRuntimeProviderTrustLevel.DEBUG_ONLY),
                environment(releaseBuild = false),
            ),
        )
        assertEquals(
            null,
            preflight(
                descriptor(
                    trustLevel = LuaRuntimeProviderTrustLevel.UNTRUSTED,
                    sameSignerAsHost = true,
                ),
            ),
        )
    }

    @Test
    fun protocolHostVersionSlotAbiAndCapabilitiesMustMatch() {
        assertEquals(
            LuaRuntimeProviderRejection.HOST_VERSION_UNSUPPORTED,
            rejection(runtimeInfo = runtimeInfo(minHostVersionCode = 7_000L)),
        )
        assertEquals(
            LuaRuntimeProviderRejection.PROTOCOL_UNSUPPORTED,
            rejection(
                runtimeInfo = runtimeInfo(
                    protocolMin = LuaProtocolVersion(2, 0),
                    protocolMax = LuaProtocolVersion(2, 0),
                ),
            ),
        )
        assertEquals(
            LuaRuntimeProviderRejection.RUNTIME_SLOT_UNSUPPORTED,
            rejection(runtimeInfo = runtimeInfo(runtimeSlot = "lua55")),
        )
        assertEquals(
            LuaRuntimeProviderRejection.ABI_UNSUPPORTED,
            rejection(
                descriptor = descriptor(infoSupportedAbis = listOf("arm64-v8a", "x86_64")),
                runtimeInfo = runtimeInfo(processAbi = "x86_64", supportedAbis = listOf("arm64-v8a", "x86_64")),
                environment = environment(supportedProcessAbis = listOf("arm64-v8a")),
            ),
        )
        assertEquals(
            LuaRuntimeProviderRejection.CAPABILITY_MISMATCH,
            rejection(
                runtimeInfo = runtimeInfo(capabilities = listOf(CAPABILITY_DEVICE)),
                requirement = requirement(requiredCapabilities = listOf(CAPABILITY_CONSOLE)),
            ),
        )
        assertEquals(
            LuaRuntimeProviderRejection.INVALID_RUNTIME_INFO,
            rejection(runtimeInfo = runtimeInfo(providerId = "Invalid Provider")),
        )
    }

    @Test
    fun singleProviderPinsImmutableCompleteIdentityAndCallerUid() {
        val mutableSigners = arrayListOf(SIGNER_B.uppercase(), SIGNER_A.uppercase())
        val mutableInfoAbis = arrayListOf("arm64-v8a", "x86_64")
        val mutableCapabilities = arrayListOf(CAPABILITY_DEVICE, CAPABILITY_CONSOLE)
        val descriptor = descriptor(
            signerSha256 = mutableSigners,
            infoSupportedAbis = mutableInfoAbis,
        )
        val runtimeInfo = runtimeInfo(
            supportedAbis = listOf("x86_64", "arm64-v8a"),
            capabilities = mutableCapabilities,
        )
        mutableSigners.clear()
        mutableInfoAbis.clear()
        mutableCapabilities.clear()

        val selected = select(descriptor, runtimeInfo) as LuaRuntimeProviderSelectionResult.Selected
        val pinned = selected.pinned

        assertEquals(descriptor.component, pinned.component)
        assertEquals(descriptor.packageUid, pinned.packageUid)
        assertEquals(descriptor.versionCode, pinned.versionCode)
        assertEquals(descriptor.lastUpdateTime, pinned.lastUpdateTime)
        assertEquals(listOf(SIGNER_A, SIGNER_B), pinned.signerSha256)
        assertEquals(LuaProtocolVersion(1, 0), pinned.protocolVersion)
        assertEquals(LuaRuntimeContract.RUNTIME_SLOT_LUA54, pinned.runtimeSlot)
        assertEquals("5.4.8", pinned.languageVersion)
        assertEquals("arm64-v8a", pinned.processAbi)
        assertEquals(listOf(CAPABILITY_CONSOLE, CAPABILITY_DEVICE), pinned.grantedCapabilities)
        assertTrue(LuaRuntimeProviderCallbackCallerPolicy.isExpectedUid(pinned, descriptor.packageUid))
        assertFalse(LuaRuntimeProviderCallbackCallerPolicy.isExpectedUid(pinned, descriptor.packageUid + 1))
        assertFalse(LuaRuntimeProviderCallbackCallerPolicy.isExpectedUid(pinned, -1))

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (pinned.signerSha256 as MutableList<String>).add(SIGNER_A)
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (pinned.grantedCapabilities as MutableList<String>).add("files.read")
        }
    }

    @Test
    fun zeroMultipleAndPreferredSelectionAreExplicit() {
        assertTrue(
            LuaRuntimeProviderSelectionPolicy.select(
                candidates = emptyList(),
                environment = environment(),
                requirement = requirement(),
            ) is LuaRuntimeProviderSelectionResult.NoMatch,
        )

        val alphaDescriptor = descriptor(component = component("alpha.lua.provider", "Runtime"), packageUid = 10_001)
        val zetaDescriptor = descriptor(component = component("zeta.lua.provider", "Runtime"), packageUid = 10_002)
        val candidates = listOf(
            LuaRuntimeProviderCandidate(zetaDescriptor, runtimeInfo(providerId = "zeta.provider")),
            LuaRuntimeProviderCandidate(alphaDescriptor, runtimeInfo(providerId = "alpha.provider")),
        )
        val ambiguous = LuaRuntimeProviderSelectionPolicy.select(candidates, environment(), requirement())
            as LuaRuntimeProviderSelectionResult.Ambiguous
        assertEquals(
            listOf("alpha.lua.provider", "zeta.lua.provider"),
            ambiguous.candidates.map { it.packageName },
        )

        val selected = LuaRuntimeProviderSelectionPolicy.select(
            candidates,
            environment(),
            requirement(),
            preferredComponent = zetaDescriptor.component,
        ) as LuaRuntimeProviderSelectionResult.Selected
        assertEquals(zetaDescriptor.component, selected.pinned.component)

        val duplicate = LuaRuntimeProviderSelectionPolicy.select(
            listOf(
                LuaRuntimeProviderCandidate(alphaDescriptor, runtimeInfo()),
                LuaRuntimeProviderCandidate(alphaDescriptor.copy(), runtimeInfo()),
            ),
            environment(),
            requirement(),
            preferredComponent = alphaDescriptor.component,
        )
        assertTrue(duplicate is LuaRuntimeProviderSelectionResult.Ambiguous)
    }

    @Test
    fun revalidationRejectsEveryPinnedPackageAndRuntimeDrift() {
        val originalDescriptor = descriptor()
        val originalInfo = runtimeInfo()
        val pinned = (select(originalDescriptor, originalInfo) as LuaRuntimeProviderSelectionResult.Selected).pinned

        assertTrue(revalidate(pinned, originalDescriptor, originalInfo))
        assertFalse(revalidate(pinned, originalDescriptor.copy(packageUid = 10_002), originalInfo))
        assertFalse(revalidate(pinned, originalDescriptor.copy(versionCode = 8L, infoVersionCode = 8L), originalInfo))
        assertFalse(revalidate(pinned, originalDescriptor.copy(lastUpdateTime = 124L), originalInfo))
        assertFalse(revalidate(pinned, originalDescriptor.copy(signerSha256 = listOf(SIGNER_B)), originalInfo))
        assertFalse(revalidate(pinned, originalDescriptor.copy(variant = "wrong"), originalInfo))
        assertFalse(revalidate(pinned, originalDescriptor, runtimeInfo(providerId = "changed.provider")))
        assertFalse(revalidate(pinned, originalDescriptor, runtimeInfo(providerVersionName = "1.0.1")))
        assertFalse(revalidate(pinned, originalDescriptor, runtimeInfo(languageVersion = "5.4.7")))
        assertFalse(
            revalidate(
                pinned,
                originalDescriptor.copy(infoSupportedAbis = listOf("x86_64")),
                runtimeInfo(processAbi = "x86_64"),
            ),
        )
        assertFalse(revalidate(pinned, originalDescriptor, runtimeInfo(capabilities = listOf(CAPABILITY_CONSOLE))))
        assertFalse(
            revalidate(
                pinned,
                originalDescriptor,
                runtimeInfo(protocolMax = LuaProtocolVersion(1, 1)),
            ),
        )
        assertFalse(revalidate(pinned, originalDescriptor, runtimeInfo(limits = runtimeLimits(maxSourceBytes = 1024L))))
        assertFalse(
            LuaRuntimeProviderSelectionPolicy.revalidate(
                pinned,
                current = null,
                environment = environment(),
                requirement = requirement(),
            ),
        )
    }

    @Test
    fun environmentAndRequirementRejectAmbiguousHostPolicyInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            environment(supportedProcessAbis = listOf("arm64-v8a", "arm64-v8a"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            requirement(hostCapabilities = listOf("invalid capability"), requiredCapabilities = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            requirement(hostCapabilities = listOf(CAPABILITY_CONSOLE), requiredCapabilities = listOf(CAPABILITY_DEVICE))
        }
    }

    private fun preflight(
        descriptor: LuaRuntimeProviderDescriptor,
        environment: LuaRuntimeProviderEnvironment = environment(),
    ) = LuaRuntimeProviderSelectionPolicy.preflight(descriptor, environment)

    private fun rejection(
        descriptor: LuaRuntimeProviderDescriptor = descriptor(),
        runtimeInfo: org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo = runtimeInfo(),
        environment: LuaRuntimeProviderEnvironment = environment(),
        requirement: LuaRuntimeProviderRequirement = requirement(),
    ): LuaRuntimeProviderRejection {
        val result = select(descriptor, runtimeInfo, environment, requirement)
            as LuaRuntimeProviderSelectionResult.NoMatch
        return result.evaluations.single().rejection
    }

    private fun select(
        descriptor: LuaRuntimeProviderDescriptor,
        runtimeInfo: org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo,
        environment: LuaRuntimeProviderEnvironment = environment(),
        requirement: LuaRuntimeProviderRequirement = requirement(),
    ): LuaRuntimeProviderSelectionResult = LuaRuntimeProviderSelectionPolicy.select(
        candidates = listOf(LuaRuntimeProviderCandidate(descriptor, runtimeInfo)),
        environment = environment,
        requirement = requirement,
    )

    private fun revalidate(
        pinned: LuaRuntimeProviderPinnedIdentity,
        descriptor: LuaRuntimeProviderDescriptor,
        runtimeInfo: org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo,
    ) = LuaRuntimeProviderSelectionPolicy.revalidate(
        pinned = pinned,
        current = LuaRuntimeProviderCandidate(descriptor, runtimeInfo),
        environment = environment(),
        requirement = requirement(),
    )

    private companion object {
        val SIGNER_A = LuaRuntimeHostTestFixtures.SIGNER_A
        val SIGNER_B = LuaRuntimeHostTestFixtures.SIGNER_B
        val CAPABILITY_CONSOLE = LuaRuntimeHostTestFixtures.CAPABILITY_CONSOLE
        val CAPABILITY_DEVICE = LuaRuntimeHostTestFixtures.CAPABILITY_DEVICE
    }
}

private fun component(
    packageName: String = "example.lua.provider",
    className: String = "LuaRuntimeService",
) = LuaRuntimeHostTestFixtures.component(packageName, className)

private fun descriptor(
    component: LuaRuntimeProviderComponent = component(),
    infoAction: String = org.autojs.plugin.lua.runtime.api.LuaPluginActions.INFO,
    runtimeAction: String = LuaRuntimeContract.SERVICE_ACTION,
    pluginId: String = org.autojs.plugin.lua.runtime.api.LuaPluginIds.ID,
    engine: String = org.autojs.plugin.lua.runtime.api.LuaPluginIds.ENGINE,
    variant: String = org.autojs.plugin.lua.runtime.api.LuaPluginIds.VARIANT_PUC_LUA_5_4,
    infoVersionCode: Long = 7L,
    infoSupportedAbis: Collection<String> = listOf("arm64-v8a"),
    exported: Boolean = true,
    applicationEnabled: Boolean = true,
    serviceEnabled: Boolean = true,
    requiredPermission: String? = LuaRuntimeContract.PLUGIN_PERMISSION,
    packageUid: Int = 10_001,
    versionCode: Long = 7L,
    lastUpdateTime: Long = 123L,
    signerSha256: Collection<String> = listOf(LuaRuntimeHostTestFixtures.SIGNER_A),
    trustLevel: LuaRuntimeProviderTrustLevel = LuaRuntimeProviderTrustLevel.OFFICIAL,
    sameSignerAsHost: Boolean = false,
) = LuaRuntimeHostTestFixtures.descriptor(
    component = component,
    infoAction = infoAction,
    runtimeAction = runtimeAction,
    pluginId = pluginId,
    engine = engine,
    variant = variant,
    infoVersionCode = infoVersionCode,
    infoSupportedAbis = infoSupportedAbis,
    exported = exported,
    applicationEnabled = applicationEnabled,
    serviceEnabled = serviceEnabled,
    requiredPermission = requiredPermission,
    packageUid = packageUid,
    versionCode = versionCode,
    lastUpdateTime = lastUpdateTime,
    signerSha256 = signerSha256,
    trustLevel = trustLevel,
    sameSignerAsHost = sameSignerAsHost,
)

private fun runtimeInfo(
    protocolMin: LuaProtocolVersion = LuaProtocolVersion(1, 0),
    protocolMax: LuaProtocolVersion = LuaProtocolVersion(1, 0),
    providerId: String = "fixture.lua.provider",
    providerVersionName: String = "1.0.0",
    providerVersionCode: Long = 7L,
    runtimeSlot: String = LuaRuntimeContract.RUNTIME_SLOT_LUA54,
    languageVersion: String = "5.4.8",
    processAbi: String = "arm64-v8a",
    supportedAbis: Collection<String> = listOf(processAbi),
    capabilities: Collection<String> = listOf(
        LuaRuntimeHostTestFixtures.CAPABILITY_CONSOLE,
        LuaRuntimeHostTestFixtures.CAPABILITY_DEVICE,
    ),
    limits: org.autojs.plugin.lua.runtime.api.LuaRuntimeLimits = runtimeLimits(),
    minHostVersionCode: Long? = 1L,
) = LuaRuntimeHostTestFixtures.runtimeInfo(
    protocolMin = protocolMin,
    protocolMax = protocolMax,
    providerId = providerId,
    providerVersionName = providerVersionName,
    providerVersionCode = providerVersionCode,
    runtimeSlot = runtimeSlot,
    languageVersion = languageVersion,
    processAbi = processAbi,
    supportedAbis = supportedAbis,
    capabilities = capabilities,
    limits = limits,
    minHostVersionCode = minHostVersionCode,
)

private fun runtimeLimits(maxSourceBytes: Long = LuaRuntimeContract.MAX_SOURCE_BYTES) =
    LuaRuntimeHostTestFixtures.runtimeLimits(maxSourceBytes = maxSourceBytes)

private fun environment(
    supportedProcessAbis: Collection<String> = listOf("arm64-v8a", "x86_64"),
    releaseBuild: Boolean = true,
    allowUserApprovedProviders: Boolean = false,
) = LuaRuntimeHostTestFixtures.environment(
    supportedProcessAbis = supportedProcessAbis,
    releaseBuild = releaseBuild,
    allowUserApprovedProviders = allowUserApprovedProviders,
)

private fun requirement(
    hostCapabilities: Collection<String> = listOf(
        LuaRuntimeHostTestFixtures.CAPABILITY_CONSOLE,
        LuaRuntimeHostTestFixtures.CAPABILITY_DEVICE,
    ),
    requiredCapabilities: Collection<String> = listOf(LuaRuntimeHostTestFixtures.CAPABILITY_CONSOLE),
) = LuaRuntimeHostTestFixtures.requirement(hostCapabilities, requiredCapabilities)
