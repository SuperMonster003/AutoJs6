# node-test-project

Small `node:test` style project with a manual fallback for runtimes where the
test runner is unavailable.

- Capabilities: `node:test`
- Expected provider: Safe Node Profile `node:test` subset
- Packaged support: source files can be packaged, but automated test execution is a developer workflow
- Security limitations: test code has the same scoped filesystem and disabled builtin policy as app code

Expected output is listed in `expected-output.txt`.
