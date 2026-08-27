# Python Runtime API distribution consumer

This debug-only, test-only application is a build consumer for the R1 Python
Runtime API distribution. It compiles against exactly these staged artifacts:

- `build/distributions/python-runtime-r1/protocol-wire-api-debug.aar`
- `build/distributions/python-runtime-r1/python-runtime-api-debug.aar`

Command-line and CI builds do not depend on either source project and contain
no Python runtime, native library, network permission, service discovery, or
script route. The root `preparePythonR1ApiDistribution` task stages both AARs
before command-line consumer compilation, unit testing, lint, or assembly.

IntelliJ IDEA is the only exception: its project model resolves every imported
Android dependency before Gradle tasks can stage the AARs. When `idea.active`
is true, this fixture uses the two source projects for IDE modeling and regular
IDE builds. The command-line verification gate still resolves the consumer
classpath and rejects any source-project dependency there, preserving the
packaged-AAR distribution boundary.
