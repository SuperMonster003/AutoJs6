var inner = require('./inner');
var value = new Promise(function (resolve) {
    setTimeout(function () {
        resolve('b');
    }, 50);
}).await();
module.exports = inner + ' / ' + value;
