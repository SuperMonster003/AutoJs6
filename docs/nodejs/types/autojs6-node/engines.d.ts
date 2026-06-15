declare module "engines" {
  namespace engines {
    export interface EngineSnapshot {
      readonly id: number | string;
      readonly engineName: string;
      readonly backend?: string;
      readonly sourceName?: string;
      readonly cwd?: string;
      readonly workingDirectory?: string;
      readonly packageName?: string;
      readonly nodeVersion?: string;
      readonly spawnDepth: number;
    }

    export interface ExecOptions extends AutoJs6Node.BridgeCallOptions {
      timeoutMs: number;
      cwd?: string;
      arguments?: AutoJs6Node.JsonObject;
    }

    export interface EnginesModule {
      myEngine(): EngineSnapshot;
      all(options?: AutoJs6Node.BridgeCallOptions): Promise<readonly EngineSnapshot[]>;
      stopAll(options?: AutoJs6Node.BridgeCallOptions): Promise<number>;
      stopSelf(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      execScript(name: string, source: string, options: ExecOptions): Promise<EngineSnapshot>;
      execScriptFile(path: string, options: ExecOptions): Promise<EngineSnapshot>;
    }
  }

  const engines: engines.EnginesModule;
  export = engines;
}
