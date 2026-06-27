declare module "base64" {
  namespace base64 {
    export type BinaryInput =
      | string
      | ArrayBuffer
      | Uint8Array
      | Int8Array
      | Uint8ClampedArray
      | readonly number[];

    export interface Base64Module {
      encode(value: BinaryInput, encoding?: string | null): string;
      decode(value: string, encoding?: string | null): string;
    }
  }

  const base64: base64.Base64Module;
  export = base64;
}
