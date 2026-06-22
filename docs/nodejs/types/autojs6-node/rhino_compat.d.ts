declare module "autojs6:compat" {
  namespace rhinoCompat {
    export type ErrorCode =
      | "ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION"
      | "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"
      | "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN";

    export interface ErrorCodes {
      readonly invalidOption: "ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION";
      readonly unsupported: "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED";
      readonly blockedByDesign: "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN";
    }

    export interface Status {
      readonly schema: "autojs6-node-rhino-compat-loader-v1";
      readonly entry: "autojs6:compat";
      readonly mode: "explicit-opt-in";
      readonly defaultGlobalInjection: false;
      readonly installed: false;
      readonly implementedTasks: readonly string[];
      readonly pendingTasks: readonly string[];
      readonly globalNames: readonly string[];
      readonly moduleNames: readonly string[];
    }

    export interface InstallOptions {
      readonly global?: boolean;
      readonly globals?: boolean;
    }

    export interface InstallResult {
      readonly schema: "autojs6-node-rhino-compat-install-result-v1";
      readonly installed: false;
      readonly explicit: true;
      readonly globals: readonly string[];
      readonly modules: readonly string[];
      readonly reason: string;
    }

    export interface RhinoCompatError extends AutoJs6Node.AutoJs6BridgeError {
      readonly code: ErrorCode;
      readonly autojs6Code?: ErrorCode;
      readonly module: "autojs6:compat";
      readonly api?: string;
      readonly category?: "invalid-option" | "unsupported" | "blocked-by-design";
    }

    export interface RhinoCompatModule {
      readonly name: "autojs6:compat";
      readonly version: 1;
      readonly mode: "explicit-opt-in";
      readonly errorCodes: ErrorCodes;
      status(): Status;
      install(options?: InstallOptions): InstallResult;
      unsupported(apiName?: string, reason?: string): RhinoCompatError;
      blockedByDesign(apiName?: string, reason?: string): RhinoCompatError;
      assertSupported(apiName?: string): never;
    }
  }

  const rhinoCompat: rhinoCompat.RhinoCompatModule;
  export = rhinoCompat;
}
