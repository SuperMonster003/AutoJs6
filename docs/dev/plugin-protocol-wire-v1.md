# Plugin Protocol Wire V1

This document freezes the common binary envelope used by the DEX Compiler and
AI protocol packs. It is a build-time contract. It does not enable either
plugin family in the host by itself.

## Goals

The wire format provides:

- Deterministic field ordering and byte representation.
- Explicit schema major and minor versions.
- Unknown optional field skipping.
- Unknown required field rejection.
- Strict size, field-count, scalar-shape, and UTF-8 validation.
- Descriptor references without embedding paths, URIs, or unbounded content.
- Pure Kotlin encoding and decoding without `Bundle`, Java serialization,
  arbitrary Parcelable classes, or implementation dependencies.

The encoded document is carried by AIDL as `byte[]`. Large content is carried
by an explicitly declared `ParcelFileDescriptor` parameter or descriptor
array. A descriptor is never serialized into the document.

## Byte Order And Header

All integers and floating-point values use network byte order, which is big
endian. Every document begins with this fixed 28-byte header:

| Offset | Size | Value |
| ---: | ---: | --- |
| 0 | 4 | Magic `0x414A3657`, ASCII `AJ6W` |
| 4 | 4 | Wire format version, currently `1` |
| 8 | 4 | Total document size including the header |
| 12 | 4 | Positive schema ID |
| 16 | 4 | Positive schema major version |
| 20 | 4 | Non-negative schema minor version |
| 24 | 4 | Non-negative field count |

The declared size must exactly match the supplied byte array. Truncated data
and trailing bytes are rejected.

## Field Encoding

Each field has a fixed 16-byte header followed by its payload:

| Offset | Size | Value |
| ---: | ---: | --- |
| 0 | 4 | Positive field tag |
| 4 | 4 | Positive wire type |
| 8 | 4 | Flags |
| 12 | 4 | Non-negative payload size |
| 16 | N | Payload |

Fields are ordered by ascending tag. Repeated values use the same tag and
retain insertion order. A decoder rejects decreasing tags. Duplicate scalar
fields are rejected by the schema codec, while explicitly repeated fields are
accepted.

V1 defines these wire types:

| ID | Type | Payload |
| ---: | --- | --- |
| 1 | `INT32` | One signed 32-bit integer |
| 2 | `INT64` | One signed 64-bit integer |
| 3 | `BOOLEAN` | Exactly one byte, `0` or `1` |
| 4 | `UTF8` | Strict UTF-8 without replacement decoding |
| 5 | `BYTES` | Bounded opaque bytes defined by the schema |
| 6 | `DOCUMENT` | One nested tagged document |
| 7 | `FD_REF` | One non-negative descriptor-array index |
| 8 | `FLOAT64` | One finite IEEE 754 binary64 value |

The only V1 flag is `REQUIRED_FOR_READER`, value `1`. A reader that does not
recognize a field carrying this flag must reject the whole message. Unknown
fields without the flag are skipped by their declared length. Unknown flag
bits are rejected.

## Schema Evolution

A compatible schema minor may add optional fields, add enum values guarded by
capability negotiation, or raise provider-advertised limits without exceeding
the host ceiling. A reader ignores unknown optional fields.

A new required field, changed lifecycle rule, changed field meaning, removed
field, incompatible enum behavior, or different credential/security boundary
requires a schema major version. Marking an unknown field as required makes an
older reader fail closed but does not turn an incompatible change into a minor
change.

Schema codecs must:

1. Check the exact schema ID and supported major version.
2. Reject unknown required fields.
3. Validate cardinality for every known tag.
4. Read every required field with its exact wire type.
5. Apply semantic quotas after structural decoding.
6. Reject unsupported required capabilities before dispatch.

## Descriptor References

`FD_REF` indexes refer only to the descriptor array on the same AIDL method.
The payload record carries MIME type, declared length, optional SHA-256, and
encoding metadata. The containing schema and AIDL method fix the descriptor's
read/write direction and semantic purpose; neither is caller-selectable text.

Before asynchronous dispatch, the receiver validates the envelope and descriptor
count, copies metadata, and duplicates every supplied descriptor into receiver-owned
lifetime. Exact reference accounting may run on the bounded worker, but it completes
before any descriptor content is consumed:

- Every index is in range.
- No descriptor index is referenced more than once.
- Every supplied descriptor is referenced exactly once.
- The declared access mode matches the method contract.
- Every descriptor is duplicated before asynchronous use.
- Every owned descriptor is closed on success, failure, cancellation, timeout,
  Binder death, and session close.

Duplicating a descriptor creates independent lifetime ownership but preserves
the same open-file position. Once dispatched, only the designated reader or
writer may use that open file description. A reliable pipe consumer checks the
peer error after EOF.

## Default Structural Limits

The shared codec defaults are intentionally smaller than Binder's process-wide
transaction buffer:

| Resource | Limit |
| --- | ---: |
| Document bytes | 256 KiB |
| One field payload | 256 KiB |
| Fields per document | 1,024 |

Each protocol applies smaller message-specific limits where appropriate. The
effective limit is always the smallest of the shared codec limit, host policy,
provider capability, request limit, and operation-specific limit.

Lengths and aggregate sizes are checked before allocation where possible.
Negative values, integer overflow, malformed scalar sizes, non-canonical
booleans, non-finite floating-point values, malformed UTF-8, and oversized
payloads are rejected.

## Binder Framing Boundary

The current build-tools-generated AIDL stubs do not call
`Parcel.enforceNoDataAvail()`. Consequently the contract must not claim that a
generated stub rejects trailing Binder data before invoking the implementation.

The public methods expose only declared byte arrays, descriptor arrays, and
callback/session Binder interfaces, so trailing objects are not visible to the
protocol implementation. Android owns cleanup of the transaction Parcel. A
future strict preflight stub or manually dispatched Binder endpoint is required
before strict trailing-data rejection can become a conformance claim.

This limitation does not relax envelope validation, caller UID checks, service
permissions, signer policy, identity pinning, descriptor accounting, or the
rule that dispatched billable/stateful operations are never retried
automatically.

## Golden Corpus

Every protocol module keeps canonical encoded fixtures for its current schema.
The conformance suite must cover:

- Current schema round trips and byte-for-byte canonical output.
- A simulated future minor containing an unknown optional field.
- An unknown required field.
- An incompatible major version.
- Truncation at every header boundary.
- Declared-size, field-count, field-length, and type mismatches.
- Duplicate scalar fields and out-of-order tags.
- Invalid UTF-8, invalid booleans, non-finite floats, and invalid descriptor
  references.

The first release cannot claim runtime compatibility with a historical AAR
that did not exist. Starting with V1.1, the matrix must compile and run the V1.0
golden corpus and frozen public API artifact against the current reader.
