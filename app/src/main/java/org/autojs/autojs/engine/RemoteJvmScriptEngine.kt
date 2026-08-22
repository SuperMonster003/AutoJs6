package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.jvm.JvmSourceExecutionControl
import org.autojs.autojs.core.plugin.jvm.JvmSourceExecutionSink
import org.autojs.autojs.core.plugin.jvm.JvmSourcePublicDiagnosticPolicy
import org.autojs.autojs.core.plugin.jvm.JvmSourceUserExperiencePolicy
import org.autojs.autojs.core.plugin.jvm.JvmSourceUserFacingException
import org.autojs.autojs.core.plugin.jvm.RemoteJvmScriptResult
import org.autojs.autojs.core.plugin.jvm.RemoteJvmSourceException
import org.autojs.autojs.core.plugin.jvm.RemoteJvmSourceHost
import org.autojs.autojs.runtime.api.Console
import org.autojs.autojs.script.JvmFileSource
import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Host-only facade. User bytecode is compiled and loaded by the provider worker; this engine never
 * receives generated artifacts or performs in-process dynamic code loading.
 */
class RemoteJvmScriptEngine internal constructor(
    private val host: RemoteJvmSourceHost,
    private val console: Console,
) : ScriptEngine.AbstractScriptEngine<JvmFileSource>() {
    private val active = AtomicReference<JvmSourceExecutionControl?>()
    private val stopping = AtomicBoolean(false)

    override fun put(name: String, value: Any?) = Unit

    override fun init() {
        check(!isDestroyed && !stopping.get()) { "Remote JVM script engine is stopped" }
    }

    override fun execute(scriptSource: JvmFileSource): Any {
        check(!isDestroyed && !stopping.get()) { "Remote JVM script engine is stopped" }
        val control = JvmSourceExecutionControl()
        check(active.compareAndSet(null, control)) { "Remote JVM script engine already has an active execution" }
        if (stopping.get()) {
            active.compareAndSet(control, null)
            control.close()
            error("Remote JVM script engine is stopped")
        }
        return try {
            host.execute(scriptSource, control, ConsoleSink())
        } catch (error: RemoteJvmSourceException) {
            // ScriptEngineService is a user-facing boundary. Do not surface the internal cause
            // chain: transport failures may contain component, Binder, UID/PID, or path details.
            throw error.toUserFacingException()
        } catch (error: JvmSourceUserFacingException) {
            throw error
        } catch (_: RuntimeException) {
            // selectedComponent, snapshot, and adapter implementations are outside the stable UX
            // contract. Their arbitrary messages must not become user-visible diagnostics.
            throw JvmSourceUserFacingException(JvmSourceUserExperiencePolicy.hostUnavailable())
        } finally {
            active.compareAndSet(control, null)
        }
    }

    override fun forceStop() {
        stopping.set(true)
        active.get()?.close()
    }

    @Synchronized
    override fun destroy() {
        if (isDestroyed) return
        forceStop()
        host.close()
        super.destroy()
    }

    /**
     * Publishes as the execution progresses instead of only on success: a compilation failure must
     * still show its ECJ diagnostics, otherwise the user only sees a stable error code.
     * zh-CN: 按执行过程发布而非仅成功时发布, 否则编译失败只剩一个错误码, 看不到 ECJ 报错.
     */
    private inner class ConsoleSink : JvmSourceExecutionSink {
        override fun publishDiagnostics(diagnostics: List<JvmSourceDiagnostic>) {
            diagnostics.forEach { diagnostic ->
                val projected = JvmSourcePublicDiagnosticPolicy.project(diagnostic)
                val location = projected.sourceFileName?.let { fileName ->
                    "$fileName:${projected.line}:${projected.column}: "
                }.orEmpty()
                val message = "$location${projected.code}: ${projected.message}"
                when (projected.severity) {
                    JvmDiagnosticSeverity.INFO -> console.info(message)
                    JvmDiagnosticSeverity.WARNING -> console.warn(message)
                    JvmDiagnosticSeverity.ERROR -> console.error(message)
                }
            }
        }

        override fun publishOutput(stdout: String, stderr: String) {
            if (stdout.isNotEmpty()) console.log(stdout)
            if (stderr.isNotEmpty()) console.error(stderr)
        }
    }
}
