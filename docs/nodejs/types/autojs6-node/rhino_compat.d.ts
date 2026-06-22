declare module "autojs6:compat" {
  import storage = require("storage");
  import database = require("database");
  import sqlite = require("sqlite");
  import consoleModule = require("console");
  import timers = require("timers");
  import accessibility = require("accessibility");
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
      unsupported(apiName?: string, reason?: string): RhinoCompatError;
      blockedByDesign(apiName?: string, reason?: string): RhinoCompatError;
      assertSupported(apiName?: string): never;
    }
  }

  const rhinoCompat: rhinoCompat.RhinoCompatModule;
  export = rhinoCompat;
}
