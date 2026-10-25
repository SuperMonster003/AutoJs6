"""Runs device cases that need a network peer against a local server on the PC (roadmap P3.5: http, web, WebSocket).

Usage: py -3.12 -I tools/augment/device_net.py -s SERIAL [--port 18735] [--cases DIR_OR_FILE ...] [device_eval.py options]

Starts an HTTP and WebSocket server on 127.0.0.1:PORT, forwards the device's 127.0.0.1:PORT to it with
`adb reverse`, runs tools/augment/device_eval.py with the given cases (default app/build/script-modules-device-net,
written by HttpModuleTest and WebModulesTest) and the remaining options (for example --strict-errors),
then removes the forwarding and stops the server. Endpoints:

  /echo        any method: JSON {method, path, query, contentType, body, header} (header is the X-A request header),
               response header X-Echo: head (HEAD gets the headers only)
  /status/404  404 with the body "nope"
  /slow        answers after 3 seconds (for timeouts)
  /ws          WebSocket: every text message comes back as "echo:<text>", pings are answered, close is echoed
"""
import argparse
import base64
import hashlib
import json
import pathlib
import struct
import subprocess
import sys
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = pathlib.Path(__file__).resolve().parent
HOST = HERE.parents[1]
DEFAULT_CASES = HOST / "app" / "build" / "script-modules-device-net"
WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format, *args):
        pass

    def _body(self):
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length).decode("utf-8", errors="replace") if length else ""

    def _send(self, status, body, content_type="application/json", head=False):
        data = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("X-Echo", "head")
        self.end_headers()
        if not head:
            try:
                self.wfile.write(data)
            except (ConnectionAbortedError, ConnectionResetError, BrokenPipeError):
                pass  # The client gave up (a timeout case on /slow).

    def _route(self, head=False):
        url = urllib.parse.urlsplit(self.path)
        if url.path == "/ws" and self.headers.get("Upgrade", "").lower() == "websocket":
            return self._websocket()
        if url.path == "/status/404":
            self._body()
            return self._send(404, "nope", "text/plain", head)
        if url.path == "/slow":
            self._body()
            time.sleep(3)
            return self._send(200, "slow", "text/plain", head)
        echo = {
            "method": self.command,
            "path": url.path,
            "query": url.query,
            "contentType": self.headers.get("Content-Type"),
            "body": self._body(),
            "header": self.headers.get("X-A"),
        }
        self._send(200, json.dumps(echo), head=head)

    def do_GET(self):
        self._route()

    def do_HEAD(self):
        self._route(head=True)

    def do_POST(self):
        self._route()

    def do_PUT(self):
        self._route()

    def do_DELETE(self):
        self._route()

    def do_PATCH(self):
        self._route()

    def _websocket(self):
        key = self.headers.get("Sec-WebSocket-Key", "")
        accept = base64.b64encode(hashlib.sha1((key + WS_GUID).encode("ascii")).digest()).decode("ascii")
        self.send_response(101, "Switching Protocols")
        self.send_header("Upgrade", "websocket")
        self.send_header("Connection", "Upgrade")
        self.send_header("Sec-WebSocket-Accept", accept)
        self.end_headers()
        self.wfile.flush()
        self.close_connection = True
        while True:
            frame = self._read_frame()
            if frame is None:
                return
            opcode, payload = frame
            if opcode == 0x1:
                self._write_frame(0x1, b"echo:" + payload)
            elif opcode == 0x9:
                self._write_frame(0xA, payload)
            elif opcode == 0x8:
                self._write_frame(0x8, payload[:2])
                return

    def _read_frame(self):
        header = self.rfile.read(2)
        if len(header) < 2:
            return None
        opcode = header[0] & 0x0F
        masked = header[1] & 0x80
        length = header[1] & 0x7F
        if length == 126:
            length = struct.unpack(">H", self.rfile.read(2))[0]
        elif length == 127:
            length = struct.unpack(">Q", self.rfile.read(8))[0]
        mask = self.rfile.read(4) if masked else b"\0\0\0\0"
        payload = bytes(b ^ mask[i % 4] for i, b in enumerate(self.rfile.read(length)))
        return opcode, payload

    def _write_frame(self, opcode, payload):
        header = bytes([0x80 | opcode])
        if len(payload) < 126:
            header += bytes([len(payload)])
        elif len(payload) < 65536:
            header += bytes([126]) + struct.pack(">H", len(payload))
        else:
            header += bytes([127]) + struct.pack(">Q", len(payload))
        self.wfile.write(header + payload)
        self.wfile.flush()


class Server(ThreadingHTTPServer):
    daemon_threads = True

    def handle_error(self, request, client_address):
        if not isinstance(sys.exc_info()[1], (ConnectionAbortedError, ConnectionResetError, BrokenPipeError)):
            super().handle_error(request, client_address)


def adb(serial, *args):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--port", type=int, default=18735)
    parser.add_argument("--cases", nargs="*", type=pathlib.Path, default=[DEFAULT_CASES])
    args, rest = parser.parse_known_args()

    server = Server(("127.0.0.1", args.port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    reverse = adb(args.serial, "reverse", f"tcp:{args.port}", f"tcp:{args.port}")
    if reverse.returncode != 0:
        server.shutdown()
        sys.exit(f"adb reverse failed: {reverse.stderr.strip()}")
    print(f"server on 127.0.0.1:{args.port}, device port forwarded")
    try:
        command = [sys.executable, "-I", str(HERE / "device_eval.py"), "-s", args.serial, "--cases", *map(str, args.cases), *rest]
        return subprocess.run(command).returncode
    finally:
        adb(args.serial, "reverse", "--remove", f"tcp:{args.port}")
        server.shutdown()


if __name__ == "__main__":
    sys.exit(main())
