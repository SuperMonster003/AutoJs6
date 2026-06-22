declare namespace AutoJs6NodeTimers {
  export type TimerHandle = object | number;

  export interface TimersModule {
    setTimeout(callback: (...args: unknown[]) => void, delay?: number, ...args: unknown[]): TimerHandle;
    clearTimeout(handle?: TimerHandle): void;
    setInterval(callback: (...args: unknown[]) => void, delay?: number, ...args: unknown[]): TimerHandle;
    clearInterval(handle?: TimerHandle): void;
    setImmediate(callback: (...args: unknown[]) => void, ...args: unknown[]): TimerHandle;
    clearImmediate(handle?: TimerHandle): void;
  }

  export interface TimersPromisesModule {
    setTimeout<T = void>(delay?: number, value?: T, options?: { readonly signal?: AutoJs6Node.AbortSignalLike }): Promise<T>;
    setImmediate<T = void>(value?: T, options?: { readonly signal?: AutoJs6Node.AbortSignalLike }): Promise<T>;
    setInterval<T = void>(delay?: number, value?: T, options?: { readonly signal?: AutoJs6Node.AbortSignalLike }): AsyncIterable<T>;
  }
}

declare function setTimeout(callback: (...args: unknown[]) => void, delay?: number, ...args: unknown[]): AutoJs6NodeTimers.TimerHandle;
declare function clearTimeout(handle?: AutoJs6NodeTimers.TimerHandle): void;
declare function setInterval(callback: (...args: unknown[]) => void, delay?: number, ...args: unknown[]): AutoJs6NodeTimers.TimerHandle;
declare function clearInterval(handle?: AutoJs6NodeTimers.TimerHandle): void;

declare module "timers" {
  namespace timers {
    export type TimerHandle = AutoJs6NodeTimers.TimerHandle;
    export type TimersModule = AutoJs6NodeTimers.TimersModule;
    export type TimersPromisesModule = AutoJs6NodeTimers.TimersPromisesModule;
  }

  const timers: timers.TimersModule;
  export = timers;
}

declare module "node:timers" {
  import timers = require("timers");
  export = timers;
}

declare module "timers/promises" {
  const timersPromises: AutoJs6NodeTimers.TimersPromisesModule;
  export = timersPromises;
}

declare module "node:timers/promises" {
  import timersPromises = require("timers/promises");
  export = timersPromises;
}
