declare module "nanoid" {
  namespace nanoid {
    export interface NanoidModule {
      (size?: number | string | null): string;
      readonly urlAlphabet: string;
      customAlphabet(alphabet: string, defaultSize?: number | string | null): (size?: number | string | null) => string;
    }
  }

  const nanoid: nanoid.NanoidModule;
  export = nanoid;
}
