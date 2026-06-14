// Type definitions for AutoJs6 internal module tasks
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026
// noinspection JSUnusedGlobalSymbols

/// <reference path="../index.d.ts" />
/// <reference path="../android.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/tasks/Tasks.kt
 */

declare namespace Internal {

    interface Tasks {

        addTask<T extends TimedTask$ | IntentTask$>(task: T): T;

        addDailyTask<TResult = TimedTask$>(options?: Tasks.TimedTask.Daily<TResult>): Tasks.AddTaskResult<TimedTask$, TResult>;

        addWeeklyTask<TResult = TimedTask$>(options?: Tasks.TimedTask.Weekly<TResult>): Tasks.AddTaskResult<TimedTask$, TResult>;

        addDisposableTask<TResult = TimedTask$>(options?: Tasks.TimedTask.Disposable<TResult>): Tasks.AddTaskResult<TimedTask$, TResult>;

        addIntentTask<TResult = IntentTask$>(options: Tasks.IntentTask.Basic<TResult>): Tasks.AddTaskResult<IntentTask$, TResult>;

        getTimedTask(id: number): TimedTask$ | null;

        getIntentTask(id: number): IntentTask$ | null;

        removeTask(task: TimedTask$ | IntentTask$ | null | undefined): boolean;

        removeTimedTask(id: number): boolean;

        removeIntentTask(id: number): boolean;

        updateTask(task: TimedTask$ | IntentTask$ | null | undefined): boolean;

        queryTimedTasks(options?: { path?: string }): TimedTask$[];

        queryIntentTasks(options?: { path?: string, action?: string }): IntentTask$[];

        timeFlagToDays(flag: number): number[];

        daysToTimeFlag(days: number[]): number;

    }

}

declare namespace Tasks {

    type AsyncTaskResult = org.autojs.autojs.core.looper.TimerThread;

    type AddTaskResult<TTask, TResult = TTask> = TTask | TResult | AsyncTaskResult;

    namespace TimedTask {

        interface Basic<TResult = TimedTask$ | IntentTask$> {

            path: string;

            delay?: number;

            interval?: number;

            loopTimes?: number;

            callback?: (task: TimedTask$ | IntentTask$) => TResult;

            isAsync?: boolean;
            async?: boolean;

        }

        interface Daily<TResult = TimedTask$> extends Basic<TResult> {

            time?: string | Date | number;
            date?: string | Date | number;

        }

        interface Weekly<TResult = TimedTask$> extends Daily<TResult> {

            /**
             * Day(s) of week, for example [3, 'Thu', 'Fri'].
             */
            daysOfWeek?: (string | number)[];

        }

        interface Disposable<TResult = TimedTask$> extends Basic<TResult> {

            time?: string | Date | number;
            date?: string | Date | number;

        }
    }

    namespace IntentTask {

        interface Basic<TResult = IntentTask$> {

            path: string;

            action?: Android.Intent.Action.Strings | string;

            dataType?: string;

            isLocal?: boolean;
            local?: boolean;

            callback?: (task: TimedTask$ | IntentTask$) => TResult;

            isAsync?: boolean;
            async?: boolean;

        }

    }
}

declare type TimedTask$ = org.autojs.autojs.timing.TimedTask;

declare type IntentTask$ = org.autojs.autojs.timing.IntentTask;
