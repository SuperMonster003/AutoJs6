# Shared manifest UID parsing evidence

Date: 2026-10-02.

`ManifestSummary` adds a nullable trailing `sharedUserId` with default null. An overload preserves
the original thirteen-argument JVM constructor. Existing component order is unchanged; this is
not a claim that a Kotlin data class's generated copy methods have unchanged binary descriptors.
Consumers of the staged AAR are recompiled with that AAR.

Security-relevant root attributes are read from the actual XML root using a bounded,
namespace-aware SAX reader. Comments cannot supply a fake package or manifest root. Only the
Android namespace supplies sharedUserId; unrelated namespaces cannot shadow it. Duplicate
expanded names, unbound prefixes, external document/entity declarations and nonliteral or
oversized shared UID values fail instead of producing an absent shared UID.

The APK binary XML and AAB protobuf decoders verify the Android sharedUserId resource ID,
namespace, name and literal. A missing display name may be recovered from the exact resource ID;
contradictory names or namespaces are refused. Duplicate attributes and inconsistent raw versus
compiled values are refused. No resources.arsc loading or resource-reference guessing is added.

An uncertain parse throws IOException through the existing failure path. For a standalone APK,
inspect fails before returning a summary; callers must not convert that exception to an empty
manifest. Container problems retain their existing handling. Null means a verified root did not
declare an Android shared UID; it is not an alias for a parse failure. Other display-oriented
child-element parsing is outside this change.

`build/p9-host-parser-verify.log` records 54 parser JVM tests with zero failures/errors/skips and
release AAR assembly. New coverage includes actual binary/protobuf fixtures, resource identity,
typed/raw disagreement, duplicate and foreign attributes, XML comments, forbidden DTDs and the
old constructor. The release AAR is 272,518 bytes, SHA-256
`4e06ff8ae204d57b836778dc183d32c31bd5677a34a7cdd154479c6051e7a47f`.

Android SAX/device compatibility must be checked by the consuming plugin, including API 24.
JVM checks alone do not establish that device result. This change adds parser metadata and
strict refusal, not an independent host blacklist UI or a remote publication.
