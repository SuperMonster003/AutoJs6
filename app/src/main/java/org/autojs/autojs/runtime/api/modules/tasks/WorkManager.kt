package org.autojs.autojs.runtime.api.modules.tasks

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.util.RhinoUtils.js_object_assign
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.VarScope

/**
 * Auto.js Pro compatible task facade.
 *
 * The legacy `tasks` module returns the removed/updated task and supports
 * completion callbacks. Pro's `$work_manager` instead exposes a smaller API
 * whose remove operations return the database result. Keeping a separate
 * facade prevents the two public contracts from silently overriding each other.
 */
class WorkManager(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val keys = listOf(
        AugmentableKey("workManager"),
        AugmentableKey("work_manager"),
    )

    override val selfAssignmentFunctions = listOf(
        ::addDailyTask.name,
        ::addWeeklyTask.name,
        ::addDisposableTask.name,
        ::addIntentTask.name,
        ::addBroadcastIntentTask.name,
        ::getTimedTask.name,
        ::getIntentTask.name,
        ::removeTimedTask.name,
        ::removeIntentTask.name,
        ::queryTimedTasks.name,
        ::queryIntentTasks.name,
    )

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addDailyTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? {
            return Tasks.addDailyTask(scriptRuntime, synchronousTaskArguments(scriptRuntime.topLevelScope, args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addWeeklyTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? {
            return Tasks.addWeeklyTask(scriptRuntime, synchronousTaskArguments(scriptRuntime.topLevelScope, args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addDisposableTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? {
            return Tasks.addDisposableTask(scriptRuntime, synchronousTaskArguments(scriptRuntime.topLevelScope, args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? {
            return Tasks.addIntentTask(scriptRuntime, synchronousTaskArguments(scriptRuntime.topLevelScope, args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addBroadcastIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? {
            return Tasks.addBroadcastIntentTask(scriptRuntime, synchronousTaskArguments(scriptRuntime.topLevelScope, args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getTimedTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? {
            return Tasks.getTimedTask(scriptRuntime, firstArgument(args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? {
            return Tasks.getIntentTask(scriptRuntime, firstArgument(args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeTimedTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean? {
            return ensureArgumentsOnlyOne(args) {
                Tasks.getTimedTaskRhino(it)?.let(Tasks::removeTaskSyncResult)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean? {
            return ensureArgumentsOnlyOne(args) {
                Tasks.getIntentTaskRhino(it)?.let(Tasks::removeTaskSyncResult)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun queryTimedTasks(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any {
            return Tasks.queryTimedTasks(scriptRuntime, firstArgument(args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun queryIntentTasks(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any {
            return Tasks.queryIntentTasks(scriptRuntime, firstArgument(args))
        }

        private fun firstArgument(args: Array<out Any?>): Array<Any?> {
            return args.firstOrNull()?.let { arrayOf(it) } ?: emptyArray()
        }

        internal fun synchronousTaskArguments(scope: VarScope, args: Array<out Any?>): Array<Any?> {
            val options = args.firstOrNull() ?: return emptyArray()
            if (options !is NativeObject) return arrayOf(options)
            val copied = newNativeObject(scope)
            js_object_assign(copied, options)
            listOf("callback", "condition", "isAsync", "async").forEach(copied::delete)
            return arrayOf(copied)
        }
    }
}
