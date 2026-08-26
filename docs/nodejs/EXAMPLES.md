# AutoJs6 Node.js Example Projects

Last reviewed: 2026-08-26.

Examples live only under **sample/nodejs** in the runtime-plugin repository. Keeping
one copy prevents host/plugin drift and keeps runtime-specific assets out of the host
checkout and AndroidTest asset graph. Each example has project metadata, package
metadata, a documented entry, expected output, and a row in **examples.json**.

Node project entries run through the external runtime plugin and may call host APIs
only through declared bridge capabilities.

## Verification

Run from **AutoJs6-Plugin-NodeJs-Runtime**:

~~~powershell
.\gradlew.bat verifyNodePluginExamples --offline
~~~

Add or update an example only in the plugin repository. Type declarations and the
TypeScript smoke sample are checked there by `verifyAutoJs6NodeTypeDeclarations` and
`typeCheckNodeTypescriptSmoke`.

## Host Integration Smoke

**NodeHostPluginIntegrationInstrumentationTest** intentionally uses small inline
fixtures rather than copying the runtime sample catalog into the host. It verifies a
basic plugin round trip, a live host capability, and TypeScript compiler output
dispatch. Exhaustive runtime/sample conformance remains plugin-owned.

Legacy packaged-compatible metadata remains readable for historical catalogs, but
the current host does not embed Node into exported APKs.
