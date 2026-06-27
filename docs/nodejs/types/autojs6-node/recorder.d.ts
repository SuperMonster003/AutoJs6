declare module "recorder" {
  namespace recorder {
    export interface RecorderStatus {
      readonly schema: "autojs6-node-recorder-status-v1";
      readonly recording: false;
      readonly providerAvailable: boolean;
      readonly microphonePermissionRequired: boolean;
      readonly microphonePermissionGranted: boolean;
      readonly foregroundDisclosureRequired: boolean;
      readonly reason: string;
    }

    export interface RecorderStartOptions extends AutoJs6Node.BridgeCallOptions {
      readonly path?: string;
      readonly sampleRate?: number;
      readonly bitRate?: number;
      readonly format?: string;
    }

    export interface RecorderModule {
      getStatus(options?: AutoJs6Node.BridgeCallOptions): Promise<RecorderStatus>;
      start(options?: RecorderStartOptions): Promise<never>;
      stop(options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
    }
  }

  const recorder: recorder.RecorderModule;
  export = recorder;
}
