# Publishing the plugin contract libraries to Maven Central

MCP Server roadmap P12, decision D58: third-party plugins depend on the contract libraries from Maven Central instead
of copying host modules.

| Module | Coordinates | Content |
|---|---|---|
| `plugin-api/common-plugin-api` | `io.github.supermonster003:autojs6-common-plugin-api` | `PluginInfo` and other types shared by every plugin |
| `plugin-api/plugin-tool-api` | `io.github.supermonster003:autojs6-plugin-tool-api` | the plugin tool contract ([plugin-tool-protocol-v1.md](plugin-tool-protocol-v1.md)) and the optional provider base class `PluginToolProviderService` (D59) |

The Java packages stay `org.autojs.plugin.common.api` and `org.autojs.plugin.tool.api`. `autojs6-plugin-tool-api`
depends on `autojs6-common-plugin-api` (API scope) and `kotlin-stdlib`. Consumers need `minSdk` 24 or higher and
`compileSdk` 36 or higher; Kotlin consumers need a Kotlin compiler that reads the metadata of the host's Kotlin
(2.4.0 for 1.0.0, so 2.3 or newer). Java consumers have no Kotlin requirement.

## Versions

- The library version is independent of the contract version. Contract version 1 only grows by appending keys, values
  and AIDL transactions, so library releases of contract version 1 stay compatible: minor versions add, patch versions
  fix. A new contract version would be a new major library version.
- Both libraries are released together with the same version. Set `extra["mavenVersion"]` in both module build files.

## Build

The configuration is shared by both modules in [`plugin-api/maven-central.gradle.kts`](../../plugin-api/maven-central.gradle.kts):
the release variant with sources and Javadoc jars, the POM (MPL 2.0, developer, SCM), a staging repository and a
`mavenCentralBundle` task per module.

```
gradlew :plugin-api:common-plugin-api:mavenCentralBundle :plugin-api:plugin-tool-api:mavenCentralBundle
```

- The publications land in `build/maven-central-staging` of the root project, with `.md5`, `.sha1`, `.sha256` and
  `.sha512` files. This directory also serves as a local Maven repository for testing a plugin against an unreleased
  version (the third-party sample repository reads it through the Gradle property `autojs6.pluginApiRepo`).
- The bundles land in `build/maven-central-bundles`, one zip per library, in the repository layout the Central Portal
  expects (no `maven-metadata.xml`).
- Without a signing key the artifacts are not signed, which is enough for local tests but refused by the Central Portal.

## One-time setup (maintainer)

1. Sign in at <https://central.sonatype.com> with the GitHub account `SuperMonster003`; the namespace
   `io.github.supermonster003` is then verified automatically. Otherwise add it under "Namespaces" and follow the
   verification steps (a temporary public GitHub repository named after the verification key).
2. Create a GPG key for signing and publish its public part, for example to `keyserver.ubuntu.com` and
   `keys.openpgp.org` (the Central Portal looks the key up there).
3. Put the signing configuration into `~/.gradle/gradle.properties` (never into the repository), one of:
   - `signing.gnupg.keyName=<key id>` to sign with the local `gpg` command;
   - `signing.keyId`, `signing.password` and `signing.secretKeyRingFile` for a key ring file;
   - `signingInMemoryKey` (ASCII-armored private key) and `signingInMemoryKeyPassword`, for example on CI.
4. Create a user token under "Account" > "Generate User Token" for uploads through the Publisher API (optional; the
   web page works without it).

## Release (maintainer)

1. Set the version in both module build files and run the two `mavenCentralBundle` tasks above with signing configured.
   Check that every artifact in the staging repository has an `.asc` file.
2. Upload `autojs6-common-plugin-api-<version>-bundle.zip` first, then `autojs6-plugin-tool-api-<version>-bundle.zip`:
   on the Central Portal under "Publish" > "Publish Component", or through the Publisher API:
   ```
   curl --request POST --header "Authorization: Bearer <base64 of user:token>" \
     --form bundle=@build/maven-central-bundles/autojs6-common-plugin-api-1.0.0-bundle.zip \
     "https://central.sonatype.com/api/v1/publisher/upload?publishingType=USER_MANAGED"
   ```
3. Wait for the validation of each deployment, then press "Publish". Releases cannot be removed or replaced afterwards;
   a mistake needs a new version.
4. Searching <https://central.sonatype.com> finds the libraries within minutes; `repo1.maven.org` may take up to about
   an hour. Then switch the third-party sample repository to the released version.

## Status

| Item | State |
|---|---|
| Publishing configuration, staging repository and bundles | done, verified locally (1.0.0, unsigned) |
| Central Portal account, namespace, GPG key | maintainer |
| First release 1.0.0 | waits for the maintainer and for the host release with contract version 1 (roadmap P12) |
