declare module "package_manager" {
  namespace packageManager {
    export type Operation = "install" | "update" | "remove" | "list" | "verify" | "prune";
    export type PlanOperation = "planInstall" | "planUpdate" | "planRemove";
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

    export interface PlanSourceRequest extends BridgeOptions {
      readonly name: string;
      readonly version: string;
      readonly sourceDir: string;
      readonly expectedIntegrity?: string;
      readonly kind?: PackageKind;
    }

    export interface PlanRemoveRequest extends BridgeOptions {
      readonly name: string;
      readonly version: string;
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

    export interface PackagePlan {
      readonly schema: "autojs6-node-android-package-plan-v1";
      readonly operation: "install" | "update" | "remove" | string;
      readonly status: "ready" | "missing" | "already_installed" | string;
      readonly dryRun: true;
      readonly sourceAuthority: SourceAuthority;
      readonly installAuthority: InstallAuthority;
      readonly grantsInstallAuthority: false;
      readonly message: string;
      readonly record: PackageRecord | null;
      readonly existingRecord: PackageRecord | null;
    }

    export type SourceAuthority = "local_unpacked_host_managed_only" | string;
    export type InstallAuthority = "internal_substrate_only" | string;
    export type SourcePolicyStatus = "decision_only_no_install_authority" | string;
    export type AllowedSource = "local_unpacked_host_managed" | string;
    export type DeniedSource =
      | "raw_registry"
      | "remote_tarball"
      | "git"
      | "http_url"
      | "file_url"
      | "workspace_symlink"
      | "unverified_cache"
      | string;

    export interface LocalUnpackedSourcePolicy {
      readonly status: "allowed_substrate" | string;
      readonly source: "host_managed_unpacked_directory" | string;
      readonly requiresPackageJson: boolean;
      readonly requiresNameVersionMatch: boolean;
      readonly requiresSha512: boolean;
      readonly requiresHostManagedLockfile: boolean;
      readonly lifecycleScripts: "denied" | string;
      readonly binLinks: "denied" | string;
      readonly symlinks: "denied" | string;
      readonly nativePayloads: "denied" | string;
      readonly pathEscapes: "denied" | string;
    }

    export interface RegistrySourcePolicy {
      readonly status: "deferred" | string;
      readonly rawRegistryAccess: boolean;
      readonly networkInstall: "denied" | string;
      readonly metadataDownload: "deferred" | string;
      readonly cacheReuse: "unverified_cache_denied" | string;
    }

    export interface TarballSourcePolicy {
      readonly status: "host_side_only" | string;
      readonly androidDownload: "denied" | string;
      readonly androidExtraction: "deferred" | string;
      readonly integrity: "sha512_required" | string;
    }

    export interface NpmCliSourcePolicy {
      readonly status: "denied" | string;
      readonly lifecycleScripts: "denied" | string;
      readonly nativeBuild: "denied" | string;
    }

    export interface SourcePolicy {
      readonly schema: "autojs6-node-package-manager-source-policy-v1";
      readonly status: SourcePolicyStatus;
      readonly sourceAuthority: SourceAuthority;
      readonly installAuthority: InstallAuthority;
      readonly grantsInstallAuthority: false;
      readonly allowedSources: readonly AllowedSource[];
      readonly deniedSources: readonly DeniedSource[];
      readonly planOperations: readonly PlanOperation[];
      readonly localUnpacked: LocalUnpackedSourcePolicy;
      readonly registry: RegistrySourcePolicy;
      readonly tarball: TarballSourcePolicy;
      readonly npmCli: NpmCliSourcePolicy;
      readonly futurePromotionRequires: readonly string[];
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
      readonly sourcePolicy: SourcePolicyStatus;
      readonly sourceAuthority: SourceAuthority;
      readonly installAuthority: InstallAuthority;
      readonly allowedSources: readonly AllowedSource[];
      readonly deniedSources: readonly DeniedSource[];
      readonly planOperations: readonly PlanOperation[];
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
      readonly sourcePolicy: SourcePolicyStatus;
      readonly sourceAuthority: SourceAuthority;
      readonly installAuthority: InstallAuthority;
      readonly allowedSources: readonly AllowedSource[];
      readonly deniedSources: readonly DeniedSource[];
      readonly planOperations: readonly PlanOperation[];
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
      sourcePolicy(): SourcePolicy;
      planInstall(request: PlanSourceRequest): Promise<PackagePlan>;
      planUpdate(request: PlanSourceRequest): Promise<PackagePlan>;
      planRemove(request: PlanRemoveRequest): Promise<PackagePlan>;
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
