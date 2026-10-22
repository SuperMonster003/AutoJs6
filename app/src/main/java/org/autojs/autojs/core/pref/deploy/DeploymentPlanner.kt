package org.autojs.autojs.core.pref.deploy

import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.archive.ImportEnvironment
import org.autojs.autojs.core.pref.archive.ImportPlan
import org.autojs.autojs.core.pref.archive.ImportPlan.Prerequisite

/**
 * The confirmations of the deployment items of an import (docs/settings/ROADMAP.md T18, D20): from what the
 * environment probe says about this device now ([ImportEnvironment]; the user may have created the directory or
 * installed the Ace editor plugin since the plan was made), [pending] lists the deployments that still need something,
 * each with its unmet prerequisites; [decide] applies the user's choice: confirmed items stay to run, given up ones and
 * those a prerequisite blocks are `DECLINED` (their keys keep the current values, R8), and those settled meanwhile are
 * written directly. [completed] is the plan that writes the keys of the items that succeeded. Pure JVM (R3).
 *
 * zh-CN: 导入中部署项的确认 (ROADMAP T18, D20): 依据环境探测给出的本机现状 ([ImportEnvironment]; 生成计划之后用户可能已创建目录或安装
 * Ace 编辑器插件), [pending] 列出仍需部署的项及其未满足的前置条件; [decide] 应用用户的选择: 确认的项留待运行, 放弃的项与被前置条件
 * 阻止的项计为 `DECLINED` (其键保持当前值, R8), 期间已满足的项直接写入. [completed] 为写入成功项对应键的计划. 纯 JVM (R3).
 */
object DeploymentPlanner {

    /**
     * One confirmation: the [deployment] and the [prerequisites] it still lacks; a [blocked] one cannot run yet.
     * zh-CN: 一项确认: 部署 [deployment] 及其仍缺的前置条件 [prerequisites]; [blocked] 的项暂时无法运行.
     */
    data class Pending(val deployment: ImportPlan.Deployment, val prerequisites: Set<Prerequisite>) {

        val blocked: Boolean
            get() = prerequisites.isNotEmpty()

        /** The id the wizard state keeps for the user's choice ([keyOf]). zh-CN: 向导状态中记录用户选择所用的 id. */
        val key: String
            get() = keyOf(deployment)
    }

    /** The deployments of [plan] that still need something on this device. zh-CN: [plan] 中本机仍需部署的项. */
    @JvmStatic
    fun pending(plan: ImportPlan, environment: ImportEnvironment): List<Pending> =
        plan.deployments.filterNot { environment.isDeployed(it.kind, it.value) }.map { Pending(it, prerequisitesOf(it.kind, environment)) }

    /**
     * [plan] after the user's choice: the deployments of [confirmed] (keys, [keyOf]) that can run stay in
     * [ImportPlan.deployments], those settled on this device meanwhile become puts, every other one is `DECLINED`.
     * zh-CN: 用户选择之后的 [plan]: [confirmed] (键, [keyOf]) 中可以运行的部署留在 [ImportPlan.deployments], 期间已在本机满足的转为写入,
     * 其余计为 `DECLINED`.
     */
    @JvmStatic
    fun decide(plan: ImportPlan, confirmed: Collection<String>, environment: ImportEnvironment): ImportPlan {
        val settled = plan.deployments.filter { environment.isDeployed(it.kind, it.value) }
        val runnable = pending(plan, environment).filter { !it.blocked && it.key in confirmed }.map { it.deployment }
        val declined = plan.deployments - settled.toSet() - runnable.toSet()
        return plan.copy(puts = plan.puts + settled.map { it.toPut() }, deployments = plan.deployments - settled.toSet()).decline(declined)
    }

    /**
     * The plan that writes the keys of [succeeded] once their items have run (T18).
     * zh-CN: 部署项运行成功之后写入 [succeeded] 对应键的计划 (T18).
     */
    @JvmStatic
    fun completed(plan: ImportPlan, succeeded: Collection<ImportPlan.Deployment>): ImportPlan =
        ImportPlan(plan.categories, succeeded.map { it.toPut() }, emptyList(), emptyList(), emptyList())

    /** The id of [deployment]: its store and name. zh-CN: [deployment] 的 id: 存储与名称. */
    @JvmStatic
    fun keyOf(deployment: ImportPlan.Deployment): String = deployment.store.name + ":" + deployment.name

    /** What [kind] needs first on this device (T18). zh-CN: [kind] 在本机需要先满足的前置条件 (T18). */
    @JvmStatic
    fun prerequisitesOf(kind: DeploymentKind, environment: ImportEnvironment): Set<Prerequisite> = when (kind) {
        DeploymentKind.CREATE_DIRECTORY -> emptySet()
        DeploymentKind.DOWNLOAD_EDITOR_FONT -> if (environment.aceEditorPluginInstalled) emptySet() else setOf(Prerequisite.ACE_EDITOR_PLUGIN)
    }
}
