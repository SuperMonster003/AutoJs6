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
    readonly "java": ProfileModuleSpec<"java", "pro_compat_opt_in", "denied", "profile_gated", "metadata_only">;
    readonly "$autojs.java": ProfileModuleSpec<"$autojs.java", "pro_compat_opt_in", "denied", "profile_gated", "metadata_only">;
    readonly "rhino.install": ProfileModuleSpec<"rhino.install", "pro_compat_opt_in", "denied", "profile_gated", "metadata_only">;
    readonly "esm": ProfileModuleSpec<"esm", "desktop_compat_opt_in", "partial", "partial", "partial">;
    readonly "worker_threads": ProfileModuleSpec<"worker_threads", "desktop_compat_opt_in", "denied", "profile_gated", "gated">;
    readonly "http": ProfileModuleSpec<"http", "desktop_compat_opt_in", "partial", "partial", "gated">;
    readonly "https": ProfileModuleSpec<"https", "desktop_compat_opt_in", "partial", "partial", "gated">;
    readonly "fs.advanced": ProfileModuleSpec<"fs.advanced", "desktop_compat_opt_in", "partial", "partial", "gated">;
    readonly "wasi": ProfileModuleSpec<"wasi", "desktop_compat_opt_in", "denied", "profile_gated", "gated">;
    readonly "inspector": ProfileModuleSpec<"inspector", "debug_unsafe_lab", "denied", "profile_gated", "denied">;
    readonly "package_manager": ProfileModuleSpec<"package_manager", "desktop_compat_opt_in", "denied", "profile_gated", "gated">;
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
}
