package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ai.AiService
import org.autojs.autojs.runtime.api.epub.EpubService
import org.autojs.autojs.runtime.api.gallery.GalleryService
import org.autojs.autojs.runtime.api.installer.InstallerService
import org.autojs.autojs.runtime.api.mail.MailService
import org.autojs.autojs.runtime.api.modules.compose.Compose
import org.autojs.autojs.runtime.api.speech.SpeechService
import org.autojs.autojs.runtime.api.terminal.TerminalService
import org.autojs.autojs.runtime.api.tts.TtsService
import java.lang.reflect.Field

/**
 * The private fields of `ScriptRuntime` that the modules of roadmap P4.1 hand to the current augment modules: the
 * services of the plugin modules and the compose instance the host holds. They stay private because a script reaches
 * every public member of `runtime` (`AiInputParsingTest` keeps AiService out of them; a Kotlin `internal` member is
 * public in the bytecode), so they are read here as `TtsServices` reads the TTS service (P3.5).
 *
 * zh-CN: P4.1 的模块交给现有 augment 模块的 ScriptRuntime 私有字段: 插件模块的服务与宿主持有的 compose 实例. 它们保持私有, 因为脚本可以访问 runtime 的
 * 全部公开成员 (AiInputParsingTest 守护 AiService 不在其中; Kotlin 的 internal 成员在字节码中是公开的), 因此像 TtsServices 读取 TTS 服务 (P3.5) 一样在此读取.
 */
internal object ScriptRuntimeFields {

    private val ai = field("ai")
    private val tts = field("tts")
    private val mail = field("mail")
    private val installer = field("installer")
    private val epub = field("epub")
    private val gallery = field("gallery")
    private val terminal = field("terminal")
    private val speech = field("speech")
    private val compose = field("compose")

    fun ai(runtime: ScriptRuntime) = ai.get(runtime) as AiService

    fun tts(runtime: ScriptRuntime) = tts.get(runtime) as TtsService

    fun mail(runtime: ScriptRuntime) = mail.get(runtime) as MailService

    fun installer(runtime: ScriptRuntime) = installer.get(runtime) as InstallerService

    fun epub(runtime: ScriptRuntime) = epub.get(runtime) as EpubService

    fun gallery(runtime: ScriptRuntime) = gallery.get(runtime) as GalleryService

    fun terminal(runtime: ScriptRuntime) = terminal.get(runtime) as TerminalService

    fun speech(runtime: ScriptRuntime) = speech.get(runtime) as SpeechService

    /** The compose instance of [runtime]: set by the old augment call, or by the new layer's ComposeModule. */
    fun compose(runtime: ScriptRuntime) = compose.get(runtime) as Compose?

    fun setCompose(runtime: ScriptRuntime, value: Compose) = compose.set(runtime, value)

    private fun field(name: String): Field = ScriptRuntime::class.java.getDeclaredField(name).apply { isAccessible = true }

}
