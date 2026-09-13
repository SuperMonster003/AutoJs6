"""Local tests of the TLS fixture; no Android device or certificate-store changes."""
import socket
import ssl
import threading
import unittest

import tls_fixture as fixture


class TlsFixtureTest(unittest.TestCase):
    def test_client_hello_parser_requires_a_complete_record(self):
        incoming, outgoing = ssl.MemoryBIO(), ssl.MemoryBIO()
        context = ssl.create_default_context()
        client = context.wrap_bio(incoming, outgoing, server_hostname="trusted.sdk37.test")
        with self.assertRaises(ssl.SSLWantReadError):
            client.do_handshake()
        record = outgoing.read()
        self.assertIn(0, fixture.client_hello_extensions(record))
        self.assertIsNone(fixture.client_hello_extensions(record[:-1]))
        self.assertIsNone(fixture.client_hello_extensions(b"not a TLS ClientHello"))

    def test_fixed_response_requires_trust_and_matching_hostname(self):
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(fixture.FIXTURES / "server-cert.pem", fixture.FIXTURES / "server-key.pem")
        server = fixture.Server(("127.0.0.1", 0), fixture.Handler)
        server.tls = context
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        trusted = ssl.create_default_context(cafile=str(fixture.FIXTURES.parents[3] / "app/src/debug/res/raw/sdk37_test_ca.pem"))
        try:
            for client, hostname, accepted in ((trusted, "trusted.sdk37.test", True),
                                               (trusted, "wrong.sdk37.test", False),
                                               (ssl.create_default_context(), "trusted.sdk37.test", False)):
                with self.subTest(hostname=hostname, trusted=accepted):
                    with socket.create_connection(server.server_address, timeout=3) as raw:
                        if not accepted:
                            with self.assertRaises(ssl.SSLCertVerificationError):
                                client.wrap_socket(raw, server_hostname=hostname)
                        else:
                            with client.wrap_socket(raw, server_hostname=hostname) as connection:
                                connection.sendall(b"GET /host-test HTTP/1.1\r\nHost: trusted.sdk37.test\r\n\r\n")
                                response = b""
                                while part := connection.recv(4096):
                                    response += part
                                self.assertTrue(response.endswith(fixture.BODY))
                                self.assertIn(b"HTTP/1.1 200 OK", response)
        finally:
            server.shutdown()
            server.server_close()
            worker.join(3)


if __name__ == "__main__":
    unittest.main()
