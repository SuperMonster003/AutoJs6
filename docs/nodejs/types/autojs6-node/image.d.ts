declare module "image" {
  namespace image {
    export type ColorInput = number | string;
    export type MultiColorPointInput = readonly [number, number, ColorInput] | MultiColorPoint;

    export interface ImageSize {
      readonly width: number;
      readonly height: number;
    }

    export interface ImageMatch extends AutoJs6Node.Point {
      readonly score: number | null;
    }

    export interface MultiColorPoint {
      readonly x: number;
      readonly y: number;
      readonly color: ColorInput;
    }

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

    export interface MatchTemplateOptions extends FindImageOptions {
      limit?: number;
      max?: number;
    }

    export interface ThresholdOptions extends AutoJs6Node.BridgeCallOptions {
      threshold?: number;
      maxValue?: number;
      type?: "binary" | "binary_inv" | "trunc" | "tozero" | "tozero_inv";
    }

    export interface FindColorOptions extends AutoJs6Node.BridgeCallOptions {
      threshold?: number;
      region?: readonly [number, number, number, number];
    }

    export interface ImageModule {
      captureScreen(options?: CaptureScreenOptions): Promise<AutoJs6Node.ImageHandle>;
      readImage(path: string, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      saveImage(image: AutoJs6Node.ImageHandleLike, path: string, options?: SaveImageOptions): Promise<void>;
      saveImage(image: AutoJs6Node.ImageHandleLike, path: string, format: SaveImageOptions["format"], options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      clip(image: AutoJs6Node.ImageHandleLike, x: number, y: number, width: number, height: number, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      resize(image: AutoJs6Node.ImageHandleLike, width: number, height: number, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      grayscale(image: AutoJs6Node.ImageHandleLike, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      threshold(image: AutoJs6Node.ImageHandleLike, options?: ThresholdOptions): Promise<AutoJs6Node.ImageHandle>;
      findImage(image: AutoJs6Node.ImageHandleLike, template: AutoJs6Node.ImageHandleLike, options?: FindImageOptions): Promise<AutoJs6Node.Point | null>;
      matchTemplate(image: AutoJs6Node.ImageHandleLike, template: AutoJs6Node.ImageHandleLike, options?: MatchTemplateOptions): Promise<readonly ImageMatch[]>;
      findColor(image: AutoJs6Node.ImageHandleLike, color: ColorInput, options?: FindColorOptions): Promise<AutoJs6Node.Point | null>;
      findMultiColors(image: AutoJs6Node.ImageHandleLike, color: ColorInput, points: readonly MultiColorPointInput[], options?: FindColorOptions): Promise<AutoJs6Node.Point | null>;
      getSize(image: AutoJs6Node.ImageHandleLike): ImageSize;
      recycle(image: AutoJs6Node.ImageHandleLike, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }
  }

  const image: image.ImageModule;
  export = image;
}
