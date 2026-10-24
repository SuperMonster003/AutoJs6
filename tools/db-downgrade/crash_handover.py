"""CrashHandler hand-over check (docs/db-downgrade/ROADMAP.md P1.5, D10).

From 6.7.0 Alpha11 (Bugly removed) to P1.5, CrashHandler took the default handler lazily, after it had become the
default itself, so a main thread crash recursed into CrashHandler and ended with exitProcess(1): no am_crash, no dropbox
entry, and no bad-process limit, so every pending broadcast, provider call or job started the crashing app again
(appendix B.1). P1.5 keeps the system's handler from before CrashHandler.install and hands such crashes to it after the
crash report.

The checks need a debuggable build with the temporary crash injection of tools/db-downgrade/README.md (P1.5 test build,
never committed): App.onCreate throws when files/db-downgrade-crash-startup exists, and the main thread throws 3 s after
the first activity resumed when files/db-downgrade-crash-foreground exists.

Subcommands:
  foreground  Arms the foreground injection and starts the launcher activity (an inrt build without a project crashes in
              SplashActivity before that, which is a foreground main thread crash as well), watches --observe seconds.
              PASS: one crash logged, the crash report page resumed (CrashReportActivity in :crash_report), am_crash and a
              new dropbox data_app_crash entry of the package, the crashed process gone (a job may start a new one).
  background  Clears a bad-process mark left by an earlier run (a provider call is a start the system does not count as
              background), arms the startup injection and sends the start broadcast of roundtrip.py --start broadcast
              every --interval seconds for --observe seconds (an explicit broadcast is always a background start).
              PASS: every start crashed with an am_crash, the system logged "crashed too many times" and marked the
              process bad, and no start followed the bad mark.
A build without P1.5 fails both (no am_crash, no dropbox entry, one start per broadcast); --label before-fix records it.

Report: <report-dir>/roundtrip-<model or AVD>-<versionCode>-<yyyyMMdd>-<label>.md (label crash-handover by default), one
section per run, counts and first exception lines only (ROADMAP R4).
"""
import argparse
import datetime
from pathlib import Path
import re
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent))

from roundtrip import (ROOT, START_ACTION, START_RECEIVER, App, Device, RoundTripError, quote, section,  # noqa: E402
                       summarize_events, types_text)

STARTUP_FLAG = "files/db-downgrade-crash-startup"
FOREGROUND_FLAG = "files/db-downgrade-crash-foreground"
REPORT_ACTIVITY = "org.autojs.autojs.ui.error.CrashReportActivity"
REPORT_PROCESS_SUFFIX = ":crash_report"
DROPBOX_HEAD = re.compile(r"^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d) data_app_crash", re.M)
START_EVENT = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+).*?\sam_proc_start\s*:\s*\[(.*)\]")
BAD_EVENT = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+).*?\sam_proc_bad\s*:\s*\[(.*)\]")


class Check:

    def __init__(self, args):
        self.args = args
        self.dev = Device(args.serial)
        self.items = []

    def item(self, name, ok, values):
        self.items.append((name, ok, values))
        mark = "PASS" if ok is True else ("FAIL" if ok is False else "INFO")
        print(f"  [{mark}] {name}: {values}")
        return ok

    def report(self, command, package, meta):
        app = App(self.dev, package)
        version_code, version_name = app.version() if app.installed() else (0, "?")
        folder = Path(self.args.report_dir)
        if not folder.is_absolute():
            folder = ROOT / folder
        folder.mkdir(parents=True, exist_ok=True)
        date = datetime.date.today().strftime("%Y%m%d")
        device = self.dev.label()
        path = folder / f"roundtrip-{device}-{version_code}-{date}-{self.args.label}.md"
        decided = [ok for _, ok, _ in self.items if ok is not None]
        verdict = ("PASS" if all(decided) else "FAIL") if decided else "INFO"
        lines = []
        if not path.exists():
            lines += [f"# 崩溃移交: {device} / {version_name} ({version_code})", "",
                      f"- 设备: {device} ({self.dev.serial}), Android {self.dev.prop('ro.build.version.release')} / API {self.dev.sdk}",
                      "- 脚本: `tools/db-downgrade/crash_handover.py` (ROADMAP P1.5, D10); 构建带临时崩溃注入 (README, 不提交)",
                      "- 每节一次运行, 只记通过 / 失败与关键数值 (R4)", ""]
        lines += [f"## {datetime.datetime.now().strftime('%H:%M:%S')} {command} {package} {verdict}", ""]
        lines += [f"- {text}" for text in meta] + ([""] if meta else [])
        lines += ["| 项 | 结果 | 数值 |", "| --- | --- | --- |"]
        for name, ok, values in self.items:
            mark = "PASS" if ok is True else ("FAIL" if ok is False else "-")
            lines.append(f"| {name} | {mark} | {str(values).replace('|', '/')} |")
        lines.append("")
        with path.open("a", encoding="utf-8", newline="\n") as stream:
            stream.write("\n".join(lines) + "\n")
        print(f"{command} {package}: {verdict} -> {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path}")
        self.items = []
        return verdict == "PASS"


def prepare(app, flag):
    """Leaves exactly [flag] (or no flag) in the files directory of [app], then force-stops it.
    The flag comes first, so a process the system starts right after the force-stop already sees it."""
    if not app.installed():
        raise RoundTripError(f"{app.package} is not installed")
    if not app.debuggable():
        raise RoundTripError(f"{app.package} is not debuggable (run-as failed)")
    commands = ["mkdir -p files", f"rm -f {STARTUP_FLAG} {FOREGROUND_FLAG}"] + ([f"touch {flag}"] if flag else [])
    code, _, err = app.run_as(" && ".join(commands))
    if code != 0:
        raise RoundTripError(f"Unable to set the injection flag: {err.strip()[:200]}")
    app.stop()


def wall_time(dev):
    return dev.shell("date +'%Y-%m-%d %H:%M:%S'").strip()


def dropbox_entries(dev, package, since):
    """(count, first root cause) of the data_app_crash entries of [package] written at or after [since] (device wall time)."""
    out = dev.adb("shell", "dumpsys dropbox --print data_app_crash", timeout=180)[1]
    count, cause = 0, ""
    for entry in re.split(r"\n(?=\d{4}-\d\d-\d\d \d\d:\d\d:\d\d data_app_crash)", out):
        head = DROPBOX_HEAD.match(entry.strip())
        if not head or head.group(1) < since or not re.search(rf"^Process: {re.escape(package)}\s*$", entry, re.M):
            continue
        count += 1
        if not cause:
            causes = re.findall(r"^(?:Caused by: )?([\w.$]+(?:Exception|Error)[^\r\n]*)$", entry, re.M)
            cause = causes[-1][:160] if causes else "?"
    return count, cause


def bad_processes(dev, package):
    """"Bad process <name> uid <uid>: ..." lines of `dumpsys activity processes` for the main process of [package]."""
    return [line.strip() for line in section(dev.shell("dumpsys activity processes", timeout=60), "Bad processes:")
            if (match := re.match(r"^\s+Bad process (\S+) uid", line)) and match.group(1) == package]


def report_resumed(dev):
    out = dev.shell("dumpsys activity activities", timeout=60)
    return any(REPORT_ACTIVITY.rsplit(".", 1)[-1] in line for line in out.splitlines()
               if re.search(r"(topResumedActivity|mResumedActivity|ResumedActivity:)", line))


def error_dialog(dev, package):
    """Whether the system's crash dialog ("Application Error: <package>", shown for a repeated crash) is up."""
    return f"Application Error: {package}" in dev.shell("dumpsys window windows", timeout=60)


def logged_crashes(dev, package, since, pids):
    """(pids, first exception line) of the uncaught exceptions logged by the package's processes [pids] (CrashHandler or AndroidRuntime)."""
    out = dev.adb("shell", f"logcat -d -v brief -s CrashHandler:E AndroidRuntime:E -T {quote(since)}", timeout=90)[1]
    crashed, first = set(), ""
    lines = out.splitlines()
    for index, line in enumerate(lines):
        match = re.match(r"^[EF]/(CrashHandler|AndroidRuntime)\(\s*(\d+)\): (.*)$", line)
        if not match or int(match.group(2)) not in pids or not match.group(3).startswith(("Uncaught Exception", "FATAL EXCEPTION")):
            continue
        crashed.add(int(match.group(2)))
        if not first:
            follow = [re.sub(r"^[EF]/\w+\(\s*\d+\): ", "", text) for text in lines[index + 1:index + 6]]
            first = next((text for text in follow if re.match(r"^[\w.$]+(Exception|Error)", text)), "")[:160]
    return crashed, first


def system_lines(dev, package, since):
    """(crashed too many times, background starts refused as bad) lines of ActivityManager / BroadcastQueue."""
    out = dev.adb("shell", f"logcat -d -b main,system -v brief -T {quote(since)}", timeout=120)[1]
    too_many, refused = [], []
    for line in out.splitlines():
        if package not in line:
            continue
        if "crashed too many times" in line or "crashed too quickly" in line:
            too_many.append(line)
        elif re.search(r"process is bad|bad process|Bad process", line):
            refused.append(line)
    return too_many, refused


def send_start_broadcast(dev, package, wait):
    """Sends the start broadcast of roundtrip.py --start broadcast and waits at most [wait] seconds for it to finish.
    Returns whether it finished: before P1.5 a crashing receiver process makes the system deliver it again and again.
    zh-CN: 发送 roundtrip.py --start broadcast 同款广播, 最多等 [wait] 秒. 返回是否完成: P1.5 之前接收进程崩溃后, 系统会一再重投."""
    try:
        dev.adb("shell", f"am broadcast -f 0x20 -n {package}/{START_RECEIVER} -a {START_ACTION}", timeout=max(wait, 1.0))
        return True
    except subprocess.TimeoutExpired:
        return False


def first_message(lines):
    return re.sub(r"^[VDIWEF]/[\w.]+\(\s*\d+\): ", "", lines[0])[:200] if lines else "-"


def foreground(check, package):
    dev = check.dev
    app = App(dev, package, "launcher")
    # From before the force-stop: the system may restart the app right after it (JobRescheduleService), and the
    # launcher start then reuses that process.
    # zh-CN: 从强停之前开始计: 系统可能在强停后立即重新拉起应用 (JobRescheduleService), 桌面入口随后复用该进程.
    since, wall = dev.now(), wall_time(dev)
    prepare(app, FOREGROUND_FLAG)
    time.sleep(1)
    app.start()
    deadline = time.time() + check.args.observe
    resumed_at = None
    while time.time() < deadline:
        if resumed_at is None and report_resumed(dev):
            resumed_at = check.args.observe - (deadline - time.time())
        time.sleep(1)
    main_pids = dev.pids(package)
    report_pids = dev.pids(package + REPORT_PROCESS_SUFFIX)
    summary = summarize_events(dev.events(since), package)
    crashed, first = logged_crashes(dev, package, since, summary["pids"])
    crashes = len(crashed)
    # A job or a service may start a new main process after the crash; only the crashed one must be gone.
    # For a repeated crash the system shows its crash dialog and kills the process when the dialog closes,
    # so wait for the dialog's own timeout instead of touching it.
    # zh-CN: 崩溃之后 job 或服务可能拉起新的主进程; 只要求崩溃的那个进程已结束. 重复崩溃时系统显示崩溃对话框,
    # 对话框关闭时才结束进程, 因此等对话框自行超时, 不去点它.
    survivors = crashed & set(main_pids)
    dialog = bool(survivors) and error_dialog(dev, package)
    waited = 0.0
    if dialog:
        limit = time.time() + check.args.dialog_wait
        while survivors and time.time() < limit:
            time.sleep(5)
            survivors = crashed & set(dev.pids(package))
        waited = check.args.dialog_wait - max(0.0, limit - time.time())
    entries, cause = dropbox_entries(dev, package, wall)
    prepare(app, None)
    check.item("crash logged", crashes == 1, f"{crashes} process(es)" + (f"; {first}" if first else ""))
    check.item("crash report page", resumed_at is not None,
               (f"CrashReportActivity resumed after {resumed_at:.0f} s" if resumed_at is not None else "not resumed")
               + f", {REPORT_PROCESS_SUFFIX} process {'running' if report_pids else 'not running'} at the end")
    check.item("am_crash", summary["crashes"] >= 1 and summary["crashes"] == crashes,
               f"{summary['crashes']}" + (f"; {summary['exception']}" if summary["exception"] else ""))
    check.item("dropbox data_app_crash", entries >= 1, f"{entries} new entr{'y' if entries == 1 else 'ies'}" + (f"; {cause}" if cause else ""))
    check.item("crashed process", crashes >= 1 and not survivors,
               ("ended" if not survivors else "still running")
               + (f" (system crash dialog for a repeated crash, ended {waited:.0f} s later when the dialog timed out)" if dialog and not survivors
                  else " (system crash dialog still up)" if dialog else "")
               + f"; main processes at the end {len(main_pids)} (started after the crash)")
    check.item("process starts", None, f"am_proc_start {summary['starts']} ({types_text(summary['startTypes'])})")
    return check.report("foreground", package, [f"桌面入口启动 ({app.launcher() or 'monkey'}), 首个 Activity 恢复后 3 s 主线程抛出 (注入), 观察 {check.args.observe:.0f} s"])


def background(check, package):
    dev = check.dev
    app = App(dev, package, "broadcast")
    prepare(app, None)
    cleared = ""
    if bad_processes(dev, package):
        dev.shell(f"content query --uri content://{package}.shizuku/db-downgrade-clear", timeout=60)
        time.sleep(5)
        app.stop()
        cleared = "; cleared by a provider start" if not bad_processes(dev, package) else "; still bad after a provider start"
    bad_before = bad_processes(dev, package)
    check.item("bad mark before", not bad_before, ("none" if not bad_before else "; ".join(bad_before)[:200]) + cleared)
    since = dev.now()
    prepare(app, STARTUP_FLAG)
    time.sleep(1)
    broadcasts = unfinished = 0
    deadline = time.time() + check.args.observe
    while time.time() < deadline:
        sent = time.time()
        if not send_start_broadcast(dev, package, check.args.interval):
            unfinished += 1
        broadcasts += 1
        time.sleep(max(0.0, min(sent + check.args.interval, deadline) - time.time()))
    time.sleep(5)
    events = dev.events(since)
    summary = summarize_events(events, package)
    # am_proc_start [user, pid, uid, process, hosting type, component]; am_proc_bad [user, uid, process].
    # zh-CN: am_proc_start [用户, pid, uid, 进程, 拉起类型, 组件]; am_proc_bad [用户, uid, 进程].
    starts = [match.group(1) for line in events if (match := START_EVENT.match(line)) and match.group(2).split(",")[3:4] == [package]]
    marks = [match.group(1) for line in events if (match := BAD_EVENT.match(line)) and match.group(2).split(",")[2:3] == [package]]
    after_mark = [when for when in starts if marks and when > marks[0]]
    crashed, first = logged_crashes(dev, package, since, summary["pids"])
    crashes = len(crashed)
    too_many, refused = system_lines(dev, package, since)
    bad_after = bad_processes(dev, package)
    prepare(app, None)
    check.item("crashes", crashes == summary["starts"] and summary["starts"] >= 1, f"{crashes} of {summary['starts']} start(s) crashed" + (f"; {first}" if first else ""))
    check.item("am_crash", summary["crashes"] == crashes and crashes >= 1, f"{summary['crashes']}")
    check.item("crashed too many times", bool(too_many) or summary["tooMuch"] >= 1,
               f"am_process_crashed_too_much {summary['tooMuch']}, log {len(too_many)}; {first_message(too_many)}")
    check.item("bad process", bool(marks) or bool(bad_after),
               f"am_proc_bad {len(marks)}" + (f" (at {marks[0][6:]})" if marks else "") + f", listed at the end {'yes' if bad_after else 'no'}")
    # Background broadcasts no longer start a bad process (API 35 skips them without a log line).
    # zh-CN: 后台广播不再拉起 bad 进程 (API 35 跳过时不写日志).
    check.item("starts stopped", bool(marks) and not after_mark,
               f"am_proc_start {len(starts)} for {broadcasts} broadcasts ({types_text(summary['startTypes'])}), "
               f"{len(after_mark)} after the bad mark" + (f", last at {starts[-1][6:]}" if starts else "")
               + f"; broadcasts unfinished after {check.args.interval:.0f} s {unfinished}; refusal lines {len(refused)}: {first_message(refused)}")
    return check.report("background", package, [f"每 {check.args.interval:.0f} s 发一次显式广播 (roundtrip.py --start broadcast 同款), 共 {check.args.observe:.0f} s; App.onCreate 抛出 (注入)"])


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0], formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("foreground", "background"))
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--package", action="append", help="org.autojs.autojs6 (default) and / or org.autojs.autojs6.inrt")
    parser.add_argument("--report-dir", default="docs/db-downgrade/evidence", help="relative paths start at the repository root")
    parser.add_argument("--label", default="crash-handover", help="report name suffix (default crash-handover)")
    parser.add_argument("--observe", type=float, help="seconds to watch (default 30 for foreground, 60 for background)")
    parser.add_argument("--interval", type=float, default=5.0, help="background: seconds between broadcasts (default 5)")
    parser.add_argument("--dialog-wait", type=float, default=330.0,
                        help="foreground: seconds to wait for the system crash dialog of a repeated crash to time out (default 330)")
    args = parser.parse_args()
    if args.observe is None:
        args.observe = 30.0 if args.command == "foreground" else 60.0
    check = Check(args)
    run = foreground if args.command == "foreground" else background
    try:
        results = [run(check, package) for package in (args.package or ["org.autojs.autojs6"])]
    except (RoundTripError, subprocess.TimeoutExpired) as error:
        print(f"ERROR: {error}")
        return 2
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
