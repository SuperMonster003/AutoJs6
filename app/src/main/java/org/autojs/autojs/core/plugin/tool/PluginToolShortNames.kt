package org.autojs.autojs.core.plugin.tool

/**
 * Settles short names claimed by several providers (MCP Server roadmap P12 D33 / D34): the claim of the highest trust
 * level keeps the name, lower claims are refused, and claims that tie at the highest level are all refused, so that a
 * less trusted plugin can never take over the tool names of a more trusted one. Pure Kotlin for the JVM tests.
 *
 * zh-CN: 裁决被多个提供者声明的短名: 信任级别最高的声明保留该名称, 较低的声明被拒绝, 最高级别并列时全部拒绝, 以免较低信任的插件
 * 夺走较高信任插件的工具名.
 */
object PluginToolShortNames {

    /** One provider's claim; a larger [rank] is more trusted. */
    data class Claim(val provider: String, val shortName: String, val rank: Int)

    /** The refusal reason of every claim that loses, keyed by provider; providers not in the map keep their name. */
    fun resolve(claims: List<Claim>): Map<String, String> {
        val refused = LinkedHashMap<String, String>()
        claims.groupBy { it.shortName }.forEach { (shortName, group) ->
            if (group.size < 2) return@forEach
            val top = group.maxOf { it.rank }
            val winners = group.filter { it.rank == top }
            if (winners.size == 1) {
                val winner = winners.single().provider
                group.filter { it.provider != winner }.forEach { refused[it.provider] = "shortName \"$shortName\" belongs to the more trusted plugin $winner" }
            } else {
                val names = winners.map { it.provider }.sorted()
                group.forEach { claim ->
                    refused[claim.provider] = if (claim.rank == top) {
                        "shortName \"$shortName\" is declared by ${names.size} plugins of the same trust level: ${names.joinToString()}"
                    } else {
                        "shortName \"$shortName\" belongs to more trusted plugins: ${names.joinToString()}"
                    }
                }
            }
        }
        return refused
    }
}
