"""Host-side checks for evidence parsing and the fixed-response LAN fixture; no adb calls."""
import http.client
import socket
import threading
import unittest

import local_network_fixture as fixture
import physical_device as device


def report(methods, codes=None, terminal=True):
    output = []
    for method, code in zip(methods, codes or [0] * len(methods)):
        class_name, test = method.split("#")
        output += [f"INSTRUMENTATION_STATUS: class={class_name}", f"INSTRUMENTATION_STATUS: test={test}",
                   f"INSTRUMENTATION_STATUS_CODE: {code}"]
    if terminal:
        output += [f"OK ({len(methods)} tests)", "INSTRUMENTATION_CODE: -1"]
    return "\n".join(output)


class InstrumentationEvidenceTest(unittest.TestCase):
    def test_missing_skipped_duplicate_or_crashed_tests_cannot_pass(self):
        expected = ["Example#one", "Example#two"]
        bad = ["OK (2 tests)", report(expected[:1]), report([expected[0], expected[0]]),
               report(expected, [0, -3]), report(expected, [0, -4]), report(expected, [0, -2]),
               report(expected, terminal=False), report(expected) + "\nINSTRUMENTATION_FAILED: Process crashed.",
               report(expected) + "\nINSTRUMENTATION_CODE: 0"]
        for text in bad:
            with self.subTest(text=text):
                self.assertFalse(device.parse_instrumentation(text, expected, 0)["success"])

    def test_exact_completions_allow_different_execution_order(self):
        expected = ["Example#one", "Example#two"]
        self.assertTrue(device.parse_instrumentation(report(list(reversed(expected))), expected, 0)["success"])
        self.assertFalse(device.parse_instrumentation(report(expected), expected, 1)["success"])
        self.assertFalse(device.parse_instrumentation(report(expected), expected, None)["success"])


class LocalFixturePeerTest(unittest.TestCase):
    def test_http_fixed_response_and_peer_rejection(self):
        server = fixture.TcpServer(("127.0.0.1", 0), fixture.HttpHandler)
        server.allowed_peer = "127.0.0.1"
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = http.client.HTTPConnection(*server.server_address, timeout=2)
            connection.request("GET", "/test-only")
            response = connection.getresponse()
            self.assertEqual(200, response.status)
            self.assertEqual(b"sdk37-local-network-ok", response.read())
            connection.close()
            server.allowed_peer = "192.168.253.254"
            connection = http.client.HTTPConnection(*server.server_address, timeout=2)
            try:
                connection.request("GET", "/not-the-selected-peer")
                with self.assertRaises((http.client.RemoteDisconnected, ConnectionResetError)):
                    connection.getresponse()
            finally:
                connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join(2)

    def test_udp_echo_and_peer_rejection(self):
        server = fixture.UdpServer(("127.0.0.1", 0), fixture.UdpHandler)
        server.allowed_peer = "127.0.0.1"
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client:
                client.settimeout(0.3)
                client.sendto(b"sdk37", server.server_address)
                self.assertEqual(b"sdk37", client.recv(32))
                server.allowed_peer = "192.168.253.254"
                client.sendto(b"sdk37", server.server_address)
                with self.assertRaises(socket.timeout):
                    client.recv(32)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(2)


if __name__ == "__main__":
    unittest.main()
