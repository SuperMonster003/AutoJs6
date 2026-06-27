declare module "package_manager" {
  namespace packageManager {
    export type Operation = "install" | "update" | "remove" | "list" | "verify" | "prune";
    export type PlanOperation = "planInstall" | "planUpdate" | "planRemove" | "planRegistryInstall" | "planTarballInstall";
    export type MutationOperation = "install" | "update" | "remove";
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

    export interface RegistryPackumentDist {
      readonly tarball?: string;
      readonly integrity?: string;
      readonly shasum?: string;
      readonly [key: string]: unknown;
    }

    export interface RegistryPackumentVersion {
      readonly name?: string;
      readonly version?: string;
      readonly dist?: RegistryPackumentDist;
      readonly [key: string]: unknown;
    }

    export interface RegistryPackument {
      readonly name?: string;
      readonly "dist-tags"?: Readonly<Record<string, string>>;
      readonly versions?: Readonly<Record<string, RegistryPackumentVersion>>;
      readonly [key: string]: unknown;
    }

    export interface PlanRegistryInstallRequest extends BridgeOptions {
      readonly name: string;
      readonly version?: string;
      readonly range?: string;
      readonly versionRange?: string;
      readonly tag?: string;
      readonly registry?: string;
      readonly expectedIntegrity?: string;
      readonly metadata?: RegistryPackument;
      readonly packument?: RegistryPackument;
    }

    export interface PlanTarballInstallRequest extends BridgeOptions {
      readonly tarballUrl?: string;
      readonly url?: string;
      readonly name?: string;
      readonly version?: string;
      readonly expectedIntegrity?: string;
      readonly integrity?: string;
    }

    export interface MutationSourceRequest extends PlanSourceRequest {
      readonly expectedIntegrity: string;
      readonly allowMutation: true;
    }

    export interface MutationRemoveRequest extends BridgeOptions {
      readonly name: string;
      readonly version: string;
      readonly expectedIntegrity: string;
      readonly allowMutation: true;
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

    export type RemotePlanResolutionStatus =
      | "metadata_not_provided"
      | "version_requested_no_metadata"
      | "range_requested_no_metadata"
      | "resolved_from_dist_tag"
      | "resolved_from_version"
      | "resolved_from_range"
      | "tag_not_found"
      | "version_not_found"
      | "range_not_satisfied"
      | "unsupported_range"
      | "name_mismatch"
      | "integrity_supplied"
      | "integrity_required"
      | string;

    export type RemotePlanSelectorKind = "version" | "range" | "tag" | "tarball_url" | string;

    export type RemotePlanIntegrityStatus =
      | "metadata_not_provided"
      | "expected_integrity_missing"
      | "expected_integrity_matched"
      | "expected_integrity_mismatch"
      | "metadata_integrity_missing"
      | "expected_integrity_provided"
      | "expected_integrity_sha512"
      | "expected_integrity_unsupported_algorithm"
      | "expected_integrity_malformed"
      | string;

    export type RemotePlanUrlStatus =
      | "not_applicable"
      | "remote_url"
      | "unsupported_protocol"
      | "unsupported_archive"
      | "invalid_url"
      | "empty_url"
      | string;

    export interface RemotePlanResolution {
      readonly schema:
        | "autojs6-node-package-manager-registry-resolution-v1"
        | "autojs6-node-package-manager-tarball-resolution-v1"
        | string;
      readonly status: RemotePlanResolutionStatus;
      readonly name: string;
      readonly metadataName: string;
      readonly nameMatches: boolean;
      readonly requestedVersion: string;
      readonly requestedRange: string;
      readonly requestedTag: string;
      readonly selector: string;
      readonly selectorKind: RemotePlanSelectorKind;
      readonly resolvedVersion: string;
      readonly inferredName: string;
      readonly resolvedName: string;
      readonly inferredVersion: string;
      readonly versionMatches: boolean;
      readonly registry: string;
      readonly tarballUrl: string;
      readonly integrity: string;
      readonly expectedIntegrity: string;
      readonly integrityStatus: RemotePlanIntegrityStatus;
      readonly integrityAlgorithm: string;
      readonly integrityAlgorithmAllowed: boolean;
      readonly shasum: string;
      readonly hasMetadata: boolean;
      readonly rangeSupported: boolean;
      readonly urlProtocol: string;
      readonly urlHost: string;
      readonly urlPath: string;
      readonly urlStatus: RemotePlanUrlStatus;
      readonly archiveExtension: string;
      readonly tarballBasename: string;
    }

    export interface RemotePackagePlan {
      readonly schema: "autojs6-node-package-manager-remote-plan-v1";
      readonly operation: "planRegistryInstall" | "planTarballInstall" | string;
      readonly status: "blocked_by_policy" | string;
      readonly dryRun: true;
      readonly source: "raw_registry" | "remote_tarball" | string;
      readonly sourceAuthority: "raw_registry_denied" | "remote_tarball_denied" | string;
      readonly installAuthority: "none" | string;
      readonly grantsInstallAuthority: false;
      readonly networkAccess: false;
      readonly cacheAccess: false;
      readonly lifecycleScripts: "denied" | string;
      readonly binLinks: "denied" | string;
      readonly nativePayloads: "denied" | string;
      readonly requiresExpectedIntegrity: boolean;
      readonly message: string;
      readonly request: Readonly<Record<string, unknown>>;
      readonly resolution: RemotePlanResolution;
      readonly policy: RegistrySourcePolicy | TarballSourcePolicy;
      readonly futurePromotionRequires: readonly string[];
    }

    export type SourceAuthority = "local_unpacked_host_managed_only" | string;
    export type InstallAuthority = "package_manager_mutate_explicit_local_unpacked" | "internal_substrate_only" | string;
    export type SourcePolicyStatus = "guarded_local_unpacked_mutation" | "decision_only_no_install_authority" | string;
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
      readonly grantsInstallAuthority: boolean;
      readonly allowedSources: readonly AllowedSource[];
      readonly deniedSources: readonly DeniedSource[];
      readonly planOperations: readonly PlanOperation[];
      readonly mutationOperations: readonly MutationOperation[];
      readonly mutationCapability: "package_manager.mutate" | string;
      readonly mutationConfirmation: "allowMutation_required" | string;
      readonly requiresExpectedIntegrity: boolean;
      readonly localUnpacked: LocalUnpackedSourcePolicy;
      readonly registry: RegistrySourcePolicy;
      readonly tarball: TarballSourcePolicy;
      readonly npmCli: NpmCliSourcePolicy;
      readonly futurePromotionRequires: readonly string[];
    }

    export interface DiagnosticStatus {
      readonly schema: "autojs6-node-package-manager-status-v1";
      readonly status: "guarded_mutation_partial" | "read_maintenance_partial" | "diagnostic_only" | string;
      readonly profileStatus: "android_substrate_partial" | string;
      readonly hostPackageManager: "available" | string;
      readonly androidStore: "local_app_private_partial" | string;
      readonly androidRuntimeInstall: "local_unpacked_only" | string;
      readonly jsBridge: "guarded_mutation_partial" | "read_maintenance_partial" | "diagnostic_only" | string;
      readonly requirePackageManager: "guarded_mutation_partial" | "read_maintenance_partial" | "diagnostic_only" | string;
      readonly diagnosticFacade: "available" | string;
      readonly sourcePolicy: SourcePolicyStatus;
      readonly sourceAuthority: SourceAuthority;
      readonly installAuthority: InstallAuthority;
      readonly allowedSources: readonly AllowedSource[];
      readonly deniedSources: readonly DeniedSource[];
      readonly planOperations: readonly PlanOperation[];
      readonly mutationOperations: readonly MutationOperation[];
      readonly mutationCapability: "package_manager.mutate" | string;
      readonly mutationConfirmation: "allowMutation_required" | string;
      readonly requiresExpectedIntegrity: boolean;
      readonly operations: readonly Operation[];
      readonly packageKinds: readonly PackageKind[];
      readonly npmCli: "denied" | string;
      readonly npmFacade: "guarded_package_manager_alias" | string;
      readonly requireNpm: "guarded_package_manager_alias" | string;
      readonly npmFacadeOperations: readonly string[];
      readonly registryDownload: "deferred" | string;
      readonly lifecycleScripts: "denied" | string;
      readonly binLinks: "denied" | string;
      readonly nativePayloads: "denied" | string;
      readonly androidNetwork: "denied" | string;
      readonly rawExecutables: boolean;
      readonly rawRegistryAccess: boolean;
      readonly grantsInstallAuthority: boolean;
    }

    export interface DiagnosticPolicy {
      readonly schema: "autojs6-node-package-manager-policy-v1";
      readonly status: "guarded_mutation_partial" | "read_maintenance_partial" | "diagnostic_only" | string;
      readonly androidStore: "local_app_private_partial" | string;
      readonly androidRuntimeInstall: "local_unpacked_only" | string;
      readonly npmCli: "denied" | string;
      readonly npmFacade: "guarded_package_manager_alias" | string;
      readonly requireNpm: "guarded_package_manager_alias" | string;
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
      readonly mutationOperations: readonly MutationOperation[];
      readonly npmFacadeOperations: readonly string[];
      readonly mutationCapability: "package_manager.mutate" | string;
      readonly mutationConfirmation: "allowMutation_required" | string;
      readonly requiresExpectedIntegrity: boolean;
      readonly operations: readonly Operation[];
      readonly packageKinds: readonly PackageKind[];
      readonly androidNetwork: "denied" | string;
      readonly rawExecutables: boolean;
      readonly rawRegistryAccess: boolean;
      readonly grantsInstallAuthority: boolean;
    }

    export interface PackageManagerModule {
      status(): DiagnosticStatus;
      policy(): DiagnosticPolicy;
      sourcePolicy(): SourcePolicy;
      planInstall(request: PlanSourceRequest): Promise<PackagePlan>;
      planUpdate(request: PlanSourceRequest): Promise<PackagePlan>;
      planRemove(request: PlanRemoveRequest): Promise<PackagePlan>;
      planRegistryInstall(request: PlanRegistryInstallRequest): Promise<RemotePackagePlan>;
      planTarballInstall(request: PlanTarballInstallRequest): Promise<RemotePackagePlan>;
      install(request: MutationSourceRequest): Promise<OperationResult>;
      update(request: MutationSourceRequest): Promise<OperationResult>;
      remove(request: MutationRemoveRequest): Promise<OperationResult>;
      list(options?: ListOptions): Promise<readonly PackageRecord[]>;
      verify(request: VerifyRequest): Promise<OperationResult>;
      prune(options?: PruneOptions): Promise<OperationResult>;
    }
  }

  const packageManager: packageManager.PackageManagerModule;
  export = packageManager;
}
