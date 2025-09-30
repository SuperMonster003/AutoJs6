// 以写入模式打开 SD 卡根目录文件 1.txt
var file = open('/sdcard/1.txt', 'w');
// 写入 aaaa
file.write('aaaa');
// 写入 bbbbb 后换行
file.writeline('bbbbb');
// 写入 ccc 与 ddd 两行
file.writelines([ 'ccc', 'ddd' ]);
// 关闭文件
file.close();

// 以附加模式打开文件
file = open('/sdcard/1.txt', 'a');
// 附加一行 "啦啦啦啦"
file.writeline('啦啦啦啦');
// 附加一行 "哈哈哈哈"
file.writeline('哈哈哈哈');
// 附加两行 ccc, ddd
file.writelines([ 'ccc', 'ddd' ]);
// 输出缓冲区
file.flush();
// 关闭文件
file.close();


// 以读取模式打开文件
file = open('/sdcard/test.txt', 'r');
// 读取一行并打印
print(file.readline());
// 读取剩余所有行并打印
for (let line in file.readlines()) {
    print(line);
}
file.close();

// 显示控制台
console.show();
