declare module "plugins" {
  namespace plugins {
    export interface PluginManifest {
      readonly id: string;
      readonly packageName: string;
      readonly version: string;
      readonly capability: string;
      readonly entry: string;
      readonly entryPath: string;
      readonly permissions: readonly string[];
      readonly dependencies: readonly string[];
      readonly exports: Readonly<Record<string, string>>;
      readonly jsOnly: true;
      readonly nativeAddons: false;
    }

    export interface PluginStatus extends PluginManifest {
      readonly enabled: boolean;
      readonly loaded: boolean;
      readonly config: Readonly<Record<string, unknown>>;
    }

    export interface PluginInstallOptions {
      readonly source?: string;
      readonly enabled?: boolean;
      readonly config?: Readonly<Record<string, unknown>>;
    }

    export interface PluginsModule {
      install(id: string, options?: PluginInstallOptions): PluginStatus;
      enable(id: string): PluginStatus;
      disable(id: string): PluginStatus;
      isEnabled(id: string): boolean;
      configure(id: string, config: Readonly<Record<string, unknown>>): Readonly<Record<string, unknown>>;
      config(id: string): Readonly<Record<string, unknown>>;
      version(id: string): string;
      dependencies(id: string): readonly string[];
      exports(id: string): Readonly<Record<string, string>>;
      unload(id: string): boolean;
      list(): readonly PluginStatus[];
      manifest(id: string): PluginManifest;
      resolve(id: string, exportName?: string): string;
      load<T = unknown>(id: string, exportName?: string): T;
    }
  }

  const plugins: plugins.PluginsModule;
  export = plugins;
}
