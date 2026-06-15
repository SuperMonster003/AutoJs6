// noinspection JSUnusedGlobalSymbols

// Type definitions for AutoJs6 internal module selector
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/selector/Selector.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/core/accessibility/UiSelector.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/core/automator/UiObject.kt
 */

declare namespace Selector {

    type ClassName = Android.Widget.String | 'android.support.v4.view.ViewPager' | 'android.support.v4.widget.DrawerLayout' | 'android.support.v7.app.ActionBar$Tab' | 'android.support.v7.widget.LinearLayoutCompat' | 'android.support.v7.widget.RecyclerView' | 'android.view.View' | 'android.view.ViewGroup' | 'android.webkit.WebView' | 'androidx.appcompat.app.ActionBar$Tab' | 'androidx.appcompat.widget.LinearLayoutCompat' | 'androidx.drawerlayout.widget.DrawerLayout' | 'androidx.recyclerview.widget.RecyclerView' | 'androidx.viewpager.widget.ViewPager' | 'com.tencent.mm.ui.MMImageView' | 'com.tencent.mm.ui.mogic.WxViewPager' | 'com.uc.webkit.bc' | 'com.uc.webview.export.WebView';

    type ClassNameAbbr = Android.Widget.StringAbbr;

}

declare namespace Detect {

    type ResultTypeFuncWithoutParams = 'click' | 'bounds';
    type ResultTypeFuncWithParams = ['child', number]
        | ['setText', string]
        | ['scrollTo', number, number]
        ;
    /**
     * @example
     * '#' | 'w' | 'widget' -> UiObject
     * '$' | 'txt' | 'content' -> string
     * '.' | 'pt' | 'point' -> Point
     * ResultTypeFuncWithoutParams -> any
     * [ResultTypeFuncWithParams, ...params[]] -> any
     */
    type ResultType = ResultTypeFuncWithoutParams
        | '#' | 'w' | 'widget'
        | '$' | 'txt' | 'content'
        | '.' | 'pt' | 'point'
        | ResultTypeFuncWithParams;

    /**
     * @example compass p
     * "p2" // ( .parent().parent() )
     * "p" // ( p1 )
     * "ppp" // ( p3 )
     * "p4ppp12p" // ( p4 pp p12 p -> 4 + 2 + 12 + 1 -> p19 )
     * @example compass c
     * "c0c2c0c1" // ( .child(0).child(2).child(0).child(1) )
     * "c0>2>0>1" // ( .child(0).child(2).child(0).child(1) )
     * "c-3" // ( .child(childCount()-3) )
     * "c-3c2c-1" // ( .child(childCount()-3).child(2).child(childCount()-1) )
     * "c1>2>3>0>-1>1" // ( c1 c2 c3 c0 c-1 c1 )
     * @example compass s
     * "s2" //  ( .parent().child(2) )
     * "s-2" //  ( .parent().child(childCount() - 2) )
     * "s>2" //  ( .parent().child(idxInParent() + 2) )
     * "s<2" //  ( .parent().child(idxInParent() - 2) )
     * @example compass k
     * "k/k1" //  ( .clickable() || .parent().clickable() aka pk/p1k )
     * "k2" //  ( .clickable() || .parent().clickable() || .p2.clickable() aka p2k )
     * "kn" //  ( .clickable() || .pn.clickable() aka pnk )
     */
    type Compass = string
        | 'p' | 'p2' | 'pp' | 'p3' | 'ppp' | 'p4'
        | 'c0' | 'c1' | 'c2' | 'c3' | 'c4'
        | 'c0c0' | 'c0c1' | 'c0c2' | 'c0c3' | 'c0c4'
        | 'c0>0' | 'c0>1' | 'c0>2' | 'c0>3' | 'c0>4'
        | 'c1c0' | 'c1c1' | 'c1c2' | 'c1c3' | 'c1c4'
        | 'c1>0' | 'c1>1' | 'c1>2' | 'c1>3' | 'c1>4'
        | 'c2c0' | 'c2c1' | 'c2c2' | 'c2c3' | 'c2c4'
        | 'c2>0' | 'c2>1' | 'c2>2' | 'c2>3' | 'c2>4'
        | 'c3c0' | 'c3c1' | 'c3c2' | 'c3c3' | 'c3c4'
        | 'c3>0' | 'c3>1' | 'c3>2' | 'c3>3' | 'c3>4'
        | 'c4c0' | 'c4c1' | 'c4c2' | 'c4c3' | 'c4c4'
        | 'c4>0' | 'c4>1' | 'c4>2' | 'c4>3' | 'c4>4'
        | 'pc0' | 'pc1' | 'pc2' | 'pc3' | 'pc4'
        | 'pc0c0' | 'pc0c1' | 'pc0c2' | 'pc0c3' | 'pc0c4'
        | 'pc0>0' | 'pc0>1' | 'pc0>2' | 'pc0>3' | 'pc0>4'
        | 'pc1c0' | 'pc1c1' | 'pc1c2' | 'pc1c3' | 'pc1c4'
        | 'pc1>0' | 'pc1>1' | 'pc1>2' | 'pc1>3' | 'pc1>4'
        | 'pc2c0' | 'pc2c1' | 'pc2c2' | 'pc2c3' | 'pc2c4'
        | 'pc2>0' | 'pc2>1' | 'pc2>2' | 'pc2>3' | 'pc2>4'
        | 'pc3c0' | 'pc3c1' | 'pc3c2' | 'pc3c3' | 'pc3c4'
        | 'pc3>0' | 'pc3>1' | 'pc3>2' | 'pc3>3' | 'pc3>4'
        | 'pc4c0' | 'pc4c1' | 'pc4c2' | 'pc4c3' | 'pc4c4'
        | 'pc4>0' | 'pc4>1' | 'pc4>2' | 'pc4>3' | 'pc4>4'
        | 'p2c0' | 'p2c1' | 'p2c2' | 'p2c3' | 'p2c4'
        | 'p2c0c0' | 'p2c0c1' | 'p2c0c2' | 'p2c0c3' | 'p2c0c4'
        | 'p2c0>0' | 'p2c0>1' | 'p2c0>2' | 'p2c0>3' | 'p2c0>4'
        | 'p2c1c0' | 'p2c1c1' | 'p2c1c2' | 'p2c1c3' | 'p2c1c4'
        | 'p2c1>0' | 'p2c1>1' | 'p2c1>2' | 'p2c1>3' | 'p2c1>4'
        | 'p2c2c0' | 'p2c2c1' | 'p2c2c2' | 'p2c2c3' | 'p2c2c4'
        | 'p2c2>0' | 'p2c2>1' | 'p2c2>2' | 'p2c2>3' | 'p2c2>4'
        | 'p2c3c0' | 'p2c3c1' | 'p2c3c2' | 'p2c3c3' | 'p2c3c4'
        | 'p2c3>0' | 'p2c3>1' | 'p2c3>2' | 'p2c3>3' | 'p2c3>4'
        | 'p2c4c0' | 'p2c4c1' | 'p2c4c2' | 'p2c4c3' | 'p2c4c4'
        | 'p2c4>0' | 'p2c4>1' | 'p2c4>2' | 'p2c4>3' | 'p2c4>4'
        | 's0' | 's1' | 's2' | 's3' | 's4'
        | 's-0' | 's-1' | 's-2' | 's-3' | 's-4'
        | 's<0' | 's<1' | 's<2' | 's<3' | 's<4'
        | 's>0' | 's>1' | 's>2' | 's>3' | 's>4'
        | 'k' | 'k1' | 'k2' | 'k3' | 'k4' | 'k5';

}

declare namespace Pickup {

    type PackageName = string;
    type AppAliases = 'alipay';

    /**
     * @example
     * '#' | 'w' | 'widget' -> UiObject
     * '{}' | 'wc' | 'collection' | 'list' | '#{}' | '{#}' | 'w{}' | '{w}' -> UiObjectCollection
     * '[]' | 'ws' | 'widgets' | '#[]' | '[#]' | 'w[]' | '[w]' -> UiObject[]
     * '$' | 'txt' | 'content' -> string
     * 'contents' | '$[]' | '[$]' | 'txt[]' | '[txt]' | 'content[]' | '[content]' -> string[]
     * '.' | 'pt' | 'point' -> Point
     * 'points' | 'pts' | '.[]' | '[.]' | 'point[]' | '[point]' | 'pt[]' | '[pt]' -> Point[]
     * '@' | 'selector' | 'sel' -> UiSelector
     * '?' | 'exists' -> boolean
     * ResultTypeFuncWithoutParams -> any
     * [ResultTypeFuncWithParams, ...params[]] -> any
     */
    type ResultType = Detect.ResultTypeFuncWithoutParams
        | '#' | 'w' | 'widget'
        | '{}' | 'wc' | 'collection' | 'list' | '#{}' | '{#}' | 'w{}' | '{w}'
        | '[]' | 'ws' | 'widgets' | '#[]' | '[#]' | 'w[]' | '[w]'
        | '$' | 'txt' | 'content'
        | 'contents' | '$[]' | '[$]' | 'txt[]' | '[txt]' | 'content[]' | '[content]'
        | '.' | 'pt' | 'point'
        | 'points' | 'pts' | '.[]' | '[.]' | 'point[]' | '[point]' | 'pt[]' | '[pt]'
        | '@' | 'selector' | 'sel'
        | '?' | 'exists'
        | Detect.ResultTypeFuncWithParams;

    type ObjectSelector = {
        accessibilityFocused?: boolean;
        addFilter?: org.autojs.autojs.core.automator.filter.Filter;
        algorithm?: 'DFS' | 'BFS';
        bounds?: [Left, Top, Right, Bottom];
        boundsContains?: [Left, Top, Right, Bottom];
        boundsInside?: [Left, Top, Right, Bottom];
        checkable?: boolean;
        checked?: boolean;
        className?: Selector.ClassName | Selector.ClassNameAbbr | string;
        classNameContains?: string;
        classNameEndsWith?: string;
        classNameMatches?: string | RegExp;
        classNameMatch?: string | RegExp;
        classNameStartsWith?: string;
        clickable?: boolean;
        column?: number;
        columnCount?: number;
        columnSpan?: number;
        content?: string;
        contentContains?: string;
        contentEndsWith?: string;
        contentInvalid?: boolean;
        contentMatches?: string | RegExp;
        contentMatch?: string | RegExp;
        contentStartsWith?: string;
        contextClickable?: boolean;
        currentApp?: org.autojs.autojs.util.App | PackageName | AppAliases;
        depth?: number;
        desc?: string;
        descContains?: string;
        descEndsWith?: string;
        descMatches?: string | RegExp;
        descMatch?: string | RegExp;
        descStartsWith?: string;
        dismissable?: boolean;
        drawingOrder?: number;
        editable?: boolean;
        enabled?: boolean;
        filter?: org.autojs.autojs.core.automator.filter.BooleanFilter.BooleanSupplier | ((w: UiObject) => boolean);
        focusable?: boolean;
        focused?: boolean;
        id?: string;
        idContains?: string;
        idEndsWith?: string;
        idMatches?: string | RegExp;
        idMatch?: string | RegExp;
        idStartsWith?: string;
        indexInParent?: number;
        longClickable?: boolean;
        multiLine?: boolean;
        packageName?: org.autojs.autojs.util.App | PackageName;
        packageNameContains?: string;
        packageNameEndsWith?: string;
        packageNameMatches?: string | RegExp;
        packageNameMatch?: string | RegExp;
        packageNameStartsWith?: string;
        password?: boolean;
        row?: number;
        rowCount?: number;
        rowSpan?: number;
        screenCenterX?: boolean;
        screenOverlay?: boolean;
        scrollable?: boolean;
        selected?: boolean;
        text?: string;
        textContains?: string;
        textEndsWith?: string;
        textMatches?: string | RegExp;
        textMatch?: string | RegExp;
        textStartsWith?: string;
        visibleToUser?: boolean;
    };

    type ContentMatchAll = '/.+/';
    type ContentSelector = ContentMatchAll | string | number | RegExp;
    type SingleSelector = null | ContentSelector | Internal.Selector | ObjectSelector;
    /**
     * @example
     * string -> content(it)
     * RegExp -> contentMatch(it)
     * UiSelector -> it
     * ObjectSelector -> key1(value1).key2(value2)...
     */
    type Selector = SingleSelector | Array<SingleSelector>;

}

// /**
//  * Locates a UiObject then returns it or its UiSelector or some attributes.
//  * If location fails to locate, returns one of falsies.
//  * @param [locator]
//  * @param [type='widget']
//  * @param [options]
//  * @example
//  * // text('abc').findOnce() || desc('abc').findOnce();
//  * pickup('abc'); // UiObject
//  * pickup('abc', 'w'); // same as above
//  * pickup('abc', 'widget'); // same as above
//  *
//  * // if (text('abc').exists()) return text('abc');
//  * // if (desc('abc').exists()) return desc('abc');
//  * pickup('abc', 'sel'); // UiSelector
//  *
//  * // let w = text('hello').findOnce();
//  * // return w.text() || w.desc();
//  * pickup(text('hello'), 'txt');
//  *
//  * // let w = text('hello').findOnce() || desc('hello').findOnce();
//  * // return w.text() || w.desc();
//  * pickup('hello', 'txt');
//  *
//  * // get the string of selector
//  * pickup(/^abc.+z/, 'sel_str'); // returns 'id'|'text'|'desc'...
//  *
//  * // text('morning').exists() || desc('morning').exists();
//  * pickup('morning', 'exists'); // boolean
//  *
//  * // text/desc('morning').findOnce().parent().parent().child(3)
//  * pickup('morning', 'p2c3');
//  *
//  * // text/desc('morning').findOnce().parent().parent().child(3).clickable();
//  * pickup('morning', 'p2c3', 'clickable');
//  *
//  * // text/desc('hello').findOnce().parent().child(%childCount% - 3)['text'|'desc']
//  * pickup('hello', 's-3', 'txt');
//  *
//  * // text/desc('hello').findOnce().parent().child(%indexInParent% + 2)['text'|'desc']
//  * pickup(['hello', 's>2', 'txt');
//  *
//  * // className('Button').findOnce()
//  * pickup(className('Button'));
//  * pickup({className: 'Button'}); // same as above
//  *
//  * // desc('a').className('Button').boundsInside(0, 0, 720, 1000)
//  * //   .findOnce().parent().child(%indexInParent% + 1).clickable()
//  * pickup([desc('a').className('Button'), {boundsInside: [0, 0, 720, 1000]}], 's>1', 'clickable');
//  *
//  * // let w = className('Button').findOnce().parent().parent().parent().parent()
//  * //   .parent().child(1).child(0).child(0).child(0).child(1);
//  * // return w.parent().child(0);
//  * pickup({className: 'Button'}, 'p5c1>0>0>0>1p1c0');
//  * pickup({className: 'Button'}, 'p5c1>0>0>0>1pc0'); // same as above
//  * pickup({className: 'Button'}, 'p5c1>0>0>0>1s0'); // same as above
//  *
//  * // let w = className('Button').findOnce().parent().parent().parent().parent()
//  * //   .parent().child(1).child(0).child(0).child(0).child(1);
//  * // return w.parent(w.indexInParent() - 1);
//  * pickup({className: 'Button'}, 'p5c1>0>0>0>1s<1');
//  *
//  * // let w = className('Button').findOnce().parent().parent().parent().parent()
//  * //   .parent().child(1).child(0).child(0).child(0).child(1);
//  * // return w.parent().child(w.parent().childCount() - 1);
//  * pickup({className: 'Button'}, 'p5c1>0>0>0>1s-1');
//  *
//  * // pickup(/Language/, 'clickable'); // false
//  * // pickup(/Language/, 'p', 'clickable'); // false
//  * // pickup(/Language/, 'p2', 'clickable'); // false
//  * // pickup(/Language/, 'p3', 'clickable'); // false
//  * // pickup(/Language/, 'p4', 'clickable'); // true
//  * // a11yx.click(pickup(/Language/, 'p4'), 'w'); // widget.click()
//  * pickup([/Language/, 'k4'], 'clickable'); // true
//  * a11yx.click(pickup(/Language/, 'k4'), 'w'); // widget.click()
//  * a11yx.click(pickup(/Language/, 'k5'), 'w'); // same as above
//  * a11yx.click(pickup(/Language/, 'k6'), 'w'); // same as above
//  */
// declare function pickup(locator?: Selector.Pickup.Locators, type?: Selector.Pickup.Result.Type, options?: Selector.Pickup.Options): Selector.Pickup.Results;

declare function pickup<R>(root: UiObject, selector: Pickup.Selector, compass: Detect.Compass, resultType: Pickup.ResultType, callback: (o: any) => R): R;
declare function pickup<R>(root: UiObject, selector: Pickup.Selector, compass: Detect.Compass, callback: (o: any) => R): R;
declare function pickup<R>(root: UiObject, selector: Pickup.Selector, resultType: Pickup.ResultType, callback: (o: any) => R): R;
declare function pickup<R>(root: UiObject, selector: Pickup.Selector, callback: (o: any) => R): R;
declare function pickup<R>(selector: Pickup.Selector, compass: Detect.Compass, resultType: Pickup.ResultType, callback: (o: any) => R): R;
declare function pickup<R>(selector: Pickup.Selector, compass: Detect.Compass, callback: (o: any) => R): R;
declare function pickup<R>(selector: Pickup.Selector, resultType: Pickup.ResultType, callback: (o: any) => R): R;
declare function pickup<R>(selector: Pickup.Selector, callback: (o: any) => R): R;
declare function pickup(root: UiObject, selector: Pickup.Selector, compass: Detect.Compass, resultType: Pickup.ResultType): any;
declare function pickup(root: UiObject, selector: Pickup.Selector, compass: Detect.Compass): UiObject;
declare function pickup(root: UiObject, selector: Pickup.Selector, resultType: Pickup.ResultType): any;
declare function pickup(root: UiObject, selector: Pickup.Selector): UiObject;
declare function pickup(selector: Pickup.Selector, compass: Detect.Compass, resultType: Pickup.ResultType): any;
declare function pickup(selector: Pickup.Selector, compass: Detect.Compass): UiObject;
declare function pickup(selector: Pickup.Selector, resultType: Pickup.ResultType): any;
declare function pickup(selector: Pickup.Selector): UiObject;
declare function pickup(): UiObject;

declare function detect<R>(w: UiObject, compass: Detect.Compass, resultType: Detect.ResultType, callback: (o: any) => R): R;
declare function detect<R>(w: UiObject, compass: Detect.Compass, callback: (w: UiObject) => R): R;
declare function detect<R>(w: UiObject, resultType: Detect.ResultType, callback: (o: any) => R): R;
declare function detect<T extends UiObject, R>(w: T, callback: (w: T) => R): T | R;
declare function detect(w: UiObject, compass: Detect.Compass, resultType: Detect.ResultType): any;
declare function detect(w: UiObject, compass: Detect.Compass): UiObject;
declare function detect(w: UiObject, resultType: Detect.ResultType): any;

declare function existsAll(...selector: Pickup.Selector[]): boolean;

declare function existsOne(...selector: Pickup.Selector[]): boolean;

declare function accessibilityFocus(): boolean;

declare function clearAccessibilityFocus(): boolean;

declare function clearFocus(): boolean;

declare function collapse(): boolean;

declare function contextClick(): boolean;

declare function copy(): boolean;

declare function cut(): boolean;

declare function dismiss(): boolean;

declare function exists(): boolean;

declare function expand(): boolean;

declare function find(max?: number): UiObjectCollection;

declare function findAndReturnList(node: UiObject, max: number): java.util.List<UiObject>;

declare function findOf(node: UiObject, max?: number): UiObjectCollection;

declare function findOnce(index?: number): UiObject;

declare function findOne(timeout: number): UiObject;
/**
 * @deprecated
 */
declare function findOne(): UiObject;

declare function focus(): boolean;

declare function paste(): boolean;

/**
 * @throws {TypeError} - Cannot call method "apply" of undefined
 */
declare function progress(): never;

/**
 * @throws {TypeError} - Cannot call method "apply" of undefined
 */
declare function selection(): never;

declare function scrollBackward(): boolean;

declare function scrollForward(): boolean;

declare function scrollLeft(): boolean;

declare function scrollRight(): boolean;

declare function scrollTo(row: number, column: number): boolean;

declare function select(): boolean;

declare function setProgress(value: number): boolean;

declare function setSelection(start: number, end: number): boolean;

declare function show(): boolean;

declare function untilFind(): UiObjectCollection;

declare function untilFindOne(): UiObject;

declare function waitFor(): void;

/* global selectors. */

declare function accessibilityFocused(b?: boolean): Internal.Selector;

declare function action(...actions: any[]): Internal.Selector;

declare function algorithm(str: string): Internal.Selector;

declare function bottom(min: number, max: number): Internal.Selector;
declare function bottom(value: number): Internal.Selector;

declare function bounds(l: number, t: number, r: number, b: number): Internal.Selector;

declare function boundsBottom(min: number, max: number): Internal.Selector;
declare function boundsBottom(value: number): Internal.Selector;

declare function boundsCenterX(min: number, max: number): Internal.Selector;
declare function boundsCenterX(value: number): Internal.Selector;

declare function boundsCenterY(min: number, max: number): Internal.Selector;
declare function boundsCenterY(value: number): Internal.Selector;

declare function boundsContains(l: number, t: number, r: number, b: number): Internal.Selector;

declare function boundsHeight(min: number, max: number): Internal.Selector;
declare function boundsHeight(value: number): Internal.Selector;

declare function boundsInside(l: number, t: number, r: number, b: number): Internal.Selector;

declare function boundsLeft(min: number, max: number): Internal.Selector;
declare function boundsLeft(value: number): Internal.Selector;

declare function boundsMaxBottom(value: number): Internal.Selector;

declare function boundsMaxCenterX(value: number): Internal.Selector;

declare function boundsMaxCenterY(value: number): Internal.Selector;

declare function boundsMaxHeight(value: number): Internal.Selector;

declare function boundsMaxLeft(value: number): Internal.Selector;

declare function boundsMaxRight(value: number): Internal.Selector;

declare function boundsMaxTop(value: number): Internal.Selector;

declare function boundsMaxWidth(value: number): Internal.Selector;

declare function boundsMinBottom(value: number): Internal.Selector;

declare function boundsMinCenterX(value: number): Internal.Selector;

declare function boundsMinCenterY(value: number): Internal.Selector;

declare function boundsMinHeight(value: number): Internal.Selector;

declare function boundsMinLeft(value: number): Internal.Selector;

declare function boundsMinRight(value: number): Internal.Selector;

declare function boundsMinTop(value: number): Internal.Selector;

declare function boundsMinWidth(value: number): Internal.Selector;

declare function boundsRight(min: number, max: number): Internal.Selector;
declare function boundsRight(value: number): Internal.Selector;

declare function boundsTop(min: number, max: number): Internal.Selector;
declare function boundsTop(value: number): Internal.Selector;

declare function boundsWidth(min: number, max: number): Internal.Selector;
declare function boundsWidth(value: number): Internal.Selector;

declare function centerX(min: number, max: number): Internal.Selector;
declare function centerX(value: number): Internal.Selector;

declare function centerY(min: number, max: number): Internal.Selector;
declare function centerY(value: number): Internal.Selector;

declare function checkable(b?: boolean): Internal.Selector;

declare function checked(b?: boolean): Internal.Selector;

declare function className(str: Selector.ClassName | Selector.ClassNameAbbr | string): Internal.Selector;

declare function classNameContains(str: string): Internal.Selector;

declare function classNameEndsWith(suffix: string): Internal.Selector;

declare function classNameMatch(regex: string | RegExp): Internal.Selector;

/** @deprecated */
declare function classNameMatches(regex: string | RegExp): Internal.Selector;

declare function classNameStartsWith(prefix: string): Internal.Selector;

declare function clickable(b?: boolean): Internal.Selector;

declare function column(d: number): Internal.Selector;

declare function columnCount(d: number): Internal.Selector;

declare function columnSpan(d: number): Internal.Selector;

declare function content(str: string): Internal.Selector;

declare function contentContains(str: string): Internal.Selector;

declare function contentEndsWith(suffix: string): Internal.Selector;

declare function contentInvalid(b?: boolean): Internal.Selector;

declare function contentMatch(regex: string | RegExp): Internal.Selector;

/** @deprecated */
declare function contentMatches(regex: string | RegExp): Internal.Selector;

declare function contentStartsWith(prefix: string): Internal.Selector;

declare function contextClickable(b?: boolean): Internal.Selector;

declare function currentApp(app: org.autojs.autojs.util.App): Internal.Selector;
declare function currentApp(name: string): Internal.Selector;

declare function depth(d: number): Internal.Selector;

declare function desc(str: string): Internal.Selector;

declare function descContains(str: string): Internal.Selector;

declare function descEndsWith(suffix: string): Internal.Selector;

declare function descMatch(regex: string | RegExp): Internal.Selector;

/** @deprecated */
declare function descMatches(regex: string | RegExp): Internal.Selector;

declare function descStartsWith(prefix: string): Internal.Selector;

declare function dismissable(b?: boolean): Internal.Selector;

declare function drawingOrder(order: number): Internal.Selector;

declare function editable(b?: boolean): Internal.Selector;

declare function enabled(b?: boolean): Internal.Selector;

declare function filter(filter: org.autojs.autojs.core.automator.filter.BooleanFilter.BooleanSupplier | ((w: UiObject) => boolean)): Internal.Selector;

declare function focusable(b?: boolean): Internal.Selector;

declare function focused(b?: boolean): Internal.Selector;

declare function height(min: number, max: number): Internal.Selector;
declare function height(value: number): Internal.Selector;

declare function id(str: string): Internal.Selector;

declare function idContains(str: string): Internal.Selector;

declare function idEndsWith(suffix: string): Internal.Selector;

declare function idHex(str: string): Internal.Selector;

declare function idMatch(regex: string | RegExp): Internal.Selector;

/** @deprecated */
declare function idMatches(regex: string | RegExp): Internal.Selector;

declare function idStartsWith(prefix: string): Internal.Selector;

declare function indexInParent(index: number): Internal.Selector;

declare function left(min: number, max: number): Internal.Selector;
declare function left(value: number): Internal.Selector;

declare function longClickable(b?: boolean): Internal.Selector;

declare function maxBottom(value: number): Internal.Selector;

declare function maxCenterX(value: number): Internal.Selector;

declare function maxCenterY(value: number): Internal.Selector;

declare function maxHeight(value: number): Internal.Selector;

declare function maxLeft(value: number): Internal.Selector;

declare function maxRight(value: number): Internal.Selector;

declare function maxTop(value: number): Internal.Selector;

declare function maxWidth(value: number): Internal.Selector;

declare function minBottom(value: number): Internal.Selector;

declare function minCenterX(value: number): Internal.Selector;

declare function minCenterY(value: number): Internal.Selector;

declare function minHeight(value: number): Internal.Selector;

declare function minLeft(value: number): Internal.Selector;

declare function minRight(value: number): Internal.Selector;

declare function minTop(value: number): Internal.Selector;

declare function minWidth(value: number): Internal.Selector;

declare function multiLine(b?: boolean): Internal.Selector;

declare function packageName(app: org.autojs.autojs.util.App): Internal.Selector;
declare function packageName(str: string): Internal.Selector;

declare function packageNameContains(str: string): Internal.Selector;

declare function packageNameEndsWith(suffix: string): Internal.Selector;

declare function packageNameMatch(regex: string | RegExp): Internal.Selector;

/** @deprecated */
declare function packageNameMatches(regex: string | RegExp): Internal.Selector;

declare function packageNameStartsWith(prefix: string): Internal.Selector;

declare function password(b?: boolean): Internal.Selector;

declare function right(min: number, max: number): Internal.Selector;
declare function right(value: number): Internal.Selector;

declare function row(d: number): Internal.Selector;

declare function rowCount(d: number): Internal.Selector;

declare function rowSpan(d: number): Internal.Selector;

declare function screenCenterX(): Internal.Selector;
declare function screenCenterX(b: boolean): Internal.Selector;
declare function screenCenterX(b: boolean, tolerance: number): Internal.Selector;
declare function screenCenterX(tolerance: number): Internal.Selector;

declare function screenCenterY(): Internal.Selector;
declare function screenCenterY(b: boolean): Internal.Selector;
declare function screenCenterY(b: boolean, tolerance: number): Internal.Selector;
declare function screenCenterY(tolerance: number): Internal.Selector;

declare function screenCoverage(): Internal.Selector;
declare function screenCoverage(min: number): Internal.Selector;

declare function scrollable(b?: boolean): Internal.Selector;

declare function selected(b?: boolean): Internal.Selector;

declare function text(str: string): Internal.Selector;

declare function textContains(str: string): Internal.Selector;

declare function textEndsWith(suffix: string): Internal.Selector;

declare function textMatch(regex: string | RegExp): Internal.Selector;

/** @deprecated */
declare function textMatches(regex: string | RegExp): Internal.Selector;

declare function textStartsWith(prefix: string): Internal.Selector;

declare function top(min: number, max: number): Internal.Selector;
declare function top(value: number): Internal.Selector;

declare function visibleToUser(b?: boolean): Internal.Selector;

declare function width(min: number, max: number): Internal.Selector;
declare function width(value: number): Internal.Selector;
