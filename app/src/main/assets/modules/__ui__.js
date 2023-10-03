// noinspection NpmUsedModulesInstalled,JSUnusedGlobalSymbols,JSUnusedLocalSymbols

/* Overwritten protection. */

let { files } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.UI}
 */
module.exports = function (scriptRuntime, scope) {
    const Looper = android.os.Looper;
    const Runnable = java.lang.Runnable;
    const ContextThemeWrapper = android.view.ContextThemeWrapper;
    const ViewExtras = org.autojs.autojs.core.ui.ViewExtras;
    const JsListView = org.autojs.autojs.core.ui.widget.JsListView;
    const JsGridView = org.autojs.autojs.core.ui.widget.JsGridView;
    const JsViewHelper = org.autojs.autojs.core.ui.JsViewHelper;
    const ThemeColor = org.autojs.autojs.theme.ThemeColor;
    const DynamicLayoutInflater = org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater;
    const ColorDrawable = android.graphics.drawable.ColorDrawable;

    require('object-observe-lite.min').call(scope);
    require('array-observe.min').call(scope);

    let isAndroidLayout = null;

    // noinspection JSValidateTypes
    let _ = {
        // @Coerce by SuperMonster003 on Nov 9, 2022.
        /** @type {Internal.UI} */
        rtUi: (/* @IIFE */ () => {
            let inRtUi = scriptRuntime.ui;
            inRtUi.bindingContext = scope;
            inRtUi.__proxy__ = {
                set(name, value) {
                    uiProxy[name] = value;
                },
                get(name) {
                    if (!uiProxy[name] && uiProxy.view) {
                        let view = uiProxy.findById(name);
                        if (view) {
                            return view;
                        }
                    }
                    return uiProxy[name];
                },
            };
            return inRtUi;
        })(),
        UI: (/* @IIFE */ () => {
            /**
             * @extends Internal.UI
             */
            const UI = function () {
                /* Empty body. */
            };

            UI.prototype = {
                constructor: UI,
                __widgets__: {},
                Widget: (/* @IIFE */ () => {
                    /**
                     * @extends Internal.UI.Widget
                     */
                    let Widget = function () {
                        this.__attrs__ = {};
                        return Object.assign(function () {
                            /* Empty body. */
                        }, Widget.prototype);
                    };

                    Widget.prototype = {
                        constructor: Widget,
                        renderInternal() {
                            if (typeof this.render === 'function') {
                                return this.render.call(this);
                            }
                            return '< />';
                        },
                        defineAttr(attrName, getter, setter) {
                            //// -=-= PENDING =-=- ////
                            let attrAlias = attrName;
                            let applier;
                            if (typeof arguments[1] === 'string') {
                                attrAlias = arguments[1];
                                if (arguments.length >= 3) {
                                    applier = arguments[2];
                                }
                            } else if (typeof arguments[1] === 'function' && typeof arguments[2] !== 'function') {
                                applier = arguments[1];
                            }
                            if (!(typeof arguments[1] === 'function' && typeof arguments[2] === 'function')) {
                                getter = () => {
                                    return this[attrAlias];
                                };
                                setter = (view, attrName, value, setter) => {
                                    this[attrAlias] = value;
                                    if (typeof applier === 'function') {
                                        applier(view, attrName, value, setter);
                                    }
                                };
                            }
                            this.__attrs__[attrName] = {
                                getter: getter,
                                setter: setter,
                            };
                        },
                        hasAttr(attrName) {
                            return this.__attrs__.hasOwnProperty(attrName);
                        },
                        setAttr(view, attrName, value, setter) {
                            this.__attrs__[attrName].setter(view, attrName, value, setter);
                        },
                        getAttr(view, attrName, getter) {
                            return this.__attrs__[attrName].getter(view, attrName, getter);
                        },
                        notifyViewCreated(view) {
                            if (typeof this.onViewCreated === 'function') {
                                this.onViewCreated.call(this, view);
                            }
                        },
                        notifyAfterInflation(view) {
                            if (typeof this.onFinishInflation === 'function') {
                                this.onFinishInflation.call(this, view);
                            }
                        },
                    };

                    return Widget;
                })(),
                get R() {
                    return scope.R;
                },
                get emitter() {
                    return typeof activity !== 'undefined' ? activity.getEventEmitter() : null;
                },
                __inflate__(ctx, xml, parent, isAttachedToParent) {
                    return layoutInflater.inflate(ctx, _.toXMLString(xml), parent || null, Boolean(isAttachedToParent));
                },
                inflate(xml, parent, isAttachedToParent) {
                    layoutInflater.setContext(typeof activity === 'undefined'
                        ? new ContextThemeWrapper(context, R.style.ScriptTheme)
                        : activity);
                    return layoutInflater.inflate(_.toXMLString(xml), parent || null, Boolean(isAttachedToParent));
                },
                useAndroidLayout(b) {
                    if (typeof b === 'boolean') {
                        isAndroidLayout = b;
                    } else if (b === undefined) {
                        isAndroidLayout = true;
                    } else {
                        isAndroidLayout = null;
                    }
                },
                run(action) {
                    if (this.isUiThread()) {
                        return action();
                    }
                    let error, result;

                    let disposable = scope.threads.disposable();
                    scriptRuntime.getUiHandler().post(() => {
                        try {
                            result = action();
                        } catch (e) {
                            error = e;
                        } finally {
                            disposable.setAndNotify(true);
                        }
                    });
                    disposable.blockedGet();

                    if (error instanceof Error) {
                        scriptRuntime.console.warn(`${species(error)} occurred in \`ui.run()\`.`);
                        scriptRuntime.console.warn(error.message);
                        scriptRuntime.console.warn(error.stack);
                        throw error;
                    }
                    return result;
                },
                post(action, delay) {
                    if (delay === undefined) {
                        scriptRuntime.getUiHandler().post(_.wrapUiAction(action));
                    } else {
                        scriptRuntime.getUiHandler().postDelayed(_.wrapUiAction(action), delay);
                    }
                },
                layout(xml) {
                    _.ensureActivity();
                    // noinspection JSCheckFunctionSignatures
                    layoutInflater.setContext(activity);
                    // noinspection JSCheckFunctionSignatures,JSTypeOfValues
                    this.setContentView(layoutInflater.inflate(
                        typeof xml === 'xml' ? xml.toXMLString() : String(xml),
                        activity.window.decorView,
                        false,
                    ));
                },
                layoutFile(path) {
                    this.layout(files.read(path));
                },
                isUiThread() {
                    return Looper.myLooper() === Looper.getMainLooper();
                },
                registerWidget(name, widget) {
                    if (typeof widget !== 'function') {
                        throw TypeError('Param "widget" should be a class-like function');
                    }
                    this.__widgets__[name] = widget;
                },
                setContentView(view) {
                    _.ensureActivity();
                    this.view = view;
                    this.run(() => activity.setContentView(view));
                },
                statusBarColor(color) {
                    _.ensureActivity();
                    this.run(() => activity.window.setStatusBarColor(colors.toInt(color)));
                },
                backgroundColor(color) {
                    _.ensureActivity();
                    this.run(() => activity.window.setBackgroundDrawable(new ColorDrawable(
                        Color(color).setAlpha(1.0).toInt(),
                    )));
                },
                findById(id) {
                    return this.view ? this.findByStringId(this.view, id) : null;
                },
                findByStringId(view, id) {
                    return JsViewHelper.findViewByStringId(view, id);
                },
                findView(id) {
                    return this.findById(id);
                },
                finish() {
                    _.ensureActivity();
                    this.run(() => activity.finish());
                },
            };

            return UI;
        })(),
        /**
         * @type {org.autojs.autojs.core.ui.inflater.LayoutInflaterDelegate}
         */
        layoutInflaterDelegate: {
            beforeConvertXml(context, xml) {
                if (isAndroidLayout === true || isAndroidLayout === null && /\bxmlns:\w+="\w+:\/\/|\b(android|app):\w+=".+"/.test(xml)) {
                    // noinspection JSTypeOfValues
                    return typeof xml === 'xml' ? xml.toXMLString() : String(xml);
                }
                return null;
            },
            afterConvertXml(context, xml) {
                return xml;
            },
            beforeInflateView(context, node, parent, attachToParent) {
                return null;
            },
            beforeCreateView(context, node, viewName, parent) {
                if (uiProxy.__widgets__.hasOwnProperty(viewName)) {
                    let Widget = uiProxy.__widgets__[viewName];
                    let widget = new Widget();
                    let ctx = layoutInflater.newInflateContext();
                    ctx.put('root', widget);
                    ctx.put('widget', widget);
                    return uiProxy.__inflate__(ctx, widget.renderInternal(), parent, false);
                }
                return null;
            },
            beforeInflation(context, xml, parent) {
                return null;
            },
            afterInflation(context, result, xml, parent) {
                return result;
            },
            afterCreateView(context, view, node, viewName, parent) {
                if (view instanceof JsListView || view instanceof JsGridView) {
                    _.initListView(view);
                }
                let widget = context.get('widget');
                if (widget !== null) {
                    widget.view = view;
                    view.widget = widget;
                    ViewExtras
                        .getViewAttributes(view, layoutInflater.getResourceParser())
                        .setViewAttributeDelegate({
                            has: name => widget.hasAttr(name),
                            get: (view, name, getter) => widget.getAttr(view, name, getter),
                            set: (view, name, value, setter) => widget.setAttr(view, name, value, setter),
                        });
                    widget.notifyViewCreated(view);
                }
                return view;
            },
            beforeApplyAttributes(context, view, inflater, attrs, parent) {
                return false;
            },
            beforeApplyAttribute(context, inflater, view, ns, attrName, value, parent) {
                let isDynamic = layoutInflater.isDynamicValue(value);
                return isDynamic && layoutInflater.getInflateFlags() === DynamicLayoutInflater.FLAG_IGNORES_DYNAMIC_ATTRS
                    || !isDynamic && layoutInflater.getInflateFlags() === DynamicLayoutInflater.FLAG_JUST_DYNAMIC_ATTRS
                    || (/* @IIFE */ () => {
                        value = _.bind(value);
                        let widget = context.get('widget');
                        if (widget !== null && widget.hasAttr(attrName)) {
                            widget.setAttr(view, attrName, value, (view, attrName, value) => {
                                inflater.setAttr(view, ns, attrName, value, parent);
                            });
                        } else {
                            inflater.setAttr(view, ns, attrName, value, parent);
                        }
                        this.afterApplyAttribute(context, inflater, view, ns, attrName, value, parent);
                        return true;
                    })();
            },
            afterApplyAttribute(context, inflater, view, ns, attrName, value, parent) {
                // Empty method body
            },
            afterApplyAttributes(context, view, inflater, attrs, parent) {
                context.remove('widget');
            },
            beforeInflateChildren(context, inflater, node, parent) {
                return false;
            },
            afterInflateChildren(context, inflater, node, parent) {
                // Empty method body
            },
            beforeApplyPendingAttributesOfChildren(context, inflater, view) {
                return false;
            },
            afterApplyPendingAttributesOfChildren(context, inflater, view) {
                // Empty method body
            },
            afterInflateView(context, view, node, parent, attachToParent) {
                let { widget } = view;
                if (widget && context.get('root') !== widget) {
                    widget.notifyAfterInflation(view);
                }
                return view;
            },
        },
        bind(value) {
            let ctx = this.rtUi.bindingContext;
            if (ctx !== null) {
                let i = -1;
                while ((i = value.indexOf('{{', i + 1)) >= 0) {
                    let j = value.indexOf('}}', i + 1);
                    if (j < 0) {
                        return value;
                    }
                    let evaluated = _.evalInContext(value.slice(i + 2, j), ctx);
                    value = value.slice(0, i) + _.attrValueConvert(evaluated) + value.slice(j + 2);
                    i = j + 1;
                }
                return value;
            }
        },
        toXMLString(xml) {
            // noinspection JSTypeOfValues
            if (typeof xml === 'xml') {
                xml = xml.toXMLString();
            }
            return xml.toString();
        },
        evalInContext(expression, ctx) {
            return __exitIfError__(() => {
                // @ScopeBinding
                // noinspection WithStatementJS
                with (ctx) {
                    return (/* @IIFE */ function () {
                        return eval(expression);
                    })();
                }
            });
        },
        initListView(list) {
            list.setDataSourceAdapter({
                getItemCount(data) {
                    return data.length;
                },
                getItem(data, i) {
                    return data[i];
                },
                setDataSource(data) {
                    let adapter = list.getAdapter();
                    Array.observe(data, function (changes) {
                        changes.forEach((change) => {
                            if (change.type === 'splice') {
                                if (change.removed && change.removed.length > 0) {
                                    adapter.notifyItemRangeRemoved(change.index, change.removed.length);
                                }
                                if (change.addedCount > 0) {
                                    adapter.notifyItemRangeInserted(change.index, change.addedCount);
                                }
                            } else if (change.type === 'update') {
                                try {
                                    adapter.notifyItemChanged(parseInt(change.name));
                                } catch (e) {
                                    // Ignored.
                                }
                            }
                        });
                    });
                },
            });
        },
        wrapUiAction(action) {
            return new Runnable({
                run: () => typeof activity !== 'undefined' ? action() : __exitIfError__(action),
            });
        },
        ensureActivity() {
            if (typeof activity === 'undefined') {
                throw ReferenceError('An activity is needed. Try running in "ui" thread');
            }
        },
        setLayoutInflaterDelegate() {
            layoutInflater.setLayoutInflaterDelegate(this.layoutInflaterDelegate);
        },
        attrValueConvert(o) {
            if (typeof o === 'string') {
                return o;
            }
            if (o instanceof scope.Color) {
                return o.toHex();
            }
            if (o instanceof ThemeColor) {
                return o.getColorPrimary();
            }
            return o;
        },
    };

    /**
     * @type {Internal.UI}
     */
    const ui = _.rtUi;
    const uiProxy = new _.UI();
    const layoutInflater = ui.layoutInflater;

    _.setLayoutInflaterDelegate();

    return ui;
};