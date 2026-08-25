# inspector-debug

Debug-only inspector probe. With `inspectorEnabled=true` on a debug host and
debug plugin build, it opens an ephemeral CDP endpoint on IPv4 loopback and
then closes it. The ordinary sample runner does not opt in, so that path prints
the explicit denial and still completes the policy probe.

- Capabilities: localhost Node inspector / Chrome DevTools Protocol
- Expected provider: controlled `inspector` facade in debug builds only
- Packaged support: not part of the packaged smoke set
- Activation: construct the host request with `inspectorEnabled = true`; project
  metadata cannot opt in and release builds ignore a forged request
- Desktop connection: keep the script alive, note the device port from
  `inspector.url()`, run `adb forward tcp:9229 tcp:<device-port>`, then configure
  `localhost:9229` in `chrome://inspect`
- Security limitations: binding is forced to `127.0.0.1`; remote hosts and
  `wait=true` are rejected; CDP exposes source paths, evaluated values and
  potentially secrets, and no path/value redaction is claimed

Expected output is listed in `expected-output.txt`.
