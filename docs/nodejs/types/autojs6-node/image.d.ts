declare module "image" {
  namespace image {
    export interface CaptureScreenOptions extends AutoJs6Node.BridgeCallOptions {
      orientation?: "portrait" | "landscape" | "auto";
      requireExistingPermission?: boolean;
    }

    export interface SaveImageOptions extends AutoJs6Node.BridgeCallOptions {
      format?: "png" | "jpg" | "jpeg" | "webp";
      quality?: number;
    }

    export interface FindImageOptions extends AutoJs6Node.BridgeCallOptions {
      threshold?: number;
      region?: readonly [number, number, number, number];
    }

    export interface ImageModule {
      captureScreen(options?: CaptureScreenOptions): Promise<AutoJs6Node.ImageHandle>;
      readImage(path: string, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      saveImage(image: AutoJs6Node.ImageHandleLike, path: string, options?: SaveImageOptions): Promise<void>;
      clip(image: AutoJs6Node.ImageHandleLike, x: number, y: number, width: number, height: number, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      resize(image: AutoJs6Node.ImageHandleLike, width: number, height: number, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      findImage(image: AutoJs6Node.ImageHandleLike, template: AutoJs6Node.ImageHandleLike, options?: FindImageOptions): Promise<AutoJs6Node.Point | null>;
      recycle(image: AutoJs6Node.ImageHandleLike, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }
  }

  const image: image.ImageModule;
  export = image;
}
