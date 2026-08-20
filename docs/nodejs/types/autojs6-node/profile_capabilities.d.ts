declare namespace AutoJs6Node {
  /** Runtime profile identifiers used by Phase 13 profile-aware declarations. */
  export type RuntimeProfileId =
    | "safe_default"
    | "pro_compat_opt_in"
    | "desktop_compat_opt_in"
    | "debug_unsafe_lab";

  /** Type-level availability for a module or surface under a specific profile. */
  export type ProfileModuleMode =
    | "available"
    | "partial"
    | "profile_gated"
    | "diagnostic_only"
    | "denied"
    | "unsupported";

  export type ProfilePackagedBehavior =
    | "supported"
    | "partial"
    | "metadata_only"
    | "gated"
    | "denied";

  export interface ProfileModuleSpec<
    TModule extends string,
    TTargetProfile extends RuntimeProfileId,
    TSafeDefault extends ProfileModuleMode,
    TTargetMode extends ProfileModuleMode,
    TPackagedBehavior extends ProfilePackagedBehavior,
  > {
    readonly module: TModule;
    readonly targetProfile: TTargetProfile;
    readonly safeDefault: TSafeDefault;
    readonly targetMode: TTargetMode;
    readonly packagedBehavior: TPackagedBehavior;
    readonly grantsAuthority: false;
    readonly note: string;
  }

  /**
   * Profile-aware catalog for high-value Pro/Desktop migration surfaces.
   *
   * This catalog is declaration-only. It records the profile required before a
   * script can reasonably expect a surface to be considered, but it does not
   * declare denied desktop modules as callable modules and does not grant
   * runtime authority.
   */
  export interface ProfileModuleCatalog {
    readonly "toast": ProfileModuleSpec<"toast", "safe_default", "available", "available", "supported">;
    readonly "app": ProfileModuleSpec<"app", "safe_default", "available", "available", "partial">;
    readonly "accessibility": ProfileModuleSpec<"accessibility", "pro_compat_opt_in", "partial", "partial", "gated">;
    readonly "image": ProfileModuleSpec<"image", "pro_compat_opt_in", "partial", "partial", "gated">;
    readonly "ocr": ProfileModuleSpec<"ocr", "pro_compat_opt_in", "partial", "partial", "gated">;
    readonly "ui": ProfileModuleSpec<"ui", "pro_compat_opt_in", "profile_gated", "partial", "gated">;
    readonly "ui.overlay": ProfileModuleSpec<"ui.overlay", "pro_compat_opt_in", "denied", "profile_gated", "gated">;
    readonly "work_manager": ProfileModuleSpec<"work_manager", "pro_compat_opt_in", "partial", "partial", "partial">;
    readonly "java": ProfileModuleSpec<"java", "pro_compat_opt_in", "available", "available", "supported">;
    readonly "$autojs.java": ProfileModuleSpec<"$autojs.java", "pro_compat_opt_in", "available", "available", "supported">;
    readonly "rhino.install": ProfileModuleSpec<"rhino.install", "pro_compat_opt_in", "available", "available", "supported">;
    readonly "esm": ProfileModuleSpec<"esm", "desktop_compat_opt_in", "available", "available", "supported">;
    readonly "worker_threads": ProfileModuleSpec<"worker_threads", "desktop_compat_opt_in", "available", "available", "supported">;
    readonly "child_process": ProfileModuleSpec<"child_process", "desktop_compat_opt_in", "available", "available", "supported">;
    readonly "http": ProfileModuleSpec<"http", "desktop_compat_opt_in", "available", "available", "supported">;
    readonly "https": ProfileModuleSpec<"https", "desktop_compat_opt_in", "available", "available", "supported">;
    readonly "raw_network": ProfileModuleSpec<"raw_network", "desktop_compat_opt_in", "available", "available", "supported">;
    readonly "fs.advanced": ProfileModuleSpec<"fs.advanced", "desktop_compat_opt_in", "partial", "partial", "gated">;
    readonly "wasi": ProfileModuleSpec<"wasi", "desktop_compat_opt_in", "denied", "profile_gated", "gated">;
    readonly "inspector": ProfileModuleSpec<"inspector", "debug_unsafe_lab", "denied", "profile_gated", "denied">;
    readonly "package_manager": ProfileModuleSpec<"package_manager", "desktop_compat_opt_in", "partial", "partial", "partial">;
    readonly "npm.pure_js": ProfileModuleSpec<"npm.pure_js", "desktop_compat_opt_in", "partial", "partial", "partial">;
  }

  export type ProfileModuleName = keyof ProfileModuleCatalog;
  export type ProfileModuleFor<TModule extends ProfileModuleName> = ProfileModuleCatalog[TModule];
  export type ProfileRequiredFor<TModule extends ProfileModuleName> = ProfileModuleFor<TModule>["targetProfile"];
  export type SafeDefaultModeFor<TModule extends ProfileModuleName> = ProfileModuleFor<TModule>["safeDefault"];
  export type TargetProfileModeFor<TModule extends ProfileModuleName> = ProfileModuleFor<TModule>["targetMode"];
  export type PackagedBehaviorFor<TModule extends ProfileModuleName> = ProfileModuleFor<TModule>["packagedBehavior"];

  export type ProfileModuleModeFor<
    TModule extends ProfileModuleName,
    TProfile extends RuntimeProfileId,
  > = TProfile extends "safe_default"
    ? SafeDefaultModeFor<TModule>
    : TProfile extends ProfileRequiredFor<TModule>
      ? TargetProfileModeFor<TModule>
      : "denied";

  export type ProfileModuleRequiresDeclaration<TModule extends ProfileModuleName> =
    SafeDefaultModeFor<TModule> extends "available" | "partial" ? false : true;

  export interface ProfileDeclarationMatrix {
    readonly schema: "autojs6-node-profile-declaration-matrix-v1";
    readonly status: "declaration_partial";
    readonly profiles: readonly RuntimeProfileId[];
    readonly modules: ProfileModuleCatalog;
    readonly metadataOnly: true;
    readonly grantsAuthority: false;
  }

  /** Node Profile release labels used by declaration-only profile metadata. */
  export type NodeProfileVersionId = "v1.1" | "v1.2" | "v1.3";

  /** P14 source gates that define the v1.2 declaration profile rows. */
  export type NodeProfileV12SourceGate =
    | "P14-07"
    | "P14-10"
    | "P14-11"
    | "P14-13"
    | "P14-14"
    | "P14-15"
    | "P14-17"
    | "P14-19"
    | "P14-21"
    | "P14-22"
    | "P14-23"
    | "P14-25"
    | "P14-26"
    | "P14-31"
    | "P14-32"
    | "P14-34"
    | "P14-35";

  export type NodeProfileV12CapabilityState =
    | "stable"
    | "provider_poc_not_promoted"
    | "policy_ready_not_promoted"
    | "host_contract_ready"
    | "stable_denied"
    | "intentionally_unsupported";

  export type NodeProfileV12TypeSurface =
    | "callable_declaration"
    | "denial_declaration"
    | "diagnostic_declaration"
    | "declaration_only_metadata";

  export type NodeProfileV12PackagedEvidence =
    | "not_applicable"
    | "runtime_kit"
    | "imported_packaged_smoke"
    | "host_contract_ready"
    | "future_device_archive_required"
    | "conditional_skip_native_unavailable"
    | "stable_denied";

  export interface NodeProfileV12CapabilitySpec<
    TCapability extends string,
    TTargetProfile extends RuntimeProfileId,
    TSafeDefault extends ProfileModuleMode,
    TTargetMode extends ProfileModuleMode,
    TState extends NodeProfileV12CapabilityState,
    TTypeSurface extends NodeProfileV12TypeSurface,
    TPackagedEvidence extends NodeProfileV12PackagedEvidence,
    TSourceGate extends NodeProfileV12SourceGate,
    TFutureOnly extends boolean,
  > {
    readonly capability: TCapability;
    readonly targetProfile: TTargetProfile;
    readonly safeDefault: TSafeDefault;
    readonly targetMode: TTargetMode;
    readonly v12State: TState;
    readonly typeSurface: TTypeSurface;
    readonly packagedEvidence: TPackagedEvidence;
    readonly sourceGate: TSourceGate;
    readonly futureOnly: TFutureOnly;
    readonly metadataOnly: true;
    readonly grantsAuthority: false;
    readonly note: string;
  }

  /**
   * v1.2 declaration matrix for promoted, gated, and stable-denied surfaces.
   *
   * This matrix is still declaration-only. It updates the type-level profile
   * truth for Phase 14 reports, but it does not promote runtime capability,
   * does not make future-only methods successful, and does not declare raw
   * Android or raw desktop objects as callable authority.
   */
  export interface NodeProfileV12CapabilityCatalog {
    readonly "ui.live": NodeProfileV12CapabilitySpec<"ui.live", "pro_compat_opt_in", "profile_gated", "partial", "provider_poc_not_promoted", "callable_declaration", "imported_packaged_smoke", "P14-07", true>;
    readonly "ui.overlay": NodeProfileV12CapabilitySpec<"ui.overlay", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P14-10", true>;
    readonly "accessibility": NodeProfileV12CapabilitySpec<"accessibility", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P14-11", true>;
    readonly "media_projection": NodeProfileV12CapabilitySpec<"media_projection", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P14-13", true>;
    readonly "image.advanced": NodeProfileV12CapabilitySpec<"image.advanced", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P14-14", true>;
    readonly "ocr": NodeProfileV12CapabilitySpec<"ocr", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "imported_packaged_smoke", "P14-15", true>;
    readonly "tasks.database": NodeProfileV12CapabilitySpec<"tasks.database", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "denial_declaration", "future_device_archive_required", "P14-17", true>;
    readonly "work_manager": NodeProfileV12CapabilitySpec<"work_manager", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "imported_packaged_smoke", "P14-19", true>;
    readonly "tasks.broadcast": NodeProfileV12CapabilitySpec<"tasks.broadcast", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "denial_declaration", "future_device_archive_required", "P14-21", true>;
    readonly "notifications": NodeProfileV12CapabilitySpec<"notifications", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P14-22", true>;
    readonly "device.power": NodeProfileV12CapabilitySpec<"device.power", "pro_compat_opt_in", "partial", "partial", "policy_ready_not_promoted", "callable_declaration", "future_device_archive_required", "P14-23", true>;
    readonly "recorder.session": NodeProfileV12CapabilitySpec<"recorder.session", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "denial_declaration", "future_device_archive_required", "P14-25", true>;
    readonly "media.playback": NodeProfileV12CapabilitySpec<"media.playback", "pro_compat_opt_in", "denied", "denied", "stable_denied", "denial_declaration", "stable_denied", "P14-26", true>;
    readonly "mediastore": NodeProfileV12CapabilitySpec<"mediastore", "pro_compat_opt_in", "denied", "denied", "stable_denied", "denial_declaration", "stable_denied", "P14-26", true>;
    readonly "worker_threads": NodeProfileV12CapabilitySpec<"worker_threads", "desktop_compat_opt_in", "available", "available", "stable", "callable_declaration", "runtime_kit", "P14-32", false>;
    readonly "child_process": NodeProfileV12CapabilitySpec<"child_process", "desktop_compat_opt_in", "available", "available", "stable", "callable_declaration", "runtime_kit", "P14-32", false>;
    readonly "process_worker": NodeProfileV12CapabilitySpec<"process_worker", "desktop_compat_opt_in", "unsupported", "unsupported", "intentionally_unsupported", "diagnostic_declaration", "stable_denied", "P14-31", true>;
    readonly "packaged.aggregate": NodeProfileV12CapabilitySpec<"packaged.aggregate", "pro_compat_opt_in", "denied", "profile_gated", "host_contract_ready", "declaration_only_metadata", "host_contract_ready", "P14-34", false>;
  }

  export type NodeProfileV12CapabilityName = keyof NodeProfileV12CapabilityCatalog;
  export type NodeProfileV12CapabilityFor<TCapability extends NodeProfileV12CapabilityName> =
    NodeProfileV12CapabilityCatalog[TCapability];
  export type NodeProfileV12RequiredProfileFor<TCapability extends NodeProfileV12CapabilityName> =
    NodeProfileV12CapabilityFor<TCapability>["targetProfile"];
  export type NodeProfileV12SafeDefaultModeFor<TCapability extends NodeProfileV12CapabilityName> =
    NodeProfileV12CapabilityFor<TCapability>["safeDefault"];
  export type NodeProfileV12TargetModeFor<TCapability extends NodeProfileV12CapabilityName> =
    NodeProfileV12CapabilityFor<TCapability>["targetMode"];
  export type NodeProfileV12StateFor<TCapability extends NodeProfileV12CapabilityName> =
    NodeProfileV12CapabilityFor<TCapability>["v12State"];
  export type NodeProfileV12TypeSurfaceFor<TCapability extends NodeProfileV12CapabilityName> =
    NodeProfileV12CapabilityFor<TCapability>["typeSurface"];
  export type NodeProfileV12PackagedEvidenceFor<TCapability extends NodeProfileV12CapabilityName> =
    NodeProfileV12CapabilityFor<TCapability>["packagedEvidence"];
  export type NodeProfileV12FutureOnlyFor<TCapability extends NodeProfileV12CapabilityName> =
    NodeProfileV12CapabilityFor<TCapability>["futureOnly"];

  export type NodeProfileV12ModeFor<
    TCapability extends NodeProfileV12CapabilityName,
    TProfile extends RuntimeProfileId,
  > = TProfile extends "safe_default"
    ? NodeProfileV12SafeDefaultModeFor<TCapability>
    : TProfile extends NodeProfileV12RequiredProfileFor<TCapability>
      ? NodeProfileV12TargetModeFor<TCapability>
      : "denied";

  export type NodeProfileV12FutureOnlyCapabilityName = {
    [TCapability in NodeProfileV12CapabilityName]: NodeProfileV12FutureOnlyFor<TCapability> extends true
      ? TCapability
      : never;
  }[NodeProfileV12CapabilityName];

  export type NodeProfileV12CapabilityNameByState<TState extends NodeProfileV12CapabilityState> = {
    [TCapability in NodeProfileV12CapabilityName]: NodeProfileV12StateFor<TCapability> extends TState
      ? TCapability
      : never;
  }[NodeProfileV12CapabilityName];

  export type NodeProfileV12StableDeniedCapabilityName =
    NodeProfileV12CapabilityNameByState<"stable_denied" | "intentionally_unsupported">;

  export interface NodeProfileV12DeclarationMatrix {
    readonly schema: "autojs6-node-profile-v1-2-declaration-matrix-v1";
    readonly profileVersion: "v1.2";
    readonly status: "v1_2_type_declarations_ready";
    readonly sourceGates: readonly NodeProfileV12SourceGate[];
    readonly capabilities: NodeProfileV12CapabilityCatalog;
    readonly metadataOnly: true;
    readonly grantsAuthority: false;
  }

  /** P15 source gates that define the v1.3 declaration profile rows. */
  export type NodeProfileV13SourceGate =
    | "P14-35"
    | "P15-04"
    | "P15-07"
    | "P15-08"
    | "P15-09"
    | "P15-10"
    | "P15-11"
    | "P15-12"
    | "P15-13"
    | "P15-14"
    | "P15-16"
    | "P15-17"
    | "P15-18"
    | "P15-20"
    | "P15-21"
    | "P15-22"
    | "P15-23"
    | "P15-24"
    | "P15-25";

  export type NodeProfileV13CapabilityState =
    | "provider_poc_not_promoted"
    | "doc_sync_ready_no_authority"
    | "security_corpus_ready_no_authority"
    | "host_contract_ready"
    | "stable_denied";

  export type NodeProfileV13TypeSurface =
    | "callable_declaration"
    | "denial_declaration"
    | "diagnostic_declaration"
    | "declaration_only_metadata";

  export type NodeProfileV13PackagedEvidence =
    | "not_applicable"
    | "imported_packaged_smoke"
    | "host_contract_ready"
    | "future_device_archive_required"
    | "stable_denied"
    | "security_corpus_ready";

  export interface NodeProfileV13CapabilitySpec<
    TCapability extends string,
    TTargetProfile extends RuntimeProfileId,
    TSafeDefault extends ProfileModuleMode,
    TTargetMode extends ProfileModuleMode,
    TState extends NodeProfileV13CapabilityState,
    TTypeSurface extends NodeProfileV13TypeSurface,
    TPackagedEvidence extends NodeProfileV13PackagedEvidence,
    TSourceGate extends NodeProfileV13SourceGate,
    TFutureOnly extends boolean,
  > {
    readonly capability: TCapability;
    readonly targetProfile: TTargetProfile;
    readonly safeDefault: TSafeDefault;
    readonly targetMode: TTargetMode;
    readonly v13State: TState;
    readonly typeSurface: TTypeSurface;
    readonly packagedEvidence: TPackagedEvidence;
    readonly sourceGate: TSourceGate;
    readonly futureOnly: TFutureOnly;
    readonly metadataOnly: true;
    readonly grantsAuthority: false;
    readonly note: string;
  }

  /**
   * v1.3 declaration matrix for provider POC, host-contract, docs-sync,
   * security-corpus, and stable-denied surfaces.
   *
   * This matrix is declaration-only. It records the type-level profile truth
   * for Phase 15 reports, but it does not promote runtime capability, does not
   * make future-only methods successful, and does not declare raw Android
   * objects, raw file descriptors, or Java objects as callable authority.
   */
  export interface NodeProfileV13CapabilityCatalog {
    readonly "ui.live": NodeProfileV13CapabilitySpec<"ui.live", "pro_compat_opt_in", "profile_gated", "partial", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P15-04", true>;
    readonly "ui.overlay": NodeProfileV13CapabilitySpec<"ui.overlay", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P15-07", true>;
    readonly "accessibility": NodeProfileV13CapabilitySpec<"accessibility", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P15-08", true>;
    readonly "media_projection": NodeProfileV13CapabilitySpec<"media_projection", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P15-09", true>;
    readonly "image.advanced": NodeProfileV13CapabilitySpec<"image.advanced", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P15-10", true>;
    readonly "ocr": NodeProfileV13CapabilitySpec<"ocr", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P15-10", true>;
    readonly "tasks.database": NodeProfileV13CapabilitySpec<"tasks.database", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "denial_declaration", "future_device_archive_required", "P15-12", true>;
    readonly "tasks.schema": NodeProfileV13CapabilitySpec<"tasks.schema", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "denial_declaration", "future_device_archive_required", "P15-13", true>;
    readonly "work_manager": NodeProfileV13CapabilitySpec<"work_manager", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "imported_packaged_smoke", "P15-14", true>;
    readonly "tasks.broadcast": NodeProfileV13CapabilitySpec<"tasks.broadcast", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "denial_declaration", "future_device_archive_required", "P15-16", true>;
    readonly "notifications": NodeProfileV13CapabilitySpec<"notifications", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P15-17", true>;
    readonly "device.power": NodeProfileV13CapabilitySpec<"device.power", "pro_compat_opt_in", "partial", "partial", "provider_poc_not_promoted", "callable_declaration", "future_device_archive_required", "P15-18", true>;
    readonly "recorder.session": NodeProfileV13CapabilitySpec<"recorder.session", "pro_compat_opt_in", "denied", "profile_gated", "provider_poc_not_promoted", "denial_declaration", "future_device_archive_required", "P15-20", true>;
    readonly "media.playback": NodeProfileV13CapabilitySpec<"media.playback", "pro_compat_opt_in", "denied", "denied", "stable_denied", "denial_declaration", "stable_denied", "P15-21", true>;
    readonly "mediastore": NodeProfileV13CapabilitySpec<"mediastore", "pro_compat_opt_in", "denied", "denied", "stable_denied", "denial_declaration", "stable_denied", "P15-21", true>;
    readonly "docs.types.release_sync": NodeProfileV13CapabilitySpec<"docs.types.release_sync", "safe_default", "partial", "partial", "doc_sync_ready_no_authority", "declaration_only_metadata", "not_applicable", "P15-22", false>;
    readonly "security.regression": NodeProfileV13CapabilitySpec<"security.regression", "safe_default", "denied", "profile_gated", "security_corpus_ready_no_authority", "declaration_only_metadata", "security_corpus_ready", "P15-23", false>;
    readonly "packaged.aggregate": NodeProfileV13CapabilitySpec<"packaged.aggregate", "pro_compat_opt_in", "denied", "profile_gated", "host_contract_ready", "declaration_only_metadata", "host_contract_ready", "P15-24", false>;
  }

  export type NodeProfileV13CapabilityName = keyof NodeProfileV13CapabilityCatalog;
  export type NodeProfileV13CapabilityFor<TCapability extends NodeProfileV13CapabilityName> =
    NodeProfileV13CapabilityCatalog[TCapability];
  export type NodeProfileV13RequiredProfileFor<TCapability extends NodeProfileV13CapabilityName> =
    NodeProfileV13CapabilityFor<TCapability>["targetProfile"];
  export type NodeProfileV13SafeDefaultModeFor<TCapability extends NodeProfileV13CapabilityName> =
    NodeProfileV13CapabilityFor<TCapability>["safeDefault"];
  export type NodeProfileV13TargetModeFor<TCapability extends NodeProfileV13CapabilityName> =
    NodeProfileV13CapabilityFor<TCapability>["targetMode"];
  export type NodeProfileV13StateFor<TCapability extends NodeProfileV13CapabilityName> =
    NodeProfileV13CapabilityFor<TCapability>["v13State"];
  export type NodeProfileV13TypeSurfaceFor<TCapability extends NodeProfileV13CapabilityName> =
    NodeProfileV13CapabilityFor<TCapability>["typeSurface"];
  export type NodeProfileV13PackagedEvidenceFor<TCapability extends NodeProfileV13CapabilityName> =
    NodeProfileV13CapabilityFor<TCapability>["packagedEvidence"];
  export type NodeProfileV13FutureOnlyFor<TCapability extends NodeProfileV13CapabilityName> =
    NodeProfileV13CapabilityFor<TCapability>["futureOnly"];

  export type NodeProfileV13ModeFor<
    TCapability extends NodeProfileV13CapabilityName,
    TProfile extends RuntimeProfileId,
  > = TProfile extends "safe_default"
    ? NodeProfileV13SafeDefaultModeFor<TCapability>
    : TProfile extends NodeProfileV13RequiredProfileFor<TCapability>
      ? NodeProfileV13TargetModeFor<TCapability>
      : "denied";

  export type NodeProfileV13FutureOnlyCapabilityName = {
    [TCapability in NodeProfileV13CapabilityName]: NodeProfileV13FutureOnlyFor<TCapability> extends true
      ? TCapability
      : never;
  }[NodeProfileV13CapabilityName];

  export type NodeProfileV13CapabilityNameByState<TState extends NodeProfileV13CapabilityState> = {
    [TCapability in NodeProfileV13CapabilityName]: NodeProfileV13StateFor<TCapability> extends TState
      ? TCapability
      : never;
  }[NodeProfileV13CapabilityName];

  export type NodeProfileV13StableDeniedCapabilityName =
    NodeProfileV13CapabilityNameByState<"stable_denied">;

  export interface NodeProfileV13DeclarationMatrix {
    readonly schema: "autojs6-node-profile-v1-3-declaration-matrix-v1";
    readonly profileVersion: "v1.3";
    readonly status: "v1_3_type_declarations_ready";
    readonly sourceGates: readonly NodeProfileV13SourceGate[];
    readonly capabilities: NodeProfileV13CapabilityCatalog;
    readonly metadataOnly: true;
    readonly grantsAuthority: false;
  }
}
