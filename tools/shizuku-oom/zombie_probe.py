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
  volume-key       T6 (9) / P1.5: the script beats until stopped; once one getevent runs under the user service (volume key
                   control on), the probe writes a volume up key to the evdev node that reports KEY_VOLUMEUP (`sendevent`,
                   as a real key press: the system volume panel shows and the volume may step), checks that the script
                   stopped, puts back the ring / music / notification volumes it changed, then force-stops the host. Stray
                   exec processes fail the round.

Every round reads the logcat lines written since it began and records only how each user service process ended: the
reason it logged (`UserService: exit: <reason>`, since P1.1 / P1.2: destroy / exit / host-died / no-host-attached),
else `destroy` (it logged `UserService: destroy`, the transaction Shizuku sends when the host's connection dies),
`exited` (gone without either line, e.g. the Shizuku starter's System.exit after the server died) or `alive`. It also counts the
sh / getevent / sleep processes that were reparented to init during the round, with their descendants (`stray`): the
commands the service was still running when it died, e.g. the `getevent -t -c 1` loop the host keeps running through the
service while volume key control is on. They are reported, killed when they run as shell, and do not decide the
verdict unless --stray-fails (or volume-key mode). With --profile, round 1 also records a memory profile of its user service before the host is ended (P0.4):
dumpsys meminfo totals, threads, dex extracted into anonymous memory and whether the host App ran in that process.
Full process lists, logcat text and the script output are not printed or stored; the report keeps per-round counts, RSS
sums and the verdict.

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
VOLUME_STREAMS = (2, 3, 5)
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
        self._volume_device = None

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

    def script_text(self, mark, spawn, volume=False):
        write = f"files.write('{mark}', "
        if volume:
            # Beats until a volume up key stops it (the exit event writes "stopped"); ends itself after 120 s.
            # zh-CN: 持续心跳, 直到音量加键停止它 (exit 事件写入 "stopped"); 120 秒后自行结束.
            return "\n".join([
                "var beats = 0, timedOut = false;",
                "events.on('exit', function () { " + write + "timedOut ? 'timeout' : 'stopped'); });",
                write + "'beat 0');",
                "setInterval(function () { " + write + "'beat ' + (++beats)); }, 500);",
                "setTimeout(function () { timedOut = true; exit(); }, 120000);",
                "",
            ])
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

    def prepare_script(self, spawn=False, volume=False):
        token = secrets.token_hex(4)
        mark = f"{DEVICE_DIR}/mark-{token}.txt"
        script = f"{DEVICE_DIR}/probe-{token}.js"
        self.dev.write(script, self.script_text(mark, spawn, volume))
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
        """Per service pid: constructed / destroy / host attached / exit reason logged; host: onServiceConnected seen
        (tag WrappedShizuku)."""
        seen = {pid: {"constructed": False, "destroy": False, "attached": False, "exitReason": None} for pid in service_pids}
        connected = False
        for line in self.dev.logcat_since(since):
            found = LOGCAT_LINE.match(line)
            if not found:
                continue
            pid, tag, message = int(found[1]), found[4], found[5].strip()
            if pid in seen and tag == "UserService":
                if message.startswith("constructor"):
                    seen[pid]["constructed"] = True
                elif message == "destroy":
                    seen[pid]["destroy"] = True
                elif message == "host attached":
                    seen[pid]["attached"] = True
                elif message.startswith("exit: "):
                    # Since P1.1 / P1.2 the service names why it exits (ROADMAP T3). zh-CN: P1.1 / P1.2 起服务写明退出原因.
                    seen[pid]["exitReason"] = message[len("exit: "):]
            elif pid not in seen and tag == "WrappedShizuku" and message.startswith("onServiceConnected"):
                connected = True
        return seen, connected

    def exit_source(self, pid, alive, seen):
        if pid is None:
            return "-"
        if alive:
            return "alive"
        events = seen.get(pid, {})
        return events.get("exitReason") or ("destroy" if events.get("destroy") else "exited")

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
            if self.args.profile and index == 1 and pid is not None:
                item["profile"] = self.profile(pid, rss)
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

    def round_volume_key(self, index):
        """T6 (9) / P1.5: with volume key control on, the host observes keys through one long-lived getevent under the
        user service; a volume up key sent to the evdev node stops the script, and the force-stop leaves no getevent.
        zh-CN: T6 (9) / P1.5: 开启音量键控制时, 宿主经用户服务下的一个常驻 getevent 观察按键; 向 evdev 节点发送音量加键能停止脚本,
        强停后不留 getevent."""
        rows = self.dev.processes()
        known = {r[0] for r in rows}
        before = {r[0] for r in self.services(rows)}
        since = self.dev.now()
        script, mark = self.prepare_script(volume=True)
        item = {"round": index}
        self.start_script(script)
        pid, rss, text = self.wait_new_service(before, mark)
        item.update(servicePid=pid, serviceRssKb=rss, mark=text)
        # GlobalKeyObserver starts the shell observer 1.2 s after the host created it. zh-CN: GlobalKeyObserver 创建 1.2 秒后启动 shell 监听.
        getevent, children = [], []
        deadline = time.time() + 15
        while pid is not None and time.time() < deadline:
            rows = self.dev.processes()
            names = {r[0]: r[4].rsplit("/", 1)[-1] for r in rows}
            children = [names.get(c, "?") for c in self.descendants(rows, pid)]
            getevent = [c for c in children if c == "getevent"]
            if getevent:
                break
            time.sleep(0.5)
        item["geteventUnderService"] = len(getevent)
        item["children"] = sorted(children)
        device = self.volume_up_device()
        if pid is None or not getevent or device is None:
            item["note"] = "no new user service process" if pid is None else (
                "no getevent under the service (is volume key control on?)" if not getevent else "no KEY_VOLUMEUP device")
        else:
            volumes = self.volumes()
            last = self.read_mark(mark)
            sent = time.time()
            self.dev.shell(f"sendevent {device} 1 115 1; sendevent {device} 0 0 0; sendevent {device} 1 115 0; sendevent {device} 0 0 0")
            stopped, signal, stalled_since = None, None, None
            while time.time() < sent + 8:
                current = self.read_mark(mark)
                if current == "stopped":
                    stopped, signal = time.time() - sent, "exit event"
                    break
                if current == last:
                    stalled_since = stalled_since or time.time()
                    if time.time() - stalled_since >= 2.5:
                        stopped, signal = stalled_since - sent, "beats stopped"
                        break
                else:
                    last, stalled_since = current, None
                time.sleep(0.25)
            item["volumeKeyStopped"] = stopped is not None
            item["volumeKeyStopSec"] = round(max(stopped, 0), 2) if stopped is not None else None
            item["volumeKeySignal"] = signal
            time.sleep(1)
            item["volumeStreamsRestored"] = self.restore_volumes(volumes)
        self.force_stop()
        rows, services = self.settle_and_count()
        alive_pids = {r[0] for r in services}
        item["residue"] = len(services)
        item["residueRssKb"] = sum(r[3] for r in services)
        item["hostAlive"] = self.host_alive(rows)
        seen, _ = self.events(since, [pid] if pid else [])
        item["exitSource"] = self.exit_source(pid, pid in alive_pids, seen)
        self.strays(item, rows, known)
        return item

    def volume_up_device(self):
        """The evdev node that reports KEY_VOLUMEUP (`getevent -pl`), looked up once."""
        if self._volume_device is None:
            current = None
            for line in self.dev.shell("getevent -pl 2>/dev/null", timeout=30).splitlines():
                found = re.match(r"add device \d+: (\S+)", line)
                if found:
                    current = found[1]
                elif current and "KEY_VOLUMEUP" in line:
                    self._volume_device = current
                    break
        return self._volume_device

    def volumes(self):
        """Stream volumes the sent key may change (ring, music, notification). zh-CN: 发送的按键可能改变的音量 (铃声, 媒体, 通知)."""
        found = {}
        for stream in VOLUME_STREAMS:
            for command in ("cmd media_session volume", "media volume"):
                value = re.search(r"volume is (\d+)", self.dev.shell(f"{command} --stream {stream} --get 2>/dev/null"))
                if value:
                    found[stream] = (command, int(value[1]))
                    break
        return found

    def restore_volumes(self, before):
        after = self.volumes()
        changed = [(stream, command, value) for stream, (command, value) in before.items() if after.get(stream, (None, value))[1] != value]
        for stream, command, value in changed:
            self.dev.shell(f"{command} --stream {stream} --set {value} >/dev/null 2>&1")
        return len(changed)

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

    def profile(self, pid, rss):
        """Memory profile of one user service process (P0.4 / P1.4 baseline), taken before the host is ended.

        Values are KB from `dumpsys meminfo <pid>` (App Summary PSS, plus RSS where Android prints it), the thread count,
        the dex the runtime had to extract into anonymous memory (smaps, readable only when the service runs as shell),
        and how many `App attachBaseContext |` / `App onCreate |` lines this process logged (App.attachBaseContext and
        WrappedShizuku.onCreate, which App.onCreate reaches through AutoJs.initInstance), which shows whether the host
        Application ran in the service process at all.
        zh-CN: 一个用户服务进程的内存画像 (P0.4 / P1.4 基线), 在结束宿主前采集. 数值单位 KB: `dumpsys meminfo <pid>` 的 App Summary
        PSS (Android 打印 RSS 时一并记录), 线程数, 运行时被迫解压到匿名内存的 dex (smaps, 仅服务以 shell 身份运行时可读),
        以及该进程打出的 `App attachBaseContext |` / `App onCreate |` 行数 (来自 App.attachBaseContext 与 App.onCreate 经
        AutoJs.initInstance 调到的 WrappedShizuku.onCreate), 用于判断宿主 Application 是否在服务进程里运行过.
        """
        dump = self.dev.shell(f"dumpsys meminfo {pid}", timeout=120)

        def summary(label):
            found = re.search(rf"^\s*{label}:\s+(\d+)(?:[ \t]+(\d+))?", dump, re.M)
            return (int(found[1]), int(found[2]) if found[2] else None) if found else (None, None)

        def row(label):
            found = re.search(rf"^\s*{re.escape(label)}\s+(\d+)\s+(\d+)", dump, re.M)
            return {"pss": int(found[1]), "privateDirty": int(found[2])} if found else None

        total = re.search(r"TOTAL PSS:\s+(\d+)", dump) or re.search(r"^\s*TOTAL:\s+(\d+)", dump, re.M)
        total_rss = re.search(r"TOTAL RSS:\s+(\d+)", dump)
        java, native, code = summary("Java Heap"), summary("Native Heap"), summary("Code")
        threads = self.dev.shell(f"ls /proc/{pid}/task | wc -l").strip()
        extracted = self.dev.shell(
            "awk '/^[0-9a-f]+-[0-9a-f]+ /{x = index($0, \"extracted in memory\") > 0} "
            f"x && /^Private_Dirty:/{{d += $2; n++}} END{{print n + 0, d + 0}}' /proc/{pid}/smaps 2>/dev/null || echo na").split()
        lines = self.dev.shell(f"logcat -d -v brief --pid={pid}", timeout=120).splitlines()
        user = next((r[2] for r in self.dev.processes() if r[0] == pid), None)
        return {
            "user": user,
            "rssKb": int(total_rss[1]) if total_rss else rss,
            "pssKb": int(total[1]) if total else None,
            "javaHeapPssKb": java[0],
            "nativeHeapPssKb": native[0],
            "codePssKb": code[0],
            "codeRssKb": code[1],
            "dexMmap": row(".dex mmap"),
            "artMmap": row(".art mmap"),
            "threads": int(threads) if threads.isdigit() else None,
            "extractedDexMaps": int(extracted[0]) if extracted[0].isdigit() else None,
            "extractedDexKb": int(extracted[1]) if len(extracted) > 1 and extracted[1].isdigit() else None,
            "appInitLines": sum(1 for line in lines if re.search(r"App (attachBaseContext|onCreate) \|", line)),
            "starterLogged": any("ShizukuServiceStarter" in line for line in lines),
        }

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
    # Stray exec processes are reported but do not decide the verdict (T2 counts user service processes), unless
    # --stray-fails (P1.1 / P1.5 acceptance) or volume-key mode asks for it.
    # zh-CN: 孤儿命令进程只报告, 不参与判定 (T2 只计用户服务进程); --stray-fails (P1.1 / P1.5 验收) 或 volume-key 模式下参与判定.
    strays = sum(1 for i in items if i.get("strayExec"))
    stray_note = f"; orphaned exec processes in {strays}/{len(items)} rounds" if strays else ""
    stray_fails = args.stray_fails or args.mode == "volume-key"
    if args.mode == "volume-key":
        stopped = sum(1 for i in items if i.get("volumeKeyStopped"))
        bad = [i for i in items if i.get("residue") or i.get("servicePid") is None or not i.get("volumeKeyStopped")
               or not i.get("geteventUnderService") or i.get("strayExec")]
        return ("FAIL" if bad else "PASS"), (f"volume key stopped the script in {stopped}/{len(items)} rounds, "
                                             f"residue in {sum(1 for i in items if i.get('residue'))}/{len(items)} rounds{stray_note}")
    if args.expect == "accumulate":
        steps = [b["residue"] - a["residue"] for a, b in zip([{"residue": args.start_residue}] + items, items)]
        grew = sum(1 for s in steps if s >= 1)
        ok = grew >= max(1, int(len(items) * 0.8 + 0.5))
        return ("PASS" if ok else "FAIL"), f"residue grew in {grew}/{len(items)} rounds (expect accumulate){stray_note}"
    bad = [i for i in items if i.get("residue") or i.get("orphans") or i.get("sleep600") or i.get("servicePid") is None
           or (stray_fails and i.get("strayExec"))]
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
    if mode == "volume-key":
        extra.append(f"getevent {item.get('geteventUnderService', '-')}")
        if "volumeKeyStopped" in item:
            extra.append(f"stopped {'yes' if item['volumeKeyStopped'] else 'no'}"
                         + (f" {item['volumeKeyStopSec']} s ({item['volumeKeySignal']})" if item["volumeKeyStopped"] else ""))
            extra.append(f"volume restored {item.get('volumeStreamsRestored', 0)}")
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
        "备注里的 stray = 本轮新出现且被 init 收养的 sh / getevent / sleep 及其后代 (服务死亡时仍在运行的命令), "
        + ("参与判定 (--stray-fails 或 volume-key 模式)" if args.stray_fails or args.mode == "volume-key" else "只报告, 不参与判定"),
    ]
    profile = next((i["profile"] for i in items if i.get("profile")), None)
    if profile:
        def size(kb):
            return "-" if kb is None else mb(kb)
        dex = profile.get("dexMmap") or {}
        lines.append(
            f"- 画像 (第 1 轮服务进程, 结束宿主前, 身份 {profile['user']}, 单位 MB): RSS {size(profile['rssKb'])}, PSS {size(profile['pssKb'])}, "
            f"Java 堆 {size(profile['javaHeapPssKb'])}, native {size(profile['nativeHeapPssKb'])}, code {size(profile['codePssKb'])} "
            f"(RSS {size(profile['codeRssKb'])}), .dex mmap PSS {size(dex.get('pss'))} / 私有脏页 {size(dex.get('privateDirty'))}, "
            f"线程 {profile['threads']}, 解压到内存的 dex {profile['extractedDexMaps'] if profile['extractedDexMaps'] is not None else '-'} 个映射 "
            f"{size(profile['extractedDexKb'])}, 该进程的 App attachBaseContext / onCreate 日志 {profile['appInitLines']} 行 "
            f"(启动器日志{'在' if profile['starterLogged'] else '不在'}缓冲区内)")
    lines += [
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
    if value in ("force-stop", "kill9", "update", "server-restart", "spawn-orphan", "volume-key"):
        return value
    found = re.fullmatch(r"bind-kill:(\d+(?:\.\d+)?)", value)
    if found and float(found[1]) <= 30:
        return value
    raise argparse.ArgumentTypeError("mode must be force-stop, kill9, bind-kill:<seconds <= 30>, update, server-restart, spawn-orphan or volume-key")


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
    parser.add_argument("--stray-fails", action="store_true",
                        help="stray exec processes fail the round (P1.1 / P1.5 acceptance); always on in volume-key mode")
    parser.add_argument("--profile", action="store_true",
                        help="profile the round 1 user service before the host is ended (meminfo, threads, extracted dex, App traces)")
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
            elif args.mode == "volume-key":
                item = probe.round_volume_key(index)
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
