!function () {
    module.exports = Object.assign(require('axios/axios.min.js'), {
        defaults: { transformRequest: [] },
        browser: require('axios/browser-libs/index.js'),
        utils: require('axios/utils/index.js'),
    });
}();