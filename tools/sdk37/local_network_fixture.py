"""Local-only HTTP/WebSocket, UDP and MQTT fixtures for LocalNetworkPermissionDeviceTest.

Use a disposable emulator with host alias 10.0.2.2. For inbound tests, configure:
  adb -s SERIAL emu redir add tcp:17645:17643
  adb -s SERIAL emu redir add tcp:17646:7347
By default the fixture binds only 127.0.0.1 and probes this redirection.
For a physical IPv4 LAN, pass --bind PC_LAN_IP --device PHONE_LAN_IP. The servers
then accept only that peer and probe its ports 17643 / 7347 directly, without adb tunnels.
Stop with Ctrl+C. It does not change permissions, firewall rules or adb state.
"""
import argparse
import base64
import hashlib
import http.server
import ipaddress
import json
import os
import signal
import socket
import socketserver
import struct
import threading
import time


def event(kind, **fields):
    print(json.dumps(dict(event=kind, time=time.time(), **fields)), flush=True)


class PeerFilter:
    allowed_peer = None

    def verify_request(self, request, client_address):
        return self.allowed_peer is None or client_address[0] == self.allowed_peer


class TcpServer(PeerFilter, socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


class UdpServer(PeerFilter, socketserver.ThreadingUDPServer):
    allow_reuse_address = True
    daemon_threads = True


class HttpHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.headers.get("Upgrade", "").lower() == "websocket":
            key = self.headers["Sec-WebSocket-Key"] + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
            self.send_response(101)
            self.send_header("Upgrade", "websocket")
            self.send_header("Connection", "Upgrade")
            self.send_header("Sec-WebSocket-Accept", base64.b64encode(hashlib.sha1(key.encode()).digest()).decode())
            self.end_headers()
            event("websocket")
            self.connection.settimeout(5)
            try:
                self.connection.recv(4096)
            except OSError:
                pass
            self.close_connection = True
        else:
            body = b"sdk37-local-network-ok"
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            try:
                self.wfile.write(body)
            except OSError:
                pass
            event("http", path=self.path)

    def log_message(self, *_):
        pass


class UdpHandler(socketserver.BaseRequestHandler):
    def handle(self):
        data, sock = self.request
        sock.sendto(data, self.client_address)
        event("udp")


class MqttHandler(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(5)
        try:
            if self.request.recv(1) != b"\x10":
                return
            length, multiplier = 0, 1
            for _ in range(4):
                byte = self.request.recv(1)
                if not byte:
                    return
                length += (byte[0] & 127) * multiplier
                multiplier *= 128
                if not byte[0] & 128:
                    break
            received = 0
            while received < length:
                chunk = self.request.recv(length - received)
                if not chunk:
                    return
                received += len(chunk)
            self.request.sendall(b"\x20\x02\x00\x00")
            event("mqtt-connect")
            self.request.recv(1024)
        except OSError:
            pass


def vscode_hello(sock):
    payload = json.dumps({"type": "hello", "data": {"extensionVersion": "99.0.0"}}).encode()
    sock.sendall(struct.pack(">ii", len(payload), 1) + payload)


class VsCodeHandler(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(5)
        try:
            vscode_hello(self.request)
            event("vscode-client")
            while self.request.recv(4096):
                pass
        except OSError:
            pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", type=ipaddress.IPv4Address, default=ipaddress.IPv4Address("127.0.0.1"))
    parser.add_argument("--device", type=ipaddress.IPv4Address, help="Physical phone IPv4 address, reachable from this PC")
    args = parser.parse_args()
    if args.device:
        if any(address.is_loopback or address.is_unspecified or address.is_multicast
               or not address.is_private for address in (args.bind, args.device)):
            parser.error("Physical mode requires explicit private unicast LAN IPv4 addresses for both --bind and --device")
    elif str(args.bind) != "127.0.0.1":
        parser.error("A non-loopback bind requires --device to select the single test peer")
    stopped = threading.Event()
    servers = [
        TcpServer((str(args.bind), 17637), HttpHandler),
        UdpServer((str(args.bind), 17638), UdpHandler),
        TcpServer((str(args.bind), 17639), MqttHandler),
        TcpServer((str(args.bind), 17640), VsCodeHandler),
    ]
    for server in servers:
        server.allowed_peer = str(args.device) if args.device else None
    inbound_host = str(args.device) if args.device else "127.0.0.1"
    inbound_port, vscode_port = (17643, 7347) if args.device else (17645, 17646)
    source_address = (str(args.bind), 0) if args.device else None

    def probe_inbound():
        while not stopped.is_set():
            try:
                with socket.create_connection((inbound_host, inbound_port), timeout=0.3, source_address=source_address) as sock:
                    sock.sendall(b"P")
                    # Keep the host endpoint alive until the guest consumes the payload.
                    # Closing immediately can discard buffered data in emulator redirection.
                    sock.settimeout(4)
                    sock.recv(1)
            except OSError:
                pass
            stopped.wait(0.3)

    def probe_vscode_server():
        while not stopped.is_set():
            try:
                with socket.create_connection((inbound_host, vscode_port), timeout=0.3, source_address=source_address) as sock:
                    sock.settimeout(4)
                    vscode_hello(sock)
                    if sock.recv(4096):
                        event("vscode-server")
                        stopped.wait(2)
            except OSError:
                pass
            stopped.wait(0.3)

    for server in servers:
        threading.Thread(target=server.serve_forever, daemon=True).start()
    threading.Thread(target=probe_inbound, daemon=True).start()
    threading.Thread(target=probe_vscode_server, daemon=True).start()
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    event("ready", pid=os.getpid(), mode="physical-ipv4" if args.device else "emulator",
          bind=str(args.bind), allowed_peer=str(args.device) if args.device else None,
          ports=[17637, 17638, 17639, 17640], inbound_host=inbound_host, inbound_ports=[inbound_port, vscode_port])
    try:
        while not stopped.wait(0.5):
            pass
    finally:
        stopped.set()
        for server in servers:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    main()
