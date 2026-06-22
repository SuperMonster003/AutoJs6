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
  import files = require("files");
  import base64 = require("base64");
  import colors = require("colors");
  import formatter = require("formatter");
  import converter = require("converter");
  import mime = require("mime");
  import nanoid = require("nanoid");
  import util = require("util");
  import opencc = require("opencc");
  import pinyin = require("pinyin");
  import pinyin4j = require("pinyin4j");

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
      depth(value: number): RhinoSelectorHandle;
      boundsInside(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsInside(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
      boundsContains(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsContains(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
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
      depth(value: number): RhinoSelectorHandle;
      boundsInside(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsInside(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
      boundsContains(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsContains(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
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
      execFile(path: string, args?: readonly string[], options?: shellModule.ShellExecOptions): Promise<shellModule.ShellResult>;
      checkAccess(options?: shellModule.ShellAccessOptions): shellModule.ShellAccessReport;
      setDefaultTimeout(ms: number): number;
    }

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
      captureScreen(options?: image.CaptureScreenOptions): Promise<AutoJs6Node.ImageHandle>;
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
      readonly mime: mime.MimeModule;
      readonly nanoid: nanoid.NanoidModule;
      readonly util: util.UtilModule;
      readonly opencc: opencc.OpenccModule;
      readonly pinyin: pinyin.PinyinModule;
      readonly pinyin4j: pinyin4j.Pinyin4jModule;
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
      depth(value: number): RhinoSelectorHandle;
      boundsInside(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsInside(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
      boundsContains(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
      boundsContains(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
      click(x: number, y: number, options?: SelectorOptions): Promise<boolean>;
      back(options?: SelectorOptions): Promise<boolean>;
      home(options?: SelectorOptions): Promise<boolean>;
      recentApps(options?: SelectorOptions): Promise<boolean>;
      recents(options?: SelectorOptions): Promise<boolean>;
      readonly app: app.AppModule;
      readonly device: device.DeviceModule;
      readonly shell: ShellCompatFunction;
      readonly images: RhinoImagesCompatModule;
      unsupported(apiName?: string, reason?: string): RhinoCompatError;
      blockedByDesign(apiName?: string, reason?: string): RhinoCompatError;
      assertSupported(apiName?: string): never;
    }
  }

  const rhinoCompat: rhinoCompat.RhinoCompatModule;
  export = rhinoCompat;
}
