declare module "converter" {
  namespace converter {
    export interface BytesOptions {
      readonly fromUnit?: string;
      readonly toUnit?: string;
      readonly fractionDigits?: number;
      readonly autoCarryThreshold?: number;
      readonly strict?: boolean;
    }

    export interface BytesConverter {
      (source: number | string, toUnit?: string | number | BytesOptions | null, options?: string | number | BytesOptions | null): number;
      readonly UNITS: string;
      readonly AUTO: "AUTO";
      readonly IEC_DIV: 1024;
      readonly SI_DIV: 1000;
      strict(source: number | string, toUnit?: string | number | BytesOptions | null, options?: string | number | BytesOptions | null): number;
      loose(source: number | string, toUnit?: string | number | BytesOptions | null, options?: string | number | BytesOptions | null): number;
    }

    export interface ConverterModule {
      readonly bytes: BytesConverter;
    }
  }

  const converter: converter.ConverterModule;
  export = converter;
}

declare module "cvt" {
  import converter = require("converter");
  export = converter;
}
