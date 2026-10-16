package org.autojs.autojs.core.plugin.speech

import android.os.ParcelFileDescriptor
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechLimits
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Pure checks and encoders of the audio files the host hands to the plugin (roadmap D15, D20): the WAV signature test,
 * the input size ceiling and the PCM16 WAV header the host writes around script PCM. Nothing here touches Android, so
 * the JVM tests cover it.
 * zh-CN: 宿主交给插件的音频文件的纯逻辑检查与编码 (路线图 D15, D20): WAV 签名检查, 输入大小上限, 以及宿主包裹脚本 PCM 时写入的 PCM16 WAV 头.
 * 这里不触碰 Android, 因此由 JVM 测试覆盖.
 */
object SpeechAudio {

    /** Bytes of the canonical PCM WAV header [writeWavHeader] writes. zh-CN: [writeWavHeader] 写出的标准 PCM WAV 头的字节数. */
    const val WAV_HEADER_BYTES = 44

    /** Whether the first 12 bytes are a RIFF / WAVE header (the only container of contract version 1). zh-CN: 前 12 字节是否为 RIFF / WAVE 头 (契约版本 1 的唯一容器). */
    fun isWav(head: ByteArray): Boolean = head.size >= 12 &&
        head[0] == 'R'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte() && head[3] == 'F'.code.toByte() &&
        head[8] == 'W'.code.toByte() && head[9] == 'A'.code.toByte() && head[10] == 'V'.code.toByte() && head[11] == 'E'.code.toByte()

    /** Fails with `LIMIT_EXCEEDED` above the contract's file ceiling. zh-CN: 超过契约文件上限时以 `LIMIT_EXCEEDED` 失败. */
    fun requireInputSize(bytes: Long, label: String) {
        if (bytes > SpeechLimits.MAX_AUDIO_FILE_BYTES) {
            throw SpeechErrorMapper.failure(SpeechErrorCodes.LIMIT_EXCEEDED, "$label is $bytes bytes; audio input is limited to ${SpeechLimits.MAX_AUDIO_FILE_BYTES} bytes")
        }
    }

    /** Writes the 44-byte header of a mono PCM16 LE WAV with [dataBytes] of samples. zh-CN: 写出 [dataBytes] 字节样本的单声道 PCM16 LE WAV 的 44 字节头. */
    fun writeWavHeader(out: OutputStream, sampleRate: Int, dataBytes: Int) {
        require(sampleRate in 1..384_000) { "sample rate out of range: $sampleRate" }
        require(dataBytes >= 0 && dataBytes % 2 == 0) { "PCM16 data has an even, non-negative byte count, not $dataBytes" }
        val channels = 1
        val bitsPerSample = 16
        val blockAlign = channels * bitsPerSample / 8
        val header = ByteArray(WAV_HEADER_BYTES)
        fun ascii(offset: Int, text: String) = text.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
        fun int32(offset: Int, value: Int) {
            header[offset] = value.toByte()
            header[offset + 1] = (value ushr 8).toByte()
            header[offset + 2] = (value ushr 16).toByte()
            header[offset + 3] = (value ushr 24).toByte()
        }
        fun int16(offset: Int, value: Int) {
            header[offset] = value.toByte()
            header[offset + 1] = (value ushr 8).toByte()
        }
        ascii(0, "RIFF")
        int32(4, 36 + dataBytes)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        int32(16, 16)
        int16(20, 1)
        int16(22, channels)
        int32(24, sampleRate)
        int32(28, sampleRate * blockAlign)
        int16(32, blockAlign)
        int16(34, bitsPerSample)
        ascii(36, "data")
        int32(40, dataBytes)
        out.write(header)
    }
}

/**
 * One audio input opened read-only for `recognizeFile` / `detectVoice`: the host checks that the script's path is a
 * regular file within the size ceiling and starts with a WAV header before the plugin sees the descriptor. A temporary
 * file (script PCM wrapped into WAV) is deleted on [close].
 * zh-CN: 为 `recognizeFile` / `detectVoice` 以只读方式打开的一个音频输入: 插件看到描述符之前, 宿主先检查脚本路径是大小在上限内且以 WAV
 * 头开始的普通文件. 临时文件 (包裹成 WAV 的脚本 PCM) 在 [close] 时删除.
 */
class SpeechAudioInput private constructor(
    val file: File,
    val descriptor: ParcelFileDescriptor,
    private val temporary: Boolean,
) : Closeable {

    override fun close() {
        runCatching { descriptor.close() }
        if (temporary) file.delete()
    }

    companion object {

        /** Opens the WAV file at [file]. zh-CN: 打开 [file] 处的 WAV 文件. */
        fun open(file: File): SpeechAudioInput {
            if (!file.isFile) throw SpeechErrorMapper.failure(SpeechErrorCodes.IO_FAILED, "audio file not found: ${file.path}")
            if (!file.canRead()) throw SpeechErrorMapper.failure(SpeechErrorCodes.IO_FAILED, "audio file is not readable: ${file.path}")
            SpeechAudio.requireInputSize(file.length(), file.name)
            val head = ByteArray(12)
            val read = try {
                RandomAccessFile(file, "r").use { it.read(head) }
            } catch (e: IOException) {
                throw SpeechErrorMapper.failure(SpeechErrorCodes.IO_FAILED, "audio file cannot be read: ${e.message ?: file.path}", e)
            }
            if (read < 12 || !SpeechAudio.isWav(head)) {
                throw SpeechErrorMapper.failure(SpeechErrorCodes.UNSUPPORTED_FORMAT, "${file.name} is not a WAV file; contract version 1 reads WAV only")
            }
            return SpeechAudioInput(file, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), temporary = false)
        }

        /** Wraps script PCM (mono PCM16 LE at [sampleRate]) into a temporary WAV under [directory]. zh-CN: 把脚本 PCM (单声道 PCM16 LE, [sampleRate]) 包裹为 [directory] 下的临时 WAV. */
        fun fromPcm(directory: File, pcm: ByteArray, sampleRate: Int): SpeechAudioInput {
            SpeechAudio.requireInputSize(pcm.size.toLong() + SpeechAudio.WAV_HEADER_BYTES, "the PCM input")
            if (pcm.size % 2 != 0) throw SpeechErrorMapper.failure(SpeechErrorCodes.INVALID_OPTIONS, "PCM16 audio has an even byte count, not ${pcm.size}")
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw SpeechErrorMapper.failure(SpeechErrorCodes.IO_FAILED, "cannot create ${directory.path}")
            }
            val file = File(directory, "pcm-${UUID.randomUUID()}.wav")
            try {
                file.outputStream().buffered().use { out ->
                    SpeechAudio.writeWavHeader(out, sampleRate, pcm.size)
                    out.write(pcm)
                }
                return SpeechAudioInput(file, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), temporary = true)
            } catch (t: Throwable) {
                file.delete()
                throw if (t is SpeechPluginException) t else SpeechErrorMapper.failure(SpeechErrorCodes.IO_FAILED, "cannot write the PCM input: ${t.message}", t)
            }
        }
    }
}

/**
 * The staged output of `synthesizeToFile` (roadmap D20): the plugin writes into an empty hidden file next to the target,
 * the host checks its size and WAV header and renames it over the target (atomic within one directory), so a failed or
 * cancelled synthesis never leaves a partial file at the script's path.
 * zh-CN: `synthesizeToFile` 的暂存输出 (路线图 D20): 插件写入目标旁边的空隐藏文件, 宿主检查其大小与 WAV 头后重命名覆盖目标 (同一目录内原子),
 * 因此失败或取消的合成不会在脚本路径上留下不完整的文件.
 */
class SpeechOutputFile private constructor(val target: File, val staging: File) {

    fun openStaging(): ParcelFileDescriptor = ParcelFileDescriptor.open(
        staging,
        ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE,
    )

    /** Moves the checked staging file over [target]; answers the written size. zh-CN: 把检查过的暂存文件移到 [target]; 应答写入的大小. */
    fun publish(): Long {
        val size = staging.length()
        if (size <= SpeechAudio.WAV_HEADER_BYTES) {
            discard()
            throw SpeechErrorMapper.failure(SpeechErrorCodes.ENGINE_FAILED, "the plugin wrote no audio")
        }
        if (size > SpeechLimits.MAX_SYNTH_OUTPUT_BYTES) {
            discard()
            throw SpeechErrorMapper.failure(SpeechErrorCodes.LIMIT_EXCEEDED, "the synthesized file exceeds ${SpeechLimits.MAX_SYNTH_OUTPUT_BYTES} bytes")
        }
        val head = ByteArray(12)
        RandomAccessFile(staging, "r").use { it.read(head) }
        if (!SpeechAudio.isWav(head)) {
            discard()
            throw SpeechErrorMapper.failure(SpeechErrorCodes.ENGINE_FAILED, "the plugin wrote no WAV header")
        }
        if (!staging.renameTo(target)) {
            discard()
            throw SpeechErrorMapper.failure(SpeechErrorCodes.IO_FAILED, "cannot move the synthesized file to ${target.path}")
        }
        return size
    }

    fun discard() {
        staging.delete()
    }

    companion object {

        /** Stages an output for [target], creating its parent directories. zh-CN: 为 [target] 准备暂存输出, 并创建其父目录. */
        fun stage(target: File): SpeechOutputFile {
            if (target.isDirectory) throw SpeechErrorMapper.failure(SpeechErrorCodes.INVALID_OPTIONS, "the output path is a directory: ${target.path}")
            val parent = target.absoluteFile.parentFile ?: throw SpeechErrorMapper.failure(SpeechErrorCodes.INVALID_OPTIONS, "the output path has no parent directory")
            if (!parent.isDirectory && !parent.mkdirs()) {
                throw SpeechErrorMapper.failure(SpeechErrorCodes.IO_FAILED, "cannot create ${parent.path}")
            }
            val staging = File(parent, ".speech-${UUID.randomUUID()}.wav.part")
            try {
                if (!staging.createNewFile()) throw IOException("cannot create ${staging.path}")
            } catch (e: IOException) {
                throw SpeechErrorMapper.failure(SpeechErrorCodes.IO_FAILED, "cannot write next to ${target.path}: ${e.message}", e)
            }
            return SpeechOutputFile(target.absoluteFile, staging)
        }
    }
}
