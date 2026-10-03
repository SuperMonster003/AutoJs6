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

`build/p9-host-parser-verify.log` records 62 parser JVM tests with zero failures/errors/skips and
release AAR assembly. New coverage includes actual binary/protobuf fixtures, resource identity,
typed/raw disagreement, duplicate and foreign attributes, XML comments, forbidden DTDs and the
old constructor. The release AAR is 272,518 bytes, SHA-256
`4e06ff8ae204d57b836778dc183d32c31bd5677a34a7cdd154479c6051e7a47f`.

Android SAX/device compatibility must be checked by the consuming plugin, including API 24.
JVM checks alone do not establish that device result. This change adds parser metadata and
strict refusal, not an independent host blacklist UI or a remote publication.

## Permission-declaration follow-up for P9.2

The preview list now comes from actual direct-child XML declarations, with Android-namespace
`name` attributes, rather than a regex over the rendered XML. Comments, CDATA, nested fake
declarations and another namespace's earlier `name` cannot invent or hide permissions. The
SAX traversal has node, depth, attribute and literal limits, rejects malformed trailing XML,
and preserves a sorted unique declaration list without claiming runtime grants or applying
device-specific grant policy.

Only `uses-permission`, `uses-permission-sdk-23`, and its supported `uses-permission-sdk-m`
alias are recognized. These cases are present in the official Android 35 source
`ParsingPackageUtils` (cases at lines 1085-1087), and in the
[AOSP parsing implementation](https://android.googlesource.com/platform/frameworks/base/+/8c8c81dd9e53dfa76ccbb6868e627c470fb5a59c/core/java/com/android/internal/pm/pkg/parsing/ParsingPackageUtils.java).
Unknown lookalike tags such as `uses-permission-sdk-99` do not become declarations. The public
[Android declaration guide](https://developer.android.com/training/permissions/declaring)
describes the API-specific declaration and maxSdkVersion behavior; the preview reports what
was declared, not what a given device has granted.

For binary APK XML and protobuf AAB XML, permission names also require the verified Android
`name` resource ID, consistent namespace/name, and matching raw/compiled literal values.
Duplicate or uncertain declarations fail instead of producing a misleading partial preview.
Protobuf validation occurs after the enclosing element is known, including when field order
places its name after its attributes. No public ManifestSummary shape or installer V3 method
changes are introduced by this follow-up.

`build/p9-permission-parser-final.log` passes the complete 69-test parser suite, with no
failures/errors/skips, and release AAR assembly. Seven new tests cover real-element selection,
comment/CDATA decoys, foreign attributes, malformed documents, binary/protobuf forged resource
IDs, duplicate attributes and raw/typed contradictions. Release AAR: 275,454 bytes, SHA-256
`41f9348190895b772b1b8af9aa2c82c5543f62646000598ca08e9b68360259a3`.

The parallel host task had changed working-tree version.properties to 5310 before this
verification. That unrelated uncommitted version change, other source work and the index
are preserved. This module AAR has no independent host versionCode; its exact module commit
and hash identify it, and 5310 is not presented here as a delivered host or minimum version.
The fixed host 5309 APK was not rebuilt. The existing current-version parser-hardening history
entry is retained; this follow-up does not sweep parallel changelog edits into its commit.
