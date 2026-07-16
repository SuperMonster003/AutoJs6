#!/usr/bin/env python3
import argparse
import html
import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen
from xml.etree import ElementTree


OFFICIAL_OWNER = "SuperMonster003"
OFFICIAL_REPO_PREFIX = "AutoJs6-Plugin-"
INDEX_REPO = "AutoJs6"
INDEX_BRANCH = "master"
OUTPUT_FILE = "plugins.official.generated.json"
USER_AGENT = "AutoJs6-Official-Plugin-Index-Generator"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate the AutoJs6 official plugin index.")
    parser.add_argument("--output", type=Path, default=Path(OUTPUT_FILE))
    args = parser.parse_args()

    repos = fetch_official_repos()
    items = []
    for repo in repos:
        entry = build_entry(repo)
        if entry is not None:
            items.append(entry)

    payload = {
        "schemaVersion": 1,
        "source": "OFFICIAL",
        "repository": {
            "owner": OFFICIAL_OWNER,
            "repo": INDEX_REPO,
            "branch": INDEX_BRANCH,
        },
        "items": items,
    }
    args.output.write_text(
        json.dumps(payload, ensure_ascii=True, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    print(f"Generated {args.output.resolve()} with {len(items)} official plugin entries.")
    return 0


def fetch_official_repos() -> list[dict]:
    repos = []
    page = 1
    while True:
        url = (
            f"https://api.github.com/users/{OFFICIAL_OWNER}/repos"
            f"?per_page=100&type=public&sort=updated&page={page}"
        )
        chunk = api_json(url)
        if not isinstance(chunk, list):
            raise RuntimeError("GitHub repository list response is not an array.")
        repos.extend(
            repo
            for repo in chunk
            if not repo.get("fork")
            and not repo.get("archived")
            and str(repo.get("name", "")).startswith(OFFICIAL_REPO_PREFIX)
        )
        if len(chunk) < 100:
            break
        page += 1
    return sorted(repos, key=lambda repo: str(repo.get("name", "")).lower())


def build_entry(repo: dict) -> dict | None:
    owner = repo.get("owner", {}).get("login") or OFFICIAL_OWNER
    repo_name = repo.get("name")
    branch = repo.get("default_branch") or "master"
    if not repo_name:
        return None

    release = safe_api_json(f"https://api.github.com/repos/{owner}/{repo_name}/releases/latest")
    if not isinstance(release, dict):
        print(f"Warning: skip {repo_name}, latest release unavailable.", file=sys.stderr)
        return None

    tree_paths = fetch_tree_paths(owner, repo_name, branch)
    strings_by_dir = fetch_string_resources(owner, repo_name, branch, tree_paths)
    version_map = parse_properties(raw_text(owner, repo_name, branch, "version.properties") or "")
    manifest_text = raw_text(owner, repo_name, branch, "app/src/main/AndroidManifest.xml")
    build_gradle = raw_text(owner, repo_name, branch, "app/build.gradle.kts") or ""

    package_name = (
        parse_manifest_package_name(manifest_text)
        or parse_application_id_from_build_gradle(build_gradle)
        or f"unknown.{re.sub(r'[^a-z0-9._-]', '-', repo_name.lower())}"
    )
    manifest_label = parse_manifest_application_label(manifest_text)
    release_name = str(release.get("name") or "").split(" @", 1)[0].strip()
    title = (
        resolve_string_reference(manifest_label, strings_by_dir)
        or literal_resource_value(manifest_label)
        or repo_name.removeprefix(OFFICIAL_REPO_PREFIX)
        or release_name
        or package_name
    )

    manifest_author = parse_manifest_author(manifest_text)
    release_author = release.get("author", {}).get("login")
    author = (
        resolve_string_reference(manifest_author, strings_by_dir)
        or literal_resource_value(manifest_author)
        or release_author
        or OFFICIAL_OWNER
    )

    localized_descriptions = localized_string_map(strings_by_dir, "plugin_description")
    localized_instructions = localized_string_map(strings_by_dir, "plugin_instruction")
    localized_instruction_urls = localized_instruction_markdown_urls(owner, repo_name, branch, tree_paths)

    icon_ref = parse_manifest_application_icon(manifest_text)
    icon_path = resolve_icon_path(icon_ref, tree_paths) or resolve_fallback_icon_path(tree_paths)
    night_icon_path = resolve_night_icon_path(icon_ref, tree_paths)

    release_tag = str(release.get("tag_name") or "").strip()
    version_name = (
        version_map.get("VERSION_NAME")
        or release_tag.removeprefix("v").removeprefix("V")
        or "0.0.0"
    )
    version_code = int(version_map.get("VERSION_CODE") or version_map.get("VERSION_BUILD") or 0)
    assets = release_assets(release)
    supported_abis = supported_abis_from_assets(assets)

    release_entry = {
        "versionName": version_name,
        "versionCode": version_code,
        "versionDate": str(release.get("published_at") or "")[:10] or None,
        "changelogUrl": release.get("html_url"),
        "changelogText": str(release.get("body") or "").strip() or None,
        "assets": assets,
    }

    entry = {
        "packageName": package_name,
        "iconUrl": raw_url(owner, repo_name, branch, icon_path) if icon_path else None,
        "nightIconUrl": raw_url(owner, repo_name, branch, night_icon_path) if night_icon_path else None,
        "title": title,
        "description": choose_default_localized(localized_descriptions),
        "localizedDescriptions": localized_descriptions,
        "instructionHardCoded": choose_default_localized(localized_instructions),
        "localizedInstructionHardCoded": localized_instructions,
        "instructionMarkdownUrl": choose_default_localized(localized_instruction_urls),
        "localizedInstructionMarkdownUrls": localized_instruction_urls,
        "author": author,
        "collaborators": [],
        "engine": parse_res_value(build_gradle, "plugin_engine"),
        "variant": parse_res_value(build_gradle, "plugin_variant"),
        "engineId": parse_res_value(build_gradle, "plugin_id"),
        "releases": [release_entry],
        "supportedAbis": supported_abis,
        "tags": ["official"],
        "source": "OFFICIAL",
    }
    return prune_nulls(entry)


def fetch_tree_paths(owner: str, repo: str, branch: str) -> set[str]:
    tree = safe_api_json(f"https://api.github.com/repos/{owner}/{repo}/git/trees/{branch}?recursive=1")
    if not isinstance(tree, dict):
        return set()
    if tree.get("truncated"):
        print(f"Warning: tree truncated for {owner}/{repo}@{branch}", file=sys.stderr)
    return {
        str(node.get("path", "")).strip()
        for node in tree.get("tree", [])
        if node.get("type") == "blob" and str(node.get("path", "")).strip()
    }


def fetch_string_resources(owner: str, repo: str, branch: str, tree_paths: set[str]) -> dict[str, dict[str, str]]:
    result = {}
    for path in sorted(tree_paths):
        match = re.fullmatch(r"app/src/main/res/(values(?:-[^/]+)?)/strings\.xml", path)
        if not match:
            continue
        text = raw_text(owner, repo, branch, path)
        if text is None:
            continue
        strings = parse_strings_xml(text)
        if strings:
            result[match.group(1)] = strings
    return result


def localized_string_map(strings_by_dir: dict[str, dict[str, str]], name: str) -> dict[str, str]:
    result = {}
    for directory in sorted(strings_by_dir):
        value = strings_by_dir[directory].get(name)
        if value:
            result[directory] = value
    return result


def localized_instruction_markdown_urls(
    owner: str,
    repo: str,
    branch: str,
    tree_paths: set[str],
) -> dict[str, str]:
    result = {}
    for path in sorted(tree_paths):
        match = re.fullmatch(r"app/src/main/res/(raw(?:-[^/]+)?)/plugin_instruction\.md", path)
        if match:
            result[match.group(1)] = raw_url(owner, repo, branch, path)
    return result


def release_assets(release: dict) -> list[dict]:
    result = []
    for asset in release.get("assets") or []:
        name = str(asset.get("name") or "")
        if not name.lower().endswith(".apk"):
            continue
        item = {
            "name": name,
            "browser_download_url": asset.get("browser_download_url"),
            "size": asset.get("size"),
            "digest": asset.get("digest"),
        }
        result.append(prune_nulls(item))
    return sorted(result, key=lambda item: str(item.get("name", "")).lower())


def supported_abis_from_assets(assets: list[dict]) -> list[str] | None:
    found = set()
    for asset in assets:
        found.update(parse_abis_from_text(str(asset.get("name") or "")))
    found.discard("universal")
    return sort_abis(found) or None


def parse_abis_from_text(text: str) -> set[str]:
    patterns = {
        "arm64-v8a": r"(?i)(arm64[-_]?v8a|aarch64)",
        "armeabi-v7a": r"(?i)(armeabi[-_]?v7a|armv7a?)",
        "x86_64": r"(?i)(x86[-_]?64|amd64)",
        "x86": r"(?i)(?<![a-z0-9])x86(?![-_]?64)",
        "armeabi": r"(?i)(armeabi(?![-_]?v7a))",
        "mips64": r"(?i)mips64",
        "mips": r"(?i)(?<![a-z0-9])mips(?!64)",
        "riscv64": r"(?i)riscv[-_]?64",
        "universal": r"(?i)(universal|noarch|all[-_]?abi(?:s)?|all[-_]?arch(?:es)?)",
    }
    return {abi for abi, pattern in patterns.items() if re.search(pattern, text)}


def sort_abis(values: set[str]) -> list[str]:
    order = ["arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi", "mips64", "mips", "riscv64", "universal"]
    return sorted(values, key=lambda item: order.index(item) if item in order else len(order))


def parse_strings_xml(text: str) -> dict[str, str]:
    try:
        root = ElementTree.fromstring(text.lstrip("\ufeff"))
    except ElementTree.ParseError:
        return {}
    result = {}
    for element in root.findall("string"):
        name = element.attrib.get("name")
        if not name:
            continue
        value = html.unescape("".join(element.itertext()).strip())
        if value:
            result[name] = value
    return result


def parse_properties(text: str) -> dict[str, str]:
    result = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def parse_manifest_package_name(text: str | None) -> str | None:
    return regex_group(text, r'<manifest[^>]*\bpackage="([^"]+)"')


def parse_manifest_application_label(text: str | None) -> str | None:
    return regex_group(text, r'<application[^>]*\bandroid:label="([^"]+)"')


def parse_manifest_application_icon(text: str | None) -> str | None:
    return regex_group(text, r'<application[^>]*\bandroid:icon="([^"]+)"')


def parse_manifest_author(text: str | None) -> str | None:
    return regex_group(
        text,
        r'<meta-data[^>]*\bandroid:name="org\.autojs\.plugin\.info\.AUTHOR"[^>]*\bandroid:value="([^"]+)"',
    )


def parse_application_id_from_build_gradle(text: str) -> str | None:
    return (
        regex_group(text, r'val\s+globalApplicationId\s*=\s*"([^"]+)"')
        or regex_group(text, r'\bapplicationId\s*=\s*"([^"]+)"')
        or regex_group(text, r'\bnamespace\s*=\s*"([^"]+)"')
    )


def parse_res_value(text: str, key: str) -> str | None:
    pattern = rf'resValue\(\s*"string"\s*,\s*"{re.escape(key)}"\s*,\s*"([^"]+)"\s*\)'
    return regex_group(text, pattern)


def regex_group(text: str | None, pattern: str) -> str | None:
    if not text:
        return None
    match = re.search(pattern, text, flags=re.DOTALL)
    if not match:
        return None
    value = match.group(1).strip()
    return value or None


def resolve_string_reference(value: str | None, strings_by_dir: dict[str, dict[str, str]]) -> str | None:
    if not value or not value.startswith("@string/"):
        return None
    name = value.removeprefix("@string/")
    localized = localized_string_map(strings_by_dir, name)
    return choose_default_localized(localized)


def literal_resource_value(value: str | None) -> str | None:
    if not value or value.startswith("@"):
        return None
    return value.strip() or None


def choose_default_localized(values: dict[str, str]) -> str | None:
    for key in ("values-en", "values", "en", "default"):
        value = values.get(key)
        if value:
            return value
    return next((value for _, value in sorted(values.items()) if value), None)


def resolve_icon_path(icon_ref: str | None, tree_paths: set[str]) -> str | None:
    for candidate in build_icon_candidates(icon_ref):
        if candidate in tree_paths:
            return candidate
    return None


def resolve_night_icon_path(icon_ref: str | None, tree_paths: set[str]) -> str | None:
    for candidate in build_night_icon_candidates(icon_ref):
        if candidate in tree_paths:
            return candidate
    return None


def build_icon_candidates(icon_ref: str | None) -> list[str]:
    if not icon_ref or not icon_ref.startswith("@") or "/" not in icon_ref:
        return []
    resource_type = icon_ref[1:].split("/", 1)[0]
    name = icon_ref.split("/", 1)[1]
    dirs = [
        f"app/src/main/res/{resource_type}",
        f"app/src/main/res/{resource_type}-xxxhdpi",
        f"app/src/main/res/{resource_type}-xxhdpi",
        f"app/src/main/res/{resource_type}-xhdpi",
        f"app/src/main/res/{resource_type}-hdpi",
        f"app/src/main/res/{resource_type}-mdpi",
        f"app/src/main/res/{resource_type}-anydpi-v26",
        "app/src/main/res/drawable",
        "app/src/main/res/drawable-xxxhdpi",
        "app/src/main/res/drawable-xxhdpi",
        "app/src/main/res/drawable-xhdpi",
        "app/src/main/res/drawable-hdpi",
        "app/src/main/res/drawable-mdpi",
    ]
    return [f"{directory}/{name}.{ext}" for directory in dirs for ext in ("png", "webp", "jpg", "jpeg")]


def build_night_icon_candidates(icon_ref: str | None) -> list[str]:
    if not icon_ref or not icon_ref.startswith("@") or "/" not in icon_ref:
        return []
    resource_type = icon_ref[1:].split("/", 1)[0]
    name = icon_ref.split("/", 1)[1]
    dirs = [f"app/src/main/res/{resource_type}-night", "app/src/main/res/drawable-night"]
    return [f"{directory}/{name}.{ext}" for directory in dirs for ext in ("png", "webp", "jpg", "jpeg")]


def resolve_fallback_icon_path(tree_paths: set[str]) -> str | None:
    image_paths = [
        path
        for path in tree_paths
        if re.fullmatch(r"app/src/main/res/(?:drawable(?:-[^/]+)?|mipmap(?:-[^/]+)?)/[^/]+\.(?:png|webp|jpg|jpeg)", path, re.I)
    ]
    if not image_paths:
        return None
    return sorted(image_paths, key=lambda path: (score_icon_path(path), len(path), path))[0]


def score_icon_path(path: str) -> int:
    lower_path = path.lower()
    file_name = lower_path.rsplit("/", 1)[-1]
    score = 100
    if "/drawable" in lower_path:
        score -= 20
    if file_name.startswith("ic_plugin"):
        score -= 70
    elif file_name.startswith("ic_launcher"):
        score -= 60
    elif "plugin" in file_name:
        score -= 45
    elif "logo" in file_name:
        score -= 35
    elif "icon" in file_name:
        score -= 20
    return score


def api_json(url: str):
    return json.loads(request_text(url, accept="application/vnd.github+json"))


def safe_api_json(url: str):
    try:
        return api_json(url)
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as exc:
        print(f"Warning: GitHub API request failed for {url}: {exc}", file=sys.stderr)
        return None


def raw_text(owner: str, repo: str, branch: str, path: str) -> str | None:
    try:
        return request_text(raw_url(owner, repo, branch, path), accept="text/plain, */*")
    except HTTPError as exc:
        if exc.code != 404:
            print(f"Warning: raw request failed for {owner}/{repo}/{path}: {exc}", file=sys.stderr)
        return None
    except (URLError, TimeoutError) as exc:
        print(f"Warning: raw request failed for {owner}/{repo}/{path}: {exc}", file=sys.stderr)
        return None


def request_text(url: str, accept: str) -> str:
    headers = {
        "Accept": accept,
        "User-Agent": USER_AGENT,
        "X-GitHub-Api-Version": "2022-11-28",
    }
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token and url.startswith("https://api.github.com/"):
        headers["Authorization"] = f"Bearer {token}"
    req = Request(url, headers=headers)
    with urlopen(req, timeout=30) as response:
        return response.read().decode("utf-8")


def raw_url(owner: str, repo: str, branch: str, path: str) -> str:
    return f"https://raw.githubusercontent.com/{owner}/{repo}/refs/heads/{branch}/{quote(path.lstrip('/'), safe='/')}"


def prune_nulls(value):
    if isinstance(value, dict):
        return {key: prune_nulls(item) for key, item in value.items() if item is not None}
    if isinstance(value, list):
        return [prune_nulls(item) for item in value]
    return value


if __name__ == "__main__":
    raise SystemExit(main())
