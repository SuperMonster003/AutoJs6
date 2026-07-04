import groovy.json.JsonOutput
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

private fun nodeJsInstrumentationExtraName(name: String): String = "nodeJsInstrumentation.$name"

@Suppress("UNCHECKED_CAST")
private fun <T> nodeJsInstrumentationInput(name: String): T =
    extensions.extraProperties.get(nodeJsInstrumentationExtraName(name)) as T

private val taskNames = nodeJsInstrumentationInput<List<String>>("taskNames")
private val nodeJsNetworkExperimentalEnabled = nodeJsInstrumentationInput<Boolean>("nodeJsNetworkExperimentalEnabled")
private val nodeJsWorkerThreadsExperimentalEnabled = nodeJsInstrumentationInput<Boolean>("nodeJsWorkerThreadsExperimentalEnabled")
private val nodeJsTestDeviceProfile = nodeJsInstrumentationInput<String?>("nodeJsTestDeviceProfile")

val nodeJsV11AlphaInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest",
    "org.autojs.autojs.engine.NodeSourceMapStackTraceInstrumentationTest",
    "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
    "org.autojs.autojs.engine.NodeWebPlatformGlobalsInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpClientFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpsClientFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpStreamSemanticsInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpAgentFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeUndiciFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpNpmCorpusInstrumentationTest",
    "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
    "org.autojs.autojs.engine.NodeModernCoreDifferentialInstrumentationTest",
    "org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest",
    "org.autojs.autojs.engine.NodeStreamMvpInstrumentationTest",
    "org.autojs.autojs.engine.NodeZlibBufferInstrumentationTest",
    "org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest",
    "org.autojs.autojs.engine.NodeFdApiInstrumentationTest",
    "org.autojs.autojs.engine.NodeOpendirInstrumentationTest",
    "org.autojs.autojs.engine.NodeFsWatchInstrumentationTest",
    "org.autojs.autojs.engine.NodeSafeFileOperationsInstrumentationTest",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeConformanceInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgePermissionManifestInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeConcurrencyLimitsInstrumentationTest",
    "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest",
    "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeNotificationsBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeShellBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunV11BridgeEmbeddedNodePackagedApk",
) + if (nodeJsWorkerThreadsExperimentalEnabled) {
    listOf(
        "org.autojs.autojs.engine.NodeWorkerThreadsBootstrapInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest",
        "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk",
    )
} else {
    listOf(
        "org.autojs.autojs.engine.NodeWorkerThreadsBootstrapInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest",
    )
}
val nodeJsV11BetaInstrumentationClasses = (
        listOf(
            "org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest",
            "org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest",
            "org.autojs.autojs.engine.NodeNativeAddonPolicyInstrumentationTest",
            "org.autojs.autojs.engine.NodeModuleCompileCacheInstrumentationTest",
            "org.autojs.autojs.engine.NodeRuntimePrewarmPoolInstrumentationTest",
            "org.autojs.autojs.engine.NodePerformanceMatrixInstrumentationTest",
            "org.autojs.autojs.engine.NodeInspectorDesignGateInstrumentationTest",
            "org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest",
            "org.autojs.autojs.engine.NodeHardenedIsolatedRuntimePocInstrumentationTest",
            "org.autojs.autojs.engine.NodeCapabilityBrokerV2InstrumentationTest",
            "org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicyInstrumentationTest",
            "org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest",
            "org.autojs.autojs.engine.NodeScopedFsFuzzerInstrumentationTest",
            "org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest",
        ) + nodeJsV11AlphaInstrumentationClasses
        ).distinct()
val nodeJsPackagedEsmGateInstrumentationClasses = listOf(
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunFullCompatibilityEmbeddedNodePackagedApk",
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunExperimentalEsmEmbeddedNodePackagedApk",
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunDynamicImportLocalEmbeddedNodePackagedApk",
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunRuntimeCompatibilityDescriptorMismatchEmbeddedNodePackagedApk",
)
val nodeJsPackagedParityGateInstrumentationClasses = nodeJsPackagedEsmGateInstrumentationClasses
val nodeJsRhinoCompatPackagedSmokeInstrumentationClasses = listOf(
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunRhinoCompatEmbeddedNodePackagedApk",
)
val nodeJsRhinoDualEngineDeviceEvidenceInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.RhinoNodeDualEngineCompatInstrumentationTest",
)
val nodeJsRhinoCompatResourceCleanupSecurityInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeBridgeResourceRegistryInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest#jsImageExpansionUsesFakeTransportAndBlocksShadowing",
    "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest#mediaProjectionAndImageModulesUseFakeTransportAndBlockShadowing",
    "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#jsRhinoCompatSelectorFacadeUsesFakeTransport",
    "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#jsAccessibilitySelectorV2HonorsTimeoutAndCancellation",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#jsUiModuleUsesFakeTransportForLayoutEventsAndPolicy",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
    "org.autojs.autojs.engine.NodeDatabaseBridgeInstrumentationTest#jsDatabaseBridgeUsesFakeTransportAndBlocksShadowing",
    "org.autojs.autojs.engine.NodeDatabaseBridgeInstrumentationTest#androidDatabaseProviderPersistsTransactionsAndDeniesEscapes",
    "org.autojs.autojs.engine.NodeDatabaseBridgeInstrumentationTest#androidDatabaseProviderRegistersAndClosesResources",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsFetchModuleUsesFakeTransportAndKeepsRawModulesDisabled",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsFetchPolicyHardeningUsesFakeTransport",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#androidFetchProviderStaysDisabledInNormalBuilds",
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#websocketMvpUsesControlledBridgeAndKeepsRawSocketsDisabled",
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#androidWebSocketProviderStaysDisabledInNormalBuilds",
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase5BridgeNetworkUiAndShellBoundariesStayDenied",
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase7CapabilityBypassDeniesBeforeProviderDispatch",
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase7ProviderPolicyAndPluginEscapesStayDeniedBeforeDispatch",
)
val nodeJsLiveProviderSummaryTaskName = "generateNodeLiveProviderSummary"
val nodeJsLiveProviderGateCases = listOf(
    mapOf(
        "provider" to "registry",
        "className" to "org.autojs.autojs.engine.NodeBridgeProviderRegistryInstrumentationTest",
        "methodName" to "providerRegistryDefinesPhase7LiveProviderSurface",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; freezes live provider surface metadata",
    ),
    mapOf(
        "provider" to "registry",
        "className" to "org.autojs.autojs.engine.NodeBridgeProviderRegistryInstrumentationTest",
        "methodName" to "androidHealthReflectsCurrentProviderAvailabilityWithoutPromotingFutureProviders",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; records provider health states",
    ),
    mapOf(
        "provider" to "doctor",
        "className" to "org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest",
        "methodName" to "generatesPrivacyRedactedTextAndJsonReports",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; records Doctor provider-health reporting",
    ),
    mapOf(
        "provider" to "fetch",
        "className" to "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest",
        "methodName" to "jsFetchAndAxiosUseAndroidLiveProviderWhenNetworkExperimentEnabled",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; pass -Pautojs.nodejs.network.experimental=true",
    ),
    mapOf(
        "provider" to "fetch",
        "className" to "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest",
        "methodName" to "androidFetchProviderCancelsActiveLiveRequestOnDestroy",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; pass -Pautojs.nodejs.network.experimental=true",
    ),
    mapOf(
        "provider" to "websocket",
        "className" to "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest",
        "methodName" to "jsWebSocketUsesAndroidLiveProviderWhenNetworkExperimentEnabled",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; pass -Pautojs.nodejs.network.experimental=true",
    ),
    mapOf(
        "provider" to "websocket",
        "className" to "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest",
        "methodName" to "androidWebSocketProviderClosesActiveLiveConnectionOnDestroy",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; pass -Pautojs.nodejs.network.experimental=true",
    ),
    mapOf(
        "provider" to "ocr",
        "className" to "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest",
        "methodName" to "kotlinOcrLiveProviderRecognizesMlKitImageHandle",
        "interaction" to "none",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when MLKit OCR provider is unavailable and JUnit records the reason",
    ),
    mapOf(
        "provider" to "ocr",
        "className" to "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest",
        "methodName" to "androidOcrRejectsUnknownImageHandleWithoutLeakingResources",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; validates live provider unknown-handle failure",
    ),
    mapOf(
        "provider" to "storage",
        "className" to "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest",
        "methodName" to "androidStorageProviderPersistsAndScopesByWorkingDirectory",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "storage",
        "className" to "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest",
        "methodName" to "androidStorageProviderEnforcesQuotaAndPackageIdentityIsolation",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "notifications",
        "className" to "org.autojs.autojs.engine.NodeNotificationsBridgeInstrumentationTest",
        "methodName" to "androidProviderPostsOrReportsNotificationPermissionAndTracksOwnedResource",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; permission-denied path is an explicit pass condition",
    ),
    mapOf(
        "provider" to "sensors",
        "className" to "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
        "methodName" to "androidProviderListsSensorsAndCleansUpLiveSubscriptionWhenAvailable",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; unavailable sensor path is asserted in-test",
    ),
    mapOf(
        "provider" to "sensors",
        "className" to "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
        "methodName" to "androidProviderOnceObservesLiveSensorEventWhenAvailable",
        "interaction" to "none",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when no supported Android sensor is available and JUnit records the reason",
    ),
    mapOf(
        "provider" to "sensors",
        "className" to "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
        "methodName" to "androidProviderSubscribeDrainsEventsAndUnsubscribesWhenAvailable",
        "interaction" to "none",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when no supported Android sensor is available and JUnit records the reason",
    ),
    mapOf(
        "provider" to "sensors",
        "className" to "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
        "methodName" to "androidProviderEnforcesLiveSubscriptionLimitWhenAvailable",
        "interaction" to "none",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when no supported Android sensor is available and JUnit records the reason",
    ),
    mapOf(
        "provider" to "sensors",
        "className" to "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
        "methodName" to "androidProviderReportsUnavailableWhenSensorManagerMissing",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; validates explicit unavailable failure",
    ),
    mapOf(
        "provider" to "accessibility",
        "className" to "org.autojs.autojs.engine.NodeAccessibilityRealDeviceSmokeInstrumentationTest",
        "methodName" to "providerFindsAndClicksControlledUiWhenAccessibilityServiceIsEnabled",
        "interaction" to "pre-run setup",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when the focused accessibility flag or service is not enabled and JUnit records the reason",
    ),
    mapOf(
        "provider" to "accessibility",
        "className" to "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest",
        "methodName" to "androidAccessibilitySelectorV2RejectsUntilProviderIsConnected",
        "interaction" to "none",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when the accessibility provider is already connected and the real smoke covers the live path",
    ),
    mapOf(
        "provider" to "image",
        "className" to "org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest",
        "methodName" to "androidImageExpansionRejectsUntilProviderIsConnected",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; validates fail-closed live image provider behavior",
    ),
    mapOf(
        "provider" to "ui",
        "className" to "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest",
        "methodName" to "jsUiModuleUsesAndroidLiveProviderForButtonAndInputEvents",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "ui",
        "className" to "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest",
        "methodName" to "androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "ui",
        "className" to "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest",
        "methodName" to "androidUiProviderRejectsUnsupportedDescriptorsAndForeignHandles",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "work_manager",
        "className" to "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest",
        "methodName" to "androidWorkManagerProviderSchedulesLiveMetadataAndEnforcesPathPolicy",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "work_manager",
        "className" to "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest",
        "methodName" to "androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "work_manager",
        "className" to "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest",
        "methodName" to "androidWorkManagerLiveProviderCancelPreventsDelayedExecution",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "work_manager",
        "className" to "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest",
        "methodName" to "androidWorkManagerProviderRequiresExplicitCapability",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
)
val nodeJsLiveProviderGateInstrumentationClasses = nodeJsLiveProviderGateCases.map { testCase ->
    "${testCase.getValue("className")}#${testCase.getValue("methodName")}"
}
val nodeJsLiveFetchGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsFetchAndAxiosUseAndroidLiveProviderWhenNetworkExperimentEnabled",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#androidFetchProviderCancelsActiveLiveRequestOnDestroy",
)
val nodeJsLiveWebSocketGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#jsWebSocketUsesAndroidLiveProviderWhenNetworkExperimentEnabled",
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#androidWebSocketProviderClosesActiveLiveConnectionOnDestroy",
)
val nodeJsLiveOcrGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#kotlinOcrLiveProviderRecognizesMlKitImageHandle",
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#androidOcrRejectsUnknownImageHandleWithoutLeakingResources",
)
val nodeJsLiveStorageNotificationsGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest#androidStorageProviderPersistsAndScopesByWorkingDirectory",
    "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest#androidStorageProviderEnforcesQuotaAndPackageIdentityIsolation",
    "org.autojs.autojs.engine.NodeNotificationsBridgeInstrumentationTest#androidProviderPostsOrReportsNotificationPermissionAndTracksOwnedResource",
)
val nodeJsLiveSensorsGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderListsSensorsAndCleansUpLiveSubscriptionWhenAvailable",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderOnceObservesLiveSensorEventWhenAvailable",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderSubscribeDrainsEventsAndUnsubscribesWhenAvailable",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderEnforcesLiveSubscriptionLimitWhenAvailable",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderReportsUnavailableWhenSensorManagerMissing",
)
val nodeJsLiveAccessibilityImageGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeAccessibilityRealDeviceSmokeInstrumentationTest#providerFindsAndClicksControlledUiWhenAccessibilityServiceIsEnabled",
    "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#jsAccessibilitySelectorV2HonorsTimeoutAndCancellation",
    "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#androidAccessibilitySelectorV2RejectsUntilProviderIsConnected",
    "org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest#androidImageExpansionRejectsUntilProviderIsConnected",
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#kotlinOcrLiveProviderRecognizesMlKitImageHandle",
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#androidOcrRejectsUnknownImageHandleWithoutLeakingResources",
)
val nodeJsLiveUiGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#jsUiModuleUsesFakeTransportForLayoutEventsAndPolicy",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#jsUiModuleUsesAndroidLiveProviderForButtonAndInputEvents",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderRejectsUnsupportedDescriptorsAndForeignHandles",
)
val nodeJsLiveWorkManagerGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerProviderSchedulesLiveMetadataAndEnforcesPathPolicy",
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult",
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerLiveProviderCancelPreventsDelayedExecution",
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerProviderRequiresExplicitCapability",
)
val nodeJsLongRunningGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeAndroidRuntimePolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeExecutionModePolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeInteractiveLongRunningInstrumentationTest",
    "org.autojs.autojs.engine.NodeLongRunningStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeRuntimeBudgetPolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeResourceRegistryInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
)
val nodeJsWorkerGateInstrumentationClasses = if (nodeJsWorkerThreadsExperimentalEnabled) {
    listOf(
        "org.autojs.autojs.engine.NodeWorkerThreadsBootstrapInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest",
        "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk",
    )
} else {
    listOf(
        "org.autojs.autojs.engine.NodeWorkerThreadsBootstrapInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest",
    )
}
val nodeJsModernCoreGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest",
    "org.autojs.autojs.engine.NodeSourceMapStackTraceInstrumentationTest",
    "org.autojs.autojs.engine.NodeFeatureFlagRegistryInstrumentationTest",
    "org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest",
    "org.autojs.autojs.engine.NodeModernCoreDifferentialInstrumentationTest",
    "org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest",
    "org.autojs.autojs.engine.NodeStreamMvpInstrumentationTest",
    "org.autojs.autojs.engine.NodeZlibBufferInstrumentationTest",
    "org.autojs.autojs.engine.NodeCryptoExpansionInstrumentationTest",
    "org.autojs.autojs.engine.NodeProcessSweepInstrumentationTest",
    "org.autojs.autojs.engine.NodeOsPrivacyInstrumentationTest",
    "org.autojs.autojs.engine.NodeModuleSafeSubsetInstrumentationTest",
    "org.autojs.autojs.engine.NodeNativeAddonPolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeRawNetworkPolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeVmDesignGateInstrumentationTest",
    "org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest",
)
val nodeJsPhase7ResourceStressGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodePhase7ResourceStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeLongRunningStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#androidFetchProviderCancelsActiveLiveRequestOnDestroy",
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#androidWebSocketProviderClosesActiveLiveConnectionOnDestroy",
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#kotlinOcrLiveProviderRecognizesMlKitImageHandle",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderSubscribeDrainsEventsAndUnsubscribesWhenAvailable",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult",
    "org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest",
)
val nodeJsSecurityGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest",
)
val nodeJs2417SecurityProbeInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase8Node2417SecurityFixProbesStaySafe",
)
val nodeJsTypeScriptGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeTypeScriptInstrumentationTest",
)
val nodeJsDeveloperExperienceGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeTypeScriptInstrumentationTest",
    "org.autojs.autojs.engine.NodeSourceMapStackTraceInstrumentationTest",
    "org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest#nodeTestEmbeddedSubsetRunsSingleRuntimeTap",
    "org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest#nodeTestEmbeddedSubsetReportsFailuresTimeoutsAndLateAsyncActivity",
    "org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest#partialEsmImportsNodeTestEmbeddedSubset",
    "org.autojs.autojs.engine.NodePluginSystemInstrumentationTest#pluginLifecycleResourcesAndDebugReload",
)
val nodeJsRealNpmGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
)
val nodeJsPluginUiGateInstrumentationClasses = (
        listOf(
            "org.autojs.autojs.engine.NodePluginSystemInstrumentationTest",
            "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunV11BridgeEmbeddedNodePackagedApk",
        ) + nodeJsLiveUiGateInstrumentationClasses
        ).distinct()
val nodeJsPhase7GateInstrumentationClasses = (
        nodeJsPackagedEsmGateInstrumentationClasses +
                nodeJsLiveProviderGateInstrumentationClasses +
                nodeJsLongRunningGateInstrumentationClasses +
                nodeJsWorkerGateInstrumentationClasses +
                nodeJsModernCoreGateInstrumentationClasses +
                nodeJsPhase7ResourceStressGateInstrumentationClasses +
                nodeJsSecurityGateInstrumentationClasses +
                nodeJsRealNpmGateInstrumentationClasses +
                nodeJsPluginUiGateInstrumentationClasses
        ).distinct()
val nodeJs2417UpgradeGateInstrumentationClasses = (
        listOf(
            "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest",
            "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
            "org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest",
            "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
            "org.autojs.autojs.engine.NodeModernCoreDifferentialInstrumentationTest",
            "org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest",
            "org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest",
            "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
        ) +
                nodeJs2417SecurityProbeInstrumentationClasses +
                nodeJsPackagedEsmGateInstrumentationClasses +
                nodeJsLiveProviderGateInstrumentationClasses +
                nodeJsLongRunningGateInstrumentationClasses +
                nodeJsWorkerGateInstrumentationClasses +
                nodeJsModernCoreGateInstrumentationClasses
        ).distinct()
val nodeJsPhase8HttpGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeHttpClientFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpsClientFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpStreamSemanticsInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpAgentFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeUndiciFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpNpmCorpusInstrumentationTest",
)
val nodeJsPhase8AdvancedFsGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest",
    "org.autojs.autojs.engine.NodeFdApiInstrumentationTest",
    "org.autojs.autojs.engine.NodeOpendirInstrumentationTest",
    "org.autojs.autojs.engine.NodeFsWatchInstrumentationTest",
    "org.autojs.autojs.engine.NodeSafeFileOperationsInstrumentationTest",
    "org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest",
)
val nodeJsPhase8PerformanceGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeModuleCompileCacheInstrumentationTest",
    "org.autojs.autojs.engine.NodeRuntimePrewarmPoolInstrumentationTest",
    "org.autojs.autojs.engine.NodePerformanceMatrixInstrumentationTest",
)
val nodeJsPhase8SandboxConcurrencyGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeHardenedIsolatedRuntimePocInstrumentationTest",
    "org.autojs.autojs.engine.NodeCapabilityBrokerV2InstrumentationTest",
    "org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeRpcFuzzerInstrumentationTest",
)
val nodeJsPhase9WasmWasiGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeWasmWasiInstrumentationTest",
)
val nodeJsPhase9GateInstrumentationClasses = (
        nodeJsPhase9WasmWasiGateInstrumentationClasses +
                nodeJsRealNpmGateInstrumentationClasses
        ).distinct()
val nodeJsWorkerWasiGateInstrumentationClasses = (
        nodeJsWorkerGateInstrumentationClasses +
                nodeJsPhase9WasmWasiGateInstrumentationClasses
        ).distinct()
val nodeJsPhase10GateInstrumentationClasses = (
        nodeJsPackagedParityGateInstrumentationClasses +
                nodeJsWorkerWasiGateInstrumentationClasses +
                nodeJsDeveloperExperienceGateInstrumentationClasses +
                nodeJsRealNpmGateInstrumentationClasses
        ).distinct()
val nodeJsPhase8SecurityStressGateInstrumentationClasses = (
        nodeJs2417SecurityProbeInstrumentationClasses +
                nodeJsTypeScriptGateInstrumentationClasses +
                listOf(
                    "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
                    "org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest",
                ) +
                nodeJsPhase8HttpGateInstrumentationClasses +
                nodeJsPhase8AdvancedFsGateInstrumentationClasses +
                nodeJsPhase8PerformanceGateInstrumentationClasses +
                nodeJsPhase8SandboxConcurrencyGateInstrumentationClasses
        ).distinct()
val nodeJsPhase8GateInstrumentationClasses = (
        nodeJs2417SecurityProbeInstrumentationClasses +
                nodeJsTypeScriptGateInstrumentationClasses +
                listOf(
                    "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
                    "org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest",
                    "org.autojs.autojs.engine.NodeInspectorDesignGateInstrumentationTest",
                    "org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest",
                ) +
                nodeJsPhase8HttpGateInstrumentationClasses +
                nodeJsPhase8AdvancedFsGateInstrumentationClasses +
                nodeJsPhase8PerformanceGateInstrumentationClasses +
                nodeJsPhase8SandboxConcurrencyGateInstrumentationClasses +
                nodeJsRealNpmGateInstrumentationClasses
        ).distinct()
val nodeJsPhase7HostGateNames = listOf(
    "generateDesktopNodeDifferentialReport",
    "generateNodeModernCoreDifferentialReport",
    "verifyNodeDependencyBundlerFixtures",
)
val nodeJsPhase7SummaryTaskName = "generateNodePhase7Summary"
val nodeJsPhase8HostGateNames = listOf(
    "generateNode2417NativeDiffReport",
    "generateNode2417SecurityProbeReport",
    "generateNode2417UpgradeGateReport",
    "verifyNodeProjectWizard",
    "verifyNodeExamples",
    "verifyAutoJs6NodeTypeDeclarations",
    "verifyNodeCompatibilityProfile",
    "verifyNodeDependencyManagerFixture",
    "verifyNodeDependencyBundlerFixtures",
)
val nodeJsPhase8SummaryTaskName = "generateNodePhase8Summary"
val nodeJsPhase9SummaryTaskName = "generateNodePhase9Summary"
val nodeJsPhase7GateCoverageGroups = listOf(
    "packaged_esm_dynamic" to nodeJsPackagedEsmGateInstrumentationClasses,
    "live_providers" to nodeJsLiveProviderGateInstrumentationClasses,
    "long_running" to nodeJsLongRunningGateInstrumentationClasses,
    "worker" to nodeJsWorkerGateInstrumentationClasses,
    "modern_core" to nodeJsModernCoreGateInstrumentationClasses,
    "dependency_sanitizer" to emptyList(),
    "plugin_ui" to nodeJsPluginUiGateInstrumentationClasses,
    "security_corpus" to nodeJsSecurityGateInstrumentationClasses,
    "stress_subset" to nodeJsPhase7ResourceStressGateInstrumentationClasses,
    "real_npm_corpus" to nodeJsRealNpmGateInstrumentationClasses,
)
val nodeJsPhase8GateCoverageGroups = listOf(
    "node_2417_upgrade_boundary" to nodeJs2417SecurityProbeInstrumentationClasses,
    "typescript_runtime" to nodeJsTypeScriptGateInstrumentationClasses,
    "module_semantics" to listOf(
        "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
        "org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest",
    ),
    "http_compatibility" to nodeJsPhase8HttpGateInstrumentationClasses,
    "advanced_fs" to nodeJsPhase8AdvancedFsGateInstrumentationClasses,
    "performance_startup" to nodeJsPhase8PerformanceGateInstrumentationClasses,
    "inspector_policy" to listOf(
        "org.autojs.autojs.engine.NodeInspectorDesignGateInstrumentationTest",
        "org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest",
    ),
    "sandbox_concurrency" to nodeJsPhase8SandboxConcurrencyGateInstrumentationClasses,
    "real_npm_corpus" to nodeJsRealNpmGateInstrumentationClasses,
    "examples_tooling" to emptyList(),
)
val nodeJsFocusedGateInstrumentationClasses = mapOf(
    nodeJsPhase7SummaryTaskName to nodeJsPhase7GateInstrumentationClasses,
    nodeJsPhase8SummaryTaskName to nodeJsPhase8GateInstrumentationClasses,
    nodeJsPhase9SummaryTaskName to nodeJsPhase9GateInstrumentationClasses,
    nodeJsLiveProviderSummaryTaskName to nodeJsLiveProviderGateInstrumentationClasses,
    "verifyNodePackagedEsmGate" to nodeJsPackagedEsmGateInstrumentationClasses,
    "verifyNodePackagedParityGate" to nodeJsPackagedParityGateInstrumentationClasses,
    "verifyNodeRhinoCompatPackagedSmoke" to nodeJsRhinoCompatPackagedSmokeInstrumentationClasses,
    "verifyNodeRhinoCompatDualEngineDeviceEvidence" to nodeJsRhinoDualEngineDeviceEvidenceInstrumentationClasses,
    "verifyNodeRhinoCompatResourceCleanupSecurity" to nodeJsRhinoCompatResourceCleanupSecurityInstrumentationClasses,
    "verifyNodeLiveProviderGate" to nodeJsLiveProviderGateInstrumentationClasses,
    "verifyNodeLiveFetchGate" to nodeJsLiveFetchGateInstrumentationClasses,
    "verifyNodeLiveWebSocketGate" to nodeJsLiveWebSocketGateInstrumentationClasses,
    "verifyNodeLiveOcrGate" to nodeJsLiveOcrGateInstrumentationClasses,
    "verifyNodeLiveStorageNotificationsGate" to nodeJsLiveStorageNotificationsGateInstrumentationClasses,
    "verifyNodeLiveSensorsGate" to nodeJsLiveSensorsGateInstrumentationClasses,
    "verifyNodeLiveAccessibilityImageGate" to nodeJsLiveAccessibilityImageGateInstrumentationClasses,
    "verifyNodeLiveUiGate" to nodeJsLiveUiGateInstrumentationClasses,
    "verifyNodeLiveWorkManagerGate" to nodeJsLiveWorkManagerGateInstrumentationClasses,
    "verifyNodeLongRunningGate" to nodeJsLongRunningGateInstrumentationClasses,
    "verifyNodeWorkerGate" to nodeJsWorkerGateInstrumentationClasses,
    "verifyNodeModernCoreGate" to nodeJsModernCoreGateInstrumentationClasses,
    "verifyNodePhase7ResourceStressGate" to nodeJsPhase7ResourceStressGateInstrumentationClasses,
    "verifyNodePhase7Gate" to nodeJsPhase7GateInstrumentationClasses,
    "verifyNode2417SecurityProbeGate" to nodeJs2417SecurityProbeInstrumentationClasses,
    "verifyNode2417UpgradeGate" to nodeJs2417UpgradeGateInstrumentationClasses,
    "verifyNodeTypeScriptGate" to nodeJsTypeScriptGateInstrumentationClasses,
    "verifyNodeDeveloperExperienceGate" to nodeJsDeveloperExperienceGateInstrumentationClasses,
    "verifyNodeRealNpmGate" to nodeJsRealNpmGateInstrumentationClasses,
    "verifyNodePhase8SecurityStressGate" to nodeJsPhase8SecurityStressGateInstrumentationClasses,
    "verifyNodePhase8Gate" to nodeJsPhase8GateInstrumentationClasses,
    "verifyNodeWasmWasiGate" to nodeJsPhase9WasmWasiGateInstrumentationClasses,
    "verifyNodeWorkerWasiGate" to nodeJsWorkerWasiGateInstrumentationClasses,
    "verifyNodePhase9Gate" to nodeJsPhase9GateInstrumentationClasses,
    "verifyNodePhase10Gate" to nodeJsPhase10GateInstrumentationClasses,
)
val nodeJsRequestedFocusedGate = taskNames
    .map { rawTaskName -> rawTaskName.substringAfterLast(':') }
    .firstOrNull { taskName -> taskName in nodeJsFocusedGateInstrumentationClasses }
val nodeJsFocusedGateInstrumentationClassArgument = nodeJsRequestedFocusedGate?.let { taskName ->
    nodeJsFocusedGateInstrumentationClasses.getValue(taskName).joinToString(",")
}
val generateNodeLiveProviderSummary = tasks.register(nodeJsLiveProviderSummaryTaskName) {
    group = "verification"
    description = "Writes build/reports/nodejs/live-provider-summary.json from the focused live-provider gate results."

    dependsOn("connectedAppDebugAndroidTest")
    outputs.file(layout.buildDirectory.file("reports/nodejs/live-provider-summary.json"))
    outputs.upToDateWhen { false }

    doFirst {
        if (!nodeJsNetworkExperimentalEnabled) {
            throw GradleException("$name requires -Pautojs.nodejs.network.experimental=true so fetch/websocket live providers are covered.")
        }
    }

    doLast {
        val resultDir = layout.buildDirectory
            .dir("outputs/androidTest-results/connected/debug/flavors/app")
            .get()
            .asFile
        val xmlFiles = resultDir
            .walkTopDown()
            .filter { file -> file.isFile && file.name.startsWith("TEST-") && file.extension == "xml" }
            .sortedBy { file -> file.absolutePath }
            .toList()
        if (xmlFiles.isEmpty()) {
            throw GradleException("No connected Android test XML results found under ${resultDir.absolutePath}.")
        }

        val parserFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val results = linkedMapOf<String, Map<String, Any?>>()
        val devices = linkedSetOf<String>()
        var totalTests = 0
        var totalFailures = 0
        var totalErrors = 0
        var totalSkipped = 0

        fun firstChild(element: org.w3c.dom.Element, name: String): org.w3c.dom.Element? {
            val children = element.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is org.w3c.dom.Element && child.tagName == name) {
                    return child
                }
            }
            return null
        }

        fun childMessage(element: org.w3c.dom.Element): String {
            return element.getAttribute("message")
                .ifBlank { element.textContent.orEmpty().trim() }
                .lineSequence()
                .firstOrNull()
                .orEmpty()
        }

        xmlFiles.forEach { xmlFile ->
            val document = parserFactory.newDocumentBuilder().parse(xmlFile)
            val root = document.documentElement
            totalTests += root.getAttribute("tests").toIntOrNull() ?: 0
            totalFailures += root.getAttribute("failures").toIntOrNull() ?: 0
            totalErrors += root.getAttribute("errors").toIntOrNull() ?: 0
            totalSkipped += root.getAttribute("skipped").toIntOrNull() ?: 0

            val properties = document.getElementsByTagName("property")
            for (index in 0 until properties.length) {
                val property = properties.item(index) as? org.w3c.dom.Element ?: continue
                if (property.getAttribute("name") == "device") {
                    property.getAttribute("value").takeIf { it.isNotBlank() }?.let(devices::add)
                }
            }

            val testCases = document.getElementsByTagName("testcase")
            for (index in 0 until testCases.length) {
                val testCase = testCases.item(index) as? org.w3c.dom.Element ?: continue
                val className = testCase.getAttribute("classname")
                val methodName = testCase.getAttribute("name")
                val failure = firstChild(testCase, "failure")
                val error = firstChild(testCase, "error")
                val skipped = firstChild(testCase, "skipped")
                val status = when {
                    failure != null -> "failed"
                    error != null -> "error"
                    skipped != null -> "skipped"
                    else -> "passed"
                }
                val message = when {
                    failure != null -> childMessage(failure)
                    error != null -> childMessage(error)
                    skipped != null -> childMessage(skipped)
                    else -> ""
                }
                results["$className#$methodName"] = linkedMapOf(
                    "status" to status,
                    "message" to message,
                    "timeSeconds" to testCase.getAttribute("time").toDoubleOrNull(),
                )
            }
        }

        val policyViolations = mutableListOf<String>()
        val caseSummaries = nodeJsLiveProviderGateCases.map { testCase ->
            val key = "${testCase.getValue("className")}#${testCase.getValue("methodName")}"
            val result = results[key]
            val status = result?.get("status")?.toString() ?: "missing"
            val xmlMessage = result?.get("message")?.toString().orEmpty()
            val skipAllowed = testCase.getValue("skipAllowed").toBooleanStrict()
            val message = if (status == "skipped" && xmlMessage.isBlank() && skipAllowed) {
                testCase.getValue("skipPolicy")
            } else {
                xmlMessage
            }
            val skipReasonSource = when {
                status != "skipped" -> null
                xmlMessage.isNotBlank() -> "junit-xml"
                message.isNotBlank() -> "summary-policy"
                else -> null
            }
            when {
                status == "missing" -> policyViolations += "$key did not appear in connected test results."
                status == "failed" || status == "error" -> policyViolations += "$key ended with $status: $message"
                status == "skipped" && !skipAllowed -> policyViolations += "$key skipped unexpectedly: $message"
                status == "skipped" && message.isBlank() -> policyViolations += "$key skipped without an explicit reason."
            }
            linkedMapOf(
                "provider" to testCase.getValue("provider"),
                "className" to testCase.getValue("className"),
                "methodName" to testCase.getValue("methodName"),
                "status" to status,
                "message" to message.ifBlank { null },
                "xmlMessage" to xmlMessage.ifBlank { null },
                "skipReasonSource" to skipReasonSource,
                "timeSeconds" to result?.get("timeSeconds"),
                "interaction" to testCase.getValue("interaction"),
                "fakeProviderAllowed" to false,
                "skipAllowed" to skipAllowed,
                "skipPolicy" to testCase.getValue("skipPolicy"),
            )
        }
        val coveredProviders = caseSummaries
            .filter { testCase -> testCase["status"] in setOf("passed", "skipped") }
            .map { testCase -> testCase.getValue("provider").toString() }
            .toSortedSet()

        val reportFile = layout.buildDirectory
            .file("reports/nodejs/live-provider-summary.json")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()
        val summary = linkedMapOf(
            "schemaVersion" to 1,
            "generatedAt" to Instant.now().toString(),
            "task" to "S7-16 Live Provider Gate",
            "gate" to "verifyNodeLiveProviderGate",
            "deviceProfile" to nodeJsTestDeviceProfile,
            "devices" to devices.toList(),
            "networkExperimentalRequired" to true,
            "networkExperimentalEnabled" to nodeJsNetworkExperimentalEnabled,
            "fakeProvidersAllowed" to false,
            "skipReasonRequired" to true,
            "resultXml" to xmlFiles.map { file -> file.relativeTo(projectDir).invariantSeparatorsPath },
            "totals" to linkedMapOf(
                "tests" to totalTests,
                "failures" to totalFailures,
                "errors" to totalErrors,
                "skipped" to totalSkipped,
                "expectedCases" to nodeJsLiveProviderGateCases.size,
                "coveredProviders" to coveredProviders.toList(),
            ),
            "policyViolations" to policyViolations,
            "cases" to caseSummaries,
        )
        reportFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(summary)) + System.lineSeparator())
        logger.lifecycle("Node live provider summary written to ${reportFile.absolutePath}")

        if (policyViolations.isNotEmpty()) {
            throw GradleException(
                "Node live provider summary found ${policyViolations.size} policy violation(s). " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}
val generateNodePhase7Summary = tasks.register(nodeJsPhase7SummaryTaskName) {
    group = "verification"
    description = "Writes build/reports/nodejs/phase7-summary.json from the focused Phase 7 gate results."

    dependsOn("connectedAppDebugAndroidTest")
    nodeJsPhase7HostGateNames.forEach { taskName -> dependsOn(taskName) }
    outputs.file(layout.buildDirectory.file("reports/nodejs/phase7-summary.json"))
    outputs.upToDateWhen { false }

    doLast {
        val resultDir = layout.buildDirectory
            .dir("outputs/androidTest-results/connected/debug/flavors/app")
            .get()
            .asFile
        val xmlFiles = resultDir
            .walkTopDown()
            .filter { file -> file.isFile && file.name.startsWith("TEST-") && file.extension == "xml" }
            .sortedBy { file -> file.absolutePath }
            .toList()
        if (xmlFiles.isEmpty()) {
            throw GradleException("No connected Android test XML results found under ${resultDir.absolutePath}.")
        }

        val parserFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val devices = linkedSetOf<String>()
        val testResults = linkedMapOf<String, MutableList<Map<String, Any?>>>()
        var totalTests = 0
        var totalFailures = 0
        var totalErrors = 0
        var totalSkipped = 0

        fun firstChild(element: org.w3c.dom.Element, name: String): org.w3c.dom.Element? {
            val children = element.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is org.w3c.dom.Element && child.tagName == name) {
                    return child
                }
            }
            return null
        }

        fun childMessage(element: org.w3c.dom.Element): String {
            return element.getAttribute("message")
                .ifBlank { element.textContent.orEmpty().trim() }
                .lineSequence()
                .firstOrNull()
                .orEmpty()
        }

        xmlFiles.forEach { xmlFile ->
            val document = parserFactory.newDocumentBuilder().parse(xmlFile)
            val root = document.documentElement
            totalTests += root.getAttribute("tests").toIntOrNull() ?: 0
            totalFailures += root.getAttribute("failures").toIntOrNull() ?: 0
            totalErrors += root.getAttribute("errors").toIntOrNull() ?: 0
            totalSkipped += root.getAttribute("skipped").toIntOrNull() ?: 0

            val properties = document.getElementsByTagName("property")
            for (index in 0 until properties.length) {
                val property = properties.item(index) as? org.w3c.dom.Element ?: continue
                if (property.getAttribute("name") == "device") {
                    property.getAttribute("value").takeIf { it.isNotBlank() }?.let(devices::add)
                }
            }

            val testCases = document.getElementsByTagName("testcase")
            for (index in 0 until testCases.length) {
                val testCase = testCases.item(index) as? org.w3c.dom.Element ?: continue
                val className = testCase.getAttribute("classname")
                val methodName = testCase.getAttribute("name")
                val failure = firstChild(testCase, "failure")
                val error = firstChild(testCase, "error")
                val skipped = firstChild(testCase, "skipped")
                val status = when {
                    failure != null -> "failed"
                    error != null -> "error"
                    skipped != null -> "skipped"
                    else -> "passed"
                }
                val message = when {
                    failure != null -> childMessage(failure)
                    error != null -> childMessage(error)
                    skipped != null -> childMessage(skipped)
                    else -> ""
                }
                testResults.getOrPut(className) { mutableListOf() } += linkedMapOf(
                    "selector" to "$className#$methodName",
                    "className" to className,
                    "methodName" to methodName,
                    "status" to status,
                    "message" to message.ifBlank { null },
                    "timeSeconds" to testCase.getAttribute("time").toDoubleOrNull(),
                )
            }
        }

        fun selectorStatus(selector: String): Map<String, Any?> {
            val className = selector.substringBefore("#")
            val methodName = selector.substringAfter("#", "")
            val matches = if (methodName.isBlank()) {
                testResults[className].orEmpty()
            } else {
                testResults[className].orEmpty().filter { result -> result["methodName"] == methodName }
            }
            val status = when {
                matches.isEmpty() -> "missing"
                matches.any { it["status"] == "failed" } -> "failed"
                matches.any { it["status"] == "error" } -> "error"
                matches.all { it["status"] == "skipped" } -> "skipped"
                else -> "passed"
            }
            return linkedMapOf(
                "selector" to selector,
                "status" to status,
                "tests" to matches.size,
                "failed" to matches.count { it["status"] == "failed" },
                "errors" to matches.count { it["status"] == "error" },
                "skipped" to matches.count { it["status"] == "skipped" },
                "messages" to matches.mapNotNull { it["message"] as? String }.filter { it.isNotBlank() }.distinct(),
            )
        }

        val coverageGroups = nodeJsPhase7GateCoverageGroups.map { (area, selectors) ->
            val selectorSummaries = selectors.map(::selectorStatus)
            linkedMapOf(
                "area" to area,
                "kind" to if (area == "dependency_sanitizer") "host" else "instrumentation",
                "expectedSelectors" to selectors.size,
                "coveredSelectors" to selectorSummaries.count { it["status"] in setOf("passed", "skipped") },
                "missingSelectors" to selectorSummaries.filter { it["status"] == "missing" }.map { it["selector"] },
                "failedSelectors" to selectorSummaries.filter { it["status"] in setOf("failed", "error") }.map { it["selector"] },
                "skippedSelectors" to selectorSummaries.filter { it["status"] == "skipped" }.map { it["selector"] },
                "selectors" to selectorSummaries,
            )
        }
        val policyViolations = coverageGroups.flatMap { group ->
            val area = group["area"].toString()
            (group["missingSelectors"] as List<*>).map { selector -> "$area missing $selector" } +
                    (group["failedSelectors"] as List<*>).map { selector -> "$area failed $selector" }
        }

        val reportFile = layout.buildDirectory
            .file("reports/nodejs/phase7-summary.json")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()
        val summary = linkedMapOf(
            "schemaVersion" to 1,
            "generatedAt" to Instant.now().toString(),
            "task" to "S7-49 Phase 7 Functional Gate",
            "gate" to "verifyNodePhase7Gate",
            "deviceProfile" to nodeJsTestDeviceProfile,
            "networkExperimentalEnabled" to nodeJsNetworkExperimentalEnabled,
            "workerThreadsExperimentalEnabled" to nodeJsWorkerThreadsExperimentalEnabled,
            "devices" to devices.toList(),
            "resultXml" to xmlFiles.map { file -> file.relativeTo(projectDir).invariantSeparatorsPath },
            "hostGates" to nodeJsPhase7HostGateNames.map { taskName ->
                linkedMapOf(
                    "task" to taskName,
                    "status" to "passed_if_summary_was_generated",
                )
            },
            "totals" to linkedMapOf(
                "tests" to totalTests,
                "failures" to totalFailures,
                "errors" to totalErrors,
                "skipped" to totalSkipped,
                "expectedInstrumentationSelectors" to nodeJsPhase7GateInstrumentationClasses.size,
            ),
            "coverageGroups" to coverageGroups,
            "policyViolations" to policyViolations,
        )
        reportFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(summary)) + System.lineSeparator())
        logger.lifecycle("Node Phase 7 summary written to ${reportFile.absolutePath}")

        if (policyViolations.isNotEmpty()) {
            throw GradleException(
                "Node Phase 7 summary found ${policyViolations.size} policy violation(s). " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}
val generateNodePhase8Summary = tasks.register(nodeJsPhase8SummaryTaskName) {
    group = "verification"
    description = "Writes build/reports/nodejs/phase8-summary.json from the focused Phase 8 gate results."

    dependsOn("connectedAppDebugAndroidTest")
    nodeJsPhase8HostGateNames.forEach { taskName -> dependsOn(taskName) }
    outputs.file(layout.buildDirectory.file("reports/nodejs/phase8-summary.json"))
    outputs.upToDateWhen { false }

    doLast {
        val resultDir = layout.buildDirectory
            .dir("outputs/androidTest-results/connected/debug/flavors/app")
            .get()
            .asFile
        val xmlFiles = resultDir
            .walkTopDown()
            .filter { file -> file.isFile && file.name.startsWith("TEST-") && file.extension == "xml" }
            .sortedBy { file -> file.absolutePath }
            .toList()
        if (xmlFiles.isEmpty()) {
            throw GradleException("No connected Android test XML results found under ${resultDir.absolutePath}.")
        }

        val parserFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val devices = linkedSetOf<String>()
        val testResults = linkedMapOf<String, MutableList<Map<String, Any?>>>()
        var totalTests = 0
        var totalFailures = 0
        var totalErrors = 0
        var totalSkipped = 0

        fun firstChild(element: org.w3c.dom.Element, name: String): org.w3c.dom.Element? {
            val children = element.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is org.w3c.dom.Element && child.tagName == name) {
                    return child
                }
            }
            return null
        }

        fun childMessage(element: org.w3c.dom.Element): String {
            return element.getAttribute("message")
                .ifBlank { element.textContent.orEmpty().trim() }
                .lineSequence()
                .firstOrNull()
                .orEmpty()
        }

        xmlFiles.forEach { xmlFile ->
            val document = parserFactory.newDocumentBuilder().parse(xmlFile)
            val root = document.documentElement
            totalTests += root.getAttribute("tests").toIntOrNull() ?: 0
            totalFailures += root.getAttribute("failures").toIntOrNull() ?: 0
            totalErrors += root.getAttribute("errors").toIntOrNull() ?: 0
            totalSkipped += root.getAttribute("skipped").toIntOrNull() ?: 0

            val properties = document.getElementsByTagName("property")
            for (index in 0 until properties.length) {
                val property = properties.item(index) as? org.w3c.dom.Element ?: continue
                if (property.getAttribute("name") == "device") {
                    property.getAttribute("value").takeIf { it.isNotBlank() }?.let(devices::add)
                }
            }

            val testCases = document.getElementsByTagName("testcase")
            for (index in 0 until testCases.length) {
                val testCase = testCases.item(index) as? org.w3c.dom.Element ?: continue
                val className = testCase.getAttribute("classname")
                val methodName = testCase.getAttribute("name")
                val failure = firstChild(testCase, "failure")
                val error = firstChild(testCase, "error")
                val skipped = firstChild(testCase, "skipped")
                val status = when {
                    failure != null -> "failed"
                    error != null -> "error"
                    skipped != null -> "skipped"
                    else -> "passed"
                }
                val message = when {
                    failure != null -> childMessage(failure)
                    error != null -> childMessage(error)
                    skipped != null -> childMessage(skipped)
                    else -> ""
                }
                testResults.getOrPut(className) { mutableListOf() } += linkedMapOf(
                    "selector" to "$className#$methodName",
                    "className" to className,
                    "methodName" to methodName,
                    "status" to status,
                    "message" to message.ifBlank { null },
                    "timeSeconds" to testCase.getAttribute("time").toDoubleOrNull(),
                )
            }
        }

        fun selectorStatus(selector: String): Map<String, Any?> {
            val className = selector.substringBefore("#")
            val methodName = selector.substringAfter("#", "")
            val matches = if (methodName.isBlank()) {
                testResults[className].orEmpty()
            } else {
                testResults[className].orEmpty().filter { result -> result["methodName"] == methodName }
            }
            val status = when {
                matches.isEmpty() -> "missing"
                matches.any { it["status"] == "failed" } -> "failed"
                matches.any { it["status"] == "error" } -> "error"
                matches.all { it["status"] == "skipped" } -> "skipped"
                else -> "passed"
            }
            return linkedMapOf(
                "selector" to selector,
                "status" to status,
                "tests" to matches.size,
                "failed" to matches.count { it["status"] == "failed" },
                "errors" to matches.count { it["status"] == "error" },
                "skipped" to matches.count { it["status"] == "skipped" },
                "messages" to matches.mapNotNull { it["message"] as? String }.filter { it.isNotBlank() }.distinct(),
            )
        }

        val coverageGroups = nodeJsPhase8GateCoverageGroups.map { (area, selectors) ->
            val selectorSummaries = selectors.map(::selectorStatus)
            linkedMapOf(
                "area" to area,
                "kind" to if (selectors.isEmpty()) "host" else "instrumentation",
                "expectedSelectors" to selectors.size,
                "coveredSelectors" to selectorSummaries.count { it["status"] in setOf("passed", "skipped") },
                "missingSelectors" to selectorSummaries.filter { it["status"] == "missing" }.map { it["selector"] },
                "failedSelectors" to selectorSummaries.filter { it["status"] in setOf("failed", "error") }.map { it["selector"] },
                "skippedSelectors" to selectorSummaries.filter { it["status"] == "skipped" }.map { it["selector"] },
                "selectors" to selectorSummaries,
            )
        }
        val policyViolations = coverageGroups.flatMap { group ->
            val area = group["area"].toString()
            (group["missingSelectors"] as List<*>).map { selector -> "$area missing $selector" } +
                    (group["failedSelectors"] as List<*>).map { selector -> "$area failed $selector" }
        }

        val reportFile = layout.buildDirectory
            .file("reports/nodejs/phase8-summary.json")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()
        val summary = linkedMapOf(
            "schemaVersion" to 1,
            "generatedAt" to Instant.now().toString(),
            "task" to "S8-50 Phase 8 Summary And Capability Matrix",
            "gate" to "verifyNodePhase8Gate",
            "deviceProfile" to nodeJsTestDeviceProfile,
            "networkExperimentalEnabled" to nodeJsNetworkExperimentalEnabled,
            "workerThreadsExperimentalEnabled" to nodeJsWorkerThreadsExperimentalEnabled,
            "devices" to devices.toList(),
            "resultXml" to xmlFiles.map { file -> file.relativeTo(projectDir).invariantSeparatorsPath },
            "hostGates" to nodeJsPhase8HostGateNames.map { taskName ->
                linkedMapOf(
                    "task" to taskName,
                    "status" to "passed_if_summary_was_generated",
                )
            },
            "knownBlockedInputs" to listOf(
                "node_24_17_runtime_promotion_requires_android_artifacts",
                "startup_snapshot_runtime_path_remains_design_gated",
                "inspector_devtools_and_profiling_remain_debug_design_gated",
                "worker_threads_partial_promotion_remains_blocked_by_native_availability",
            ),
            "totals" to linkedMapOf(
                "tests" to totalTests,
                "failures" to totalFailures,
                "errors" to totalErrors,
                "skipped" to totalSkipped,
                "expectedInstrumentationSelectors" to nodeJsPhase8GateInstrumentationClasses.size,
            ),
            "coverageGroups" to coverageGroups,
            "policyViolations" to policyViolations,
        )
        reportFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(summary)) + System.lineSeparator())
        logger.lifecycle("Node Phase 8 summary written to ${reportFile.absolutePath}")

        if (policyViolations.isNotEmpty()) {
            throw GradleException(
                "Node Phase 8 summary found ${policyViolations.size} policy violation(s). " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}
val nodeJsTestDeviceProfileClasses = when (nodeJsTestDeviceProfile) {
    "sony" -> listOf(
        "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest",
        "org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest",
        "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest",
        "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest",
        "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
        "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
        "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest",
        "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
    )
    "emulator" -> listOf(
        "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest",
        "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
        "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
        "org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest",
        "org.autojs.autojs.engine.NodeScopedFsFuzzerInstrumentationTest",
    )
    "smoke" -> listOf(
        "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest",
        "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest",
        "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunToastBridgeEmbeddedNodePackagedApk",
    )
    "accessibility" -> listOf(
        "org.autojs.autojs.engine.NodeAccessibilityRealDeviceSmokeInstrumentationTest",
    )
    "screen_capture" -> listOf(
        "org.autojs.autojs.engine.NodeScreenCaptureRealDeviceSmokeInstrumentationTest",
    )
    "v1_1_alpha" -> nodeJsV11AlphaInstrumentationClasses
    "v1_1_beta" -> nodeJsV11BetaInstrumentationClasses
    "full" -> listOf(
        "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest",
        "org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest",
        "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest",
        "org.autojs.autojs.engine.NodeBridgeRpcFuzzerInstrumentationTest",
        "org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest",
        "org.autojs.autojs.engine.NodeScopedFsFuzzerInstrumentationTest",
        "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest",
        "org.autojs.autojs.engine.NodeNativeAddonPolicyInstrumentationTest",
        "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
        "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
        "org.autojs.autojs.engine.NodeModernCoreDifferentialInstrumentationTest",
        "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
    ) + if (nodeJsWorkerThreadsExperimentalEnabled) {
        listOf("org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest")
    } else {
        emptyList()
    } + listOf("org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest")
    else -> emptyList()
}

extensions.extraProperties.set(
    nodeJsInstrumentationExtraName("requestedFocusedGate"),
    nodeJsRequestedFocusedGate,
)
extensions.extraProperties.set(
    nodeJsInstrumentationExtraName("focusedGateInstrumentationClassArgument"),
    nodeJsFocusedGateInstrumentationClassArgument,
)
extensions.extraProperties.set(
    nodeJsInstrumentationExtraName("testDeviceProfileClasses"),
    nodeJsTestDeviceProfileClasses,
)
