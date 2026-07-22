# Image Quantization plugin integration

## Scope

The Image Quantization integration removes its ABI-specific JNI payload and private native dependencies from the ordinary AutoJs6 application while preserving the existing Java and script API. Like the current OpenCV integration, it is an in-process native plugin design rather than an isolated service runtime.

The boundary is:

| Component | Owner |
| --- | --- |
| `Images` PNG save/compress behavior and `PngQuantBridge` Java ABI | AutoJs6 host and APK Builder template |
| `libpngquant_bridge.so` | Official Image Quantization plugin, or an INRT APK produced by APK Builder |
| libimagequant 2.17.0 and libpng 1.6.49 | Statically linked into `libpngquant_bridge.so` |
| C++ runtime used by Image Quantization | Statically linked `c++_static` inside `libpngquant_bridge.so` |
| Plugin discovery, policy validation, staging, lifecycle checks, and loading | AutoJs6 host |
| Android system libraries such as `libz`, `liblog`, and `libjnigraphics` | Android platform |

The standalone implementation is maintained as the sibling `AutoJs6-Plugin-Image-Quantization` project. It publishes four single-ABI APKs plus a universal APK. Each ABI contains one private native payload, `libpngquant_bridge.so`; it must not package a separate `libimagequant.so`, `libpng.so`, `libpng16.so`, or `libc++_shared.so`. Every plugin artifact also carries the required GPLv3, libimagequant, libpng, and Android NDK r26b LLVM license material below `assets/licenses/imagequant/`.

## Contract and discovery

The shared contract is in `plugin-api/imagequant-api`. Contract v1 is locked to:

- libimagequant 2.17.0.
- libpng 1.6.49.
- `libpngquant_bridge.so` and native bridge ABI version 1.
- NDK 26.1.10909125.
- `c++_static`.
- `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

The plugin exposes a service for:

- Action `org.autojs.plugin.IMAGE_QUANT`.
- Category `imagequant-runtime`.
- Engine `imagequant`.
- Variant `2.17.0`.
- Default package `io.github.supermonster003.autojs6.plugin.imagequant`.

The application or service metadata must declare the contract version, libimagequant version, libpng version, native library name, native NDK version, STL mode, native bridge ABI version, supported ABI inventory, and optional minimum and maximum host versions. The service also exposes the common plugin information Binder used by Plugin Center and APK Builder. The ordinary host resolves the official package on demand; APK Builder may query compatible services by action and variant for packaging.

## Trust and compatibility policy

The host may load an in-process candidate only when all of the following are true:

1. The application, runtime service, and Plugin Center entry are enabled.
2. The package is signed like the host, or its trust state is `OFFICIAL` or `TRUSTED`.
3. The contract version is supported.
4. The libimagequant and libpng versions exactly match the contract.
5. The native library name, NDK version, static STL mode, native bridge ABI version, and complete ABI inventory exactly match the contract.
6. The current host version is within the plugin's declared bounds.
7. The plugin contains the bridge library for an ABI supported by the current process bitness.
8. The value reported by the loaded JNI runtime probe exactly matches the contract.

A user grant alone is deliberately insufficient. The plugin executes native code inside the AutoJs6 process and therefore receives the host's privileges and failure domain. Missing or malformed metadata must fail closed rather than falling back to an unverified payload.

## Native library staging and loading

The host inspects the plugin base APK and split APK paths. It selects ABI candidates from the current process bitness and searches these entries in platform preference order:

```text
lib/<abi>/libpngquant_bridge.so
libs/<abi>/libpngquant_bridge.so
```

ABI preference takes precedence over APK path order. The selected payload is staged below the host code cache with package version and update identity in its path. Extraction validates the declared size, extraction limit, ZIP CRC, ELF magic, ELF class, and machine type. A staged file is reused only while its size, CRC, and ABI identity still match the installed APK entry.

The bridge is then loaded by absolute path and its runtime probe is checked before the first quantization call. The bridge statically contains libimagequant, libpng, and its C++ runtime, so Image Quantization loading must not depend on the host's `libc++_shared.so` or on an accidentally packaged private libpng shared object.

Only an INRT packaged application may load a bundled bridge from its own native library path. The ordinary AutoJs6 host must always pass plugin discovery, enablement, trust, metadata, host-version, ABI, staging, and probe checks. The `verifyImageQuantPluginSeparation` Gradle task rejects host APKs that contain `libpngquant_bridge.so` or a private libpng shared payload.

## Behavior without the plugin

Image Quantization is initialized only for PNG save or compression operations whose quality is not 100. Bitmap creation and access, PNG quality 100, JPEG, WebP, and unrelated image algorithms must remain available without this plugin.

If the plugin is missing, disabled, incompatible, unavailable for the current ABI, or fails native validation, a quantization request fails with an actionable plugin load exception. It must remain visible to and catchable by the calling script. The host must not silently replace palette quantization with `Bitmap.compress`, because Android's ordinary PNG encoder does not provide the same quality-controlled color quantization semantics.

A background script must not force an Activity or dialog to open. A foreground UI entry point may offer to open Plugin Center filtered to the official Image Quantization package.

## Plugin lifecycle limitation

`System.load` has no reliable Android application mechanism for unloading a JNI library from the live app class loader. Once `libpngquant_bridge.so` has been loaded, its mapping and registered native methods must be treated as process-lifetime state.

Consequently:

- Disabling or uninstalling the plugin causes subsequent quantization requests to fail policy checks, but it does not remove the already mapped library from the current process.
- Replacing or updating the plugin must not load a second bridge identity into the same process. The new payload becomes active only after a full AutoJs6 process restart.
- A request already executing native code cannot be revoked by uninstalling the package.
- A native crash still terminates the AutoJs6 process.

Package identity and CRC-based staging prevent stale bytes from being selected on the next process start. They do not make JNI fully hot-unloadable. User-facing lifecycle messages must state the restart requirement.

## `libc++_shared.so` ownership

Moving Image Quantization out of the host does not by itself allow AutoJs6 to remove `libc++_shared.so`.

Image Quantization has no shared-libc++ requirement: its bridge is built with `c++_static` and is self-contained with respect to its private C/C++ dependencies. The ordinary host must nevertheless remain the single owner of one process-wide, NDK-aligned `libc++_shared.so` for OpenCV, whose plugin JNI payload is loaded into the AutoJs6 process and deliberately uses `c++_shared`. Host variants that package the embedded Node.js runtime also need that copy for `libnode.so` and the native bridge. The default external Binder Node.js plugin runs in its own plugin process and must own the compatible runtime for that process; it is not a reason by itself to retain a copy in an otherwise native-free host.

Keeping one host-owned copy is necessary because:

1. `libopencv_java4.so`, and `libnode.so` in embedded Node host variants, have a dynamic dependency on `libc++_shared.so`; without a resolvable copy, loading can fail with `UnsatisfiedLinkError` or missing C++ symbols.
2. C++ exceptions, RTTI, allocators, and standard-library objects that cross library boundaries must use a compatible process-wide runtime.
3. Allowing several plugins to ship competing copies makes the selected runtime depend on load order and Android linker namespace behavior. A version or ABI mismatch can corrupt state or crash during exception unwinding.
4. The host can lock and verify one NDK/runtime baseline, while plugins omit duplicate copies and declare that baseline in their contracts.

APK Builder also uses the host-owned copy when producing applications that select OpenCV, Embedded Node.js, Paddle OCR, or MediaInfo variants whose native payloads require `libc++_shared.so`. The OpenCV plugin intentionally omits that library and relies on this single-owner rule. An INRT APK must similarly package one compatible shared C++ runtime for the selected payloads. Selecting Image Quantization alone does not add such a requirement.

## APK Builder and INRT behavior

`ApkBuilder.Lib.IMAGE_QUANT` is a plugin-supplied build dependency. APK Builder selects an eligible service by action, engine, and the `2.17.0` variant, then copies `libpngquant_bridge.so` for the target ABI and the complete `assets/licenses/imagequant/` directory into the generated application. The license directory includes GPLv3, the libimagequant copyright file, the libpng license, the Android NDK r26b LLVM notice for the statically linked C++ runtime, a source offer, and native build documentation.

Generated applications must be self-contained. They retain the `PngQuantBridge` Java ABI in the template and receive the selected native bridge at build time. At runtime an INRT application loads that bundled library and never discovers an Image Quantization plugin installed on the target device. If the requested library is absent, the build must fail; if an invalid package somehow reaches runtime, initialization fails as a packaging error.

The generated APK must not contain extra private libpng, libimagequant, or shared-libc++ payloads on behalf of Image Quantization. Release verification must cover each supported ABI and confirm both conditions:

- Ordinary AutoJs6 host APKs contain no Image Quantization native payload.
- Generated APKs that select Image Quantization work on a device without the plugin installed.

## License and source obligations

Plugin separation changes artifact ownership but does not remove redistribution obligations.

The vendored libimagequant 2.17.0 sources state GPL version 3 or later and also retain portions under the original pngquant license. Because libimagequant is statically linked into `libpngquant_bridge.so`, every distribution that conveys that bridge, including the official plugin APK and an APK Builder-generated application, must pass the project's GPL release review. At minimum, the release process must:

1. Preserve the complete libimagequant `COPYRIGHT` and GPL text, including the original pngquant notices.
2. Provide the complete corresponding source for the exact distributed binary, including the JNI bridge, vendored native sources, local modifications, build scripts, and configuration needed to reproduce it, or use another GPL-compliant source-delivery mechanism.
3. Mark modified source versions and keep an auditable mapping from each released binary to its source revision and toolchain.
4. Ensure the terms applied to the conveyed combined artifact are GPL-compatible. Moving the native binary to another APK is not, by itself, a licensing exception.

libpng 1.6.49 is distributed under the PNG Reference Library License. Its `LICENSE`, copyright, disclaimer, origin, and modification notices must remain intact. The LLVM C++ runtime components statically linked by `c++_static` use Apache License 2.0 with LLVM Exceptions, so the authoritative Android NDK r26b LLVM notice is also mandatory. Plugin release gates verify immutable license fingerprints and require every plugin APK to contain GPLv3, the libimagequant copyright file, the libpng license, the NDK LLVM notice, a source offer, and native build documentation under `assets/licenses/imagequant/`. APK Builder treats that complete directory as a mandatory part of the `2.17.0` plugin variant and copies it into every generated APK containing the bridge. Packaging fails rather than producing an Image Quantization artifact without those assets. The source offer must identify a durable corresponding-source location for the exact binary revision.

This section defines project release requirements and is not a substitute for legal review.

## Test and release gates

At minimum, changes to this integration should cover:

- Contract and policy tests for enablement, trust, exact versions, NDK, STL mode, native ABI version, ABI inventory, and host-version bounds.
- Native stager tests for process-bitness ABI ordering, base/split APK discovery, CRC cache reuse and invalidation, extraction limits, and invalid ELF rejection.
- Runtime lifecycle tests for missing, disabled, uninstalled, updated, and restart-required states.
- APK Builder policy tests for the Image Quantization action, engine, `2.17.0` variant, bridge-only native inventory, and mandatory `assets/licenses/imagequant/` copy rule.
- Device tests that load the external plugin, run a real PNG quantization, verify the runtime probe, catch an unavailable-plugin error, and confirm ordinary bitmap/PNG quality 100 operations do not initialize the plugin.
- Plugin release verification for four single-ABI APKs and a universal APK, exact ELF architecture and NDK identity, byte-identical expected payloads, absence of forbidden shared libraries, and complete GPLv3/libimagequant/libpng/source-offer/build-documentation assets.
- `verifyImageQuantPluginSeparation` for debug and release host APK inventories.

Device release testing should additionally cover plugin disable and uninstall before and after initialization, restart after plugin update, all published ABI splits, corrupted payload rejection, repeated quantization under memory pressure, and an APK Builder output installed on a device without the plugin.
