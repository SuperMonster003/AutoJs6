# -*- coding: utf-8 -*-
import locale

# 设置语言环境为 UTF-8
locale.setlocale(locale.LC_ALL, '')
encoding = locale.getpreferredencoding()
if encoding.lower() != 'utf-8':
    # 强制使用 UTF-8 编码
    locale.setlocale(locale.LC_ALL, 'en_US.UTF-8')

from jinja2 import Environment, FileSystemLoader, StrictUndefined
from collections import defaultdict
from pprint import pformat
import os
import json
import re
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

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

RELATED_AUTOJS_PROJECT_DATES_START_MARKER = "# BEGIN RELATED_AUTOJS_PROJECT_DATES"
RELATED_AUTOJS_PROJECT_DATES_END_MARKER = "# END RELATED_AUTOJS_PROJECT_DATES"

# BEGIN RELATED_AUTOJS_PROJECT_DATES
RELATED_AUTOJS_PROJECT_DATES = {'hyb1996_auto_js': {'repo': 'https://github.com/hyb1996/Auto.js',
                     'created': '2017/01/27',
                     'recent_active': '2020/03/13'},
 'auto_js_pro_7': {'created': '2019/03/13', 'recent_active': '2019/07/08'},
 'auto_js_pro_8': {'created': '2019/10/13', 'recent_active': '2021/07/24'},
 'tonyjiangwj_auto_js': {'repo': 'https://github.com/TonyJiangWJ/Auto.js',
                         'created': '2019/11/21',
                         'recent_active_cached': '2025/08/22'},
 'kkevsekk1_autox': {'repo': 'https://github.com/kkevsekk1/AutoX',
                     'created': '2020/07/24',
                     'recent_active': '2025/01/07',
                     'recent_active_status': 'inaccessible'},
 'auto_js_pro_9': {'created': '2021/03/28', 'recent_active': '2023/02/09'},
 'supermonster003_autojs6': {'repo': 'https://github.com/SuperMonster003/AutoJs6',
                             'created': '2021/12/01',
                             'recent_active_cached': '2026/03/16'},
 'blackcd318_autojs4': {'repo': 'https://github.com/blackcd318/autojs4',
                        'created': '2021/12/15',
                        'recent_active': '2023/07/31'},
 'kkevsekk1_autox_v6': {'repo': 'https://github.com/kkevsekk1/AutoX',
                        'created': '2022/05/26',
                        'recent_active': '2025/01/07',
                        'recent_active_status': 'inaccessible'},
 'openautojs_openautojs': {'repo': 'https://github.com/openautojs/openautojs',
                           'created': '2023/02/17',
                           'recent_active': '2023/04/16'},
 'supermonster003_autojs4': {'repo': 'https://github.com/SuperMonster003/AutoJs4',
                             'created': '2023/04/11',
                             'recent_active_cached': '2026/03/15'},
 'aiselp_autox': {'repo': 'https://github.com/aiselp/AutoX',
                  'created': '2024/04/21',
                  'recent_active_cached': '2026/06/13'},
 'ozobiozobi_autoxjs_v6_ozobi': {'repo': 'https://github.com/ozobiozobi/Autoxjs_v6_ozobi',
                                 'created': '2024/10/01',
                                 'recent_active_cached': '2026/04/16'},
 'autox_community_autox': {'repo': 'https://github.com/autox-community/AutoX',
                           'created': '2025/03/30',
                           'recent_active_cached': '2025/11/29'}}
# END RELATED_AUTOJS_PROJECT_DATES

RELATED_AUTOJS_PROJECT_TABLE_ENTRIES = {
    'hyb1996_auto_js': {
        'project': '[Auto.js](https://github.com/hyb1996/Auto.js)',
        'app_name': '[Auto.js](https://github.com/TonyJiangWJ/Auto.js/commit/268ec8895bbfa28fc7715154eb15b1c1eaaefd14#diff-5e01f7d37a66e4ca03deefc205d8e7008661cdd0284a05aaba1858e6b7bf9103R2)',
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
        'app_name': '[Auto.js&#160;M](https://github.com/TonyJiangWJ/Auto.js/commit/268ec8895bbfa28fc7715154eb15b1c1eaaefd14#diff-5e01f7d37a66e4ca03deefc205d8e7008661cdd0284a05aaba1858e6b7bf9103R2)',
        'main_developers': '[TonyJiangWJ](https://github.com/TonyJiangWJ)',
    },
    'kkevsekk1_autox': {
        'project': '[AutoX](https://github.com/kkevsekk1/AutoX)',
        'app_name': '[Autox.js](https://github.com/kkevsekk1/AutoX/commit/8143e4ed893d4af05d22aa791b83a962f9959873#diff-5e01f7d37a66e4ca03deefc205d8e7008661cdd0284a05aaba1858e6b7bf9103R2)',
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
        'app_name': '[AutoJs6](https://github.com/SuperMonster003/AutoJs6/commit/a8ce1b9acb541e9736c33134be3194c3148a15a3#diff-833a46a97033e77558372a2dce103fd6fee29aaaa899f610022a7aece592ee7bR27)',
        'main_developers': '[SuperMonster003](https://github.com/SuperMonster003)',
    },
    'blackcd318_autojs4': {
        'project': '[autojs4](https://github.com/blackcd318/autojs4)',
        'app_name': 'Auto.js',
        'main_developers': '[blackcd318](https://github.com/blackcd318)',
    },
    'kkevsekk1_autox_v6': {
        'project': '[AutoX](https://github.com/kkevsekk1/AutoX)',
        'app_name': '[Autox.js&#160;v6](https://github.com/kkevsekk1/AutoX/commit/8b6776cff8b0fca4be4a52719b7d7d07c0a058f3#diff-51a0b488f963eb0be6c6599bf5df497313877cf5bdff3950807373912ac1cdc9R130)',
        'app_name_style': 'white-space:pre',
        'main_developers': '[wilinz](https://github.com/wilinz) / [aiselp](https://github.com/aiselp)',
    },
    'openautojs_openautojs': {
        'project': '[openautojs](https://github.com/openautojs/openautojs)',
        'app_name': '[OpenAuto.js](https://github.com/openautojs/openautojs/commit/a11feaad025154de9b453ba70b49e94a6ca8b48a#diff-7d757295fcec3b37c258337e048644c258233d79259152e77baa6d36bb0ec418R2)',
        'app_name_style': 'white-space:pre',
        'main_developers': '[openautojs](https://github.com/openautojs)',
    },
    'supermonster003_autojs4': {
        'project': '[AutoJs4](https://github.com/SuperMonster003/AutoJs4)',
        'app_name': '[AutoJs4](https://github.com/SuperMonster003/AutoJs4/commit/8de11699b608be1c1f49f0f10e2e2826f8c77978)',
        'main_developers': '[SuperMonster003](https://github.com/SuperMonster003)',
    },
    'aiselp_autox': {
        'project': '[AutoX](https://github.com/aiselp/AutoX)',
        'app_name': '[Autox.js&#160;v7](https://github.com/aiselp/AutoX/commit/484491fd5fe12b8203d0b09c181eb0f471c0ea9f#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R120)',
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
        'app_name': '[Autox.js&#160;v6](https://github.com/autox-community/AutoX/commit/8b6776cff8b0fca4be4a52719b7d7d07c0a058f3#diff-51a0b488f963eb0be6c6599bf5df497313877cf5bdff3950807373912ac1cdc9R130)',
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
        print(f'[warn] version.properties not found at {props_path}, skip injecting JDK constraints')
    except Exception as e:
        print(f'[warn] Failed to read version.properties: {e}')
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
    """
    读取 .readme/template_readme.md, 将 Android Studio 与 IntelliJ IDEA 的徽标版本替换为
    version.properties 中的最小支持版本:
      - MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION
      - MIN_SUPPORTED_INTELLIJ_IDEA_IDE_VERSION
    """
    template_path = os.path.join(readme_root_dir, 'template_readme.md')
    try:
        with open(template_path, 'r', encoding='utf-8') as f:
            data = f.read()
    except FileNotFoundError:
        print(f'[warn] template_readme.md not found at {template_path}, skip badge update')
        return
    except Exception as e:
        print(f'[warn] Failed to read template_readme.md: {e}')
        return

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

    if data == original:
        return

    try:
        with open(template_path, 'w', encoding='utf-8') as f:
            f.write(data)
            print(f'Updated badge(s) in {template_path}')
    except Exception as e:
        print(f'[warn] Failed to write template_readme.md: {e}')

# 执行模板内徽标版本更新, 并清空 Jinja2 缓存后再获取模板
update_readme_badge_versions()
try:
    env.cache.clear()  # 确保重新读取更新后的模板文件
except Exception:
    pass

# 读取模板文件 (在更新徽标版本之后)
template_readme = env.get_template('template_readme.md')
template_changelog = env.get_template('template_changelog.md')

language_content_map = {}
changelog_content_map = {}
changelog_data_map = defaultdict(dict)

# 读取共用 JSON 文件
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
        # 获取当前日期并格式化为 YYYY/MM/DD
        date_str = datetime.now().strftime("%Y/%m/%d")

    if lang_code in date_formats:
        try:
            date_obj = datetime.strptime(date_str, "%Y/%m/%d")
            formatted_date = date_obj.strftime(date_formats[lang_code])

            # 在 formatted_date 中每个非字母和非数字字符前后增加空格 (如果没有空格)
            formatted_date_with_spaces = re.sub(r'(\d+)', r' \1 ', formatted_date).strip()
            # 将数字与逗号之间的空格去除
            formatted_date_with_spaces = re.sub(r'(\d)\s+,', r'\1,', formatted_date_with_spaces)
            formatted_date_with_spaces = re.sub(r'\s+', ' ', formatted_date_with_spaces)

            # 去掉前导零
            formatted_date_no_leading_zeros = re.sub(r'\b0+(\d)', r'\1', formatted_date_with_spaces)

            return formatted_date_no_leading_zeros
        except ValueError as e:
            print(f"Date format error: {e}")
    return date_str


def parse_github_repo_path(repo_url: str):
    match = re.search(r'github\.com[:/]([^/\s?#]+)/([^/\s?#]+)', repo_url or '', re.IGNORECASE)
    if not match:
        return None
    owner = match.group(1)
    repo = re.sub(r'\.git$', '', match.group(2))
    return owner, repo


def request_github_json(url: str):
    request = Request(
        url,
        headers={
            'Accept': 'application/vnd.github+json',
            'User-Agent': 'AutoJs6-readme-generator',
        },
    )
    try:
        with urlopen(request, timeout=15) as response:
            data = json.loads(response.read().decode('utf-8'))
            return data, response.headers
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError, OSError) as e:
        print(f'[warn] Failed to fetch GitHub data from {url}: {e}')
        return None, {}


def parse_link_header(link_header: str) -> dict:
    links = {}
    for part in (link_header or '').split(','):
        match = re.match(r'\s*<([^>]+)>;\s*rel="([^"]+)"', part)
        if match:
            links[match.group(2)] = match.group(1)
    return links


def github_commit_item_to_date(commit_item) -> str:
    commit = (commit_item or {}).get('commit') or {}
    commit_date = ((commit.get('committer') or {}).get('date')
                   or (commit.get('author') or {}).get('date'))
    if not commit_date:
        return ''
    try:
        return datetime.fromisoformat(commit_date.replace('Z', '+00:00')).astimezone(timezone.utc).strftime('%Y/%m/%d')
    except ValueError:
        return ''


def fetch_github_commit_date(repo_url: str, oldest: bool = False) -> str:
    repo_path = parse_github_repo_path(repo_url)
    if not repo_path:
        return ''

    owner, repo = repo_path
    base_url = f"https://api.github.com/repos/{quote(owner, safe='')}/{quote(repo, safe='')}/commits?per_page=1"
    data, headers = request_github_json(base_url)
    if not isinstance(data, list) or not data:
        return ''

    if oldest:
        last_url = parse_link_header(headers.get('Link', '')).get('last')
        if last_url:
            oldest_data, _ = request_github_json(last_url)
            if isinstance(oldest_data, list) and oldest_data:
                return github_commit_item_to_date(oldest_data[0])

    return github_commit_item_to_date(data[0])


def persist_related_autojs_project_dates():
    source_path = os.path.abspath(__file__)
    try:
        with open(source_path, 'r', encoding='utf-8') as f:
            source = f.read()
    except OSError as e:
        print(f'[warn] Failed to read {source_path}: {e}')
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
        print(f'Updated related Auto.js project date cache in {source_path}')
    except OSError as e:
        print(f'[warn] Failed to write {source_path}: {e}')


def format_table_date(date_str: str) -> str:
    if not date_str:
        return '-'
    return f'`{date_str}`'


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
        related_autojs_table_cell(format_table_date(record['created_date'])),
        related_autojs_table_cell(format_table_date(record['recent_active_date'])),
        related_autojs_table_cell(calc_years_between(record['created_date'], record['recent_active_date'])),
    ]
    return f"| {' | '.join(cells)} |"


def build_related_autojs_project_data() -> dict:
    changed = False
    current_date = datetime.now().strftime('%Y/%m/%d')
    rendered_data = {}
    table_records = []

    for project_key, date_data in RELATED_AUTOJS_PROJECT_DATES.items():
        repo_url = date_data.get('repo', '')
        created_date = date_data.get('created', '')
        if not created_date:
            created_date = fetch_github_commit_date(repo_url, oldest=True)
            if created_date:
                date_data['created'] = created_date
                changed = True

        fixed_recent_active_date = date_data.get('recent_active', '')
        if fixed_recent_active_date:
            recent_active_date = fixed_recent_active_date
        else:
            fetched_recent_active_date = fetch_github_commit_date(repo_url)
            if fetched_recent_active_date:
                recent_active_date = fetched_recent_active_date
                if date_data.get('recent_active_cached') != fetched_recent_active_date:
                    date_data['recent_active_cached'] = fetched_recent_active_date
                    changed = True
            else:
                recent_active_date = date_data.get('recent_active_cached') or current_date

        prefix = f'data_related_autojs_project_{project_key}'
        rendered_data[f'{prefix}_created_on'] = format_table_date(created_date)
        rendered_data[f'{prefix}_recent_active_on'] = format_table_date(recent_active_date)
        rendered_data[f'{prefix}_active_maintenance_phase'] = calc_years_between(created_date, recent_active_date)
        table_records.append({
            'project_key': project_key,
            'created_date': created_date,
            'recent_active_date': recent_active_date,
        })

    if changed:
        persist_related_autojs_project_dates()

    sorted_records = sorted(
        table_records,
        key=lambda item: parse_date_for_sort(item['recent_active_date']),
        reverse=True,
    )
    rendered_data['placeholder_related_autojs_table_rows'] = '\n'.join(
        build_related_autojs_project_table_row(record) for record in sorted_records
    )

    return rendered_data


related_autojs_project_data = build_related_autojs_project_data()


def init_languages():
    if language_code_default not in language_codes:
        raise ValueError(f"Default language code \"{language_code_default}\" is not in the language codes list")

    for language_code in language_codes:
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


def extract_latest_versions(lang_code, lang_content, num_versions=3):
    file_path = os.path.abspath(os.path.join(changelog_files_source_dir, f"CHANGELOG-{lang_code}.md"))
    changelog_content = changelog_content_map[lang_code]

    with open(file_path, 'r', encoding='utf-8') as file:
        data = file.read()

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
    for lang_code, content in changelog_content_map.items():
        handle_changelog_placeholder(lang_code, content)
        output = template_changelog.render(**content)
        output_file = f'CHANGELOG-{lang_code}.md'
        output_path = os.path.join(changelog_files_source_dir, output_file)

        with open(output_path, 'w', encoding='utf-8') as file:
            file.write(output)
            print(f'Generated {output_file} at {output_path}')

        if lang_code == language_code_default:
            default_output_file = 'CHANGELOG.md'
            default_output_path = os.path.join(changelog_files_source_dir, default_output_file)
            with open(default_output_path, 'w', encoding='utf-8') as file:
                file.write(output)
                print(f'Generated {default_output_file} at {default_output_path}')


def generate_readme_files():
    for lang_code, content in language_content_map.items():
        handle_readme_placeholder(lang_code, content)
        output = template_readme.render(**content)
        output_file = f'README-{lang_code}.md'
        output_path = os.path.join(project_root_dir, '.readme', output_file)

        with open(output_path, 'w', encoding='utf-8') as file:
            file.write(output)
            print(f'Generated {output_file} at {output_path}')

        if lang_code == language_code_default:
            default_output_file = 'README.md'
            default_output_path = os.path.join(project_root_dir, default_output_file)
            with open(default_output_path, 'w', encoding='utf-8') as file:
                file.write(output)
                print(f'Generated {default_output_file} at {default_output_path}')


init_languages()
generate_changelog_files()
generate_readme_files()
