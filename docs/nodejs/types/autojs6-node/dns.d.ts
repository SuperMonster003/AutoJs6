declare module "dns" {
  namespace dns {
    export type Family = 0 | 4 | 6;
    export type ResultOrder = "verbatim" | "ipv4first" | "ipv6first";
    export type RecordType = keyof ResolveRecordMap;
    export type ResolveCallback<T> = (error: Error | null, addresses?: T) => void;
    export type ResolveRecordFor<T extends string> = T extends keyof ResolveRecordMap ? ResolveRecordMap[T] : ResolveResult;
    export type ResolveResult =
      | readonly string[]
      | readonly (readonly string[])[]
      | readonly AnyRecord[]
      | readonly CaaRecord[]
      | readonly MxRecord[]
      | readonly NaptrRecord[]
      | SoaRecord
      | readonly SrvRecord[]
      | readonly TlsaRecord[];
    export type AnyRecord =
      | AnyARecord
      | AnyAaaaRecord
      | AnyCaaRecord
      | AnyCnameRecord
      | AnyMxRecord
      | AnyNaptrRecord
      | AnyNsRecord
      | AnyPtrRecord
      | AnySoaRecord
      | AnySrvRecord
      | AnyTlsaRecord
      | AnyTxtRecord;

    export interface LookupAddress {
      address: string;
      family: 4 | 6;
    }

    export interface ResolverOptions {
      timeout?: number;
      tries?: number;
      maxTimeout?: number;
    }

    export interface ResolveOptions {
      ttl?: boolean;
    }

    export interface ResolveWithTtlOptions {
      ttl: true;
    }

    export interface ResolveAddressWithTtl {
      address: string;
      ttl: number;
    }

    export interface MxRecord {
      exchange: string;
      priority: number;
    }

    export interface NaptrRecord {
      flags: string;
      service: string;
      regexp: string;
      replacement: string;
      order: number;
      preference: number;
    }

    export interface SoaRecord {
      nsname: string;
      hostmaster: string;
      serial: number;
      refresh: number;
      retry: number;
      expire: number;
      minttl: number;
    }

    export interface SrvRecord {
      priority: number;
      weight: number;
      port: number;
      name: string;
    }

    export interface CaaRecord {
      critical: number;
      issue?: string;
      issuewild?: string;
      iodef?: string;
    }

    export interface TlsaRecord {
      certUsage: number;
      selector: number;
      match: number;
      data: ArrayBuffer;
    }

    export interface AnyARecord extends ResolveAddressWithTtl {
      type: "A";
    }

    export interface AnyAaaaRecord extends ResolveAddressWithTtl {
      type: "AAAA";
    }

    export interface AnyCaaRecord extends CaaRecord {
      type: "CAA";
    }

    export interface AnyCnameRecord {
      type: "CNAME";
      value: string;
    }

    export interface AnyMxRecord extends MxRecord {
      type: "MX";
    }

    export interface AnyNaptrRecord extends NaptrRecord {
      type: "NAPTR";
    }

    export interface AnyNsRecord {
      type: "NS";
      value: string;
    }

    export interface AnyPtrRecord {
      type: "PTR";
      value: string;
    }

    export interface AnySoaRecord extends SoaRecord {
      type: "SOA";
    }

    export interface AnySrvRecord extends SrvRecord {
      type: "SRV";
    }

    export interface AnyTlsaRecord extends TlsaRecord {
      type: "TLSA";
    }

    export interface AnyTxtRecord {
      type: "TXT";
      entries: readonly string[];
    }

    export interface ResolveRecordMap {
      A: readonly string[];
      AAAA: readonly string[];
      ANY: readonly AnyRecord[];
      CAA: readonly CaaRecord[];
      CNAME: readonly string[];
      MX: readonly MxRecord[];
      NAPTR: readonly NaptrRecord[];
      NS: readonly string[];
      PTR: readonly string[];
      SOA: SoaRecord;
      SRV: readonly SrvRecord[];
      TLSA: readonly TlsaRecord[];
      TXT: readonly (readonly string[])[];
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
      constructor(options?: ResolverOptions);
      cancel(): void;
      getServers(): readonly string[];
      setLocalAddress(ipv4?: string, ipv6?: string): void;
      setServers(servers: readonly string[]): void;
      resolve(hostname: string, callback: ResolveCallback<readonly string[]>): void;
      resolve<T extends string>(hostname: string, rrtype: T, callback: ResolveCallback<ResolveRecordFor<T>>): void;
      resolve4(hostname: string, callback: ResolveCallback<readonly string[]>): void;
      resolve4(hostname: string, options: ResolveWithTtlOptions, callback: ResolveCallback<readonly ResolveAddressWithTtl[]>): void;
      resolve4(hostname: string, options: ResolveOptions, callback: ResolveCallback<readonly string[]>): void;
      resolve6(hostname: string, callback: ResolveCallback<readonly string[]>): void;
      resolve6(hostname: string, options: ResolveWithTtlOptions, callback: ResolveCallback<readonly ResolveAddressWithTtl[]>): void;
      resolve6(hostname: string, options: ResolveOptions, callback: ResolveCallback<readonly string[]>): void;
      resolveAny(hostname: string, callback: ResolveCallback<readonly AnyRecord[]>): void;
      resolveCaa(hostname: string, callback: ResolveCallback<readonly CaaRecord[]>): void;
      resolveCname(hostname: string, callback: ResolveCallback<readonly string[]>): void;
      resolveMx(hostname: string, callback: ResolveCallback<readonly MxRecord[]>): void;
      resolveNaptr(hostname: string, callback: ResolveCallback<readonly NaptrRecord[]>): void;
      resolveNs(hostname: string, callback: ResolveCallback<readonly string[]>): void;
      resolvePtr(hostname: string, callback: ResolveCallback<readonly string[]>): void;
      resolveSoa(hostname: string, callback: ResolveCallback<SoaRecord>): void;
      resolveSrv(hostname: string, callback: ResolveCallback<readonly SrvRecord[]>): void;
      resolveTlsa(hostname: string, callback: ResolveCallback<readonly TlsaRecord[]>): void;
      resolveTxt(hostname: string, callback: ResolveCallback<readonly (readonly string[])[]>): void;
      reverse(ip: string, callback: ResolveCallback<readonly string[]>): void;
    }

    export namespace promises {
      export class Resolver {
        constructor(options?: ResolverOptions);
        cancel(): void;
        getServers(): readonly string[];
        setLocalAddress(ipv4?: string, ipv6?: string): void;
        setServers(servers: readonly string[]): void;
        resolve(hostname: string): Promise<readonly string[]>;
        resolve<T extends string>(hostname: string, rrtype: T): Promise<ResolveRecordFor<T>>;
        resolve4(hostname: string, options: ResolveWithTtlOptions): Promise<readonly ResolveAddressWithTtl[]>;
        resolve4(hostname: string, options?: ResolveOptions): Promise<readonly string[]>;
        resolve6(hostname: string, options: ResolveWithTtlOptions): Promise<readonly ResolveAddressWithTtl[]>;
        resolve6(hostname: string, options?: ResolveOptions): Promise<readonly string[]>;
        resolveAny(hostname: string): Promise<readonly AnyRecord[]>;
        resolveCaa(hostname: string): Promise<readonly CaaRecord[]>;
        resolveCname(hostname: string): Promise<readonly string[]>;
        resolveMx(hostname: string): Promise<readonly MxRecord[]>;
        resolveNaptr(hostname: string): Promise<readonly NaptrRecord[]>;
        resolveNs(hostname: string): Promise<readonly string[]>;
        resolvePtr(hostname: string): Promise<readonly string[]>;
        resolveSoa(hostname: string): Promise<SoaRecord>;
        resolveSrv(hostname: string): Promise<readonly SrvRecord[]>;
        resolveTlsa(hostname: string): Promise<readonly TlsaRecord[]>;
        resolveTxt(hostname: string): Promise<readonly (readonly string[])[]>;
        reverse(ip: string): Promise<readonly string[]>;
      }

      export function lookup(hostname: string, options: LookupAllOptions): Promise<readonly LookupAddress[]>;
      export function lookup(hostname: string, options?: Family | LookupOneOptions): Promise<LookupAddress>;
      export function lookupService(address: string, port: number): Promise<{ hostname: string; service: string }>;
      export function resolve(hostname: string): Promise<readonly string[]>;
      export function resolve<T extends string>(hostname: string, rrtype: T): Promise<ResolveRecordFor<T>>;
      export function resolve4(hostname: string, options: ResolveWithTtlOptions): Promise<readonly ResolveAddressWithTtl[]>;
      export function resolve4(hostname: string, options?: ResolveOptions): Promise<readonly string[]>;
      export function resolve6(hostname: string, options: ResolveWithTtlOptions): Promise<readonly ResolveAddressWithTtl[]>;
      export function resolve6(hostname: string, options?: ResolveOptions): Promise<readonly string[]>;
      export function resolveAny(hostname: string): Promise<readonly AnyRecord[]>;
      export function resolveCaa(hostname: string): Promise<readonly CaaRecord[]>;
      export function resolveCname(hostname: string): Promise<readonly string[]>;
      export function resolveMx(hostname: string): Promise<readonly MxRecord[]>;
      export function resolveNaptr(hostname: string): Promise<readonly NaptrRecord[]>;
      export function resolveNs(hostname: string): Promise<readonly string[]>;
      export function resolvePtr(hostname: string): Promise<readonly string[]>;
      export function resolveSoa(hostname: string): Promise<SoaRecord>;
      export function resolveSrv(hostname: string): Promise<readonly SrvRecord[]>;
      export function resolveTlsa(hostname: string): Promise<readonly TlsaRecord[]>;
      export function resolveTxt(hostname: string): Promise<readonly (readonly string[])[]>;
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
    export function resolve(hostname: string, callback: ResolveCallback<readonly string[]>): void;
    export function resolve<T extends string>(hostname: string, rrtype: T, callback: ResolveCallback<ResolveRecordFor<T>>): void;
    export function resolve4(hostname: string, callback: ResolveCallback<readonly string[]>): void;
    export function resolve4(hostname: string, options: ResolveWithTtlOptions, callback: ResolveCallback<readonly ResolveAddressWithTtl[]>): void;
    export function resolve4(hostname: string, options: ResolveOptions, callback: ResolveCallback<readonly string[]>): void;
    export function resolve6(hostname: string, callback: ResolveCallback<readonly string[]>): void;
    export function resolve6(hostname: string, options: ResolveWithTtlOptions, callback: ResolveCallback<readonly ResolveAddressWithTtl[]>): void;
    export function resolve6(hostname: string, options: ResolveOptions, callback: ResolveCallback<readonly string[]>): void;
    export function resolveAny(hostname: string, callback: ResolveCallback<readonly AnyRecord[]>): void;
    export function resolveCaa(hostname: string, callback: ResolveCallback<readonly CaaRecord[]>): void;
    export function resolveCname(hostname: string, callback: ResolveCallback<readonly string[]>): void;
    export function resolveMx(hostname: string, callback: ResolveCallback<readonly MxRecord[]>): void;
    export function resolveNaptr(hostname: string, callback: ResolveCallback<readonly NaptrRecord[]>): void;
    export function resolveNs(hostname: string, callback: ResolveCallback<readonly string[]>): void;
    export function resolvePtr(hostname: string, callback: ResolveCallback<readonly string[]>): void;
    export function resolveSoa(hostname: string, callback: ResolveCallback<SoaRecord>): void;
    export function resolveSrv(hostname: string, callback: ResolveCallback<readonly SrvRecord[]>): void;
    export function resolveTlsa(hostname: string, callback: ResolveCallback<readonly TlsaRecord[]>): void;
    export function resolveTxt(hostname: string, callback: ResolveCallback<readonly (readonly string[])[]>): void;
    export function reverse(ip: string, callback: ResolveCallback<readonly string[]>): void;
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
    export const FILE: "EFILE";
    export const NOMEM: "ENOMEM";
    export const DESTRUCTION: "EDESTRUCTION";
    export const BADSTR: "EBADSTR";
    export const BADFLAGS: "EBADFLAGS";
    export const NONAME: "ENONAME";
    export const BADHINTS: "EBADHINTS";
    export const NOTINITIALIZED: "ENOTINITIALIZED";
    export const LOADIPHLPAPI: "ELOADIPHLPAPI";
    export const ADDRGETNETWORKPARAMS: "EADDRGETNETWORKPARAMS";
    export const CANCELLED: "ECANCELLED";
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
    export import AnyARecord = dns.AnyARecord;
    export import AnyAaaaRecord = dns.AnyAaaaRecord;
    export import AnyCaaRecord = dns.AnyCaaRecord;
    export import AnyCnameRecord = dns.AnyCnameRecord;
    export import AnyMxRecord = dns.AnyMxRecord;
    export import AnyNaptrRecord = dns.AnyNaptrRecord;
    export import AnyNsRecord = dns.AnyNsRecord;
    export import AnyPtrRecord = dns.AnyPtrRecord;
    export import AnyRecord = dns.AnyRecord;
    export import AnySoaRecord = dns.AnySoaRecord;
    export import AnySrvRecord = dns.AnySrvRecord;
    export import AnyTlsaRecord = dns.AnyTlsaRecord;
    export import AnyTxtRecord = dns.AnyTxtRecord;
    export import CaaRecord = dns.CaaRecord;
    export import Family = dns.Family;
    export import LookupAddress = dns.LookupAddress;
    export import LookupAllOptions = dns.LookupAllOptions;
    export import LookupOneOptions = dns.LookupOneOptions;
    export import LookupOptions = dns.LookupOptions;
    export import MxRecord = dns.MxRecord;
    export import NaptrRecord = dns.NaptrRecord;
    export import RecordType = dns.RecordType;
    export import ResolveAddressWithTtl = dns.ResolveAddressWithTtl;
    export import ResolveOptions = dns.ResolveOptions;
    export import ResolveRecordFor = dns.ResolveRecordFor;
    export import ResolveRecordMap = dns.ResolveRecordMap;
    export import ResolveResult = dns.ResolveResult;
    export import ResolveWithTtlOptions = dns.ResolveWithTtlOptions;
    export import ResolverOptions = dns.ResolverOptions;
    export import ResultOrder = dns.ResultOrder;
    export import SoaRecord = dns.SoaRecord;
    export import SrvRecord = dns.SrvRecord;
    export import TlsaRecord = dns.TlsaRecord;

    export function lookup(hostname: string, options: LookupAllOptions): Promise<readonly LookupAddress[]>;
    export function lookup(hostname: string, options?: Family | LookupOneOptions): Promise<LookupAddress>;
    export function lookupService(address: string, port: number): Promise<{ hostname: string; service: string }>;
    export function resolve(hostname: string): Promise<readonly string[]>;
    export function resolve<T extends string>(hostname: string, rrtype: T): Promise<ResolveRecordFor<T>>;
    export function resolve4(hostname: string, options: ResolveWithTtlOptions): Promise<readonly ResolveAddressWithTtl[]>;
    export function resolve4(hostname: string, options?: ResolveOptions): Promise<readonly string[]>;
    export function resolve6(hostname: string, options: ResolveWithTtlOptions): Promise<readonly ResolveAddressWithTtl[]>;
    export function resolve6(hostname: string, options?: ResolveOptions): Promise<readonly string[]>;
    export function resolveAny(hostname: string): Promise<readonly AnyRecord[]>;
    export function resolveCaa(hostname: string): Promise<readonly CaaRecord[]>;
    export function resolveCname(hostname: string): Promise<readonly string[]>;
    export function resolveMx(hostname: string): Promise<readonly MxRecord[]>;
    export function resolveNaptr(hostname: string): Promise<readonly NaptrRecord[]>;
    export function resolveNs(hostname: string): Promise<readonly string[]>;
    export function resolvePtr(hostname: string): Promise<readonly string[]>;
    export function resolveSoa(hostname: string): Promise<SoaRecord>;
    export function resolveSrv(hostname: string): Promise<readonly SrvRecord[]>;
    export function resolveTlsa(hostname: string): Promise<readonly TlsaRecord[]>;
    export function resolveTxt(hostname: string): Promise<readonly (readonly string[])[]>;
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
