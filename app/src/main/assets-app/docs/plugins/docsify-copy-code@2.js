/**
 * Compatibility:
 * Local Documentation
 * Online Documentation
 *
 * Relations:
 * docsify-copy-code@2-styles.css
 */

// Dependencies
// =============================================================================
// eslint-disable-next-line no-unused-vars

// Plugin
// =============================================================================

const isOnlineEnvironment = 'Docsify' in window;

if (isOnlineEnvironment) {
    function docsifyCopyCode(hook, vm) {
        hook.doneEach(insertButtonElement.bind(null, vm));
        hook.mounted(addButtonListeners);
    }

    // Deprecation warning for v1.x: stylesheet
    if (document.querySelector('link[href*="docsify-copy-code"]')) {
        // eslint-disable-next-line
        console.warn('[Deprecation] Link to external docsify-copy-code stylesheet is no longer necessary.');
    }

    // Deprecation warning for v1.x: init()
    window.DocsifyCopyCodePlugin = {
        init: function () {
            return function (hook, vm) {
                hook.ready(function () {
                    // eslint-disable-next-line
                    console.warn('[Deprecation] Manually initializing docsify-copy-code using window.DocsifyCopyCodePlugin.init() is no longer necessary.');
                });
            };
        },
    };

    window.$docsify = window.$docsify || {};
    window.$docsify.plugins = [ docsifyCopyCode ].concat(window.$docsify.plugins || []);

} else {
    const winOnloadBak = window.onload ? window.onload.bind(window) : null;

    window.onload = function () {
        if (typeof winOnloadBak === 'function') {
            winOnloadBak();
        }
        insertButtonElement();
        addButtonListeners();
    };
}

function insertButtonElement(vm) {
    const selectors = isOnlineEnvironment ? 'pre[data-lang]' : '.codeWrapper';
    const targetElms = Array.from(document.querySelectorAll(selectors));
    const i18n = {
        buttonText: 'Copy',
        errorText: 'Error',
        successText: 'Copied',
    };

    // Update i18n strings based on options and location.href
    if (vm && vm.config.copyCode) {
        Object.keys(i18n).forEach(key => {
            const textValue = vm.config.copyCode[key];

            if (typeof textValue === 'string') {
                i18n[key] = textValue;
            } else if (typeof textValue === 'object') {
                Object.keys(textValue).some(match => {
                    const isMatch = location.href.indexOf(match) > -1;

                    i18n[key] = isMatch ? textValue[match] : i18n[key];

                    return isMatch;
                });
            }
        });
    }

    const template = [
        '<button class="docsify-copy-code-button">',
        `<span class="label">${i18n.buttonText}</span>`,
        `<span class="error">${i18n.errorText}</span>`,
        `<span class="success">${i18n.successText}</span>`,
        '</button>',
    ].join('');

    targetElms.forEach(elm => {
        elm.insertAdjacentHTML(isOnlineEnvironment ? 'beforeend' : 'afterbegin', template);
    });
}

function addButtonListeners() {
    const selectors = isOnlineEnvironment ? '.content' : '#apicontent';
    const listenerHost = document.querySelector(selectors);

    listenerHost.addEventListener('click', function (evt) {
        const isCopyCodeButton = evt.target.classList.contains('docsify-copy-code-button');

        if (isCopyCodeButton) {
            const buttonElm = evt.target.tagName === 'BUTTON' ? evt.target : evt.target.parentNode;
            const range = document.createRange();
            const preElm = buttonElm.parentNode;
            const codeElm = preElm.querySelector('code');

            let selection = window.getSelection();

            range.selectNode(codeElm);
            selection.removeAllRanges();
            selection.addRange(range);

            try {
                // Copy selected text
                const successful = document.execCommand('copy');

                if (successful) {
                    buttonElm.classList.add('success');
                    setTimeout(function () {
                        buttonElm.classList.remove('success');
                    }, 1000);
                }
            } catch (err) {
                // eslint-disable-next-line
                console.error(`docsify-copy-code: ${err}`);

                buttonElm.classList.add('error');
                setTimeout(function () {
                    buttonElm.classList.remove('error');
                }, 1000);
            }

            selection = window.getSelection();

            if (typeof selection.removeRange === 'function') {
                selection.removeRange(range);
            } else if (typeof selection.removeAllRanges === 'function') {
                selection.removeAllRanges();
            }
        }
    });
}