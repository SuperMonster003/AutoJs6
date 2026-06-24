declare module "jsox" {
  namespace jsox {
    export type ModuleName = "Mathx" | "mathx" | "Math";
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

    export interface Status {
      readonly schema: "autojs6-node-jsox-v1";
      readonly task: "P13-27";
      readonly modules: readonly "Mathx"[];
      readonly nodeModules: readonly ("jsox" | "jsox.mathx")[];
      readonly safeDefault: true;
      readonly defaultGlobalMutation: false;
      readonly rawAndroidObjects: false;
    }

    export interface JsoxModule {
      <T extends object>(target: T, ...modules: readonly ModuleNameInput[]): T;
      readonly mathx: MathxModule;
      readonly Mathx: MathxModule;
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
