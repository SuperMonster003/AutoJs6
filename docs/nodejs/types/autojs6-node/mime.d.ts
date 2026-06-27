declare module "mime" {
  namespace mime {
    export interface JsMime {
      readonly raw: string;
      readonly type: string;
      readonly subtype: string;
      readonly mimeType: string;
      readonly mimeTypeRefined: string;
      readonly parameters: Readonly<Record<string, string>>;
      toString(): string;
      toStringReadable(): string;
    }

    export interface MimeModule {
      (value?: unknown): JsMime;
      readonly WILDCARD: "*/*";
      readonly TEXT_PLAIN: "text/plain";
      readonly APPLICATION_JSON: "application/json";
      readonly APPLICATION_OCTET_STREAM: "application/octet-stream";
      readonly IMAGE_PNG: "image/png";
      parse(value?: unknown): JsMime;
    }
  }

  const mime: mime.MimeModule;
  export = mime;
}
