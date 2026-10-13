# -*- coding: utf-8 -*-
"""Generate README and release history from committed sources.

python .python/generate_markdown.py                 # offline generation
python .python/generate_markdown.py --check         # offline, read-only CI check
python .python/generate_markdown.py --refresh-metadata  # explicitly query GitHub

Dates in .readme/common.json are explicit YYYY/MM/DD source values. Network
refresh is opt-in; ordinary generation and checks never read credentials or
rewrite templates, metadata caches, or source JSON files.
"""
import argparse
import sys
from pathlib import Path


def parse_arguments(argv=None):
    parser = argparse.ArgumentParser(description="Generate localized documentation from committed sources")
    parser.add_argument('--check', action='store_true', help='compare generated files without writes or network access')
    parser.add_argument('--refresh-metadata', action='store_true', help='explicitly refresh the cached GitHub project metadata before generation')
    args = parser.parse_args(argv)
    if args.check and args.refresh_metadata:
        parser.error('--check cannot be combined with --refresh-metadata')
    return args


arguments = parse_arguments() if __name__ == '__main__' else argparse.Namespace(check=False, refresh_metadata=False)
REFRESH_METADATA = arguments.refresh_metadata
GENERATED = {}

from jinja2 import Environment, FileSystemLoader, StrictUndefined
from collections import defaultdict
from pprint import pformat
import os
import json
import re
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen


def log(message: str):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {message}", flush=True)


def load_dotenv_value(env_path: str, key: str) -> str:
    """Read one value from a dotenv file without adding a runtime dependency."""
    try:
        with open(env_path, 'r', encoding='utf-8-sig') as env_file:
            for raw_line in env_file:
                line = raw_line.strip()
                if not line or line.startswith('#'):
                    continue
                if line.startswith('export '):
                    line = line[len('export '):].lstrip()

                name, separator, value = line.partition('=')
                if not separator or name.strip() != key:
                    continue

                value = value.strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
                    return value[1:-1]
                return value.split(' #', 1)[0].rstrip()
    except FileNotFoundError:
        return ''
    except OSError as e:
        log(f'[warn] Failed to read dotenv file at {env_path}: {e}')
    return ''


language_codes = ["zh-Hans", "zh-Hant-HK", "zh-Hant-TW", "en", "fr", "es", "ja", "ko", "ru", "ar"]
language_code_default = "zh-Hans"

# 定义各语言的日期格式
date_formats = {
    "zh-Hans": "%Y 年 %m 月 %d 日",  # 简体中文
    "zh-Hant-HK": "%Y 年 %m 月 %d 日",  # 香港繁体中文
    "zh-Hant-TW": "%Y 年 %m 月 %d 日",  # 台湾繁体中文
    "en": "%b %d, %Y",  # 英文
    "fr": "%d %B %Y",  # 法文
    "es": "%d de %B de %Y",  # 西班牙文
    "ja": "%Y 年 %m 月 %d 日",  # 日文
    "ko": "%Y 년 %m 월 %d 일",  # 韩文
    "ru": "%d %B %Y года",  # 俄文
    "ar": "%d %B %Y"  # 阿拉伯文
}

script_dir = os.path.dirname(os.path.abspath(__file__))
project_root_dir = os.path.abspath(os.path.join(script_dir, os.pardir))
changelog_files_source_dir = os.path.join(project_root_dir, 'app', 'src', 'main', 'assets-app', 'doc')
changelog_root_dir = os.path.join(project_root_dir, '.changelog')
readme_root_dir = os.path.join(project_root_dir, '.readme')
utils_env_path = os.path.join(project_root_dir, '.utils', '.env')

# Prefer the repository-local token, then fall back to the process environment (for example, in CI).
github_token_from_dotenv = load_dotenv_value(utils_env_path, 'GITHUB_TOKEN') if REFRESH_METADATA else ''
github_token = (github_token_from_dotenv or os.environ.get('GITHUB_TOKEN', '').strip()) if REFRESH_METADATA else ''

log(f'Starting markdown generation in {project_root_dir}')
if github_token:
    github_token_source = utils_env_path if github_token_from_dotenv else 'process environment'
    log(f'Using GITHUB_TOKEN from {github_token_source}')

RELATED_AUTOJS_PROJECT_DATES_START_MARKER = "# BEGIN RELATED_AUTOJS_PROJECT_DATES"
RELATED_AUTOJS_PROJECT_DATES_END_MARKER = "# END RELATED_AUTOJS_PROJECT_DATES"

# BEGIN RELATED_AUTOJS_PROJECT_DATES
RELATED_AUTOJS_PROJECT_DATES = {'hyb1996_auto_js': {'repo': 'https://github.com/hyb1996/Auto.js',
                     'created': '2017/01/27',
                     'created_commit_url': 'https://github.com/SuperMonster003/AutoJs6/commit/307dbe8859692c48f1d3594d99077c0a2638bb39',
                     'recent_active': '2020/03/13',
                     'recent_active_commit_url': 'https://github.com/SuperMonster003/AutoJs6/commit/483c6492b7fa8aaf3f29544fbb9b55c46c3dc9e0'},
 'auto_js_pro_7': {'created': '2019/03/13', 'recent_active': '2019/07/08'},
 'auto_js_pro_8': {'created': '2019/10/13', 'recent_active': '2021/07/24'},
 'tonyjiangwj_auto_js': {'repo': 'https://github.com/TonyJiangWJ/Auto.js',
                         'created': '2019/11/21',
                         'recent_active_cached': '2025/08/22',
                         'created_commit_cached': 'dbd71b8c0ab0737b2666f2cc1dc5ecd3c82f83fd',
                         'recent_active_commit_cached': '2fd2cd000e5d9dff2f584ecbb3bf6980bbcba64b'},
 'kkevsekk1_autox': {'repo': 'https://github.com/kkevsekk1/AutoX',
                     'created': '2020/07/24',
                     'created_commit_url': 'https://github.com/aiselp/AutoX/commit/3f99d7a3457aaa7eb733a30a0c8ba487d352ebf1',
                     'recent_active': '2024/11/14',
                     'recent_active_commit_url': 'https://github.com/aiselp/AutoX/commit/e71906d87e33f7ae932bb81622bbce84d6372b7a',
                     'recent_active_status': 'inaccessible'},
 'auto_js_pro_9': {'created': '2021/03/28', 'recent_active': '2023/02/09'},
 'supermonster003_autojs6': {'repo': 'https://github.com/SuperMonster003/AutoJs6',
                             'created': '2021/12/01',
                             'recent_active_cached': '2026/03/16',
                             'created_commit_cached': '99a1d8490fac5b6d55f6f183db59ad833a2064ed',
                             'recent_active_commit_cached': 'ed3eb10e88db5a8425fd94bdddefa4176e5e1c94'},
 'blackcd318_autojs4': {'repo': 'https://github.com/blackcd318/autojs4',
                        'created': '2021/12/15',
                        'recent_active': '2023/07/31',
                        'created_commit_cached': '64828428dac44f66360b107be177e177b7e449fa',
                        'recent_active_commit_cached': 'fabfb0bab6c95166afabe9ed56cf7912b34b3a68'},
 'kkevsekk1_autox_v6': {'repo': 'https://github.com/kkevsekk1/AutoX',
                        'created': '2022/05/26',
                        'created_commit_url': 'https://github.com/aiselp/AutoX/commit/a8c3ebf26d1c5bf0f912b10889efce24261c52ac',
                        'recent_active': '2024/06/27',
                        'recent_active_commit_url': 'https://github.com/aiselp/AutoX/commit/015f4f3278ebcb7da6604b41ec0dc69505423e75',
                        'recent_active_status': 'inaccessible'},
 'openautojs_openautojs': {'repo': 'https://github.com/openautojs/openautojs',
                           'created': '2023/02/17',
                           'recent_active': '2023/04/16',
                           'created_commit_cached': '29adb5d1953a56c825decff30fc20de831092f38',
                           'recent_active_commit_cached': 'f23289d25edbcb1b1b73248006a46462d437f826'},
 'supermonster003_autojs4': {'repo': 'https://github.com/SuperMonster003/AutoJs4',
                             'created': '2023/04/11',
                             'recent_active_cached': '2026/03/15',
                             'created_commit_cached': '8de11699b608be1c1f49f0f10e2e2826f8c77978',
                             'recent_active_commit_cached': '3fb08269198ebd49a880ab3ca2621383eaa10443'},
 'aiselp_autox': {'repo': 'https://github.com/aiselp/AutoX',
                  'created': '2024/04/21',
                  'recent_active_cached': '2026/10/09',
                  'created_commit_cached': 'c76e80b0d049947b0f3ac49ada2d16d0b10c2800',
                  'recent_active_commit_cached': '54d2638fec07c0bd79663e286e8e426a6e28511e'},
 'ozobiozobi_autoxjs_v6_ozobi': {'repo': 'https://github.com/ozobiozobi/Autoxjs_v6_ozobi',
                                 'created': '2024/10/01',
                                 'recent_active_cached': '2026/04/16',
                                 'recent_active_commit_cached': 'e8563000af8068f07f821a4a8b23d4457167c1ab'},
 'autox_community_autox': {'repo': 'https://github.com/autox-community/AutoX',
                           'created': '2025/03/30',
                           'recent_active_cached': '2025/11/29',
                           'created_commit_cached': '696181012293d6fc6c6d6333b80bc5be6c2b442d',
                           'recent_active_commit_cached': '4dca21b4af5e17e3f6c372d8b74d9700a3c8fa5a'}}
# END RELATED_AUTOJS_PROJECT_DATES

RELATED_AUTOJS_PROJECT_TABLE_ENTRIES = {
    'hyb1996_auto_js': {
        'project': '[Auto.js](https://github.com/hyb1996/Auto.js)',
        'app_name': '[Auto.js](https://github.com/SuperMonster003/AutoJs6/blob/ab2ad906e767f188c70c33e22224104b5c2c0135/app/src/main/res/values/strings.xml#L2)',
        'main_developers': '[hyb1996](https://github.com/hyb1996)',
    },
    'auto_js_pro_7': {
        'project': 'Auto.js Pro 7',
        'app_name': 'Auto.js',
        'main_developers': '[hyb1996](https://github.com/hyb1996)',
    },
    'auto_js_pro_8': {
        'project': 'Auto.js Pro 8',
        'app_name': 'AutoJsPro',
        'main_developers': '[hyb1996](https://github.com/hyb1996)',
    },
    'tonyjiangwj_auto_js': {
        'project': '[Auto.js](https://github.com/TonyJiangWJ/Auto.js)',
        'app_name': '[Auto.js&#160;M](https://github.com/TonyJiangWJ/Auto.js/blob/268ec8895bbfa28fc7715154eb15b1c1eaaefd14/app/src/main/res/values/strings.xml#L2)',
        'main_developers': '[TonyJiangWJ](https://github.com/TonyJiangWJ)',
    },
    'kkevsekk1_autox': {
        'project': '[AutoX](https://github.com/kkevsekk1/AutoX)',
        'app_name': '[Autox.js](https://github.com/kkevsekk1/AutoX/blob/8143e4ed893d4af05d22aa791b83a962f9959873/app/src/main/res/values/strings.xml#L2)',
        'app_name_style': 'white-space:pre',
        'main_developers': '[kkevsekk1](https://github.com/kkevsekk1)',
    },
    'auto_js_pro_9': {
        'project': '[Auto.js Pro 9](https://pro.autojs.org/)',
        'app_name': 'AutoJsPro',
        'main_developers': '[hyb1996](https://github.com/hyb1996)',
    },
    'supermonster003_autojs6': {
        'project': '[AutoJs6](https://github.com/SuperMonster003/AutoJs6)',
        'app_name': '[AutoJs6](https://github.com/SuperMonster003/AutoJs6/blob/a8ce1b9acb541e9736c33134be3194c3148a15a3/common/src/main/res/values/strings.xml#L27)',
        'main_developers': '[SuperMonster003](https://github.com/SuperMonster003)',
    },
    'blackcd318_autojs4': {
        'project': '[autojs4](https://github.com/blackcd318/autojs4)',
        'app_name': 'Auto.js',
        'main_developers': '[blackcd318](https://github.com/blackcd318)',
    },
    'kkevsekk1_autox_v6': {
        'project': '[AutoX](https://github.com/kkevsekk1/AutoX)',
        'app_name': '[Autox.js&#160;v6](https://github.com/kkevsekk1/AutoX/blob/8b6776cff8b0fca4be4a52719b7d7d07c0a058f3/app/build.gradle#L130)',
        'app_name_style': 'white-space:pre',
        'main_developers': '[wilinz](https://github.com/wilinz) / [aiselp](https://github.com/aiselp)',
    },
    'openautojs_openautojs': {
        'project': '[openautojs](https://github.com/openautojs/openautojs)',
        'app_name': '[OpenAuto.js](https://github.com/openautojs/openautojs/blob/a11feaad025154de9b453ba70b49e94a6ca8b48a/app/src/main/res-i18n/values/strings.xml#L2)',
        'app_name_style': 'white-space:pre',
        'main_developers': '[openautojs](https://github.com/openautojs)',
    },
    'supermonster003_autojs4': {
        'project': '[AutoJs4](https://github.com/SuperMonster003/AutoJs4)',
        'app_name': '[AutoJs4](https://github.com/SuperMonster003/AutoJs4/blob/a6b952704b8305aed1bb6265f40acb3598ac80df/app/src/main/res/values/strings.xml#L2)',
        'main_developers': '[SuperMonster003](https://github.com/SuperMonster003)',
    },
    'aiselp_autox': {
        'project': '[AutoX](https://github.com/aiselp/AutoX)',
        'app_name': '[Autox.js&#160;v7](https://github.com/aiselp/AutoX/blob/484491fd5fe12b8203d0b09c181eb0f471c0ea9f/app/build.gradle.kts#L124)',
        'app_name_style': 'white-space:pre',
        'main_developers': '[aiselp](https://github.com/aiselp)',
    },
    'ozobiozobi_autoxjs_v6_ozobi': {
        'project': '[Autoxjs_v6_ozobi](https://github.com/ozobiozobi/Autoxjs_v6_ozobi)',
        'app_name': '[Autox.js&#160;v6_ozobi](https://github.com/ozobiozobi/Autoxjs_v6_ozobi/blob/a651d02246e09cfbbfa87e6eaccf900fab/app/build.gradle.kts#L143)',
        'app_name_style': 'white-space:pre',
        'main_developers': '[ozobiozobi](https://github.com/ozobiozobi)',
    },
    'autox_community_autox': {
        'project': '[AutoX](https://github.com/autox-community/AutoX)',
        'app_name': '[Autox.js&#160;v6](https://github.com/autox-community/AutoX/blob/8b6776cff8b0fca4be4a52719b7d7d07c0a058f3/app/build.gradle#L130)',
        'app_name_style': 'white-space:pre',
        'main_developers': '[autox-community](https://github.com/autox-community)',
    },
}

# 设置模板文件夹
file_loader = FileSystemLoader([
    changelog_root_dir,
    readme_root_dir,
])

# 创建 Jinja2 环境
env = Environment(
    loader=file_loader,
    undefined=StrictUndefined,
)

# 从 version.properties 读取 JDK 相关限制, 以便注入到 merged_data
def _load_version_properties(props_path: str) -> dict:
    props = {}
    try:
        with open(props_path, 'r', encoding='utf-8') as f:
            for raw in f.read().splitlines():
                line = raw.strip()
                if not line or line.startswith('#') or line.startswith('!'):
                    continue
                sep = line.find('=')
                if sep <= 0:
                    continue
                key = line[:sep].strip()
                val = line[sep + 1 :].strip()
                props[key] = val
    except FileNotFoundError:
        log(f'[warn] version.properties not found at {props_path}, skip injecting JDK constraints')
    except Exception as e:
        log(f'[warn] Failed to read version.properties: {e}')
    return props

_version_props_path = os.path.join(project_root_dir, 'version.properties')
_version_props = _load_version_properties(_version_props_path)

_jdk_min_supported = _version_props.get('JAVA_VERSION_MIN_SUPPORTED')
_jdk_min_suggested = _version_props.get('JAVA_VERSION_MIN_SUGGESTED')
_jdk_max_supported = _version_props.get('JAVA_VERSION_MAX_SUPPORTED')
_android_studio_min_supported = _version_props.get('MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION')
_intellij_idea_min_supported = _version_props.get('MIN_SUPPORTED_INTELLIJ_IDEA_IDE_VERSION')

# 在加载模板前, 先更新模板中的 Android Studio 与 IntelliJ IDEA Badge 版本
def update_readme_badge_versions():
    """Render version badges in memory; templates are never rewritten."""
    template_path = Path(readme_root_dir) / 'template_readme.md'
    data = template_path.read_text(encoding='utf-8')
    # 与原逻辑等价的正则片段
    prefix = r'<img\s[^>]*?src="https://img\.shields\.io/badge/'
    android_fragment = r'android(?:%20|\s)studio'
    idea_fragment = r'intellij(?:%20|\s)idea'
    # 捕获 2024.1 或 2024.1.2 这类版本, 末尾带一个 '+'
    version_group = r'.*?-([0-9]{4}\.[0-9]+(?:\.[0-9]+)?)\+'

    re_android = re.compile(prefix + android_fragment + version_group, re.IGNORECASE)
    re_idea = re.compile(prefix + idea_fragment + version_group, re.IGNORECASE)

    original = data

    if _android_studio_min_supported:
        def _repl_android(m):
            old = m.group(1)
            if old != _android_studio_min_supported:
                return m.group(0).replace(old, _android_studio_min_supported)
            return m.group(0)
        data = re_android.sub(_repl_android, data)

    if _intellij_idea_min_supported:
        def _repl_idea(m):
            old = m.group(1)
            if old != _intellij_idea_min_supported:
                return m.group(0).replace(old, _intellij_idea_min_supported)
            return m.group(0)
        data = re_idea.sub(_repl_idea, data)

    return data


# 读取模板文件 (在更新徽标版本之后)
template_readme = env.from_string(update_readme_badge_versions())
template_changelog = env.get_template('template_changelog.md')

language_content_map = {}
changelog_content_map = {}
changelog_data_map = defaultdict(dict)

# 读取共用 JSON 文件
log('Loading common README data')
with open(os.path.join(readme_root_dir, 'common.json'), 'r', encoding='utf-8') as common_json_file:
    common_data = json.load(common_json_file)

# 检查并转换 common_data 中的值类型
for key, value in common_data.items():
    if isinstance(value, int):
        common_data[key] = str(value)
    elif isinstance(value, str) and value.isdigit():
        common_data[key] = int(value)


def format_date(date_str, lang_code):
    if date_str == "%CURRENT_DATE%":
        raise ValueError("Use a committed YYYY/MM/DD date instead of %CURRENT_DATE%")

    if lang_code in date_formats:
        try:
            date_obj = datetime.strptime(date_str, "%Y/%m/%d")
            months = ('January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December')
            formatted_date = date_formats[lang_code]
            for token, value in {'%Y': str(date_obj.year), '%m': f'{date_obj.month:02d}', '%d': f'{date_obj.day:02d}', '%B': months[date_obj.month - 1], '%b': months[date_obj.month - 1][:3]}.items():
                formatted_date = formatted_date.replace(token, value)

            # 在 formatted_date 中每个非字母和非数字字符前后增加空格 (如果没有空格)
            formatted_date_with_spaces = re.sub(r'(\d+)', r' \1 ', formatted_date).strip()
            # 将数字与逗号之间的空格去除
            formatted_date_with_spaces = re.sub(r'(\d)\s+,', r'\1,', formatted_date_with_spaces)
            formatted_date_with_spaces = re.sub(r'\s+', ' ', formatted_date_with_spaces)

            # 去掉前导零
            formatted_date_no_leading_zeros = re.sub(r'\b0+(\d)', r'\1', formatted_date_with_spaces)

            return formatted_date_no_leading_zeros
        except ValueError as e:
            log(f"Date format error: {e}")
    return date_str


def parse_github_repo_path(repo_url: str):
    match = re.search(r'github\.com[:/]([^/\s?#]+)/([^/\s?#]+)', repo_url or '', re.IGNORECASE)
    if not match:
        return None
    owner = match.group(1)
    repo = re.sub(r'\.git$', '', match.group(2))
    return owner, repo


def request_github_json(url: str, description: str = ''):
    if not REFRESH_METADATA:
        return None, {}
    request_label = description or url
    started_at = datetime.now()
    log(f"Fetching GitHub data: {request_label}")
    headers = {
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'AutoJs6-readme-generator',
    }
    if github_token:
        headers['Authorization'] = f'Bearer {github_token}'
    request = Request(
        url,
        headers=headers,
    )
    try:
        with urlopen(request, timeout=15) as response:
            data = json.loads(response.read().decode('utf-8'))
            elapsed = (datetime.now() - started_at).total_seconds()
            log(f"Fetched GitHub data: {request_label} ({elapsed:.1f}s)")
            return data, response.headers
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError, OSError) as e:
        elapsed = (datetime.now() - started_at).total_seconds()
        log(f'[warn] Failed to fetch GitHub data: {request_label} ({elapsed:.1f}s), url={url}, error={e}')
        return None, {}


def parse_link_header(link_header: str) -> dict:
    links = {}
    for part in (link_header or '').split(','):
        match = re.match(r'\s*<([^>]+)>;\s*rel="([^"]+)"', part)
        if match:
            links[match.group(2)] = match.group(1)
    return links


def github_commit_item_to_data(commit_item) -> dict:
    commit = (commit_item or {}).get('commit') or {}
    commit_date = ((commit.get('committer') or {}).get('date')
                   or (commit.get('author') or {}).get('date'))
    if not commit_date:
        return {}
    try:
        date = datetime.fromisoformat(commit_date.replace('Z', '+00:00')).astimezone(timezone.utc).strftime('%Y/%m/%d')
    except ValueError:
        return {}
    return {
        'date': date,
        'sha': (commit_item or {}).get('sha', ''),
    }


def format_github_api_date(date_str: str) -> str:
    return date_str.replace('/', '-')


def describe_github_commit_lookup(owner: str, repo: str, oldest: bool = False, since_date: str = '', until_date: str = '') -> str:
    repo_label = f'{owner}/{repo}'
    if since_date and until_date and since_date == until_date:
        return f'{repo_label} commit on {since_date}'
    if oldest:
        return f'{repo_label} oldest commit'
    if since_date or until_date:
        return f'{repo_label} commits from {since_date or "-"} to {until_date or "-"}'
    return f'{repo_label} latest commit'


def fetch_github_commit(repo_url: str, oldest: bool = False, since_date: str = '', until_date: str = '') -> dict:
    if not REFRESH_METADATA:
        return {}
    repo_path = parse_github_repo_path(repo_url)
    if not repo_path:
        return {}

    owner, repo = repo_path
    request_label = describe_github_commit_lookup(owner, repo, oldest, since_date, until_date)
    query = {'per_page': '1'}
    if since_date:
        query['since'] = f'{format_github_api_date(since_date)}T00:00:00Z'
    if until_date:
        query['until'] = f'{format_github_api_date(until_date)}T23:59:59Z'
    base_url = f"https://api.github.com/repos/{quote(owner, safe='')}/{quote(repo, safe='')}/commits?{urlencode(query)}"
    data, headers = request_github_json(base_url, request_label)
    if not isinstance(data, list) or not data:
        log(f'No GitHub commit data returned for {request_label}')
        return {}

    if oldest:
        last_url = parse_link_header(headers.get('Link', '')).get('last')
        if last_url:
            oldest_data, _ = request_github_json(last_url, f'{owner}/{repo} oldest commit page')
            if isinstance(oldest_data, list) and oldest_data:
                return github_commit_item_to_data(oldest_data[0])

    return github_commit_item_to_data(data[0])


def fetch_github_commit_by_sha(repo_url: str, commit_sha: str):
    if not REFRESH_METADATA:
        return None
    repo_path = parse_github_repo_path(repo_url)
    if not repo_path or not commit_sha:
        return {}

    owner, repo = repo_path
    url = f"https://api.github.com/repos/{quote(owner, safe='')}/{quote(repo, safe='')}/commits/{quote(commit_sha, safe='')}"
    data, _ = request_github_json(url, f'{owner}/{repo} commit {commit_sha[:12]}')
    if data is None:
        return None
    if not isinstance(data, dict):
        return {}
    return github_commit_item_to_data(data)


def resolve_cached_commit_for_date(date_data: dict, cache_key: str, repo_url: str, date_str: str) -> tuple[str, bool]:
    commit_sha = date_data.get(cache_key, '')
    if not commit_sha or not repo_url or not date_str:
        return commit_sha, False

    commit_data = fetch_github_commit_by_sha(repo_url, commit_sha)
    if commit_data is None:
        return commit_sha, False
    if commit_data.get('date') == date_str:
        return commit_sha, False

    date_data.pop(cache_key, None)
    return '', True


def persist_related_autojs_project_dates():
    if not REFRESH_METADATA:
        raise RuntimeError('Metadata updates require --refresh-metadata')
    source_path = os.path.abspath(__file__)
    try:
        with open(source_path, 'r', encoding='utf-8') as f:
            source = f.read()
    except OSError as e:
        log(f'[warn] Failed to read {source_path}: {e}')
        return

    replacement = (
        f"{RELATED_AUTOJS_PROJECT_DATES_START_MARKER}\n"
        f"RELATED_AUTOJS_PROJECT_DATES = {pformat(RELATED_AUTOJS_PROJECT_DATES, width=120, sort_dicts=False)}\n"
        f"{RELATED_AUTOJS_PROJECT_DATES_END_MARKER}"
    )
    pattern = (
        rf"{re.escape(RELATED_AUTOJS_PROJECT_DATES_START_MARKER)}\n"
        rf"RELATED_AUTOJS_PROJECT_DATES = .*?\n"
        rf"{re.escape(RELATED_AUTOJS_PROJECT_DATES_END_MARKER)}"
    )
    updated_source, count = re.subn(pattern, replacement, source, count=1, flags=re.DOTALL)
    if count != 1 or updated_source == source:
        return

    try:
        with open(source_path, 'w', encoding='utf-8') as f:
            f.write(updated_source)
        log(f'Updated related Auto.js project date cache in {source_path}')
    except OSError as e:
        log(f'[warn] Failed to write {source_path}: {e}')


def build_github_commit_url(repo_url: str, commit_sha: str) -> str:
    if not repo_url or not commit_sha:
        return ''
    return f"{repo_url.rstrip('/').removesuffix('.git')}/commit/{commit_sha}"


def format_table_date(date_str: str, repo_url: str = '', commit_sha: str = '', commit_url: str = '') -> str:
    if not date_str:
        return '-'
    value = f'`{date_str}`'
    resolved_commit_url = commit_url or build_github_commit_url(repo_url, commit_sha)
    return f'[{value}]({resolved_commit_url})' if resolved_commit_url else value


def calc_years_between(start_date_str: str, end_date_str: str) -> str:
    try:
        start_date = datetime.strptime(start_date_str, '%Y/%m/%d')
        end_date = datetime.strptime(end_date_str, '%Y/%m/%d')
        return f"{(end_date - start_date).days / 365:.2f}"
    except ValueError:
        return '-'


def parse_date_for_sort(date_str: str) -> datetime:
    try:
        return datetime.strptime(date_str, '%Y/%m/%d')
    except ValueError:
        return datetime.min


def related_autojs_table_cell(content: str, style: str = 'word-break:keep-all;white-space:nowrap') -> str:
    return f'<span style="{style}">{content}</span>'


def build_related_autojs_project_table_row(record: dict) -> str:
    table_entry = RELATED_AUTOJS_PROJECT_TABLE_ENTRIES[record['project_key']]
    cells = [
        related_autojs_table_cell(table_entry['project']),
        related_autojs_table_cell(table_entry['app_name'], table_entry.get('app_name_style', 'word-break:keep-all;white-space:nowrap')),
        related_autojs_table_cell(table_entry['main_developers']),
        related_autojs_table_cell(format_table_date(
            record['created_date'],
            record['repo_url'],
            record['created_commit'],
            record['created_commit_url'],
        )),
        related_autojs_table_cell(format_table_date(
            record['recent_active_date'],
            record['repo_url'],
            record['recent_active_commit'],
            record['recent_active_commit_url'],
        )),
        related_autojs_table_cell(calc_years_between(record['created_date'], record['recent_active_date'])),
    ]
    return f"| {' | '.join(cells)} |"


def describe_related_project(project_key: str, repo_url: str) -> str:
    repo_path = parse_github_repo_path(repo_url)
    if repo_path:
        owner, repo = repo_path
        return f'{project_key} ({owner}/{repo})'
    return f'{project_key} (static data)'


def build_related_autojs_project_data() -> dict:
    changed = False
    rendered_data = {}
    table_records = []
    total_projects = len(RELATED_AUTOJS_PROJECT_DATES)

    log(f'Building related Auto.js project data ({total_projects} projects)')
    for project_index, (project_key, date_data) in enumerate(RELATED_AUTOJS_PROJECT_DATES.items(), start=1):
        repo_url = date_data.get('repo', '')
        project_label = describe_related_project(project_key, repo_url)
        log(f'[{project_index}/{total_projects}] Resolving related project: {project_label}')
        created_date = date_data.get('created', '')
        created_commit = date_data.get('created_commit', '')
        created_commit_url = date_data.get('created_commit_url', '')
        if not created_commit and not created_commit_url:
            created_commit, cache_changed = resolve_cached_commit_for_date(date_data, 'created_commit_cached', repo_url, created_date)
            if cache_changed:
                log(f'Cached created commit is stale: {project_key}')
            changed = changed or cache_changed
        if not created_date:
            commit_data = fetch_github_commit(repo_url, oldest=True)
            if commit_data:
                created_date = commit_data.get('date', '')
                created_commit = commit_data.get('sha', '')
                if created_date:
                    date_data['created'] = created_date
                if created_commit:
                    date_data['created_commit_cached'] = created_commit
                changed = True
        elif repo_url and not created_commit and not created_commit_url:
            commit_data = fetch_github_commit(repo_url, oldest=True, since_date=created_date, until_date=created_date)
            created_commit = commit_data.get('sha', '') if commit_data.get('date') == created_date else ''
            if created_commit:
                date_data['created_commit_cached'] = created_commit
                changed = True

        fixed_recent_active_date = date_data.get('recent_active', '')
        recent_active_commit = date_data.get('recent_active_commit', '')
        recent_active_commit_url = date_data.get('recent_active_commit_url', '')
        if fixed_recent_active_date:
            recent_active_date = fixed_recent_active_date
            if not recent_active_commit and not recent_active_commit_url:
                recent_active_commit, cache_changed = resolve_cached_commit_for_date(date_data, 'recent_active_commit_cached', repo_url, recent_active_date)
                if cache_changed:
                    log(f'Cached recent active commit is stale: {project_key}')
                changed = changed or cache_changed
            if repo_url and not recent_active_commit and not recent_active_commit_url:
                commit_data = fetch_github_commit(repo_url, since_date=recent_active_date, until_date=recent_active_date)
                recent_active_commit = commit_data.get('sha', '') if commit_data.get('date') == recent_active_date else ''
                if recent_active_commit:
                    date_data['recent_active_commit_cached'] = recent_active_commit
                    changed = True
        else:
            commit_data = fetch_github_commit(repo_url)
            if commit_data:
                recent_active_date = commit_data.get('date', '')
                recent_active_commit = commit_data.get('sha', '')
                if date_data.get('recent_active_cached') != recent_active_date:
                    date_data['recent_active_cached'] = recent_active_date
                    changed = True
                if recent_active_commit and date_data.get('recent_active_commit_cached') != recent_active_commit:
                    date_data['recent_active_commit_cached'] = recent_active_commit
                    changed = True
            else:
                recent_active_date = date_data.get('recent_active_cached', '')
                if not recent_active_date:
                    raise ValueError(f'Missing committed recent activity date: {project_key}')
                log(f'Using cached recent active date for {project_key}: {recent_active_date}')
                if not recent_active_commit:
                    recent_active_commit, cache_changed = resolve_cached_commit_for_date(
                        date_data,
                        'recent_active_commit_cached',
                        repo_url,
                        recent_active_date,
                    )
                    if cache_changed:
                        log(f'Cached recent active commit is stale: {project_key}')
                    changed = changed or cache_changed

        prefix = f'data_related_autojs_project_{project_key}'
        rendered_data[f'{prefix}_created_on'] = format_table_date(created_date, repo_url, created_commit, created_commit_url)
        rendered_data[f'{prefix}_recent_active_on'] = format_table_date(recent_active_date, repo_url, recent_active_commit, recent_active_commit_url)
        rendered_data[f'{prefix}_active_maintenance_phase'] = calc_years_between(created_date, recent_active_date)
        table_records.append({
            'project_key': project_key,
            'repo_url': repo_url,
            'created_date': created_date,
            'created_commit': created_commit,
            'created_commit_url': created_commit_url,
            'recent_active_date': recent_active_date,
            'recent_active_commit': recent_active_commit,
            'recent_active_commit_url': recent_active_commit_url,
        })
        log(f'[{project_index}/{total_projects}] Resolved {project_key}: created={created_date or "-"}, recent_active={recent_active_date or "-"}')

    if changed:
        log('Related Auto.js project date cache changed; writing updated cache')
        persist_related_autojs_project_dates()

    sorted_records = sorted(
        table_records,
        key=lambda item: parse_date_for_sort(item['recent_active_date']),
        reverse=True,
    )
    rendered_data['placeholder_related_autojs_table_rows'] = '\n'.join(
        build_related_autojs_project_table_row(record) for record in sorted_records
    )
    log('Related Auto.js project data ready')

    return rendered_data


related_autojs_project_data = build_related_autojs_project_data()
if REFRESH_METADATA:
    common_data['var_date_introduction_related_autojs_table_data_updated'] = datetime.now(timezone.utc).strftime('%Y/%m/%d')
    Path(readme_root_dir, 'common.json').write_text(
        json.dumps(common_data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n'
    )


def init_languages():
    if language_code_default not in language_codes:
        raise ValueError(f"Default language code \"{language_code_default}\" is not in the language codes list")

    total_languages = len(language_codes)
    log(f'Initializing language data ({total_languages} languages)')
    for language_index, language_code in enumerate(language_codes, start=1):
        log(f'[{language_index}/{total_languages}] Loading language data: {language_code}')
        with open(os.path.join(readme_root_dir, f'lang_{language_code}.json'), 'r', encoding='utf-8') as language_json_file:
            raw_data = json.load(language_json_file)
            prefix = "$var_"
            processed_data = {key[len(prefix):] if key.startswith(prefix) else key: value for key, value in raw_data.items()}

            # 合并共用 JSON 数据
            merged_data = {**common_data, **processed_data}

            # 注入 JDK 版本约束 (来自 version.properties)
            if _jdk_min_supported is not None:
                merged_data['jdk_min_supported'] = str(_jdk_min_supported)
            if _jdk_min_suggested is not None:
                merged_data['jdk_min_suggested'] = str(_jdk_min_suggested)
            if _jdk_max_supported is not None:
                merged_data['jdk_max_supported'] = str(_jdk_max_supported)

            merged_data.update(related_autojs_project_data)

            # 处理日期转换标记
            for key, value in merged_data.items():
                if key.startswith('var_date_'):
                    merged_data[key] = format_date(value, language_code)

            # 渲染动态字符串
            language_content_map[language_code] = render_dynamic_strings(merged_data, merged_data)

        with open(os.path.join(changelog_root_dir, f'lang_{language_code}.json'), 'r', encoding='utf-8') as changelog_json_file:
            raw_data = json.load(changelog_json_file)
            changelog_content = {}
            changelog_data = {}
            for key, value in raw_data.items():
                if key != "$data":
                    changelog_content[key] = value
                else:
                    changelog_data = value

            # 渲染动态字符串
            changelog_content_map[language_code] = render_dynamic_strings(changelog_content, changelog_content)
            changelog_data_map[language_code] = render_dynamic_strings(changelog_data, changelog_content)
        log(f'[{language_index}/{total_languages}] Language data ready: {language_code}')
    log('Language data initialization complete')


def extract_latest_versions(lang_code, lang_content, num_versions=3):
    file_path = os.path.abspath(os.path.join(changelog_files_source_dir, f"CHANGELOG-{lang_code}.md"))
    changelog_content = changelog_content_map[lang_code]

    data = GENERATED[Path(file_path)]

    # 正则模式匹配版本号及其更新内容
    pattern = re.compile(r'(# v(\d+)\.(\d+)\.(\d+).*?)(?=# v|$)', re.DOTALL)

    versions = pattern.findall(data)

    # 获取最新的 num_versions 个版本
    latest_versions = versions[:num_versions]

    filtered_versions = []
    for version, major, minor, patch in latest_versions:
        # 动态生成的版本链接
        version_url_fragment = f'v{major}{minor}{patch}'

        # 构建版本内容的最终字符串
        improvement_label = changelog_content['changelog_label_improvement']
        dependency_text = lang_content['text_changelog_item_dependency']
        # noinspection HttpUrlsUsage
        base_url = 'http://project.autojs6.com/blob/master/app/src/main/assets-app/doc/CHANGELOG.md'

        original_lines = version.strip().split("\n")
        filtered_content = "\n".join(
            line for line in original_lines
            if not line.strip().startswith(f"* `{changelog_content['changelog_label_dependency']}`")
        )
        is_filtered = len(original_lines) != len(filtered_content.split("\n"))

        if is_filtered:
            filtered_versions.append(filtered_content)
            filtered_versions.append(f'* `{improvement_label}` {dependency_text} _[`CHANGELOG.md`]({base_url}#{version_url_fragment})_\n')
        else:
            filtered_versions.append(f'{filtered_content}\n')

    return filtered_versions


def handle_changelog_placeholder(aim_lang_code, aim_content):
    history_str = ""
    for version_name, data in changelog_data_map[aim_lang_code].items():
        if not isinstance(data, dict):
            raise TypeError(f"Expected data to be dict, but got {type(data)} for version {version_name} in language {aim_lang_code}")

        history_str += f"# {version_name}\n\n"
        history_str += f"###### {data['released_date']}"
        if 'released_hint' in data:
            history_str += f" - {data['released_hint']}"
        history_str += "\n\n"
        for simple_key in ['hint', 'feature', 'fix', 'improvement', 'dependency']:
            if simple_key in data:
                for text in data[simple_key]:
                    label = changelog_content_map[aim_lang_code][f'changelog_label_{simple_key}']
                    history_str += f"* `{label}` {text}\n"
        history_str += "\n"

    aim_content['h3_release_history'] = language_content_map[aim_lang_code]['h3_release_history']
    aim_content['placeholder_release_history'] = history_str.rstrip("\n")


def handle_readme_placeholder(aim_lang_code, aim_content):
    new_array = []
    for lang_code, content in language_content_map.items():
        if lang_code == aim_lang_code:
            new_array.append(f" - {content['$name']} [{lang_code}] # {content['text_current_lowercase']}")
        else:
            # noinspection HttpUrlsUsage
            new_array.append(f" - [{content['$name']} [{lang_code}]](http://project.autojs6.com/blob/master/.readme/README-{lang_code}.md)")
    aim_content['placeholder_ul_languages_all_supported'] = "\n".join(new_array)

    aim_content['placeholder_latest_three_release_history'] = "\n".join(extract_latest_versions(aim_lang_code, aim_content)).rstrip("\n")

    aim_content['placeholder_read_more_in_changelog_md'] = f"[CHANGELOG.md](http://project.autojs6.com/blob/master/app/src/main/assets-app/doc/CHANGELOG-{aim_lang_code}.md)"


def render_dynamic_strings(data, placeholder_map):
    """ Recursively render dynamic strings within the JSON data using Jinja environment """
    if isinstance(data, dict):
        return {key: render_dynamic_strings(value, placeholder_map) for key, value in data.items()}
    elif isinstance(data, list):
        return [render_dynamic_strings(item, placeholder_map) for item in data]
    elif isinstance(data, str):
        template = env.from_string(data)
        return template.render(placeholder_map)
    else:
        return data


def generate_changelog_files():
    log(f'Generating changelog files ({len(changelog_content_map)} languages)')
    for lang_code, content in changelog_content_map.items():
        handle_changelog_placeholder(lang_code, content)
        output = template_changelog.render(**content)
        output_file = f'CHANGELOG-{lang_code}.md'
        output_path = os.path.join(changelog_files_source_dir, output_file)

        GENERATED[Path(output_path)] = output

        if lang_code == language_code_default:
            default_output_file = 'CHANGELOG.md'
            default_output_path = os.path.join(changelog_files_source_dir, default_output_file)
            GENERATED[Path(default_output_path)] = output
    log('Changelog files generated')


def generate_readme_files():
    log(f'Generating README files ({len(language_content_map)} languages)')
    for lang_code, content in language_content_map.items():
        handle_readme_placeholder(lang_code, content)
        output = template_readme.render(**content)
        output_file = f'README-{lang_code}.md'
        output_path = os.path.join(project_root_dir, '.readme', output_file)

        GENERATED[Path(output_path)] = output

        if lang_code == language_code_default:
            default_output_file = 'README.md'
            default_output_path = os.path.join(project_root_dir, default_output_file)
            GENERATED[Path(default_output_path)] = output
    log('README files generated')


def main():
    if arguments.check:
        # Keep local helper imports read-only too, including Python bytecode.
        sys.dont_write_bytecode = True
        from check_translations import check_translations
        errors = check_translations(project_root_dir)
        if errors:
            print('CHANGELOG_TRANSLATIONS_ERROR\n' + '\n'.join(errors), file=sys.stderr)
            return 1
    init_languages()
    generate_changelog_files()
    generate_readme_files()
    root = Path(project_root_dir)
    actual = {path for pattern in ('README.md', 'README-*.md', 'CHANGELOG*.md', '.readme/README-*.md', 'app/src/main/assets-app/doc/CHANGELOG*.md') for path in root.glob(pattern)}
    orphans = sorted(actual - set(GENERATED))
    if orphans:
        raise ValueError('Unexpected generated files: ' + ', '.join(str(p.relative_to(root)) for p in orphans))
    if arguments.check:
        drift = [str(path.relative_to(root)) for path, expected in GENERATED.items()
                 if not path.is_file() or path.read_text(encoding='utf-8') != expected]
        if drift:
            print('MARKDOWN_ERROR stale or missing: ' + ', '.join(drift), file=sys.stderr)
            return 1
    else:
        for path, content in GENERATED.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding='utf-8', newline='\n')
    print(f"MARKDOWN_OK languages={len(language_codes)} artifacts={len(GENERATED)} mode={'check' if arguments.check else 'write'}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
