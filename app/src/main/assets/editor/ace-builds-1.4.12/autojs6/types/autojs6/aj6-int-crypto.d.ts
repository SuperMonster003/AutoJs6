// Type definitions for AutoJs6 internal module crypto
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.7.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />
/// <reference lib="es2022" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/cryptyo/Crypto.kt
 * @Source /src/main/java/org/autojs/autojs/core/crypto/Crypto.kt
 */

declare namespace Internal {

    interface Crypto {

        readonly Key: typeof Crypto.Key;

        readonly KeyPair: typeof Crypto.KeyPair;

        fromHex(hex: string): number[];

        toHex(bytes: number[]): string;

        digest(message: string, algorithm?: Crypto.DigestAlgorithm, options?: Crypto.DigestOptions): Crypto.Output;
        digest(message: string, options: Crypto.DigestOptions): Crypto.Output;

        encrypt(data: Crypto.Input,
                key: Crypto.Key | java.security.Key,
                transformation: Crypto.CipherTransformation.All,
                options?: Crypto.CipherOptions,
        ): Crypto.Output;

        decrypt(data: Crypto.Input,
                key: Crypto.Key | java.security.Key,
                transformation: Crypto.CipherTransformation.All,
                options?: Crypto.CipherOptions,
        ): Crypto.Output;

        generateKeyPair(algorithm: Crypto.KeyPairGeneratorAlgorithm, length?: number): Crypto.KeyPair;

    }

    namespace Crypto {

        class Key extends org.autojs.autojs.core.crypto.Crypto.Key {

            constructor(data: Crypto.Input, options?: Crypto.KeyOptions);

            toKeySpec(transformation: Crypto.CipherTransformation.All | string): java.security.Key;

        }

        class KeyPair extends org.autojs.autojs.core.crypto.Crypto.KeyPair {

            constructor(publicKeyParam: Crypto.Input, privateKeyParam: Crypto.Input, options?: Crypto.KeyOptions);

            publicKey: org.autojs.autojs.core.crypto.Crypto.Key;
            privateKey: org.autojs.autojs.core.crypto.Crypto.Key;

        }

        interface InputDigestOptions {
            /** @default 'string' */
            input?: 'file' | 'base64' | 'hex' | 'string';
        }

        interface OutputDigestOptions {
            /** @default 'hex' */
            output?: 'bytes' | 'base64' | 'hex' | 'string';
        }

        interface BaseDigestOptions {
            /**
             * Available for **string** type for both input and output.
             *
             * @default 'UTF-8'
             */
            encoding?: 'US-ASCII' | 'ISO-8859-1' | 'UTF-8' | 'UTF-16BE' | 'UTF-16LE' | 'UTF-16';
        }

        interface DigestOptions extends InputDigestOptions, OutputDigestOptions, BaseDigestOptions {
            /* Empty body. */
        }

        // @ts-ignore
        interface CipherOptions extends DigestOptions {

            iv?: string | number[] | java.security.spec.AlgorithmParameterSpec;

            output?: 'bytes' | 'base64' | 'hex' | 'string' | 'file';

            dest?: string;

        }

        interface KeyOptions extends InputDigestOptions {
            keyPair?: 'public' | 'private';
        }

        type Input = string | number[];

        type Output = string | number[];

        type DigestAlgorithm = 'MD5' | `SHA-${1 | 224 | 256 | 384 | 512}`;

        /**
         * The following algorithms seem not available: RSASSA-PSS, X25519 and X448.
         */
        type KeyPairGeneratorAlgorithm = 'DiffieHellman' | 'DH' | 'DSA' | 'RSA' | 'EC' | 'XDH' | 'RSASSA-PSS' | 'X25519' | 'X448';

        namespace CipherTransformation {

            type AES = 'AES' |
                `AES/${'CBC' | 'CFB' | 'CTR' | 'CTS' | 'ECB' | 'OFB'}/${'ISO10126Padding' | 'NoPadding' | 'PKCS5Padding'}` |
                `AES/${'GCM'}/${'NoPadding'}`;

            type AES_128 = `AES_128` |
                `AES_128/${'CBC' | 'ECB'}/${'NoPadding' | 'PKCS5Padding'}` |
                `AES_128/${'GCM'}/${'NoPadding'}`;

            type AES_256 = `AES_256` |
                `AES_256/${'CBC' | 'ECB'}/${'NoPadding' | 'PKCS5Padding'}` |
                `AES_256/${'GCM'}/${'NoPadding'}`;

            type ARC4 = `ARC4` |
                `ARC4/${'ECB'}/${'NoPadding'}` |
                `ARC4/${'NONE'}/${'NoPadding'}`;

            type BLOWFISH = `BLOWFISH` |
                `BLOWFISH/${'CBC' | 'CFB' | 'CTR' | 'CTS' | 'ECB' | 'OFB'}/${'ISO10126Padding' | 'NoPadding' | 'PKCS5Padding'}`;

            type ChaCha20 = `ChaCha20` |
                `ChaCha20/${'NONE' | 'Poly1305'}/${'NoPadding'}`;

            type DES = `DES` |
                `DES/${'CBC' | 'CFB' | 'CTR' | 'CTS' | 'ECB' | 'OFB'}/${'ISO10126Padding' | 'NoPadding' | 'PKCS5Padding'}`;

            type DESede = `DESede` |
                `DESede/${'CBC' | 'CFB' | 'CTR' | 'CTS' | 'ECB' | 'OFB'}/${'ISO10126Padding' | 'NoPadding' | 'PKCS5Padding'}`;

            type RSA = `RSA` |
                `RSA/${'ECB' | 'NONE'}/${'NoPadding' | 'OAEPPadding' | 'PKCS1Padding' | `OAEPwithSHA-${1 | 224 | 256 | 384 | 512}andMGF1Padding`}`;

            type All = AES | AES_128 | AES_256 | ARC4 | BLOWFISH | ChaCha20 | DES | DESede | RSA;

        }

    }

}
