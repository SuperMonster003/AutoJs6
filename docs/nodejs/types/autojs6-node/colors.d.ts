declare module "colors" {
  namespace colors {
    export type ColorInput =
      | number
      | bigint
      | string
      | {
          readonly color?: number;
          readonly a?: number;
          readonly r?: number;
          readonly g?: number;
          readonly b?: number;
        };

    export interface ComponentOptions {
      readonly max?: 1 | 255;
    }

    export interface AlphaStringOptions {
      readonly keepTrailingZeroForFullAlpha?: boolean;
    }

    export interface ColorsModule {
      readonly all: Readonly<Record<string, number>>;
      readonly themeColor: never;
      toInt(color: ColorInput): number;
      toHex(color: ColorInput, alphaOrLength?: boolean | number | null): string;
      toFullHex(color: ColorInput): string;
      toString(color: ColorInput, alphaOrLength?: boolean | number | null): string;
      alpha(color: ColorInput, options?: ComponentOptions | null): number;
      getAlpha(color: ColorInput, options?: ComponentOptions | null): number;
      alphaDouble(color: ColorInput): number;
      getAlphaDouble(color: ColorInput): number;
      setAlpha(color: ColorInput, alpha: number): number;
      setAlphaRelative(color: ColorInput, percentage: number): number;
      removeAlpha(color: ColorInput): number;
      red(color: ColorInput, options?: ComponentOptions | null): number;
      getRed(color: ColorInput, options?: ComponentOptions | null): number;
      redDouble(color: ColorInput): number;
      getRedDouble(color: ColorInput): number;
      setRed(color: ColorInput, red: number): number;
      setRedRelative(color: ColorInput, percentage: number): number;
      removeRed(color: ColorInput): number;
      green(color: ColorInput, options?: ComponentOptions | null): number;
      getGreen(color: ColorInput, options?: ComponentOptions | null): number;
      greenDouble(color: ColorInput): number;
      getGreenDouble(color: ColorInput): number;
      setGreen(color: ColorInput, green: number): number;
      setGreenRelative(color: ColorInput, percentage: number): number;
      removeGreen(color: ColorInput): number;
      blue(color: ColorInput, options?: ComponentOptions | null): number;
      getBlue(color: ColorInput, options?: ComponentOptions | null): number;
      blueDouble(color: ColorInput): number;
      getBlueDouble(color: ColorInput): number;
      setBlue(color: ColorInput, blue: number): number;
      setBlueRelative(color: ColorInput, percentage: number): number;
      removeBlue(color: ColorInput): number;
      argb(alpha: number, red: number, green: number, blue: number): number;
      rgb(red: number, green: number, blue: number): number;
      rgba(red: number, green: number, blue: number, alpha?: number): number;
      hsv(hue: number, saturation: number, value: number): number;
      hsva(hue: number, saturation: number, value: number, alpha?: number): number;
      hsl(hue: number, saturation: number, lightness: number): number;
      hsla(hue: number, saturation: number, lightness: number, alpha?: number): number;
      toRgb(color: ColorInput): readonly [number, number, number];
      toRgba(color: ColorInput): readonly [number, number, number, number];
      toArgb(color: ColorInput): readonly [number, number, number, number];
      toHsv(color: ColorInput): readonly [number, number, number];
      toHsva(color: ColorInput): readonly [number, number, number, number];
      toHsl(color: ColorInput): readonly [number, number, number];
      toHsla(color: ColorInput): readonly [number, number, number, number];
      toRgbString(color: ColorInput): string;
      toRgbaString(color: ColorInput, options?: AlphaStringOptions | null): string;
      toArgbString(color: ColorInput, options?: AlphaStringOptions | null): string;
      toHsvString(color: ColorInput): string;
      toHsvaString(color: ColorInput): string;
      toHslString(color: ColorInput): string;
      toHslaString(color: ColorInput): string;
      isEqual(left: ColorInput, right: ColorInput): boolean;
      isSimilar(left: ColorInput, right: ColorInput, threshold?: number | null): boolean;
      luminance(color: ColorInput): number;
      build(alpha?: number, red?: number, green?: number, blue?: number): number;
      summary(color: ColorInput): string;
      toColorStateList(...colors: readonly ColorInput[]): never;
      setPaintColor(paint: unknown, color: ColorInput): never;
    }
  }

  const colors: colors.ColorsModule;
  export = colors;
}
