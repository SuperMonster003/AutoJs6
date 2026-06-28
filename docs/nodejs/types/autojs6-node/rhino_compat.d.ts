declare module "autojs6:compat" {
  import storage = require("storage");
  import database = require("database");
  import sqlite = require("sqlite");
  import consoleModule = require("console");
  import timers = require("timers");
  import accessibility = require("accessibility");
  import app = require("app");
  import device = require("device");
  import shellModule = require("shell");
  import image = require("image");
  import mediaProjection = require("media_projection");
  import ocr = require("ocr");
  import barcodeModule = require("barcode");
  import mediaModule = require("media");
  import mediainfoModule = require("mediainfo");
  import recorderModule = require("recorder");
  import ui = require("ui");
  import files = require("files");
  import base64 = require("base64");
  import colors = require("colors");
  import formatter = require("formatter");
  import converter = require("converter");
  import s13n = require("s13n");
  import mime = require("mime");
  import nanoid = require("nanoid");
  import util = require("util");
  import opencc = require("opencc");
  import pinyin = require("pinyin");
  import pinyin4j = require("pinyin4j");
  import jsox = require("jsox");
  import workManager = require("work_manager");

  namespace rhinoCompat {
    export type ErrorCode =
      | "ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION"
      | "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"
      | "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN";

    export interface ErrorCodes {
      readonly invalidOption: "ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION";
      readonly unsupported: "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED";
      readonly blockedByDesign: "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN";
    }

    export interface Status {
      readonly schema: "autojs6-node-rhino-compat-loader-v1";
      readonly entry: "autojs6:compat";
      readonly mode: "explicit-opt-in";
      readonly defaultGlobalInjection: false;
      readonly installed: boolean;
      readonly implementedTasks: readonly string[];
      readonly pendingTasks: readonly string[];
      readonly globalNames: readonly string[];
      readonly availableGlobalNames: readonly string[];
      readonly moduleNames: readonly string[];
    }

    export interface InstallOptions {
      readonly global?: boolean;
      readonly globals?: boolean;
      readonly overwrite?: boolean;
    }

    export interface InstallResult {
      readonly schema: "autojs6-node-rhino-compat-install-result-v1";
      readonly installed: boolean;
      readonly explicit: true;
      readonly globals: readonly string[];
      readonly modules: readonly string[];
      readonly warnings: readonly string[];
      readonly reason: string;
    }

    export interface ToastOptions extends AutoJs6Node.BridgeCallOptions {
      readonly duration?: "short" | "long" | number;
      readonly log?: boolean;
    }

    export interface RhinoCompatError extends AutoJs6Node.AutoJs6BridgeError {
      readonly code: ErrorCode;
      readonly autojs6Code?: ErrorCode;
      readonly module: "autojs6:compat";
      readonly api?: string;
      readonly category?: "invalid-option" | "unsupported" | "blocked-by-design";
    }

    export type SelectorOptions = AutoJs6Node.BridgeCallOptions;

    export type FindOneOptions = number | SelectorOptions;

    export interface RhinoSelectorHandle {
      readonly type: "autojs6.rhino.selector.handle";
      readonly version: 1;
      readonly descriptor: accessibility.SelectorDescriptor;
      text(value: string): RhinoSelectorHandle;
      desc(value: string): RhinoSelectorHandle;
      id(value: string): RhinoSelectorHandle;
      className(value: string): RhinoSelectorHandle;
      textContains(value: string): RhinoSelectorHandle;
      descContains(value: string): RhinoSelectorHandle;
      textMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      descMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      idMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      classNameMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      clickable(value?: boolean): RhinoSelectorHandle;
      enabled(value?: boolean): RhinoSelectorHandle;
      scrollable(value?: boolean): RhinoSelectorHandle;
      depth(value: number): RhinoSelectorHandle;
      boundsInside(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsInside(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
      boundsContains(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsContains(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
      description(value: string): RhinoSelectorHandle;
      descriptionContains(value: string): RhinoSelectorHandle;
      descriptionMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      idContains(value: string): RhinoSelectorHandle;
      classNameContains(value: string): RhinoSelectorHandle;
      findOne(options?: FindOneOptions): Promise<accessibility.UiNodeSnapshot | null>;
      find(options?: SelectorOptions): Promise<readonly accessibility.UiNodeSnapshot[]>;
      findAll(options?: SelectorOptions): Promise<readonly accessibility.UiNodeSnapshot[]>;
      click(options?: SelectorOptions): Promise<boolean>;
      longClick(options?: SelectorOptions): Promise<boolean>;
      setText(text: string, options?: SelectorOptions): Promise<boolean>;
      scrollForward(options?: SelectorOptions): Promise<boolean>;
      scrollBackward(options?: SelectorOptions): Promise<boolean>;
      exists(options?: FindOneOptions): Promise<boolean>;
    }

    export interface SelectorDslModule {
      text(value: string): RhinoSelectorHandle;
      desc(value: string): RhinoSelectorHandle;
      id(value: string): RhinoSelectorHandle;
      className(value: string): RhinoSelectorHandle;
      textContains(value: string): RhinoSelectorHandle;
      descContains(value: string): RhinoSelectorHandle;
      textMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      descMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      idMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      classNameMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      clickable(value?: boolean): RhinoSelectorHandle;
      enabled(value?: boolean): RhinoSelectorHandle;
      scrollable(value?: boolean): RhinoSelectorHandle;
      depth(value: number): RhinoSelectorHandle;
      boundsInside(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsInside(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
      boundsContains(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsContains(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
      description(value: string): RhinoSelectorHandle;
      descriptionContains(value: string): RhinoSelectorHandle;
      descriptionMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      idContains(value: string): RhinoSelectorHandle;
      classNameContains(value: string): RhinoSelectorHandle;
      click(x: number, y: number, options?: SelectorOptions): Promise<boolean>;
      back(options?: SelectorOptions): Promise<boolean>;
      home(options?: SelectorOptions): Promise<boolean>;
      recentApps(options?: SelectorOptions): Promise<boolean>;
      recents(options?: SelectorOptions): Promise<boolean>;
    }

    export type ShellCompatOptions = boolean | shellModule.ShellExecOptions;

    export interface ShellCompatFunction {
      (command: string, options?: ShellCompatOptions): Promise<shellModule.ShellResult>;
      exec(command: string, options?: shellModule.ShellExecOptions): Promise<shellModule.ShellResult>;
      execRoot(command: string, options?: Omit<shellModule.ShellExecOptions, "root">): Promise<shellModule.ShellResult>;
      execShizuku(command: string, options?: Omit<shellModule.ShellExecOptions, "root" | "shizuku">): Promise<never>;
      execFile(path: string, args?: readonly string[], options?: shellModule.ShellExecOptions): Promise<shellModule.ShellResult>;
      checkAccess(options?: shellModule.ShellAccessOptions): shellModule.ShellAccessReport;
      setDefaultTimeout(ms: number): number;
    }

    export type ScreenCaptureRequestOptions =
      | boolean
      | "portrait"
      | "landscape"
      | "auto"
      | mediaProjection.ScreenCaptureOptions;

    export interface CaptureScreenToFileOptions extends image.CaptureScreenOptions, image.SaveImageOptions {}

    export type ImageRegionInput =
      | readonly [number, number, number, number]
      | {
          readonly x?: number;
          readonly y?: number;
          readonly width?: number;
          readonly height?: number;
          readonly left?: number;
          readonly top?: number;
          readonly right?: number;
          readonly bottom?: number;
        };

    export type ImageSizeInput =
      | readonly [number, number]
      | {
          readonly width: number;
          readonly height: number;
        };

    export type ImageThresholdType =
      | "binary"
      | "binary_inv"
      | "trunc"
      | "tozero"
      | "tozero_inv"
      | "BINARY"
      | "BINARY_INV"
      | "TRUNC"
      | "TOZERO"
      | "TOZERO_INV"
      | number;

    export interface RhinoImagesCompatModule {
      requestScreenCapture(options?: ScreenCaptureRequestOptions): Promise<boolean>;
      requestScreenCaptureAsync(options?: ScreenCaptureRequestOptions): Promise<boolean>;
      captureScreen(options?: image.CaptureScreenOptions): Promise<AutoJs6Node.ImageHandle>;
      captureScreen(path: string, options?: CaptureScreenToFileOptions): Promise<boolean>;
      stopScreenCapture(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      read(path: string, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      read(path: string, strict?: boolean, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      readImage(path: string, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      save(image: AutoJs6Node.ImageHandleLike, path: string, options?: image.SaveImageOptions): Promise<boolean>;
      save(image: AutoJs6Node.ImageHandleLike, path: string, format: image.SaveImageOptions["format"], quality?: number): Promise<boolean>;
      save(image: AutoJs6Node.ImageHandleLike, path: string, format: image.SaveImageOptions["format"], options?: image.SaveImageOptions): Promise<boolean>;
      saveImage(image: AutoJs6Node.ImageHandleLike, path: string, options?: image.SaveImageOptions): Promise<boolean>;
      saveImage(image: AutoJs6Node.ImageHandleLike, path: string, format: image.SaveImageOptions["format"], quality?: number): Promise<boolean>;
      saveImage(image: AutoJs6Node.ImageHandleLike, path: string, format: image.SaveImageOptions["format"], options?: image.SaveImageOptions): Promise<boolean>;
      clip(image: AutoJs6Node.ImageHandleLike, region: ImageRegionInput, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      clip(image: AutoJs6Node.ImageHandleLike, x: number, y: number, width: number, height: number, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      resize(image: AutoJs6Node.ImageHandleLike, size: ImageSizeInput, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      resize(image: AutoJs6Node.ImageHandleLike, width: number, height: number, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      grayscale(image: AutoJs6Node.ImageHandleLike, options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      threshold(image: AutoJs6Node.ImageHandleLike, options?: image.ThresholdOptions): Promise<AutoJs6Node.ImageHandle>;
      threshold(image: AutoJs6Node.ImageHandleLike, threshold: number, maxValue: number, type?: ImageThresholdType): Promise<AutoJs6Node.ImageHandle>;
      findImage(image: AutoJs6Node.ImageHandleLike, template: AutoJs6Node.ImageHandleLike, options?: image.FindImageOptions): Promise<AutoJs6Node.Point | null>;
      findImage(image: AutoJs6Node.ImageHandleLike, template: AutoJs6Node.ImageHandleLike, x: number, y: number, width: number, height: number, threshold?: number): Promise<AutoJs6Node.Point | null>;
      matchTemplate(image: AutoJs6Node.ImageHandleLike, template: AutoJs6Node.ImageHandleLike, options?: image.MatchTemplateOptions): Promise<readonly image.ImageMatch[]>;
      findColor(image: AutoJs6Node.ImageHandleLike, color: image.ColorInput, options?: image.FindColorOptions): Promise<AutoJs6Node.Point | null>;
      findColor(image: AutoJs6Node.ImageHandleLike, color: image.ColorInput, x: number, y: number, width: number, height: number, threshold?: number): Promise<AutoJs6Node.Point | null>;
      findMultiColors(image: AutoJs6Node.ImageHandleLike, color: image.ColorInput, points: readonly image.MultiColorPointInput[], options?: image.FindColorOptions): Promise<AutoJs6Node.Point | null>;
      getSize(image: AutoJs6Node.ImageHandleLike): image.ImageSize;
      recycle(image: AutoJs6Node.ImageHandleLike, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }

    export type OcrImageInput = string | AutoJs6Node.ImageHandleLike;

    export type RhinoOcrOptions = Omit<ocr.OcrOptions, "region"> & {
      readonly region?: ImageRegionInput;
    };

    export type OcrInputOptions = RhinoOcrOptions | ImageRegionInput;

    export interface RhinoOcrCompatFunction {
      (options?: OcrInputOptions): Promise<readonly string[]>;
      (image: OcrImageInput, options?: OcrInputOptions): Promise<readonly string[]>;
      recognize(options?: OcrInputOptions): Promise<readonly ocr.OcrResult[]>;
      recognize(image: OcrImageInput, options?: OcrInputOptions): Promise<readonly ocr.OcrResult[]>;
      recognizeText(options?: OcrInputOptions): Promise<readonly string[]>;
      recognizeText(image: OcrImageInput, options?: OcrInputOptions): Promise<readonly string[]>;
      detect(options?: OcrInputOptions): Promise<readonly ocr.OcrResult[]>;
      detect(image: OcrImageInput, options?: OcrInputOptions): Promise<readonly ocr.OcrResult[]>;
      detectTextBounds(options?: OcrInputOptions): Promise<readonly ocr.OcrResult[]>;
      detectTextBounds(image: OcrImageInput, options?: OcrInputOptions): Promise<readonly ocr.OcrResult[]>;
      tap(mode?: "mlkit"): "mlkit";
      summary(): string;
      toString(): string;
      readonly mode: "mlkit";
    }

    export type BarcodeImageInput = string | AutoJs6Node.ImageHandleLike | barcodeModule.PathImageInput | barcodeModule.HandleImageInput | barcodeModule.CaptureImageInput;

    export type RhinoBarcodeOptions = Omit<barcodeModule.BarcodeOptions, "region"> & {
      readonly region?: ImageRegionInput;
    };

    export type BarcodeInputOptions = RhinoBarcodeOptions | ImageRegionInput;

    export interface RhinoBarcodeCompatFunction {
      (options?: BarcodeInputOptions): Promise<readonly string[]>;
      (image: BarcodeImageInput, options?: BarcodeInputOptions): Promise<readonly string[]>;
      detect(options?: BarcodeInputOptions): Promise<barcodeModule.BarcodeSnapshot | null>;
      detect(image: BarcodeImageInput, options?: BarcodeInputOptions): Promise<barcodeModule.BarcodeSnapshot | null>;
      detectAll(options?: BarcodeInputOptions): Promise<readonly barcodeModule.BarcodeSnapshot[]>;
      detectAll(image: BarcodeImageInput, options?: BarcodeInputOptions): Promise<readonly barcodeModule.BarcodeSnapshot[]>;
      recognizeText(options?: BarcodeInputOptions): Promise<string | null>;
      recognizeText(image: BarcodeImageInput, options?: BarcodeInputOptions): Promise<string | null>;
      recognizeTexts(options?: BarcodeInputOptions): Promise<readonly string[]>;
      recognizeTexts(image: BarcodeImageInput, options?: BarcodeInputOptions): Promise<readonly string[]>;
    }

    export interface RhinoEventKeysCompatModule {
      readonly home: 3;
      readonly HOME: 3;
      readonly menu: 82;
      readonly MENU: 82;
      readonly back: 4;
      readonly BACK: 4;
      readonly volumeUp: 24;
      readonly volume_up: 24;
      readonly VOLUME_UP: 24;
      readonly volumeDown: 25;
      readonly volume_down: 25;
      readonly VOLUME_DOWN: 25;
    }

    export interface RhinoEventsCompatModule {
      readonly keys: RhinoEventKeysCompatModule;
    }

    export type DisposableTaskDateInput = Date | number | string;

    export interface RhinoDisposableTaskOptions extends AutoJs6Node.BridgeCallOptions {
      readonly path?: string;
      readonly scriptPath?: string;
      readonly entry?: string;
      readonly date?: DisposableTaskDateInput;
      readonly time?: DisposableTaskDateInput;
      readonly delayMs?: number;
      readonly taskTimeoutMs?: number;
      readonly constraints?: workManager.WorkConstraints;
      readonly packaged?: boolean;
      readonly backend?: "embedded";
    }

    export interface RhinoTasksQueryOptions extends AutoJs6Node.BridgeCallOptions {}

    export interface RhinoTasksRemoveOptions extends AutoJs6Node.BridgeCallOptions {}

    export interface RhinoTasksCompatModule {
      addDisposableTask(options: RhinoDisposableTaskOptions): Promise<string>;
      addDailyTask(options?: unknown): Promise<never>;
      addWeeklyTask(options?: unknown): Promise<never>;
      addIntentTask(options?: unknown): Promise<never>;
      removeTask(id: string, options?: RhinoTasksRemoveOptions): Promise<boolean>;
      queryTasks(options?: RhinoTasksQueryOptions): Promise<readonly workManager.WorkTask[]>;
      queryIntentTasks(options?: unknown): Promise<never>;
      queryTimedTasks(options?: RhinoTasksQueryOptions): Promise<readonly workManager.WorkTask[]>;
      getIntentTask(id: string | number, options?: unknown): Promise<never>;
      getTimedTask(id: string | number, options?: RhinoTasksQueryOptions): Promise<workManager.WorkTask | null>;
      removeTimedTask(id: string | number, options?: RhinoTasksRemoveOptions): Promise<boolean>;
      removeIntentTask(id: string | number, options?: unknown): Promise<never>;
      updateTask(task: unknown): Promise<never>;
    }

    export type RhinoUiCompatModule = ui.UiModule;

    export interface RhinoCompatModule {
      readonly name: "autojs6:compat";
      readonly version: 1;
      readonly mode: "explicit-opt-in";
      readonly errorCodes: ErrorCodes;
      status(): Status;
      install(options?: InstallOptions): InstallResult;
      toast(message?: unknown, options?: ToastOptions): Promise<void>;
      toastLog(...args: readonly unknown[]): Promise<void>;
      sleep(millisMin: number | string, millisMax?: number | string): Promise<void>;
      setClip(text?: unknown, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      getClip(options?: AutoJs6Node.BridgeCallOptions): Promise<string>;
      readonly files: files.FilesModule;
      readonly base64: base64.Base64Module;
      readonly colors: colors.ColorsModule;
      readonly fmt: formatter.FormatterModule;
      readonly formatter: formatter.FormatterModule;
      readonly cvt: converter.ConverterModule;
      readonly converter: converter.ConverterModule;
      readonly s13n: s13n.S13nModule;
      readonly mime: mime.MimeModule;
      readonly nanoid: nanoid.NanoidModule;
      readonly util: util.UtilModule;
      readonly opencc: opencc.OpenccModule;
      readonly pinyin: pinyin.PinyinModule;
      readonly pinyin4j: pinyin4j.Pinyin4jModule;
      readonly jsox: jsox.JsoxModule;
      readonly keys: RhinoEventKeysCompatModule;
      readonly events: RhinoEventsCompatModule;
      readonly storage: storage.StorageModule;
      readonly storages: storage.StorageModule;
      readonly database: database.DatabaseModule;
      readonly sqlite: sqlite.SQLiteModule;
      readonly console: consoleModule.ConsoleModule;
      readonly timers: timers.TimersModule;
      readonly setTimeout: timers.TimersModule["setTimeout"];
      readonly clearTimeout: timers.TimersModule["clearTimeout"];
      readonly setInterval: timers.TimersModule["setInterval"];
      readonly clearInterval: timers.TimersModule["clearInterval"];
      readonly selector: SelectorDslModule;
      text(value: string): RhinoSelectorHandle;
      desc(value: string): RhinoSelectorHandle;
      id(value: string): RhinoSelectorHandle;
      className(value: string): RhinoSelectorHandle;
      textContains(value: string): RhinoSelectorHandle;
      descContains(value: string): RhinoSelectorHandle;
      textMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      descMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      idMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      classNameMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      clickable(value?: boolean): RhinoSelectorHandle;
      enabled(value?: boolean): RhinoSelectorHandle;
      scrollable(value?: boolean): RhinoSelectorHandle;
      depth(value: number): RhinoSelectorHandle;
      boundsInside(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsInside(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
      boundsContains(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsContains(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
      description(value: string): RhinoSelectorHandle;
      descriptionContains(value: string): RhinoSelectorHandle;
      descriptionMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
      idContains(value: string): RhinoSelectorHandle;
      classNameContains(value: string): RhinoSelectorHandle;
      click(x: number, y: number, options?: SelectorOptions): Promise<boolean>;
      back(options?: SelectorOptions): Promise<boolean>;
      home(options?: SelectorOptions): Promise<boolean>;
      recentApps(options?: SelectorOptions): Promise<boolean>;
      recents(options?: SelectorOptions): Promise<boolean>;
      readonly app: app.AppModule;
      readonly device: device.DeviceModule;
      readonly shell: ShellCompatFunction;
      readonly images: RhinoImagesCompatModule;
      readonly ocr: RhinoOcrCompatFunction;
      readonly barcode: RhinoBarcodeCompatFunction;
      readonly qrcode: RhinoBarcodeCompatFunction;
      readonly media: mediaModule.MediaModule;
      readonly mediainfo: mediainfoModule.MediainfoModule;
      readonly recorder: recorderModule.RecorderModule;
      readonly ui: RhinoUiCompatModule;
      readonly tasks: RhinoTasksCompatModule;
      requestScreenCapture(options?: ScreenCaptureRequestOptions): Promise<boolean>;
      requestScreenCaptureAsync(options?: ScreenCaptureRequestOptions): Promise<boolean>;
      captureScreen(options?: image.CaptureScreenOptions): Promise<AutoJs6Node.ImageHandle>;
      captureScreen(path: string, options?: CaptureScreenToFileOptions): Promise<boolean>;
      unsupported(apiName?: string, reason?: string): RhinoCompatError;
      blockedByDesign(apiName?: string, reason?: string): RhinoCompatError;
      assertSupported(apiName?: string): never;
    }
  }

  const rhinoCompat: rhinoCompat.RhinoCompatModule;
  export = rhinoCompat;
}
