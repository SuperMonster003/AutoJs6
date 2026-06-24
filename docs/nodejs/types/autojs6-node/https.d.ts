declare module "https" {
  import http = require("http");

  namespace https {
    export interface RequestOptions extends Omit<http.RequestOptions, "protocol" | "agent"> {
      protocol?: "https:";
      rejectUnauthorized?: true;
      agent?: Agent | false | null;
    }

    export interface AgentOptions extends http.AgentOptions {
      rejectUnauthorized?: true;
    }

    export class Agent extends http.Agent {
      constructor(options?: AgentOptions);
      readonly defaultPort: 443;
      readonly protocol: "https:";
    }

    export const METHODS: typeof http.METHODS;
    export const STATUS_CODES: typeof http.STATUS_CODES;
    export const maxHeaderSize: number;
    export const globalAgent: Agent;
    export function request(url: string | RequestOptions, options?: RequestOptions | ((response: http.IncomingMessage) => void), callback?: (response: http.IncomingMessage) => void): http.ClientRequest;
    export function get(url: string | RequestOptions, options?: RequestOptions | ((response: http.IncomingMessage) => void), callback?: (response: http.IncomingMessage) => void): http.ClientRequest;
    export function createServer(options?: unknown, requestListener?: (request: http.IncomingMessage, response: unknown) => void): http.Server;
    export function validateHeaderName(name: string): void;
    export function validateHeaderValue(name: string, value: http.HeaderValue): void;
  }

  export = https;
}

declare module "node:https" {
  import https = require("https");
  export = https;
}
