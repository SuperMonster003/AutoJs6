declare module "net" {
  namespace net {
    export type IPVersion = 4 | 6;
    export type IPFamily = "ipv4" | "ipv6";
    export type SocketFamily = IPVersion | IPFamily;
    export type SocketChunk = string | ArrayBuffer | Uint8Array;

    export interface SocketConstructorOpts {
      allowHalfOpen?: boolean;
    }

    export interface SocketAddressOptions {
      address?: string;
      family?: SocketFamily;
      flowlabel?: number;
      port?: number | string;
    }

    export class Socket {
      constructor(options?: SocketConstructorOpts);
      connecting: boolean;
      destroyed: boolean;
      pending: boolean;
      readyState: "closed" | string;
      allowHalfOpen: boolean;
      remoteAddress?: string;
      remoteFamily?: string;
      remotePort?: number;
      localAddress?: string;
      localPort?: number;
      bytesRead: number;
      bytesWritten: number;
      connect(...args: readonly unknown[]): never;
      write(chunk: SocketChunk, encoding?: string, callback?: (error?: Error | null) => void): boolean;
      write(chunk: SocketChunk, callback?: (error?: Error | null) => void): boolean;
      end(chunk?: SocketChunk, encoding?: string, callback?: () => void): this;
      end(chunk?: SocketChunk, callback?: () => void): this;
      destroy(error?: Error): this;
      pause(): this;
      resume(): this;
      setEncoding(encoding?: string): this;
      setKeepAlive(enable?: boolean, initialDelay?: number): this;
      setNoDelay(noDelay?: boolean): this;
      setTimeout(timeout: number, callback?: () => void): this;
      ref(): this;
      unref(): this;
      address(): Readonly<Record<string, never>>;
      on(event: string, listener: (...args: readonly unknown[]) => void): this;
      once(event: string, listener: (...args: readonly unknown[]) => void): this;
    }

    export class Server {
      constructor(options?: unknown, connectionListener?: (socket: Socket) => void);
      listening: boolean;
      maxConnections: number;
      connections: number;
      listen(...args: readonly unknown[]): never;
      close(callback?: (error: Error | null) => void): this;
      address(): null;
      getConnections(callback: (error: Error | null, count: number) => void): this;
      ref(): this;
      unref(): this;
      on(event: string, listener: (...args: readonly unknown[]) => void): this;
      once(event: string, listener: (...args: readonly unknown[]) => void): this;
    }

    export class SocketAddress {
      constructor(options?: SocketAddressOptions);
      readonly address: string;
      readonly family: IPFamily;
      readonly flowlabel: number;
      readonly port: number;
      static parse(input: string): SocketAddress | undefined;
    }

    export class BlockList {
      addAddress(address: string, family?: SocketFamily): void;
      addRange(start: string, end: string, family?: SocketFamily): void;
      addSubnet(network: string, prefix: number, family?: SocketFamily): void;
      check(address: string, family?: SocketFamily): boolean;
      readonly rules: readonly string[];
    }

    export const Stream: typeof Socket;
    export function connect(...args: readonly unknown[]): never;
    export function createConnection(...args: readonly unknown[]): never;
    export function createServer(options?: unknown, connectionListener?: (socket: Socket) => void): Server;
    export function getDefaultAutoSelectFamily(): boolean;
    export function setDefaultAutoSelectFamily(value: boolean): void;
    export function getDefaultAutoSelectFamilyAttemptTimeout(): number;
    export function setDefaultAutoSelectFamilyAttemptTimeout(value: number): void;
    export function isIP(input: string): 0 | 4 | 6;
    export function isIPv4(input: string): boolean;
    export function isIPv6(input: string): boolean;
    export function isSocketAddress(input: unknown): input is SocketAddress;
  }

  export = net;
}

declare module "node:net" {
  import net = require("net");
  export = net;
}
