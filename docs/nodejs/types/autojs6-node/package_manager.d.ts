declare module "package_manager" {
  namespace packageManager {
    export type Operation = "install" | "update" | "remove" | "list" | "verify" | "prune";
    export type PackageKind = "dependency" | "plugin" | "project";

    export interface BridgeOptions {
      readonly timeoutMs?: number;
      readonly signal?: unknown;
      readonly permissions?: readonly string[];
      readonly [key: string]: unknown;
    }

    export interface PackageRequest extends BridgeOptions {
      readonly name?: string;
      readonly version?: string;
      readonly sourceDir?: string;
      readonly expectedIntegrity?: string;
      readonly kind?: PackageKind;
    }

    export interface ListOptions extends BridgeOptions {}

    export interface VerifyRequest extends BridgeOptions {
      readonly name: string;
      readonly version: string;
    }

    export interface PruneOptions extends BridgeOptions {
      readonly maxTransactionAgeMs?: number;
    }

    export interface PackageRecord {
      readonly schema: "autojs6-node-android-package-record-v1" | string;
      readonly name: string;
      readonly version: string;
      readonly kind: PackageKind | string;
      readonly integrity: string;
      readonly installedAtEpochMs: number;
      readonly fileCount: number;
      readonly totalBytes: number;
      readonly packageDir: string;
    }

    export interface OperationResult {
      readonly operation: Operation | string;
      readonly status: "installed" | "updated" | "removed" | "missing" | "verified" | "pruned" | string;
      readonly transactionId: string;
      readonly message: string;
      readonly record: PackageRecord | null;
    }

    export interface DiagnosticStatus {
      readonly schema: "autojs6-node-package-manager-status-v1";
      readonly status: "read_maintenance_partial" | "diagnostic_only" | string;
      readonly profileStatus: "android_substrate_partial" | string;
      readonly hostPackageManager: "available" | string;
      readonly androidStore: "local_app_private_partial" | string;
      readonly androidRuntimeInstall: "local_unpacked_only" | string;
      readonly jsBridge: "read_maintenance_partial" | "diagnostic_only" | string;
      readonly requirePackageManager: "read_maintenance_partial" | "diagnostic_only" | string;
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
      readonly status: "read_maintenance_partial" | "diagnostic_only" | string;
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
      list(options?: ListOptions): Promise<readonly PackageRecord[]>;
      verify(request: VerifyRequest): Promise<OperationResult>;
      prune(options?: PruneOptions): Promise<OperationResult>;
    }
  }

  const packageManager: packageManager.PackageManagerModule;
  export = packageManager;
}
