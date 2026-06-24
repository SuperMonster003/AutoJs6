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
    readonly filesystemProfile: FilesystemProfile;
    readonly workerThreadsProfile: WorkerThreadsProfile;
    readonly processWorkerReplacementProfile: ProcessWorkerReplacementProfile;
    readonly vmProfile: VmProfile;
    readonly inspectorProfile: InspectorProfile;
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
    readonly bridgePermissions: Readonly<Record<string, unknown>>;
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
