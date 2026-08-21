# Migrating Rhino Scripts to the Node.js Runtime Plugin

Last reviewed: 2026-08-21.

Node.js is a separate engine, not a replacement parser for Rhino source. Migration
should be explicit and reversible.

## Select Node.js

Use the "nodejs" directive, a Node-specific extension, or project metadata with
**type: node**. Keep ordinary Rhino scripts unchanged. If the runtime plugin is not
available, a Node-marked script fails; it is not executed as Rhino.

## Replace Globals Deliberately

Prefer Node modules and the documented AutoJs6 bridge modules. Do not assume every
Rhino global, Java package object, Activity, Context, or raw AutoJs6 runtime object is
available. Consult the plugin's **docs/HOST-API.md** and the declarations under
[types/autojs6-node](types/autojs6-node/README.md).

## Declare Authority

When project capability metadata is present, declare every required host capability.
Sensitive child capabilities such as accessibility gestures, root shell, package
mutation, recording, or overlay authority are not implied by their parent module.
Android permissions and provider readiness are still checked at call time.

## Files and Modules

Treat the project workspace as the portable filesystem boundary. Avoid Android-private
absolute paths and symlink escapes. Use supported CommonJS/ESM and light TypeScript
syntax; do not depend on native addons, arbitrary shared libraries, unrestricted
reflection, or desktop-only process behavior.

## Long-Running and Scheduled Work

Use bounded plugin execution and the documented work_manager provider. The legacy
work_manager descriptor token **backend="embedded"** is retained for wire compatibility
only. It does not select a host runtime.

## Exported Applications

The host no longer embeds Node into exported APKs. A project that must run independently
of AutoJs6 cannot currently depend on this Node runtime path. Keep it as an AutoJs6
project with the runtime plugin installed, or retain a Rhino entry where standalone
packaging is mandatory.
