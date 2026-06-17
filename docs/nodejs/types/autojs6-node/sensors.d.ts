declare module "sensors" {
  namespace sensors {
    export type SensorType = "accelerometer" | "gyroscope" | "light";

    export interface SensorInfo {
      readonly type: SensorType;
      readonly name: string;
      readonly vendor?: string;
      readonly version?: number;
      readonly resolution?: number;
      readonly power?: number;
      readonly minDelayUs?: number;
      readonly maxDelayUs?: number;
    }

    export interface SensorEvent {
      readonly type: SensorType;
      readonly timestamp: number;
      readonly accuracy: number;
      readonly values: readonly number[];
    }

    export interface SensorOptions extends AutoJs6Node.BridgeCallOptions {
      samplingIntervalMs?: number;
      intervalMs?: number;
      maxQueueSize?: number;
      drainIntervalMs?: number;
    }

    export interface SensorSubscription {
      readonly id: string;
      readonly type: SensorType;
      readonly closed: boolean;
      readonly ready: Promise<void>;
      close(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }

    export interface SensorsModule {
      readonly policy: {
        readonly maxSubscriptions: number;
        readonly minSamplingIntervalMs: number;
        readonly defaultSamplingIntervalMs: number;
        readonly maxSamplingIntervalMs: number;
        readonly defaultTimeoutMs: number;
        readonly drainIntervalMs: number;
        readonly defaultMaxQueueSize: number;
        readonly hardMaxQueueSize: number;
      };

      getAvailableSensors(options?: AutoJs6Node.BridgeCallOptions): Promise<readonly SensorInfo[]>;
      once(type: SensorType, options?: SensorOptions): Promise<SensorEvent>;
      subscribe(type: SensorType, callback: (event: SensorEvent) => void, options?: SensorOptions): SensorSubscription;
    }
  }

  const sensors: sensors.SensorsModule;
  export = sensors;
}
