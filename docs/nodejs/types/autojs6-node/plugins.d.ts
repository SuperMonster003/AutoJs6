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
      readonly jsOnly: true;
      readonly nativeAddons: false;
    }

    export interface PluginsModule {
      manifest(id: string): PluginManifest;
      resolve(id: string): string;
      load<T = unknown>(id: string): T;
    }
  }

  const plugins: plugins.PluginsModule;
  export = plugins;
}
