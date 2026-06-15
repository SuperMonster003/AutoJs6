declare namespace AutoJs6Node {
  export type JsonPrimitive = string | number | boolean | null;
  export type JsonValue = JsonPrimitive | JsonObject | JsonArray;

  export interface JsonObject {
    readonly [key: string]: JsonValue;
  }

  export interface JsonArray extends ReadonlyArray<JsonValue> {}

  export interface AbortSignalLike {
    readonly aborted: boolean;
    addEventListener?(type: "abort", listener: () => void): void;
    removeEventListener?(type: "abort", listener: () => void): void;
  }

  export interface BridgeCallOptions {
    timeoutMs?: number;
    signal?: AbortSignalLike;
  }

  export interface AutoJs6BridgeError extends Error {
    readonly autojs6Code?: string;
    readonly code?: string;
    readonly module?: string;
    readonly method?: string;
    readonly missingCapabilities?: readonly string[];
  }

  export interface Bounds {
    readonly left: number;
    readonly top: number;
    readonly right: number;
    readonly bottom: number;
  }

  export interface Point {
    readonly x: number;
    readonly y: number;
  }

  export interface ImageHandle {
    readonly id: string;
    readonly __autojs6ImageHandle?: string;
    readonly width: number;
    readonly height: number;
  }

  export interface ImageHandleLike {
    readonly id: string;
    readonly width: number;
    readonly height: number;
    readonly __autojs6ImageHandle?: string;
  }
}
