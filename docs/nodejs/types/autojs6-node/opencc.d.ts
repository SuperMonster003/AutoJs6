declare module "opencc" {
  namespace opencc {
    export type ConversionType =
      | "hk2s"
      | "hk2t"
      | "jp2t"
      | "s2hk"
      | "s2t"
      | "s2tw"
      | "s2twp"
      | "t2hk"
      | "t2s"
      | "t2tw"
      | "t2jp"
      | "tw2s"
      | "tw2t"
      | "tw2sp"
      | "s2twi"
      | "twi2s"
      | "s2jp"
      | "t2twi"
      | "hk2tw"
      | "hk2twi"
      | "hk2jp"
      | "tw2hk"
      | "tw2twi"
      | "tw2jp"
      | "twi2t"
      | "twi2hk"
      | "twi2tw"
      | "twi2jp"
      | "jp2s"
      | "jp2hk"
      | "jp2tw"
      | "jp2twi";

    export interface OpenccModule {
      (value: unknown, type: ConversionType | string): never;
      convert(value: unknown, type: ConversionType | string): never;
      hk2s(value: unknown): never;
      hk2t(value: unknown): never;
      jp2t(value: unknown): never;
      s2hk(value: unknown): never;
      s2t(value: unknown): never;
      s2tw(value: unknown): never;
      s2twp(value: unknown): never;
      t2hk(value: unknown): never;
      t2s(value: unknown): never;
      t2tw(value: unknown): never;
      t2jp(value: unknown): never;
      tw2s(value: unknown): never;
      tw2t(value: unknown): never;
      tw2sp(value: unknown): never;
      s2twi(value: unknown): never;
      twi2s(value: unknown): never;
      s2jp(value: unknown): never;
      t2twi(value: unknown): never;
      hk2tw(value: unknown): never;
      hk2twi(value: unknown): never;
      hk2jp(value: unknown): never;
      tw2hk(value: unknown): never;
      tw2twi(value: unknown): never;
      tw2jp(value: unknown): never;
      twi2t(value: unknown): never;
      twi2hk(value: unknown): never;
      twi2tw(value: unknown): never;
      twi2jp(value: unknown): never;
      jp2s(value: unknown): never;
      jp2hk(value: unknown): never;
      jp2tw(value: unknown): never;
      jp2twi(value: unknown): never;
    }
  }

  const opencc: opencc.OpenccModule;
  export = opencc;
}
