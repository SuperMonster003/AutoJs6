declare module "mediainfo" {
  namespace mediainfo {
    export type StreamKind = "general" | "video" | "audio" | "text" | "other" | "image" | "menu";

    export interface ReadOptions extends AutoJs6Node.BridgeCallOptions {
      readonly includeInform?: boolean;
      readonly includeSections?: boolean;
    }

    export interface SectionMap {
      readonly [section: string]: readonly Readonly<Record<string, string>>[];
    }

    export interface Snapshot {
      readonly schema: "autojs6-node-mediainfo-snapshot-v1";
      readonly path: string;
      readonly fileName: string;
      readonly sizeBytes: number;
      readonly inform: string;
      readonly sections: SectionMap;
    }

    export interface MediainfoModule {
      (path: string, options?: ReadOptions): Promise<Snapshot>;
      read(path: string, options?: ReadOptions): Promise<Snapshot>;
      get(path: string, streamKind: StreamKind, parameter?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<string>;
    }
  }

  const mediainfo: mediainfo.MediainfoModule;
  export = mediainfo;
}
