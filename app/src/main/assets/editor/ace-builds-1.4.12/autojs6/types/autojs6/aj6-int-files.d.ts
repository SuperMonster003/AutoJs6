// Type definitions for AutoJs6 internal module files
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/files/Files.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/Files.kt
 */

declare namespace Internal {

    class Files extends org.autojs.autojs.runtime.api.Files {

        /**
         * @Overrides for JSDoc or more specific types
         */

        constructor(runtime: ScriptRuntime);

        /**
         * @example
         * console.log(files.join("a", "b", "c", "pic.png")); // "a/b/c/pic.png"
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * const fs = __runtime__.files;
         * const files = Object.create(fs);
         * files.join = function (base) {
         *     let paths = Array.prototype.slice.call(arguments, 1);
         *     return fs.join(base, paths);
         * };
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static String join(String parent, String... child) {
         *     return PFiles.join(parent, child);
         * }
         *
         * @see org.autojs.autojs.runtime.api.Files.join
         */
        join(parent: string, ...child: string[]): string;

        static join(parent: string, ...child: string[]): string;

        /**
         * @param path
         * @param text
         * @param [encoding='utf-8']
         * @example
         * files.append("./1.txt", "hello");
         * files.append("./1.txt", "\n" + "world");
         * app.viewFile("./1.txt");
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void append(String path, String text) {
         *     PFiles.append(path(path), text);
         * }
         * public void append(String path, String text, String encoding) {
         *     PFiles.append(path(path), text, encoding);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static void append(String path, String text) {
         *     create(path);
         *     try {
         *         write(new FileOutputStream(path, true), text);
         *     } catch (FileNotFoundException e) {
         *         throw new UncheckedIOException(e);
         *     }
         * }
         * public static void append(String path, String text, String encoding) {
         *     create(path);
         *     try {
         *         write(new FileOutputStream(path, true), text, encoding);
         *     } catch (FileNotFoundException e) {
         *         throw new UncheckedIOException(e);
         *     }
         * }
         */
        append(path: string, text: string, encoding?: StandardCharsets): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void appendBytes(String path, byte[] bytes) {
         *     PFiles.appendBytes(path(path), bytes);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static void appendBytes(String path, byte[] bytes) {
         *     create(path);
         *     try {
         *         writeBytes(new FileOutputStream(path, true), bytes);
         *     } catch (IOException e) {
         *         throw new UncheckedIOException(e);
         *     }
         * }
         */
        appendBytes(path: string, bytes: number[]): void;

        /**
         * @example
         * files.copy("./1.txt", "./Backup/1.txt");
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean copy(String pathFrom, String pathTo) {
         *     return PFiles.copy(path(pathFrom), path(pathTo));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean copy(String pathFrom, String pathTo) {
         *     try {
         *         return copyStream(new FileInputStream(pathFrom), pathTo);
         *     } catch (FileNotFoundException e) {
         *         e.printStackTrace();
         *         return false;
         *     }
         * }
         */
        copy(pathFrom: string, pathTo: string): boolean;

        /**
         * @example
         * files.create("/sdcard/new_folder/");
         * @example
         * files.create("/sdcard/1.txt");
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean create(String path) {
         *     return PFiles.create(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean create(String path) {
         *     File f = new File(path);
         *     if (path.endsWith(File.separator)) {
         *         return f.mkdir();
         *     } else {
         *         try {
         *             return f.createNewFile();
         *         } catch (IOException e) {
         *             return false;
         *         }
         *     }
         * }
         */
        create(path: string): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean createIfNotExists(String path) {
         *     return PFiles.createIfNotExists(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean createIfNotExists(String path) {
         *     ensureDir(path);
         *     File file = new File(path);
         *     if (!file.exists()) {
         *         try {
         *             return file.createNewFile();
         *         } catch (IOException e) {
         *             e.printStackTrace();
         *         }
         *     }
         *     return false;
         * }
         */
        createIfNotExists(path: string): boolean;

        /**
         * @example
         * files.createWithDirs("/sdcard/new_folder/a/b/c/d");
         * @example
         * files.createWithDirs("/sdcard/x/y/z/1.txt");
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean createWithDirs(String path) {
         *     return PFiles.createWithDirs(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean createWithDirs(String path) {
         *     return createIfNotExists(path);
         * }
         */
        createWithDirs(path: string): boolean;

        /**
         * @example
         * files.cwd(); // e.g. '/storage/emulated/0/Scripts/Sample'
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String cwd() {
         *     return mRuntime.engines.myEngine().cwd();
         * }
         */
        cwd(): string;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean ensureDir(String path) {
         *     return PFiles.ensureDir(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean ensureDir(String path) {
         *     int i = path.lastIndexOf('\\');
         *     if (i < 0)
         *         i = path.lastIndexOf('/');
         *     if (i >= 0) {
         *         String folder = path.substring(0, i);
         *         File file = new File(folder);
         *         if (file.exists())
         *             return true;
         *         return file.mkdirs();
         *     } else {
         *         return false;
         *     }
         * }
         */
        ensureDir(path: string): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean exists(String path) {
         *     return PFiles.exists(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean exists(String path) {
         *     return new File(path).exists();
         * }
         */
        exists(path: string): boolean;

        /**
         * @example
         * files.getExtension("/sdcard/1.txt"); // "txt"
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String getExtension(String fileName) {
         *     return PFiles.getExtension(fileName);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static String getExtension(String fileName) {
         *     int i = fileName.lastIndexOf('.');
         *     if (i < 0 || i + 1 >= fileName.length() - 1)
         *         return "";
         *     return fileName.substring(i + 1);
         * }
         */
        getExtension(fileName: string): string;

        /**
         * @example
         * files.getHumanReadableSize(0); // '0 B'
         * files.getHumanReadableSize(10); // '10 B'
         * files.getHumanReadableSize(1000); // '1000 B'
         * files.getHumanReadableSize(1024); // '1.0 KB'
         * files.getHumanReadableSize(2000); // '2.0 KB' (approximation)
         * files.getHumanReadableSize(2048); // '2.0 KB'
         * files.getHumanReadableSize(2000000); // '1.9 MB' (approximation)
         * files.getHumanReadableSize(2097152); // '2.0 MB'
         * files.getHumanReadableSize(3 * Math.pow(1024, 3)); // '3.0 GB'
         * files.getHumanReadableSize(4 * Math.pow(1024, 4)); // '4.0 TB'
         * files.getHumanReadableSize(5 * Math.pow(1024, 5)); // '5.0 PB'
         * files.getHumanReadableSize(6 * Math.pow(1024, 6)); // '6.0 EB'
         * files.getHumanReadableSize(-1); // '-1 B'
         * files.getHumanReadableSize(2e10); // '18.6 GB' (approximation)
         * files.getHumanReadableSize(-2e10); // '-20000000000 B'
         * files.getHumanReadableSize(); // Error
         * files.getHumanReadableSize(7 * Math.pow(1024, 7)); // Error
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String getHumanReadableSize(long bytes) {
         *     return PFiles.getHumanReadableSize(bytes);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static String getHumanReadableSize(long bytes) {
         *     int unit = 1024;
         *     if (bytes < unit) return bytes + " B";
         *     int exp = (int) (Math.log(bytes) / Math.log(unit));
         *     String pre = "KMGTPE".substring(exp - 1, exp);
         *     return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(unit, exp), pre);
         * }
         */
        getHumanReadableSize(bytes: number): string;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String getName(String filePath) {
         *     return PFiles.getName(filePath);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static String getName(String filePath) {
         *     filePath = filePath.replace('\\', '/');
         *     return new File(filePath).getName();
         * }
         */
        getName(filePath: string): string;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String getNameWithoutExtension(String filePath) {
         *     return PFiles.getNameWithoutExtension(filePath);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static String getNameWithoutExtension(String filePath) {
         *     String fileName = getName(filePath);
         *     int b = fileName.lastIndexOf('.');
         *     if (b < 0)
         *         b = fileName.length();
         *     fileName = fileName.substring(0, b);
         *     return fileName;
         * }
         */
        getNameWithoutExtension(filePath: string): string;

        /**
         * @example
         * files.getSdcardPath(); // e.g. '/storage/emulated/0'
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String getSdcardPath() {
         *     return PFiles.getSdcardPath();
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static String getSdcardPath() {
         *     return Environment.getExternalStorageDirectory().getPath();
         * }
         */
        getSdcardPath(): string;

        /**
         * @example
         * files.getSimplifiedPath('modules'); // 'modules'
         * files.getSimplifiedPath(files.getSdcardPath() + java.io.File.separator + 'Sample/modules'); // e.g. '/Sample/modules'
         * files.getSimplifiedPath('/storage/emulated/0/Sample/modules'); // e.g. '/Sample/modules'
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String getSimplifiedPath(String path) {
         *     return PFiles.getSimplifiedPath(path);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static String getSimplifiedPath(String path) {
         *     if (path.startsWith(Environment.getExternalStorageDirectory().getPath())) {
         *         return path.substring(Environment.getExternalStorageDirectory().getPath().length());
         *     }
         *     return path;
         * }
         */
        getSimplifiedPath(path: string): string;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean isDir(String path) {
         *     return PFiles.isDir(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean isDir(String path) {
         *     return new File(path).isDirectory();
         * }
         */
        isDir(path: string): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean isEmptyDir(String path) {
         *     return PFiles.isEmptyDir(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean isEmptyDir(String path) {
         *     File file = new File(path);
         *     return file.isDirectory() && file.list().length == 0;
         * }
         */
        isEmptyDir(path: string): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean isFile(String path) {
         *     return PFiles.isFile(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean isFile(String path) {
         *     return new File(path).isFile();
         * }
         */
        isFile(path: string): boolean;

        /**
         * @example
         * let dir = files.cwd();
         * let jsFiles = files.listDir(dir, function (name) {
         *     return name.endsWith('.js') && files.isFile(files.join(dir, name));
         * });
         * console.log(jsFiles);
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String[] listDir(String path) {
         *     return PFiles.listDir(path(path));
         * }
         * public String[] listDir(String path, Func1<String, Boolean> filter) {
         *     return PFiles.listDir(path(path), filter);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static String[] listDir(String path, final Func1<String, Boolean> filter) {
         *     final File file = new File(path);
         *     return wrapNonNull(file.list(new FilenameFilter() {
         *         public boolean accept(File dir, String name) {
         *             return filter.call(name);
         *         }
         *     }));
         * }
         * @see org.autojs.autojs.tool.Func1
         */
        listDir(path: string, filter?: org.autojs.autojs.tool.Func1<string, java.lang.Boolean> | ((name: string) => boolean)): string[];

        /**
         * @example
         * files.move("./1.txt", "./Backup/1.txt");
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean move(String path, String newPath) {
         *     return PFiles.move(path(path), newPath);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean move(String path, String newPath) {
         *     File f = new File(path);
         *     return f.renameTo(new File(newPath));
         * }
         */
        move(pathFrom: string, pathTo: string): boolean;

        /**
         * @param path
         * @param [mode='r']
         * @param [encoding='utf-8']
         * @param [bufferSize=8192]
         * @example Source code summary (zh-CN: 源代码摘要)
         * static final int DEFAULT_BUFFER_SIZE = 8192;
         * static final String DEFAULT_ENCODING = Charset.defaultCharset().name();
         * public static PFileInterface open(String path, String mode, String encoding, int bufferSize) {
         *     switch (mode) {
         *         case "r":
         *             return new PReadableTextFile(path, encoding, bufferSize);
         *         case "w":
         *             return new PWritableTextFile(path, encoding, bufferSize, false);
         *         case "a":
         *             return new PWritableTextFile(path, encoding, bufferSize, true);
         *     }
         *     return null;
         * }
         * public static Object open(String path, String mode, String encoding) {
         *     return open(path, mode, encoding, DEFAULT_BUFFER_SIZE);
         * }
         * public static Object open(String path, String mode) {
         *     return open(path, mode, DEFAULT_ENCODING, DEFAULT_BUFFER_SIZE);
         * }
         * public static Object open(String path) {
         *     return open(path, "r", DEFAULT_ENCODING, DEFAULT_BUFFER_SIZE);
         * }
         * @see org.autojs.autojs.pio.PFileInterface
         * @see org.autojs.autojs.pio.PReadableTextFile
         * @see org.autojs.autojs.pio.PWritableTextFile
         * @see java.nio.charset.Charset
         */
        open(path: string, mode?: 'r', encoding?: StandardCharsets, bufferSize?: number): org.autojs.autojs.pio.PReadableTextFile;

        /**
         * @param path
         * @param mode
         * @param [encoding='utf-8']
         * @param [bufferSize=8192]
         * @example Source code summary (zh-CN: 源代码摘要)
         * static final int DEFAULT_BUFFER_SIZE = 8192;
         * static final String DEFAULT_ENCODING = Charset.defaultCharset().name();
         * public static PFileInterface open(String path, String mode, String encoding, int bufferSize) {
         *     switch (mode) {
         *         case "r":
         *             return new PReadableTextFile(path, encoding, bufferSize);
         *         case "w":
         *             return new PWritableTextFile(path, encoding, bufferSize, false);
         *         case "a":
         *             return new PWritableTextFile(path, encoding, bufferSize, true);
         *     }
         *     return null;
         * }
         * public static Object open(String path, String mode, String encoding) {
         *     return open(path, mode, encoding, DEFAULT_BUFFER_SIZE);
         * }
         * public static Object open(String path, String mode) {
         *     return open(path, mode, DEFAULT_ENCODING, DEFAULT_BUFFER_SIZE);
         * }
         * public static Object open(String path) {
         *     return open(path, "r", DEFAULT_ENCODING, DEFAULT_BUFFER_SIZE);
         * }
         * @see org.autojs.autojs.pio.PFileInterface
         * @see org.autojs.autojs.pio.PReadableTextFile
         * @see org.autojs.autojs.pio.PWritableTextFile
         * @see java.nio.charset.Charset
         */
        open(path: string, mode: 'w' | 'a', encoding?: StandardCharsets, bufferSize?: number): org.autojs.autojs.pio.PWritableTextFile;

        /**
         * @example
         * // e.g. "/storage/emulated/0/Scripts/1.png"
         * console.log(files.path("./1.png"));
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String path(String relativePath) {
         *     String cwd = cwd();
         *     if (cwd == null || relativePath == null || relativePath.startsWith('/'))
         *         return relativePath;
         *     File f = new File(cwd);
         *     String[] paths = relativePath.split('/');
         *     for (String path : paths) {
         *         if (path.equals('.'))
         *             continue;
         *         if (path.equals('..')) {
         *             f = f.getParentFile();
         *             continue;
         *         }
         *         f = new File(f, path);
         *     }
         *     String path = f.getPath();
         *     return relativePath.endsWith(File.separator) ? path + "/" : path;
         * }
         * public String cwd() {
         *     return mRuntime.engines.myEngine().cwd();
         * }
         */
        path(relativePath?: string | null): string | null;

        /**
         * @param path
         * @param [encoding='utf-8']
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String read(String path, String encoding) {
         *     return PFiles.read(path(path), encoding);
         * }
         * public String read(String path) {
         *     return PFiles.read(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static String read(InputStream is, String encoding) {
         *     try {
         *         byte[] bytes = new byte[is.available()];
         *         is.read(bytes);
         *         return new String(bytes, encoding);
         *     } catch (IOException e) {
         *         throw new UncheckedIOException(e);
         *     } finally {
         *         closeSilently(is);
         *     }
         * }
         */
        read(path: string, encoding?: StandardCharsets): string;

        /**
         * @param path
         * @param [encoding='utf-8']
         * @example
         * // ['全局函数', '应用', '自动操作', '控制台', ...]
         * JSON.parse(files.readAssets('indices/all.json')).map(o => o.summary);
         * @example Source code summary (zh-CN: 源代码摘要)
         * public String readAssets(String path, String encoding) {
         *     try {
         *         return PFiles.read(mRuntime.getUiHandler().getContext().getAssets().open(path), encoding);
         *     } catch (IOException e) {
         *         throw new UncheckedIOException(e);
         *     }
         * }
         */
        readAssets(path: Files.AssetsPaths, encoding?: StandardCharsets): string;

        /**
         * @example
         * const data = files.readBytes("/sdcard/1.png");
         * const sb = new java.lang.StringBuilder();
         * for (let i = 0; i < data.length; i++) {
         *     sb.append(data[i].toString(16));
         * }
         * console.log(sb.toString());
         * @example Source code summary (zh-CN: 源代码摘要)
         * public byte[] readBytes(String path) {
         *     return PFiles.readBytes(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static byte[] readBytes(InputStream is) {
         *     try {
         *         byte[] bytes = new byte[is.available()];
         *         is.read(bytes);
         *         return bytes;
         *     } catch (IOException e) {
         *         throw new UncheckedIOException(e);
         *     }
         * }
         */
        readBytes(path: string): number[];

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean remove(String path) {
         *     return PFiles.remove(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean remove(String path) {
         *     return new File(path).delete();
         * }
         */
        remove(path: string): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean removeDir(String path) {
         *     return PFiles.removeDir(path(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean removeDir(String path) {
         *     return deleteRecursively(new File(path));
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean deleteRecursively(File file) {
         *     if (file.isFile())
         *         return file.delete();
         *     File[] children = file.listFiles();
         *     if (children != null) {
         *         for (File child : children) {
         *             if (!deleteRecursively(child))
         *                 return false;
         *         }
         *     }
         *     return file.delete();
         * }
         */
        removeDir(path: string): boolean;

        /**
         * @example
         * files.rename("./1.txt", "2.txt");
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean rename(String path, String newName) {
         *     return PFiles.rename(path(path), newName);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean rename(String path, String newName) {
         *     File f = new File(path);
         *     return f.renameTo(new File(f.getParent(), newName));
         * }
         */
        rename(path: string, newName: string): boolean;

        /**
         * @example
         * files.rename("./1.txt", "2");
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean renameWithoutExtension(String path, String newName) {
         *     return PFiles.renameWithoutExtension(path(path), newName);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static boolean renameWithoutExtension(String path, String newName) {
         *     File file = new File(path);
         *     File newFile = new File(file.getParent(), newName + "." + getExtension(file.getName()));
         *     return file.renameTo(newFile);
         * }
         */
        renameWithoutExtension(path: string, newName: string): boolean;

        /**
         * @param path
         * @param text
         * @param [encoding="utf-8"]
         * @example
         * files.write("./1.txt", "Hello");
         * app.viewFile("./1.txt");
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void write(String path, String text) {
         *     PFiles.write(path(path), text);
         * }
         * public void write(String path, String text, String encoding) {
         *     PFiles.write(path(path), text, encoding);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static void write(OutputStream outputStream, String text, String encoding) {
         *     try {
         *         outputStream.write(text.getBytes(encoding));
         *     } catch (IOException e) {
         *         throw new UncheckedIOException(e);
         *     } finally {
         *         closeSilently(outputStream);
         *     }
         * }
         */
        write(path: string, text: string, encoding?: StandardCharsets): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void writeBytes(String path, byte[] bytes) {
         *     PFiles.writeBytes(path(path), bytes);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static void writeBytes(OutputStream outputStream, byte[] bytes) {
         *     try {
         *         outputStream.write(bytes);
         *         outputStream.close();
         *     } catch (IOException e) {
         *         throw new UncheckedIOException(e);
         *     }
         * }
         */
        writeBytes(path: string, bytes: number[]): void;

        toFile(path: string): java.io.File;

    }
}

declare namespace Files {

    type AssetsPaths = 'modules/__app__.js' | 'modules/__automator__.js' | 'modules/__console__.js' | 'modules/__continuation__.js' | 'modules/__device__.js' | 'modules/__dialogs__.js' | 'modules/__engines__.js' | 'modules/__events__.js' | 'modules/__floaty__.js' | 'modules/__globals__.js' | 'modules/__http__.js' | 'modules/__images__.js' | 'modules/__files__.js' | 'modules/__json2__.js' | 'modules/__media__.js' | 'modules/__plugins__.js' | 'modules/__RootAutomator__.js' | 'modules/__selector__.js' | 'modules/__sensors__.js' | 'modules/__shell__.js' | 'modules/__storages__.js' | 'modules/__threads__.js' | 'modules/__timers__.js' | 'modules/__ui__.js' | 'modules/__util__.js' | 'modules/__web__.js' | 'docs/all.html' | 'indices/all.json' | 'indices/all_android_classes.json' | 'docs/app.html' | 'modules/array-observe.min.js' | 'autojs.keystore' | 'js/js-beautify/beautify.js' | 'sample/图片与图色处理/找图/block.png' | 'docs/canvas.html' | 'docs/console.html' | 'docs/coordinates-based-automation.html' | 'editor/theme/dark_plus.json' | 'docs/device.html' | 'docs/dialogs.html' | 'docs/assets/dnt_helper.js' | 'docs/documentation.html' | 'sample/JavaScript/E4X.js' | 'docs/engines.html' | 'docs/events.html' | 'docs/images/ex-gravity.png' | 'docs/images/ex-padding.png' | 'docs/images/ex1-horizontal.png' | 'docs/files.html' | 'docs/floaty.html' | 'docs/assets/fonts.css' | 'docs/globals.html' | 'sample/协程/协程HelloWorld/hello.txt' | 'sample/JavaScript/HelloWorld.js' | 'docs/http.html' | 'docs/images.html' | 'docs/index.html' | 'init.js' | 'modules/jvm-npm.js' | 'docs/keys.html' | 'editor/theme/light_plus.json' | 'sample/调用Java API/liveConnect.js' | 'modules/lodash.js' | 'sample/协程/ui中使用协程/main.js' | 'sample/协程/协程HelloWorld/main.js' | 'sample/图片与图色处理/找图/mario.png' | 'docs/media.html' | 'docs/modules.html' | 'modules/object-observe-lite.min.js' | 'docs/overview.html' | 'sample/协程/ui中使用协程/project.json' | 'sample/协程/协程HelloWorld/project.json' | 'modules/promise.js' | 'docs/qa.html' | 'modules/result-adapter.js' | 'docs/assets/fonts/S6u9w4BMUTPHh6UVSwiPGQ.woff2' | 'docs/assets/fonts/S6uyw4BMUTPHjx4wXg.woff2' | 'docs/sensors.html' | 'docs/assets/sh.css' | 'docs/assets/sh_javascript.min.js' | 'docs/shell.html' | 'docs/storages.html' | 'docs/assets/style.css' | 'docs/threads.html' | 'docs/timers.html' | 'sample/事件与按键、触摸监听/Toast监听.js' | 'docs/ui.html' | 'sample/对话框/UI模式下使用对话框.js' | 'docs/util.html' | 'sample/复杂界面/WannaCry(仅为娱乐).js' | 'docs/widgets-based-automation.html' | 'docs/work-with-java.html' | 'js/js-beautify/xml_formatter.js' | 'sample/安卓7.0+点按和手势/三指下滑.js' | 'sample/安卓7.0+点按和手势/三指捏合.js' | 'sample/界面控件/下拉菜单.js' | 'sample/控制台/产生随机数.js' | 'sample/本地存储/保存数组和复杂对象.js' | 'sample/本地存储/保存整数等简单数据.js' | 'sample/控制台/保存日志.js' | 'sample/脚本引擎/停止所有正在运行的脚本.js' | 'sample/文件读写/写入文本文件.js' | 'sample/Shell命令/冻结网易云音乐.js' | 'sample/画布/函数图像简单版.js' | 'sample/画布/函数图像高级版.js' | 'sample/界面控件/列表控件.js' | 'sample/文件读写/删除所有空文件夹.js' | 'sample/悬浮窗/动态悬浮文字.js' | 'sample/图片与图色处理/区域找色1.js' | 'sample/图片与图色处理/区域找色2.js' | 'sample/对话框/单选框.js' | 'sample/界面控件/卡片布局.js' | 'sample/应用/卸载应用.js' | 'sample/多线程/原子变量.js' | 'sample/安卓7.0+点按和手势/双指捏合.js' | 'sample/应用/发送意图-文本消息分享.js' | 'sample/多线程/变量可见性实验.js' | 'sample/图片与图色处理/图片处理.js' | 'sample/界面控件/图片控件.js' | 'sample/界面控件/复选框单选框控件.js' | 'sample/多线程/多线程按键监听.js' | 'sample/多线程/多线程简单示例.js' | 'sample/对话框/多选框.js' | 'sample/定时器/定时执行.js' | 'sample/图片与图色处理/实时显示触摸点颜色.js' | 'sample/应用/应用工具.js' | 'sample/应用/强制停止应用.js' | 'sample/复杂界面/待办事项.js' | 'sample/定时器/循环执行.js' | 'sample/安卓7.0+点按和手势/心形手势.js' | 'sample/悬浮窗/悬浮文字.js' | 'sample/悬浮窗/悬浮窗输入框.js' | 'sample/悬浮窗/悬浮窗运行脚本按钮简单版.js' | 'sample/悬浮窗/悬浮运行脚本按钮.js' | 'sample/图片与图色处理/截图并保存.js' | 'sample/传感器/打印常用传感器信息.js' | 'sample/应用/打开应用.js' | 'sample/图片与图色处理/找图/找出所有问号方块.js' | 'sample/图片与图色处理/找图/找出问号方块并画出位置.js' | 'sample/图片与图色处理/找图/找出马里奥.js' | 'sample/图片与图色处理/找到QQ红点位置.js' | 'sample/悬浮窗/护眼模式.js' | 'sample/安卓7.0+点按和手势/拉出通知栏.js' | 'sample/界面控件/按钮控件.js' | 'sample/事件与按键、触摸监听/按键监听.js' | 'sample/控制台/控制台示例.js' | 'sample/JavaScript/数字.js' | 'sample/HTTP网络请求/文件上传.js' | 'sample/HTTP网络请求/文件下载.js' | 'sample/文件读写/文件编码转换(高级).js' | 'sample/文件读写/文件编码转换.js' | 'sample/界面控件/文本控件.js' | 'sample/界面控件/时间日期选择控件.js' | 'sample/传感器/显示常用传感器信息.js' | 'sample/控制台/格式化输出.js' | 'sample/对话框/模拟更新下载对话框.js' | 'sample/图片与图色处理/模糊找色.js' | 'sample/安卓7.0+点按和手势/点击左上角.js' | 'sample/复杂界面/用户调查.js' | 'sample/复杂界面/界面模板一.js' | 'sample/复杂界面/登录界面.js' | 'sample/对话框/确认框.js' | 'sample/对话框/简单计算器.js' | 'sample/图片与图色处理/精确找色.js' | 'sample/多线程/线程启动与关闭.js' | 'sample/控制台/终端模拟器.js' | 'sample/Shell命令/结束所有后台进程.js' | 'sample/界面控件/自定义控件-使用配置勾选框.js' | 'sample/界面控件/自定义控件-布局模板.js' | 'sample/界面控件/自定义控件-带颜色按钮.js' | 'sample/界面控件/自定义控件-模块-配置勾选框.js' | 'sample/图片与图色处理/获取网络图片并保存.js' | 'sample/HTTP网络请求/获取网页.js' | 'sample/设备与设备信息/获取设备信息.js' | 'sample/对话框/菜单.js' | 'sample/界面控件/表格控件-内置图标查看器.js' | 'sample/Shell命令/解冻并打开网易云音乐.js' | 'sample/事件与按键、触摸监听/触摸监听.js' | 'sample/文件读写/读写文本文件.js' | 'sample/文件读写/读取文本文件.js' | 'sample/控制台/调整控制台大小位置.js' | 'sample/设备与设备信息/调整设备亮度.js' | 'sample/设备与设备信息/调整设备音量.js' | 'sample/画布/贪吃蛇.js' | 'sample/画布/贪吃蛇重力感应版.js' | 'sample/对话框/输入框.js' | 'sample/界面控件/输入框控件.js' | 'sample/脚本引擎/运行录制文件.js' | 'sample/脚本引擎/运行新的脚本任务.js' | 'sample/脚本引擎/运行脚本文件.js' | 'sample/界面控件/进度条控件.js' | 'sample/事件与按键、触摸监听/通知监听.js' | 'sample/Shell命令/锁屏.js' | 'sample/事件与按键、触摸监听/长按返回退出当前程序.js' | 'sample/本地存储/随手记.js' | 'sample/复杂界面/音乐浏览器.js' | 'sample/事件与按键、触摸监听/音量键控制程序.js' | 'sample/图片与图色处理/颜色获取和检测.js';

}

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * const fs = __runtime__.files;
 * const files = Object.create(fs);
 * scope.open = function (path, mode, encoding, bufferSize) {
 *     if (arguments.length === 1) {
 *         return files.open(path);
 *     } else if (arguments.length === 2) {
 *         return files.open(path, mode);
 *     } else if (arguments.length === 3) {
 *         return files.open(path, mode, encoding);
 *     } else if (arguments.length === 4) {
 *         return files.open(path, mode, encoding, bufferSize);
 *     }
 * };
 * @see org.autojs.autojs.runtime.api.Files.open
 */
declare function open(path: string, mode?: 'r', encoding?: StandardCharsets, bufferSize?: number): org.autojs.autojs.pio.PReadableTextFile;
/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * const fs = __runtime__.files;
 * const files = Object.create(fs);
 * scope.open = function (path, mode, encoding, bufferSize) {
 *     if (arguments.length === 1) {
 *         return files.open(path);
 *     } else if (arguments.length === 2) {
 *         return files.open(path, mode);
 *     } else if (arguments.length === 3) {
 *         return files.open(path, mode, encoding);
 *     } else if (arguments.length === 4) {
 *         return files.open(path, mode, encoding, bufferSize);
 *     }
 * };
 * @see org.autojs.autojs.runtime.api.Files.open
 */
declare function open(path: string, mode: 'w' | 'a', encoding?: StandardCharsets, bufferSize?: number): org.autojs.autojs.pio.PWritableTextFile;
