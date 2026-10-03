"""Associate private fixture truth with captured nodes without using the private keys as features."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any

CAPABILITIES = frozenset(("click", "long-click", "set-text", "scroll-forward", "scroll-backward"))


def _nonblank_string(value: Any) -> bool:
    return (isinstance(value, str) and bool(value.strip())
            and not any(ord(char) < 32 or 127 <= ord(char) <= 159 for char in value))


def _depth(value: Any) -> int:
    if type(value) is not int or not 0 <= value <= 2**31 - 1:
        raise ValueError("Depth must be a nonnegative 32-bit integer")
    return value


class AssociationError(ValueError):
    def __init__(self, code: str, key: str, candidates: int = 0):
        super().__init__(f"{code}: {key} ({candidates} candidates)")
        self.code, self.key, self.candidates = code, key, candidates


@dataclass(frozen=True)
class AssociatedTarget:
    key: str
    path: tuple[int, ...]
    capabilities: tuple[str, ...]


def flatten(snapshot: dict[str, Any]) -> list[tuple[tuple[int, ...], dict[str, Any]]]:
    if not isinstance(snapshot, dict):
        raise ValueError("Snapshot must be an object")
    root = snapshot.get("root")
    if not isinstance(root, dict):
        raise ValueError("Snapshot must contain an object root")
    result = []
    pending = [((), root)]
    while pending:
        path, node = pending.pop()
        if not isinstance(node, dict):
            raise ValueError("Snapshot node must be an object")
        result.append((path, node))
        children = node.get("children", [])
        if not isinstance(children, list):
            raise ValueError("Snapshot children must be a list")
        pending.extend((path + (index,), child) for index, child in reversed(list(enumerate(children))))
    return result


def rectangle(value: Any) -> tuple[int, int, int, int]:
    if (not isinstance(value, list) or len(value) != 4
            or any(type(n) is not int or not -(2**31) <= n <= 2**31 - 1 for n in value)):
        raise ValueError("Bounds must contain four 32-bit integer coordinates")
    left, top, right, bottom = value
    if right <= left or bottom <= top:
        raise ValueError("Target bounds must have positive area")
    return left, top, right, bottom


def associate(snapshot: dict[str, Any], truth: dict[str, Any]) -> list[AssociatedTarget]:
    """Exact screen bounds first, captured depth only to resolve equal rectangles.

    A unique rectangle must also agree with an optional accessibility class supplied for audit.
    Unmatched/clipped/ambiguous targets reject the whole state; no nearest-node guessing.
    """
    if (not isinstance(truth, dict) or type(truth.get("format")) is not int or truth["format"] != 1
            or not isinstance(truth.get("targets"), list) or not truth["targets"]):
        raise ValueError("Truth format 1 with nonempty targets is required")
    by_bounds: dict[tuple[int, ...], list[tuple[tuple[int, ...], dict[str, Any]]]] = {}
    for path, node in flatten(snapshot):
        try:
            bounds = rectangle(node.get("bounds", [0, 0, 0, 0]))
        except ValueError:
            continue
        by_bounds.setdefault(bounds, []).append((path, node))
    keys: set[str] = set()
    paths: set[tuple[int, ...]] = set()
    associated = []
    for item in truth["targets"]:
        if not isinstance(item, dict):
            raise ValueError("Each truth target must be an object")
        key = item.get("key")
        if not _nonblank_string(key) or key in keys:
            raise ValueError("Truth keys must be nonblank, unique strings without control characters")
        keys.add(key)
        if "depth" in item:
            _depth(item["depth"])
        if "className" in item and not _nonblank_string(item["className"]):
            raise ValueError("Truth className must be a nonblank string without control characters")
        capabilities = item.get("capabilities", [])
        if (not isinstance(capabilities, list)
                or any(not isinstance(value, str) or value not in CAPABILITIES for value in capabilities)
                or len(capabilities) != len(set(capabilities))):
            raise ValueError("Capabilities must be distinct supported action names")
        bounds = rectangle(item.get("bounds"))
        candidates = by_bounds.get(bounds, [])
        if not candidates:
            raise AssociationError("NO_EXACT_BOUNDS", key)
        if len(candidates) > 1:
            depth = item.get("depth")
            if type(depth) is not int or depth < 0:
                raise AssociationError("AMBIGUOUS_BOUNDS", key, len(candidates))
            candidates = [(path, node) for path, node in candidates if _depth(node.get("depth", len(path))) == depth]
        if len(candidates) != 1:
            raise AssociationError("AMBIGUOUS_BOUNDS_DEPTH", key, len(candidates))
        path, node = candidates[0]
        if item.get("className") and item["className"] != node.get("className"):
            raise AssociationError("CLASS_MISMATCH", key, 1)
        if path in paths:
            raise AssociationError("DUPLICATE_TARGET_NODE", key, 1)
        paths.add(path)
        associated.append(AssociatedTarget(key, path, tuple(capabilities)))
    return sorted(associated, key=lambda target: target.key)
