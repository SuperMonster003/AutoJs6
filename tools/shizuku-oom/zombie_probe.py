"""Shizuku user service residue probe (docs/shizuku-oom/ROADMAP.md P0.1, T1 / T2; issue #474).

Each round starts the host through RunIntentActivity (`am start -n org.autojs.autojs6/...RunIntentActivity -e path
<script>`, the route the tools/memory device scripts use; run_gate.py itself drives `am instrument`, which ends with the
host process and cannot outlive a force-stop) with a minimal script that calls `shizuku('id')`, writes a mark file and
exits. Once the user service process `<package>:shizuku-service-for-<last segment>` has appeared, the host is ended as
--mode says, and after --settle seconds (default 3) the probe counts the user service processes still running and sums
their RSS (`ps -A -o PID,USER,RSS,NAME`). A non-daemon user service must end with the host, so the expected residue is 0
(T2); a build without the 6.7.0 fix (489a8d3a70) leaves one more process every round.

Modes:
  force-stop       `am force-stop <package>` (issue #474: an app freezer such as Greenify force-stops the host).
  kill9            `run-as <package> kill -9 <host pid>` (debuggable host only).
  bind-kill:<sec>  A device-side loop starts the script, waits for the new user service process and force-stops the host
                   <sec> seconds after it appeared (before or after the service hands its binder to Shizuku).
  update           Installs --apk-a, runs the script, installs --apk-b over it (two builds whose versionCode differs, so
                   the bind version differs too), counts the old service, runs the script again and checks that only the
                   new service is left; then force-stops the host. Rounds alternate nothing: every round is A -> B.
  server-restart   Runs the script, restarts the Shizuku server with --start-sh (adb-started Shizuku only) and counts the
                   old service after the new server is up; then force-stops the host.
  spawn-orphan     The script keeps `shizuku.spawn('sleep 600')` running; after the force-stop the probe also counts the
                   service's child processes (sh / sleep) still alive, then kills those it can (same uid as adb shell).

Every round reads the logcat lines written since it began and records only how each user service process ended:
`destroy` (it logged `UserService: destroy`, the transaction Shizuku sends when the host's connection dies), `exited`
(gone without that line, e.g. the Shizuku starter's System.exit after the server died) or `alive`. It also counts the
sh / getevent / sleep processes that were reparented to init during the round, with their descendants (`stray`): the
commands the service was still running when it died, e.g. the `getevent -t -c 1` loop the host keeps running through the
service while volume key control is on. They are reported, killed when they run as shell, and do not decide the
verdict. Full process lists,
logcat text and the script output are not printed or stored; the report keeps per-round counts, RSS sums and the verdict.

Output: <report-dir>/probe-<model>-<versionCode>-<mode>-<yyyyMMdd>[-<label>].json and the same-named .md, one line per
round on the terminal, PASS / FAIL at the end. With --expect accumulate (negative control, P0.2) PASS means the probe saw
the residue grow by one in at least 80% of the rounds.

The probe never taps the screen and changes no host setting. A force-stop makes Android drop the host from
enabled_accessibility_services; when the host was in that list before the run, the probe puts its entries back at the
end (a11yRestored in the report). It installs APKs only in update mode (back up the original package first, see
README.md). Temporary files live in /sdcard/Download/aj6-shizuku-oom/ and are removed at the end.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
DEVICE_DIR = "/sdcard/Download/aj6-shizuku-oom"
SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
DEFAULT_START_SH = f"/sdcard/Android/data/{SHIZUKU_PACKAGE}/start.sh"
SERVER_NAME = "shizuku_server"
SPAWN_COMMAND = "sleep 600"
STRAY_NAMES = ("sh", "getevent", "sleep")
LOGCAT_LINE = re.compile(r"^\d\d-\d\d \S+\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.*?)\s*: (.*)$")


class ProbeError(Exception):
    pass


class Device:

    def __init__(self, serial):
        self.serial = serial

    def adb(self, *args, timeout=60, data=None):
        result = subprocess.run(["adb", "-s", self.serial, *args], input=data, capture_output=True, timeout=timeout)
        return result.returncode, result.stdout.decode("utf-8", "replace"), result.stderr.decode("utf-8", "replace")

    def shell(self, command, timeout=60):
        return self.adb("shell", command, timeout=timeout)[1]

    def write(self, remote, text):
        # `adb exec-in "cat > f"` writes nothing on Android 9 (no shell redirection), so push a local copy.
        # zh-CN: Android 9 上 `adb exec-in "cat > f"` 不经 shell 重定向, 什么也写不进去, 改为推送本地副本.
        fd, local = tempfile.mkstemp(suffix=".js")
        try:
            with os.fdopen(fd, "wb") as stream:
                stream.write(text.encode("utf-8"))
            code, _, err = self.adb("push", local, remote)
        finally:
            os.unlink(local)
        if code != 0:
            raise ProbeError(f"Unable to write {remote}: {err.strip()[:200]}")

    def prop(self, name):
        return self.shell(f"getprop {name}").strip()

    def now(self):
        """Device time in the logcat -T format."""
        return self.shell("date +'%m-%d %H:%M:%S.000'").strip()

    def processes(self):
        """(pid, ppid, user, rss KB, name) of every process."""
        rows = []
        for line in self.shell("ps -A -o PID,PPID,USER,RSS,NAME").splitlines()[1:]:
            parts = line.split(None, 4)
            if len(parts) == 5 and parts[0].isdigit():
                rows.append((int(parts[0]), int(parts[1]), parts[2], int(parts[3]) if parts[3].isdigit() else 0, parts[4].strip()))
        return rows

    def args_of(self, pids):
        """Command lines of [pids] (ps ARGS), for telling `sleep 600` from other sleeps."""
        if not pids:
            return {}
        out = self.shell("ps -o PID,ARGS -p " + ",".join(str(p) for p in pids))
        found = {}
        for line in out.splitlines()[1:]:
            parts = line.strip().split(None, 1)
            if len(parts) == 2 and parts[0].isdigit():
                found[int(parts[0])] = parts[1]
        return found

    def logcat_since(self, since):
        return self.shell(f"logcat -d -v threadtime -T '{since}'", timeout=120).splitlines()


class Probe:

    def __init__(self, device, args):
        self.dev = device
        self.args = args
        self.package = args.package
        self.service_name = f"{args.package}:shizuku-service-for-{args.package.rsplit('.', 1)[-1]}"
        self.pre_sleeps = set()

    # Processes. zh-CN: 进程.

    def services(self, rows=None):
        return [r for r in (rows or self.dev.processes()) if r[4] == self.service_name]

    def host_pid(self):
        out = self.dev.shell(f"pidof {self.package}").split()
        return int(out[0]) if out and out[0].isdigit() else None

    def host_alive(self, rows):
        # Something may start the host again within the settle time (an enabled accessibility service, a receiver),
        # and that host binds a fresh user service in App.onCreate; such a count is not a residue.
        # zh-CN: 等待期间宿主可能被再次拉起 (已启用的无障碍服务, 广播接收器), 它在 App.onCreate 里会绑定新的用户服务, 这时的计数不是残留.
        return any(r[4] == self.package for r in rows)

    def server_pid(self):
        out = self.dev.shell(f"pidof {SERVER_NAME}").split()
        return int(out[0]) if out and out[0].isdigit() else None

    @staticmethod
    def descendants(rows, root):
        children = {}
        for pid, ppid, _, _, _ in rows:
            children.setdefault(ppid, []).append(pid)
        found, queue = [], [root]
        while queue:
            for child in children.get(queue.pop(), []):
                found.append(child)
                queue.append(child)
        return found

    # Script. zh-CN: 脚本.

    def script_text(self, mark, spawn):
        write = f"files.write('{mark}', "
        if spawn:
            return "\n".join([
                f"var p = shizuku.spawn('{SPAWN_COMMAND}');",
                write + "'spawned');",
                "setInterval(function () {}, 1000);",
                "",
            ])
        return "\n".join([
            "var r = shizuku('id');",
            "var m = /uid=(\\d+)/.exec(String(r.result));",
            write + "'code=' + r.code + ' uid=' + (m ? m[1] : '-'));",
            "exit();",
            "",
        ])

    def prepare_script(self, spawn=False):
        token = secrets.token_hex(4)
        mark = f"{DEVICE_DIR}/mark-{token}.txt"
        script = f"{DEVICE_DIR}/probe-{token}.js"
        self.dev.write(script, self.script_text(mark, spawn))
        return script, mark

    def start_command(self, script):
        return f"am start -n {self.package}/{RUN_ACTIVITY} -e path {script}"

    def start_script(self, script):
        code, out, err = self.dev.adb("shell", self.start_command(script))
        if code != 0 or "Error" in out:
            raise ProbeError(f"am start failed: {(out + err).strip()[:200]}")

    def read_mark(self, mark):
        code, out, _ = self.dev.adb("shell", f"cat {mark} 2>/dev/null")
        return out.strip() if code == 0 and out.strip() else None

    def wait_new_service(self, before, mark=None, wait=None):
        """Waits for a user service pid not in [before] (and the mark file when given); returns (pid, rss KB, mark)."""
        deadline = time.time() + (wait or self.args.wait)
        pid, rss, text = None, 0, None
        while time.time() < deadline:
            if pid is None:
                fresh = [r for r in self.services() if r[0] not in before]
                if fresh:
                    pid, rss = fresh[0][0], fresh[0][3]
            if mark and text is None:
                text = self.read_mark(mark)
            if pid is not None and (mark is None or text is not None):
                break
            time.sleep(0.5)
        if pid is not None:
            # RSS once the service has finished starting. zh-CN: 服务启动完成后的 RSS.
            rss = next((r[3] for r in self.services() if r[0] == pid), rss)
        return pid, rss, text

    # Ending the host. zh-CN: 结束宿主.

    def force_stop(self):
        self.dev.shell(f"am force-stop {self.package}")

    def kill9(self):
        pid = self.host_pid()
        if pid is None:
            return "host not running"
        code, out, err = self.dev.adb("shell", f"run-as {self.package} kill -9 {pid}")
        if code != 0 or out.strip() or err.strip():
            raise ProbeError(f"run-as kill failed (is the host debuggable?): {(out + err).strip()[:200]}")
        return None

    def bind_kill(self, script, before, delay):
        """Starts [script], waits on the device for a new service pid, sleeps [delay] s, force-stops the host."""
        limit = int(self.args.wait / 0.05)
        old = " ".join(str(p) for p in before)
        loop = "; ".join([
            self.start_command(script) + " >/dev/null",
            f'old=" {old} "; found=""; n=0',
            f"while [ $n -lt {limit} ]; do for p in $(pidof {self.service_name}); do "
            'case "$old" in *" $p "*) ;; *) found=$p ;; esac; done; [ -n "$found" ] && break; sleep 0.05; n=$((n+1)); done',
            f'[ -n "$found" ] && sleep {delay}',
            f"am force-stop {self.package}",
            'echo "found=$found"',
        ])
        out = self.dev.shell(loop, timeout=self.args.wait + 60)
        found = re.search(r"found=(\d+)", out)
        return int(found[1]) if found else None

    # Logcat. zh-CN: 日志.

    def events(self, since, service_pids):
        """Per service pid: constructed / destroy logged; host: onServiceConnected seen (tag WrappedShizuku)."""
        seen = {pid: {"constructed": False, "destroy": False} for pid in service_pids}
        connected = False
        for line in self.dev.logcat_since(since):
            found = LOGCAT_LINE.match(line)
            if not found:
                continue
            pid, tag, message = int(found[1]), found[4], found[5]
            if pid in seen and tag == "UserService":
                if message.startswith("constructor"):
                    seen[pid]["constructed"] = True
                elif message.strip() == "destroy":
                    seen[pid]["destroy"] = True
            elif pid not in seen and tag == "WrappedShizuku" and message.startswith("onServiceConnected"):
                connected = True
        return seen, connected

    def exit_source(self, pid, alive, seen):
        if pid is None:
            return "-"
        if alive:
            return "alive"
        return "destroy" if seen.get(pid, {}).get("destroy") else "exited"

    # Rounds. zh-CN: 轮次.

    def settle_and_count(self):
        time.sleep(self.args.settle)
        rows = self.dev.processes()
        services = self.services(rows)
        return rows, services

    def strays(self, item, rows, known):
        """Records the sh / getevent / sleep processes reparented to init this round (with their descendants), i.e. what
        the service was running when it died, e.g. the host's `getevent -t -c 1` loop (InputEventObserver); kills those
        with the adb shell uid. zh-CN: 记录本轮新出现, 已被 init 收养的 sh / getevent / sleep 进程 (含其后代), 即服务死亡时仍在运行的命令,
        例如宿主 InputEventObserver 的 `getevent -t -c 1` 循环; 与 adb shell 同 uid 的会被结束."""
        roots = [r[0] for r in rows if r[1] == 1 and r[0] not in known and r[4].rsplit("/", 1)[-1] in STRAY_NAMES]
        found = set(roots)
        for root in roots:
            found.update(self.descendants(rows, root))
        item["strayExec"] = len(found)
        item["strayExecRssKb"] = sum(r[3] for r in rows if r[0] in found)
        item["strayExecNames"] = sorted({r[4].rsplit("/", 1)[-1] for r in rows if r[0] in found})
        killable = sorted(r[0] for r in rows if r[0] in found and r[2] == "shell")
        item["strayExecKilled"] = self.kill_pids(killable)

    def round_basic(self, index):
        mode = self.args.mode
        rows = self.dev.processes()
        known = {r[0] for r in rows}
        before = {r[0] for r in self.services(rows)}
        since = self.dev.now()
        spawn = mode == "spawn-orphan"
        script, mark = self.prepare_script(spawn=spawn)
        item = {"round": index}
        if mode.startswith("bind-kill:"):
            delay = float(mode.split(":", 1)[1])
            pid = self.bind_kill(script, before, delay)
            item["killDelaySec"] = delay
            rss = 0
        else:
            self.start_script(script)
            pid, rss, text = self.wait_new_service(before, mark)
            item["mark"] = text
        item["servicePid"] = pid
        item["serviceRssKb"] = rss
        children = []
        if spawn and pid is not None:
            rows = self.dev.processes()
            children = self.descendants(rows, pid)
            names = {r[0]: r[4] for r in rows}
            item["children"] = sorted(names.get(c, "?").rsplit("/", 1)[-1] for c in children)
        if not mode.startswith("bind-kill:"):
            if mode == "kill9":
                problem = self.kill9()
                if problem:
                    item["note"] = problem
            else:
                self.force_stop()
        rows, services = self.settle_and_count()
        alive_pids = {r[0] for r in services}
        item["residue"] = len(services)
        item["residueRssKb"] = sum(r[3] for r in services)
        item["hostAlive"] = self.host_alive(rows)
        seen, connected = self.events(since, [pid] if pid else [])
        item["exitSource"] = self.exit_source(pid, pid in alive_pids, seen)
        if pid is not None:
            item["constructed"] = seen[pid]["constructed"]
        if mode.startswith("bind-kill:"):
            item["hostConnectedBeforeKill"] = connected
        if spawn:
            live = {r[0] for r in rows}
            orphans = [c for c in children if c in live]
            names = {r[0]: r[4] for r in rows}
            item["orphans"] = len(orphans)
            item["orphanNames"] = sorted(names.get(c, "?").rsplit("/", 1)[-1] for c in orphans)
            # Also count `sleep 600` processes started this round that are no longer children of the service.
            args = self.dev.args_of([r[0] for r in rows if r[4].rsplit("/", 1)[-1] == "sleep" and r[0] not in self.pre_sleeps])
            stray = [p for p, a in args.items() if a.strip() == SPAWN_COMMAND]
            item["sleep600"] = len(stray)
            killed = self.kill_pids(sorted(set(orphans) | set(stray)))
            item["orphansKilled"] = killed
        self.strays(item, rows, known)
        if pid is None:
            item["note"] = item.get("note") or "no new user service process"
        return item

    def round_update(self, index):
        a, b = self.args.apk_a, self.args.apk_b
        item = {"round": index}
        known = {r[0] for r in self.dev.processes()}
        self.install(a)
        time.sleep(2)
        before = {r[0] for r in self.services()}
        script, mark = self.prepare_script()
        self.start_script(script)
        pid_a, rss_a, text = self.wait_new_service(before, mark)
        item.update(servicePid=pid_a, serviceRssKb=rss_a, mark=text)
        since = self.dev.now()
        self.install(b)
        rows, services = self.settle_and_count()
        alive = {r[0] for r in services}
        item["residueAfterUpdate"] = int(pid_a in alive) if pid_a else 0
        item["residue"] = len(services)
        item["residueRssKb"] = sum(r[3] for r in services)
        seen, _ = self.events(since, [pid_a] if pid_a else [])
        item["exitSource"] = self.exit_source(pid_a, pid_a in alive, seen)
        # The new build binds with its own version; only its service may be left. zh-CN: 新构建以自己的 version 绑定, 只应剩它的服务.
        script, mark = self.prepare_script()
        before = alive
        self.start_script(script)
        pid_b, _, text_b = self.wait_new_service(before, mark)
        item["newServicePid"] = pid_b
        item["newMark"] = text_b
        item["servicesWithNewHost"] = len(self.services())
        self.force_stop()
        rows, services = self.settle_and_count()
        item["residueAfterStop"] = len(services)
        item["residue"] = max(item["residue"], len(services))
        item["residueRssKb"] = max(item["residueRssKb"], sum(r[3] for r in services))
        self.strays(item, rows, known)
        if pid_a is None or pid_b is None:
            item["note"] = "no new user service process"
        return item

    def round_server_restart(self, index):
        item = {"round": index}
        rows = self.dev.processes()
        known = {r[0] for r in rows}
        before = {r[0] for r in self.services(rows)}
        script, mark = self.prepare_script()
        self.start_script(script)
        pid, rss, text = self.wait_new_service(before, mark)
        item.update(servicePid=pid, serviceRssKb=rss, mark=text)
        old_server = self.server_pid()
        since = self.dev.now()
        code, _, _ = self.dev.adb("shell", f"sh {self.args.start_sh} >/dev/null 2>&1", timeout=120)
        item["startShExit"] = code
        deadline = time.time() + 30
        new_server = None
        while time.time() < deadline:
            new_server = self.server_pid()
            if new_server and new_server != old_server:
                break
            time.sleep(0.5)
        item["serverRestarted"] = bool(new_server and new_server != old_server)
        _, services = self.settle_and_count()
        old = [r for r in services if r[0] == pid]
        item["residueAfterRestart"] = len(old)
        seen, _ = self.events(since, [pid] if pid else [])
        item["exitSource"] = self.exit_source(pid, bool(old), seen)
        self.force_stop()
        rows, services = self.settle_and_count()
        item["residueAfterStop"] = len(services)
        item["residue"] = max(len(old), len(services))
        item["residueRssKb"] = max(sum(r[3] for r in old), sum(r[3] for r in services))
        self.strays(item, rows, known)
        if pid is None:
            item["note"] = "no new user service process"
        return item

    # Helpers. zh-CN: 辅助.

    def install(self, apk):
        code, out, err = self.dev.adb("install", "-r", "-d", "-t", str(apk), timeout=300)
        if code != 0 or "Success" not in out:
            raise ProbeError(f"install {Path(apk).name} failed: {(out + err).strip()[-200:]}")

    def kill_pids(self, pids):
        if not pids:
            return 0
        self.dev.shell("kill -9 " + " ".join(str(p) for p in pids) + " 2>/dev/null")
        time.sleep(0.5)
        live = {r[0] for r in self.dev.processes()}
        return sum(1 for p in pids if p not in live)

    def host_info(self):
        dump = self.dev.shell(f"dumpsys package {self.package}")
        code = re.search(r"versionCode=(\d+)", dump)
        name = re.search(r"versionName=(\S+)", dump)
        flags = re.search(r"pkgFlags=\[([^\]]*)\]", dump)
        granted = re.search(re.escape(SHIZUKU_PERMISSION) + r": granted=(\w+)", dump)
        return {
            "versionCode": int(code[1]) if code else None,
            "versionName": name[1] if name else None,
            "debuggable": bool(flags and "DEBUGGABLE" in flags[1]),
            "shizukuPermission": granted[1] == "true" if granted else False,
        }

    def shizuku_info(self):
        dump = self.dev.shell(f"dumpsys package {SHIZUKU_PACKAGE}")
        name = re.search(r"versionName=(\S+)", dump)
        server = next((r for r in self.dev.processes() if r[4] == SERVER_NAME), None)
        return {"version": name[1] if name else None, "serverUser": server[2] if server else None}

    def a11y_entries(self):
        value = self.dev.shell("settings get secure enabled_accessibility_services").strip()
        return [e for e in value.split(":") if e and e != "null"]

    def host_a11y(self, entries):
        return [e for e in entries if e.startswith(f"{self.package}/")]

    def restore_a11y(self, before):
        """Puts back the host entries a force-stop dropped (Android removes a force-stopped package from the list)."""
        current = self.a11y_entries()
        missing = [e for e in self.host_a11y(before) if e not in current]
        if missing:
            self.dev.shell(f"settings put secure enabled_accessibility_services '{':'.join(current + missing)}'")
        return len(missing)


def verdict(items, args):
    # Stray exec processes are reported but do not decide the verdict (T2 counts user service processes).
    # zh-CN: 孤儿命令进程只报告, 不参与判定 (T2 只计用户服务进程).
    strays = sum(1 for i in items if i.get("strayExec"))
    stray_note = f"; orphaned exec processes in {strays}/{len(items)} rounds" if strays else ""
    if args.expect == "accumulate":
        steps = [b["residue"] - a["residue"] for a, b in zip([{"residue": args.start_residue}] + items, items)]
        grew = sum(1 for s in steps if s >= 1)
        ok = grew >= max(1, int(len(items) * 0.8 + 0.5))
        return ("PASS" if ok else "FAIL"), f"residue grew in {grew}/{len(items)} rounds (expect accumulate){stray_note}"
    bad = [i for i in items if i.get("residue") or i.get("orphans") or i.get("sleep600") or i.get("servicePid") is None]
    total = sum(1 for i in items if i.get("residue"))
    if args.mode == "spawn-orphan":
        orphaned = sum(1 for i in items if i.get("orphans") or i.get("sleep600"))
        return ("FAIL" if bad else "PASS"), f"service residue in {total}/{len(items)} rounds, orphans in {orphaned}/{len(items)} rounds{stray_note}"
    return ("FAIL" if bad else "PASS"), f"residue in {total}/{len(items)} rounds{stray_note}"


def mb(kb):
    return f"{kb / 1024:.1f}"


def round_note(item, mode):
    extra = []
    if mode == "spawn-orphan":
        extra.append(f"orphans {item.get('orphans', '-')} {'/'.join(item.get('orphanNames') or []) or ''}".rstrip())
        extra.append(f"sleep600 {item.get('sleep600', '-')}")
    if mode == "update":
        extra.append(f"after-update {item.get('residueAfterUpdate', '-')} after-stop {item.get('residueAfterStop', '-')}")
    if mode == "server-restart":
        extra.append(f"after-restart {item.get('residueAfterRestart', '-')} after-stop {item.get('residueAfterStop', '-')}")
    if mode.startswith("bind-kill:"):
        extra.append(f"connected {'yes' if item.get('hostConnectedBeforeKill') else 'no'}")
    if item.get("strayExec"):
        extra.append(f"stray {item['strayExec']} {'/'.join(item.get('strayExecNames') or [])} "
                     f"{mb(item.get('strayExecRssKb', 0))} MB killed {item.get('strayExecKilled', 0)}")
    if item.get("hostAlive"):
        extra.append("host alive at count")
    if item.get("note"):
        extra.append(item["note"])
    return "  ".join(extra)


def round_line(item, mode):
    return (f"round {item['round']:>3}  residue {item.get('residue', '-'):>2}  rss {mb(item.get('residueRssKb', 0)):>7} MB  "
            f"exit {item.get('exitSource', '-'):<8} {round_note(item, mode)}").rstrip()


def write_reports(meta, items, result, args):
    out = Path(args.report_dir)
    if not out.is_absolute():
        out = ROOT / out
    out.mkdir(parents=True, exist_ok=True)
    mode = args.mode.replace(":", "-")
    stem = f"probe-{meta['model']}-{meta['host']['versionCode']}-{mode}-{meta['date']}" + (f"-{args.label}" if args.label else "")
    data = {"meta": meta, "rounds": items, "verdict": result[0], "summary": result[1]}
    (out / f"{stem}.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = [
        f"# Shizuku 用户服务残留探针: {meta['model']} / {meta['host']['versionName']} ({meta['host']['versionCode']}) / {args.mode}",
        "",
        f"- 设备: {meta['model']} ({meta['serial']}), Android {meta['android']} / API {meta['sdk']}",
        f"- 宿主: {meta['host']['versionName']} versionCode {meta['host']['versionCode']}, "
        f"{'debuggable' if meta['host']['debuggable'] else 'release'}, Shizuku 授权 {meta['host']['shizukuPermission']}",
        f"- Shizuku: {meta['shizuku']['version']}, 服务端身份 {meta['shizuku']['serverUser']}",
        f"- 模式 {args.mode}, 轮数 {len(items)}, 结束宿主后等待 {args.settle} s, 开始前残留 {meta['startResidue']}"
        + (f", 预期 {args.expect}" if args.expect != "none" else ""),
        f"- 时间 {meta['startedAt']} - {meta['endedAt']}; 宿主无障碍服务开启 {meta['a11yBefore']} -> {meta['a11yAfter']}"
        + (f" (强停使其被系统移出启用列表, 结束时补回 {meta['a11yRestored']} 项)" if meta.get("a11yRestored") else ""),
        "- 探针: `tools/shizuku-oom/zombie_probe.py` (ROADMAP P0.1); 残留 = 结束宿主后仍在运行的用户服务进程数, RSS 合计为这些进程的 RSS 之和; "
        "备注里的 stray = 本轮新出现且被 init 收养的 sh / getevent / sleep 及其后代 (服务死亡时仍在运行的命令), 只报告, 不参与判定",
        "",
        "| 轮次 | 残留 | RSS 合计 MB | 退出来源 | 备注 |",
        "| --- | --- | --- | --- | --- |",
    ]
    for item in items:
        note = round_note(item, args.mode)
        lines.append(f"| {item['round']} | {item.get('residue', '-')} | {mb(item.get('residueRssKb', 0))} | {item.get('exitSource', '-')} | {note} |")
    lines += ["", f"结论: **{result[0]}** ({result[1]})", ""]
    (out / f"{stem}.md").write_text("\n".join(lines), encoding="utf-8")
    return out / f"{stem}.md"


def mode_type(value):
    if value in ("force-stop", "kill9", "update", "server-restart", "spawn-orphan"):
        return value
    found = re.fullmatch(r"bind-kill:(\d+(?:\.\d+)?)", value)
    if found and float(found[1]) <= 30:
        return value
    raise argparse.ArgumentTypeError("mode must be force-stop, kill9, bind-kill:<seconds <= 30>, update, server-restart or spawn-orphan")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--rounds", type=int, default=10)
    parser.add_argument("--mode", type=mode_type, default="force-stop")
    parser.add_argument("--report-dir", default="docs/shizuku-oom/evidence", help="relative paths are resolved against the repository root")
    parser.add_argument("--label", help="suffix of the report name, e.g. v664")
    parser.add_argument("--package", default="org.autojs.autojs6")
    parser.add_argument("--settle", type=float, default=3.0, help="seconds between ending the host and counting (T2: 3)")
    parser.add_argument("--wait", type=float, default=45.0, help="seconds to wait for the user service and the script mark")
    parser.add_argument("--expect", choices=("none", "accumulate"), default="none",
                        help="accumulate: negative control, PASS when the residue grows by one in >= 80%% of the rounds")
    parser.add_argument("--apk-a", help="update mode: first build (installed with -r -d)")
    parser.add_argument("--apk-b", help="update mode: second build with another versionCode, installed over --apk-a")
    parser.add_argument("--start-sh", default=DEFAULT_START_SH, help="server-restart mode: Shizuku start script on the device")
    parser.add_argument("--kill-residue", action="store_true",
                        help="at the end, kill user service processes that appeared during the run (negative control cleanup)")
    parser.add_argument("--no-wake", action="store_true", help="do not send KEYCODE_WAKEUP before the run")
    args = parser.parse_args()
    if args.mode == "update" and not (args.apk_a and args.apk_b):
        parser.error("update mode needs --apk-a and --apk-b")
    if args.rounds < 1:
        parser.error("--rounds must be at least 1")

    dev = Device(args.serial)
    probe = Probe(dev, args)
    state = dev.adb("get-state")[1].strip()
    if state != "device":
        raise SystemExit(f"{args.serial} is not online ({state or 'unknown'})")
    host = probe.host_info()
    if host["versionCode"] is None:
        raise SystemExit(f"{args.package} is not installed on {args.serial}")
    if not host["shizukuPermission"]:
        raise SystemExit("The host has no Shizuku permission; grant it on the phone first")
    if probe.server_pid() is None:
        raise SystemExit("The Shizuku server is not running")
    if args.mode == "kill9" and not host["debuggable"]:
        raise SystemExit("kill9 mode needs a debuggable host (run-as)")
    if args.mode == "server-restart" and dev.adb("shell", f"ls {args.start_sh}")[0] != 0:
        raise SystemExit(f"{args.start_sh} not found (server-restart needs an adb-started Shizuku)")

    if not args.no_wake:
        dev.shell("input keyevent KEYCODE_WAKEUP")
    if "isKeyguardShowing=true" in dev.shell("dumpsys window | grep isKeyguardShowing"):
        print("warning: the keyguard is showing; unlock the phone if the script does not start", file=sys.stderr)
    dev.shell(f"mkdir -p {DEVICE_DIR}")

    # Start from a stopped host so that round 1 starts its own service. zh-CN: 从宿主已停止的状态开始, 第 1 轮启动自己的服务.
    # With the host's accessibility service enabled, Android 9 binds it again right after the first force-stop and the
    # host comes back; the next force-stop drops it from the enabled list. zh-CN: 宿主无障碍服务启用时, Android 9 在第一次强停后
    # 立即重新绑定, 宿主随之复活; 再强停一次系统才把它移出启用列表.
    a11y_before = probe.a11y_entries()
    start_stops = 0
    while True:
        probe.force_stop()
        start_stops += 1
        time.sleep(args.settle)
        first_rows = dev.processes()
        if not probe.host_alive(first_rows) or start_stops >= 3:
            break
    initial = {r[0] for r in probe.services(first_rows)}
    args.start_residue = len(initial)
    if probe.host_alive(first_rows):
        print(f"warning: the host is still running after {start_stops} force-stops; the start residue may be a live service", file=sys.stderr)
    probe.pre_sleeps = {r[0] for r in first_rows if r[4].rsplit("/", 1)[-1] == "sleep"}
    started = datetime.datetime.now()
    meta = {
        "serial": args.serial,
        "model": dev.prop("ro.product.model").replace(" ", "_"),
        "android": dev.prop("ro.build.version.release"),
        "sdk": dev.prop("ro.build.version.sdk"),
        "host": host,
        "shizuku": probe.shizuku_info(),
        "mode": args.mode,
        "rounds": args.rounds,
        "settleSec": args.settle,
        "startResidue": args.start_residue,
        "startHostAlive": probe.host_alive(first_rows),
        "startForceStops": start_stops,
        "date": started.strftime("%Y%m%d"),
        "startedAt": started.strftime("%Y-%m-%d %H:%M:%S"),
    }
    print(f"{meta['model']} {host['versionName']} ({host['versionCode']}) mode {args.mode}, start residue {args.start_residue}")
    items = []
    try:
        for index in range(1, args.rounds + 1):
            if args.mode == "update":
                item = probe.round_update(index)
            elif args.mode == "server-restart":
                item = probe.round_server_restart(index)
            else:
                item = probe.round_basic(index)
            items.append(item)
            print(round_line(item, args.mode), flush=True)
    except (ProbeError, subprocess.TimeoutExpired) as e:
        print(f"stopped: {e}", file=sys.stderr)
        meta["stopped"] = str(e)
    finally:
        if args.mode == "update":
            meta["host"] = probe.host_info()  # The report names the build installed last. zh-CN: 报告以最后安装的构建命名.
            meta["apks"] = [Path(args.apk_a).name, Path(args.apk_b).name]
        meta["endedAt"] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        meta["a11yBefore"] = bool(probe.host_a11y(a11y_before))
        meta["a11yRestored"] = probe.restore_a11y(a11y_before)
        meta["a11yAfter"] = bool(probe.host_a11y(probe.a11y_entries()))
        if args.kill_residue:
            leftover = [r[0] for r in probe.services() if r[0] not in initial]
            meta["residueKilled"] = probe.kill_pids(leftover)
            meta["residueKillTargets"] = len(leftover)
            print(f"cleanup: killed {meta['residueKilled']}/{len(leftover)} residual user service processes")
        dev.shell(f"rm -rf {DEVICE_DIR}")
    if not items:
        raise SystemExit("no round completed")
    result = verdict(items, args)
    report = write_reports(meta, items, result, args)
    print(f"{result[0]}  {result[1]}")
    print(f"report: {report.relative_to(ROOT) if report.is_relative_to(ROOT) else report}")
    return 0 if result[0] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
