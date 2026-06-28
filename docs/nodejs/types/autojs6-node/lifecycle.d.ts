declare module "autojs6:lifecycle" {
  namespace lifecycle {
    export type StopReason = string;

    export interface CheckpointOptions extends AutoJs6Node.BridgeCallOptions {
      reason?: string;
    }

    export interface RestoreOptions extends AutoJs6Node.BridgeCallOptions {
      clear?: boolean;
    }

    export interface Policy {
      readonly enabled: boolean;
      readonly maxBytes: number;
      readonly automaticRestart: false;
      readonly restartPolicy: "never";
      readonly executionMode: "interactive_long_running" | string;
      readonly launchSurface: "interactive_session" | "packaged_long_running" | string;
    }

    export interface CheckpointRecord {
      readonly schemaVersion: number;
      readonly projectKey: string;
      readonly packageName: string;
      readonly workingDirectory: string;
      readonly executionId: string;
      readonly sourceName: string;
      readonly executionMode: "interactive_long_running" | string;
      readonly launchSurface: "interactive_session" | "packaged_long_running" | string;
      readonly reason: string;
      readonly checkpointBytes: number;
      readonly createdAtEpochMs: number;
      readonly updatedAtEpochMs: number;
      readonly automaticRestart: false;
      readonly restartPolicy: "never";
      readonly checkpoint: AutoJs6Node.JsonValue;
    }

    export type CheckpointHandler = (reason: string, record: CheckpointRecord) => void | Promise<void>;
    export type RestoreHandler = (checkpoint: AutoJs6Node.JsonValue, record: CheckpointRecord) => void | Promise<void>;
    export type StopHandler = (reason: StopReason) => void | Promise<void>;
    export type Unsubscribe = () => void;

    export interface LifecycleModule {
      readonly policy: Policy;
      checkpoint(checkpoint: AutoJs6Node.JsonValue, options?: CheckpointOptions): Promise<CheckpointRecord>;
      readCheckpoint(options?: AutoJs6Node.BridgeCallOptions): Promise<CheckpointRecord | null>;
      clearCheckpoint(options?: AutoJs6Node.BridgeCallOptions): Promise<{ readonly deleted: boolean; readonly projectKey: string }>;
      onCheckpoint(handler: CheckpointHandler): Unsubscribe;
      onRestore(handler: RestoreHandler, options?: RestoreOptions): Promise<CheckpointRecord | null>;
      onStop(handler: StopHandler): Unsubscribe;
    }
  }

  const lifecycle: lifecycle.LifecycleModule;
  export = lifecycle;
}
