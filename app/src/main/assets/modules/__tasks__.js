// noinspection JSUnusedGlobalSymbols,JSUnusedLocalSymbols

/* Overwritten protection. */

let { ui, threads, files } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Tasks}
 */
module.exports = function (scriptRuntime, scope) {
    const TimedTask = org.autojs.autojs.timing.TimedTask;
    const IntentTask = org.autojs.autojs.timing.IntentTask;
    const TimedTaskManager = org.autojs.autojs.timing.TimedTaskManager;
    const ExecutionConfig = org.autojs.autojs.execution.ExecutionConfig;
    const DynamicBroadcastReceivers = org.autojs.autojs.external.receiver.DynamicBroadcastReceivers;

    let _ = {
        Tasks: (/* @IIFE */ () => {
            /**
             * @implements Internal.Tasks
             */
            let Task = function () {
                /* Empty body. */
            };

            Task.prototype = {
                constructor: Task,
                /**
                 * @template {TimedTask$|IntentTask$|null} T
                 * @param {T} task
                 * @return {T}
                 */
                addTask(task) {
                    if (task) {
                        TimedTaskManager.addTask(task);
                    }
                    return task || null;
                },
                /**
                 * @param {Timers.TimedTask.Daily} [options]
                 * @return {TimedTask$|org.autojs.autojs.core.looper.TimerThread|null}
                 */
                addDailyTask(options) {
                    let opt = options || {};

                    let localTime = _.parseDateTime('LocalTime', opt.time || Date.now());
                    let path = _.parsePath(opt.path);
                    let config = _.parseConfig(opt);

                    let timedTask = this.addTask(TimedTask.dailyTask(localTime, path, config));

                    return _.taskFulfilled(timedTask, opt);
                },
                /**
                 * @param {Timers.TimedTask.Weekly} [options]
                 * @return {TimedTask$|org.autojs.autojs.core.looper.TimerThread|null}
                 */
                addWeeklyTask(options) {
                    let timeFlag = 0;
                    let opt = options || {};

                    let daysOfWeek = opt.daysOfWeek || [ _.getCurrentDayOfWeek() ];
                    let daysOfWeekList = _.getDaysOfWeekFlatList();

                    for (let i = 0; i < daysOfWeek.length; i += 1) {
                        let dayString = daysOfWeek[i].toString();
                        let dayIndex = daysOfWeekList.indexOf(dayString.toLowerCase()) % 7;
                        if (dayIndex < 0) {
                            throw Error(`Unknown day: ${dayString}`);
                        }
                        timeFlag |= TimedTask.getDayOfWeekTimeFlag(dayIndex + 1);
                    }

                    let localTime = _.parseDateTime('LocalTime', opt.time || Date.now());
                    let flagsNum = Number(new java.lang.Long(timeFlag));
                    let path = _.parsePath(opt.path);
                    let config = _.parseConfig(opt);

                    let timedTask = this.addTask(TimedTask.weeklyTask(localTime, flagsNum, path, config));

                    return _.taskFulfilled(timedTask, opt);
                },
                /**
                 * @param {Timers.TimedTask.Disposable} options
                 * @return {TimedTask$|org.autojs.autojs.core.looper.TimerThread|null}
                 */
                addDisposableTask(options) {
                    let opt = options || {};

                    let localDateTime = _.parseDateTime('LocalDateTime', opt.date || Date.now());
                    let path = _.parsePath(opt.path);
                    let config = _.parseConfig(opt);

                    let timedTask = this.addTask(TimedTask.disposableTask(localDateTime, path, config));

                    return _.taskFulfilled(timedTask, opt);
                },
                /**
                 * @param {Timers.IntentTask.Basic} [options]
                 * @return {IntentTask$|org.autojs.autojs.core.looper.TimerThread|null}
                 */
                addIntentTask(options) {
                    let opt = options || {};

                    let intentTask = (function $iiFe() {
                        let intentTask = new IntentTask();
                        intentTask.setScriptPath(_.parsePath(opt.path));
                        if (typeof opt.action === 'string') {
                            intentTask.setAction(opt.action);
                            if (opt.action === DynamicBroadcastReceivers.ACTION_STARTUP) {
                                // @Indecision opt.local
                                intentTask.setLocal(true);
                            }
                        }
                        if (typeof opt.dataType === 'string') {
                            intentTask.setDataType(opt.dataType);
                        }
                        if (typeof opt.local === 'boolean') {
                            // @FinalDecision opt.action
                            intentTask.setLocal(opt.local);
                        }
                        return intentTask;
                    })();

                    let timedTask = this.addTask(intentTask);

                    return _.taskFulfilled(timedTask, opt);
                },
                /**
                 * @param {number} id
                 * @return {TimedTask$}
                 */
                getTimedTask(id) {
                    return TimedTaskManager.getTimedTask(id);
                },
                /**
                 * @param {number} id
                 * @return {IntentTask$}
                 */
                getIntentTask(id) {
                    return TimedTaskManager.getIntentTask(id);
                },
                /**
                 * @param {TimedTask$|IntentTask$|null} task
                 * @return {TimedTask$|IntentTask$}
                 */
                removeTask(task) {
                    if (task) {
                        TimedTaskManager.removeTask(task);
                    }
                    return task;
                },
                /**
                 * @param {number} id
                 * @param {Timers.TimedTask.Extension} [options]
                 * @return {TimedTask$|org.autojs.autojs.core.looper.TimerThread|null}
                 */
                removeTimedTask(id, options) {
                    let opt = options || {};
                    let task = this.removeTask(this.getTimedTask(id));
                    return _.taskFulfilled(task, Object.assign(opt, {
                        condition: () => !this.getTimedTask(id),
                    }));
                },
                /**
                 * @param {number} id
                 * @param {Timers.TimedTask.Extension} [options]
                 * @return {TimedTask$|org.autojs.autojs.core.looper.TimerThread|null}
                 */
                removeIntentTask(id, options) {
                    let opt = options || {};
                    let task = this.removeTask(this.getIntentTask(id));
                    return _.taskFulfilled(task, Object.assign(opt, {
                        condition: () => !this.getIntentTask(id),
                    }));
                },
                /**
                 * @param {TimedTask$|null} task
                 * @return {TimedTask$|null}
                 */
                updateTask(task) {
                    if (!task) {
                        return null;
                    }
                    task.setScheduled(false);
                    TimedTaskManager.updateTask(task);
                    return task;
                },
                /**
                 * @param {{path?:string}} [options]
                 * @return {TimedTask$[]}
                 */
                queryTimedTasks(options) {
                    let opt = options || {};
                    let path = opt.path;
                    let list = TimedTaskManager.getAllTasksAsList().toArray();
                    return path ? list.filter(task => task.getScriptPath() === path) : list;
                },
                /**
                 * @param {{path?:string,action?:string}} [options]
                 * @return {IntentTask$[]}
                 */
                queryIntentTasks(options) {
                    let opt = options || {};
                    let { path, action } = opt;

                    let list = TimedTaskManager.getAllIntentTasksAsList().toArray();

                    if (!path && !action) {
                        return list;
                    }

                    let pathTrigger = task => !path || task.getScriptPath() === path;
                    let actionTrigger = task => !action || task.getAction() === action;

                    return list.filter(task => pathTrigger(task) && actionTrigger(task));
                },
                /**
                 * @param flag {number} -- number from 0 to 127
                 * @return {number[]}
                 */
                timeFlagToDays(flag) {
                    let days = [];
                    let binaryString = Number(flag).toString(2);
                    let currentDayNumber = binaryString.length - 1;
                    for (let i of binaryString) {
                        if (i !== '0') {
                            days.unshift(currentDayNumber);
                        }
                        currentDayNumber -= 1;
                    }
                    return days;
                },
                /**
                 * @param days {number[]}
                 * @return {number}
                 */
                daysToTimeFlag(days) {
                    return Array(7).fill(0).reduce((a, b, i) => {
                        return a + (days.includes(i) ? Math.pow(2, i) : 0);
                    }, 0);
                },
            };

            return Task;
        })(),
        parsePath(path) {
            if (!path) {
                throw 'A path is necessary for a task';
            }
            if (!files.exists(path)) {
                scriptRuntime.console.warn(`Specified path "${path}" doesn't exist`);
            }
            return files.path(path);
        },
        /**
         * @param {any} num
         * @param {number|function():number} [def=0]
         * @returns {number}
         */
        parseNumber(num, def) {
            return typeof num === 'number' ? num : typeof def === 'function' ? def() : def || 0;
        },
        /**
         * @param {Object} config
         * @param {number} [config.delay=0]
         * @param {number} [config.interval=0]
         * @param {number} [config.loopTimes=1]
         * @return {org.autojs.autojs.execution.ExecutionConfig}
         */
        parseConfig(config) {
            let execConfig = new ExecutionConfig();
            execConfig.setDelay(_.parseNumber(config.delay, 0));
            execConfig.setInterval(_.parseNumber(config.interval, 0));
            execConfig.setLoopTimes(_.parseNumber(config.loopTimes, 1));
            return execConfig;
        },
        /**
         * @param {'LocalTime'|'LocalDateTime'} clazz
         * @param {string|Date|number} dateTime
         * @return {org.joda.time.LocalTime|org.joda.time.LocalDateTime}
         */
        parseDateTime(clazz, dateTime) {
            let clz = (/* @IIFE */ () => {
                switch (clazz) {
                    case 'LocalTime':
                        return org.joda.time.LocalTime;
                    case 'LocalDateTime':
                        return org.joda.time.LocalDateTime;
                    default:
                        throw Error('Unknown clazz for parseDateTime');
                }
            })();
            if (typeof dateTime === 'string') {
                return clz.parse(dateTime);
            }
            if (dateTime instanceof Date) {
                return new clz(dateTime.getTime());
            }
            if (typeof dateTime === 'number') {
                return new clz(dateTime);
            }
            throw Error('Unknown dateTime for parseDateTime');
        },
        /**
         * @param {TimedTask$|IntentTask$} task
         * @param {Timers.TimedTask.Extension|{}} [options]
         * @return {TimedTask$|org.autojs.autojs.core.looper.TimerThread|null}
         */
        taskFulfilled(task, options) {
            let opt = options || {};

            let run = () => {
                if (task) {
                    let timeout = 2e3;
                    let interval = 50;
                    let condition = opt.condition || (() => task['id'] > 0);
                    while (timeout > 0) {
                        if (condition()) {
                            break;
                        }
                        sleep(interval);
                        timeout -= interval;
                    }
                }
                return opt.callback ? opt.callback(task) : task || null;
            };

            return opt.isAsync || ui.isUiThread() ? threads.start(run) : run();
        },
        getCurrentDayOfWeek() {
            return new Date().getDay();
        },
        getDaysOfWeekFlatList() {
            return [
                'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday',
                'mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun',
                '一', '二', '三', '四', '五', '六', '日',
                1, 2, 3, 4, 5, 6, 0,
                1, 2, 3, 4, 5, 6, 7,
            ].map(x => typeof x === 'string' ? x : x.toString());
        },
    };

    // noinspection UnnecessaryLocalVariableJS
    /** @type {Internal.Tasks} */
    const tasks = new _.Tasks();

    return tasks;
};