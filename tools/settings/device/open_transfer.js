// Opens the page "Manage AutoJs6 settings" from inside the app (docs/settings/ROADMAP.md P6.2 - P6.4, E3); the page is
// not exported. AJ6_WIZARD ("export" or null) opens a wizard with it. AJ6_REMEMBERED changes the remembered selection
// of the export wizard (P6.3) instead of opening the page when it has no "before":
//   {before: ids}  records the remembered selection ("original" line), sets it to ids and opens the page;
//   {after: ids}   sets it to the selectable categories not in ids (the open wizard must not pick that up);
//   {restore: text} writes the recorded text back (null removes the state).
// Run by tools/settings/device_transfer_check.py.
// zh-CN: 在应用内打开 "管理 AutoJs6 设置" 页面 (ROADMAP P6.2 - P6.4, E3), 该页面未导出. AJ6_WIZARD ("export" 或 null) 同时打开向导.
// AJ6_REMEMBERED 修改导出向导记住的勾选 (P6.3), 不含 "before" 时不打开页面: before 记录原值 ("original" 行), 设为 ids 并打开页面;
// after 设为不在 ids 中的可选分类 (已打开的向导不应采用); restore 写回记录的文本 (null 即删除).

var MARK = 'AJ6SET';

function rememberedKey() {
    return Packages.org.autojs.autojs.core.pref.keys.MiscStateKeys.SETTINGS_EXPORT_CATEGORIES;
}

try {
    var remembered = AJ6_REMEMBERED;
    if (remembered && remembered.before) {
        var original = rememberedKey().storedText();
        console.log(MARK + ' original ' + encodeURIComponent(JSON.stringify(original === null ? null : String(original))));
        rememberedKey().setStoredText(JSON.stringify(remembered.before), true);
        console.log(MARK + ' PASS remembered-before ' + remembered.before.join(','));
    } else if (remembered && remembered.after) {
        var all = Packages.org.autojs.autojs.core.pref.SettingCategory.ALL;
        var complement = [];
        for (var i = 0; i < all.size(); i++) {
            var category = all.get(i);
            var id = String(category.getId());
            if (!category.isData() && remembered.after.indexOf(id) < 0) complement.push(id);
        }
        rememberedKey().setStoredText(JSON.stringify(complement), true);
        console.log(MARK + ' PASS remembered-after ' + complement.length + ' other categories');
    } else if (remembered && 'restore' in remembered) {
        rememberedKey().setStoredText(remembered.restore, true);
        console.log(MARK + ' PASS remembered-restored ' + (remembered.restore === null ? 'absent' : remembered.restore.length + ' chars'));
    }
    if (!remembered || remembered.before) {
        var intent = new android.content.Intent().setClassName(context.getPackageName(), AJ6_ACTIVITY);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        if (AJ6_WIZARD) intent.putExtra('wizard', AJ6_WIZARD);
        context.startActivity(intent);
        console.log(MARK + ' PASS page-started' + (AJ6_WIZARD ? ' with the ' + AJ6_WIZARD + ' wizard' : ''));
    }
} catch (e) {
    console.log(MARK + ' FAIL open-transfer threw ' + e);
}
console.log(MARK + ' done');
