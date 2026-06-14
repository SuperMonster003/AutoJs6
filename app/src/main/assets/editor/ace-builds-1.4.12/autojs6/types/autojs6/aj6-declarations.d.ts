declare type Func = (...args: any[]) => any;
declare type Func1<T, R> = (arg: T) => R;
declare type Func2<T1, T2, R> = (arg1: T1, arg2: T2) => R;
declare type Func3<T1, T2, T3, R> = (arg1: T1, arg2: T2, arg3: T3) => R;
declare type Func4<T1, T2, T3, T4, R> = (arg1: T1, arg2: T2, arg3: T3, arg4: T4) => R;
declare type Func5<T1, T2, T3, T4, T5, R> = (arg1: T1, arg2: T2, arg3: T3, arg4: T4, arg5: T5) => R;
declare type Func6<T1, T2, T3, T4, T5, T6, R> = (arg1: T1, arg2: T2, arg3: T3, arg4: T4, arg5: T5, arg6: T6) => R;
declare type Func7<T1, T2, T3, T4, T5, T6, T7, R> = (arg1: T1, arg2: T2, arg3: T3, arg4: T4, arg5: T5, arg6: T6, arg7: T7) => R;
declare type Tuple1<TItem> = [TItem];
declare type Tuple2<TItem> = [TItem, TItem];
declare type Tuple3<TItem> = [TItem, TItem, TItem];
declare type Tuple4<TItem> = [TItem, TItem, TItem, TItem];
declare type Tuple5<TItem> = [TItem, TItem, TItem, TItem, TItem];
declare type Tuple6<TItem> = [TItem, TItem, TItem, TItem, TItem, TItem];
declare type Tuple7<TItem> = [TItem, TItem, TItem, TItem, TItem, TItem, TItem];

/**
 * @pattern /^\d+(\.\d+)?%$/
 * @example
 * let integer = 23;
 * let fraction = 22.99;
 * let integer_string = '50%';
 * let integer_fraction = '16.9%';
 */
declare type Percentage$ = number | string;

declare type HexDigitString = NumDigitString | 'A' | 'B' | 'C' | 'D' | 'E' | 'F';

declare type NumDigitString = '0' | NumDigitNoZeroString;

declare type NumDigitNoZeroString = '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9';

declare type PercentString = `100%` | `${NumDigitString}%` |`${NumDigitNoZeroString}${NumDigitString}%` | string;
/**
 * @pattern /^#([0-9A-Z]{2}){3,4}$/
 * @example
 * let string_rgb = "#BB91FF";
 * let string_argb = "#FFBB91FF";
 * let number_dec = -4484609;
 * let number_hex_6 = 0xBB91FF;
 * let number_hex_8 = 0xFFBB91FF;
 */
declare type Color$ = number | string;

declare type X = number;
declare type Y = number;
declare type Width = number;
declare type Height = number;
declare type Left = number;
declare type Top = number;
declare type Right = number;
declare type Bottom = number;
declare type Side = number;

declare type StandardCharset = StandardCharsets | 'US_ASCII' | 'ISO_8859_1' | 'UTF_8' | 'UTF_16BE' | 'UTF_16LE' | 'UTF_16' | 'us-ascii' | 'iso-8859-1' | 'utf-8' | 'utf-16be' | 'utf-16le' | 'utf-16';

declare type PointLike = { x: number; y: number; };