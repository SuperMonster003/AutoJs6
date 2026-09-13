// Put the generated sdk37-probe.dex and sdk37-probe.jar beside this script.
// Both passes run in one process to exercise read-only cache reuse.
try {
    for (let pass = 1; pass <= 2; pass++) {
        runtime.loadDex(files.path("./sdk37-probe.dex"));
        if (String(Packages.org.autojs.sdk37.fixture.DexProbe.value()) !== "SDK37_DEX_OK") {
            throw new Error("DEX fixture returned an unexpected value");
        }
        console.log(`[SDK37-DCL] PASS: runtime.loadDex (pass ${pass})`);

        runtime.loadJar(files.path("./sdk37-probe.jar"));
        if (String(Packages.org.autojs.sdk37.fixture.JarProbe.value()) !== "SDK37_JAR_OK") {
            throw new Error("JAR fixture returned an unexpected value");
        }
        console.log(`[SDK37-DCL] PASS: runtime.loadJar (pass ${pass})`);
    }
    console.log("[SDK37-DCL] PASS: all dynamic loading checks");
} catch (error) {
    console.error(`[SDK37-DCL] FAIL: ${error}`);
    throw error;
}
