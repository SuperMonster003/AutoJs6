# -*- coding: utf-8 -*-
import locale
import sys

# 设置语言环境为 UTF-8
locale.setlocale(locale.LC_ALL, '')
encoding = locale.getpreferredencoding()
if encoding.lower() != 'utf-8':
    # 强制使用 UTF-8 编码
    locale.setlocale(locale.LC_ALL, 'en_US.UTF-8')
    # 如果 locale 无法设置，使用以下方式
    # sys.stdout.reconfigure(encoding='utf-8')
    # sys.stderr.reconfigure(encoding='utf-8')

from jinja2 import Environment, FileSystemLoader, StrictUndefined
from collections import defaultdict
import os
import json
import re
from datetime import datetime

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
    "ru": "%d %B %Y г.",  # 俄文
    "ar": "%d %B %Y"  # 阿拉伯文
}

project_root_dir = os.path.abspath(os.path.join(os.getcwd(), os.pardir))
changelog_files_source_dir = os.path.join(project_root_dir, 'app', 'src', 'main', 'assets-app', 'doc')
changelog_root_dir = os.path.join(project_root_dir, '.changelog')
readme_root_dir = os.path.join(project_root_dir, '.readme')

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

# 读取模板文件
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

            # 在 formatted_date 中每个非字母和非数字字符前后增加空格（如果没有空格）
            formatted_date_with_spaces = re.sub(r'(\d+)', r' \1 ', formatted_date).strip()
            formatted_date_with_spaces = re.sub(r'\s+', ' ', formatted_date_with_spaces)

            # 去掉前导零
            formatted_date_no_leading_zeros = re.sub(r'\b0+(\d)', r'\1', formatted_date_with_spaces)

            return formatted_date_no_leading_zeros
        except ValueError as e:
            print(f"Date format error: {e}")
    return date_str


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
        formatted_version = (
            f'{version.strip()}\n'
            f'* `{improvement_label}` {dependency_text} '
            f'_[`CHANGELOG.md`]({base_url}#{version_url_fragment})_\n'
        )

        # 去除以 `依赖` label 开头的行
        filtered_version = "\n".join(
            line for line in formatted_version.strip().split("\n")
            if not line.strip().startswith(f"* `{changelog_content['changelog_label_dependency']}`")
        )
        filtered_versions.append(filtered_version + '\n')

    return filtered_versions


def handle_changelog_placeholder(aim_lang_code, aim_content):
    histories_str = ""
    for version_name, data in changelog_data_map[aim_lang_code].items():
        if not isinstance(data, dict):
            raise TypeError(f"Expected data to be dict, but got {type(data)} for version {version_name} in language {aim_lang_code}")

        histories_str += f"# {version_name}\n\n"
        histories_str += f"###### {data['released_date']}"
        if 'released_hint' in data:
            histories_str += f" - {data['released_hint']}"
        histories_str += "\n\n"
        for simple_key in ['hint', 'feature', 'fix', 'improvement', 'dependency']:
            if simple_key in data:
                for text in data[simple_key]:
                    label = changelog_content_map[aim_lang_code][f'changelog_label_{simple_key}']
                    histories_str += f"* `{label}` {text}\n"
        histories_str += "\n"

    aim_content['h3_version_histories'] = language_content_map[aim_lang_code]['h3_version_histories']
    aim_content['placeholder_version_histories'] = histories_str.rstrip("\n")


def handle_readme_placeholder(aim_lang_code, aim_content):
    new_array = []
    for lang_code, content in language_content_map.items():
        if lang_code == aim_lang_code:
            new_array.append(f" - {content['$name']} [{lang_code}] # {content['text_current_lowercase']}")
        else:
            # noinspection HttpUrlsUsage
            new_array.append(f" - [{content['$name']} [{lang_code}]](http://project.autojs6.com/blob/master/.readme/README-{lang_code}.md)")
    aim_content['placeholder_ul_languages_all_supported'] = "\n".join(new_array)

    aim_content['placeholder_latest_three_version_histories'] = "\n".join(extract_latest_versions(aim_lang_code, aim_content)).rstrip("\n")

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
