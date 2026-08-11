# Python Runtime API distribution consumer

This debug-only, test-only application is a build consumer for the R1 Python
Runtime API distribution. It compiles against exactly these staged artifacts:

- `build/distributions/python-runtime-r1/protocol-wire-api-debug.aar`
- `build/distributions/python-runtime-r1/python-runtime-api-debug.aar`

It does not depend on either source project and contains no Python runtime,
native library, network permission, service discovery, or script route. The
root `preparePythonR1ApiDistribution` task must stage both AARs before consumer
compilation, unit testing, lint, or assembly.
