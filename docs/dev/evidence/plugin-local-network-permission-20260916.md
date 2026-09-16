# Android 17 plugin local network permission entry points

2026-09-16. This records the follow-up to the SDK 37 fleet work for Node.js Runtime, Bun Runtime, Python Runtime and MCP Server.

On API 37 and later, a manual enable action in the plugin center first explains the required plugin permission. The plugin requests Nearby devices under its own UID. Canceling or denying leaves its enable switch off; granting continues that manual enable attempt through the existing ABI and live service checks. A separate Local network permission row is available near the top of the plugin's Settings page. Granting there does not enable the plugin. MCP Server also retains the permission button in its native settings page. Neither settings entry is shown below API 37.

Node.js, Bun and Python no longer declare a MAIN/LAUNCHER permission activity. All four use an explicit, translucent `LocalNetworkPermissionActivity`, protected by the host's signature permission `org.autojs.permission.PLUGIN`. It requests permission and returns without creating a content page or starting the runtime. Permanent denial offers the plugin's own application settings; denial state is read from Android instead of a persistent "already requested" flag. The host checks the actual plugin grant again when an activity returns. Its own local network grant cannot satisfy the plugin requirement.

`PluginEnableStore` rejects enable writes and clears stale enabled flags when a required plugin grant is missing. Automatic mount, runtime selection and MCP inspection use this store without presenting permission UI. The view model reflects the accepted state, and asynchronous enable/probe completions check permission again.

The first interactive grant exposed an existing result-forwarding omission in `MainActivity`. A non-suspending IntelliJ logpoint recorded request code `246003602`, result `-1` at `MainActivity.onActivityResult` at `2026-09-16T04:18:04.910Z`; the corresponding registry callback was not reached, and PackageManager reported the Bun grant as true while its switch stayed off. Calling the superclass restores AndroidX result delivery while retaining the existing mediator. The new device test exercises the actual cross-APK result through both `MainActivity` and `PluginCenterActivity`. All agent logpoints, the temporary remote debug configuration and the owned ADB forward were removed after diagnosis.

Validation used signed debug APKs, with x86_64 artifacts for native packages. Compile/target SDK remain 37/37 in all four plugins.

| Repository | Version / build | JVM tests | Python tests |
| --- | --- | ---: | ---: |
| AutoJs6 | 6.8.0 / 5280 | 85 | n/a |
| NodeJs-Runtime | 1.5.3 / 175 | 79 | 2 |
| Bun-Runtime | 0.2.4 / 112 | 59 | 38 |
| Python-Runtime | 0.5.4 / 110 | 56 | 3 |
| MCP-Server | 1.0.2 / 62 | 235 | n/a |

All 514 selected JVM tests passed. The host selection covers plugin center and MCP packages; each plugin ran its complete JVM suite. Debug and androidTest assemblies passed. All four plugin lint runs reported zero errors. Ten-language Markdown generation/checks passed, including generated plugin instructions; LAN resource XML parsing, ordering, ASCII punctuation and default/explicit English parity were checked. Bun's compatibility matrix check passed. Existing native payloads and their locks were not changed; debug native alignment verification passed where applicable.

The device regression class is `PluginLocalNetworkPermissionDeviceTest`, opted in with `-e pluginLanPermissionProbe true` and the four companion APKs installed. API 37 ran four applicable tests with one older-system assumption skip; API 33 ran three applicable tests with two API 37 assumption skips. Both runs reported `OK (5 tests)`. They cover host/plugin grant isolation, rejected enable writes, stale enablement after revocation, grant-only remaining off, manifest/launcher protection, older-system behavior and activity-result delivery through both plugin-center entry points.

| Environment | Device | Permission UI results |
| --- | --- | --- |
| API 37, 4 KiB | `codex-sdk37-fleet-4k-20260916`, `emulator-5564` | All four: cancel -> off; deny -> off; grant -> enabled after the existing enable checks; Settings grant -> still off |
| API 33, 4 KiB | `codex-plugin-lan-api33-20260916`, `emulator-5566` | All four enable normally, with no LAN permission entry in their Settings pages, including MCP's native settings page |

The API 37 image advertises translated arm64 before x86_64. Its existing ABI confirmation was explicitly acknowledged for the installed x86_64 native plugins before checking successful enablement. This does not establish arm64 hardware compatibility. The pre-existing API 33 AVD encountered a System UI ANR during setup; the UI matrix used the newly created disposable AVD instead. Only owned, headless, read-only emulator instances were used. No physical device or ColorOS first-install activation test was performed.

Raw build logs, debugger events, UI XML/screenshots and result records are under the ignored `build/verification/plugin-lan-center-20260916/` directory. This is a permission UX follow-up; the previous fleet evidence remains bound to its recorded commits.

Additional API 37 checks passed: MCP's native Settings button opens Android's application settings for MCP Server; restarting the host with all four enable flags previously on and all four plugin grants revoked restores four off switches without a permission dialog; permanent denial opens the affected plugin's application settings; clearing Android's denial flags allows a fresh system request, and granting from Settings still leaves the plugin off.

A concurrent MCP CI follow-up supplied three instrumentation fixes and its roadmap evidence. They were reviewed and preserved in a separate commit: request IDs advance for ordinary requests and pairing polls, success responses verify their HTTP status and matching ID, and startup waits for both the socket and cleared persisted user-stop flag. The permission change is `ea1ec26`; the CI test change is `075cbcc`, with build 62. The final combined MCP HEAD passed JVM 235/235, debug/androidTest assembly, lint with zero errors, signed release collection, and the complete API 33 instrumentation suite: `OK (36 tests)`, 35 passed / one optional process-kill assumption skip, 100.618 s. Its four API 37 permission UI cases were repeated with build 62 and passed. The other session's API 24 results remain in its own roadmap record and are not counted as new runs here.

All four signed release/digest tasks passed on the final plugin commits. The 11 current ABI artifacts were matched byte-for-byte to their Gradle release outputs and verified with apksigner and their filename CRC32. Historical MCP APKs with the same version name remain separate. Artifact names, repository-relative locations, commits, sizes and SHA-256 values are recorded in [plugin-local-network-permission-20260916.json](plugin-local-network-permission-20260916.json).
