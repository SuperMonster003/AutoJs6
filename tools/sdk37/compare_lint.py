#!/usr/bin/env python3
"""Compare app debug XML and release vital text reports from the SDK migration checks."""

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


REQUIRED_REPORTS = {"lint-results-appDebug.xml", "lint-results-vital-appRelease.txt",
                    "lint-results-vital-inrtRelease.txt"}


def read_vital(path):
    content = path.read_text(encoding="utf-8-sig")
    issues = Counter()
    pattern = r"^(.+?):\d+: (Error|Fatal): (.+) \[([\w-]+)(?: from [^\]]+)?\]$"
    for file, _, message, issue_id in re.findall(pattern, content, re.MULTILINE):
        issues[(issue_id, "Fatal", message, (file.replace("\\", "/"),))] += 1
    summaries = re.findall(r"^(\d+) errors?(?:, (\d+) warnings?)?\s*$", content, re.MULTILINE)
    if summaries:
        errors, warnings = summaries[-1]
        if int(errors) != sum(issues.values()) or int(warnings or 0):
            raise ValueError(f"Unparsed vital diagnostics in {path}")
    elif content.strip() != "No issues found.":
        raise ValueError(f"Missing vital report summary in {path}")
    return issues


def read_reports(directory):
    missing = REQUIRED_REPORTS - {path.name for path in directory.iterdir()}
    if missing:
        raise ValueError(f"Missing required reports in {directory}: {sorted(missing)}")
    reports = {}
    for path in sorted(directory.glob("lint-results-*.xml")):
        root = ET.parse(path).getroot()
        if root.tag != "issues":
            raise ValueError(f"Not an Android lint report: {path}")
        issues = Counter()
        for issue in root.findall("issue"):
            # Line numbers change when imports or nearby code are edited. Compare
            # locations by file while retaining duplicate occurrences with Counter.
            files = tuple(location.get("file", "").replace("\\", "/")
                          for location in issue.findall("location"))
            key = (issue.attrib["id"], issue.attrib["severity"], issue.attrib["message"], files)
            issues[key] += 1
        reports[path.name] = issues
    for path in sorted(directory.glob("lint-results-vital-*.txt")):
        reports[path.name] = read_vital(path)
    return reports


def counts(issues):
    severity = Counter()
    by_id = Counter()
    for (issue_id, level, _, _), count in issues.items():
        severity[level] += count
        by_id[f"{level}:{issue_id}"] += count
    return {"severity": dict(sorted(severity.items())), "issues": dict(sorted(by_id.items()))}


def details(issues):
    return [dict(id=key[0], severity=key[1], message=key[2], files=key[3], count=count)
            for key, count in sorted(issues.items())]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--output", type=Path, help="Write counts and full added / removed issue identities as JSON")
    args = parser.parse_args()
    try:
        baseline = read_reports(args.baseline)
        candidate = read_reports(args.candidate)
        if baseline.keys() != candidate.keys():
            raise ValueError(f"Report sets differ: baseline={sorted(baseline)}, candidate={sorted(candidate)}")
        result = {}
        new_errors = 0
        for name, before in baseline.items():
            after = candidate[name]
            added, removed = after - before, before - after
            new_errors += sum(count for key, count in added.items() if key[1] in {"Error", "Fatal"})
            result[name] = {"baseline": counts(before), "candidate": counts(after),
                            "added": details(added), "removed": details(removed)}
            print(f"{name}: {counts(before)['severity']} -> {counts(after)['severity']}; "
                  f"added={sum(added.values())}, removed={sum(removed.values())}")
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"New Error / Fatal occurrences: {new_errors}")
        return 1 if new_errors else 0
    except (OSError, ET.ParseError, KeyError, ValueError) as error:
        print(f"Cannot compare lint reports: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
