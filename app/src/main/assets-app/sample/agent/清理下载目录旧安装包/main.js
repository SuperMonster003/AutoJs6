// 由 Agent 传入参数, 仅处理下载目录内的普通文件. 建议先 dryRun 预览.
let args = engines.myEngine().execArgv;
let days = Number(args.days);
if (!isFinite(days) || Math.floor(days) !== days || days < 1 || days > 3650) {
    throw Error('days must be an integer between 1 and 3650');
}
let downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getCanonicalFile();
let directory = args.directory == null ? downloads : new java.io.File(String(args.directory)).getCanonicalFile();
let basePath = String(downloads.getPath());
let targetPath = String(directory.getPath());
if (targetPath !== basePath && targetPath.indexOf(basePath + '/') !== 0) {
    throw Error('directory must be Downloads or one of its children');
}
let children = directory.listFiles();
if (children == null) throw Error('Downloads is unavailable or unreadable');
let cutoff = Date.now() - days * 86400000;
let dryRun = args.dryRun !== false;
let result = { dryRun: dryRun, days: days, matched: 0, removed: 0, failed: 0, bytes: 0, names: [] };
for (let i = 0; i < children.length; i++) {
    let child = children[i];
    if (!child.isFile() || String(child.getCanonicalFile().getParent()) !== targetPath) continue;
    let name = String(child.getName());
    let modified = Number(child.lastModified());
    if (!/\.(apk|apks|xapk)$/i.test(name) || modified <= 0 || modified >= cutoff) continue;
    result.matched++;
    result.bytes += Number(child.length());
    if (result.names.length < 50) result.names.push(name);
    if (!dryRun) {
        if (child.delete()) result.removed++;
        else result.failed++;
    }
}
result.namesTruncated = result.matched > result.names.length;
ai.agent.result(result);
console.log(JSON.stringify(result));
