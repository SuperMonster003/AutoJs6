# long-running-service

Interactive long-running service example with lifecycle checkpoint hooks.

- Capabilities: `interactive_long_running`, `lifecycle_checkpoint`
- Expected provider: explicit interactive long-running launch surface
- Packaged support: not supported; packaged entries remain one-shot
- Security limitations: no automatic restart; checkpoints are JSON-only and project-scoped

Expected output is listed in `expected-output.txt`.
