declare module "rhino" {
  namespace rhino {
    export type ErrorCode =
      | "ERR_AUTOJS6_RHINO_SHIM_DISABLED"
      | "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED"
      | "ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED"
      | "ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED"
      | "ERR_AUTOJS6_RHINO_INVALID_REQUEST"
      | "ERR_AUTOJS6_RHINO_EXECUTION_ERROR"
      | "ERR_AUTOJS6_RHINO_EXECUTION_TIMEOUT"
      | "ERR_AUTOJS6_RHINO_JSON_UNSAFE_RESULT";

    export interface ErrorCodes {
      readonly shimDisabled: "ERR_AUTOJS6_RHINO_SHIM_DISABLED";
      readonly installUnsupported: "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED";
      readonly importUnsupported: "ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED";
      readonly javaAdapterUnsupported: "ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED";
      readonly invalidRequest: "ERR_AUTOJS6_RHINO_INVALID_REQUEST";
      readonly executionError: "ERR_AUTOJS6_RHINO_EXECUTION_ERROR";
      readonly executionTimeout: "ERR_AUTOJS6_RHINO_EXECUTION_TIMEOUT";
      readonly jsonUnsafeResult: "ERR_AUTOJS6_RHINO_JSON_UNSAFE_RESULT";
    }

    export interface Status {
      readonly schema: "autojs6-rhino-json-only-shim-disabled-v1";
      readonly module: "rhino";
      readonly mode: "disabled";
      readonly enabled: false;
      readonly run: "disabled";
      readonly install: "unsupported";
      readonly globalsInstalled: false;
      readonly nodeAlias: false;
      readonly shadowing: "reserved";
    }

    export interface InstallOptions {
      readonly explicit: true;
      readonly globals?: boolean;
      readonly target?: object;
    }

    export interface InstallResult {
      readonly schema: "autojs6-rhino-install-java-proxy-v1";
      readonly module: "rhino";
      readonly mode: "java-allowlist-proxy";
      readonly explicit: true;
      readonly globalsInstalled: boolean;
      readonly installed: readonly ["Packages", "java", "android"];
      readonly javaInterop: AutoJs6Node.AutoJsJavaPolicy;
      readonly importClass: false;
      readonly importPackage: false;
      readonly JavaAdapter: false;
      readonly shadowing: "reserved";
    }

    export interface RhinoShimError extends Error {
      readonly code: ErrorCode;
      readonly autojs6Code?: ErrorCode;
      readonly module: "rhino";
      readonly api?: "run" | "install" | string;
      readonly category?: "disabled" | "unsupported" | string;
    }

    export type JsonPrimitive = string | number | boolean | null;
    export type JsonValue = JsonPrimitive | readonly JsonValue[] | { readonly [key: string]: JsonValue };

    export interface RunBaseRequest {
      readonly explicit: true;
      readonly timeoutMs: number;
      readonly args?: readonly JsonValue[];
      readonly cwd?: string;
      readonly signal?: AutoJs6Node.AbortSignalLike;
    }

    export interface RunSourceRequest extends RunBaseRequest {
      readonly source: string;
      readonly name?: string;
      readonly path?: never;
    }

    export interface RunPathRequest extends RunBaseRequest {
      readonly path: string;
      readonly source?: never;
      readonly name?: never;
    }

    export type RunRequest = RunSourceRequest | RunPathRequest;

    export interface ConsoleLine {
      readonly level: "log" | "info" | "warn" | "error" | string;
      readonly message: string;
    }

    export interface ConsoleSummary {
      readonly schema: "autojs6-rhino-json-only-console-v1";
      readonly lines: readonly ConsoleLine[];
      readonly truncated: boolean;
      readonly maxLines: number;
      readonly maxBytes: number;
    }

    export interface LifecycleSummary {
      readonly engineCreated: boolean;
      readonly forceStopRequested: boolean;
      readonly destroyed: boolean;
    }

    export interface RunError {
      readonly name: string;
      readonly message: string;
      readonly code: ErrorCode;
      readonly category: string;
      readonly sourceName?: string;
      readonly lineNumber?: number;
      readonly columnNumber?: number;
      readonly stack?: string;
    }

    export interface RunResultBase {
      readonly schema: "autojs6-rhino-json-only-run-result-v1";
      readonly mode: "json-only-poc";
      readonly provider: "android-rhino-json-only-poc" | "queued-host" | string;
      readonly timedOut: boolean;
      readonly sourceName: string;
      readonly durationMs: number;
      readonly console: ConsoleSummary;
      readonly lifecycle: LifecycleSummary;
    }

    export interface RunSuccess<T extends JsonValue = JsonValue> extends RunResultBase {
      readonly ok: true;
      readonly value: T;
    }

    export interface RunFailure extends RunResultBase {
      readonly ok: false;
      readonly error: RunError;
    }

    export type RunResult<T extends JsonValue = JsonValue> = RunSuccess<T> | RunFailure;

    export interface RhinoModule {
      readonly name: "rhino";
      readonly version: 1;
      readonly schema: "autojs6-rhino-json-only-shim-disabled-v1";
      readonly mode: "disabled";
      readonly enabled: false;
      readonly errorCodes: ErrorCodes;
      isEnabled(): false;
      status(): Status;
      run<T extends JsonValue = JsonValue>(request: RunRequest): Promise<RunResult<T>>;
      run(request?: unknown): Promise<never>;
      install(options: InstallOptions): InstallResult;
      install(options?: unknown): never;
      importClass(value?: unknown, options?: unknown): never;
      importPackage(value?: unknown, options?: unknown): never;
      JavaAdapter(...args: readonly unknown[]): never;
    }
  }

  const rhino: rhino.RhinoModule;
  export = rhino;
}
