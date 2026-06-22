declare module "util" {
  namespace util {
    export interface InspectOptions {
      readonly showHidden?: boolean;
      readonly depth?: number | null;
      readonly colors?: boolean;
      readonly customInspect?: boolean;
      readonly compact?: boolean | number;
      readonly breakLength?: number;
      readonly sorted?: boolean | ((a: string, b: string) => number);
    }

    export interface UtilModule {
      format(format?: unknown, ...args: readonly unknown[]): string;
      inspect(object: unknown, options?: InspectOptions | boolean | null): string;
      inspect(object: unknown, showHidden?: boolean, depth?: number | null, colors?: boolean): string;
    }
  }

  const util: util.UtilModule;
  export = util;
}

declare module "node:util" {
  import util = require("util");
  export = util;
}
