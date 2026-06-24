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
      isIgnoringBatteryOptimizations(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      openBatteryOptimizationSettings(options?: DevicePowerSettingsOptions): Promise<DevicePowerSettingsResult>;
    }

    export interface DevicePowerSettingsOptions extends AutoJs6Node.BridgeCallOptions {
      dryRun?: boolean;
    }

    export interface DevicePowerSettingsResult {
      readonly schema: "autojs6-node-battery-optimization-settings-v1";
      readonly sdkInt: number;
      readonly ignoringBatteryOptimizations: boolean;
      readonly canOpenSettings: boolean;
      readonly opened: boolean;
      readonly dryRun: boolean;
      readonly action: string;
      readonly reason: string;
    }
  }

  const device: device.DeviceModule;
  export = device;
}
