/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const Looper = android.os.Looper;
const Linkify = android.text.util.Linkify;
const LayoutParams = android.view.WindowManager.LayoutParams;
const ColorDrawable = android.graphics.drawable.ColorDrawable;

// @Hint by SuperMonster003 on May 5, 2022.
//  ! Store global function reference(s) immediately in case
//  ! the one(s) being overwritten (usually by accident).
const $isNullish = global.isNullish.bind(global);

let _ = {
    propertySetters: {
        title: null,
        titleColor: {adapter: colors.toInt.bind(colors)},
        buttonRippleColor: {adapter: colors.toInt.bind(colors)},
        icon: null,
        content: null,
        contentColor: {adapter: colors.toInt.bind(colors)},
        contentLineSpacing: null,
        items: null,
        itemsColor: {adapter: colors.toInt.bind(colors)},
        positive: {method: 'positiveText'},
        positiveColor: {adapter: colors.toInt.bind(colors)},
        neutral: {method: 'neutralText'},
        neutralColor: {adapter: colors.toInt.bind(colors)},
        negative: {method: 'negativeText'},
        negativeColor: {adapter: colors.toInt.bind(colors)},
        cancelable: null,
        canceledOnTouchOutside: null,
        autoDismiss: null,
    },
    linkifyMask: ['all', 'emailAddresses', 'mapAddresses', 'phoneNumbers', 'webUrls'],
    animation: ['default', 'activity', 'dialog', 'inputMethod', 'toast', 'translucent'],
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        this.dialogs = {};

        Object.assign(this, {
            /**
             * @return {com.stardust.autojs.runtime.api.Dialogs|com.stardust.autojs.runtime.api.Dialogs.NonUiDialogs|*}
             */
            get rtDialogs() {
                return _.isUiThread()
                    ? __runtime__.dialogs
                    : __runtime__.dialogs.nonUiDialogs;
            },
        });
    },
    getModule() {
        return this.dialogs;
    },
    selfAugment() {
        Object.assign(this.dialogs, {
            /**
             * @param {Dialogs.Build.Properties} properties
             * @return {com.stardust.autojs.core.ui.dialog.JsDialog}
             */
            build(properties) {
                /**
                 * @type {Dialogs.Builder}
                 */
                let builder = Object.create(_.runtime.dialogs.newBuilder(), {
                    thread: {value: threads.currentThread()},
                });

                Object.keys(properties).forEach((name) => {
                    _.applyDialogProperty(builder, name, properties[name]);
                });
                _.applyOtherDialogProperties(builder, properties);

                let dialog = ui.run(() => builder.buildDialog());

                _.applyBuiltDialogProperties(dialog, properties);

                return dialog;
            },
            rawInput(title, prefill, callback) {
                if (_.isUiThread() && !callback) {
                    return new Promise((resolve) => {
                        _.rtDialogs.rawInput(title, prefill || '', function () {
                            resolve.apply(null, arguments);
                        });
                    });
                }
                return _.rtDialogs.rawInput(title, prefill || '', callback || null);
            },
            input(title, prefill, callback) {
                if (_.isUiThread() && !callback) {
                    return new Promise((resolve) => {
                        _.rtDialogs.rawInput(title, prefill || '', function (str) {
                            resolve(eval(str));
                        });
                    });
                }
                if (!callback) {
                    return eval(String(this.rawInput(title, prefill)));
                }
                this.rawInput(title, prefill, str => callback(eval(str)));
            },
            prompt(title, prefill, callback) {
                return this.rawInput(title, prefill, callback);
            },
            alert(title, prefill, callback) {
                if (_.isUiThread() && !callback) {
                    return new Promise((resolve) => {
                        _.rtDialogs.alert(title, prefill || '', function () {
                            resolve.apply(null, arguments);
                        });
                    });
                }
                return _.rtDialogs.alert(title, prefill || '', callback || null);
            },
            confirm(title, prefill, callback) {
                if (_.isUiThread() && !callback) {
                    return new Promise((resolve) => {
                        _.rtDialogs.confirm(title, prefill || '', function () {
                            resolve.apply(null, arguments);
                        });
                    });
                }
                return _.rtDialogs.confirm(title, prefill || '', callback || null);
            },
            select(title, items, callback) {
                if (Array.isArray(items)) {
                    if (_.isUiThread() && !callback) {
                        return new Promise((resolve) => {
                            _.rtDialogs.select(title, items, function () {
                                resolve.apply(null, arguments);
                            });
                        });
                    }
                    return _.rtDialogs.select(title, items, callback || null);
                }
                let itemsGatheredFromArguments = Array.from(arguments).slice(1);
                return _.rtDialogs.select(title, itemsGatheredFromArguments, null);
            },
            singleChoice(title, items, index, callback) {
                if (_.isUiThread() && !callback) {
                    return new Promise((resolve) => {
                        _.rtDialogs.singleChoice(title, index || 0, items, function () {
                            resolve.apply(null, arguments);
                        });
                    });
                }
                return _.rtDialogs.singleChoice(title, index || 0, items, callback || null);
            },
            multiChoice(title, items, index, callback) {
                index = index || [];
                if (_.isUiThread() && !callback) {
                    return new Promise((resolve) => {
                        _.rtDialogs.multiChoice(title, index, items, function (r) {
                            resolve(_.toJsArray(r));
                        });
                    });
                }
                if (callback) {
                    return _.toJsArray(_.rtDialogs.multiChoice(title, index, items, function (r) {
                        callback(_.toJsArray(r));
                    }));
                }
                return _.toJsArray(_.rtDialogs.multiChoice(title, index, items, null));

            },
        });
    },
    scopeAugment() {
        /**
         * @type {(keyof Internal.Dialogs)[]}
         */
        let methods = ['rawInput', 'alert', 'confirm', 'prompt'];
        __asGlobal__(this.dialogs, methods);
    },
    applyDialogProperty(builder, name, value) {
        if (_.propertySetters.hasOwnProperty(name)) {
            let propertySetter = _.propertySetters[name] || {};
            if (propertySetter.method === undefined) {
                propertySetter.method = name;
            }
            if (propertySetter.adapter) {
                value = propertySetter.adapter(value);
            }
            builder[propertySetter.method](value);
        }
    },
    /**
     * @param {Dialogs.Builder} builder
     * @param {Dialogs.Build.Properties} props
     */
    applyOtherDialogProperties(builder, props) {
        if (props.inputHint !== undefined || props.inputPrefill !== undefined) {
            let inputHint = _.wrapNonNullString(props.inputHint);
            let inputPrefill = _.wrapNonNullString(props.inputPrefill);
            builder.input(inputHint, inputPrefill, (dialog, input) => {
                return builder.emit('input_change', builder.getDialog(), String(input));
            }).alwaysCallInputCallback();
        }

        if (props.items !== undefined) {
            let itemsSelectMode = props.itemsSelectMode;
            if (itemsSelectMode === undefined || itemsSelectMode === 'select') {
                builder.itemsCallback((dialog, view, position, text) => {
                    builder.emit('item_select', position, text.toString(), builder.getDialog());
                });
            } else if (itemsSelectMode === 'single') {
                let selectedIndex = props.itemsSelectedIndex === undefined ? -1 : props.itemsSelectedIndex;
                builder.itemsCallbackSingleChoice(selectedIndex, (dialog, view, which, text) => {
                    builder.emit('single_choice', which, text.toString(), builder.getDialog());
                    return true;
                });
            } else if (itemsSelectMode === 'multi') {
                let selectedIndices = props.itemsSelectedIndices !== undefined
                    ? Array.isArray(props.itemsSelectedIndices)
                        ? props.itemsSelectedIndices
                        : [props.itemsSelectedIndices]
                    : props.itemsSelectedIndex === undefined ? []
                        : Array.isArray(props.itemsSelectedIndex)
                            ? props.itemsSelectedIndex
                            : [props.itemsSelectedIndex];
                builder.itemsCallbackMultiChoice(selectedIndices, (dialog, indices, texts) => {
                    builder.emit('multi_choice',
                        _.toJsArray(indices, (l, i) => parseInt(l[i])),
                        _.toJsArray(texts, (l, i) => l[i].toString()),
                        builder.getDialog());
                    return true;
                });
            } else {
                throw Error(`Unknown itemsSelectMode ${itemsSelectMode}`);
            }
        }

        if (props.progress !== undefined) {
            let {progress} = props;
            let isIndeterminate = progress.max === -1;
            builder.progress(isIndeterminate, progress.max, Boolean(progress.showMinMax));
            builder.progressIndeterminateStyle(Boolean(progress.horizontal));
        }

        if (props.checkBoxPrompt !== undefined || props.checkBoxChecked !== undefined) {
            builder.checkBoxPrompt(_.wrapNonNullString(props.checkBoxPrompt),
                Boolean(props.checkBoxChecked),
                (view, checked) => builder.getDialog().emit('check', checked, builder.getDialog()));
        }

        if (props.customView !== undefined) {
            let customView = props.customView;
            // noinspection JSTypeOfValues
            if (typeof customView === 'xml' || typeof customView === 'string') {
                customView = ui.run(() => ui.inflate(customView));
            }
            let wrapInScrollView = props.wrapInScrollView === undefined || Boolean(props.wrapInScrollView);
            builder.customView(customView, wrapInScrollView);
        }

        if (props.stubborn) {
            let isOperationAvail = prop => prop === undefined || Boolean(prop) !== true;
            if (isOperationAvail(props.autoDismiss) && isOperationAvail(props.canceledOnTouchOutside)) {
                builder.autoDismiss(false);
                builder.canceledOnTouchOutside(false);
            }
        }
    },
    /**
     *
     * @param {com.stardust.autojs.core.ui.dialog.JsDialog} dialog
     * @param {Dialogs.Build.Properties} props
     */
    applyBuiltDialogProperties(dialog, props) {
        if (props.linkify !== undefined && Boolean(props.linkify) !== false) {
            let linkify = ( /* @IIFE */ () => {
                let linkify = typeof props.linkify === 'string' ? props.linkify : 'all';
                if (_.linkifyMask.includes(linkify)) {
                    linkify = linkify.replace(/[A-Z]/g, '_$&').toUpperCase();
                }
                if (linkify in Linkify) {
                    return Linkify[linkify];
                }
                throw Error(`Unknown linkify: ${props.linkify}`);
            })();
            let view = dialog.getContentView();
            let text = view.getText().toString();
            ui.run(() => {
                view.setAutoLinkMask(linkify);
                view.setText(text);
            });
        }

        if (props.onBackKey !== undefined) {
            let isFunction = typeof props.onBackKey === 'function';
            let isDisabled = Boolean(props.onBackKey) === false
                || String(props.onBackKey).match(/^disabled?$/i);

            if (isDisabled || isFunction) {
                dialog.setOnKeyListener({
                    onKey(dialogInterface, keyCode, event) {
                        if (event.getAction() !== KeyEvent.ACTION_UP || keyCode !== KeyEvent.KEYCODE_BACK) {
                            return false;
                        }
                        if (isFunction) {
                            props.onBackKey(dialog);
                        }
                        return true;
                    },
                });
            }
        }

        if (props.dimAmount !== undefined) {
            let dim = Number(props.dimAmount);
            while (dim > 1) {
                dim /= 100;
            }
            if (!isNaN(dim)) {
                ui.post(() => dialog.getWindow().setDimAmount(dim));
            }
        }

        if (props.background !== undefined) {
            let bg = props.background;
            let win = dialog.getWindow();
            ui.post(() => {
                if (typeof bg === 'string') {
                    bg.startsWith('#')
                        ? win.setBackgroundDrawable(new ColorDrawable(colors.toInt(bg)))
                        : win.setBackgroundDrawableResource(android.R.color[bg]);
                } else if (typeof bg === 'number') {
                    win.setBackgroundDrawable(new ColorDrawable(bg));
                } else {
                    throw TypeError(`Unknown type of background property: ${props.background}`);
                }
            });
        }

        if (props.animation !== undefined && Boolean(props.animation) !== false) {
            let animation = typeof props.animation === 'string' ? props.animation : 'default';
            if (!_.animation.includes(animation)) {
                throw Error(`Unknown linkify: ${props.animation}`);
            }
            ui.post(() => {
                let win = dialog.getWindow();
                if (animation === 'default') {
                    win.setWindowAnimations(android.R.style.Animation);
                } else {
                    let suffix = animation[0].toUpperCase() + animation.slice(1);
                    win.setWindowAnimations(android.R.style[`Animation_${suffix}`]);
                }
            });
        }

        if (props.keepScreenOn) {
            ui.post(() => {
                let win = dialog.getWindow();
                win.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
    },
    wrapNonNullString(str) {
        return $isNullish(str) ? '' : String(str);
    },
    toJsArray(javaArray, adapter) {
        let jsArray = [];
        if (typeof adapter === 'function') {
            for (let i = 0; i < javaArray.length; i += 1) {
                jsArray.push(adapter(javaArray, i));
            }
        } else {
            for (let i = 0; i < javaArray.length; i += 1) {
                jsArray.push(javaArray[i]);
            }
        }
        return jsArray;
    },
    isUiThread() {
        return Looper.myLooper() === Looper.getMainLooper();
    },
    parseColor(c) {
        if (typeof c === 'string') {
            return colors.parseColor(c);
        }
        return c;
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.scopeAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Dialogs}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};