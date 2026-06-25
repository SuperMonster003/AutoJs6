declare module "package_manager" {
  namespace packageManager {
    export type Operation = "install" | "update" | "remove" | "list" | "verify" | "prune";
    export type PackageKind = "dependency" | "plugin" | "project";

    export interface PackageRequest {
      readonly name?: string;
      readonly version?: string;
      readonly sourceDir?: string;
      readonly expectedIntegrity?: string;
      readonly kind?: PackageKind;
      readonly [key: string]: unknown;
    }

    export interface DiagnosticStatus {
      readonly schema: "autojs6-node-package-manager-status-v1";
      readonly status: "diagnostic_only" | string;
      readonly profileStatus: "android_substrate_partial" | string;
      readonly hostPackageManager: "available" | string;
      readonly androidStore: "local_app_private_partial" | string;
      readonly androidRuntimeInstall: "local_unpacked_only" | string;
      readonly jsBridge: "diagnostic_only" | string;
      readonly requirePackageManager: "diagnostic_only" | string;
      readonly diagnosticFacade: "available" | string;
      readonly operations: readonly Operation[];
      readonly packageKinds: readonly PackageKind[];
      readonly npmCli: "denied" | string;
      readonly registryDownload: "deferred" | string;
      readonly lifecycleScripts: "denied" | string;
      readonly binLinks: "denied" | string;
      readonly nativePayloads: "denied" | string;
      readonly androidNetwork: "denied" | string;
      readonly rawExecutables: boolean;
      readonly rawRegistryAccess: boolean;
      readonly grantsInstallAuthority: false;
    }

    export interface DiagnosticPolicy {
      readonly schema: "autojs6-node-package-manager-policy-v1";
      readonly status: "diagnostic_only" | string;
      readonly androidStore: "local_app_private_partial" | string;
      readonly androidRuntimeInstall: "local_unpacked_only" | string;
      readonly npmCli: "denied" | string;
      readonly npmInstallEquivalent: "host_managed_plus_android_store_substrate" | string;
      readonly registryDownload: "deferred" | string;
      readonly tarballInstall: "host_side_only" | string;
      readonly lockfile: "autojs6_lock_host_managed" | string;
      readonly integrity: "sha512_required" | string;
      readonly lifecycleScripts: "denied" | string;
      readonly debugLifecycleScripts: "not_promoted" | string;
      readonly binLinks: "denied" | string;
      readonly symlinks: "denied" | string;
      readonly nativeBuild: "denied" | string;
      readonly nativePayloads: "denied" | string;
      readonly postinstallDownload: "denied" | string;
      readonly nodeModulesMutation: "transactional_store_only" | string;
      readonly transaction: "locked_cancellable_atomic" | string;
      readonly operations: readonly Operation[];
      readonly packageKinds: readonly PackageKind[];
      readonly androidNetwork: "denied" | string;
      readonly rawExecutables: boolean;
      readonly rawRegistryAccess: boolean;
      readonly grantsInstallAuthority: false;
    }

    export interface PackageManagerModule {
      status(): DiagnosticStatus;
      policy(): DiagnosticPolicy;
      install(request?: PackageRequest): never;
      update(request?: PackageRequest): never;
      remove(request?: PackageRequest): never;
      list(request?: PackageRequest): never;
      verify(request?: PackageRequest): never;
      prune(request?: PackageRequest): never;
    }
  }

  const packageManager: packageManager.PackageManagerModule;
  export = packageManager;
}
