var cleanupTask = tasks.addDisposableTask({
  path: "jobs/cleanup.js",
  date: new Date(Date.now() + 60 * 1000),
});
var reportTask = tasks.addDisposableTask({
  path: "jobs/report.js",
  date: new Date(Date.now() + 120 * 1000),
});

var allTimedTasks = tasks.queryTimedTasks();
var reportSnapshot = tasks.getTimedTask(reportTask.id);
tasks.removeTimedTask(cleanupTask.id);

var dailyTask = tasks.addDailyTask({
  path: "jobs/daily.js",
  time: "08:30",
});
var weeklyTask = tasks.addWeeklyTask({
  path: "jobs/weekly.js",
  time: "08:30",
  daysOfWeek: ["MONDAY"],
});

var intentTask = tasks.addIntentTask({
  path: "jobs/on-broadcast.js",
  action: "org.example.ACTION",
});
var intentTasks = tasks.queryIntentTasks();
var sameIntentTask = tasks.getIntentTask(intentTask.id);
tasks.removeIntentTask(intentTask.id);

var rawTimedTask = tasks.addTask(reportTask);
tasks.updateTask(reportTask);
tasks.removeTask(rawTimedTask);

var mondayFlag = tasks.daysToTimeFlag(["MONDAY"]);
var days = tasks.timeFlagToDays(mondayFlag);
log(JSON.stringify({
  allTimedTasks: allTimedTasks.length,
  reportSnapshot: !!reportSnapshot,
  dailyTask: !!dailyTask,
  weeklyTask: !!weeklyTask,
  intentTasks: intentTasks.length,
  sameIntentTask: !!sameIntentTask,
  days: days,
}));
