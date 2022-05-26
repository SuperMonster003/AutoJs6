// noinspection NpmUsedModulesInstalled,JSUnusedGlobalSymbols,JSUnusedLocalSymbols

/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const R = com.stardust.autojs.R;
const Looper = android.os.Looper;
const Runnable = java.lang.Runnable;
const Color = android.graphics.Color;
const ContextThemeWrapper = android.view.ContextThemeWrapper;
const ViewExtras = com.stardust.autojs.core.ui.ViewExtras;
const JsListView = com.stardust.autojs.core.ui.widget.JsListView;
const JsGridView = com.stardust.autojs.core.ui.widget.JsGridView;
const JsViewHelper = com.stardust.autojs.core.ui.JsViewHelper;
const DynamicLayoutInflater = com.stardust.autojs.core.ui.inflater.DynamicLayoutInflater;

let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        require('object-observe-lite.min').call(scope);
        require('array-observe.min').call(scope);

        this.ui = {};
        this.rtUi = __runtime__.ui;
        this.rtUi.bindingContext = scope;
        this.rtUi.__proxy__ = {
            set(name, value) {
                _.ui[name] = value;
            },
            get(name) {
                if (!_.ui[name] && _.ui.view) {
                    let view = _.ui.findById(name);
                    if (view) {
                        return view;
                    }
                }
                return _.ui[name];
            },
        };

        this.layoutInflater = this.rtUi.layoutInflater;
    },
    getModule() {
        return this.rtUi;
    },
    /**
     * @this {this}
     */
    selfAugment() {
        Object.assign(this.ui, {
            __widgets__: {},
            Widget: (function () {
                function Widget() {
                    this.__attrs__ = {};
                }

                Object.assign(Widget.prototype, {
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
                });

                return Widget;
            })(),
            get emitter() {
                return typeof activity !== 'undefined' ? activity.getEventEmitter() : null;
            },
            __inflate__(ctx, xml, parent, isAttachedToParent) {
                return _.layoutInflater.inflate(ctx, _.toXMLString(xml), parent || null, Boolean(isAttachedToParent));
            },
            inflate(xml, parent, isAttachedToParent) {
                _.layoutInflater.setContext(typeof activity === 'undefined'
                    ? new ContextThemeWrapper(context, R.style.ScriptTheme)
                    : activity);
                return _.layoutInflater.inflate(_.toXMLString(xml), parent || null, Boolean(isAttachedToParent));
            },
            run(action) {
                if (this.isUiThread()) {
                    return action();
                }
                let error, result;

                let disposable = _.scope.threads.disposable();
                _.runtime.getUiHandler().post(function () {
                    try {
                        result = action();
                    } catch (e) {
                        error = e;
                    } finally {
                        disposable.setAndNotify(true);
                    }
                });
                disposable.blockedGet();

                if (error) {
                    throw error;
                }
                return result;
            },
            post(action, delay) {
                if (delay === undefined) {
                    _.runtime.getUiHandler().post(_.wrapUiAction(action));
                } else {
                    _.runtime.getUiHandler().postDelayed(_.wrapUiAction(action), delay);
                }
            },
            layout(xml) {
                _.ensureActivity();
                _.layoutInflater.setContext(activity);
                this.setContentView(_.layoutInflater.inflate(xml, activity.window.decorView, false));
            },
            layoutFile(file) {
                this.layout(files.read(file));
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
                _.ui.view = view;
                this.run(() => activity.setContentView(view));
            },
            statusBarColor(color) {
                _.ensureActivity();
                this.run(() => activity.getWindow().setStatusBarColor(colors.toInt(color)));
            },
            findById(id) {
                return _.ui.view ? this.findByStringId(_.ui.view, id) : null;
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
        });
    },
    setLayoutInflaterDelegate() {
        this.layoutInflater.setLayoutInflaterDelegate({
            beforeConvertXml: function (context, xml) {
                return null;
            },
            afterConvertXml: function (context, xml) {
                return xml;
            },
            beforeInflation: function (context, xml, parent) {
                return null;
            },
            afterInflation: function (context, result, xml, parent) {
                return result;
            },
            beforeInflateView: function (context, node, parent, attachToParent) {
                return null;
            },
            afterInflateView: function (context, view, node, parent, attachToParent) {
                let {widget} = view;
                if (widget && context.get('root') !== widget) {
                    widget.notifyAfterInflation(view);
                }
                return view;
            },
            beforeCreateView: function (context, node, viewName, parent, attrs) {
                if (_.ui.__widgets__.hasOwnProperty(viewName)) {
                    let Widget = _.ui.__widgets__[viewName];
                    let widget = new Widget();
                    let ctx = _.layoutInflater.newInflateContext();
                    ctx.put('root', widget);
                    ctx.put('widget', widget);
                    return _.ui.__inflate__(ctx, widget.renderInternal(), parent, false);
                }
                return null;
            },
            afterCreateView: function (context, view, node, viewName, parent, attrs) {
                if (view instanceof JsListView || view instanceof JsGridView) {
                    _.initListView(view);
                }
                let widget = context.get('widget');
                if (widget !== null) {
                    widget.view = view;
                    view.widget = widget;
                    ViewExtras.getViewAttributes(view, _.layoutInflater.getResourceParser()).setViewAttributeDelegate({
                        has: name => widget.hasAttr(name),
                        get: (view, name, getter) => widget.getAttr(view, name, getter),
                        set: (view, name, value, setter) => widget.setAttr(view, name, value, setter),
                    });
                    widget.notifyViewCreated(view);
                }
                return view;
            },
            beforeApplyAttributes: function (context, view, inflater, attrs, parent) {
                return false;
            },
            afterApplyAttributes: function (context, view, inflater, attrs, parent) {
                context.remove('widget');
            },
            beforeInflateChildren: function (context, inflater, node, parent) {
                return false;
            },
            afterInflateChildren: function (context, inflater, node, parent) {
                // Empty method body
            },
            beforeApplyPendingAttributesOfChildren: function (context, inflater, view) {
                return false;
            },
            afterApplyPendingAttributesOfChildren: function (context, inflater, view) {
                // Empty method body
            },
            beforeApplyAttribute: function (context, inflater, view, ns, attrName, value, parent, attrs) {
                let isDynamic = _.layoutInflater.isDynamicValue(value);
                return isDynamic && _.layoutInflater.getInflateFlags() === DynamicLayoutInflater.FLAG_IGNORES_DYNAMIC_ATTRS
                    || !isDynamic && _.layoutInflater.getInflateFlags() === DynamicLayoutInflater.FLAG_JUST_DYNAMIC_ATTRS
                    || ( /* @IIFE */ () => {
                        value = _.bind(value);
                        let widget = context.get('widget');
                        if (widget !== null && widget.hasAttr(attrName)) {
                            widget.setAttr(view, attrName, value, (view, attrName, value) => {
                                inflater.setAttr(view, ns, attrName, value, parent, attrs);
                            });
                        } else {
                            inflater.setAttr(view, ns, attrName, value, parent, attrs);
                        }
                        this.afterApplyAttribute(context, inflater, view, ns, attrName, value, parent, attrs);
                        return true;
                    })();
            },
            afterApplyAttribute: function (context, inflater, view, ns, attrName, value, parent, attrs) {
                // Empty method body
            },
        });
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
                value = value.slice(0, i) + this.evalInContext(value.slice(i + 2, j), ctx) + value.slice(j + 2);
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
    evalInContext(x, ctx) {
        return __exitIfError__(() => {
            // @ScopeBinding
            return ( /* @IIFE */ function () {
                return eval(x);
            }).call(ctx);
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
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.setLayoutInflaterDelegate();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.UI}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};