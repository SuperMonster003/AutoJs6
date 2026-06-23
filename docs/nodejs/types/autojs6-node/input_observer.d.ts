declare module "input_observer" {
  export type InputObserverKeyAction = "down" | "up";
  export type InputObserverSourceId = "fake" | "accessibility";

  export interface InputObserverKeyDescriptor {
    readonly type?: "key";
    readonly keys: readonly string[];
    readonly actions?: readonly InputObserverKeyAction[];
    readonly source?: InputObserverSourceId;
    readonly intercept?: false;
  }

  export interface InputObserverOptions {
    readonly maxQueueSize?: number;
    readonly timeoutMs?: number;
    readonly signal?: AutoJs6Node.AbortSignalLike;
  }

  export interface InputObserverDrainOptions {
    readonly maxEvents?: number;
    readonly timeoutMs?: number;
    readonly signal?: AutoJs6Node.AbortSignalLike;
  }

  export interface InputObserverKeyEvent {
    readonly type: "key";
    readonly source: InputObserverSourceId;
    readonly key: string;
    readonly keyCode: number;
    readonly action: InputObserverKeyAction;
    readonly eventTime: number;
    readonly repeatCount: number;
  }

  export interface InputObserverSubscription {
    readonly id: string;
    readonly type: "key";
    readonly source: InputObserverSourceId;
    readonly ready: boolean;
    readonly closed: boolean;
    drainEvents(options?: InputObserverDrainOptions): Promise<readonly InputObserverKeyEvent[]>;
    close(options?: InputObserverDrainOptions): Promise<boolean>;
  }

  export interface InputObserverSource {
    readonly id: InputObserverSourceId;
    readonly type: "key";
    readonly available: boolean;
    readonly live: boolean;
    readonly reason?: string;
  }

  export interface InputObserverModule {
    readonly policy: {
      readonly maxActiveSubscriptions: 8;
      readonly defaultQueueSize: 64;
      readonly maxQueueSize: 256;
      readonly defaultDrainBatchSize: 32;
      readonly maxDrainBatchSize: 128;
      readonly defaultTimeoutMs: 10000;
      readonly maxTimeoutMs: 60000;
      readonly allowedActions: readonly InputObserverKeyAction[];
      readonly allowedSources: readonly InputObserverSourceId[];
    };
    observeKeys(
      descriptor: InputObserverKeyDescriptor | readonly string[],
      options?: InputObserverOptions,
    ): Promise<InputObserverSubscription>;
    getAvailableSources(options?: InputObserverDrainOptions): Promise<readonly InputObserverSource[]>;
  }

  const inputObserver: InputObserverModule;
  export default inputObserver;
  export const policy: InputObserverModule["policy"];
  export const observeKeys: InputObserverModule["observeKeys"];
  export const getAvailableSources: InputObserverModule["getAvailableSources"];
}
