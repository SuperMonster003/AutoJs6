package org.autojs.autojs.engine

import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.optArray
import org.autojs.autojs.core.pref.optBoolean
import org.autojs.autojs.core.pref.optInt
import org.autojs.autojs.core.pref.optLong
import org.autojs.autojs.core.pref.optObject
import org.autojs.autojs.core.pref.optString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The task records of [NodeWorkManagerBridgeScheduler] as plain values of [SettingsJson] (docs/settings/ROADMAP.md
 * P5.3): a record that Android's org.json wrote reads the same and is written back with the same members, the record
 * of a scheduled task keeps the member order of the task, and the run history keeps the last runs.
 *
 * zh-CN: [NodeWorkManagerBridgeScheduler] 的任务记录以 [SettingsJson] 普通值表示 (ROADMAP P5.3): Android org.json 写的记录照常
 * 读取, 写回时成员不变; 调度任务的记录保持任务的成员顺序; 运行历史保留最近几次运行.
 */
class NodeWorkManagerBridgeSchedulerJsonTest {

    // A task record as the scheduler wrote it before P5.3: Android's `JSONObject.toString()` (escaped slashes, Long
    // timestamps, nested objects, JSON nulls) of a once task after one successful run.
    private val oldTaskRecord = """{"id":"node-work-7f3a","type":"once","status":"succeeded","scheduler":"androidx.work","backend":"embedded",""" +
        """"entryKind":"scriptPath","scriptPath":"main.cjs","packaged":false,"delayMs":60000,"timeoutMs":30000,""" +
        """"constraints":{"network":true},"engineId":"node-exec-42",""" +
        """"sourceName":"\/storage\/emulated\/0\/Scripts\/node-app\/main.cjs",""" +
        """"workingDirectory":"\/storage\/emulated\/0\/Scripts\/node-app","packageName":"org.autojs.autojs6",""" +
        """"versionName":"6.8.0","versionCode":5456,"createdAtEpochMs":1761134340000,"schemaVersion":1,""" +
        """"noImplicitRetry":true,"scheduledRunnerVersion":1,"executionMode":"scheduled",""" +
        """"launchSurface":"scheduled_runner","updatedAtEpochMs":1761134401250,"lastFailure":null,""" +
        """"runHistory":[{"startedAtEpochMs":1761134401000,"finishedAtEpochMs":1761134401250,"elapsedMs":250,""" +
        """"succeeded":true,"trigger":"work_manager","runnerVersion":1,"attempt":1,""" +
        """"executionId":"node-work-run-node-work-7f3a-1","executionMode":"scheduled","launchSurface":"scheduled_runner",""" +
        """"exitCode":0,"sourceName":"main.cjs","errorCode":null,"errorName":null,"errorMessage":null,"timedOut":false,""" +
        """"stdout":"done at \/sdcard\/out.txt","stderr":"","androidPolicy":{"sdkInt":36,"scheduler":"androidx.work",""" +
        """"longRunningWorkerUsesForegroundService":true,"jobSchedulerQuotaApplies":true,""" +
        """"quotaState":"jobscheduler_quota_shared","errorCode":null,""" +
        """"recommendedAction":"keep scheduled Node tasks bounded and avoid long-running periodic processes"},""" +
        """"nativeValues":{"timing.host.launch_to_first_stdout.ms":"120","embedded_script.execution_mode":"scheduled",""" +
        """"embedded_script.execution_mode_launch_surface":"scheduled_runner",""" +
        """"embedded_script.execution_mode_supported":"true"}}],"workManagerUniqueName":"autojs6-node-work-node-work-7f3a",""" +
        """"workManagerTag":"autojs6-node-work-id-node-work-7f3a","androidPolicy":{"sdkInt":36,"scheduler":"androidx.work",""" +
        """"longRunningWorkerUsesForegroundService":true,"jobSchedulerQuotaApplies":true,""" +
        """"quotaState":"jobscheduler_quota_shared","errorCode":null,""" +
        """"recommendedAction":"keep scheduled Node tasks bounded and avoid long-running periodic processes"},""" +
        """"workRequestId":"3f2c9d4e-0b1a-4c5d-8e7f-a1b2c3d4e5f6","lastRunStatus":"succeeded","runAttemptCount":1,""" +
        """"lastTrigger":"work_manager","lastExecutionId":"node-work-run-node-work-7f3a-1",""" +
        """"lastRun":{"startedAtEpochMs":1761134401000,"finishedAtEpochMs":1761134401250,"elapsedMs":250,"succeeded":true,""" +
        """"trigger":"work_manager","runnerVersion":1,"attempt":1,"executionId":"node-work-run-node-work-7f3a-1",""" +
        """"executionMode":"scheduled","launchSurface":"scheduled_runner","exitCode":0,"sourceName":"main.cjs",""" +
        """"errorCode":null,"errorName":null,"errorMessage":null,"timedOut":false,"stdout":"done at \/sdcard\/out.txt",""" +
        """"stderr":"","androidPolicy":{"sdkInt":36,"scheduler":"androidx.work",""" +
        """"longRunningWorkerUsesForegroundService":true,"jobSchedulerQuotaApplies":true,""" +
        """"quotaState":"jobscheduler_quota_shared","errorCode":null,""" +
        """"recommendedAction":"keep scheduled Node tasks bounded and avoid long-running periodic processes"},""" +
        """"nativeValues":{"timing.host.launch_to_first_stdout.ms":"120","embedded_script.execution_mode":"scheduled",""" +
        """"embedded_script.execution_mode_launch_surface":"scheduled_runner",""" +
        """"embedded_script.execution_mode_supported":"true"}}}"""

    // `NodeAndroidRuntimePolicy.workManagerQuotaDiagnostics` on API 36, as plain values.
    private val policyText = """{"sdkInt":36,"scheduler":"androidx.work","longRunningWorkerUsesForegroundService":true,""" +
        """"jobSchedulerQuotaApplies":true,"quotaState":"jobscheduler_quota_shared","errorCode":null,""" +
        """"recommendedAction":"keep scheduled Node tasks bounded and avoid long-running periodic processes"}"""

    // The task that `ControlledWorkTaskDescriptor.toJson` builds, as plain values.
    private val taskText = """{"id":"node-work-7f3a","type":"once","status":"registered","scheduler":"androidx.work","backend":"embedded",""" +
        """"entryKind":"scriptPath","scriptPath":"main.cjs","packaged":false,"delayMs":60000,"timeoutMs":30000,""" +
        """"constraints":{"network":true},"engineId":"node-exec-42",""" +
        """"sourceName":"/storage/emulated/0/Scripts/node-app/main.cjs",""" +
        """"workingDirectory":"/storage/emulated/0/Scripts/node-app","packageName":"org.autojs.autojs6",""" +
        """"versionName":"6.8.0","versionCode":5456,"createdAtEpochMs":1761134340000}"""

    @Test
    fun taskRecordsThatOrgJsonWroteStillRead() {
        val record = NodeWorkManagerBridgeScheduler.decode(oldTaskRecord)
        assertEquals(
            listOf(
                "id", "type", "status", "scheduler", "backend", "entryKind", "scriptPath", "packaged", "delayMs", "timeoutMs",
                "constraints", "engineId", "sourceName", "workingDirectory", "packageName", "versionName", "versionCode",
                "createdAtEpochMs", "schemaVersion", "noImplicitRetry", "scheduledRunnerVersion", "executionMode", "launchSurface",
                "updatedAtEpochMs", "lastFailure", "runHistory", "workManagerUniqueName", "workManagerTag", "androidPolicy",
                "workRequestId", "lastRunStatus", "runAttemptCount", "lastTrigger", "lastExecutionId", "lastRun",
            ),
            record.keys.toList(),
        )
        assertEquals("/storage/emulated/0/Scripts/node-app", record["workingDirectory"])
        assertEquals("/storage/emulated/0/Scripts/node-app/main.cjs", record["sourceName"])
        assertEquals(1761134340000L, record["createdAtEpochMs"])
        assertEquals(1761134401250L, record["updatedAtEpochMs"])
        assertEquals(5456, record["versionCode"])
        assertEquals(mapOf("network" to true), record["constraints"])
        assertTrue(record.containsKey("lastFailure"))
        assertNull(record["lastFailure"])

        // The reads of the scheduler and the worker.
        assertEquals("once", record.optString("type"))
        assertEquals(60000L, record.optLong("delayMs", 0L))
        assertEquals(30000L, record.optLong("timeoutMs", 0L))
        assertEquals(1, record.optInt("runAttemptCount", 0))
        assertEquals("node-work-run-node-work-7f3a-1", record.optString("lastExecutionId"))
        assertTrue(record.optObject("constraints")!!.optBoolean("network", false))

        val history = record.optArray("runHistory")!!
        assertEquals(1, history.size)
        val run = history[0] as Map<*, *>
        assertEquals(record["lastRun"], run)
        assertEquals(1761134401000L, run["startedAtEpochMs"])
        assertEquals(250, run["elapsedMs"])
        assertEquals(0, run["exitCode"])
        assertEquals("done at /sdcard/out.txt", run["stdout"])
        for (key in listOf("errorCode", "errorName", "errorMessage")) {
            assertTrue(run.containsKey(key))
            assertNull(run[key])
        }
        val policy = run["androidPolicy"] as Map<*, *>
        assertTrue(policy.containsKey("errorCode"))
        assertNull(policy["errorCode"])
        assertEquals(SettingsJson.parseMap(policyText), policy)
        assertEquals(policy, record["androidPolicy"])
    }

    @Test
    fun taskRecordsRoundTripWithTheSameMembers() {
        val text = NodeWorkManagerBridgeScheduler.encode(NodeWorkManagerBridgeScheduler.decode(oldTaskRecord))
        assertEquals(oldTaskRecord.replace("""\/""", "/"), text)
        assertEquals(NodeWorkManagerBridgeScheduler.decode(oldTaskRecord), NodeWorkManagerBridgeScheduler.decode(text))
        // work_manager.list sends the records to scripts as one JSON array.
        val records = listOf(NodeWorkManagerBridgeScheduler.decode(oldTaskRecord), NodeWorkManagerBridgeScheduler.decode(text))
        assertEquals("[$text,$text]", SettingsJson.stringify(records))
    }

    @Test
    fun scheduledRecordsAppendTheSchedulerMembersToTheTask() {
        val task = SettingsJson.parseMap(taskText)
        val record = NodeWorkManagerBridgeScheduler.scheduledRecord(task, "node-work-7f3a", 1761134340005L, SettingsJson.parseMap(policyText))
        // `status` and `scheduler` of the task are replaced in place; `lastFailure` is a JSON null.
        val expected = """{"id":"node-work-7f3a","type":"once","status":"enqueued","scheduler":"androidx.work","backend":"embedded",""" +
            """"entryKind":"scriptPath","scriptPath":"main.cjs","packaged":false,"delayMs":60000,"timeoutMs":30000,""" +
            """"constraints":{"network":true},"engineId":"node-exec-42",""" +
            """"sourceName":"/storage/emulated/0/Scripts/node-app/main.cjs",""" +
            """"workingDirectory":"/storage/emulated/0/Scripts/node-app","packageName":"org.autojs.autojs6",""" +
            """"versionName":"6.8.0","versionCode":5456,"createdAtEpochMs":1761134340000,"schemaVersion":1,""" +
            """"noImplicitRetry":true,"scheduledRunnerVersion":1,"executionMode":"scheduled",""" +
            """"launchSurface":"scheduled_runner","updatedAtEpochMs":1761134340005,"lastFailure":null,"runHistory":[],""" +
            """"workManagerUniqueName":"autojs6-node-work-node-work-7f3a",""" +
            """"workManagerTag":"autojs6-node-work-id-node-work-7f3a","androidPolicy":{"sdkInt":36,"scheduler":"androidx.work",""" +
            """"longRunningWorkerUsesForegroundService":true,"jobSchedulerQuotaApplies":true,""" +
            """"quotaState":"jobscheduler_quota_shared","errorCode":null,""" +
            """"recommendedAction":"keep scheduled Node tasks bounded and avoid long-running periodic processes"}}"""
        assertEquals(expected, NodeWorkManagerBridgeScheduler.encode(record))
        assertEquals("registered", task["status"])
        assertEquals(record, NodeWorkManagerBridgeScheduler.decode(expected))
    }

    @Test
    fun scheduledRecordsOfOrgJsonTasksHoldPlainValues() {
        val task = JSONObject()
            .put("id", "node-work-1")
            .put("type", "periodic")
            .put("intervalMs", 900000L)
            .put("createdAtEpochMs", 1761134340000L)
            .put("constraints", JSONObject().put("charging", true))
            .put("workingDirectory", "/sdcard/Scripts")
        val record = NodeWorkManagerBridgeScheduler.scheduledRecord(
            SettingsJson.normalizeObject(task),
            "node-work-1",
            1761134340005L,
            SettingsJson.parseMap(policyText),
        )
        assertEquals(task.keys().asSequence().toSet(), record.keys.take(6).toSet())
        assertEquals(
            listOf(
                "schemaVersion", "scheduler", "status", "noImplicitRetry", "scheduledRunnerVersion", "executionMode", "launchSurface",
                "updatedAtEpochMs", "lastFailure", "runHistory", "workManagerUniqueName", "workManagerTag", "androidPolicy",
            ),
            record.keys.drop(6),
        )
        assertEquals(900000, record["intervalMs"])
        assertEquals(1761134340000L, record["createdAtEpochMs"])
        assertEquals(mapOf("charging" to true), record["constraints"])
        assertEquals("/sdcard/Scripts", record["workingDirectory"])
        assertEquals(NodeWorkManagerBridgeScheduler.STATUS_ENQUEUED, record["status"])
        assertEquals(record, NodeWorkManagerBridgeScheduler.decode(NodeWorkManagerBridgeScheduler.encode(record)))
    }

    @Test
    fun runHistoryKeepsTheLastEightRuns() {
        val record = NodeWorkManagerBridgeScheduler.decode(oldTaskRecord)
        val keys = record.keys.toList()
        val run = linkedMapOf<String, Any?>("startedAtEpochMs" to 1761138001000L, "attempt" to 2, "errorCode" to null)
        NodeWorkManagerBridgeScheduler.appendRunHistory(record, run)
        run["attempt"] = -1
        assertEquals(keys, record.keys.toList())
        val history = record.optArray("runHistory")!!
        assertEquals(listOf(1, 2), history.map { (it as Map<*, *>)["attempt"] })
        assertTrue((history[1] as Map<*, *>).containsKey("errorCode"))

        for (attempt in 3..10) {
            NodeWorkManagerBridgeScheduler.appendRunHistory(record, linkedMapOf("attempt" to attempt))
        }
        assertEquals((3..10).toList(), record.optArray("runHistory")!!.map { (it as Map<*, *>)["attempt"] })

        val withoutHistory = linkedMapOf<String, Any?>("id" to "node-work-1")
        val brokenHistory = linkedMapOf<String, Any?>("id" to "node-work-1", "runHistory" to "broken")
        for (start in listOf(withoutHistory, brokenHistory)) {
            NodeWorkManagerBridgeScheduler.appendRunHistory(start, mapOf("attempt" to 1))
            assertEquals(listOf(mapOf("attempt" to 1)), start["runHistory"])
        }
    }
}
