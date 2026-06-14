// Type definitions for AutoJs6 internal module shell
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/shell/Shell.kt
 */

declare namespace Internal {

    import AbstractShell = org.autojs.autojs.runtime.api.AbstractShell;

    interface Shell {

        (command: Shell.Command): AbstractShell.Result;
        (command: Shell.Command, withRoot: boolean | number): AbstractShell.Result;
        (command: Shell.Command, commandArgument: Shell.CommandArguments): AbstractShell.Result;
        (command: Shell.Command, commandArgument: Shell.CommandArguments, withRoot: boolean | number): AbstractShell.Result;

        execCommand(command: Shell.Command): AbstractShell.Result;
        execCommand(command: Shell.Command, withRoot: boolean | number): AbstractShell.Result;
        execCommand(command: Shell.Command, commandArgument: Shell.CommandArguments): AbstractShell.Result;
        execCommand(command: Shell.Command, commandArgument: Shell.CommandArguments, withRoot: boolean | number): AbstractShell.Result;

        getCommand(command: Shell.Command): string;
        getCommand(command: Shell.Command, withRoot: boolean | number): string;
        getCommand(command: Shell.Command, commandArgument: Shell.CommandArguments): string;
        getCommand(command: Shell.Command, commandArgument: Shell.CommandArguments, withRoot: boolean | number): string;

        /**
         * @see app.intentToShell
         */
        fromIntent(i: Intent.Common): string;

        kill(app: org.autojs.autojs.util.App | string): boolean;

        currentPackage(): string;

        currentActivity(): string;

        currentComponent(): string;

    }

}

declare namespace Shell {

    type Command = string | string[];

    interface CommandArgumentMap {
        root?: boolean;
        exit?: boolean;
        [name: string]: string | number | boolean | null | undefined;
    }

    type CommandArguments = string | CommandArgumentMap;

}

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.SetScreenMetrics = function (w, h) {
 *     __runtime__.getRootShell().SetScreenMetrics(w, h);
 * };
 * @see org.autojs.autojs.runtime.api.AbstractShell.SetScreenMetrics
 */
declare function SetScreenMetrics(w: number, h: number): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Tap = function (x, y) {
 *     __runtime__.getRootShell().Tap(x, y);
 * };
 * @see org.autojs.autojs.runtime.api.AbstractShell.Tap
 */
declare function Tap(x: number, y: number): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Swipe = function (x1, y1, x2, y2, duration) {
 *     if (arguments.length == 5) {
 *         __runtime__.getRootShell().Swipe(x1, y1, x2, y2, duration);
 *     } else {
 *         __runtime__.getRootShell().Swipe(x1, y1, x2, y2);
 *     }
 * };
 * @see org.autojs.autojs.runtime.api.AbstractShell.Swipe
 */
declare function Swipe(x1: number, y1: number, x2: number, y2: number, duration?: number): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Screencap = function (path) {
 *     __runtime__.getRootShell().Screencap(path);
 * };
 * @see org.autojs.autojs.runtime.api.AbstractShell.Screencap
 */
declare function Screencap(path: string): void;

/**
 * @example
 * // 1: KEYCODE_MENU
 * // 2: KEYCODE_SOFT_RIGHT
 * // 3: KEYCODE_HOME
 * // 4: KEYCODE_BACK
 * // 5: KEYCODE_CALL
 * // 6: KEYCODE_ENDCALL
 * // 7: KEYCODE_0
 * // 8: KEYCODE_1
 * // 9: KEYCODE_2
 * // 10: KEYCODE_3
 * // 11: KEYCODE_4
 * // 12: KEYCODE_5
 * // 13: KEYCODE_6
 * // 14: KEYCODE_7
 * // 15: KEYCODE_8
 * // 16: KEYCODE_9
 * // 17: KEYCODE_STAR
 * // 18: KEYCODE_POUND
 * // 19: KEYCODE_DPAD_UP
 * // 20: KEYCODE_DPAD_DOWN
 * // 21: KEYCODE_DPAD_LEFT
 * // 22: KEYCODE_DPAD_RIGHT
 * // 23: KEYCODE_DPAD_CENTER
 * // 24: KEYCODE_VOLUME_UP
 * // 25: KEYCODE_VOLUME_DOWN
 * // 26: KEYCODE_POWER
 * // 27: KEYCODE_CAMERA
 * // 28: KEYCODE_CLEAR
 * // 29: KEYCODE_A
 * // 30: KEYCODE_B
 * // 31: KEYCODE_C
 * // 32: KEYCODE_D
 * // 33: KEYCODE_E
 * // 34: KEYCODE_F
 * // 35: KEYCODE_G
 * // 36: KEYCODE_H
 * // 37: KEYCODE_I
 * // 38: KEYCODE_J
 * // 39: KEYCODE_K
 * // 40: KEYCODE_L
 * // 41: KEYCODE_M
 * // 42: KEYCODE_N
 * // 43: KEYCODE_O
 * // 44: KEYCODE_P
 * // 45: KEYCODE_Q
 * // 46: KEYCODE_R
 * // 47: KEYCODE_S
 * // 48: KEYCODE_T
 * // 49: KEYCODE_U
 * // 50: KEYCODE_V
 * // 51: KEYCODE_W
 * // 52: KEYCODE_X
 * // 53: KEYCODE_Y
 * // 54: KEYCODE_Z
 * // 55: KEYCODE_COMMA
 * // 56: KEYCODE_PERIOD
 * // 57: KEYCODE_ALT_LEFT
 * // 58: KEYCODE_ALT_RIGHT
 * // 59: KEYCODE_SHIFT_LEFT
 * // 60: KEYCODE_SHIFT_RIGHT
 * // 61: KEYCODE_TAB
 * // 62: KEYCODE_SPACE
 * // 63: KEYCODE_SYM
 * // 64: KEYCODE_EXPLORER
 * // 65: KEYCODE_ENVELOPE
 * // 66: KEYCODE_ENTER
 * // 67: KEYCODE_DEL
 * // 68: KEYCODE_GRAVE
 * // 69: KEYCODE_MINUS
 * // 70: KEYCODE_EQUALS
 * // 71: KEYCODE_LEFT_BRACKET
 * // 72: KEYCODE_RIGHT_BRACKET
 * // 73: KEYCODE_BACKSLASH
 * // 74: KEYCODE_SEMICOLON
 * // 75: KEYCODE_APOSTROPHE
 * // 76: KEYCODE_SLASH
 * // 77: KEYCODE_AT
 * // 78: KEYCODE_NUM
 * // 79: KEYCODE_HEADSETHOOK
 * // 80: KEYCODE_FOCUS
 * // 81: KEYCODE_PLUS
 * // 82: KEYCODE_MENU
 * // 83: KEYCODE_NOTIFICATION
 * // 84: KEYCODE_SEARCH
 * // 85: TAG_LAST_KEYCODE
 * KeyCode(3); // Home button
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.KeyCode = function (keyCode) {
 *     __runtime__.getRootShell().KeyCode(keyCode);
 * };
 * @see org.autojs.autojs.runtime.api.AbstractShell.KeyCode
 * @see android.view.KeyEvent
 * @see https://developer.android.com/reference/android/view/KeyEvent
 */
declare function KeyCode(keyCode: number | string): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Home = function () {
 *     return KeyCode(3);
 * };
 * @see KeyCode
 */
declare function Home(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Back = function () {
 *     return KeyCode(4);
 * };
 * @see KeyCode
 */
declare function Back(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Power = function () {
 *     return KeyCode(26);
 * };
 * @see KeyCode
 */
declare function Power(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Up = function () {
 *     return KeyCode(19);
 * };
 * @see KeyCode
 */
declare function Up(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Down = function () {
 *     return KeyCode(20);
 * };
 * @see KeyCode
 */
declare function Down(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Left = function () {
 *     return KeyCode(21);
 * };
 * @see KeyCode
 */
declare function Left(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Right = function () {
 *     return KeyCode(22);
 * };
 * @see KeyCode
 */
declare function Right(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.OK = function () {
 *     return KeyCode(23);
 * };
 * @see KeyCode
 */
declare function OK(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.VolumeUp = function () {
 *     return KeyCode(24);
 * };
 * @see KeyCode
 */
declare function VolumeUp(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.VolumeDown = function () {
 *     return KeyCode(25);
 * };
 * @see KeyCode
 */
declare function VolumeDown(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Menu = function () {
 *     return KeyCode(1);
 * };
 * @see KeyCode
 */
declare function Menu(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Camera = function () {
 *     return KeyCode(27);
 * };
 * @see KeyCode
 */
declare function Camera(): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Text = function (text) {
 *     __runtime__.getRootShell().Text(text);
 * };
 * @see org.autojs.autojs.runtime.api.AbstractShell.Text
 */
declare function Text(text: string): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.Input = scope.Text;
 * @see Text
 */
declare function Input(text: string): void;
