declare module "dns" {
  namespace dns {
    export type Family = 0 | 4 | 6;
    export type ResultOrder = "verbatim" | "ipv4first" | "ipv6first";
    export type AnyRecord = unknown;

    export interface LookupAddress {
      address: string;
      family: 4 | 6;
    }

    export interface LookupOneOptions {
      family?: Family;
      hints?: number;
      all?: false;
      verbatim?: boolean;
      order?: ResultOrder;
    }

    export interface LookupAllOptions {
      family?: Family;
      hints?: number;
      all: true;
      verbatim?: boolean;
      order?: ResultOrder;
    }

    export type LookupOptions = Family | LookupOneOptions | LookupAllOptions;

    export class Resolver {
      cancel(): void;
      getServers(): readonly string[];
      setLocalAddress(ipv4?: string, ipv6?: string): void;
      setServers(servers: readonly string[]): void;
      resolve(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
      resolve4(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
      resolve6(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
      resolveAny(hostname: string, callback: (error: Error | null, addresses?: readonly AnyRecord[]) => void): void;
      resolveCaa(hostname: string, callback: (error: Error | null, addresses?: readonly AnyRecord[]) => void): void;
      resolveCname(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
      resolveMx(hostname: string, callback: (error: Error | null, addresses?: readonly AnyRecord[]) => void): void;
      resolveNaptr(hostname: string, callback: (error: Error | null, addresses?: readonly AnyRecord[]) => void): void;
      resolveNs(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
      resolvePtr(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
      resolveSoa(hostname: string, callback: (error: Error | null, address?: AnyRecord) => void): void;
      resolveSrv(hostname: string, callback: (error: Error | null, addresses?: readonly AnyRecord[]) => void): void;
      resolveTxt(hostname: string, callback: (error: Error | null, addresses?: readonly string[][]) => void): void;
      reverse(ip: string, callback: (error: Error | null, hostnames?: readonly string[]) => void): void;
    }

    export namespace promises {
      export class Resolver {
        cancel(): void;
        getServers(): readonly string[];
        setLocalAddress(ipv4?: string, ipv6?: string): void;
        setServers(servers: readonly string[]): void;
        resolve(hostname: string): Promise<readonly string[]>;
        resolve4(hostname: string): Promise<readonly string[]>;
        resolve6(hostname: string): Promise<readonly string[]>;
        resolveAny(hostname: string): Promise<readonly AnyRecord[]>;
        resolveCaa(hostname: string): Promise<readonly AnyRecord[]>;
        resolveCname(hostname: string): Promise<readonly string[]>;
        resolveMx(hostname: string): Promise<readonly AnyRecord[]>;
        resolveNaptr(hostname: string): Promise<readonly AnyRecord[]>;
        resolveNs(hostname: string): Promise<readonly string[]>;
        resolvePtr(hostname: string): Promise<readonly string[]>;
        resolveSoa(hostname: string): Promise<AnyRecord>;
        resolveSrv(hostname: string): Promise<readonly AnyRecord[]>;
        resolveTxt(hostname: string): Promise<readonly string[][]>;
        reverse(ip: string): Promise<readonly string[]>;
      }

      export function lookup(hostname: string, options: LookupAllOptions): Promise<readonly LookupAddress[]>;
      export function lookup(hostname: string, options?: Family | LookupOneOptions): Promise<LookupAddress>;
      export function lookupService(address: string, port: number): Promise<{ hostname: string; service: string }>;
      export function resolve(hostname: string): Promise<readonly string[]>;
      export function resolve4(hostname: string): Promise<readonly string[]>;
      export function resolve6(hostname: string): Promise<readonly string[]>;
      export function resolveAny(hostname: string): Promise<readonly AnyRecord[]>;
      export function resolveCaa(hostname: string): Promise<readonly AnyRecord[]>;
      export function resolveCname(hostname: string): Promise<readonly string[]>;
      export function resolveMx(hostname: string): Promise<readonly AnyRecord[]>;
      export function resolveNaptr(hostname: string): Promise<readonly AnyRecord[]>;
      export function resolveNs(hostname: string): Promise<readonly string[]>;
      export function resolvePtr(hostname: string): Promise<readonly string[]>;
      export function resolveSoa(hostname: string): Promise<AnyRecord>;
      export function resolveSrv(hostname: string): Promise<readonly AnyRecord[]>;
      export function resolveTxt(hostname: string): Promise<readonly string[][]>;
      export function reverse(ip: string): Promise<readonly string[]>;
      export function getServers(): readonly string[];
      export function setServers(servers: readonly string[]): void;
      export function getDefaultResultOrder(): ResultOrder;
      export function setDefaultResultOrder(order: ResultOrder): void;
    }

    export function lookup(hostname: string, options: LookupAllOptions, callback: (error: Error | null, addresses: readonly LookupAddress[]) => void): void;
    export function lookup(hostname: string, options: Family | LookupOneOptions | undefined, callback: (error: Error | null, address: string, family: 4 | 6) => void): void;
    export function lookup(hostname: string, callback: (error: Error | null, address: string, family: 4 | 6) => void): void;
    export function lookupService(address: string, port: number, callback: (error: Error | null, hostname: string, service: string) => void): void;
    export function resolve(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
    export function resolve4(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
    export function resolve6(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
    export function resolveAny(hostname: string, callback: (error: Error | null, addresses?: readonly AnyRecord[]) => void): void;
    export function resolveCaa(hostname: string, callback: (error: Error | null, addresses?: readonly AnyRecord[]) => void): void;
    export function resolveCname(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
    export function resolveMx(hostname: string, callback: (error: Error | null, addresses?: readonly AnyRecord[]) => void): void;
    export function resolveNaptr(hostname: string, callback: (error: Error | null, addresses?: readonly AnyRecord[]) => void): void;
    export function resolveNs(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
    export function resolvePtr(hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void): void;
    export function resolveSoa(hostname: string, callback: (error: Error | null, address?: AnyRecord) => void): void;
    export function resolveSrv(hostname: string, callback: (error: Error | null, addresses?: readonly AnyRecord[]) => void): void;
    export function resolveTxt(hostname: string, callback: (error: Error | null, addresses?: readonly string[][]) => void): void;
    export function reverse(ip: string, callback: (error: Error | null, hostnames?: readonly string[]) => void): void;
    export function getServers(): readonly string[];
    export function setServers(servers: readonly string[]): void;
    export function getDefaultResultOrder(): ResultOrder;
    export function setDefaultResultOrder(order: ResultOrder): void;

    export const NODATA: "ENODATA";
    export const FORMERR: "EFORMERR";
    export const SERVFAIL: "ESERVFAIL";
    export const NOTFOUND: "ENOTFOUND";
    export const NOTIMP: "ENOTIMP";
    export const REFUSED: "EREFUSED";
    export const BADQUERY: "EBADQUERY";
    export const BADNAME: "EBADNAME";
    export const BADFAMILY: "EBADFAMILY";
    export const BADRESP: "EBADRESP";
    export const CONNREFUSED: "ECONNREFUSED";
    export const TIMEOUT: "ETIMEOUT";
    export const EOF: "EOF";
    export const ADDRCONFIG: 32;
    export const ALL: 16;
    export const V4MAPPED: 8;
  }

  export = dns;
}

declare module "node:dns" {
  import dns = require("dns");
  export = dns;
}

declare module "dns/promises" {
  import dns = require("dns");

  namespace dnsPromises {
    export import Resolver = dns.promises.Resolver;
    export import LookupAddress = dns.LookupAddress;
    export import LookupAllOptions = dns.LookupAllOptions;
    export import LookupOneOptions = dns.LookupOneOptions;
    export import LookupOptions = dns.LookupOptions;
    export import ResultOrder = dns.ResultOrder;
    export import AnyRecord = dns.AnyRecord;

    export function lookup(hostname: string, options: LookupAllOptions): Promise<readonly LookupAddress[]>;
    export function lookup(hostname: string, options?: dns.Family | LookupOneOptions): Promise<LookupAddress>;
    export function lookupService(address: string, port: number): Promise<{ hostname: string; service: string }>;
    export function resolve(hostname: string): Promise<readonly string[]>;
    export function resolve4(hostname: string): Promise<readonly string[]>;
    export function resolve6(hostname: string): Promise<readonly string[]>;
    export function resolveAny(hostname: string): Promise<readonly AnyRecord[]>;
    export function resolveCaa(hostname: string): Promise<readonly AnyRecord[]>;
    export function resolveCname(hostname: string): Promise<readonly string[]>;
    export function resolveMx(hostname: string): Promise<readonly AnyRecord[]>;
    export function resolveNaptr(hostname: string): Promise<readonly AnyRecord[]>;
    export function resolveNs(hostname: string): Promise<readonly string[]>;
    export function resolvePtr(hostname: string): Promise<readonly string[]>;
    export function resolveSoa(hostname: string): Promise<AnyRecord>;
    export function resolveSrv(hostname: string): Promise<readonly AnyRecord[]>;
    export function resolveTxt(hostname: string): Promise<readonly string[][]>;
    export function reverse(ip: string): Promise<readonly string[]>;
    export function getServers(): readonly string[];
    export function setServers(servers: readonly string[]): void;
    export function getDefaultResultOrder(): ResultOrder;
    export function setDefaultResultOrder(order: ResultOrder): void;
  }

  export = dnsPromises;
}

declare module "node:dns/promises" {
  import dnsPromises = require("dns/promises");
  export = dnsPromises;
}
