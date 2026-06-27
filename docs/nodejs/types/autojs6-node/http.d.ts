declare module "http" {
  namespace http {
    export type HeaderValue = string | number | readonly string[];
    export type HeaderInit = Readonly<Record<string, HeaderValue>> | readonly (readonly [string, HeaderValue])[];
    export type BodyChunk = string | ArrayBuffer | Uint8Array;

    export interface RequestOptions {
      protocol?: "http:";
      host?: string;
      hostname?: string;
      port?: number | string;
      path?: string;
      method?: "GET" | "POST" | string;
      headers?: HeaderInit;
      timeout?: number;
      timeoutMs?: number;
      maxResponseBytes?: number;
      maxRedirects?: number;
      redirect?: "manual" | "follow" | "error";
      signal?: AutoJs6Node.AbortSignalLike;
      agent?: Agent | false | null;
    }

    export interface IncomingMessage {
      readonly statusCode: number | null;
      readonly statusMessage: string | null;
      readonly headers: Readonly<Record<string, string>>;
      readonly rawHeaders: readonly string[];
      readonly url: string;
      setEncoding(encoding?: string): this;
      pause(): this;
      resume(): this;
      pipe<T extends { write?(chunk: unknown): unknown; end?(): unknown }>(destination: T): T;
      on(event: "data", listener: (chunk: string | Uint8Array) => void): this;
      on(event: "end" | "close", listener: () => void): this;
      on(event: "error", listener: (error: Error) => void): this;
    }

    export interface ClientRequest {
      readonly method: string;
      readonly path: string;
      readonly protocol: string;
      readonly host: string;
      readonly agent: Agent;
      setHeader(name: string, value: HeaderValue): this;
      getHeader(name: string): HeaderValue | undefined;
      getHeaderNames(): readonly string[];
      getHeaders(): Readonly<Record<string, HeaderValue>>;
      hasHeader(name: string): boolean;
      removeHeader(name: string): void;
      write(chunk: BodyChunk, encoding?: string, callback?: (error?: Error | null) => void): boolean;
      end(chunk?: BodyChunk, encoding?: string, callback?: () => void): this;
      abort(): this;
      destroy(error?: Error): this;
      setTimeout(timeout: number, callback?: () => void): this;
      on(event: "response", listener: (response: IncomingMessage) => void): this;
      on(event: "timeout" | "abort" | "close", listener: () => void): this;
      on(event: "error", listener: (error: Error) => void): this;
    }

    export interface AgentOptions {
      keepAlive?: boolean;
      keepAliveMsecs?: number;
      maxSockets?: number;
      maxFreeSockets?: number;
      maxTotalSockets?: number;
      scheduling?: "fifo" | "lifo" | string;
    }

    export class Agent {
      constructor(options?: AgentOptions);
      readonly options: Readonly<AgentOptions>;
      readonly requests: Readonly<Record<string, never>>;
      readonly sockets: Readonly<Record<string, never>>;
      readonly freeSockets: Readonly<Record<string, never>>;
      readonly keepAlive: boolean;
      readonly maxSockets: number;
      readonly maxFreeSockets: number;
      readonly defaultPort: number;
      readonly protocol: string;
      readonly destroyed?: boolean;
      createConnection(): never;
      addRequest(): never;
      getName(options?: RequestOptions): string;
      destroy(): void;
    }

    export interface Server {
      readonly listening: boolean;
      listen(...args: readonly unknown[]): never;
      close(callback?: (error: Error | null) => void): this;
      address(): null;
      setTimeout(timeout: number, callback?: () => void): this;
    }

    export const METHODS: readonly string[];
    export const STATUS_CODES: Readonly<Record<number, string>>;
    export const maxHeaderSize: number;
    export const globalAgent: Agent;
    export function request(url: string | RequestOptions, options?: RequestOptions | ((response: IncomingMessage) => void), callback?: (response: IncomingMessage) => void): ClientRequest;
    export function get(url: string | RequestOptions, options?: RequestOptions | ((response: IncomingMessage) => void), callback?: (response: IncomingMessage) => void): ClientRequest;
    export function createServer(options?: unknown, requestListener?: (request: IncomingMessage, response: unknown) => void): Server;
    export function validateHeaderName(name: string): void;
    export function validateHeaderValue(name: string, value: HeaderValue): void;
  }

  export = http;
}

declare module "node:http" {
  import http = require("http");
  export = http;
}
