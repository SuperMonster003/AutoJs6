"""Fixed TLS responder for the SDK 37 emulator tests; binds only to host loopback.

The committed key is a disposable test identity for reserved .test names, never a
production credential. The test CA is trusted only for reserved debug-build domains.
No certificate or settings are installed on a device by this process.
"""
import argparse
import json
from pathlib import Path
import signal
import socket
import socketserver
import ssl
import threading
import time


FIXTURES = Path(__file__).resolve().parent / "fixtures/tls"
BODY = b"sdk37-tls-ok"


def event(kind, **fields):
    print(json.dumps({"event": kind, "time": time.time(), **fields}), flush=True)


def client_hello_extensions(record):
    """Return extension types from one complete TLS ClientHello, or None if incomplete."""
    if len(record) < 9 or record[0] != 22 or record[5] != 1:
        return None
    length = int.from_bytes(record[3:5], "big")
    if len(record) < length + 5:
        return None
    try:
        pos = 9 + 2 + 32
        pos += 1 + record[pos]
        pos += 2 + int.from_bytes(record[pos:pos + 2], "big")
        pos += 1 + record[pos]
        end = pos + 2 + int.from_bytes(record[pos:pos + 2], "big")
        pos += 2
        if end > len(record):
            return None
        extensions = []
        while pos + 4 <= end:
            kind = int.from_bytes(record[pos:pos + 2], "big")
            size = int.from_bytes(record[pos + 2:pos + 4], "big")
            pos += 4 + size
            if pos > end:
                return None
            extensions.append(kind)
        return extensions if pos == end else None
    except IndexError:
        return None


class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(8)
        try:
            # Observe the greeting without consuming bytes needed by the TLS provider.
            deadline = time.monotonic() + 3
            extensions = None
            while time.monotonic() < deadline:
                record = self.request.recv(32768, socket.MSG_PEEK)
                if not record:
                    return
                extensions = client_hello_extensions(record)
                if extensions is not None:
                    break
                time.sleep(0.01)
            event("client-hello", peer=self.client_address[0], extensions=extensions,
                  echExtensionPresent=(0xfe0d in extensions) if extensions is not None else None,
                  echNegotiated=False)
            with self.server.tls.wrap_socket(self.request, server_side=True) as connection:
                request = b""
                while b"\r\n\r\n" not in request and len(request) < 8192:
                    part = connection.recv(2048)
                    if not part:
                        return
                    request += part
                if b"\r\n\r\n" not in request:
                    return
                line = request.split(b"\r\n", 1)[0].decode("ascii", errors="replace")
                if not line.startswith("GET /"):
                    return
                connection.sendall(b"HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: text/plain\r\nContent-Length: "
                                   + str(len(BODY)).encode() + b"\r\n\r\n" + BODY)
                event("response", request=line, tls=connection.version())
        except (OSError, ssl.SSLError) as error:
            event("connection-ended", error=str(error))


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=17647)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error("Use an unprivileged TCP port")
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(FIXTURES / "server-cert.pem", FIXTURES / "server-key.pem")
    tls.set_servername_callback(lambda _socket, name, _context: event("sni", name=name))
    stopped = threading.Event()
    with Server(("127.0.0.1", args.port), Handler) as server:
        server.tls = tls
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        signal.signal(signal.SIGINT, lambda *_: stopped.set())
        signal.signal(signal.SIGTERM, lambda *_: stopped.set())
        event("ready", address=server.server_address, echSupported=False)
        try:
            while not stopped.wait(0.5):
                pass
        finally:
            server.shutdown()
            worker.join(3)


if __name__ == "__main__":
    main()
