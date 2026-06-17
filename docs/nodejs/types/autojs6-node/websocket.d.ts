declare module "websocket" {
  namespace websocket {
    export type ReadyState = "connecting" | "open" | "closing" | "closed";

    export interface ConnectOptions {
      headers?: Readonly<Record<string, string>> | readonly (readonly [string, string])[];
      protocols?: string | readonly string[];
      timeoutMs?: number;
      maxMessageBytes?: number;
      maxQueueSize?: number;
      signal?: AutoJs6Node.AbortSignalLike;
    }

    export interface Message {
      readonly type: "text" | "binary";
      readonly text?: string;
      readonly dataBase64?: string;
    }

    export interface CloseEvent {
      readonly type: "close";
      readonly code: number;
      readonly reason: string;
    }

    export interface Connection {
      readonly id: string;
      readonly url: string;
      readonly readyState: ReadyState;
      readonly maxMessageBytes: number;
      onopen: (() => void) | null;
      onmessage: ((message: Message) => void) | null;
      onclose: ((event: CloseEvent) => void) | null;
      onerror: ((error: Error) => void) | null;
      on(event: "open", listener: () => void): this;
      on(event: "message", listener: (message: Message) => void): this;
      on(event: "close", listener: (event: CloseEvent) => void): this;
      on(event: "error", listener: (error: Error) => void): this;
      off(event: "open", listener: () => void): this;
      off(event: "message", listener: (message: Message) => void): this;
      off(event: "close", listener: (event: CloseEvent) => void): this;
      off(event: "error", listener: (error: Error) => void): this;
      addEventListener(event: "open", listener: () => void): this;
      addEventListener(event: "message", listener: (message: Message) => void): this;
      addEventListener(event: "close", listener: (event: CloseEvent) => void): this;
      addEventListener(event: "error", listener: (error: Error) => void): this;
      removeEventListener(event: "open", listener: () => void): this;
      removeEventListener(event: "message", listener: (message: Message) => void): this;
      removeEventListener(event: "close", listener: (event: CloseEvent) => void): this;
      removeEventListener(event: "error", listener: (error: Error) => void): this;
      send(message: string | ArrayBuffer | Uint8Array, options?: { timeoutMs?: number; signal?: AutoJs6Node.AbortSignalLike }): Promise<void>;
      close(code?: number, reason?: string, options?: { timeoutMs?: number; signal?: AutoJs6Node.AbortSignalLike }): Promise<void>;
    }

    export interface Policy {
      readonly maxConnections: number;
      readonly defaultTimeoutMs: number;
      readonly hardTimeoutMs: number;
      readonly defaultMaxMessageBytes: number;
      readonly hardMaxMessageBytes: number;
      readonly defaultMaxQueueSize: number;
      readonly hardMaxQueueSize: number;
      readonly allowedSchemes: readonly string[];
    }

    export interface Diagnostics {
      readonly activeConnectionCount: number;
      readonly maxActiveConnectionCount: number;
      readonly timeoutCount: number;
      readonly rejectedByPolicyCount: number;
      readonly closedCount: number;
      readonly messageCount: number;
      readonly lastPolicyRejection: string;
    }

    export interface WebSocketModule {
      readonly policy: Policy;
      connect(url: string, options?: ConnectOptions): Promise<Connection>;
      diagnostics(): Diagnostics;
    }
  }

  const websocket: websocket.WebSocketModule;
  export = websocket;
}
