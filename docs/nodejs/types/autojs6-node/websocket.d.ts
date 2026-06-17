declare module "websocket" {
  namespace websocket {
    export type ReadyState = "connecting" | "open" | "closing" | "closed";

    export interface ConnectOptions {
      timeoutMs?: number;
      maxMessageBytes?: number;
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
      on(event: "open", listener: () => void): this;
      on(event: "message", listener: (message: Message) => void): this;
      on(event: "close", listener: (event: CloseEvent) => void): this;
      on(event: "error", listener: (error: Error) => void): this;
      off(event: "open", listener: () => void): this;
      off(event: "message", listener: (message: Message) => void): this;
      off(event: "close", listener: (event: CloseEvent) => void): this;
      off(event: "error", listener: (error: Error) => void): this;
      send(message: string | ArrayBuffer | Uint8Array, options?: { timeoutMs?: number; signal?: AutoJs6Node.AbortSignalLike }): Promise<boolean>;
      close(code?: number, reason?: string, options?: { timeoutMs?: number; signal?: AutoJs6Node.AbortSignalLike }): Promise<void>;
    }

    export interface Policy {
      readonly maxConnections: number;
      readonly defaultTimeoutMs: number;
      readonly defaultMaxMessageBytes: number;
    }

    export interface Diagnostics {
      readonly activeConnectionCount: number;
      readonly maxActiveConnectionCount: number;
      readonly timeoutCount: number;
      readonly rejectedByPolicyCount: number;
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
