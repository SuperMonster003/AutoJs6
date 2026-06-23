declare module "rhino" {
  namespace rhino {
    export type ErrorCode =
      | "ERR_AUTOJS6_RHINO_SHIM_DISABLED"
      | "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED";

    export interface ErrorCodes {
      readonly shimDisabled: "ERR_AUTOJS6_RHINO_SHIM_DISABLED";
      readonly installUnsupported: "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED";
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

    export interface RhinoShimError extends Error {
      readonly code: ErrorCode;
      readonly autojs6Code?: ErrorCode;
      readonly module: "rhino";
      readonly api?: "run" | "install" | string;
      readonly category?: "disabled" | "unsupported" | string;
    }

    export interface RhinoDisabledModule {
      readonly name: "rhino";
      readonly version: 1;
      readonly schema: "autojs6-rhino-json-only-shim-disabled-v1";
      readonly mode: "disabled";
      readonly enabled: false;
      readonly errorCodes: ErrorCodes;
      isEnabled(): false;
      status(): Status;
      run(request?: unknown): Promise<never>;
      install(options?: unknown): never;
    }
  }

  const rhino: rhino.RhinoDisabledModule;
  export = rhino;
}
