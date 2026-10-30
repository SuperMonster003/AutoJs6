// The device cases of colors.theme, gallery, files.batchRename and the speech analysis members (api-coverage P3.5 / P4.5),
// read by device_plugin_family.py. [id, body]: the body of a function that runs in Rhino, in Node and in Bun, with the
// helpers below in scope. In Rhino AW(x) is x and name@(...) is name(...); in Node and Bun AW(x) awaits x, name@(...) is
// nameAsync(...) and each "function (" is an async function. An id ending with "~" is a function (done) that waits for
// events and is not rewritten: it uses the forms that are the same in the three runtimes.
var PLUGIN_FAMILY_CASES = [
    // A run starts by setting the color it read, so that what current() reports does not depend on the run before.
    ["theme.get", "S.color = AW(colors.theme.get()); AW(colors.theme.set(S.color)); return typeof S.color;"],
    ["theme.libraries", "S.libraries = libraries(AW(colors.theme.libraries())); return S.libraries;"],
    ["theme.current", "return plain(AW(colors.theme.current()));"],
    ["theme.set", "AW(colors.theme.set('#336699')); return AW(colors.theme.get());"],
    ["theme.set.options", "AW(colors.theme.set('#ff336699', {accent: '#112233', dark: '#445566'})); return plain(AW(colors.theme.current()));"],
    ["theme.set.badOptions", "return T(function () { return AW(colors.theme.set('#336699', 1)); });"],
    ["theme.set.badColor", "return T(function () { return AW(colors.theme.set({})); });"],
    ["theme.reset", "AW(colors.theme.reset()); return AW(colors.theme.get());"],
    ["theme.activate", "S.library = intelligent(S.libraries); if (S.library === null) return null; AW(colors.theme.activate(S.library)); var c = AW(colors.theme.current()); return [Number(c.libraryId), !!c.intelligent];"],
    ["theme.state", "return T(function () { return AW(colors.theme.state('aj6-none')); });"],
    ["theme.next", "return S.library === null ? null : typeof AW(colors.theme.next());"],
    ["theme.deactivate", "return AW(colors.theme.deactivate());"],
    ["theme.idle", "return [AW(colors.theme.deactivate()), AW(colors.theme.next())];"],
    ["theme.activate.bad", "return T(function () { return AW(colors.theme.activate(-1)); });"],
    ["theme.state.null", "return T(function () { return AW(colors.theme.state(null)); });"],
    ["theme.restore", "AW(colors.theme.set(S.color)); return AW(colors.theme.get()) === S.color;"],

    ["gallery.isAvailable", "return AW(gallery.isAvailable());"],
    ["gallery.isAvailableAsync", "return AW(gallery.isAvailable@());"],
    ["gallery.GalleryError", "var e = new gallery.GalleryError('m', 'X'); return [String(e.name), String(e.code), String(e.message), String(e), e instanceof Error];"],
    ["gallery.convert", "return written(AW(gallery.convert(D + '/a.png', {format: 'jpeg', quality: 80, background: '#ffffff'})));"],
    ["gallery.convertAsync", "return written(AW(gallery.convert@(D + '/a.png', {output: D + '/out/a.webp', format: 'webp', percent: 50})));"],
    ["gallery.convert.exists", "return T(function () { return AW(gallery.convert(D + '/a.png', {output: D + '/out/a.webp'})); });"],
    ["gallery.convert.overwrite", "return written(AW(gallery.convert(D + '/a.png', {output: D + '/out/a.webp', overwrite: true, format: 'webp'})));"],
    ["gallery.convert.missing", "return T(function () { return AW(gallery.convert(D + '/none.png')); });"],
    ["gallery.convert.unknownKey", "return T(function () { return AW(gallery.convert(D + '/a.png', {nope: 1})); });"],
    ["gallery.convert.noPath", "return T(function () { return AW(gallery.convert()); });"],
    ["gallery.pick.badOptions", "return T(function () { return AW(gallery.pick({multiple: true, maxCount: 0})); });"],
    ["gallery.edit.missing", "return T(function () { return AW(gallery.edit(D + '/none.png')); });"],
    ["gallery.editAsync.exists", "return T(function () { return AW(gallery.edit@(D + '/a.png', {output: D + '/out/a.webp'})); });"],
    ["gallery.view.limit", "return T(function () { return AW(gallery.view(repeated(D + '/a.png', 129))); });"],
    ["gallery.viewAsync.missing", "return T(function () { return AW(gallery.view@(D + '/none.png')); });"],
    ["gallery.view~", "function (done) {\n"
        + "    var log = [];\n"
        + "    THEN(function () { return gallery.view([D + '/a.png', D + '/b.png'], {index: 1}); }, function (session) {\n"
        + "        log.push('state ' + session.state, 'count ' + session.count, 'isOpen ' + session.isOpen);\n"
        + "        session.on('open', function () { log.push('open'); });\n"
        + "        session.on('page', function (page) {\n"
        + "            log.push('page ' + page.index + ' ' + base(page.path));\n"
        + "            if (page.index === 1) setTimeout(function () { session.close(); }, 500);\n"
        + "        });\n"
        + "        session.on('close', function (event) {\n"
        + "            log.push('close ' + event.reason, 'state ' + session.state, 'isClosed ' + session.isClosed, 'reason ' + session.reason, 'index ' + session.index, 'path ' + base(session.path), String(session));\n"
        + "            done(log);\n"
        + "        });\n"
        + "    }, function (e) { done('rejected ' + e.code + ' ' + e.message); });\n"
        + "}"],
    ["gallery.viewAsync~", "function (done) {\n"
        + "    gallery.viewAsync(D + '/a.png').then(function (session) {\n"
        + "        var state = session.state;\n"
        + "        session.on('close', function (event) { done([state, 'close ' + event.reason, session.count]); });\n"
        + "        setTimeout(function () { session.close({finish: true}); }, 500);\n"
        + "    }, function (e) { done('rejected ' + e.code + ' ' + e.message); });\n"
        + "}"],
    // A new viewer ends the picker and the editor, so these two need nobody at the device (SESSION_REPLACED).
    ["gallery.pick~", "function (done) {\n"
        + "    var out = {};\n"
        + "    gallery.pickAsync({title: 'aj6'}).then(function () { out.pick = 'answered'; }, function (e) { out.pick = String(e.code); });\n"
        + "    setTimeout(function () {\n"
        + "        gallery.viewAsync(D + '/a.png').then(function (session) {\n"
        + "            session.on('close', function () { setTimeout(function () { done(out); }, 300); });\n"
        + "            setTimeout(function () { session.close(); }, 500);\n"
        + "        }, function (e) { done('rejected ' + e.code + ' ' + e.message); });\n"
        + "    }, 2500);\n"
        + "}"],
    ["gallery.edit~", "function (done) {\n"
        + "    var out = {};\n"
        + "    gallery.editAsync(D + '/a.png', {output: D + '/out/edited.png', format: 'png'}).then(function () { out.edit = 'answered'; }, function (e) { out.edit = String(e.code); });\n"
        + "    setTimeout(function () {\n"
        + "        gallery.viewAsync(D + '/a.png').then(function (session) {\n"
        + "            session.on('close', function () { setTimeout(function () { done(out); }, 300); });\n"
        + "            setTimeout(function () { session.close(); }, 500);\n"
        + "        }, function (e) { done('rejected ' + e.code + ' ' + e.message); });\n"
        + "    }, 2500);\n"
        + "}"],

    ["rename.isAvailable", "return AW(files.batchRename.isAvailable());"],
    ["rename.BatchRenameError", "var e = new files.batchRename.BatchRenameError('m', 'X'); return [String(e.name), String(e.code), String(e.message), String(e), e instanceof Error];"],
    ["rename.preview", "return plain(AW(files.batchRename.preview('IMG_0001.JPG', {search: 'IMG_', replace: 'Photo_', format: 'lower'})));"],
    ["rename.preview.regexp", "return plain(AW(files.batchRename.preview({name: 'scan.pdf', lastModified: new Date(2026, 9, 15)}, {search: /^/, replace: '$YYYY-$MM-$DD_', target: 'name'})));"],
    ["rename.previewAsync", "return allPlain(AW(files.batchRename.preview@(['a.txt', 'b.txt', 'c.md'], {search: /^.+$/, replace: 'note_${start=1}', target: 'name', enumerate: true})));"],
    ["rename.preview.badName", "return T(function () { return AW(files.batchRename.preview('a/b', {search: 'a', replace: 'b'})); });"],
    ["rename.plan", "S.plan = AW(files.batchRename.plan(D + '/r', {search: 'IMG_', replace: 'Photo_', includeFolders: false})); return [S.plan.rejected === true, items(S.plan)];"],
    ["rename.apply", "var n = 0; S.result = AW(files.batchRename.apply(S.plan, {onProgress: function (done, total) { n = total; }})); return [n, plain(S.result.counts), !!S.result.undoable];"],
    ["rename.apply.stale", "return T(function () { return AW(files.batchRename.apply(S.plan)); });"],
    ["rename.plan.after", "return items(AW(files.batchRename.plan(D + '/r', {search: 'Photo_', replace: 'IMG_', includeFolders: false})));"],
    ["rename.history", "var h = A(AW(files.batchRename.history(1))); return [h.length, String(h[0].source), h[0].batchId === S.result.batchId, !!h[0].undoable];"],
    ["rename.history.bad", "return T(function () { return AW(files.batchRename.history(-1)); });"],
    ["rename.undo", "var u = AW(files.batchRename.undo(S.result)); return [String(u.state), plain(u.counts)];"],
    ["rename.undo.again", "return T(function () { return AW(files.batchRename.undo(S.result)); });"],
    ["rename.planAsync", "S.plan = AW(files.batchRename.plan@(D + '/r', {search: 'notes', replace: 'memo', includeFolders: false})); return items(S.plan);"],
    ["rename.applyAsync", "S.result2 = AW(files.batchRename.apply@(S.plan)); return plain(S.result2.counts);"],
    ["rename.undoAsync", "var u = AW(files.batchRename.undo@(S.result2.batchId)); return [String(u.state), plain(u.counts)];"],
    ["rename.callable", "S.result3 = AW(files.batchRename(D + '/r', {search: 'txt', replace: 'md', target: 'extension', includeFolders: false})); return plain(S.result3.counts);"],
    ["rename.deleteHistory", "var ok = [AW(files.batchRename.deleteHistory(S.result3)), AW(files.batchRename.deleteHistory(S.result3.batchId)), AW(files.batchRename.deleteHistory(S.result2.batchId)), AW(files.batchRename.deleteHistory(S.result.batchId))]; return ok;"],
    ["rename.deleteHistory.bad", "return T(function () { return AW(files.batchRename.deleteHistory({})); });"],
    ["rename.clearHistory.bad", "return T(function () { return AW(files.batchRename.clearHistory({source: 'nope'})); });"],
    ["rename.clearHistory", "return AW(files.batchRename.clearHistory({source: 'explorer'}));"],
    ["rename.importPresets", "var r = AW(files.batchRename.importPresets(PRESET_DOCUMENT, {onConflict: 'overwrite'})); return [A(r.imported).length, A(r.skipped).length, A(r.rejected).length];"],
    ["rename.importPresetsAsync", "var r = AW(files.batchRename.importPresets@(JSON.stringify(PRESET_DOCUMENT), {onConflict: 'skip'})); return [A(r.imported).length, A(r.skipped).length, A(r.rejected).length];"],
    ["rename.presets", "return presetNames(AW(files.batchRename.presets()));"],
    ["rename.presetsAsync", "return presetNames(AW(files.batchRename.presets@()));"],
    ["rename.preset.use", "return plain(AW(files.batchRename.preview('IMG_1.txt', {preset: 'aj6-p45'})));"],

    ["speech.speaker.list", "return A(AW(speech.speaker.list())).length;"],
    ["speech.speaker.remove", "return AW(speech.speaker.remove('aj6-none'));"],
    ["speech.speaker.enroll", "return T(function () { return outcome(AW(speech.speaker.enroll('aj6-none', [D + '/s.wav']))); });"],
    ["speech.speaker.enrollAsync", "return T(function () { return outcome(AW(speech.speaker.enroll@('aj6-none', [D + '/s.wav']))); });"],
    ["speech.speaker.verify", "return T(function () { return outcome(AW(speech.speaker.verify('aj6-none', D + '/s.wav'))); });"],
    ["speech.speaker.verifyAsync", "return T(function () { return outcome(AW(speech.speaker.verify@('aj6-none', D + '/s.wav'))); });"],
    ["speech.speaker.identify", "return T(function () { return outcome(AW(speech.speaker.identify(D + '/s.wav'))); });"],
    ["speech.speaker.identifyAsync", "return T(function () { return outcome(AW(speech.speaker.identify@(D + '/s.wav'))); });"],
    ["speech.diarize", "return T(function () { return outcome(AW(speech.diarize(D + '/s.wav'))); });"],
    ["speech.diarizeAsync", "return T(function () { return outcome(AW(speech.diarize@(D + '/s.wav'))); });"],
    ["speech.detectLanguage", "return T(function () { return outcome(AW(speech.detectLanguage(D + '/s.wav'))); });"],
    ["speech.detectLanguageAsync", "return T(function () { return outcome(AW(speech.detectLanguage@(D + '/s.wav'))); });"],
    ["speech.tag", "return T(function () { return outcome(AW(speech.tag(D + '/s.wav'))); });"],
    ["speech.tagAsync", "return T(function () { return outcome(AW(speech.tag@(D + '/s.wav'))); });"],
    ["speech.diarize.missing", "return T(function () { return outcome(AW(speech.diarize(D + '/none.wav'))); });"],
    ["speech.tag.noFile", "return T(function () { return outcome(AW(speech.tag())); });"],
];

// Helpers shared by the runtimes; A is the runtime's own (a Rhino list is not always an array).
var PRESET_DOCUMENT = {format: "autojs6-batch-rename-presets", version: 1, presets: [{name: "aj6-p45", options: {search: "IMG_", replace: "Shot_"}}]};
function sorted(value) {
    var out = {}, keys = Object.keys(value).sort();
    for (var i = 0; i < keys.length; i++) out[keys[i]] = value[keys[i]];
    return out;
}
function primitive(value) {
    if (value === null || value === undefined) return null;
    if (typeof value === "number" || typeof value === "boolean") return value;
    if (typeof value === "object" && typeof value.getClass !== "function" && !Array.isArray(value) && Object.keys(value).length) return plain(value);
    return String(value);
}
function plain(value) {
    var keys = Object.keys(value).sort(), out = {};
    for (var i = 0; i < keys.length; i++) out[keys[i]] = primitive(value[keys[i]]);
    return out;
}
function allPlain(list) {
    var values = A(list), out = [];
    for (var i = 0; i < values.length; i++) out.push(plain(values[i]));
    return out;
}
function libraries(list) { return allPlain(list); }
function intelligent(list) {
    for (var i = 0; i < list.length; i++) if (list[i].intelligent === true) return list[i].id;
    return null;
}
function base(path) { return path === null || path === undefined ? null : String(path).slice(String(path).lastIndexOf("/") + 1); }
function written(file) {
    return {name: String(file.name), mimeType: String(file.mimeType), width: Number(file.width), height: Number(file.height), bytes: Number(file.size) > 0,
        inDirectory: String(file.path).indexOf(D) === 0};
}
function repeated(value, count) {
    var out = [];
    for (var i = 0; i < count; i++) out.push(value);
    return out;
}
function items(plan) {
    var list = A(plan.items), out = [];
    for (var i = 0; i < list.length; i++) out.push([String(list[i].name), String(list[i].newName), String(list[i].status)]);
    return out;
}
function presetNames(list) {
    var values = A(list), out = [];
    for (var i = 0; i < values.length; i++) if (String(values[i].name) === "aj6-p45") out.push(String(values[i].options.replace));
    return out;
}
// The keys only: what a model found depends on the model, the shape of the answer does not.
function outcome(value) { return Array.isArray(value) ? ["array"] : value && typeof value === "object" ? Object.keys(value).sort() : value; }

if (typeof module === "object" && module && module.exports) module.exports = {cases: PLUGIN_FAMILY_CASES};
