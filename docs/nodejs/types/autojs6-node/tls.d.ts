declare module "tls" {
  import net = require("net");

  namespace tls {
    export interface SecureContextOptions {
      minVersion?: string;
      maxVersion?: string;
      secureProtocol?: string;
      servername?: string;
      requestCert?: boolean;
      rejectUnauthorized?: boolean;
      [key: string]: unknown;
    }

    export interface TLSSocketOptions extends net.SocketConstructorOpts {
      servername?: string;
      requestCert?: boolean;
      rejectUnauthorized?: boolean;
    }

    export interface PeerCertificate {
      readonly subject?: Readonly<Record<string, unknown>>;
      readonly issuer?: Readonly<Record<string, unknown>>;
      readonly subjectaltname?: string;
      readonly infoAccess?: Readonly<Record<string, unknown>>;
      readonly modulus?: string;
      readonly bits?: number;
      readonly exponent?: string;
      readonly pubkey?: Uint8Array;
      readonly valid_from?: string;
      readonly valid_to?: string;
      readonly fingerprint?: string;
      readonly fingerprint256?: string;
      readonly fingerprint512?: string;
      readonly serialNumber?: string;
      readonly raw?: Uint8Array;
      readonly ext_key_usage?: readonly string[];
    }

    export interface CipherNameAndProtocol {
      readonly name: string;
      readonly standardName: string;
      readonly version: string;
    }

    export class SecureContext {
      constructor(options?: SecureContextOptions);
      readonly context: Readonly<SecureContextOptions>;
      readonly options: Readonly<SecureContextOptions>;
      readonly secureProtocol: string;
    }

    export class TLSSocket extends net.Socket {
      constructor(socket?: net.Socket | TLSSocketOptions, options?: TLSSocketOptions);
      encrypted: boolean;
      authorized: boolean;
      authorizationError: string | null;
      alpnProtocol: string | false;
      servername?: string;
      ssl: null;
      secureConnecting: boolean;
      connect(...args: readonly unknown[]): never;
      renegotiate(options?: unknown, callback?: (error: Error | null) => void): boolean;
      renegotiate(callback?: (error: Error | null) => void): boolean;
      getPeerCertificate(detailed?: boolean): PeerCertificate;
      getCertificate(): PeerCertificate;
      getCipher(): CipherNameAndProtocol;
      getSharedSigalgs(): readonly string[];
      getProtocol(): string | null;
      getSession(): Uint8Array | undefined;
      getTLSTicket(): Uint8Array | undefined;
      getEphemeralKeyInfo(): Readonly<Record<string, never>>;
      getFinished(): Uint8Array | undefined;
      getPeerFinished(): Uint8Array | undefined;
      exportKeyingMaterial(length: number, label: string, context?: Uint8Array): never;
      enableTrace(): void;
      setMaxSendFragment(size: number): boolean;
    }

    export class Server extends net.Server {
      constructor(options?: SecureContextOptions, secureConnectionListener?: (socket: TLSSocket) => void);
      requestCert: boolean;
      rejectUnauthorized: boolean;
      secureContext: SecureContext;
      listen(...args: readonly unknown[]): never;
      setSecureContext(options?: SecureContextOptions): this;
      addContext(hostname: string, context?: SecureContext | SecureContextOptions): this;
      getTicketKeys(): Uint8Array;
      setTicketKeys(keys: { readonly length: number }): this;
    }

    export const CLIENT_RENEG_LIMIT: number;
    export const CLIENT_RENEG_WINDOW: number;
    export const DEFAULT_CIPHERS: string;
    export const DEFAULT_ECDH_CURVE: string;
    export const DEFAULT_MAX_VERSION: string;
    export const DEFAULT_MIN_VERSION: string;
    export const rootCertificates: readonly string[];
    export function checkServerIdentity(hostname: string, cert: PeerCertificate | Readonly<Record<string, unknown>>): Error | undefined;
    export function connect(...args: readonly unknown[]): never;
    export function createConnection(...args: readonly unknown[]): never;
    export function createSecureContext(options?: SecureContextOptions): SecureContext;
    export function createServer(options?: SecureContextOptions, secureConnectionListener?: (socket: TLSSocket) => void): Server;
    export function getCiphers(): readonly string[];
  }

  export = tls;
}

declare module "node:tls" {
  import tls = require("tls");
  export = tls;
}
