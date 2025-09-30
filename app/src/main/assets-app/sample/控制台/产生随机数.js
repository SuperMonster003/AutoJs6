console.show();

log('将产生 5 个 1 到 100 的随机数');

for (let i = 0; i < 5; i++) {
    print(random(1, 100));
    print('  ');
    sleep(400);
}
print('\n');

log('将产生 10 个 1 到 20 的不重复随机数');

var exists = {};

for (let i = 0; i < 10; i++) {
    var r;
    do {
        r = random(1, 20);
    } while (exists[r]);
    exists[r] = true;
    print(r + '  ');
    sleep(400);
}
