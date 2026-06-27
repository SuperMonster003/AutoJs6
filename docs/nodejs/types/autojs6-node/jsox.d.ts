declare module "jsox" {
  namespace jsox {
    export type ModuleName =
      | "Mathx"
      | "mathx"
      | "Math"
      | "Arrayx"
      | "arrayx"
      | "Array"
      | "Numberx"
      | "numberx"
      | "Number";
    export type ModuleNameInput = ModuleName | readonly ModuleNameInput[];

    export interface PointLike {
      readonly x: number;
      readonly y: number;
    }

    export type PointInput = readonly [number, number] | PointLike;

    export interface MathxModule {
      randInt(...values: readonly unknown[]): number;
      randomInt(...values: readonly unknown[]): number;
      randFloat(...values: readonly unknown[]): number;
      randomFloat(...values: readonly unknown[]): number;
      random(...values: readonly unknown[]): number;
      sum(...values: readonly unknown[]): number;
      mean(...values: readonly unknown[]): number;
      avg(...values: readonly unknown[]): number;
      median(...values: readonly unknown[]): number;
      "var"(...values: readonly unknown[]): number;
      std(...values: readonly unknown[]): number;
      cv(...values: readonly unknown[]): number;
      mode(...values: readonly unknown[]): number;
      dist(a: PointInput, b: PointInput, fraction?: number): number;
      logMn(base: number, antilogarithm: number, fraction?: number): number;
      floorLog(base: number, antilogarithm: number): number;
      ceilLog(base: number, antilogarithm: number): number;
      roundLog(base: number, antilogarithm: number): number;
      floorPow(base: number, antilogarithm: number): number;
      ceilPow(base: number, antilogarithm: number): number;
      roundPow(base: number, antilogarithm: number): number;
      max(...values: readonly unknown[]): number;
      maxi(...values: readonly unknown[]): number;
      min(...values: readonly unknown[]): number;
      mini(...values: readonly unknown[]): number;
    }

    export interface ArrayxModule {
      ensureArray(...values: readonly unknown[]): void;
      distinct<T>(arr: readonly T[]): T[];
      distinctBy<T>(arr: readonly T[], selector: (value: T) => unknown): T[];
      union<T>(...arrays: readonly (readonly T[])[]): T[];
      intersect<T>(...arrays: readonly (readonly T[])[]): T[];
      subtract<T>(left: readonly T[], right: readonly T[]): T[];
      differ<T>(left: readonly T[], right: readonly T[]): T[];
      sortBy<T>(arr: T[], selector: (value: T) => unknown): T[];
      sortByDescending<T>(arr: T[], selector: (value: T) => unknown): T[];
      sortDescending<T>(arr: T[]): T[];
      sorted<T>(arr: readonly T[]): T[];
      sortedBy<T>(arr: readonly T[], selector: (value: T) => unknown): T[];
      sortedByDescending<T>(arr: readonly T[], selector: (value: T) => unknown): T[];
      sortedDescending<T>(arr: readonly T[]): T[];
      shuffle<T>(arr: T[]): T[];
    }

    export interface NumberxModule {
      readonly ICU: 996;
      ensureNumber(...values: readonly unknown[]): void;
      ensureNumberLike(value: unknown, allowNaN?: boolean): number;
      ensureNumbersLike(values: readonly unknown[], allowNaN?: boolean, containsNaN?: boolean): number[];
      check(...values: readonly unknown[]): boolean;
      clamp(num: unknown, ...clamps: readonly unknown[]): number;
      clampTo(num: unknown, range: readonly unknown[], cycle?: unknown): number;
      toFixedNum(num: unknown, fraction?: unknown): number;
      padStart(num: unknown, targetLength: unknown, pad?: unknown): string;
      padEnd(num: unknown, targetLength: unknown, pad?: unknown): string;
      parseFloat(value: unknown, radix?: unknown): number;
      parsePercent(value: unknown): number;
      parseRatio(value: unknown): number;
      parseAny(value: unknown): number;
    }

    export interface Status {
      readonly schema: "autojs6-node-jsox-v1";
      readonly task: "P13-27";
      readonly modules: readonly ("Mathx" | "Numberx" | "Arrayx")[];
      readonly nodeModules: readonly ("jsox" | "jsox.mathx" | "jsox.arrayx" | "jsox.numberx")[];
      readonly safeDefault: true;
      readonly defaultGlobalMutation: false;
      readonly rawAndroidObjects: false;
    }

    export interface JsoxModule {
      <T extends object>(target: T, ...modules: readonly ModuleNameInput[]): T;
      readonly mathx: MathxModule;
      readonly Mathx: MathxModule;
      readonly arrayx: ArrayxModule;
      readonly Arrayx: ArrayxModule;
      readonly numberx: NumberxModule;
      readonly Numberx: NumberxModule;
      extend<T extends object>(target: T, ...modules: readonly ModuleNameInput[]): T;
      extendAll<T extends object>(target: T): T;
      status(): Status;
    }
  }

  const jsox: jsox.JsoxModule;
  export = jsox;
}

declare module "jsox.mathx" {
  import jsox = require("jsox");

  const mathx: jsox.MathxModule;
  export = mathx;
}

declare module "jsox.arrayx" {
  import jsox = require("jsox");

  const arrayx: jsox.ArrayxModule;
  export = arrayx;
}

declare module "jsox.numberx" {
  import jsox = require("jsox");

  const numberx: jsox.NumberxModule;
  export = numberx;
}
