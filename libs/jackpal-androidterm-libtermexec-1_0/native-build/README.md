# jackpal JNI rebuild

The AAR keeps its original Java, Android manifest, AIDL and resource contents. Only its eight
`jni/<abi>/*.so` entries are replaced. Normal AutoJs6 builds consume this AAR and do not need an NDK.

Upstream: https://github.com/jackpal/Android-Terminal-Emulator

`upstream.lock.json` fixes commit `35188f8a8b57989a4a4ec9485e11187b46be26d9`, all JNI source/header
digests and NDK `28.2.13676358`. Source mapping follows upstream `term/build.gradle` and
`libtermexec/build.gradle`:

| Library | Upstream sources | JNI contract |
| --- | --- | --- |
| libjackpal-androidterm5.so | term/src/main/jni/{common,fileCompat,termExec}.cpp | JNI_OnLoad registers Exec.setPtyWindowSizeInternal (IIIII)V, Exec.setPtyUTF8ModeInternal (IZ)V and compat.FileCompat$Api8OrEarlier.testExecute (Ljava/lang/String;)Z |
| libjackpal-termexec2.so | libtermexec/src/main/jni/process.cpp | Java_jackpal_androidterm_TermExec_{createSubprocessInternal,sendSignal,waitFor} |

Run from the repository root (Python 3, Git, CMake 3.22.1 and Ninja required):

```powershell
python libs/jackpal-androidterm-libtermexec-1_0/native-build/build.py `
  --ndk E:/.android/sdk/ndk/28.2.13676358 `
  --cmake E:/.android/sdk/cmake/3.22.1/bin/cmake.exe --replace
```

Omit `--replace` to stage the verified AAR in `build/tmp-16kb/jackpal-native/`. `--source` accepts
an existing checkout only when its commit and source digests match the lock. The script checks all
JNI exports against the input AAR, verifies 16 KB PT_LOAD alignment for all four ABIs, and compares
every non-native entry byte for byte. The receipt includes each ELF SHA-256, alignment, JNI exports,
the classes.jar digest and output AAR digest.

CMake uses Android API 24, C++98 (upstream declares its own char16_t), no STL, and explicit
`-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`. A forced `string.h` include replaces a
transitive include provided by old NDK headers. Upstream source code is otherwise unchanged.
`LICENSE` and `NOTICE` are copied from the locked Apache-2.0 upstream source.

Device testing must exercise terminal PTY creation/window sizing, UTF-8 mode and child-process exit
in both the host and an INRT application on 4 KB and 16 KB devices. ELF checks alone do not prove
these behaviors. Progress and device evidence: [16 KB roadmap](../../../docs/dev/page-size-16kb-roadmap.md).
