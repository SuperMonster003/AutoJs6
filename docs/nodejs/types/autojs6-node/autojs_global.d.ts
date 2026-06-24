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
