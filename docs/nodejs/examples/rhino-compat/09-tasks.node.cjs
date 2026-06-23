"nodejs";

const compat = require("autojs6:compat");
const tasks = compat.tasks;

async function expectCompatRejection(api, call, expectedCode) {
  try {
    await call();
  } catch (error) {
    if (error && error.code === expectedCode) {
      return { api, code: error.code };
    }
    throw error;
  }
  throw new Error(api + " unexpectedly succeeded");
}

async function main() {
  const firstId = await tasks.addDisposableTask({
    path: "jobs/cleanup.cjs",
    delayMs: 60_000,
    taskTimeoutMs: 30_000,
  });
  const secondId = await tasks.addDisposableTask({
    scriptPath: "jobs/report.cjs",
    delayMs: 120_000,
    taskTimeoutMs: 30_000,
  });

  const allTasks = await tasks.queryTasks();
  const timedTasks = await tasks.queryTimedTasks();
  const secondSnapshot = await tasks.getTimedTask(secondId);
  const firstRemoved = await tasks.removeTask(firstId);
  const secondRemoved = await tasks.removeTimedTask(secondId);

  const unsupported = await Promise.all([
    expectCompatRejection("tasks.addDailyTask", () => tasks.addDailyTask({
      path: "jobs/daily.cjs",
      time: "08:30",
    }), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"),
    expectCompatRejection("tasks.addWeeklyTask", () => tasks.addWeeklyTask({
      path: "jobs/weekly.cjs",
      time: "08:30",
      daysOfWeek: ["MONDAY"],
    }), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"),
    expectCompatRejection("tasks.addIntentTask", () => tasks.addIntentTask({
      path: "jobs/on-broadcast.cjs",
      action: "org.example.ACTION",
    }), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"),
    expectCompatRejection("tasks.queryIntentTasks", () => tasks.queryIntentTasks(),
      "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"),
    expectCompatRejection("tasks.getIntentTask", () => tasks.getIntentTask("intent-task-id"),
      "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"),
    expectCompatRejection("tasks.removeIntentTask", () => tasks.removeIntentTask("intent-task-id"),
      "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"),
  ]);
  const blocked = await expectCompatRejection("tasks.updateTask", () => tasks.updateTask({
    id: secondId,
    path: "jobs/changed.cjs",
  }), "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN");

  console.log(JSON.stringify({
    firstId,
    secondId,
    allTaskCount: allTasks.length,
    timedTaskCount: timedTasks.length,
    secondSnapshot: Boolean(secondSnapshot),
    firstRemoved,
    secondRemoved,
    unsupported,
    blocked,
  }));
}

if (require.main === module && process.env.AUTOJS6_RUN_TASKS_EXAMPLE === "1") {
  main().catch((error) => {
    console.error(error && (error.stack || error.message) || error);
    process.exitCode = 1;
  });
}
