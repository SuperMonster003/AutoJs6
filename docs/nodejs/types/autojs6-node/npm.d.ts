declare module "npm" {
  import packageManager = require("package_manager");

  namespace npm {
    export type PackageRecord = packageManager.PackageRecord;
    export type OperationResult = packageManager.OperationResult;
    export type PackagePlan = packageManager.PackagePlan;
    export type RemotePackagePlan = packageManager.RemotePackagePlan;
    export type RegistryPackument = packageManager.RegistryPackument;
    export type RegistryPackumentVersion = packageManager.RegistryPackumentVersion;
    export type RegistryPackumentDist = packageManager.RegistryPackumentDist;
    export type RemotePlanResolution = packageManager.RemotePlanResolution;
    export type RemotePlanSelectorKind = packageManager.RemotePlanSelectorKind;
    export type RemotePlanIntegrityStatus = packageManager.RemotePlanIntegrityStatus;
    export type RemotePlanUrlStatus = packageManager.RemotePlanUrlStatus;
    export type SourcePolicy = packageManager.SourcePolicy;
    export type PlanSourceRequest = packageManager.PlanSourceRequest;
    export type PlanRemoveRequest = packageManager.PlanRemoveRequest;
    export type PlanRegistryInstallRequest = packageManager.PlanRegistryInstallRequest;
    export type PlanTarballInstallRequest = packageManager.PlanTarballInstallRequest;
    export type MutationSourceRequest = packageManager.MutationSourceRequest;
    export type MutationRemoveRequest = packageManager.MutationRemoveRequest;
    export type ListOptions = packageManager.ListOptions;
    export type VerifyRequest = packageManager.VerifyRequest;
    export type PruneOptions = packageManager.PruneOptions;

    export interface NpmFacadeStatus {
      readonly schema: "autojs6-node-npm-facade-status-v1";
      readonly status: "guarded_package_manager_alias" | string;
      readonly aliasFor: "package_manager" | string;
      readonly packageManagerStatus: "guarded_mutation_partial" | string;
      readonly packageManagerBridge: "guarded_mutation_partial" | string;
      readonly npmCli: "denied" | string;
      readonly registryDownload: "deferred" | string;
      readonly lifecycleScripts: "denied" | string;
      readonly binLinks: "denied" | string;
      readonly nativePayloads: "denied" | string;
      readonly sourcePolicy: "guarded_local_unpacked_mutation" | string;
      readonly sourceAuthority: "local_unpacked_host_managed_only" | string;
      readonly installAuthority: "package_manager_mutate_explicit_local_unpacked" | string;
      readonly operations: readonly string[];
      readonly delegatedOperations: readonly packageManager.Operation[];
      readonly planOperations: readonly packageManager.PlanOperation[];
      readonly mutationOperations: readonly packageManager.MutationOperation[];
      readonly mutationCapability: "package_manager.mutate" | string;
      readonly mutationConfirmation: "allowMutation_required" | string;
      readonly requiresExpectedIntegrity: boolean;
      readonly rawRegistryAccess: boolean;
      readonly rawExecutables: boolean;
      readonly packageManager: packageManager.DiagnosticStatus;
    }

    export interface NpmFacadePolicy {
      readonly schema: "autojs6-node-npm-facade-policy-v1";
      readonly status: "guarded_package_manager_alias" | string;
      readonly aliasFor: "package_manager" | string;
      readonly cli: "denied" | string;
      readonly npmCli: "denied" | string;
      readonly registryDownload: "deferred" | string;
      readonly tarballInstall: "host_side_only" | string;
      readonly lifecycleScripts: "denied" | string;
      readonly debugLifecycleScripts: "not_promoted" | string;
      readonly binLinks: "denied" | string;
      readonly symlinks: "denied" | string;
      readonly nativeBuild: "denied" | string;
      readonly nativePayloads: "denied" | string;
      readonly postinstallDownload: "denied" | string;
      readonly sourcePolicy: "guarded_local_unpacked_mutation" | string;
      readonly sourceAuthority: "local_unpacked_host_managed_only" | string;
      readonly installAuthority: "package_manager_mutate_explicit_local_unpacked" | string;
      readonly allowedSources: readonly packageManager.AllowedSource[];
      readonly deniedSources: readonly packageManager.DeniedSource[];
      readonly operations: readonly string[];
      readonly delegatedOperations: readonly packageManager.Operation[];
      readonly planOperations: readonly packageManager.PlanOperation[];
      readonly mutationOperations: readonly packageManager.MutationOperation[];
      readonly mutationCapability: "package_manager.mutate" | string;
      readonly mutationConfirmation: "allowMutation_required" | string;
      readonly requiresExpectedIntegrity: boolean;
      readonly rawRegistryAccess: boolean;
      readonly rawExecutables: boolean;
      readonly packageManager: packageManager.DiagnosticPolicy;
    }

    export interface NpmFacadeModule {
      status(): NpmFacadeStatus;
      policy(): NpmFacadePolicy;
      sourcePolicy(): SourcePolicy;
      packageManager(): packageManager.PackageManagerModule;
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
      cli(...args: readonly unknown[]): never;
      exec(...args: readonly unknown[]): never;
      runScript(...args: readonly unknown[]): never;
      installFromRegistry(...args: readonly unknown[]): never;
      publish(...args: readonly unknown[]): never;
      pack(...args: readonly unknown[]): never;
    }
  }

  const npm: npm.NpmFacadeModule;
  export = npm;
}
