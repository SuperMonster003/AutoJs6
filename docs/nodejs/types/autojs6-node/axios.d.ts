declare module "axios" {
  namespace axios {
    export type HeaderInit = Readonly<Record<string, string>> | readonly (readonly [string, string])[];
    export type ParamsInit =
      | string
      | Readonly<Record<string, string | number | boolean | readonly (string | number | boolean)[] | null | undefined>>
      | readonly (readonly [string, string | number | boolean | null | undefined])[];
    export type BodyInit = string | ArrayBuffer | Uint8Array | Record<string, unknown> | null;
    export type ResponseType = "json" | "text" | "arraybuffer";

    export interface RequestConfig {
      url?: string;
      baseURL?: string;
      method?: "GET" | "POST" | "get" | "post";
      headers?: HeaderInit;
      params?: ParamsInit;
      data?: BodyInit;
      body?: BodyInit;
      timeout?: number;
      maxResponseBytes?: number;
      maxContentLength?: number;
      maxRedirects?: number;
      responseType?: ResponseType;
      signal?: AutoJs6Node.AbortSignalLike;
      validateStatus?(status: number): boolean;
    }

    export interface Response<T = unknown> {
      readonly data: T;
      readonly status: number;
      readonly statusText: string;
      readonly headers: Readonly<Record<string, string>>;
      readonly config: RequestConfig;
      readonly request: Readonly<{ method: string; url: string }>;
    }

    export interface AxiosError<T = unknown> extends Error {
      readonly isAxiosError: true;
      readonly code?: string;
      readonly autojs6Code?: string;
      readonly config: RequestConfig;
      readonly request?: Readonly<{ method: string; url: string }>;
      readonly response?: Response<T>;
      readonly status?: number;
      toJSON(): {
        message: string;
        name: string;
        code?: string;
        autojs6Code?: string;
        status?: number;
      };
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

    export interface AxiosModule {
      <T = unknown>(config: RequestConfig): Promise<Response<T>>;
      <T = unknown>(url: string, config?: RequestConfig): Promise<Response<T>>;
      readonly request: <T = unknown>(config: RequestConfig) => Promise<Response<T>>;
      readonly get: <T = unknown>(url: string, config?: RequestConfig) => Promise<Response<T>>;
      readonly post: <T = unknown>(url: string, data?: BodyInit, config?: RequestConfig) => Promise<Response<T>>;
      readonly create: (config?: RequestConfig) => AxiosModule;
      readonly isAxiosError: (value: unknown) => value is AxiosError;
      readonly defaults: Readonly<RequestConfig>;
      readonly adapterName: "autojs6-controlled-fetch";
      readonly VERSION: string;
      readonly policy: Policy;
      diagnostics(): Diagnostics;
      readonly default: AxiosModule;
    }
  }

  const axios: axios.AxiosModule;
  export = axios;
}
