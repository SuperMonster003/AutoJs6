declare module "media_projection" {
  namespace mediaProjection {
    export interface ScreenCaptureOptions extends AutoJs6Node.BridgeCallOptions {
      orientation?: "portrait" | "landscape" | "auto";
      width?: number;
      height?: number;
      requireExistingPermission?: boolean;
    }

    export interface ScreenCapturer {
      readonly id: string;
      readonly width: number;
      readonly height: number;
      nextImage(options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      stop(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }

    export interface MediaProjectionModule {
      requestScreenCapture(options?: ScreenCaptureOptions): Promise<ScreenCapturer>;
    }
  }

  const mediaProjection: mediaProjection.MediaProjectionModule;
  export = mediaProjection;
}
