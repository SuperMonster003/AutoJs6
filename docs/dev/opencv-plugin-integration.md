# OpenCV plugin integration

## Scope

The first OpenCV plugin integration removes the large ABI-specific JNI payloads from the AutoJs6 application while preserving the existing Java and script API. It is an intentionally transitional, in-process design.

The boundary in this phase is:

| Component | Owner |
| --- | --- |
| OpenCV 4.8.0 Java classes and the public `Mat` ABI | AutoJs6 host and APK Builder template |
| `libopencv_java4.so` | Official OpenCV plugin |
| Process-wide `libc++_shared.so` dependency | AutoJs6 host and APK Builder template |
| Plugin discovery, validation, staging, and loading | AutoJs6 host |
| OpenCV algorithms after JNI loading | AutoJs6 process |

The Java ABI remains in the host because existing code and scripts directly use OpenCV classes such as `Mat`, `Imgproc`, `Imgcodecs`, `CvType`, `Point`, `Rect`, and `Size`. Those objects cannot be moved transparently across a Binder boundary.

The matching standalone implementation is maintained as the sibling `AutoJs6-Plugin-OpenCV` project. It publishes four single-ABI APKs plus a universal APK.

## Contract and discovery

The shared contract is in `plugin-api/opencv-api`. Both the host and plugin must compile against the same contract constants. Contract v2 is locked to OpenCV 4.8.0, `libopencv_java4.so`, and NDK 26.1.10909125.

The plugin exposes a service for:

- Action `org.autojs.plugin.OPENCV`.
- Category `opencv-runtime`.
- Engine `opencv`.
- Variant `4.8.0`.
- Default package `io.github.supermonster003.autojs6.plugin.opencv`.

The service or application metadata must provide the contract version, OpenCV version, native library name, native NDK version, Java API SHA-256 fingerprint, and optional minimum and maximum host version. The service also needs to expose the common plugin information Binder used by Plugin Center and APK Builder.

The host accepts an in-process candidate only when all of the following are true:

1. The application, service, and Plugin Center entry are enabled.
2. The package is signed like the host, or its trust state is `OFFICIAL` or `TRUSTED`.
3. The contract version is supported.
4. The OpenCV version exactly matches the Java ABI retained by the host.
5. The native library name exactly matches the contract.
6. The native runtime was built with the NDK version locked by the contract.
7. The Java API fingerprint exactly matches the classes retained by the host.
8. The current host version is within the plugin's declared bounds.

A user grant alone is deliberately insufficient. Loading native code into the AutoJs6 process grants it the same privileges and failure domain as the host.

## Native library staging

The host inspects the plugin base APK and split APK paths. It first selects the ABI list for the current process bitness (`Build.SUPPORTED_64_BIT_ABIS` for a 64-bit process or `Build.SUPPORTED_32_BIT_ABIS` for a 32-bit process), then searches these entries in platform preference order:

```text
lib/<abi>/libopencv_java4.so
libs/<abi>/libopencv_java4.so
```

ABI preference takes precedence over APK path order, so a preferred ABI in a split is selected before a lower-priority ABI in the base APK.

The selected payload is staged below the host code cache using the plugin package version and update time as part of its identity. Extraction validates the declared size, extraction limit, ZIP CRC, and ELF magic. An existing staged file is reused only when its size, ELF header, and CRC still match the current APK entry.

The host then loads the staged file by absolute path. `libc++_shared.so` is loaded first on a best-effort basis because it may already be provided by another host dependency. OpenCV is rebuilt with the host's NDK 26.1.10909125 toolchain so its C++ exception unwinder is compatible with that process-wide runtime; older official OpenCV Android payloads built with NDK r18b are rejected by contract v2. The native runtime version reported through JNI must still equal the version declared in plugin metadata.

Only an INRT packaged application may load a bundled OpenCV runtime. The ordinary AutoJs6 host never probes a bundled copy, even if a future transitive dependency accidentally packages one; it must always pass fixed-package discovery, enablement, signature/trust, metadata, and compatibility checks. The `verifyOpenCvPluginSeparation` Gradle task additionally rejects host APKs that contain `libopencv_java4.so`.

## Behavior without the plugin

Creating, retaining, displaying, or otherwise using a bitmap-backed `ImageWrapper` must not initialize OpenCV. Bitmap-only operations therefore remain available when the plugin is missing, disabled, incompatible, or unavailable for the current ABI.

Accessing a `Mat`, creating a BGR matrix, converting between `Mat` and `Bitmap`, or invoking an OpenCV-backed image algorithm initializes the runtime on demand. Such operations fail with an actionable plugin load error when no eligible native runtime is available. An algorithm must not silently substitute a different implementation with different image-processing semantics.

Low-level consumers that directly construct `org.opencv.core.Mat` must first call the AutoJs6 initializer (`images.initOpenCvIfNeeded()` or `OpenCVHelper.initIfNeeded`). The retained upstream `OpenCVLoader.initDebug()` only searches the application's own native library path and cannot discover the external runtime plugin.

Background scripts must not force an Activity or dialog to open. A foreground UI entry point may offer to open Plugin Center filtered to the official OpenCV package.

## Plugin lifecycle limitation

This phase loads JNI into the AutoJs6 process. Android cannot safely unload that native library while Java objects, native allocations, or registered JNI methods may still reference it. Consequently:

- Disabling or uninstalling the plugin prevents selection on the next cold process start, but it cannot unload an already loaded runtime.
- Installing an updated native runtime does not replace the active runtime in the current process.
- Switching OpenCV contract or native versions requires a full AutoJs6 process restart.
- A native crash still terminates the AutoJs6 process.

Plugin Center and user-facing messages must describe the restart requirement rather than implying complete runtime hot replacement. Package identity and CRC-based staging protect the next process start from reusing stale bytes; they do not turn JNI into an unloadable component.

## APK Builder behavior

`ApkBuilder.Lib.OPENCV` is a plugin-supplied build dependency. It selects the OpenCV service by action, engine, and `4.8.0` variant, then copies `libc++_shared.so` and `libopencv_java4.so` for the selected target ABI into the generated APK.

Generated applications must be self-contained. They retain the compatible OpenCV Java classes in the template and receive the selected native libraries while being built. A generated APK must not contact Plugin Center and must not require the OpenCV plugin to be installed on its target device.

The OpenCV plugin owns only the OpenCV runtime of the host and of generated APKs: the `org.opencv` Java API and the `libopencv_java4.so` that images and other modules load. OCR plugins link their own private OpenCV into their engine libraries and never supply this runtime ([OpenCV / OCR runtime decoupling roadmap](opencv-ocr-runtime-decoupling-roadmap.md), section 2).

The builder records the SHA-256 of every packaged `libopencv_java4.so` in `assets/opencv-runtime.properties` (`BundledOpenCvRuntime`) when OpenCV is selected. At runtime, an INRT application loads its bundled JNI library only if the library matches that record. Without the record, or with a same-named library from another plugin, initialization fails with a packaging error; INRT never discovers or loads an OpenCV plugin installed on the device.

Native libraries from the host and from every selected plugin are staged and resolved by content (`PackagedNativeLibraryPolicy`):

- Identical bytes under one `lib/<abi>/<name>` are packaged once. Different bytes stop the build with both sources named, so an older OCR plugin that still ships a different `libopencv_java4.so` must be updated.
- `libc++_shared.so` is the newest copy among the host and the plugins, ordered by its Android ident build number. Every packaged library that needs it must find its `std::__ndk1` symbols there, otherwise the build stops.
- An OCR plugin's `libopencv_java4.so` is copied only when one of its packaged libraries lists it in `DT_NEEDED`, which applies to OCR plugins released before the decoupling.

Release verification must cover each supported ABI and confirm these conditions:

- The AutoJs6 host APK does not bundle the OpenCV JNI payload.
- A generated APK that includes OpenCV runs on a device without the plugin installed.
- A generated APK that includes OpenCV and OCR keeps working when the private OpenCV of an OCR engine has another version (`tools/opencv-ocr/verify_packaged_ocr.py`).

## Future isolated runtime

The target architecture is a dedicated OpenCV plugin process reached through a versioned AIDL contract. This will isolate native crashes and permit service rebinding after plugin disable, uninstall, update, or process death without loading plugin native code into AutoJs6.

That migration requires replacing public native objects with process-neutral data. The expected design is:

1. Transfer encoded images through `ParcelFileDescriptor`, with raw pixel transport through `SharedMemory` where supported.
2. Create a remote session and return opaque image or matrix handles owned by that session.
3. Chain transforms and queries against handles to avoid encoding an image for every operation.
4. Apply quotas, cancellation, idle expiry, and deterministic handle release.
5. Invalidate every handle on Binder death and never expose native pointers to the host.
6. Export final pixels or structured results back through file descriptors or Parcelable data.

Raw `Mat` and static `Imgproc` exposure must be deprecated, replaced, or confined to an explicitly documented legacy mode before full AIDL isolation can be achieved. Until then, the current split should be described as JNI payload modularization, not complete OpenCV runtime isolation.

## Test and release gates

At minimum, changes to this integration should run:

- `OpenCvPluginPolicyTest` for enable, trust, contract, OpenCV version, native name, native NDK version, and host version policy.
- `OpenCvNativeLibraryStagerTest` for ABI ordering, CRC cache reuse and invalidation, missing ABI payloads, and invalid ELF payloads.
- `ApkBuilderLibPolicyTest` for the OpenCV plugin action, engine, variant, and packaged libraries.
- `OpenCvPluginHostDeviceTest` for cross-APK discovery, staging, JNI loading, version verification, a real `Mat` operation, and intentional native OpenCV errors reaching Java as `CvException` without terminating the process (skipped when the plugin is not installed).
- `ImagesResizeRhinoInstrumentationTest` for the documented `images.resize(image, [width, height])` contract and catchable rejection of the common incorrect scalar width/height call.
- The plugin project's `verifyOpenCvApks` task for split/universal ABI inventory, ELF architecture and NDK identity, byte-identical native payloads, absence of duplicate OpenCV Java classes, and complete packaged license assets.
- `verifyOpenCvPluginSeparation` for the host Java-only AAR and debug/release APK native inventory.

Device release testing should additionally cover plugin disable and uninstall before initialization, restart after plugin upgrade, all published ABI splits, corrupted APK payload rejection, repeated image allocation and recycling, and APK Builder output on a device that does not have the plugin.
