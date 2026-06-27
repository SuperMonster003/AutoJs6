declare module "formatter" {
  namespace formatter {
    export interface BytesOptions {
      readonly fromUnit?: string;
      readonly toUnit?: string;
      readonly useIecIdentifier?: boolean;
      readonly useSpace?: boolean;
      readonly fractionDigits?: number;
      readonly trimTrailingZero?: boolean;
      readonly autoCarryThreshold?: number;
      readonly strict?: boolean;
    }

    export interface BytesFormatter {
      (source: number | string, toUnit?: string | number | boolean | BytesOptions | null, options?: string | number | boolean | BytesOptions | null): string;
      readonly UNITS: string;
      readonly AUTO: "AUTO";
      readonly IEC_DIV: 1024;
      readonly SI_DIV: 1000;
      strict(source: number | string, toUnit?: string | number | BytesOptions | null, options?: string | number | BytesOptions | null): string;
      loose(source: number | string, toUnit?: string | number | boolean | BytesOptions | null, options?: string | number | boolean | BytesOptions | null): string;
    }

    export interface FormatterModule {
      readonly bytes: BytesFormatter;
    }
  }

  const formatter: formatter.FormatterModule;
  export = formatter;
}

declare module "fmt" {
  import formatter = require("formatter");
  export = formatter;
}
