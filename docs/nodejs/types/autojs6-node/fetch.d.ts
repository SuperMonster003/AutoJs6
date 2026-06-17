declare module "fetch" {
  namespace fetchModule {
    export type HeaderInit = Readonly<Record<string, string>> | readonly (readonly [string, string])[];
    export type BodyInit = string | ArrayBuffer | Uint8Array | null;

    export interface RequestOptions {
      method?: string;
      headers?: HeaderInit;
      body?: BodyInit;
      timeoutMs?: number;
      maxResponseBytes?: number;
      maxRedirects?: number;
      signal?: AutoJs6Node.AbortSignalLike;
    }

    export interface Headers {
      get(name: string): string | null;
      has(name: string): boolean;
      entries(): IterableIterator<readonly [string, string]>;
      toJSON?(): readonly (readonly [string, string])[];
    }

    export interface Response {
      readonly url: string;
      readonly status: number;
      readonly statusText: string;
      readonly ok: boolean;
      readonly headers: Headers;
      text(): Promise<string>;
      json<T = unknown>(): Promise<T>;
      arrayBuffer(): Promise<ArrayBuffer>;
    }

    export interface Policy {
      readonly maxConcurrentRequests: number;
      readonly defaultTimeoutMs: number;
      readonly defaultMaxResponseBytes: number;
      readonly defaultMaxRedirects: number;
    }

    export interface Diagnostics {
      readonly activeRequestCount: number;
      readonly maxActiveRequestCount: number;
      readonly timeoutCount: number;
      readonly rejectedByPolicyCount: number;
      readonly abortedCount: number;
    }

    export interface FetchModule {
      (input: string, init?: RequestOptions): Promise<Response>;
      readonly fetch: FetchModule;
      readonly default: FetchModule;
      readonly policy: Policy;
      diagnostics(): Diagnostics;
    }
  }

  const fetchModule: fetchModule.FetchModule;
  export = fetchModule;
}
