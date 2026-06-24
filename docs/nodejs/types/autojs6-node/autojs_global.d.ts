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
    readonly status: "partial" | "experimental" | "disabled" | "unsupported" | string;
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
    readonly dataUrlImports: "denied" | string;
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
    readonly operations: readonly string[];
    readonly packageKinds: readonly string[];
    readonly jsBridge: "not_exposed" | "partial" | string;
    readonly requirePackageManager: "denied" | "partial" | string;
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
    readonly processGetBuiltinModule: "denied" | string;
    readonly androidDifferences: "documented_stable" | string;
    readonly androidUnsupported: readonly string[];
    readonly resourceLimits: "bounded_callbacks_streams_crypto_zlib" | string;
    readonly npmCorpus: "real_npm_phase9_30_fixture_baseline" | string;
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
    readonly envMutation: "script_local_mutable" | string;
    readonly envSources: "request_map_plus_safe_defaults" | string;
    readonly envDefaultKeys: readonly string[];
    readonly envFilteredKeys: readonly string[];
    readonly cwd: "scoped_working_directory" | string;
    readonly chdir: "scoped_relative_directory_only" | string;
    readonly exit: "exitCode_only_process_exit_denied" | string;
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
    readonly status: "disabled_by_default" | "experimental_opt_in_preflight" | string;
    readonly defaultEnabled: boolean;
    readonly requestLocalOptIn: boolean;
    readonly nativeAvailability: "preflight_required" | string;
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
    readonly workerThreads: "request_local_experimental_preflight" | string;
    readonly childProcess: "denied" | string;
    readonly shellBridge: "separate_scoped_command_bridge" | string;
    readonly executionMode: "worker_computation_reserved" | string;
    readonly secondExecutionSlot: "not_closed" | string;
    readonly scopedScriptPath: "workingDirectory" | string;
    readonly messageSizeBytes: number;
    readonly maxQueuedMessages: number;
    readonly cleanup: "worker_pool_close_or_execution_destroy" | string;
    readonly packagedBehavior: "not_promoted" | string;
    readonly bridgeModules: "denied" | string;
    readonly nestedWorkers: boolean;
    readonly rawProcessHandles: boolean;
  }

  export interface VmProfile {
    readonly status: "disabled_by_default" | "experimental_opt_in" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly module: "denied" | "partial" | string;
    readonly script: "deferred" | "partial" | string;
    readonly context: "deferred" | "partial" | string;
    readonly timeout: "required_before_enablement" | string;
    readonly compileFunction: "denied" | string;
    readonly syntheticModule: "denied" | string;
    readonly sourceTextModule: "denied" | string;
    readonly dynamicImportHooks: "denied" | string;
    readonly bridgeModules: "denied" | string;
    readonly processAccess: "denied_for_future_contexts" | string;
    readonly scopedFs: "must_preserve" | string;
    readonly privateBindings: "denied" | string;
    readonly hostObjects: "denied" | string;
    readonly memoryLimits: "not_defined" | string;
    readonly packagedBehavior: "not_promoted" | string;
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
    readonly status: "disabled_by_default" | "desktop_opt_in" | string;
    readonly defaultEnabled: boolean;
    readonly targetProfiles: readonly string[];
    readonly module: "denied" | "partial" | string;
    readonly nodePrefix: "denied" | string;
    readonly scopedRunner: "deferred" | "partial" | string;
    readonly shellBridge: "separate_scoped_command_bridge" | string;
    readonly androidShellProvider: "available_with_shell_capability" | string;
    readonly appPrivateBinaries: "required_before_enablement" | string;
    readonly allowlistedBinaries: "required_before_enablement" | string;
    readonly arbitraryPaths: "denied" | string;
    readonly cwd: "scoped_working_directory" | string;
    readonly env: "string_map_scoped" | string;
    readonly stdin: "deferred" | "bounded_required" | string;
    readonly stdout: "bounded_required" | string;
    readonly stderr: "bounded_required" | string;
    readonly timeout: "required" | string;
    readonly cancellation: "required_before_enablement" | string;
    readonly kill: "required_before_enablement" | string;
    readonly cleanup: "required_before_enablement" | string;
    readonly processHandles: "denied" | string;
    readonly fdStreams: "denied" | string;
    readonly detached: "denied" | string;
    readonly shellOperators: "denied" | string;
    readonly packagedBehavior: "not_promoted" | string;
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
    readonly experimentalFlags: Readonly<Record<string, boolean>>;
    readonly featureFlags: Readonly<Record<string, FeatureFlagDescriptor>>;
    readonly enabledExperimentalFlags: readonly string[];
    readonly deniedFeatureFlags: readonly string[];
    readonly errorCodes: Readonly<Record<string, string | Readonly<Record<string, string>>>>;
    readonly compileCache: Readonly<Record<string, unknown>>;
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
    readonly experimental: boolean;
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
