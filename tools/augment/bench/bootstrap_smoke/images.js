// ROADMAP P2.5 smoke: images.loadAsync, whose Promise comes from ResultAdapter.promise (Kotlin since P2.5) and is settled
// on the UI thread. Run by device_bootstrap_check.py.
var MARK = 'AJ6P25I';
var path = files.join(files.getSdcardPath(), 'Download/aj6-augment-bench/p25.png');
var bitmap = android.graphics.Bitmap.createBitmap(4, 3, android.graphics.Bitmap.Config.ARGB_8888);
var out = new java.io.FileOutputStream(path);
bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
out.close();
var keep = setInterval(function () {}, 100);
var p = images.loadAsync('file://' + path);
console.log(MARK + ' promise ' + (p instanceof Promise));
p.then(function (img) {
    console.log(MARK + ' loadAsync ' + img.getWidth() + 'x' + img.getHeight());
    img.recycle();
}).catch(function (e) {
    console.log(MARK + ' loadAsync throws ' + e);
}).finally(function () {
    clearInterval(keep);
    files.remove(path);
    console.log(MARK + ' done');
});
