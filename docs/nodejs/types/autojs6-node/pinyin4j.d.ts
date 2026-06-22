declare module "pinyin4j" {
  namespace pinyin4j {
    export interface Pinyin4jOptions {
      readonly separator?: string;
      readonly sep?: string;
      readonly case?: "LOWERCASE" | "LOW" | "L" | "0" | "UPPERCASE" | "UP" | "U" | "1";
      readonly caseType?: Pinyin4jOptions["case"];
      readonly tone?:
        | "WITH_TONE_NUMBER"
        | "WITH_NUMBER"
        | "NUMBER"
        | "NUM"
        | "WITHOUT_TONE"
        | "NO_TONE"
        | "NO"
        | "FALSE"
        | "0"
        | "WITH_TONE_MARK"
        | "WITH_MARK"
        | "MARK"
        | "TRUE"
        | "1";
      readonly toneType?: Pinyin4jOptions["tone"];
      readonly v?: string;
      readonly vChar?: string;
      readonly vCharType?: string;
    }

    export interface Pinyin4jModule {
      (value: unknown, options?: Pinyin4jOptions | string | null): string;
      of(value: unknown, options?: Pinyin4jOptions | string | null): string;
      as(value: unknown): string;
    }
  }

  const pinyin4j: pinyin4j.Pinyin4jModule;
  export = pinyin4j;
}
