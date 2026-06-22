declare module "pinyin" {
  namespace pinyin {
    export type PinyinRow = string[];
    export type PinyinResult = PinyinRow[] & { compact(): string };

    export interface PinyinOptions {
      readonly style?: number | "NORMAL" | "TONE" | "TONE2" | "TO3NE" | "INITIALS" | "FIRST_LETTER";
      readonly mode?: number | "NORMAL" | "SURNAME" | "PLACE_NAME" | "PLACENAME";
      readonly heteronym?: boolean;
      readonly group?: boolean;
      readonly segment?: boolean;
    }

    export interface PinyinModule {
      (value: unknown, options?: PinyinOptions | null): PinyinResult;
      readonly STYLE_NORMAL: 0;
      readonly STYLE_TONE: 1;
      readonly STYLE_TONE2: 2;
      readonly STYLE_INITIALS: 3;
      readonly STYLE_FIRST_LETTER: 4;
      readonly STYLE_TO3NE: 5;
      readonly MODE_NORMAL: 0;
      readonly MODE_SURNAME: 1;
      readonly MODE_PLACENAME: 2;
      readonly MODE_PLACE_NAME: 2;
      convert(value: unknown, options?: PinyinOptions | null): PinyinResult;
      simple(value: unknown, enableNumericTone?: boolean, enableSegment?: boolean): string;
      compact(rows: readonly (readonly string[])[]): string;
      compare(left: unknown, right: unknown): number;
      fromCodePoint(codePoint: number): string | null;
      fromPhrase(phrase: unknown): string[][];
    }
  }

  const pinyin: pinyin.PinyinModule;
  export = pinyin;
}
