declare module "plugins" {
  namespace plugins {
    export type PluginLifecycleState = "installed" | "loaded" | "starting" | "started" | "stopping" | "stopped";
    export type PluginResourceKind = "timer" | "provider" | "ui" | "socket" | "image" | "generic";

    export interface PluginResourceToken {
      readonly id: number;
      readonly kind: PluginResourceKind;
      readonly owner: string;
    }

    export interface PluginResourceSnapshot {
      readonly owner: string;
      readonly total: number;
      readonly timers: number;
      readonly providers: number;
      readonly ui: number;
      readonly sockets: number;
      readonly images: number;
      readonly generic: number;
      readonly cleanupErrors: number;
    }

    export interface PluginResourceRegistry {
      track(kind: PluginResourceKind, handle?: unknown, cleanup?: (handle: unknown) => unknown): PluginResourceToken;
      release(token: PluginResourceToken | number): boolean | Promise<unknown>;
      addCleanup(cleanup: () => unknown): PluginResourceToken;
      snapshot(): PluginResourceSnapshot;
      setTimeout(callback: (...args: unknown[]) => void, delay?: number, ...args: unknown[]): PluginResourceToken;
      clearTimeout(token: PluginResourceToken | number): boolean | Promise<unknown>;
      setInterval(callback: (...args: unknown[]) => void, delay?: number, ...args: unknown[]): PluginResourceToken;
      clearInterval(token: PluginResourceToken | number): boolean | Promise<unknown>;
    }

    export interface PluginLifecycleContext {
      readonly id: string;
      readonly namespace: string;
      readonly owner: string;
      readonly phase: "start" | "stop";
      readonly capabilities: readonly string[];
      readonly permissions: readonly string[];
      readonly config: Readonly<Record<string, unknown>>;
      readonly options: Readonly<Record<string, unknown>>;
      readonly resources: PluginResourceRegistry;
    }

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
      readonly namespace: string;
      readonly capabilities: readonly string[];
      readonly enabled: boolean;
      readonly loaded: boolean;
      readonly started: boolean;
      readonly state: PluginLifecycleState;
      readonly startCount: number;
      readonly stopCount: number;
      readonly reloadCount: number;
      readonly resources: PluginResourceSnapshot;
      readonly config: Readonly<Record<string, unknown>>;
    }

    export interface PluginInstallOptions {
      readonly source?: string;
      readonly enabled?: boolean;
      readonly config?: Readonly<Record<string, unknown>>;
    }

    export interface PluginReloadOptions extends PluginInstallOptions {
      readonly debug: true;
    }

    export interface PluginsModule {
      install(id: string, options?: PluginInstallOptions): PluginStatus | Promise<PluginStatus>;
      enable(id: string): PluginStatus;
      disable(id: string): PluginStatus | Promise<PluginStatus>;
      isEnabled(id: string): boolean;
      configure(id: string, config: Readonly<Record<string, unknown>>): Readonly<Record<string, unknown>>;
      config(id: string): Readonly<Record<string, unknown>>;
      version(id: string): string;
      dependencies(id: string): readonly string[];
      exports(id: string): Readonly<Record<string, string>>;
      capabilities(id: string): readonly string[];
      status(id: string): PluginStatus;
      start(id: string, options?: Readonly<Record<string, unknown>>): PluginStatus | Promise<PluginStatus>;
      stop(id: string, options?: Readonly<Record<string, unknown>>): PluginStatus | Promise<PluginStatus>;
      reload(id: string, options: PluginReloadOptions): PluginStatus | Promise<PluginStatus>;
      unload(id: string): boolean | Promise<boolean>;
      list(): readonly PluginStatus[];
      manifest(id: string): PluginManifest;
      resolve(id: string, exportName?: string): string;
      load<T = unknown>(id: string, exportName?: string): T;
    }
  }

  const plugins: plugins.PluginsModule;
  export = plugins;
}
