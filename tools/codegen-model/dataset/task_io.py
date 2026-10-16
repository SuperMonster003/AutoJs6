"""Shared input checks for the Python tools; Kotlin remains the plan evaluator."""

import json
import os
from pathlib import Path, PurePosixPath


SCOPES = ("single", "collection")
ACTIONS = ("none", "click", "long-click", "set-text", "scroll-forward", "scroll-backward")
ATTRIBUTES = ("id", "text", "desc")


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key: " + key)
        result[key] = value
    return result


def _invalid_constant(value):
    raise ValueError("Non-finite JSON value: " + value)


def parse_json(text):
    return json.loads(text, object_pairs_hook=_unique_object, parse_constant=_invalid_constant)


def read_json(path):
    return parse_json(Path(path).read_text(encoding="utf-8-sig"))


def write_json(path, value):
    """Replace one local artifact atomically, preserving Unicode and actual newlines."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n",
                         encoding="utf-8", newline="\n")
    temporary.replace(path)


def inherit_parent_acl(path):
    """Give a staging directory or a moved file the Windows access rights of a plain mkdir in its parent.

    Since the CVE-2024-4030 fix (Python 3.12.4 and 3.13), tempfile.mkdtemp() on Windows sets a
    protected DACL that allows only SYSTEM, Administrators and OWNER RIGHTS. Files created inside
    inherit it and a rename keeps it, so a run directory would stay unreadable for other accounts
    and tools. An empty, unprotected DACL keeps only the entries inherited from the parent (what
    icacls /reset does). Other platforms are left untouched.
    """
    if os.name != "nt":
        return
    import ctypes
    from ctypes import wintypes
    advapi32 = ctypes.WinDLL("advapi32", use_last_error=True)
    initialize = advapi32.InitializeAcl
    initialize.argtypes, initialize.restype = (ctypes.c_void_p, wintypes.DWORD, wintypes.DWORD), wintypes.BOOL
    apply = advapi32.SetNamedSecurityInfoW
    apply.argtypes = (wintypes.LPCWSTR, ctypes.c_int, wintypes.DWORD) + (ctypes.c_void_p,) * 4
    apply.restype = wintypes.DWORD
    # An empty ACL, never a NULL one: a NULL DACL would grant everyone full access.
    acl = ctypes.create_string_buffer(8)  # sizeof(ACL)
    if not initialize(acl, len(acl), 2):  # ACL_REVISION
        raise ctypes.WinError(ctypes.get_last_error())
    # SE_FILE_OBJECT, DACL_SECURITY_INFORMATION | UNPROTECTED_DACL_SECURITY_INFORMATION
    error = apply(str(path), 1, 0x20000004, None, None, acl, None)
    if error:
        raise OSError(None, ctypes.FormatError(error), str(path), error)


def require_fields(value, required, optional=(), description="object"):
    if not isinstance(value, dict):
        raise ValueError(description + " must be an object")
    missing, unknown = set(required) - value.keys(), value.keys() - set(required) - set(optional)
    if missing or unknown:
        raise ValueError(f"Invalid {description} fields: missing={sorted(missing)}, unknown={sorted(unknown)}")


def nonempty_string(value, name):
    if (not isinstance(value, str) or not value.strip()
            or any(ord(character) < 32 or 127 <= ord(character) <= 159 for character in value)):
        raise ValueError(name + " must be a nonempty string without control characters")
    return value


def private_path(path):
    """Resolve within the maintainer's private root and outside existing Git worktrees."""
    configured = os.environ.get("CODEGEN_PRIVATE_DIR")
    if not configured or not Path(configured).is_absolute():
        raise ValueError("Set CODEGEN_PRIVATE_DIR to an absolute directory outside Git worktrees")
    root, resolved = Path(configured).resolve(), Path(path).resolve()
    if not resolved.is_relative_to(root):
        raise ValueError("Real application data must stay under CODEGEN_PRIVATE_DIR")
    for ancestor in (resolved, *resolved.parents, root, *root.parents):
        if (ancestor / ".git").exists():
            raise ValueError("CODEGEN_PRIVATE_DIR and real application data must be outside Git worktrees")
    return resolved


def snapshot_path(task_file, relative):
    nonempty_string(relative, "snapshot")
    reference = PurePosixPath(relative)
    if (reference.is_absolute() or "\\" in relative or ":" in relative
            or any(part in ("", ".", "..") for part in relative.split("/"))
            or not relative.endswith(".layout.json")):
        raise ValueError("snapshot must be a .layout.json path below the task folder, using forward slashes")
    folder = Path(task_file).resolve().parent
    resolved = folder.joinpath(*reference.parts).resolve()
    if not resolved.is_relative_to(folder) or not resolved.is_file():
        raise ValueError("snapshot is missing or escapes its task folder: " + relative)
    return resolved


def snapshot_root(path):
    data = read_json(path)
    # Existing Android/JVM layout readers accept the root across envelope versions.
    if not isinstance(data, dict) or not isinstance(data.get("root"), dict):
        raise ValueError("Snapshot root must be an object")
    return data["root"]


def resolve_target(root, path):
    if not isinstance(path, list) or any(type(index) is not int or not 0 <= index <= 2**31 - 1 for index in path):
        raise ValueError("Target paths must contain only nonnegative 32-bit child indices")
    node = root
    for index in path:
        children = node.get("children", [])
        if not isinstance(children, list) or index >= len(children) or not isinstance(children[index], dict):
            raise ValueError("Target path does not exist in snapshot: " + str(path))
        node = children[index]
    return node


def load_group(path, validate_targets=True):
    path = Path(path).resolve()
    group = read_json(path)
    require_fields(group, ("format", "groupId", "source", "app", "screenFamily", "request", "states"),
                   ("captureState",), "task")
    if type(group["format"]) is not int or group["format"] != 1:
        raise ValueError("Unsupported task format")
    for field in ("groupId", "app", "screenFamily"):
        nonempty_string(group[field], field)
    if group["source"] not in ("fixture", "real"):
        raise ValueError("source must be fixture or real")
    if group["source"] == "real":
        private_path(path)
    request = group["request"]
    require_fields(request, ("scope", "attributes", "action"), ("receiver",), "request")
    if request["scope"] not in SCOPES or request["action"] not in ACTIONS:
        raise ValueError("Unknown scope or action")
    if request.get("receiver", "auto") not in ("auto", "self"):
        raise ValueError("Unknown receiver")
    attrs = request["attributes"]
    if not isinstance(attrs, list) or any(attr not in ATTRIBUTES for attr in attrs) or len(set(attrs)) != len(attrs):
        raise ValueError("attributes must be distinct id/text/desc values")
    states = group["states"]
    if not isinstance(states, list) or not states:
        raise ValueError("A task must have at least one state")
    capture_state = group.get("captureState", 0)
    if type(capture_state) is not int or not 0 <= capture_state < len(states):
        raise ValueError("captureState is out of range")
    state_ids, target_count, snapshots = set(), None, []
    for state in states:
        require_fields(state, ("stateId", "snapshot", "mutations", "targets"), description="state")
        nonempty_string(state["stateId"], "stateId")
        if state["stateId"] in state_ids:
            raise ValueError("Duplicate stateId: " + state["stateId"])
        state_ids.add(state["stateId"])
        mutations = state["mutations"]
        if not isinstance(mutations, list) or any(not isinstance(item, str) or not item.strip() for item in mutations):
            raise ValueError("mutations must be a string list")
        for mutation in mutations:
            nonempty_string(mutation, "mutation")
        if len(mutations) != len(set(mutations)):
            raise ValueError("Duplicate mutation")
        targets = state["targets"]
        if not isinstance(targets, list) or not targets:
            raise ValueError("states require nonempty targets")
        for target in targets:
            if not isinstance(target, list) or any(type(index) is not int or not 0 <= index <= 2**31 - 1 for index in target):
                raise ValueError("Invalid target path")
        if len({tuple(target) for target in targets}) != len(targets):
            raise ValueError("Duplicate target path")
        if target_count is not None and target_count != len(targets):
            raise ValueError("Every state must have the same target count")
        target_count = len(targets)
        if request["scope"] == "single" and target_count != 1:
            raise ValueError("single requires exactly one target")
        snapshot = snapshot_path(path, state["snapshot"])
        if group["source"] == "real":
            private_path(snapshot)
        if validate_targets:
            root = snapshot_root(snapshot)
            for target in targets:
                resolve_target(root, target)
        snapshots.append(snapshot)
    return group, snapshots
