# TLS test identity

`server-key.pem` is a deliberately committed, disposable test key. It is valid
only with the test certificate for `trusted.sdk37.test`, `ct.sdk37.test` and
`untrusted.sdk37.test`. Never use it for a deployed server or import its CA into
a device's system / user certificate store.

The public CA lives in `app/src/debug/res/raw/sdk37_test_ca.pem`. Only the debug
network configuration for `trusted.sdk37.test`, `mismatch.sdk37.test` and
`ct.sdk37.test` trusts it. The mismatch name is deliberately absent from the
certificate SANs, isolating hostname rejection from CA trust. The CT domain
also explicitly requires Certificate Transparency. The server has no
SCTs, allowing a certificate-chain success and a separate CT rejection to be
observed. Normal application domains retain their existing configuration.

`server.cnf` records the certificate extensions. The root signing key is not
committed; to regenerate the identity, create a disposable root, sign a new
server certificate with these extensions and replace the debug CA, server
certificate and server key together. Rebuild the debug APK afterwards.

`../../tls_fixture.py` responds with a fixed body and records TLS ClientHello
extension types. It does not support ECH negotiation: the presence of an ECH
extension alone would show a greeting / GREASE, not successful encrypted SNI.
