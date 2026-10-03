"""Repeatable device checks for inspector region hand-off and saved-layout behavior.

Usage: python tools/layout-inspector/smoke-region.py SERIAL
Requires an installed debug host with accessibility and overlay permission. Uses Settings,
or the browser URL already configured in /sdcard/inspector-target.txt. Writes only test
diagnostics under build/inspector-smoke and device scratch files, and saves one layout
through the host's normal Save layout menu.
"""
import json
import re
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

SERIAL = sys.argv[1]
PKG = 'org.autojs.autojs6'
OUT = Path('build/inspector-smoke') / ('region-' + SERIAL)
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args, binary=False):
    result = subprocess.run(['adb', '-s', SERIAL, *args], capture_output=True, timeout=40, check=True)
    return result.stdout if binary else result.stdout.decode('utf-8', 'replace').strip()


def tree():
    for _ in range(3):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/inspector-region-ui.xml')
            xml = adb('shell', 'cat', '/sdcard/inspector-region-ui.xml')
            if '<hierarchy' in xml:
                return ET.fromstring(xml[xml.index('<hierarchy'):])
        except (subprocess.CalledProcessError, ET.ParseError):
            pass
        time.sleep(0.5)
    raise AssertionError('No accessibility hierarchy')


def find(key, value, root=None):
    return next((n for n in (tree() if root is None else root).iter('node') if n.get(key) == value), None)


def wait_id(name):
    for _ in range(12):
        node = find('resource-id', PKG + ':id/' + name)
        if node is not None:
            return node
        time.sleep(0.5)
    raise AssertionError('Missing view ' + name)


def box(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))


def tap(node, long=False, phone_insets=False):
    assert node is not None, 'Missing tap target'
    l, t, r, b = box(node)
    if phone_insets and API <= 28:
        # TYPE_PHONE accessibility bounds can briefly omit the window's status-bar offset.
        windows = adb('shell', 'dumpsys', 'window', 'windows')
        for block in windows.split('  Window #')[1:]:
            if not re.search(r'^[^\n]* u0 org\.autojs\.autojs6\}', block):
                continue
            frame = re.search(r'm?[Ff]rame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', block)
            if frame:
                top = int(frame[2])
                density = int(re.search(r'density: (\d+)', adb('shell', 'wm', 'density'))[1]) / 160
                if t < top + 8 * density - 2:
                    t += top
                    b += top
                break
    x, y = str((l + r) // 2), str((t + b) // 2)
    if phone_insets and API <= 28:
        density = int(re.search(r'density: (\d+)', adb('shell', 'wm', 'density'))[1]) / 160
        # These API 24/28 fleet devices have a 24 dp status bar; the TYPE_PHONE surface
        # may be laid out behind it before its window frame is updated.
        y = str(max(int(y), round((50 if node.get('resource-id').endswith('/pill') else 54) * density)))
    print('tap', node.get('resource-id') or node.get('text'), x, y, flush=True)
    adb('shell', 'input', *(['swipe', x, y, x, y, '850'] if long else ['tap', x, y]))
    time.sleep(0.4)


def click_id(name):
    tap(wait_id(name))


def scroll_to(text, direction='down'):
    for _ in range(14):
        root = tree()
        result = find('text', text, root)
        if result is not None:
            return result
        scrolls = [n for n in root.iter('node') if n.get('scrollable') == 'true']
        assert scrolls, 'No scrollable dialog'
        viewport = max(scrolls, key=lambda n: (box(n)[2] - box(n)[0]) * (box(n)[3] - box(n)[1]))
        l, t, r, b = box(viewport)
        x = str((l + r) // 2)
        y1, y2 = str(t + int((b - t) * 0.8)), str(t + int((b - t) * 0.2))
        if direction == 'up':
            y1, y2 = y2, y1
        adb('shell', 'input', 'swipe', x, y1, x, y2, '300')
    raise AssertionError('Missing dialog option ' + text)


def screenshot(name):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))


def run_script(path):
    # uiautomator briefly suppresses other services; do not capture in that interval.
    for _ in range(20):
        if 'Service[label=AutoJs6,' in adb('shell', 'dumpsys', 'accessibility'):
            break
        time.sleep(0.5)
    else:
        raise AssertionError('Host accessibility service is not bound')
    time.sleep(2)
    remote = '/sdcard/' + path.name
    adb('push', str(path), remote)
    adb('shell', 'am', 'start', '-n', PKG + '/org.autojs.autojs.external.open.RunIntentActivity', '--es', 'path', remote)


def expand():
    for attempt in range(4):
        root = tree()
        if find('resource-id', PKG + ':id/filter', root) is not None:
            return
        pill = find('resource-id', PKG + ':id/pill', root)
        if pill is not None:
            tap(pill, phone_insets=attempt % 2 == 1)
        time.sleep(0.5)
    wait_id('filter')


def region_pass(info, offline):
    label = info['labels']
    expand()
    if offline:
        for _ in range(3):
            if wait_id('mode').get('text') == 'Aa':
                break
            click_id('mode')
        click_id('query')
        adb('shell', 'input text ' + shlex.quote(info['query']))
        time.sleep(1)
        assert int(wait_id('count').get('text').split('/')[-1]) > 0
        screenshot('offline-search')
        for _ in range(3):
            if wait_id('query').get('text') != info['query']:
                break
            adb('shell', 'input', 'keyevent', '4')
        click_id('filter')
        assert scroll_to(info['packageName']) is not None
        select = scroll_to(label['text_inspector_select_region'], 'up')
    else:
        click_id('filter')
        select = scroll_to(label['text_inspector_select_region'])
    tap(select)
    wait_id('pill')
    x1, y1, x2, y2 = info['region']
    # Start away from the left edge so gesture navigation does not consume the drag as Back.
    adb('shell', 'input', 'swipe', str(x2), str(y2), str(x1), str(y1), '700')
    time.sleep(0.5)
    expand()
    assert wait_id('filter_badge').get('text') == '1'
    assert int(wait_id('count').get('text').split('/')[-1]) > 0
    screenshot('offline-region' if offline else 'live-region')
    # Expanding focuses the empty query and opens history; collapse it before tapping the canvas.
    click_id('collapse')
    adb('shell', 'input', 'tap', *map(str, info['point']))
    time.sleep(0.5)
    menu = tree()
    if offline:
        assert find('text', label['text_switch_window'], menu) is None
        assert find('text', label['text_save_layout_to_file'], menu) is None
    tap(find('text', label['text_show_layout_hierarchy'], menu))
    assert wait_id('filter_badge').get('text') == '1'
    screenshot('offline-hierarchy' if offline else 'live-hierarchy')
    click_id('filter')
    tap(scroll_to(label['text_inspector_clear_region']))
    adb('shell', 'input', 'keyevent', '4')
    assert find('resource-id', PKG + ':id/filter_badge') is None
    print(('offline' if offline else 'live') + ': region, nonzero matches, menu, hand-off, clear PASS', flush=True)


API = int(adb('shell', 'getprop', 'ro.build.version.sdk'))
for _ in range(3):
    adb('shell', 'input', 'keyevent', '25')
run_script(Path(__file__).with_name('region-target.js'))
wait_id('pill')
info = json.loads(adb('shell', 'cat', '/sdcard/inspector-region-target.json'))
region_pass(info, offline=False)
tap(wait_id('dataItemName'), long=True)
tap(find('text', info['labels']['text_save_layout_to_file']))
time.sleep(3)
opener = OUT / 'inspector-open-region-layout.js'
opener.write_text('''let storage = Packages.org.autojs.autojs.core.accessibility.LayoutSnapshotFiles.INSTANCE;
let entries = storage.directory().listFiles();
let newest = null;
for (let i = 0; i < entries.length; i++) {
    let file = entries[i];
    if (storage.isLayoutFile(file.getName()) && (newest === null || file.lastModified() > newest.lastModified())) newest = file;
}
if (!newest) throw new Error('No saved layout');
let themed = new android.view.ContextThemeWrapper(context, org.autojs.autojs6.R.style.AppTheme);
Packages.org.autojs.autojs.ui.floating.layoutinspector.LayoutSnapshotViewer.INSTANCE.open(themed, newest.getPath());
''', encoding='utf-8')
run_script(opener)
wait_id('pill')
region_pass(info, offline=True)
adb('shell', 'input', 'keyevent', '25')
(OUT / 'result.json').write_text(json.dumps({'result': 'PASS', 'liveAndOffline': True, 'target': info}, ensure_ascii=False, indent=2), encoding='utf-8')
