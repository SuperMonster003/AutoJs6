declare module "s13n" {
  namespace s13n {
    export interface S13nModule {
      bytes(source: number | string, fromUnit?: string): number;
      color(value?: unknown): never;
      point(value?: unknown, other?: unknown): never;
      throwable(value?: unknown): never;
      time(value?: unknown, fromUnit?: unknown, toUnit?: unknown): never;
    }
  }

  const s13n: s13n.S13nModule;
  export = s13n;
}
