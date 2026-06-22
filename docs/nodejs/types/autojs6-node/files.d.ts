declare module "files" {
  namespace files {
    export type TextEncoding = string;
    export type ListDirFilter = (name: string) => boolean | null | undefined;

    export interface FilesModule {
      path(path?: string | null): string | null;
      join(parent: string, ...children: string[]): string;
      read(path?: string | null, encoding?: TextEncoding | null): string;
      write(path: string, text: unknown, encoding?: TextEncoding | null): void;
      append(path: string, text: unknown, encoding?: TextEncoding | null): void;
      exists(path?: string | null): boolean;
      listDir(path?: string | null, filter?: ListDirFilter | null): string[];
      remove(path?: string | null): boolean;
      copy(pathFrom: string, pathTo: string): boolean;
      move(path: string, newPath: string): boolean;
      ensureDir(path: string): boolean;
      isFile(path?: string | null): boolean;
      isDir(path?: string | null): boolean;
      getName(filePath: string): string;
      getExtension(fileName: string): string;
      open(path?: string | null, mode?: string | null, encoding?: TextEncoding | null, bufferSize?: number | null): never;
      toFile(path: string): never;
    }
  }

  const files: files.FilesModule;
  export = files;
}
