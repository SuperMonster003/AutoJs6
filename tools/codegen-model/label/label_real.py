#!/usr/bin/env python3
"""Interactively label local snapshots. Recommendations never confirm ground truth."""

import argparse
from dataclasses import dataclass
import hashlib
import math
from pathlib import Path
import shutil
import sys

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from dataset.task_io import (ACTIONS, ATTRIBUTES, SCOPES, load_group, nonempty_string,
                             private_path, snapshot_root, write_json)


@dataclass(frozen=True)
class Node:
    index: int
    path: tuple
    full_id: str
    entry_id: str
    class_name: str
    text: str
    position: tuple


@dataclass(frozen=True)
class Recommendation:
    nodes: tuple
    ambiguous: bool
    reasons: tuple


def flatten(root):
    """Use actual child order, never stale depth/indexInParent snapshot metadata."""
    bounds = root.get("bounds", [0, 0, 0, 0])
    if not isinstance(bounds, list) or len(bounds) != 4 or any(type(x) not in (int, float) for x in bounds):
        raise ValueError("Snapshot bounds must contain four numbers")
    left, top, right, bottom = bounds
    width, height = max(1, right - left), max(1, bottom - top)
    stack, nodes = [(root, ())], []
    while stack:
        raw, path = stack.pop()
        if not isinstance(raw, dict):
            raise ValueError("Snapshot children must be objects")
        bounds = raw.get("bounds", [0, 0, 0, 0])
        if not isinstance(bounds, list) or len(bounds) != 4 or any(type(x) not in (int, float) for x in bounds):
            raise ValueError("Snapshot bounds must contain four numbers")
        position = (((bounds[0] + bounds[2]) / 2 - left) / width,
                    ((bounds[1] + bounds[3]) / 2 - top) / height)
        if not all(math.isfinite(value) for value in position):
            raise ValueError("Snapshot coordinates must be finite")
        full_id = raw.get("fullId") or ""
        entry_id = raw.get("id") or ""
        # A full resource id in older exports remains useful; never invent a package.
        if not full_id and ":id/" in entry_id:
            full_id = entry_id
        values = (full_id, entry_id, raw.get("className") or "", raw.get("text") or "")
        if not all(isinstance(value, str) for value in values):
            raise ValueError("Node id/className/text must be strings")
        nodes.append(Node(len(nodes), path, *values, position))
        children = raw.get("children", [])
        if not isinstance(children, list):
            raise ValueError("Snapshot children must be a list")
        stack.extend((child, path + (index,)) for index, child in reversed(list(enumerate(children))))
    return nodes


def _score(reference, candidate):
    reasons, score = [], 0.0
    if reference.full_id and reference.full_id == candidate.full_id:
        score += 8
        reasons.append("fullId")
    if reference.class_name and reference.class_name == candidate.class_name:
        score += 2
        reasons.append("className")
    if reference.text and reference.text == candidate.text:
        score += 6
        reasons.append("text")
    # Relative position only breaks near-ties; it cannot independently identify a node.
    if not reasons:
        return 0.0, ()
    distance = math.dist(reference.position, candidate.position)
    score += max(0, 1 - distance)
    return score, tuple(reasons)


def recommend(references, candidates):
    """Suggest a distinct node per reference; even strong suggestions require confirmation."""
    if not references:
        return Recommendation((), True, ())
    proposals = []
    for order, reference in enumerate(references):
        ranked = sorted(((_score(reference, node), node) for node in candidates),
                        key=lambda item: (-item[0][0], item[1].index))
        ranked = [item for item in ranked if item[0][0] > 0]
        margin = ranked[0][0][0] - ranked[1][0][0] if len(ranked) > 1 else float("inf")
        proposals.append((order, margin, ranked))
    # Assign the clearest references first, so a weaker match does not consume their node.
    used, chosen, reasons, ambiguous = set(), {}, {}, False
    for order, margin, ranked in sorted(proposals, key=lambda item: (-item[1], item[0])):
        remaining = [item for item in ranked if item[1].index not in used]
        if not remaining:
            ambiguous = True
            continue
        (score, evidence), node = remaining[0]
        local_margin = score - remaining[1][0][0] if len(remaining) > 1 else float("inf")
        ambiguous |= margin <= 1 or local_margin <= 1 or not ({"fullId", "text"} & set(evidence))
        used.add(node.index)
        chosen[order], reasons[order] = node, evidence
    return Recommendation(tuple(chosen[index] for index in sorted(chosen)), ambiguous,
                          tuple(reasons[index] for index in sorted(reasons)))


def select(nodes, expression):
    kind, _, value = expression.strip().partition(" ")
    if kind in ("index", "n"):
        try:
            indices = [int(token.strip()) for token in value.split(",")]
        except ValueError as error:
            raise ValueError("Use index 0,2,3 (zero-based preorder indices)") from error
        if len(set(indices)) != len(indices) or any(index < 0 or index >= len(nodes) for index in indices):
            raise ValueError("Indices must be distinct and within the displayed snapshot")
        return [nodes[index] for index in indices]
    if kind in ("text", "id"):
        if not value:
            raise ValueError("A text/id query must not be empty")
        if kind == "text":
            return [node for node in nodes if value in node.text]
        return [node for node in nodes if value in node.full_id or value in node.entry_id]
    raise ValueError("Use index <0,1>, text <substring>, or id <substring>")


def show_nodes(nodes, output=print):
    # repr escapes newlines and terminal control characters in untrusted UI text.
    for node in nodes[:50]:
        output(f"[{node.index}] path={list(node.path)} class={node.class_name!r} "
               f"id={node.full_id or node.entry_id!r} text={node.text!r}")
    if len(nodes) > 50:
        output(f"Showing 50 of {len(nodes)} nodes; narrow the text/id query to inspect more.")


def confirm_selection(nodes, proposed=(), *, scope="single", expected_count=None,
                      input_fn=input, output=print):
    """Only an explicit c/confirm can accept a state. EOF aborts the whole session."""
    selected = list(proposed)
    show_nodes(selected, output)
    while True:
        answer = input_fn("confirm [c] / skip [s] / change [x] / list / index N,N / text QUERY / id QUERY: ").strip()
        if answer in ("c", "confirm"):
            if not selected:
                output("Choose targets before confirming.")
            elif scope == "single" and len(selected) != 1:
                output("Single scope requires exactly one target.")
            elif expected_count is not None and len(selected) != expected_count:
                output(f"This state requires {expected_count} distinct targets.")
            else:
                return selected
        elif answer in ("s", "skip"):
            return None
        elif answer in ("x", "change"):
            selected = []
            output("Selection cleared. Choose indices or run a text/id query.")
        elif answer == "list":
            show_nodes(nodes, output)
        else:
            try:
                selected = select(nodes, answer)
                show_nodes(selected, output)
                output(f"{len(selected)} node(s) selected; confirm explicitly after reviewing.")
            except ValueError as error:
                output(str(error))


def label_snapshots(snapshots, *, group_id, app, screen_family, output_path,
                    scope="single", attributes=ATTRIBUTES, action="none", receiver="auto",
                    mutations=None, target_queries=(), input_fn=input, output=print):
    for name, value in (("groupId", group_id), ("app", app), ("screenFamily", screen_family)):
        nonempty_string(value, name)
    if scope not in SCOPES or action not in ACTIONS or receiver not in ("auto", "self"):
        raise ValueError("Unknown request option")
    if len(set(attributes)) != len(attributes) or any(attr not in ATTRIBUTES for attr in attributes):
        raise ValueError("Invalid attributes")
    output_path = private_path(output_path)
    if not output_path.name.endswith(".cgtask.json") or output_path.exists():
        raise ValueError("Choose a new output filename ending in .cgtask.json")
    paths = [private_path(path) for path in snapshots]
    if not paths or len(set(paths)) != len(paths):
        raise ValueError("Provide distinct snapshots in chronological order")
    if any(not path.name.endswith(".layout.json") for path in paths):
        raise ValueError("All inputs must be .layout.json snapshots")
    if mutations is None:
        mutations = [[] for _ in paths]
    if len(mutations) != len(paths):
        raise ValueError("Provide one mutations entry for each snapshot")
    for state_mutations in mutations:
        if (not isinstance(state_mutations, list) or len(set(state_mutations)) != len(state_mutations)
                or any(not isinstance(item, str) or not item.strip() for item in state_mutations)):
            raise ValueError("Mutations must be distinct, nonempty strings")
        for mutation in state_mutations:
            nonempty_string(mutation, "mutation")
    confirmed, previous = [], None
    expected_count = None
    for index, path in enumerate(paths):
        output(f"State s{index}: {path.name}")
        nodes = flatten(snapshot_root(path))
        if previous is None:
            proposed = []
            for query in target_queries:
                proposed.extend(select(nodes, query))
            proposed = list({node.index: node for node in proposed}.values())
            if not proposed:
                show_nodes(nodes, output)
        else:
            recommendation = recommend(previous, nodes)
            proposed = recommendation.nodes
            output("Recommendation requires review" + (" (ambiguous or weak match)." if recommendation.ambiguous else "."))
            output("Matching evidence: " + repr(recommendation.reasons))
        selected = confirm_selection(nodes, proposed, scope=scope, expected_count=expected_count,
                                     input_fn=input_fn, output=output)
        if selected is None:
            if index == 0:
                output("Initial state skipped; no task written.")
                return None
            continue
        expected_count = len(selected)
        previous = selected
        confirmed.append((index, path, [list(node.path) for node in selected]))
    # Do not write any snapshot copies or task metadata until every prompt is resolved.
    states = []
    for index, source, targets in confirmed:
        if source.is_relative_to(output_path.parent):
            destination = source
        else:
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            destination = private_path(output_path.parent / "snapshots" / (digest + ".layout.json"))
            destination.parent.mkdir(parents=True, exist_ok=True)
            if destination.exists() and destination.read_bytes() != source.read_bytes():
                raise ValueError("Existing snapshot copy has different content")
            if not destination.exists():
                shutil.copyfile(source, destination)
        states.append({"stateId": f"s{index}", "snapshot": destination.relative_to(output_path.parent).as_posix(),
                       "mutations": mutations[index], "targets": targets})
    group = {"format": 1, "groupId": group_id, "source": "real", "app": app,
             "screenFamily": screen_family,
             "request": {"scope": scope, "attributes": list(attributes), "action": action, "receiver": receiver},
             "captureState": 0, "states": states}
    write_json(output_path, group)
    load_group(output_path)
    output(f"Wrote {len(states)} explicitly confirmed state(s): {output_path}")
    if len(states) < 2:
        output("Only one state: collect another before exporting survival labels.")
    return group


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshots", type=Path, nargs="+", required=True)
    parser.add_argument("--group-id", required=True)
    parser.add_argument("--app", required=True)
    parser.add_argument("--screen-family", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--scope", choices=SCOPES, default="single")
    parser.add_argument("--action", choices=ACTIONS, default="none")
    parser.add_argument("--receiver", choices=("auto", "self"), default="auto")
    parser.add_argument("--attributes", nargs="*", choices=ATTRIBUTES, default=list(ATTRIBUTES))
    parser.add_argument("--target", action="append", default=[], help='Initial query, e.g. "index 4" or "id package:id/buy"')
    parser.add_argument("--mutations", nargs="+", help='One comma-delimited entry per snapshot; use "-" for none')
    args = parser.parse_args(argv)
    try:
        result = label_snapshots(args.snapshots, group_id=args.group_id, app=args.app,
                                screen_family=args.screen_family, output_path=args.output,
                                scope=args.scope, attributes=args.attributes, action=args.action,
                                receiver=args.receiver, target_queries=args.target,
                                mutations=None if args.mutations is None else
                                [[] if value == "-" else value.split(",") for value in args.mutations])
    except (ValueError, OSError, EOFError, KeyboardInterrupt) as error:
        parser.exit(2, f"No completed labeling session: {error or 'explicit confirmation required'}\n")
    return 0 if result is not None else 1


if __name__ == "__main__":
    raise SystemExit(main())
