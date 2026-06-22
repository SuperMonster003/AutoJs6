declare namespace AutoJs6NodeConsole {
  export interface WritableLike {
    write(chunk: unknown, ...args: unknown[]): unknown;
  }

  export interface ConsoleOptions {
    readonly stdout: WritableLike;
    readonly stderr?: WritableLike;
  }

  export interface ConsoleConstructor {
    new(stdout: WritableLike | ConsoleOptions, stderr?: WritableLike): ConsoleInstance;
    (stdout: WritableLike | ConsoleOptions, stderr?: WritableLike): ConsoleInstance;
    readonly prototype: ConsoleInstance;
  }

  export interface ConsoleInstance {
    log(...data: unknown[]): void;
    info(...data: unknown[]): void;
    debug(...data: unknown[]): void;
    dir(...data: unknown[]): void;
    dirxml(...data: unknown[]): void;
    table(...data: unknown[]): void;
    error(...data: unknown[]): void;
    warn(...data: unknown[]): void;
    assert(value?: unknown, ...data: unknown[]): void;
    count(label?: string): void;
    countReset(label?: string): void;
    time(label?: string): void;
    timeLog(label?: string, ...data: unknown[]): void;
    timeEnd(label?: string): void;
    trace(...data: unknown[]): void;
    printAllStackTrace(error?: unknown): void;
    clear(): void;
    group(...data: unknown[]): void;
    groupCollapsed(...data: unknown[]): void;
    groupEnd(): void;
  }

  export interface ConsoleModule extends ConsoleInstance {
    readonly Console: ConsoleConstructor;
    context(): ConsoleModule;
    createTask(): { run<T>(fn: (...args: unknown[]) => T, ...args: unknown[]): T };
    print(...data: unknown[]): void;
    verbose(...data: unknown[]): void;
    err(...data: unknown[]): void;
    clearConsole(): void;
    show(autoHide?: boolean): ConsoleModule;
    showConsole(autoHide?: boolean): ConsoleModule;
    openConsole(autoHide?: boolean): ConsoleModule;
    hide(): ConsoleModule;
    reset(): ConsoleModule;
    expand(): ConsoleModule;
    collapse(): ConsoleModule;
    launch(): ConsoleModule;
    launchConsole(): ConsoleModule;
    setSize(width: number, height: number): ConsoleModule;
    setPosition(x: number, y: number): ConsoleModule;
    setTitle(title?: unknown): ConsoleModule;
    setTitleTextSize(size: number): ConsoleModule;
    setTitleTextColor(color: unknown): ConsoleModule;
    setTitleBackgroundColor(color: unknown): ConsoleModule;
    setTitleBackgroundTint(color?: unknown): ConsoleModule;
    setTitleBackgroundAlpha(alpha: number): ConsoleModule;
    setTitleIconsTint(color?: unknown): ConsoleModule;
    setContentTextSize(size: number): ConsoleModule;
    setContentTextColor(color: unknown): ConsoleModule;
    setContentTextColors(...colors: unknown[]): ConsoleModule;
    setContentBackgroundColor(color: unknown): ConsoleModule;
    setContentBackgroundTint(color?: unknown): ConsoleModule;
    setContentBackgroundAlpha(alpha: number): ConsoleModule;
    setTextSize(size: number): ConsoleModule;
    setTextColor(color: unknown): ConsoleModule;
    setBackgroundColor(color: unknown): ConsoleModule;
    setBackgroundTint(color?: unknown): ConsoleModule;
    setBackgroundAlpha(alpha: number): ConsoleModule;
    setExitOnClose(enabledOrTimeout?: boolean | number): ConsoleModule;
    setTouchable(touchable?: boolean): ConsoleModule;
    setGlobalLogConfig(config: Record<string, unknown>): ConsoleModule;
    resetGlobalLogConfig(): ConsoleModule;
    input(title?: unknown, prefill?: unknown): never;
    rawInput(title?: unknown, prefill?: unknown): never;
    build(options?: unknown): never;
    profile(...data: unknown[]): void;
    profileEnd(...data: unknown[]): void;
    timeStamp(...data: unknown[]): void;
  }
}

interface Console extends AutoJs6NodeConsole.ConsoleModule {}

declare var console: Console;

declare module "console" {
  namespace console {
    export type WritableLike = AutoJs6NodeConsole.WritableLike;
    export type ConsoleOptions = AutoJs6NodeConsole.ConsoleOptions;
    export type ConsoleConstructor = AutoJs6NodeConsole.ConsoleConstructor;
    export type ConsoleInstance = AutoJs6NodeConsole.ConsoleInstance;
    export type ConsoleModule = AutoJs6NodeConsole.ConsoleModule;
  }

  const console: console.ConsoleModule;
  export = console;
}

declare module "node:console" {
  import console = require("console");
  export = console;
}
