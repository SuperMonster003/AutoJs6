# AutoJs6 Node.js Example Projects

Last reviewed: 2026-08-21.

Examples live under **sample/nodejs** in both the host and runtime-plugin
repositories. Both trees currently contain 358 files and are intentionally retained:
the host exposes user examples while the plugin owns runtime validation.

Each example has project metadata, package metadata, a documented entry, expected
output, and a row in **examples.json**. Node project entries run through the external
runtime plugin and may call host APIs only through declared bridge capabilities.

## Verification

~~~powershell
.\gradlew.bat :app:verifyNodeExamples
.\gradlew.bat :app:verifyNodePluginExampleMirror "-Pautojs.nodejs.plugin.root=D:\idea-projects\AutoJs6-Plugin-NodeJs-Runtime"
~~~

The mirror task compares relative paths and bytes across all files. Add or update an
example in both repositories in the same change set.

## Device Smoke

**NodeExampleProjectInstrumentationTest** runs seven representative groups through
the real plugin, including hello-world, bridge permissions, CommonJS/npm, TypeScript,
and stable profile subsets. A successful compile is not a substitute for this run.

Legacy packaged-compatible metadata remains readable for historical catalogs, but
the current host does not embed Node into exported APKs.
