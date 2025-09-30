(/* @IIFE */ () => {
    // 以 UTF-8 编码打开 SD 卡上的 1.txt 文件
    var f = open('/sdcard/1.txt', 'r', 'utf-8');
    // 读取文件所有内容
    var text = f.read();
    // 关闭文件
    f.close();
    // 以 gbk 编码打开 SD 卡上的 2.txt 文件
    var out = open('/sdcard/2.txt', 'w', 'gbk');
    // 写入内容
    out.write(text);
    // 关闭文件
    out.close();
})();
