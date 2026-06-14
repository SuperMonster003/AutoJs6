// Type definitions for AutoJs6 internal module zip
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.8.4
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/zip/Zip.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/zip/ZipNativeObject.kt
 */

declare namespace Internal {

    interface Zip {
        (zipPath: string): Zip.ZipFileHandle;

        open(zipPath: string, options?: Zip.OpenOptions): Zip.ZipFileHandle;

        zipFile(filePath: string, destZipPath: Zip.DestinationPath, options?: Zip.ZipOptions): Zip.ZipFileHandle;
        zipFile(filePath: string, options: Zip.ZipOptions): Zip.ZipFileHandle;

        zipDir(dirPath: string, destZipPath: Zip.DestinationPath, options?: Zip.ZipOptions): Zip.ZipFileHandle;
        zipDir(dirPath: string, options: Zip.ZipOptions): Zip.ZipFileHandle;

        zipFiles(filePathList: Zip.FilePathList, destZipPath: Zip.DestinationPath, options?: Zip.ZipOptions): Zip.ZipFileHandle;
        zipFiles(filePathList: Zip.FilePathList, options: Zip.ZipOptions): Zip.ZipFileHandle;

        unzip(zipPath: string, destPath: Zip.DestinationPath, options?: Zip.UnzipOptions): Zip.ZipFileHandle;
        unzip(zipPath: string, options: Zip.UnzipOptions): Zip.ZipFileHandle;
    }

}

declare namespace Zip {

    type AesKeyStrength = 128 | 192 | 256 | number | string | net.lingala.zip4j.model.enums.AesKeyStrength;
    type AesVersion = 1 | 2 | number | string | net.lingala.zip4j.model.enums.AesVersion;
    type CompressionLevel = number | string | net.lingala.zip4j.model.enums.CompressionLevel;
    type CompressionMethod = number | string | net.lingala.zip4j.model.enums.CompressionMethod;
    type EncryptionMethod = -1 | 0 | 1 | 99 | number | string | net.lingala.zip4j.model.enums.EncryptionMethod;
    type SymbolicLinkAction = string | net.lingala.zip4j.model.ZipParameters.SymbolicLinkAction;
    type DestinationPath = string | null | undefined;
    type FilePathList = string[] | java.lang.Iterable<string>;

    interface OpenOptions extends ZipOptions, UnzipOptions {
        /* Shared open options. */
    }

    interface ZipOptions {
        aesKeyStrength?: AesKeyStrength;
        aesVersion?: AesVersion;
        compressionLevel?: CompressionLevel;
        compressionMethod?: CompressionMethod;
        encryptionMethod?: EncryptionMethod;
        defaultFolderPath?: string;
        entryCRC?: number;
        entrySize?: number;
        excludeFileFilter?: net.lingala.zip4j.model.ExcludeFileFilter | ((file: java.io.File) => boolean);
        fileComment?: string;
        comment?: string;
        fileNameInZip?: string;
        isIncludeRootFolder?: boolean;
        includeRootFolder?: boolean;
        isOverrideExistingFilesInZip?: boolean;
        overrideExistingFilesInZip?: boolean;
        isReadHiddenFiles?: boolean;
        readHiddenFiles?: boolean;
        isReadHiddenFolders?: boolean;
        readHiddenFolders?: boolean;
        isUnixMode?: boolean;
        unixMode?: boolean;
        isWriteExtendedLocalFileHeader?: boolean;
        writeExtendedLocalFileHeader?: boolean;
        lastModifiedFileTime?: number;
        rootFolderNameInZip?: string;
        symbolicLinkAction?: SymbolicLinkAction;
        isEncryptFiles?: boolean;
        encryptFiles?: boolean;
        password?: string;
    }

    interface UnzipOptions {
        isExtractSymbolicLinks?: boolean;
        password?: string;
        /** @deprecated Unsupported since Zip4j 2.x. Passing it throws at runtime. */
        isIgnoreDateTimeAttributes?: never;
        /** @deprecated Unsupported since Zip4j 2.x. Passing it throws at runtime. */
        ignoreDateTimeAttributes?: never;
    }

    interface ZipFileHandle {
        readonly name: string;
        readonly path: string;
        readonly zipFile: net.lingala.zip4j.ZipFile;
        readonly options: org.mozilla.javascript.ScriptableObject;
        readonly zipParameters: net.lingala.zip4j.model.ZipParameters;
        readonly unzipParameters: net.lingala.zip4j.model.UnzipParameters;

        toString(): string;

        addFile(filePath: string, options?: ZipOptions): void;

        addFiles(filePathList: FilePathList, options?: ZipOptions): void;

        addFolder(dirPath: string, options?: ZipOptions): void;

        extractAll(destPath: string, options?: UnzipOptions): void;

        extractFile(fileName: string, destPath: string, options?: UnzipOptions, newFileName?: string): void;

        setPassword(password: string): void;

        getFileHeader(fileName: string): net.lingala.zip4j.model.FileHeader;

        getFileHeaders(): net.lingala.zip4j.model.FileHeader[];

        isEncrypted(): boolean;

        removeFile(fileName: string): void;

        isValidZipFile(): boolean;

        getPath(): string;

        getZipFile(): net.lingala.zip4j.ZipFile;
    }

}
