declare module "device" {
  namespace device {
    export interface DeviceModule {
      readonly sdkInt: number;
      readonly width: number;
      readonly height: number;
      readonly density: number;
      isScreenOn(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      wakeUp(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      vibrate(durationMs: number, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }
  }

  const device: device.DeviceModule;
  export = device;
}
