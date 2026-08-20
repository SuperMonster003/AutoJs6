declare namespace AutoJs6Node {
  export interface FeatureFlagDescriptor {
    readonly id: string;
    readonly diagnosticName: string;
    readonly gradleProperty: string | null;
    readonly defaultEnabled: boolean;
    readonly enabled: boolean;
    readonly buildTimeOverrideAllowed: boolean;
    readonly projectJsonOverrideAllowed: boolean;
    readonly packagedApkOverrideAllowed: boolean;
    readonly buildOnly: boolean;
    readonly debugOnly: boolean;
    readonly projectAllowed: boolean;
    readonly packagedAllowed: boolean;
    readonly releaseForbidden: boolean;
    readonly securityLevel: "low" | "medium" | "high" | "critical" | string;
    readonly status: "stable" | "partial" | "disabled" | "unsupported" | string;
  }

  export interface EsmLoaderProfile {
    readonly status: "partial_default" | "disabled_by_request" | string;
    readonly defaultEnabled: boolean;
    readonly requestEnabled: boolean;
    readonly dynamicImportDefaultEnabled: boolean;
    readonly dynamicImportEnabled: boolean;
    readonly implementation: "autojs6_managed_transform" | string;
    readonly mjsEntry: "partial" | "unsupported" | string;
    readonly packageTypeModuleEntry: "partial" | "unsupported" | string;
    readonly topLevelAwait: "partial" | "unsupported" | string;
    readonly staticImport: "local_scoped_partial" | string;
    readonly dynamicImport: "local_scoped_partial" | string;
    readonly packageExports: "partial" | string;
    readonly packageImports: "partial" | string;
    readonly jsonImportAttributes: "partial" | string;
    readonly importMeta: "scoped_partial" | string;
    readonly cjsInterop: "sync_esm_only" | string;
    readonly moduleSources: "partial" | string;
    readonly packagedBehavior: "focused_smoke_partial" | string;
    readonly sourceMaps: "sourceURL_stack_only" | string;
    readonly loaderHooks: "denied" | string;
    readonly rawNodeModuleLoader: "denied" | string;
    readonly networkImports: "denied" | string;
    readonly fileUrlImports: "denied" | string;
    readonly dataUrlImports: "inline_js_json_partial" | "denied" | string;
    readonly absolutePathImports: "denied" | string;
    readonly workingDirectoryEscape: "denied" | string;
    readonly disabledBuiltinImports: "denied" | string;
    readonly encryptedGraphParity: "not_promoted" | string;
    readonly pendingEvaluationCleanup: "not_proven" | string;
    readonly realEsmCorpus: "partial_required" | string;
    readonly rawNodeLoader: boolean;
    readonly customConditions: boolean;
  }

  export interface PackageManagerProfile {
    readonly status: "android_substrate_partial" | "disabled_by_default" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly hostPackageManager: "available" | string;
    readonly androidStore: "local_app_private_partial" | string;
    readonly androidRuntimeInstall: "local_unpacked_only" | string;
    readonly npmCli: "denied" | "debug_only" | "partial" | string;
    readonly npmFacade: "guarded_package_manager_alias" | string;
    readonly requireNpm: "guarded_package_manager_alias" | string;
    readonly npmInstallEquivalent: "host_managed_plus_android_store_substrate" | string;
    readonly registryDownload: "deferred" | "denied" | "partial" | string;
    readonly tarballInstall: "host_side_only" | "partial" | string;
    readonly lockfile: "autojs6_lock_host_managed" | string;
    readonly integrity: "sha512_required" | string;
    readonly lifecycleScripts: "denied" | "debug_only" | string;
    readonly debugLifecycleScripts: "not_promoted" | "debug_only" | string;
    readonly binLinks: "denied" | "partial" | string;
    readonly symlinks: "denied" | string;
    readonly nativeBuild: "denied" | string;
    readonly nativePayloads: "denied" | "signed_packaged_only" | string;
    readonly postinstallDownload: "denied" | string;
    readonly nodeModulesMutation: "transactional_store_only" | string;
    readonly transaction: "locked_cancellable_atomic" | string;
    readonly sourcePolicy: "guarded_local_unpacked_mutation" | "decision_only_no_install_authority" | string;
    readonly sourceAuthority: "local_unpacked_host_managed_only" | string;
    readonly installAuthority: "package_manager_mutate_explicit_local_unpacked" | "internal_substrate_only" | string;
    readonly allowedSources: readonly string[];
    readonly deniedSources: readonly string[];
    readonly planOperations: readonly string[];
    readonly mutationOperations: readonly string[];
    readonly npmFacadeOperations: readonly string[];
    readonly mutationCapability: "package_manager.mutate" | string;
    readonly mutationConfirmation: "allowMutation_required" | string;
    readonly operations: readonly string[];
    readonly packageKinds: readonly string[];
    readonly jsBridge: "guarded_mutation_partial" | "read_maintenance_partial" | "diagnostic_only" | "not_exposed" | "partial" | string;
    readonly requirePackageManager: "guarded_mutation_partial" | "read_maintenance_partial" | "diagnostic_only" | "denied" | "partial" | string;
    readonly diagnosticFacade: "available" | string;
    readonly packagedBehavior: "not_promoted" | string;
    readonly androidNetwork: "denied" | "partial" | string;
    readonly rawExecutables: boolean;
    readonly rawRegistryAccess: boolean;
  }

  export interface StdlibProfile {
    readonly status: "safe_subset_partial" | "desktop_opt_in" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly conformanceSet: "node24_safe_stdlib_subset" | string;
    readonly moduleStatus: Readonly<Record<string, "partial" | "limited_network_gated" | "disabled" | string>>;
    readonly conformanceModules: readonly string[];
    readonly cjsAliases: "bare_and_node_prefix" | string;
    readonly esmImports: "partial" | string;
    readonly shadowing: "builtin_precedence" | string;
    readonly processGetBuiltinModule: "controlled_partial" | "denied" | string;
    readonly androidDifferences: "documented_stable" | string;
    readonly androidUnsupported: readonly string[];
    readonly resourceLimits: "bounded_callbacks_streams_crypto_zlib" | string;
    readonly npmCorpus: "real_npm_phase13_60_fixture_corpus" | string;
    readonly desktopDifferential: "modern_core_s7_36_partial" | string;
    readonly packagedBehavior: "existing_v1_1_smokes_partial" | string;
    readonly rawDesktopParity: boolean;
    readonly rawNativeHandles: boolean;
    readonly unrestrictedNetwork: boolean;
    readonly broadTerminal: boolean;
  }

  export interface ProcessParityProfile {
    readonly status: "safe_process_subset_partial" | "desktop_opt_in" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly argv: "entry_and_project_runner_partial" | string;
    readonly env: "controlled_string_map" | string;
    readonly envMutation: "script_local_string_coercing_mutable" | string;
    readonly envSources: "request_map_plus_safe_defaults" | string;
    readonly envDefaultKeys: readonly string[];
    readonly envFilteredKeys: readonly string[];
    readonly cwd: "scoped_working_directory" | string;
    readonly chdir: "scoped_relative_directory_only" | string;
    readonly exit: "lifecycle_mapped_process_exit" | string;
    readonly signals: "denied_no_android_signal_delivery" | string;
    readonly versions: "partial_node_v8_available" | string;
    readonly resourceUsage: "partial_runtime_snapshot" | string;
    readonly timing: "partial_uptime_hrtime_cpu_memory" | string;
    readonly permission: "autojs6_readonly_capability_query" | string;
    readonly report: "getReport_only_write_denied" | string;
    readonly stdio: "bounded_non_tty" | string;
    readonly moduleAliases: readonly string[];
    readonly androidDifferences: "documented_stable" | string;
    readonly androidUnsupported: readonly string[];
    readonly packagedBehavior: "existing_project_runner_smokes_partial" | string;
    readonly rawDesktopParity: boolean;
    readonly rawEnvInheritance: boolean;
    readonly rawSignals: boolean;
    readonly rawProcessHandles: boolean;
    readonly nativeBindings: boolean;
  }

  export interface PackagedCapabilityProfile {
    readonly status: "packaged_metadata_partial" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly metadataSchema: "autojs6-packaged-capability-metadata-v1" | string;
    readonly metadataSources: readonly string[];
    readonly permissionManifest: "node_permissions_enforced" | string;
    readonly androidManifestSynthesis: "bridge_android_permissions_only" | string;
    readonly profileMetadata: "diagnostic_only" | string;
    readonly nodeBuiltins: "declared_metadata_only_no_authority" | string;
    readonly nativeAssets: "declared_metadata_only_denied" | string;
    readonly filesystemRoots: "declared_metadata_only_scoped_default" | string;
    readonly network: "network_capability_maps_android_internet" | string;
    readonly longRunning: "execution_mode_metadata_partial" | string;
    readonly undeclaredCapabilities: "rejected_before_dispatch" | string;
    readonly manifestConsistency: "project_package_metadata_verified" | string;
    readonly packagedRuntimeDescriptor: "version_vector_required" | string;
    readonly packagedLaunchDescriptor: "scoped_internal_metadata" | string;
    readonly rawProfilePromotion: boolean;
    readonly rawBuiltinPromotion: boolean;
    readonly nativeAssetLoading: boolean;
    readonly expandedFilesystemRoots: boolean;
    readonly unreviewedAndroidPermissions: boolean;
  }

  export interface PackagedCapabilityMetadataNetwork {
    readonly requested: boolean;
    readonly declaredCapability: boolean;
    readonly androidPermissions: readonly string[];
  }

  export interface PackagedCapabilityMetadata {
    readonly schema: "autojs6-packaged-capability-metadata-v1" | string;
    readonly status: "partial" | "absent" | string;
    readonly profile: string;
    readonly requestedProfiles: readonly string[];
    readonly builtins: readonly string[];
    readonly nativeAssets: readonly string[];
    readonly filesystemRoots: readonly string[];
    readonly executionMode: string;
    readonly longRunning: boolean;
    readonly network: PackagedCapabilityMetadataNetwork;
    readonly metadataOnly: boolean;
    readonly grantsAuthority: boolean;
    readonly sources: readonly string[];
    readonly warnings: readonly string[];
  }

  export interface PluginSystemProfile {
    readonly status: "js_only_local_manager_partial" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly manager: "js_only_local_manager_v1" | string;
    readonly pluginRoot: "project_local_plugins_directory" | string;
    readonly installMode: "already_present_current_execution" | string;
    readonly operations: readonly string[];
    readonly permissionModel: "exact_plugin_id_plus_requested_permissions" | string;
    readonly hostManifestEnforcement: "required_before_install_load_start" | string;
    readonly overDeclaredPluginCapabilities: "rejected_before_code_execution" | string;
    readonly pluginNamespacePermissions: "rejected_in_manifest" | string;
    readonly androidProviderPlugins: "deferred" | "denied" | "partial" | string;
    readonly nativePlugins: "denied" | "deferred" | "partial" | string;
    readonly remoteMarketplace: "deferred" | "denied" | "partial" | string;
    readonly signedArchives: "deferred" | "required" | string;
    readonly updateChannel: "deferred" | "partial" | string;
    readonly packagedDistribution: "local_checked_files_only" | "partial" | string;
    readonly resourceOwnership: "plugin_lifecycle_context_partial" | string;
    readonly debugReload: "debug_only" | string;
    readonly downloadInstaller: boolean;
    readonly nativeAddonLoading: boolean;
    readonly androidPluginApkBridge: boolean;
    readonly marketplaceInstall: boolean;
    readonly persistedGlobalState: boolean;
    readonly grantsHostCapabilities: boolean;
  }

  export interface PackageIntegrityProfile {
    readonly status: "lock_integrity_policy_partial" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly policySchema: "autojs6-node-package-lock-integrity-policy-v1" | string;
    readonly lockfile: "autojs6-lock.json" | string;
    readonly androidRecordSchema: "autojs6-node-android-package-record-v1" | string;
    readonly hashAlgorithm: "sha512" | string;
    readonly hashFormat: "ssri_sha512_base64" | string;
    readonly androidPackageManager: "transactional_local_store" | string;
    readonly pluginPackages: "local_checked_files_signed_archives_deferred" | string;
    readonly nativeAddons: "denied_until_signed_packaged_artifacts" | string;
    readonly signatureScheme: "autojs6-package-signature-v1" | string;
    readonly signaturePolicy: "required_before_plugin_native_promotion" | string;
    readonly rollback: "staging_restore_previous_on_failure" | string;
    readonly cacheCleanup: "staging_prune_and_reinstall_required" | string;
    readonly corruptionRecovery: "verify_mismatch_reinstall_required" | string;
    readonly lockfileAuthority: "host_managed_required" | string;
    readonly recoveryActions: readonly string[];
    readonly subjects: readonly string[];
    readonly unsafePackageExecution: boolean;
    readonly rawRegistryInstall: boolean;
    readonly unsignedPluginInstall: boolean;
    readonly unsignedNativeAddonLoad: boolean;
    readonly lifecycleScripts: boolean;
    readonly postinstallDownloads: boolean;
    readonly unverifiedCacheReuse: boolean;
  }

  export interface BridgePermissionsReport {
    readonly version: number;
    readonly enforced: boolean;
    readonly permissions: readonly string[];
    readonly normalizedPermissions: readonly string[];
    readonly sources: readonly string[];
    readonly knownCapabilities: readonly string[];
    readonly unknownPermissions: readonly string[];
    readonly androidPermissions: readonly string[];
    readonly serviceRequired: boolean;
    readonly packagedMetadata: PackagedCapabilityMetadata;
    readonly warnings: readonly string[];
  }

  export interface ProfileSelectionProfileDescriptor {
    readonly id: "safe_default" | "pro_compat_opt_in" | "desktop_compat_opt_in" | "debug_unsafe_lab" | string;
    readonly requested: boolean;
    readonly effective: boolean;
    readonly available: boolean;
    readonly status: "effective" | "diagnostic_only" | string;
    readonly description: string;
  }

  export interface ProfileSelectionHighRiskCapability {
    readonly id: string;
    readonly source: "permission_manifest" | "feature_flag" | string;
    readonly declared: boolean;
    readonly effectiveProfile: "safe_default" | string;
    readonly requiredProfile: "safe_default" | "pro_compat_opt_in" | "desktop_compat_opt_in" | "debug_unsafe_lab" | string;
    readonly profilePromoted?: boolean;
    readonly requiredAndroidPermissions?: readonly string[];
    readonly enabledByDefault?: boolean;
    readonly status?: string;
    readonly securityLevel?: "high" | "critical" | string;
    readonly releaseForbidden?: boolean;
  }

  export interface ProfileSelectionDeclarationExamples {
    readonly nodePermissions: readonly string[];
    readonly projectJson: {
      readonly node: {
        readonly permissions: readonly string[];
      };
    };
    readonly packageJson: {
      readonly autojs6: {
        readonly node: {
          readonly permissions: readonly string[];
        };
      };
    };
  }

  export interface ProfileSelectionFailureHint {
    readonly schema: "autojs6-node-profile-selection-failure-hint-v1" | string;
    readonly module: string;
    readonly method: string;
    readonly effectiveProfile: "safe_default" | string;
    readonly requiredProfiles: readonly string[];
    readonly requiredNodePermissions: readonly string[];
    readonly requiredAndroidPermissions: readonly string[];
    readonly profileSelectionRequired: boolean;
    readonly message: string;
    readonly declarationHint: string;
    readonly declarationExamples: ProfileSelectionDeclarationExamples;
  }

  export interface ProfileSelectionFailureHints {
    readonly capabilityNotDeclared: ProfileSelectionFailureHint;
    readonly profilePromotionBlocked: string;
  }

  export interface ProfileSelectionDiagnostics {
    readonly schema: "autojs6-node-profile-selection-diagnostics-v1" | string;
    readonly status: "diagnostic_only" | string;
    readonly source: string;
    readonly defaultProfile: "safe_default" | string;
    readonly selectedProfile: "safe_default" | string;
    readonly effectiveProfile: "safe_default" | string;
    readonly declaredProfile: string;
    readonly requestedProfiles: readonly string[];
    readonly selectionMode: "safe_default_effective_metadata_only" | string;
    readonly profilePromotion: boolean;
    readonly userSelectable: boolean;
    readonly projectSettingsSupported: boolean;
    readonly packagedSettingsSupported: boolean;
    readonly debugProfileReleaseDenied: boolean;
    readonly metadataOnly: boolean;
    readonly grantsAuthority: boolean;
    readonly declaredNodePermissions: readonly string[];
    readonly declaredAndroidPermissions: readonly string[];
    readonly profileCatalog: readonly ProfileSelectionProfileDescriptor[];
    readonly highRiskCapabilities: readonly ProfileSelectionHighRiskCapability[];
    readonly blockedProfilePromotions: readonly string[];
    readonly failureHints: ProfileSelectionFailureHints;
    readonly warnings: readonly string[];
  }

  export interface ProfileRollbackState {
    readonly id: "safe_default" | "pro_compat_opt_in" | "desktop_compat_opt_in" | "debug_unsafe_lab" | string;
    readonly property: string;
    readonly enabled: boolean;
    readonly rollbackAction: string;
    readonly fallbackProfile: "safe_default" | string;
    readonly packagedAction: string;
  }

  export interface CapabilityRollbackState {
    readonly id: string;
    readonly profile: "safe_default" | "pro_compat_opt_in" | "desktop_compat_opt_in" | "debug_unsafe_lab" | "pro_or_desktop" | string;
    readonly property: string;
    readonly enabled: boolean;
    readonly rollbackAction: string;
    readonly fallbackProfile: "safe_default" | string;
  }

  export interface ProfileRollbackTrigger {
    readonly id: "native_crash" | "bridge_provider_crash" | "stress_regression" | "security_corpus_regression" | "packaged_apk_regression" | string;
    readonly evidence: string;
    readonly fallbackProfile: "safe_default" | string;
    readonly action: string;
  }

  export interface ProfileRollbackPackagedDowngrade {
    readonly strategy: "publish_safe_profile_patch_or_remove_promoted_metadata" | string;
    readonly authority: "metadata_only_no_authority" | string;
    readonly publishSafeProfilePatch: boolean;
    readonly remoteKillSwitchAvailable: boolean;
    readonly remoteKillSwitchPolicy: "rejected_v1_1_publish_safe_profile_patch" | string;
    readonly requiresUserDisclosure: boolean;
    readonly actions: readonly string[];
  }

  export interface ProfileRollbackReleasePolicy {
    readonly releaseBuildForbidsDebugUnsafeLab: boolean;
    readonly releaseBuildHasFeatureGates: boolean;
    readonly stableFeaturesIncluded: boolean;
    readonly safeProfileReleaseFallback: boolean;
    readonly runtimePoliciesRemainEnforced: boolean;
  }

  export interface ProfileRollbackPolicy {
    readonly schema: "autojs6-node-profile-rollback-policy-v1" | string;
    readonly status: "stable_features" | string;
    readonly defaultProfile: "safe_default" | string;
    readonly effectiveProfile: "safe_default" | string;
    readonly rollbackTargetProfile: "safe_default" | string;
    readonly crashFallbackProfile: "safe_default" | string;
    readonly stressFallbackProfile: "safe_default" | string;
    readonly profilePromotion: boolean;
    readonly userSelectable: boolean;
    readonly metadataOnly: boolean;
    readonly grantsAuthority: boolean;
    readonly safeDefaultAlwaysAvailable: boolean;
    readonly independentProfileDisable: boolean;
    readonly profileKillSwitches: Readonly<Record<string, string>>;
    readonly capabilityKillSwitches: Readonly<Record<string, string>>;
    readonly profileStates: readonly ProfileRollbackState[];
    readonly capabilityStates: readonly CapabilityRollbackState[];
    readonly rollbackTriggers: readonly ProfileRollbackTrigger[];
    readonly packagedDowngrade: ProfileRollbackPackagedDowngrade;
    readonly releasePolicy: ProfileRollbackReleasePolicy;
    readonly pending: readonly string[];
  }

  export interface PrivacyDisclosureSurface {
    readonly id: string;
    readonly capability: string;
    readonly profile: "safe_default" | "pro_compat_opt_in" | "desktop_compat_opt_in" | "debug_unsafe_lab" | string;
    readonly androidPermissions: readonly string[];
    readonly requiresUserPrompt: boolean;
    readonly requiresForegroundDisclosure: boolean;
    readonly requiresNotification: boolean;
    readonly requiresSettingsIntent: boolean;
    readonly promptChannel: string;
    readonly disclosureChannel: string;
    readonly settingsIntent: string | null;
    readonly packagedBehavior: "deny_without_reviewed_disclosure_manifest" | string;
    readonly runtimeStatus: "fail_closed_until_disclosure" | string;
    readonly promotionBlockedUntil: "user_prompt_and_foreground_disclosure_implemented" | string;
    readonly implicitGrantAllowed: boolean;
    readonly authority: "metadata_only_no_authority" | string;
  }

  export interface PrivacyDisclosureChannel {
    readonly id: string;
    readonly status: "platform_controlled" | "required_before_promotion" | "required_before_packaged_promotion" | "diagnostic_only" | string;
    readonly requiredFor: readonly string[];
    readonly grantsAuthority: boolean;
  }

  export interface PrivacyDisclosurePackagedPolicy {
    readonly schema: "autojs6-node-packaged-disclosure-manifest-v1" | string;
    readonly status: "policy_partial" | string;
    readonly metadataOnly: boolean;
    readonly grantsAuthority: boolean;
    readonly implicitHighPrivilegeDenied: boolean;
    readonly requiresDisclosureManifest: boolean;
    readonly reviewedManifestRequiredForPromotion: boolean;
    readonly denyUndeclaredHighRiskSurfaces: boolean;
    readonly permissionSynthesisRequiresReviewedDisclosure: boolean;
    readonly defaultAction: "deny_without_reviewed_disclosure_manifest" | string;
    readonly manifestKeys: readonly string[];
  }

  export interface PrivacyDisclosureDoctorPrivacy {
    readonly scriptContentsIncluded: boolean;
    readonly environmentValuesIncluded: boolean;
    readonly pathsRedacted: boolean;
    readonly surfaceMetadataOnly: boolean;
  }

  export interface PrivacyDisclosureAutomationImplications {
    readonly automaticCrashCircuitBreaker: "future_metadata_only_safe_profile_fallback" | string;
    readonly crashEvidencePrivacy: "metadata_only_no_script_contents" | string;
    readonly remotePackagedKillSwitch: "rejected_v1_1_publish_safe_profile_patch" | string;
    readonly requiresDisclosureBeforeReenable: boolean;
    readonly grantsAuthority: boolean;
  }

  export interface PrivacyDisclosurePolicy {
    readonly schema: "autojs6-node-privacy-disclosure-policy-v1" | string;
    readonly status: "policy_partial" | string;
    readonly defaultProfile: "safe_default" | string;
    readonly effectiveProfile: "safe_default" | string;
    readonly metadataOnly: boolean;
    readonly grantsAuthority: boolean;
    readonly implicitHighPrivilegeDenied: boolean;
    readonly packagedImplicitHighPrivilegeDenied: boolean;
    readonly disclosureBlocksPromotion: boolean;
    readonly foregroundDisclosureRequiredForPromotedSensitiveSurfaces: boolean;
    readonly userPromptRequiredForPromotedSensitiveSurfaces: boolean;
    readonly surfaces: readonly PrivacyDisclosureSurface[];
    readonly channels: readonly PrivacyDisclosureChannel[];
    readonly packagedPolicy: PrivacyDisclosurePackagedPolicy;
    readonly doctorPrivacy: PrivacyDisclosureDoctorPrivacy;
    readonly automationImplications: PrivacyDisclosureAutomationImplications;
    readonly pending: readonly string[];
  }

  export interface AutoJsBridgeCapabilityNotDeclaredError extends Error {
    readonly code: "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED";
    readonly module?: string;
    readonly method?: string;
    readonly missingCapabilities: readonly string[];
    readonly requiredProfiles: readonly string[];
    readonly requiredNodePermissions: readonly string[];
    readonly requiredAndroidPermissions: readonly string[];
    readonly profileSelectionHint: ProfileSelectionFailureHint;
  }

  export interface FilesystemAdvancedApis {
    readonly streams: "partial" | "unsupported" | string;
    readonly fileHandle: "partial" | "unsupported" | string;
    readonly fd: "partial" | "unsupported" | string;
    readonly opendir: "partial" | "unsupported" | string;
    readonly watch: "partial" | "unsupported" | string;
    readonly realpath: "partial" | "unsupported" | string;
    readonly recursiveWatch: "unsupported" | "partial" | string;
    readonly closeOnDestroy: "execution_owned" | "unsupported" | string;
    readonly packagedBehavior: "partial" | "unsupported" | string;
    readonly rawFd: boolean;
  }

  export interface FilesystemProfileLimits {
    readonly watcherLimit: number;
    readonly watchEventLimit: number;
    readonly watchEventWindowMs: number;
  }

  export interface FilesystemProfile {
    readonly mode: "scoped_working_directory" | string;
    readonly safeProfileScoped: boolean;
    readonly additionalRoots: readonly string[];
    readonly userAuthorizedRoots: boolean;
    readonly safRoots: boolean;
    readonly appPrivateRoots: boolean;
    readonly androidSharedStorage: boolean;
    readonly advancedApis: FilesystemAdvancedApis;
    readonly limits: FilesystemProfileLimits;
  }

  export interface WorkerThreadsFsProfile {
    readonly read: "workingDirectory" | "denied" | string;
    readonly write: "workingDirectory" | "denied" | string;
  }

  export interface WorkerThreadsWorkerProfile {
    readonly fs: WorkerThreadsFsProfile;
    readonly network: "denied" | string;
    readonly autoJsBridge: "denied" | string;
    readonly shell: "denied" | string;
    readonly javaInterop: "denied" | string;
    readonly inspector: "denied" | string;
  }

  export interface WorkerThreadsPoolPolicy {
    readonly minWorkers: number;
    readonly maxWorkers: number;
    readonly idleTimeoutMs: number;
    readonly taskTimeoutMs: number;
    readonly queueLimit: number;
  }

  export interface WorkerThreadsResourceLimits {
    readonly maxOldGenerationSizeMb: number;
    readonly maxYoungGenerationSizeMb: number;
    readonly codeRangeSizeMb: number;
    readonly stackSizeMb: number;
  }

  export interface WorkerThreadsPolicy {
    readonly maxWorkers: number;
    readonly startupTimeoutMs: number;
    readonly maxMessageBytes: number;
    readonly maxQueuedMessages: number;
    readonly allowedBuiltins: readonly string[];
    readonly workerProfile: WorkerThreadsWorkerProfile;
    readonly workerPool: WorkerThreadsPoolPolicy;
    readonly resourceLimits: WorkerThreadsResourceLimits;
  }

  export interface WorkerThreadsProfile {
    readonly status: "stable" | "disabled_by_request" | string;
    readonly defaultEnabled: boolean;
    readonly requestEnabled: boolean;
    readonly nativeAvailability: "available" | string;
    readonly messageChannel: "partial" | "unsupported" | string;
    readonly transferList: "partial" | "unsupported" | string;
    readonly workerPool: "partial" | "unsupported" | string;
    readonly bridgeModules: "denied" | string;
    readonly nestedWorkers: boolean;
    readonly rawNativeHandles: boolean;
    readonly packagedBehavior: "native_available_only" | "partial" | string;
    readonly policy: WorkerThreadsPolicy;
  }

  export interface ProcessWorkerReplacementProfile {
    readonly status: "decision_partial" | "ready" | string;
    readonly api: "not_introduced" | "autojs6_process_worker" | string;
    readonly facade: "not_introduced" | string;
    readonly preferredPrimitive: "worker_threads.WorkerPool" | string;
    readonly workerThreads: "stable_enabled" | string;
    readonly childProcess: "stable_enabled" | string;
    readonly shellBridge: "separate_scoped_command_bridge" | string;
    readonly executionMode: "worker_computation_reserved" | string;
    readonly secondExecutionSlot: "closed_by_p13_06" | string;
    readonly scopedScriptPath: "workingDirectory" | string;
    readonly messageSizeBytes: number;
    readonly maxQueuedMessages: number;
    readonly cleanup: "worker_pool_close_or_execution_destroy" | string;
    readonly packagedBehavior: "stable" | string;
    readonly bridgeModules: "denied" | string;
    readonly nestedWorkers: boolean;
    readonly rawProcessHandles: boolean;
  }

  export interface VmProfile {
    readonly status: "native_available" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly module: "denied" | "partial" | "native_available" | string;
    readonly script: "deferred" | "partial" | "native_available" | string;
    readonly context: "deferred" | "partial" | "native_available" | string;
    readonly timeout: "required_before_enablement" | "native_run_options" | string;
    readonly compileFunction: "denied" | "native_available" | string;
    readonly syntheticModule: "denied" | "native_available" | string;
    readonly sourceTextModule: "denied" | "native_available" | string;
    readonly dynamicImportHooks: "denied" | "native_available" | string;
    readonly bridgeModules: "denied" | string;
    readonly processAccess: "denied_for_future_contexts" | "not_security_boundary" | string;
    readonly scopedFs: "must_preserve" | string;
    readonly privateBindings: "denied" | string;
    readonly hostObjects: "denied" | "not_injected_by_autojs6" | string;
    readonly memoryLimits: "not_defined" | "native_node_defaults" | string;
    readonly packagedBehavior: "not_promoted" | "native_builtin" | string;
    readonly rawGlobalAccess: boolean;
  }

  export interface InspectorProfile {
    readonly status: "disabled_by_default" | "debug_opt_in" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly module: "denied" | "partial" | string;
    readonly devtools: "deferred" | "partial" | string;
    readonly inspectorProtocol: "deferred" | "partial" | string;
    readonly adbForwarding: "required_before_enablement" | string;
    readonly explicitUserAction: "required_before_enablement" | string;
    readonly releaseBuild: "denied" | string;
    readonly packagedBehavior: "not_promoted" | string;
    readonly portBinding: "denied" | "loopback_only" | string;
    readonly remoteConnections: "denied" | string;
    readonly bridgeModules: "denied" | string;
    readonly processAccess: "denied" | string;
    readonly heapSnapshot: "denied" | "partial" | string;
    readonly cpuProfiler: "denied" | "partial" | string;
    readonly sensitivePathRedaction: "not_proven" | "required" | string;
    readonly rawSockets: boolean;
  }

  export interface WasiProfile {
    readonly status: "disabled_by_default" | "controlled_opt_in" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly pureWebAssembly: "available" | string;
    readonly rawNodeWasi: "denied" | string;
    readonly controlledFacade: "deferred" | "partial" | string;
    readonly module: "denied" | "partial" | string;
    readonly preopens: "scoped_or_authorized_roots_required" | string;
    readonly workingDirectoryPreopen: "deferred" | "partial" | string;
    readonly authorizedRoots: "required_before_enablement" | string;
    readonly args: "explicit_only" | string;
    readonly env: "empty_by_default" | string;
    readonly fd: "virtualized_required" | string;
    readonly stdio: "bounded_required" | string;
    readonly clock: "limited_required" | string;
    readonly random: "limited_required" | string;
    readonly network: "denied" | string;
    readonly processExit: "denied" | string;
    readonly symlinkEscape: "must_deny" | string;
    readonly workerIntegration: "deferred" | "partial" | string;
    readonly packagedBehavior: "not_promoted" | string;
    readonly rawHostPaths: boolean;
    readonly rawFdNumbers: boolean;
  }

  export interface NativeAddonProfile {
    readonly status: "disabled_by_default" | "desktop_opt_in" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly module: "denied" | "partial" | string;
    readonly requireExtension: "denied" | "partial" | string;
    readonly processDlopen: "denied" | string;
    readonly processBinding: "denied" | string;
    readonly nodeGyp: "denied" | string;
    readonly downloadedArtifacts: "denied" | string;
    readonly signedPackagedOnly: "required_before_enablement" | string;
    readonly abiAllowlist: "required_before_enablement" | string;
    readonly hashAllowlist: "required_before_enablement" | string;
    readonly dependencyScan: "required_before_enablement" | string;
    readonly crashIsolation: "required_before_enablement" | string;
    readonly packagedBehavior: "not_promoted" | string;
    readonly jniBridge: "denied" | string;
    readonly rawDlopen: "denied" | string;
    readonly nativeMemoryAccess: "denied" | string;
    readonly rawNativeHandles: boolean;
    readonly downloadExec: boolean;
  }

  export interface ChildProcessProfile {
    readonly status: "stable" | "disabled_by_request" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly module: "native_available" | "denied" | string;
    readonly nodePrefix: "native_available" | "denied" | string;
    readonly implementation: "native_node_builtin" | "denied" | string;
    readonly shellBridge: "separate_scoped_command_bridge" | string;
    readonly androidShellProvider: "available_with_shell_capability" | string;
    readonly appPrivateBinaryAllowlist: "not_enforced_by_native_builtin" | string;
    readonly executablePolicy: "android_app_sandbox_and_caller_validation" | "denied" | string;
    readonly cwd: "native_node_options" | "denied" | string;
    readonly env: "native_node_options" | "denied" | string;
    readonly stdin: "native_node_streams" | "deferred" | string;
    readonly stdout: "native_node_streams_caller_bounded" | "denied" | string;
    readonly stderr: "native_node_streams_caller_bounded" | "denied" | string;
    readonly timeout: "caller_managed" | "required" | string;
    readonly cancellation: "native_node_kill" | "required_before_enablement" | string;
    readonly kill: "native_available" | "required_before_enablement" | string;
    readonly cleanup: "native_node_lifecycle" | "required_before_enablement" | string;
    readonly processHandles: "native_available" | "denied" | string;
    readonly fdStreams: "native_available" | "denied" | string;
    readonly detached: "native_node_options" | "denied" | string;
    readonly shellOperators: "native_shell_option" | "denied" | string;
    readonly packagedBehavior: "stable" | "disabled_by_request" | string;
    readonly rawProcessHandles: boolean;
    readonly rawSignals: boolean;
  }

  export interface AutoJsProfile {
    readonly name: string;
    readonly engineVersion: string;
    readonly node: string;
    readonly cjs: boolean;
    readonly esm: boolean;
    readonly dynamicImport: boolean;
    readonly network: boolean;
    readonly workerThreads: boolean;
    readonly javaInterop: boolean;
    readonly nativeAddon: boolean;
    readonly scopedFs: boolean;
    readonly esmLoaderProfile: EsmLoaderProfile;
    readonly packageManagerProfile: PackageManagerProfile;
    readonly stdlibProfile: StdlibProfile;
    readonly processParityProfile: ProcessParityProfile;
    readonly packagedCapabilityProfile: PackagedCapabilityProfile;
    readonly pluginSystemProfile: PluginSystemProfile;
    readonly packageIntegrityProfile: PackageIntegrityProfile;
    readonly filesystemProfile: FilesystemProfile;
    readonly workerThreadsProfile: WorkerThreadsProfile;
    readonly processWorkerReplacementProfile: ProcessWorkerReplacementProfile;
    readonly vmProfile: VmProfile;
    readonly inspectorProfile: InspectorProfile;
    readonly wasiProfile: WasiProfile;
    readonly nativeAddonProfile: NativeAddonProfile;
    readonly childProcessProfile: ChildProcessProfile;
    readonly directBuiltinAliases: Readonly<Record<string, string>>;
    readonly limitedBuiltins: readonly string[];
    readonly disabledBuiltins: readonly string[];
    readonly networkBuiltins: readonly string[];
    readonly highRiskCapabilities: Readonly<Record<string, boolean>>;
    readonly featureStates: Readonly<Record<string, boolean>>;
    readonly featureFlags: Readonly<Record<string, FeatureFlagDescriptor>>;
    readonly enabledFeatures: readonly string[];
    readonly deniedFeatureFlags: readonly string[];
    readonly errorCodes: Readonly<Record<string, string | Readonly<Record<string, string>>>>;
    readonly compileCache: Readonly<Record<string, unknown>>;
    readonly profileSelection: ProfileSelectionDiagnostics;
    readonly profileRollbackPolicy: ProfileRollbackPolicy;
    readonly privacyDisclosurePolicy: PrivacyDisclosurePolicy;
    readonly bridgePermissions: BridgePermissionsReport;
    readonly bridgeLimits: Readonly<Record<string, unknown>>;
  }

  export interface AutoJsJavaPolicy {
    readonly enabled: boolean;
    readonly allowedClasses: readonly string[];
    readonly deniedClassPrefixes: readonly string[];
  }

  export interface AutoJsJavaObjectHandle<TValue extends Readonly<Record<string, JsonValue>> = Readonly<Record<string, JsonValue>>> {
    readonly __autojs6JavaHandle: string;
    readonly className: string;
    readonly value?: TValue;
    call(methodName: string, args?: readonly JsonValue[], options?: BridgeCallOptions): Promise<unknown>;
    callInstance(methodName: string, args?: readonly JsonValue[], options?: BridgeCallOptions): Promise<unknown>;
    getField(fieldName: string, options?: BridgeCallOptions): Promise<unknown>;
    release(options?: BridgeCallOptions): Promise<boolean>;
    dispose(options?: BridgeCallOptions): Promise<boolean>;
  }

  export interface AutoJsJavaClassDescriptor {
    readonly className: string;
    readonly name: string;
    callStatic(methodName: string, args?: readonly JsonValue[], options?: BridgeCallOptions): Promise<unknown>;
    "new"(args?: readonly JsonValue[], options?: BridgeCallOptions): Promise<AutoJsJavaObjectHandle>;
    create(args?: readonly JsonValue[], options?: BridgeCallOptions): Promise<AutoJsJavaObjectHandle>;
  }

  export interface AutoJsJavaModule {
    readonly policy: AutoJsJavaPolicy;
    findClass(className: string): AutoJsJavaClassDescriptor;
    type(className: string): AutoJsJavaClassDescriptor;
    callStatic(
      classOrName: string | AutoJsJavaClassDescriptor,
      methodName: string,
      args?: readonly JsonValue[],
      options?: BridgeCallOptions
    ): Promise<unknown>;
    "new"(classOrName: string | AutoJsJavaClassDescriptor, args?: readonly JsonValue[], options?: BridgeCallOptions): Promise<AutoJsJavaObjectHandle>;
    create(classOrName: string | AutoJsJavaClassDescriptor, args?: readonly JsonValue[], options?: BridgeCallOptions): Promise<AutoJsJavaObjectHandle>;
    call(target: string | AutoJsJavaObjectHandle, methodName: string, args?: readonly JsonValue[], options?: BridgeCallOptions): Promise<unknown>;
    callInstance(target: string | AutoJsJavaObjectHandle, methodName: string, args?: readonly JsonValue[], options?: BridgeCallOptions): Promise<unknown>;
    getField(target: string | AutoJsJavaObjectHandle, fieldName: string, options?: BridgeCallOptions): Promise<unknown>;
    release(target: string | AutoJsJavaObjectHandle, options?: BridgeCallOptions): Promise<boolean>;
    dispose(target: string | AutoJsJavaObjectHandle, options?: BridgeCallOptions): Promise<boolean>;
    defineClass(descriptor?: unknown, options?: BridgeCallOptions): never;
  }

  export interface AutoJsJavaFacade extends AutoJsJavaModule {
    readonly schema: "autojs6-node-autojs-java-v1";
    readonly enabled: boolean;
    readonly stable: boolean;
    readonly mode: "disabled" | "allowlist" | string;
    readonly reason: string;
    create(
      classOrName: string | AutoJsJavaClassDescriptor,
      args?: readonly JsonValue[],
      threadModeOrOptions?: "default" | BridgeCallOptions,
      options?: BridgeCallOptions
    ): Promise<AutoJsJavaObjectHandle>;
    loadDex(dexFile: string): never;
    loadJar(jarFile: string): never;
    setThreadMode(obj: unknown, threadMode: string): never;
    setDefaultThreadMode(clazz: unknown, threadMode: string): never;
    wrap(callback: (...args: readonly unknown[]) => unknown): never;
  }

  export interface AutoJsAndroidContextDescriptor {
    readonly schema: "autojs6-node-autojs-android-context-v1";
    readonly available: false;
    readonly proxy: true;
    readonly type: "android.content.Context";
    readonly packageName: string;
    readonly reason: string;
  }

  export interface AutoJsVersionDescriptor {
    readonly name: string;
    readonly code: number;
    readonly engine: string;
    readonly node: string;
  }

  export interface AutoJsGlobal {
    readonly schema: "autojs6-node-autojs-global-v1";
    readonly version: AutoJsVersionDescriptor;
    readonly versionName: string;
    readonly versionCode: number;
    readonly packageName: string;
    readonly profile: AutoJsProfile;
    readonly androidContext: AutoJsAndroidContextDescriptor;
    readonly java: AutoJsJavaFacade;
  }
}

declare const $autojs: AutoJs6Node.AutoJsGlobal;

declare module "autojs6:profile" {
  const profile: AutoJs6Node.AutoJsProfile;
  export = profile;
}

declare module "java" {
  const java: AutoJs6Node.AutoJsJavaModule;
  export = java;
}
