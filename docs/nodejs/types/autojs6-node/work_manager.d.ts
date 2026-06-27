declare module "work_manager" {
  namespace workManager {
    export type WorkType = "once" | "periodic";
    export type WorkStatus = "registered" | "enqueued" | "running" | "succeeded" | "cancelled" | "failed" | "unknown";

    export interface WorkConstraints {
      charging?: boolean;
      network?: boolean;
      idle?: boolean;
    }

    export interface ScheduleOnceDescriptor extends AutoJs6Node.BridgeCallOptions {
      scriptPath?: string;
      entry?: string;
      delayMs?: number;
      constraints?: WorkConstraints;
      packaged?: boolean;
      backend?: "embedded";
      taskTimeoutMs?: number;
    }

    export interface SchedulePeriodicDescriptor extends AutoJs6Node.BridgeCallOptions {
      scriptPath?: string;
      entry?: string;
      intervalMs: number;
      constraints?: WorkConstraints;
      packaged?: boolean;
      backend?: "embedded";
      taskTimeoutMs?: number;
    }

    export interface WorkRun {
      readonly startedAtEpochMs: number;
      readonly finishedAtEpochMs: number;
      readonly elapsedMs: number;
      readonly succeeded: boolean;
      readonly trigger: string;
      readonly runnerVersion: number;
      readonly attempt: number;
      readonly executionId: string;
      readonly executionMode: "scheduled" | string;
      readonly launchSurface: "scheduled_runner" | string;
      readonly exitCode: number | null;
      readonly sourceName: string;
      readonly errorCode: string | null;
      readonly errorName: string | null;
      readonly errorMessage: string | null;
      readonly timedOut: boolean;
      readonly stdout: string;
      readonly stderr: string;
      readonly nativeValues: Readonly<Record<string, string>>;
    }

    export interface WorkTask {
      readonly id: string;
      readonly type: WorkType;
      readonly status: WorkStatus;
      readonly scheduler: string;
      readonly backend: "embedded";
      readonly scriptPath?: string;
      readonly entry?: string;
      readonly entryKind?: "scriptPath" | "projectEntry";
      readonly packaged: boolean;
      readonly delayMs?: number;
      readonly intervalMs?: number | null;
      readonly timeoutMs?: number;
      readonly constraints: Required<WorkConstraints>;
      readonly packageName?: string;
      readonly createdAtEpochMs?: number;
      readonly updatedAtEpochMs?: number;
      readonly lastRunStatus?: WorkStatus;
      readonly lastFailure?: string | null;
      readonly lastRun?: WorkRun;
      readonly runHistory: readonly WorkRun[];
    }

    export interface Policy {
      readonly maxTasksPerProvider: number;
      readonly maxDelayMs: number;
      readonly defaultTaskTimeoutMs: number;
      readonly maxTaskTimeoutMs: number;
      readonly minPeriodicIntervalMs: number;
      readonly maxPeriodicIntervalMs: number;
      readonly allowedConstraints: readonly (keyof WorkConstraints)[];
    }

    export interface WorkManagerModule {
      readonly policy: Policy;
      scheduleOnce(descriptor: ScheduleOnceDescriptor): Promise<string>;
      schedulePeriodic(descriptor: SchedulePeriodicDescriptor): Promise<string>;
      cancel(id: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      list(options?: AutoJs6Node.BridgeCallOptions): Promise<readonly WorkTask[]>;
    }
  }

  const workManager: workManager.WorkManagerModule;
  export = workManager;
}
