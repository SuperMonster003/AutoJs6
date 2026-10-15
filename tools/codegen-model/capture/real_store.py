"""Resumable, explicitly confirmed real-app collection; no model or network dependency."""
from __future__ import annotations

from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import uuid

from dataset.task_io import (ACTIONS, ATTRIBUTES, SCOPES, load_group, nonempty_string,
                             parse_json, private_path, read_json, resolve_target,
                             snapshot_path, write_json)
from label.label_real import flatten, recommend

PACKAGE = re.compile(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+\Z")
IDENTITY = re.compile(r"real-[a-f0-9]{32}\Z")
MAX_SNAPSHOT_BYTES = 32 * 1024 * 1024


def now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def private_write_json(path, value):
    path = private_path(path)
    private_path(path.with_name(path.name + ".tmp"))
    write_json(path, value)


def tree_digest(root):
    # Ignore only envelope metadata (e.g. capturedAt), never hide actual node differences.
    return digest(json.dumps(root, ensure_ascii=False, sort_keys=True,
                             separators=(",", ":"), allow_nan=False).encode("utf-8"))


def inspect_snapshot(data, app):
    if len(data) > MAX_SNAPSHOT_BYTES:
        raise ValueError("布局文件超过 32 MiB, 请缩小采集页面范围。")
    value = parse_json(data.decode("utf-8-sig"))
    if not isinstance(value, dict) or not isinstance(value.get("root"), dict):
        raise ValueError("文件缺少布局根节点, 请导入 AutoJs6 的 .layout.json 文件。")
    root = value["root"]
    if root.get("packageName") != app:
        raise ValueError("布局所属应用与任务包名不同, 请返回目标应用后重试。")
    nodes = flatten(root)
    if len(nodes) < 2:
        raise ValueError("布局没有可供标注的子节点, 请检查无障碍服务和当前页面。")
    return root, nodes


class WorkspaceLock:
    """OS-owned lock: another collector cannot overwrite drafts; crashes release it."""

    def __init__(self, root):
        path = private_path(Path(root) / ".collector.lock")
        path.parent.mkdir(parents=True, exist_ok=True)
        self.file = path.open("a+b")
        try:
            if path.stat().st_size == 0:
                self.file.write(b"0")
                self.file.flush()
            self.file.seek(0)
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(self.file.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.file, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            self.file.close()
            raise ValueError("此私有目录已由另一个采集助手打开, 请先关闭另一个窗口。") from error

    def close(self):
        self.file.close()


class CollectionStore:
    def __init__(self, root):
        self.root = private_path(root)
        self.sessions = private_path(self.root / "collector" / "groups")
        self.sessions.mkdir(parents=True, exist_ok=True)

    def folder(self, group_id):
        if not isinstance(group_id, str) or not IDENTITY.fullmatch(group_id):
            raise ValueError("无效的采集任务编号。")
        folder = private_path(self.sessions / group_id)
        if folder.parent != self.sessions:
            raise ValueError("任务目录不能链接到其他位置。")
        return folder

    def create(self, *, app, title, scope="single", action="none", receiver="auto", attributes=ATTRIBUTES):
        if not isinstance(app, str) or not PACKAGE.fullmatch(app):
            raise ValueError("请输入完整应用包名, 例如 com.android.settings。")
        nonempty_string(title, "任务名称")
        if scope not in SCOPES or action not in ACTIONS or receiver not in ("auto", "self"):
            raise ValueError("无效的任务选项。")
        if len(set(attributes)) != len(attributes) or any(a not in ATTRIBUTES for a in attributes):
            raise ValueError("无效的选择器属性。")
        group_id = "real-" + uuid.uuid4().hex
        session = {"format": 1, "groupId": group_id, "app": app, "title": title,
                   "createdAt": now(), "updatedAt": now(),
                   "request": {"scope": scope, "action": action, "receiver": receiver,
                               "attributes": list(attributes)}, "states": []}
        folder = self.folder(group_id)
        folder.mkdir(exist_ok=False)
        self.save(session)
        return session

    def save(self, session):
        folder = self.folder(session["groupId"])
        if (folder / "task.cgtask.json").exists():
            raise ValueError("此任务已导出并冻结。需要新状态时请新建任务。")
        session["updatedAt"] = now()
        private_write_json(folder / "session.json", session)

    def load(self, group_id):
        folder = self.folder(group_id)
        session = read_json(private_path(folder / "session.json"))
        if (not isinstance(session, dict) or session.get("format") != 1
                or session.get("groupId") != group_id or not isinstance(session.get("states"), list)):
            raise ValueError("采集草稿格式无效。")
        if not PACKAGE.fullmatch(session.get("app", "")):
            raise ValueError("草稿中的应用包名无效。")
        for state in session["states"]:
            private_path(snapshot_path(folder / "session.json", state["snapshot"]))
        return session

    def list_sessions(self):
        result, errors = [], []
        for path in sorted(self.sessions.glob("*/session.json")):
            try:
                result.append(self.load(path.parent.name))
            except (ValueError, OSError, KeyError, TypeError) as error:
                errors.append(f"{path.parent.name}: {error}")
        return sorted(result, key=lambda s: s["createdAt"], reverse=True), errors

    def read_state(self, session, index):
        state = session["states"][index]
        path = private_path(snapshot_path(self.folder(session["groupId"]) / "session.json", state["snapshot"]))
        data = path.read_bytes()
        if digest(data) != state["sha256"]:
            raise ValueError("布局文件在采集后被修改, 请重新采集该状态。")
        root, nodes = inspect_snapshot(data, session["app"])
        if tree_digest(root) != state["treeSha256"]:
            raise ValueError("布局树校验失败。")
        return root, nodes

    def add_snapshot(self, group_id, data, *, provenance=None):
        session = self.load(group_id)
        if self.export_path(group_id).exists():
            raise ValueError("已导出的任务不能再加入状态。")
        root, _ = inspect_snapshot(data, session["app"])
        fingerprint = tree_digest(root)
        if any(s["treeSha256"] == fingerprint and not s["excluded"] for s in session["states"]):
            raise ValueError("布局与已有状态完全相同。请先改变列表顺序、内容、语言或页面结构。")
        state_id = f"s{len(session['states'])}"
        relative = f"snapshots/{state_id}-{uuid.uuid4().hex[:12]}.layout.json"
        destination = private_path(self.folder(group_id) / relative)
        destination.parent.mkdir(parents=True, exist_ok=True)
        # A crash can leave an unreferenced file, but never change an existing state's bytes.
        with destination.open("xb") as stream:
            stream.write(data)
        session["states"].append({"stateId": state_id, "snapshot": relative, "sha256": digest(data),
                                  "treeSha256": fingerprint, "capturedAt": now(),
                                  "provenance": deepcopy(provenance or {}), "targets": None,
                                  "confirmedAt": None, "note": "", "excluded": False})
        self.save(session)
        return session

    def import_snapshot(self, group_id, source):
        source = private_path(source)
        if not source.name.endswith(".layout.json"):
            raise ValueError("请选择 .layout.json 文件。")
        if source.stat().st_size > MAX_SNAPSHOT_BYTES:
            raise ValueError("布局文件超过 32 MiB。")
        return self.add_snapshot(group_id, source.read_bytes(), provenance={"kind": "import"})

    def proposal(self, session, index):
        _, nodes = self.read_state(session, index)
        for previous_index in range(index - 1, -1, -1):
            state = session["states"][previous_index]
            if state["confirmedAt"] and not state["excluded"]:
                _, previous_nodes = self.read_state(session, previous_index)
                paths = {tuple(p) for p in state["targets"]}
                return recommend([n for n in previous_nodes if n.path in paths], nodes)
        return recommend([], nodes)

    def confirm(self, group_id, index, indices, note):
        session = self.load(group_id)
        state = session["states"][index]
        if state["excluded"]:
            raise ValueError("请先恢复此状态。")
        root, nodes = self.read_state(session, index)
        if (not indices or any(type(i) is not int or not 0 <= i < len(nodes) for i in indices)
                or len(set(indices)) != len(indices)):
            raise ValueError("请选择有效且不重复的目标节点。")
        count = len(indices)
        if session["request"]["scope"] == "single" and count != 1:
            raise ValueError("单个目标任务只能选择一个节点。")
        others = [s for i, s in enumerate(session["states"])
                  if i != index and s["confirmedAt"] and not s["excluded"]]
        if any(len(s["targets"]) != count for s in others):
            raise ValueError("每个状态的目标数量必须一致。目标消失或集合数量变化不适用当前任务格式。")
        note = note.strip()
        first = next(i for i, s in enumerate(session["states"]) if not s["excluded"])
        if index != first or note:
            nonempty_string(note, "变化说明")
        state.update(targets=[list(nodes[i].path) for i in sorted(indices)],
                     confirmedAt=now(), note=note)
        for target in state["targets"]:
            resolve_target(root, target)
        self.save(session)
        return session

    def exclude(self, group_id, index, excluded=True):
        session = self.load(group_id)
        # Restoring requires a fresh explicit confirmation, avoiding stale labels/counts.
        session["states"][index].update(excluded=excluded, targets=None, confirmedAt=None)
        self.save(session)
        return session

    def export_path(self, group_id):
        return private_path(self.folder(group_id) / "task.cgtask.json")

    def build_group(self, session):
        states, fingerprints = [], set()
        for index, state in enumerate(session["states"]):
            if state["excluded"]:
                continue
            if not state["confirmedAt"] or not state["targets"]:
                raise ValueError("请逐个确认所有保留状态的目标, 或排除不需要的状态。")
            self.read_state(session, index)
            if state["treeSha256"] in fingerprints:
                raise ValueError("存在重复布局, 请排除重复状态后再导出。")
            fingerprints.add(state["treeSha256"])
            if states and not state["note"].strip():
                raise ValueError("请为后续状态填写变化说明。")
            states.append({"stateId": state["stateId"], "snapshot": state["snapshot"],
                           "mutations": [state["note"]] if state["note"] else [],
                           "targets": state["targets"]})
        if len(states) < 3:
            raise ValueError("至少需要 3 个不同布局且已确认目标的状态, 才能计入本轮采集。")
        return {"format": 1, "groupId": session["groupId"], "source": "real", "app": session["app"],
                "screenFamily": session["title"], "captureState": 0,
                "request": session["request"], "states": states}

    def export(self, group_id):
        session = self.load(group_id)
        group = self.build_group(session)
        destination = self.export_path(group_id)
        if destination.exists():
            load_group(destination)
            if read_json(destination) != group:
                raise ValueError("已导出文件与草稿不一致, 请检查文件是否被修改。")
            return destination
        pending = private_path(destination.with_name("export.pending.json"))
        private_write_json(pending, group)
        load_group(pending)
        pending.replace(destination)
        return destination

    def report(self):
        """Count validated exports, including existing label_real groups; reject duplicates."""
        counts, errors, short, seen_ids, seen_tasks = Counter(), [], 0, set(), set()
        for path in sorted(self.root.rglob("*.cgtask.json")):
            try:
                group, snapshots = load_group(private_path(path))
                if group["source"] != "real":
                    continue
                if path.parent.parent == self.sessions:
                    session = self.load(path.parent.name)
                    if self.build_group(session) != group:
                        raise ValueError("导出任务与确认记录不一致")
                if group["groupId"] in seen_ids:
                    raise ValueError("重复 groupId, 未重复计数")
                seen_ids.add(group["groupId"])
                trees = [tree_digest(inspect_snapshot(p.read_bytes(), group["app"])[0]) for p in snapshots]
                if len(trees) < 3:
                    short += 1
                    continue
                if len(set(trees)) != len(trees):
                    raise ValueError("包含重复布局状态, 未计入完成数")
                # Renaming/copying a task cannot inflate the Q8 totals.
                signature = json.dumps([group["app"], {**group["request"], "receiver": group["request"].get("receiver", "auto")},
                                        sorted((tree, sorted(s["targets"])) for tree, s in zip(trees, group["states"]))],
                                       sort_keys=True, ensure_ascii=False)
                if signature in seen_tasks:
                    raise ValueError("重复任务内容, 未重复计数")
                seen_tasks.add(signature)
                counts[group["app"]] += 1
            except (ValueError, OSError, KeyError, TypeError, RecursionError) as error:
                errors.append({"file": path.relative_to(self.root).as_posix(), "error": str(error)})
        sessions, draft_errors = self.list_sessions()
        report = {"format": 1, "generatedAt": now(), "apps": len(counts),
                  "groups": sum(counts.values()), "appsWithFiveGroups": sum(n >= 5 for n in counts.values()),
                  "goal": {"apps": 30, "groups": 150, "groupsPerApp": 5, "statesPerGroup": 3},
                  "complete": len(counts) >= 30 and sum(n >= 5 for n in counts.values()) >= 30
                              and sum(counts.values()) >= 150 and not errors and not draft_errors,
                  "perApp": dict(sorted(counts.items())), "shortGroups": short,
                  "drafts": sum(not self.export_path(s["groupId"]).exists() for s in sessions),
                  "errors": errors, "draftErrors": draft_errors}
        private_write_json(self.root / "collector" / "progress.json", report)
        return report
