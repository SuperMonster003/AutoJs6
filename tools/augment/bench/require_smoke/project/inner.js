module.exports = 'inner ' + new Promise(function (resolve) {
    setTimeout(function () {
        resolve('a');
    }, 50);
}).await();
