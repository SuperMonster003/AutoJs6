package org.autojs.autojs.runtime.api.modules.sample

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptJavaClass
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptNameLookup
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.ScriptProxyGet
import org.autojs.autojs.runtime.api.modules.ScriptProxyObject
import org.autojs.autojs.runtime.api.modules.ScriptProxySet
import org.autojs.autojs.runtime.api.modules.ScriptToString
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.Scriptable
import java.util.concurrent.TimeUnit

/**
 * Test fixture of roadmap P3.0: an assign module (no module object, every member on the global object, the prototype on
 * the global object) with a name lookup prototype (as the Java classes of global) and an install hook defining a name
 * computed at run time; checked by `ScriptModuleShapesTest`. [created] counts the instances.
 */
@ScriptModule(name = "assignProbe", assign = true)
@ScriptJavaClass(name = "AssignProbeUnit", type = TimeUnit::class)
class AssignProbeModule(@Suppress("unused") private val runtime: ScriptRuntime) {

    init {
        created++
    }

    @ScriptProperty
    val assignConstant = "constant"

    @ScriptFunction(name = "toString", toStringRole = ScriptToString.LITERAL)
    fun describe(): String = "[object assignProbe]"

    @ScriptFunction(aliases = ["assignPingAlias"])
    fun assignPing(value: String = "pong"): String = value

    @ScriptGetter
    val assignCreated: Int
        get() = created

    @ScriptPrototype
    val lookup = ScriptNameLookup { scope, name ->
        when (name) {
            "LookupUnit" -> NativeJavaClass(scope, TimeUnit::class.java)
            "LookupFail" -> throw IllegalStateException("lookup")
            else -> Scriptable.NOT_FOUND
        }
    }

    @ScriptInstall
    fun install(installation: ScriptModuleInstallation) {
        installation.defineLazy("assignDynamic", ScriptAttributes.PERMANENT) {
            installation.function("assignDynamic", 1) { _, _, _, args -> if (args.isEmpty()) throw IllegalStateException("no argument") else "dynamic:" + args[0] }
        }
    }

    companion object {
        @JvmStatic
        var created = 0
    }

}

/**
 * Test fixture of roadmap P3.0: a proxy module (as ui / console) whose handlers keep the names starting with `stored` in
 * a map and read everything else from the members, and whose install hook adds a member once the members are built.
 */
@ScriptModule(name = "proxyProbe", proxy = true, attributes = ScriptAttributes.EMPTY)
class ProxyProbeModule {

    private val store = HashMap<String, Any?>()

    @ScriptFunction
    fun ping(): String = "pong"

    @ScriptProperty
    val constant = 1

    @ScriptPrototype
    val proto = ProxyProto()

    @ScriptProxyGet
    fun read(members: Scriptable, name: String): Any? = when {
        name.startsWith("stored") -> if (store.containsKey(name)) store[name] else Scriptable.NOT_FOUND
        name == "failing" -> throw IllegalStateException("read")
        else -> ScriptProxyObject.member(members, name)
    }

    @ScriptProxySet
    fun write(members: Scriptable, name: String, value: Any?) {
        store[name] = value
    }

    @ScriptInstall
    fun install(installation: ScriptModuleInstallation) {
        installation.onBuilt { members ->
            members.defineProperty("builtMark", "marked", ScriptAttributes.PERMANENT)
            members.defineProperty("probeRef", installation.moduleObject("probe"), ScriptAttributes.PERMANENT)
        }
    }

    class ProxyProto {
        fun greet(): String = "hello"
    }

}

/** A proxy module without handlers: reads come from the members, writes define names on the proxy. */
@ScriptModule(name = "plainProxy", proxy = true, dollarPrefix = false)
object PlainProxyModule {

    @ScriptFunction
    fun ping(): String = "plain"

}
