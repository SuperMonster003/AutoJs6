// Type definitions for AutoJs6 internal module ui
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026
//
// noinspection JSUnusedGlobalSymbols

/// <reference path="../android.d.ts"/>
/// <reference path="../libraries.d.ts"/>
/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/ui/UI.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/ui/UIWidget.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/UI.kt
 */

declare namespace Internal {

    class UI extends org.autojs.autojs.runtime.api.UI {

        __widgets__: object;

        /**
         * @param ctx
         * @param xml
         * @param [parent]
         * @param [isAttachedToParent=false]
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.__inflate__ = function (ctx, xml, parent, isAttachedToParent) {
         *     if (typeof (xml) == 'xml') {
         *         xml = xml.toXMLString();
         *     }
         *     parent = parent || null;
         *     isAttachedToParent = !!isAttachedToParent;
         *     return runtime.ui.layoutInflater.inflate(ctx, xml.toString(), parent, isAttachedToParent);
         * };
         */
        __inflate__(
            ctx: org.autojs.autojs.core.ui.inflater.InflateContext,
            xml: Xml,
            parent?: android.view.ViewGroup,
            isAttachedToParent?: boolean,
        ): android.view.View;

        layoutInflater: org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater;

        bindingContext: object;

        R: org.autojs.autojs6.R;

        Widget: {
            new(): UI.Widget;
        };

        useAndroidLayout(b?: boolean | null): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.__defineGetter__("emitter", () => activity ? activity.getEventEmitter() : null);
         */
        get emitter(): EventEmitter$ | null;

        get root(): android.widget.FrameLayout;

        get statusBarHeight(): number;

        get visibleStatusBarHeight(): number;

        get navigationBarHeight(): number;

        get visibleNavigationBarHeight(): number;

        /**
         * @example
         * "ui";
         * let i = 2;
         * ui.layout(<linear><text id="_" textSize="40" text="{{i}}"/></linear>);
         * setInterval(() => ui._.setText((--i).toString()), 1000);
         * setTimeout(ui.finish, 2000);
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.layout = function (xml) {
         *     if (typeof (activity) == 'undefined') {
         *         throw new Error('需要在ui模式下运行才能使用该函数');
         *     }
         *     runtime.ui.layoutInflater.setContext(activity);
         *     let view = runtime.ui.layoutInflater.inflate(xml, activity.window.decorView, false);
         *     ui.setContentView(view);
         * };
         */
        layout(layout: Xml): void;

        /**
         * @example
         * "ui";
         * ui.layoutFile("./test.xml");
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.layoutFile = function (file) {
         *     ui.layout(files.read(file));
         * };
         */
        layoutFile(path: string): void;

        /**
         * @param xml
         * @param [parent]
         * @param [isAttachedToParent=false]
         * @example
         * "ui";
         * ui.layout(<vertical id="main"><frame/></vertical>);
         * ui.main.addView(ui.inflate(<text text="Hello" textSize="45"/>));
         * ui.main.addView(ui.inflate(<text text="World" textSize="45"/>));
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.inflate = function (xml, parent, isAttachedToParent) {
         *     if (typeof (xml) == 'xml') {
         *         xml = xml.toXMLString();
         *     }
         *     parent = parent || null;
         *     isAttachedToParent = !!isAttachedToParent;
         *     let ctx;
         *     if (typeof (activity) == 'undefined') {
         *         ctx = new android.view.ContextThemeWrapper(context, org.autojs.autojs6.R.style.ScriptTheme);
         *     } else {
         *         ctx = activity;
         *     }
         *     runtime.ui.layoutInflater.setContext(ctx);
         *     return runtime.ui.layoutInflater.inflate(xml.toString(), parent, isAttachedToParent);
         * };
         */
        inflate(xml: Xml, parent?: android.view.ViewGroup, isAttachedToParent?: boolean): UI.View;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.registerWidget = function (name, widget) {
         *     if (typeof (widget) !== 'function') {
         *         throw new TypeError('widget should be a class-like function');
         *     }
         *     ui.__widgets__[name] = widget;
         * };
         */
        registerWidget(name: string, widget: Func): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.setContentView = function (view) {
         *     ui.view = view;
         *     ui.run(function () {
         *         activity.setContentView(view);
         *     });
         * };
         */
        setContentView(view: android.view.View): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.findById = function (id) {
         *     if (!ui.view)
         *         return null;
         *     return ui.findByStringId(ui.view, id);
         * };
         * @see ui.findByStringId
         */
        findById(stringId: string): android.view.View;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.findView = function (id) {
         *     return ui.findById(id);
         * };
         * @see ui.findById
         */
        findView(stringId: string): android.view.View;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.isUiThread = function () {
         *     let Looper = android.os.Looper;
         *     return Looper.myLooper() === Looper.getMainLooper();
         * };
         */
        isUiThread(): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.run = function (action) {
         *     if (ui.isUiThread()) {
         *         return action();
         *     }
         *     let err = null;
         *     let result;
         *     let disposable = global.threads.disposable();
         *     runtime.uiHandler.post(function () {
         *         try {
         *             result = action();
         *             disposable.setAndNotify(true);
         *         } catch (e) {
         *             err = e;
         *             disposable.setAndNotify(true);
         *         }
         *     });
         *     disposable.blockedGet();
         *     if (err) {
         *         throw err;
         *     }
         *     return result;
         * };
         */
        run<R>(action: () => R): R;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.post = function (action, delay) {
         *     if (delay === undefined) {
         *         runtime.getUiHandler().post(wrapUiAction(action));
         *     } else {
         *         runtime.getUiHandler().postDelayed(wrapUiAction(action), delay);
         *     }
         * };
         * function wrapUiAction(action) {
         *     if (typeof (activity) != 'undefined') {
         *         return function () {
         *             return action();
         *         };
         *     }
         *     return function () {
         *         return __exitIfError__(action);
         *     }
         * }
         */
        post(action: Func, delay?: number): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.statusBarColor = function (color) {
         *     if (typeof (color) == 'string') {
         *         color = android.graphics.Color.parseColor(color);
         *     }
         *     if (android.os.Build.VERSION.SDK_INT >= 21) {
         *         ui.run(function () {
         *             activity.getWindow().setStatusBarColor(color);
         *         });
         *     }
         * };
         */
        statusBarColor(color: OmniColor): void;

        statusBarIconLight(isLight?: boolean): void;

        statusBarIconLightBy(refColor: OmniColor): void;

        backgroundColor(color: OmniColor): void;

        navigationBarColor(color: OmniColor): void;

        navigationBarIconLight(isLight?: boolean): void;

        navigationBarIconLightBy(refColor: OmniColor): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.finish = function () {
         *     ui.run(function () {
         *         activity.finish();
         *     });
         * };
         * @see ui.run
         */
        finish(): void;

        keepScreenOn(): void;

        getStatusBarHeight(options?: UI.BarHeightOptions): number;

        getVisibleStatusBarHeight(options?: UI.BarHeightOptions): number;

        getNavigationBarHeight(options?: UI.BarHeightOptions): number;

        getVisibleNavigationBarHeight(options?: UI.BarHeightOptions): number;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * ui.findByStringId = function (view, id) {
         *     return org.autojs.autojs.core.ui.JsViewHelper.findViewByStringId(view, id);
         * };
         */
        findByStringId(view: android.view.View, stringId: string): android.view.View;

    }

    namespace UI {

        interface BarHeightOptions {
            withComputed?: boolean;
            withDimen?: boolean;
            ignoreVisibility?: boolean;
        }

        type View = JsView & Android.Widget.View &
            androidx.cardview.widget.CardView &
            androidx.drawerlayout.widget.DrawerLayout &
            androidx.appcompat.widget.ContentFrameLayout &
            org.autojs.autojs.core.graphics.ScriptCanvasView &
            com.google.android.material.appbar.AppBarLayout &
            com.google.android.material.floatingactionbutton.FloatingActionButton;
        type JsView = JsButton$ & JsEditText$ & JsFrameLayout$ & JsGridView$ &
            JsImageView$ & JsLinearLayout$ & JsListView$ & JsRelativeLayout$ &
            JsSpinner$ & JsTabLayout$ & JsTextView$ & JsToolbar$ & JsViewPager$ & JsWebView$;

        type ViewAttribute = Android.Attribute.ImageView | Android.Attribute.AppBarLayout |
            Android.Attribute.TextView | Android.Attribute.CardView |
            Android.Attribute.FloatingActionButton | string;

        class Widget {

            public __attrs__: {
                [attrName: string]: {
                    getter(): any;
                    setter(view, attrName, value: any, setter): void;
                };
            };

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Widget.prototype.renderInternal = function () {
             *     if (typeof (this.render) === 'function') {
             *         return this.render();
             *     }
             *     return (< />);
             * };
             */
            public renderInternal(): Xml;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Widget.prototype.defineAttr = function (attrName, getter, setter) {
             *     let attrAlias = attrName;
             *     let setter = null;
             *     if (typeof (arguments[1]) == 'string') {
             *         attrAlias = arguments[1];
             *         if (arguments.length >= 3) {
             *             setter = arguments[2];
             *         }
             *     } else if (typeof (arguments[1]) == 'function' && typeof (arguments[2]) != 'function') {
             *         setter = arguments[1];
             *     }
             *     if (!(typeof (arguments[1]) == 'function' && typeof (arguments[2]) == 'function')) {
             *         getter = () => {
             *             return this[attrAlias];
             *         };
             *         setter = (view, attrName, value, setter) => {
             *             this[attrAlias] = value;
             *             setter && setter(view, attrName, value, setter);
             *         };
             *     }
             *     this.__attrs__[attrName] = {
             *         getter: getter,
             *         setter: setter
             *     };
             * };
             */
            public defineAttr(attrName: string, attrAlias?: string, setter?: Func): void;
            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Widget.prototype.defineAttr = function (attrName, getter, setter) {
             *     let attrAlias = attrName;
             *     let setter = null;
             *     if (typeof (arguments[1]) == 'string') {
             *         attrAlias = arguments[1];
             *         if (arguments.length >= 3) {
             *             setter = arguments[2];
             *         }
             *     } else if (typeof (arguments[1]) == 'function' && typeof (arguments[2]) != 'function') {
             *         setter = arguments[1];
             *     }
             *     if (!(typeof (arguments[1]) == 'function' && typeof (arguments[2]) == 'function')) {
             *         getter = () => {
             *             return this[attrAlias];
             *         };
             *         setter = (view, attrName, value, setter) => {
             *             this[attrAlias] = value;
             *             setter && setter(view, attrName, value, setter);
             *         };
             *     }
             *     this.__attrs__[attrName] = {
             *         getter: getter,
             *         setter: setter
             *     };
             * };
             */
            public defineAttr(attrName: string, setter: Func): void;
            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Widget.prototype.defineAttr = function (attrName, getter, setter) {
             *     let attrAlias = attrName;
             *     let applier = null;
             *     if (typeof (arguments[1]) == 'string') {
             *         attrAlias = arguments[1];
             *         if (arguments.length >= 3) {
             *             applier = arguments[2];
             *         }
             *     } else if (typeof (arguments[1]) == 'function' && typeof (arguments[2]) != 'function') {
             *         applier = arguments[1];
             *     }
             *     if (!(typeof (arguments[1]) == 'function' && typeof (arguments[2]) == 'function')) {
             *         getter = () => {
             *             return this[attrAlias];
             *         };
             *         setter = (view, attrName, value, setter) => {
             *             this[attrAlias] = value;
             *             applier && applier(view, attrName, value, setter);
             *         };
             *     }
             *     this.__attrs__[attrName] = {
             *         getter: getter,
             *         setter: setter
             *     };
             * };
             */
            public defineAttr(attrName: string, getter: Func, setter: Func): void;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Widget.prototype.hasAttr = function (attrName) {
             *     return this.__attrs__.hasOwnProperty(attrName);
             * };
             */
            public hasAttr(attrName: string | number | symbol): boolean;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Widget.prototype.setAttr = function (view, attrName, value, setter) {
             *     this.__attrs__[attrName].setter(view, attrName, value, setter);
             * };
             */
            public setAttr(view?, attrName?, value?, setter?): void;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Widget.prototype.getAttr = function (view, attrName, getter) {
             *     return this.__attrs__[attrName].getter(view, attrName, getter);
             * };
             */
            public getAttr(view?, attrName?, getter?): any;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Widget.prototype.notifyViewCreated = function (view) {
             *     if (typeof (this.onViewCreated) == 'function') {
             *         this.onViewCreated(view);
             *     }
             * };
             */
            public notifyViewCreated(view?): void;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Widget.prototype.notifyAfterInflation = function (view) {
             *     if (typeof (this.onFinishInflation) == 'function') {
             *         this.onFinishInflation(view);
             *     }
             * };
             */
            public notifyAfterInflation(view?): void;

            public render(): Xml;

            public onViewCreated(view?): void;

            public onFinishInflation(view?): void;

        }

    }

}

declare function isUiThread(): boolean;

// @ts-ignore
declare type Xml = JSX.Element | { toXMLString(): string };

declare type JsButton$ = org.autojs.autojs.core.ui.widget.JsButton;
declare type JsEditText$ = org.autojs.autojs.core.ui.widget.JsEditText;
declare type JsFrameLayout$ = org.autojs.autojs.core.ui.widget.JsFrameLayout;
declare type JsGridView$ = org.autojs.autojs.core.ui.widget.JsGridView;
declare type JsImageView$ = org.autojs.autojs.core.ui.widget.JsImageView;
declare type JsLinearLayout$ = org.autojs.autojs.core.ui.widget.JsLinearLayout;
declare type JsListView$ = org.autojs.autojs.core.ui.widget.JsListView;
declare type JsRelativeLayout$ = org.autojs.autojs.core.ui.widget.JsRelativeLayout;
declare type JsSpinner$ = org.autojs.autojs.core.ui.widget.JsSpinner;
declare type JsTabLayout$ = org.autojs.autojs.core.ui.widget.JsTabLayout;
declare type JsTextView$ = org.autojs.autojs.core.ui.widget.JsTextView;
declare type JsToolbar$ = org.autojs.autojs.core.ui.widget.JsToolbar;
declare type JsViewPager$ = org.autojs.autojs.core.ui.widget.JsViewPager;
declare type JsWebView$ = org.autojs.autojs.core.ui.widget.JsWebView;
