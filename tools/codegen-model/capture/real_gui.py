"""Chinese desktop assistant for the P2.5/Q8 real-app collection workflow."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import queue
import sys
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from capture.real_device import PrivateAdb, RealCaptureWorker, find_adb, list_devices
from capture.real_store import CollectionStore, WorkspaceLock, inspect_snapshot
from dataset.task_io import resolve_target

SCOPES = {"单个目标": "single", "目标集合 (Ctrl 多选)": "collection"}
ACTIONS = {"仅定位": "none", "点击": "click", "长按": "long-click", "输入文字": "set-text",
           "向前滚动": "scroll-forward", "向后滚动": "scroll-backward"}
RECEIVERS = {"自动选择支持动作的父级": "auto", "直接使用选中节点": "self"}


class CollectorApp:
    def __init__(self, window, store, *, adb=None, serial=None, lock=None, auto_refresh=True):
        self.window, self.store, self.lock = window, store, lock
        self.adb_option = adb
        preferences = store.preferences()
        self.preferred_serial = serial or preferences.get("serial")
        self.worker = None
        self.busy = False
        self.closing = False
        self.close_error = None
        self.events = queue.Queue()
        self.poll_id = None
        self.controls = []
        self.devices = {}
        self.session = None
        self.state_index = None
        self.preview_return = None
        self.note_dirty = False
        self.loading_note = False
        self.query_dirty = False
        self.loading_query = False
        self.highlight_index = None
        self.nodes, self.raw_nodes, self.selected, self.visible = [], {}, set(), set()
        self.group_ids = []
        self.canvas_transform = None
        self.report_data = None
        style = ttk.Style(window)
        style.theme_use("clam")
        style.configure(".", font=("Microsoft YaHei UI", 10), background="#f3f5f8", foreground="#243244")
        style.configure("TButton", padding=(8, 5))
        style.map("TButton", foreground=[("disabled", "#8994a3"), ("!disabled", "#243244")])
        style.configure("Treeview", background="white", fieldbackground="white", rowheight=26)
        style.configure("Treeview.Heading", background="#e6ebf2", foreground="#243244")
        style.map("Treeview.Heading", foreground=[("disabled", "#8994a3"), ("!disabled", "#243244")])
        style.map("Treeview", background=[("selected", "#2166a5")], foreground=[("selected", "white")])
        window.option_add("*Font", ("Microsoft YaHei UI", 10))
        window.title("AutoJs6 · 真实应用数据采集助手")
        window.geometry("1360x880")
        window.minsize(1040, 700)
        window.protocol("WM_DELETE_WINDOW", self.close)
        window.bind("<Destroy>", self.on_destroy, add="+")
        self.status = tk.StringVar(value="先选择设备, 新建任务, 然后在手机打开需要采集的页面。")
        self.progress = tk.StringVar(value="正在检查已有采集数据...")
        self.device = tk.StringVar()
        self.query = tk.StringVar()
        self.query.trace_add("write", self.query_changed)
        self.note = tk.StringVar()
        self.note.trace_add("write", self.note_changed)
        self.selection_text = tk.StringVar(value="尚未选择目标")
        self.state_text = tk.StringVar(value="尚未打开状态")
        self._build()
        self.refresh_sessions()
        self.sync_controls()
        self.schedule_poll()
        if auto_refresh:
            self.refresh_devices()

    def button(self, parent, text, callback, **pack):
        widget = ttk.Button(parent, text=text, command=lambda: self.guard(callback))
        widget.pack(**pack)
        self.controls.append(widget)
        return widget

    def _build(self):
        outer = ttk.Frame(self.window, padding=12)
        outer.pack(fill="both", expand=True)
        ttk.Label(outer, text="真实应用数据采集", font=("Microsoft YaHei UI", 17, "bold")).pack(anchor="w")
        ttk.Label(outer, text="手机切换页面状态 → 采集布局 → 核对目标并确认 → 至少 3 个状态后导出").pack(anchor="w", pady=(4, 8))
        row = ttk.Frame(outer)
        row.pack(fill="x")
        ttk.Label(row, text="设备: ").pack(side="left")
        self.device_box = ttk.Combobox(row, textvariable=self.device, state="readonly", width=45)
        self.device_box.pack(side="left")
        self.device_box.bind("<<ComboboxSelected>>", lambda _: self.guard(self.remember_device))
        self.controls.append(self.device_box)
        self.button(row, "刷新设备", self.refresh_devices, side="left", padx=6)
        self.button(row, "新建任务", self.new_task, side="left")
        self.button(row, "检查采集进度", self.refresh_report, side="right")
        self.button(row, "打开数据目录", self.open_folder, side="right", padx=6)
        ttk.Label(outer, text="私有目录: " + str(self.store.root)).pack(anchor="w", pady=(7, 2))
        ttk.Label(outer, textvariable=self.progress, foreground="#276b47").pack(anchor="w", pady=(0, 8))
        # Reserve the status area before the expanding inspector, also at smaller window sizes.
        ttk.Label(outer, textvariable=self.status, wraplength=1250).pack(side="bottom", anchor="w")
        ttk.Separator(outer).pack(side="bottom", fill="x", pady=(10, 6))
        panes = ttk.Panedwindow(outer, orient="horizontal")
        panes.pack(fill="both", expand=True)
        left = ttk.Frame(panes, width=245)
        panes.add(left, weight=1)
        left.columnconfigure(0, weight=1)
        left.rowconfigure(1, weight=1)
        group_heading = ttk.Frame(left)
        group_heading.grid(row=0, column=0, sticky="ew")
        ttk.Label(group_heading, text="任务 (草稿自动保存)").pack(side="left")
        self.delete_draft_button = self.button(group_heading, "删除草稿", self.delete_draft, side="right")
        group_frame = ttk.Frame(left)
        group_frame.grid(row=1, column=0, sticky="nsew")
        self.groups = tk.Listbox(group_frame, exportselection=False, width=28, height=11)
        self.groups.pack(side="left", fill="both", expand=True)
        scroll = ttk.Scrollbar(group_frame, command=self.groups.yview)
        scroll.pack(side="right", fill="y")
        self.groups.configure(yscrollcommand=scroll.set)
        self.groups.bind("<<ListboxSelect>>", self.select_group)
        ttk.Label(left, text="状态: ○ 待确认 / ✓ 已确认 / × 排除").grid(row=2, column=0, sticky="w", pady=(12, 3))
        self.states = tk.Listbox(left, exportselection=False, height=6)
        self.states.grid(row=3, column=0, sticky="ew")
        self.states.bind("<<ListboxSelect>>", self.select_state)
        self.controls.extend([self.groups, self.states])
        exclude_bar = ttk.Frame(left)
        exclude_bar.grid(row=4, column=0, sticky="ew", pady=6)
        self.exclude_button = self.button(exclude_bar, "排除 / 恢复", self.toggle_excluded, side="left", fill="x", expand=True)
        self.delete_state_button = self.button(exclude_bar, "删除状态", self.delete_state, side="right", padx=(6, 0))
        self.task_info = ttk.Label(left, text="单个任务跟踪同一目标或同一组目标。", wraplength=240)
        self.task_info.grid(row=5, column=0, sticky="w", pady=5)
        main = ttk.Frame(panes, padding=(12, 0, 0, 0))
        panes.add(main, weight=5)
        main.columnconfigure(0, weight=1)
        main.rowconfigure(3, weight=1)
        actions = ttk.Frame(main)
        actions.grid(row=0, column=0, sticky="ew")
        task_actions = ttk.Frame(actions)
        task_actions.pack(fill="x")
        self.capture_button = self.button(task_actions, "采集当前手机布局", self.capture, side="left")
        self.import_button = self.button(task_actions, "导入已有布局", self.import_layout, side="left", padx=6)
        self.export_button = self.button(task_actions, "完成并导出任务", self.export_task, side="right")
        view_actions = ttk.Frame(actions)
        view_actions.pack(fill="x", pady=(5, 0))
        self.preview_button = self.button(view_actions, "查看当前布局", self.preview_current, side="left")
        self.return_button = self.button(view_actions, "返回任务", self.return_to_task, side="left", padx=6)
        ttk.Label(view_actions, text="临时查看手机前台页面, 不关联任务").pack(side="left")
        ttk.Label(main, textvariable=self.state_text, wraplength=850).grid(row=1, column=0, sticky="w", pady=(8, 5))
        search = ttk.Frame(main)
        search.grid(row=2, column=0, sticky="ew")
        ttk.Label(search, text="搜索文字 / ID / 描述 / 类名 / 序号: ").pack(side="left")
        self.search_entry = ttk.Combobox(search, textvariable=self.query, values=self.store.search_history(), height=10)
        self.search_entry.pack(side="left", fill="x", expand=True)
        self.controls.append(self.search_entry)
        self.search_entry.bind("<Return>", lambda _: self.guard(self.apply_search))
        self.search_entry.bind("<<ComboboxSelected>>", lambda _: self.guard(self.apply_search))
        self.search_entry.bind("<FocusOut>", lambda _: self.guard(self.remember_current_search))
        self.button(search, "搜索", self.apply_search, side="left", padx=4)
        self.button(search, "显示全部", self.clear_search, side="left")
        inspect = ttk.Panedwindow(main, orient="horizontal")
        inspect.grid(row=3, column=0, sticky="nsew", pady=7)
        table_frame = ttk.Frame(inspect)
        inspect.add(table_frame, weight=4)
        self.table = ttk.Treeview(table_frame, columns=("index", "class", "id", "text", "desc"),
                                  show="headings", selectmode="extended", height=13)
        for key, label, width in (("index", "序号", 48), ("class", "控件类型", 120),
                                   ("id", "ID", 150), ("text", "文字", 180), ("desc", "描述", 150)):
            self.table.heading(key, text=label)
            self.table.column(key, width=width, minwidth=40, stretch=key in ("id", "text", "desc"))
        self.table.grid(row=0, column=0, sticky="nsew")
        yscroll = ttk.Scrollbar(table_frame, command=self.table.yview)
        yscroll.grid(row=0, column=1, sticky="ns")
        xscroll = ttk.Scrollbar(table_frame, orient="horizontal", command=self.table.xview)
        xscroll.grid(row=1, column=0, sticky="ew")
        self.table.configure(yscrollcommand=yscroll.set, xscrollcommand=xscroll.set)
        table_frame.rowconfigure(0, weight=1)
        table_frame.columnconfigure(0, weight=1)
        self.table.bind("<<TreeviewSelect>>", self.node_selected)
        self.controls.append(self.table)
        preview = ttk.Frame(inspect, width=240)
        inspect.add(preview, weight=2)
        ttk.Label(preview, text="布局示意 (点击选中, Ctrl 多选)").pack(anchor="w")
        self.canvas = tk.Canvas(preview, background="#f5f7fa", highlightthickness=0, width=270)
        self.canvas.pack(fill="both", expand=True)
        self.canvas.bind("<Configure>", lambda _: self.draw_preview())
        self.canvas.bind("<Button-1>", self.canvas_selected)
        selection_bar = ttk.Frame(main)
        selection_bar.grid(row=4, column=0, sticky="ew")
        parent_button = self.button(selection_bar, "选父节点", self.select_parent, side="right")
        clear_button = self.button(selection_bar, "清空选择", self.clear_selection, side="right", padx=4)
        self.proposal_button = self.button(selection_bar, "重新推荐", self.use_proposal, side="right")
        self.next_highlight_button = self.button(selection_bar, "下一个高亮", lambda: self.navigate_highlight(1), side="right", padx=4)
        self.previous_highlight_button = self.button(selection_bar, "上一个高亮", lambda: self.navigate_highlight(-1), side="right")
        for button in (parent_button, clear_button, self.proposal_button,
                       self.next_highlight_button, self.previous_highlight_button):
            button.configure(width=0)
        ttk.Label(selection_bar, textvariable=self.selection_text, wraplength=280).pack(side="left", fill="x", expand=True)
        details_frame = ttk.Frame(main)
        details_frame.grid(row=5, column=0, sticky="ew", pady=6)
        self.details = tk.Text(details_frame, height=5, wrap="word", state="disabled", font=("Microsoft YaHei UI", 10))
        self.details.pack(side="left", fill="x", expand=True)
        details_scroll = ttk.Scrollbar(details_frame, command=self.details.yview)
        details_scroll.pack(side="right", fill="y")
        self.details.configure(yscrollcommand=details_scroll.set)
        row = ttk.Frame(main)
        row.grid(row=6, column=0, sticky="ew")
        ttk.Label(row, text="相对首个状态的变化: ").pack(side="left")
        self.note_entry = ttk.Combobox(row, textvariable=self.note, values=self.store.note_history(), height=10)
        self.note_entry.pack(side="left", fill="x", expand=True)
        self.note_entry.bind("<FocusOut>", lambda _: self.guard(self.remember_current_note))
        self.note_entry.bind("<Return>", lambda _: self.guard(self.remember_current_note))
        self.note_entry.bind("<<ComboboxSelected>>", lambda _: self.guard(self.remember_current_note))
        self.controls.append(self.note_entry)
        self.confirm_button = self.button(row, "确认这些目标", self.confirm, side="right", padx=(8, 0))
        ttk.Label(main, text="例: 上方一行被删除 / 目标前插入进度条 / 列表重排。请核对父级和相邻文字, 推荐可能选错。",
                  wraplength=850).grid(row=7, column=0, sticky="w", pady=(5, 0))

    def guard(self, callback):
        if self.busy or self.closing:
            return
        try:
            callback()
        except Exception as error:
            self.error(error)

    def error(self, error):
        self.status.set(str(error))
        messagebox.showerror("采集助手", str(error), parent=self.window)

    def run_job(self, work, done, message):
        if self.busy:
            return
        if not self.closing:
            self.remember_current_note()
            self.remember_current_search()
        self.busy = True
        self.status.set(message)
        for control in self.controls:
            if isinstance(control, ttk.Widget):
                control.state(["disabled"])
            else:
                control.configure(state="disabled")

        def execute():
            try:
                self.events.put((done, work(), None))
            except Exception as error:
                self.events.put((done, None, error))
        threading.Thread(target=execute, daemon=False).start()

    def _poll(self):
        self.poll_id = None
        try:
            done, value, error = self.events.get_nowait()
        except queue.Empty:
            pass
        else:
            self.busy = False
            for control in self.controls:
                if isinstance(control, ttk.Widget):
                    control.state(["!disabled"])
                else:
                    control.configure(state="normal")
            if self.closing:
                if error:
                    self.close_error = error
                self._finish_close()
                return
            if error:
                self.error(error)
            else:
                self.guard(lambda: done(value))
            self.sync_controls()
        self.schedule_poll()

    def schedule_poll(self):
        if self.poll_id is None:
            self.poll_id = self.window.after(80, self._poll)

    def on_destroy(self, event):
        if event.widget is self.window and self.poll_id is not None:
            self.window.after_cancel(self.poll_id)
            self.poll_id = None

    def sync_controls(self):
        task = self.session is not None and self.preview_return is None
        draft = task and not self.store.export_path(self.session["groupId"]).exists()
        state = task and self.state_index is not None
        included = state and not self.session["states"][self.state_index]["excluded"]
        for widget, enabled in ((self.capture_button, draft), (self.import_button, draft),
                                (self.export_button, task), (self.delete_draft_button, draft),
                                (self.delete_state_button, draft and state), (self.exclude_button, draft and state),
                                (self.proposal_button, draft and included), (self.confirm_button, draft and included),
                                (self.note_entry, draft and included), (self.return_button, self.preview_return is not None),
                                (self.previous_highlight_button, bool(self.selected & self.visible)),
                                (self.next_highlight_button, bool(self.selected & self.visible))):
            widget.state(["!disabled"] if enabled and not self.busy else ["disabled"])

    def note_changed(self, *_):
        if not self.loading_note and self.preview_return is None:
            self.note_dirty = True

    def set_note(self, value):
        self.loading_note = True
        try:
            self.note.set(value)
        finally:
            self.loading_note = False
            self.note_dirty = False

    def remember_current_note(self, force=False):
        if self.preview_return is None and (force or self.note_dirty):
            self.note_entry["values"] = self.store.remember_note(self.note.get())
            self.note_dirty = False

    def query_changed(self, *_):
        if not self.loading_query:
            self.query_dirty = True

    def set_query(self, value):
        self.loading_query = True
        try:
            self.query.set(value)
        finally:
            self.loading_query = False
            self.query_dirty = False

    def remember_current_search(self, force=False):
        if force or self.query_dirty:
            self.search_entry["values"] = self.store.remember_search(self.query.get())
            self.query_dirty = False

    def apply_search(self):
        self.remember_current_search(force=True)
        self.render_nodes()

    def executable(self):
        return find_adb(self.adb_option)

    def selected_adb(self):
        device = self.devices.get(self.device.get())
        if not device:
            raise ValueError("请先在设备下拉列表选择一台设备。")
        if device["status"] != "device":
            raise ValueError("设备未就绪。请解锁手机并允许 USB 调试, 然后刷新设备。")
        return PrivateAdb(self.executable(), device["serial"], self.store.root)

    def refresh_devices(self):
        def done(devices):
            self.devices = {f"{d['model']} · {d['serial']} · {d['status']}": d for d in devices}
            self.device_box["values"] = tuple(self.devices)
            if self.device.get() not in self.devices:
                preferred = next((label for label, d in self.devices.items() if d["serial"] == self.preferred_serial), "")
                self.device.set(preferred or (next(iter(self.devices)) if len(devices) == 1 else ""))
            self.status.set(f"检测到 {len(devices)} 台设备。选择一台后新建任务；导入已有布局无需连接设备。")
            if self.report_data is None:
                self.refresh_report(show=False)
        self.run_job(lambda: list_devices(self.executable()), done, "正在检查设备连接...")

    def remember_device(self):
        device = self.devices.get(self.device.get())
        if device:
            self.preferred_serial = device["serial"]
            self.store.update_preferences(serial=self.preferred_serial)

    def refresh_sessions(self, selected_id=None):
        sessions, errors = self.store.list_sessions()
        self.groups.delete(0, "end")
        self.group_ids = [s["groupId"] for s in sessions]
        for s in sessions:
            finished = self.store.export_path(s["groupId"]).exists()
            count = sum(bool(x["confirmedAt"]) and not x["excluded"] for x in s["states"])
            self.groups.insert("end", f"{'✓' if finished else '○'} {s['title']} ({count} 状态)")
        if selected_id in self.group_ids:
            index = self.group_ids.index(selected_id)
            self.groups.selection_set(index)
            self.groups.see(index)
        if errors:
            self.status.set("部分草稿无法读取, 请查看进度检查中的错误。")

    def select_group(self, _=None):
        if self.busy or not self.groups.curselection():
            return
        self.guard(lambda: self.open_session(self.group_ids[self.groups.curselection()[0]]))

    def open_session(self, group_id, index=None):
        self.remember_current_note()
        self.remember_current_search()
        self.session = self.store.load(group_id)
        self.preview_return = None
        self.refresh_sessions(group_id)
        self.states.delete(0, "end")
        for state in self.session["states"]:
            marker = "×" if state["excluded"] else "✓" if state["confirmedAt"] else "○"
            self.states.insert("end", f"{marker} {state['stateId']}  {state['note'][:24]}")
        request = self.session["request"]
        frozen = self.store.export_path(group_id).exists()
        self.task_info.configure(text=f"{self.session['title']}\n{self.session['app']}\n"
                                 f"范围: {next(k for k, v in SCOPES.items() if v == request['scope'])}\n"
                                 f"动作: {next(k for k, v in ACTIONS.items() if v == request['action'])}\n"
                                 + ("已导出并冻结, 可查看。" if frozen else "草稿可继续采集与修改标注。"))
        self.state_index = None
        self.nodes, self.raw_nodes, self.selected = [], {}, set()
        self.highlight_index = None
        self.set_note("")
        if self.session["states"]:
            self.show_state(len(self.session["states"]) - 1 if index is None else index)
        else:
            self.state_text.set("尚无状态。请在手机打开目标页面, 再点击采集。")
            self.render_nodes()
        self.sync_controls()

    def clear_task(self):
        self.remember_current_search()
        self.session, self.state_index, self.preview_return = None, None, None
        self.states.delete(0, "end")
        self.groups.selection_clear(0, "end")
        self.nodes, self.raw_nodes, self.selected = [], {}, set()
        self.highlight_index = None
        self.set_note("")
        self.set_query("")
        self.task_info.configure(text="请选择任务, 或用查看当前布局临时检查手机页面。")
        self.state_text.set("尚未打开状态")
        self.render_nodes()
        self.sync_controls()

    def select_state(self, _=None):
        if not self.busy and self.states.curselection():
            index = self.states.curselection()[0]
            if self.preview_return is not None:
                self.guard(lambda: self.open_session(self.session["groupId"], index))
            else:
                self.guard(lambda: self.show_state(index))

    def show_state(self, index):
        self.remember_current_note()
        self.remember_current_search()
        self.preview_return = None
        self.state_index = index
        state = self.session["states"][index]
        root, self.nodes = self.store.read_state(self.session, index)
        self.raw_nodes = {n.index: resolve_target(root, list(n.path)) for n in self.nodes}
        self.states.selection_clear(0, "end")
        self.states.selection_set(index)
        self.highlight_index = None
        self.set_note(state["note"])
        if state["targets"]:
            targets = {tuple(p) for p in state["targets"]}
            self.selected = {n.index for n in self.nodes if n.path in targets}
            label = "已保存确认记录" if state["confirmedAt"] else "保留原选择, 请核对并重新确认"
        elif state["excluded"]:
            self.selected = set()
            label = "此状态已排除"
        else:
            proposal = self.store.proposal(self.session, index)
            self.selected = {n.index for n in proposal.nodes}
            label = "推荐不确定, 请核对并手动确认" if proposal.ambiguous else "已推荐目标, 请核对并手动确认"
        self.state_text.set(f"{state['stateId']} · {len(self.nodes)} 个节点 · {label}")
        self.render_nodes()
        self.sync_controls()

    def require_session(self, editable=False):
        if self.preview_return is not None:
            raise ValueError("当前为临时查看模式, 请先返回任务。")
        if self.session is None:
            raise ValueError("请先新建或选择一个任务。")
        if editable and self.store.export_path(self.session["groupId"]).exists():
            raise ValueError("此任务已导出并冻结。请新建任务以采集另一目标或新状态。")

    def new_task(self):
        dialog = tk.Toplevel(self.window)
        dialog.title("新建采集任务")
        dialog.transient(self.window)
        dialog.grab_set()
        box = ttk.Frame(dialog, padding=18)
        box.pack(fill="both", expand=True)
        app = tk.StringVar(value=self.session["app"] if self.session else "")
        title, scope, action, receiver = (tk.StringVar(value=value) for value in
                                          ("", next(iter(SCOPES)), next(iter(ACTIONS)), next(iter(RECEIVERS))))
        for row, label, variable in ((0, "应用包名", app), (1, "任务名称 / 页面与目标", title)):
            ttk.Label(box, text=label).grid(row=row, column=0, sticky="w", pady=5)
            ttk.Entry(box, textvariable=variable, width=45).grid(row=row, column=1, sticky="ew", pady=5)
        foreground = ttk.Button(box, text="读取手机前台包名")
        foreground.grid(row=0, column=2, padx=(8, 0))

        def read_foreground():
            try:
                adb = self.selected_adb()
            except Exception as error:
                messagebox.showerror("选择设备", str(error), parent=dialog)
                return
            foreground.state(["disabled"])
            def done(package):
                if dialog.winfo_exists():
                    app.set(package)
                    foreground.state(["!disabled"])
                self.status.set("已读取前台应用包名。")
            self.run_job(adb.foreground_package, done, "正在读取前台应用包名...")
            # Re-enable after failures too; widget callbacks never run on the worker thread.
            def reenable():
                if dialog.winfo_exists():
                    if self.busy:
                        dialog.after(150, reenable)
                    else:
                        foreground.state(["!disabled"])
            dialog.after(150, reenable)
        foreground.configure(command=read_foreground)
        for row, label, variable, choices in ((2, "范围", scope, SCOPES), (3, "期望动作", action, ACTIONS),
                                                (4, "动作接收者", receiver, RECEIVERS)):
            ttk.Label(box, text=label).grid(row=row, column=0, sticky="w", pady=5)
            ttk.Combobox(box, textvariable=variable, values=tuple(choices), state="readonly", width=40).grid(row=row, column=1, sticky="ew")
        attrs = {name: tk.BooleanVar(value=True) for name in ("id", "text", "desc")}
        ttk.Label(box, text="允许使用的属性").grid(row=5, column=0, sticky="w", pady=8)
        checks = ttk.Frame(box)
        checks.grid(row=5, column=1, sticky="w")
        for name, variable in attrs.items():
            ttk.Checkbutton(checks, text=name, variable=variable).pack(side="left", padx=8)
        ttk.Label(box, text="例如: 更新列表中某应用的图标。后续状态应保留同一目标, 同时改变周围布局。",
                  wraplength=560).grid(row=6, column=0, columnspan=3, sticky="w", pady=10)

        def create():
            if self.busy:
                return
            try:
                session = self.store.create(app=app.get().strip(), title=title.get().strip(),
                                            scope=SCOPES[scope.get()], action=ACTIONS[action.get()],
                                            receiver=RECEIVERS[receiver.get()],
                                            attributes=[name for name, value in attrs.items() if value.get()])
                dialog.destroy()
                self.open_session(session["groupId"])
                self.status.set("任务已建立。请保持目标应用在前台, 点击采集当前手机布局。")
            except Exception as error:
                messagebox.showerror("无法新建任务", str(error), parent=dialog)
        ttk.Button(box, text="创建任务", command=create).grid(row=7, column=1, sticky="e")

    def capture(self):
        self.require_session(editable=True)
        adb = self.selected_adb()
        group_id, app = self.session["groupId"], self.session["app"]

        def work():
            data, provenance = self.read_phone_layout(adb, app)
            return self.store.add_snapshot(group_id, data, provenance=provenance)

        def done(session):
            self.open_session(session["groupId"])
            self.status.set("布局已保存。请核对目标和相邻文字, 填写变化说明, 再点击确认这些目标。")
        self.run_job(work, done, "正在采集布局。请保持目标页面在前台...")

    def read_phone_layout(self, adb, app):
        """Shared transport only; the caller explicitly decides whether to persist a task state."""
        if self.worker and (self.worker.adb.command != adb.command or self.worker.app != app or not self.worker.reusable()):
            self.worker.stop()
            self.worker = None
        if self.worker is None:
            self.worker = RealCaptureWorker(adb, app)
        try:
            return self.worker.capture_layout()
        except Exception as capture_error:
            try:
                self.worker.stop()
            except Exception as cleanup_error:
                raise RuntimeError(f"{capture_error}\n手机临时文件清理未完成: {cleanup_error}") from capture_error
            finally:
                self.worker = None
            raise

    def preview_current(self):
        adb = self.selected_adb()
        def work():
            app = adb.foreground_package()
            data, _ = self.read_phone_layout(adb, app)
            root, nodes = inspect_snapshot(data, app)
            return app, root, nodes
        self.run_job(work, self.show_preview, "正在读取手机前台页面, 用于临时查看...")

    def show_preview(self, result):
        app, root, nodes = result
        self.remember_current_search()
        if self.preview_return is None:
            self.remember_current_note()
            self.preview_return = {"groupId": self.session["groupId"] if self.session else None,
                                   "index": self.state_index, "selected": set(self.selected),
                                   "note": self.note.get(), "query": self.query.get(),
                                   "stateText": self.state_text.get()}
        self.nodes = nodes
        self.raw_nodes = {n.index: resolve_target(root, list(n.path)) for n in nodes}
        self.selected = set()
        self.highlight_index = None
        self.groups.selection_clear(0, "end")
        self.states.selection_clear(0, "end")
        self.set_note("")
        self.task_info.configure(text=f"临时查看\n{app}\n本次布局仅在内存中查看。\n返回任务可继续之前的标注。")
        self.state_text.set(f"临时查看 · {app} · {len(nodes)} 个节点 · 未关联任务")
        self.render_nodes()
        self.sync_controls()
        self.status.set("可以搜索、查看节点和邻近文字。查看完成后点击返回任务。")

    def return_to_task(self):
        if self.preview_return is None:
            return
        self.remember_current_search()
        previous, self.preview_return = self.preview_return, None
        if previous["groupId"] is None:
            self.clear_task()
        else:
            self.open_session(previous["groupId"], previous["index"])
            self.selected = previous["selected"]
            self.set_note(previous["note"])
            self.set_query(previous["query"])
            self.state_text.set(previous["stateText"])
            self.render_nodes()
        self.sync_controls()
        self.status.set("已结束临时查看, 本次查看未新增任务或状态。")

    def import_layout(self):
        self.require_session(editable=True)
        paths = filedialog.askopenfilenames(parent=self.window, title="选择私有目录中的布局 (按文件名顺序导入)",
                                            initialdir=self.store.root, filetypes=[("AutoJs6 布局", "*.layout.json")])
        if not paths:
            return
        group_id = self.session["groupId"]
        # Each accepted file is independently durable; an invalid later file never loses earlier work.
        def work():
            errors = []
            for path in sorted(paths):
                try:
                    self.store.import_snapshot(group_id, Path(path))
                except (ValueError, OSError) as error:
                    errors.append(Path(path).name + ": " + str(error))
            return errors
        def done(errors):
            self.open_session(group_id)
            self.status.set("导入完成。请逐个打开状态, 核对目标后确认。")
            if errors:
                self.error("部分文件未导入:\n" + "\n".join(errors))
        self.run_job(work, done, "正在校验并导入布局...")

    def clear_search(self):
        self.remember_current_search(force=True)
        self.set_query("")
        self.render_nodes()
        highlights = sorted(self.selected & self.visible)
        if highlights:
            self.focus_highlight(highlights[0])

    def render_nodes(self):
        self.table.delete(*self.table.get_children())
        query = self.query.get().strip().casefold()
        self.visible = set()
        for node in self.nodes:
            raw = self.raw_nodes[node.index]
            desc = str(raw.get("desc") or "")
            haystack = " ".join((node.full_id, node.entry_id, node.text, node.class_name, desc)).casefold()
            if query and query not in haystack and query != str(node.index):
                continue
            self.visible.add(node.index)
            values = (node.index, node.class_name.rsplit(".", 1)[-1], node.full_id or node.entry_id, node.text, desc)
            self.table.insert("", "end", iid=str(node.index), values=values)
        self.table.selection_set([str(i) for i in sorted(self.selected & self.visible)])
        if self.highlight_index in self.selected & self.visible:
            self.table.focus(str(self.highlight_index))
        else:
            self.highlight_index = None
        self.update_selection()

    def node_selected(self, _=None):
        if self.busy:
            return
        self.selected = (self.selected - self.visible) | {int(i) for i in self.table.selection()}
        focused = self.table.focus()
        if focused and int(focused) in self.selected & self.visible:
            self.highlight_index = int(focused)
        self.update_selection()

    def focus_highlight(self, index):
        highlights = sorted(self.selected & self.visible)
        if index not in highlights:
            return
        self.highlight_index = index
        # Focus/scroll only: selection_set would silently change collection ground truth.
        self.table.focus(str(index))
        self.table.see(str(index))
        self.update_selection()
        self.status.set(f"已定位第 {highlights.index(index) + 1}/{len(highlights)} 个高亮条目: 序号 {index}。")

    def navigate_highlight(self, direction):
        highlights = sorted(self.selected & self.visible)
        if not highlights:
            return
        if self.highlight_index in highlights:
            position = (highlights.index(self.highlight_index) + direction) % len(highlights)
        else:
            position = 0 if direction > 0 else len(highlights) - 1
        self.focus_highlight(highlights[position])

    def clear_selection(self):
        self.selected.clear()
        self.render_nodes()

    def use_proposal(self):
        self.require_session(editable=True)
        if self.state_index is None:
            return
        proposal = self.store.proposal(self.session, self.state_index)
        self.selected = {node.index for node in proposal.nodes}
        self.state_text.set("推荐仅供参考, 请检查父级和相邻控件后手动确认。")
        self.clear_search()

    def update_selection(self):
        highlights = self.selected & self.visible
        if self.highlight_index not in highlights:
            self.highlight_index = None
        for button in (self.previous_highlight_button, self.next_highlight_button):
            button.state(["!disabled"] if highlights and not self.busy else ["disabled"])
        ids = sorted(self.selected)
        hidden = len(self.selected - self.visible)
        self.selection_text.set(f"已选 {len(ids)} 个目标: {', '.join(map(str, ids[:24]))}"
                                + (f" (含 {hidden} 个搜索结果外的节点)" if hidden else ""))
        lines = []
        detail_ids = ([self.highlight_index] + [i for i in ids if i != self.highlight_index]
                      if self.highlight_index in highlights else ids)
        for index in detail_ids[:3]:
            node, raw = self.nodes[index], self.raw_nodes[index]
            lines.append(f"[{index}] 路径 {list(node.path)} | {node.class_name}\n"
                         f"ID: {node.full_id or node.entry_id!r} | 文字: {node.text!r} | 描述: {raw.get('desc')!r}\n"
                         f"位置: {raw.get('bounds')} | 可点击: {raw.get('clickable', False)} | 可编辑: {raw.get('editable', False)}")
            if node.path:
                parent = next(n for n in self.nodes if n.path == node.path[:-1])
                siblings = [n for n in self.nodes if n.path and n.path[:-1] == parent.path]
                lines.append(f"父级 [{parent.index}] {parent.class_name}: {parent.text!r}\n同级: "
                             + " / ".join(f"[{n.index}] {n.text or n.entry_id or n.class_name.rsplit('.', 1)[-1]}" for n in siblings[:18]))
                for distance in (2, 3):
                    if len(node.path) < distance:
                        break
                    ancestor_path = node.path[:-distance]
                    nearby = [n for n in self.nodes if n.path[:len(ancestor_path)] == ancestor_path and n.text]
                    lines.append(f"向上 {distance} 级范围内的文字: "
                                 + " / ".join(f"[{n.index}] {n.text!r}" for n in nearby[:10]))
        self.details.configure(state="normal")
        self.details.delete("1.0", "end")
        self.details.insert("1.0", "\n\n".join(lines) or "在列表或布局示意中选择目标。电脑上的选择不会点击手机。")
        self.details.configure(state="disabled")
        self.draw_preview()

    def draw_preview(self):
        self.canvas.delete("all")
        self.canvas_transform = None
        if not self.nodes:
            return
        left, top, right, bottom = self.raw_nodes[0].get("bounds", [0, 0, 1, 1])
        width, height = max(1, right - left), max(1, bottom - top)
        scale = min(max(1, self.canvas.winfo_width() - 16) / width,
                    max(1, self.canvas.winfo_height() - 16) / height)
        self.canvas_transform = (left, top, scale)
        # Limit background drawing for very large trees; selected nodes are always drawn.
        indices = list(range(min(len(self.nodes), 1400))) + sorted(self.selected)
        for index in indices:
            raw = self.raw_nodes[index]
            x1, y1, x2, y2 = raw.get("bounds", [0, 0, 0, 0])
            if x2 <= x1 or y2 <= y1:
                continue
            x1, x2 = (x1 - left) * scale + 8, (x2 - left) * scale + 8
            y1, y2 = (y1 - top) * scale + 8, (y2 - top) * scale + 8
            selected = index in self.selected
            self.canvas.create_rectangle(x1, y1, x2, y2, outline="#d34621" if selected else "#b9c5d2",
                                          width=3 if selected else 1)
            label = self.nodes[index].text or str(raw.get("desc") or "")
            if label and y2 - y1 >= 9 and x2 - x1 >= 12:
                self.canvas.create_text(x1 + 2, y1 + 1, text=label[:28], anchor="nw",
                                        fill="#8c2610" if selected else "#475569", font=("Microsoft YaHei UI", 8),
                                        width=max(10, x2 - x1 - 4))

    def canvas_selected(self, event):
        if self.busy or not self.canvas_transform:
            return
        left, top, scale = self.canvas_transform
        x, y = (event.x - 8) / scale + left, (event.y - 8) / scale + top
        candidates = []
        for node in self.nodes:
            x1, y1, x2, y2 = self.raw_nodes[node.index].get("bounds", [0, 0, 0, 0])
            if x1 <= x < x2 and y1 <= y < y2:
                candidates.append(((x2 - x1) * (y2 - y1), -len(node.path), node.index))
        if candidates:
            index = min(candidates)[2]
            if event.state & 0x0004:
                self.selected.symmetric_difference_update({index})
            else:
                self.selected = {index}
            self.clear_search()
            self.focus_highlight(index)

    def select_parent(self):
        if len(self.selected) != 1:
            raise ValueError("请先选择一个节点, 再选择其父节点。")
        node = self.nodes[next(iter(self.selected))]
        if node.path:
            self.selected = {next(n.index for n in self.nodes if n.path == node.path[:-1])}
            self.clear_search()
            self.table.see(str(next(iter(self.selected))))

    def confirm(self):
        self.require_session(editable=True)
        if self.state_index is None:
            raise ValueError("请先采集或导入布局。")
        session = self.store.confirm(self.session["groupId"], self.state_index, sorted(self.selected), self.note.get())
        self.remember_current_note(force=True)
        self.open_session(session["groupId"], self.state_index)
        self.status.set("目标已确认并保存。请在手机改变页面状态后继续采集, 或完成并导出任务。")

    def toggle_excluded(self):
        self.require_session(editable=True)
        if self.state_index is not None:
            index = self.state_index
            self.store.exclude(self.session["groupId"], index, not self.session["states"][index]["excluded"])
            self.open_session(self.session["groupId"], index)
            self.status.set("状态已更新。恢复的状态需要重新确认目标。")

    def delete_draft(self):
        self.require_session(editable=True)
        group_id = self.session["groupId"]
        if not messagebox.askyesno("删除草稿", f"永久删除草稿“{self.session['title']}”及其全部布局和标注?\n"
                                   f"共 {len(self.session['states'])} 个状态。此操作无法撤销。",
                                   parent=self.window, icon="warning", default="no"):
            return
        def work():
            try:
                self.store.delete_draft(group_id)
            except (OSError, ValueError) as error:
                return error
            return None
        def done(error):
            # A file-system error may happen after some files were removed; always reload the list.
            self.clear_task()
            self.refresh_sessions()
            if error:
                self.error(error)
            self.refresh_report(show=False)
        self.run_job(work, done, "正在删除草稿及其布局文件...")

    def delete_state(self):
        self.require_session(editable=True)
        if self.state_index is None:
            raise ValueError("请先选择一个状态。")
        group_id, index = self.session["groupId"], self.state_index
        state = self.session["states"][index]
        baseline_changes = state["stateId"] == self.store.baseline(self.session)
        message = f"永久删除状态 {state['stateId']} 的布局和标注? 此操作无法撤销。"
        if baseline_changes and len(self.session["states"]) > 1:
            message += "\n\n首个有效状态将改变。剩余有效状态的变化说明会清空, 并需要重新确认; 原目标选择保留供核对。"
        if not messagebox.askyesno("删除状态", message, parent=self.window, icon="warning", default="no"):
            return
        def work():
            try:
                self.store.delete_state(group_id, index)
            except (OSError, ValueError) as error:
                return error
            return None
        def done(error):
            session = self.store.load(group_id)
            next_index = min(index, len(session["states"]) - 1) if session["states"] else None
            self.open_session(group_id, next_index)
            self.status.set("状态已删除。" + ("首个有效状态已改变, 请重新确认剩余状态。" if baseline_changes else ""))
            if error:
                self.error(error)
        self.run_job(work, done, "正在删除状态及对应布局文件...")

    def export_task(self):
        self.require_session()
        if self.state_index is not None:
            state = self.session["states"][self.state_index]
            if state["confirmedAt"] and not state["excluded"]:
                current_targets = {self.nodes[i].path for i in self.selected}
                saved_targets = {tuple(p) for p in state["targets"]}
                if current_targets != saved_targets or self.note.get().strip() != state["note"]:
                    raise ValueError("当前状态的选择或变化说明尚未保存。请先点击确认这些目标, 再导出。")
        group_id = self.session["groupId"]
        def work():
            path = self.store.export(group_id)
            return path, self.store.report()
        def done(value):
            path, report = value
            self.open_session(group_id, self.state_index)
            self.show_report(report, show=False)
            self.status.set("任务已导出: " + str(path))
            messagebox.showinfo("任务已导出", "已保存可供现有离线工具读取的 .cgtask.json。\n\n"
                                + str(path) + "\n\n可以新建下一个目标任务。", parent=self.window)
        self.run_job(work, done, "正在校验目标、布局和状态数量并导出任务...")

    def show_report(self, report, show=True):
        self.report_data = report
        self.progress.set(f"已完成 {report['apps']} / 30 个应用, {report['groups']} / 150 组任务；"
                          f"其中 {report['appsWithFiveGroups']} 个应用达到每应用 5 组。草稿: {report['drafts']}。"
                          + ("有数据错误, 请查看详情。" if report['errors'] or report['draftErrors'] else ""))
        self.status.set("进度检查已保存到私有目录 collector/progress.json。")
        if show:
            dialog = tk.Toplevel(self.window)
            dialog.title("采集进度与缺口")
            text = tk.Text(dialog, width=95, height=26, wrap="word", padx=12, pady=12)
            text.pack(fill="both", expand=True)
            lines = [self.progress.get(), f"少于 3 个不同状态的旧任务: {report['shortGroups']} (未计入完成数)", "",
                     "应用 / 已完成任务数 / 距 5 组还差:"]
            lines.extend(f"{app}    {count}    {max(0, 5 - count)}" for app, count in report["perApp"].items())
            lines.extend(["", "数据检查问题:"])
            lines.extend(f"{e['file']}: {e['error']}" for e in report["errors"])
            lines.extend(report["draftErrors"])
            text.insert("1.0", "\n".join(lines))
            text.configure(state="disabled")

    def refresh_report(self, show=True):
        self.run_job(self.store.report, lambda report: self.show_report(report, show), "正在检查已有任务与采集进度...")

    def open_folder(self):
        if os.name == "nt":
            os.startfile(self.store.root)
        else:
            messagebox.showinfo("数据目录", str(self.store.root), parent=self.window)

    def close(self):
        try:
            self.remember_current_note()
            self.remember_current_search()
        except Exception as error:
            self.close_error = error
        self.closing = True
        self.status.set("正在结束采集并清理手机临时文件, 草稿已保留...")
        if not self.busy:
            self._finish_close()

    def _finish_close(self):
        if self.worker is not None:
            worker, self.worker = self.worker, None
            self.run_job(worker.stop, lambda _: None, "正在结束手机上的采集脚本...")
            # _poll normally reschedules itself; this branch can also be reached from _poll.
            self.schedule_poll()
            return
        if self.lock:
            self.lock.close()
        if self.close_error:
            messagebox.showwarning("采集已关闭", f"草稿保留在电脑私有目录。设备操作或临时文件清理未完成:\n{self.close_error}",
                                   parent=self.window)
        self.window.destroy()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    default = os.environ.get("CODEGEN_PRIVATE_DIR") or ("D:/codegen-private" if Path("D:/codegen-private").is_dir()
                                                       else str(Path.home() / "codegen-private"))
    parser.add_argument("--private-dir", type=Path, default=Path(default), help="Absolute private directory outside Git")
    parser.add_argument("--adb", help="Path to adb executable (auto-detected by default)")
    parser.add_argument("--serial", help="Initially select this ADB device serial")
    parser.add_argument("--report", action="store_true", help="Validate saved groups, write progress.json and print counts")
    args = parser.parse_args(argv)
    os.environ["CODEGEN_PRIVATE_DIR"] = str(args.private_dir)
    lock = None
    try:
        store = CollectionStore(args.private_dir)
        lock = WorkspaceLock(store.root)
        if args.report:
            report = store.report()
            print(json.dumps(report, ensure_ascii=False, indent=2))
            return 1 if report["errors"] or report["draftErrors"] else 0
        window = tk.Tk()
        CollectorApp(window, store, adb=args.adb, serial=args.serial, lock=lock)
        window.mainloop()
        return 0
    except (ValueError, OSError, tk.TclError) as error:
        if args.report:
            print(str(error), file=sys.stderr)
        else:
            messagebox.showerror("无法打开采集助手", str(error))
        return 2
    finally:
        if lock:
            lock.close()


if __name__ == "__main__":
    raise SystemExit(main())
