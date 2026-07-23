package org.autojs.autojs.ui.main.scripts

import java.util.Locale

internal data class MediaInfoDialogData(
    val containerFormat: String = "",
    val videoFormat: String = "",
    val audioFormat: String = "",
    val fileSize: String = "",
    val duration: String = "",
    val audioBitRate: String = "",
    val videoBitRate: String = "",
    val trackName: String = "",
    val performer: String = "",
    val album: String = "",
    val width: String = "",
    val height: String = "",
    val aspectRatio: String = "",
    val frameRate: String = "",
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
) {

    fun withEssentialFallbacks(fileSizeFallback: String, containerFormatFallback: String): MediaInfoDialogData {
        return copy(
            containerFormat = containerFormat.ifBlank { containerFormatFallback },
            fileSize = fileSize.ifBlank { fileSizeFallback },
        )
    }

    fun asFallbackReport(failureMessage: String): String = buildString {
        appendLine("# MediaInfo")
        appendLine("Status : Unavailable")
        appendLine("Reason : $failureMessage")
        appendLine()
        appendLine("# Android media metadata")
        reportValue("Container format", containerFormat)
        reportValue("Video format", videoFormat)
        reportValue("Audio format", audioFormat)
        reportValue("File size", fileSize)
        reportValue("Duration", duration)
        reportValue("Audio bit rate", audioBitRate)
        reportValue("Video bit rate", videoBitRate)
        reportValue("Track name", trackName)
        reportValue("Performer", performer)
        reportValue("Album", album)
        if (width.isNotBlank() && height.isNotBlank()) {
            reportValue("Resolution", "$width x $height")
        }
        reportValue("Display aspect ratio", aspectRatio)
        reportValue("Frame rate", frameRate)
    }.trimEnd()

    private fun StringBuilder.reportValue(label: String, value: String) {
        if (value.isNotBlank()) {
            appendLine("$label : $value")
        }
    }
}

internal enum class MediaInfoDialogKind(
    val showsVideoDetails: Boolean = false,
    val showsAudioDetails: Boolean = false,
    val showsSplitLine: Boolean = false,
) {
    VIDEO(
        showsVideoDetails = true,
        showsSplitLine = true,
    ),
    AUDIO(
        showsAudioDetails = true,
        showsSplitLine = true,
    ),
    MEDIA_MENU,
    OTHER,
}

internal fun MediaInfoDialogData.resolveDialogKind(isMediaMenu: Boolean): MediaInfoDialogKind {
    return when {
        hasVideo || videoFormat.isNotBlank() -> MediaInfoDialogKind.VIDEO
        hasAudio || audioFormat.isNotBlank() -> MediaInfoDialogKind.AUDIO
        isMediaMenu -> MediaInfoDialogKind.MEDIA_MENU
        else -> MediaInfoDialogKind.OTHER
    }
}

internal object MediaInfoInformParser {

    fun parse(inform: String): MediaInfoDialogData {
        val sections = linkedMapOf<String, MutableMap<String, String>>()
        var currentSection: String? = null

        inform.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@forEach
            val colonIndex = line.indexOf(':')
            if (colonIndex < 0) {
                currentSection = sectionKind(line)
                currentSection?.let { sections.putIfAbsent(it, linkedMapOf()) }
                return@forEach
            }

            val section = currentSection ?: return@forEach
            val key = normalize(line.substring(0, colonIndex))
            val value = line.substring(colonIndex + 1).trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                sections.getValue(section).putIfAbsent(key, value)
            }
        }

        val general = sections[SECTION_GENERAL].orEmpty()
        val video = sections[SECTION_VIDEO].orEmpty()
        val audio = sections[SECTION_AUDIO].orEmpty()
        return MediaInfoDialogData(
            containerFormat = general.firstValue("format"),
            videoFormat = video.firstValue("format"),
            audioFormat = audio.firstValue("format"),
            fileSize = general.firstValue("filesize"),
            duration = general.firstValue("duration"),
            audioBitRate = audio.firstValue("bitrate", "nominalbitrate", "maximumbitrate"),
            videoBitRate = video.firstValue("bitrate", "nominalbitrate", "maximumbitrate"),
            trackName = general.firstValue("trackname", "track", "title"),
            performer = general.firstValue("performer", "artist"),
            album = general.firstValue("album"),
            width = video.firstValue("width"),
            height = video.firstValue("height"),
            aspectRatio = video.firstValue("displayaspectratio", "aspectratio"),
            frameRate = video.firstValue("framerate"),
            hasVideo = video.isNotEmpty(),
            hasAudio = audio.isNotEmpty(),
        )
    }

    private fun sectionKind(raw: String): String? {
        val normalized = normalize(raw)
        return when {
            normalized.startsWith(SECTION_GENERAL) -> SECTION_GENERAL
            normalized.startsWith(SECTION_VIDEO) -> SECTION_VIDEO
            normalized.startsWith(SECTION_AUDIO) -> SECTION_AUDIO
            else -> null
        }
    }

    private fun Map<String, String>.firstValue(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key -> get(key)?.takeIf(String::isNotBlank) }.orEmpty()
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.US)
            .filter { it in 'a'..'z' || it in '0'..'9' }
    }

    private const val SECTION_GENERAL = "general"
    private const val SECTION_VIDEO = "video"
    private const val SECTION_AUDIO = "audio"
}

internal object MediaInfoFailureMessage {

    fun from(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .toList()
        val message = chain
            .firstOrNull { it.message.orEmpty().contains(LIBRARY_UNAVAILABLE, ignoreCase = true) }
            ?.message
            ?: chain.firstNotNullOfOrNull { it.message?.takeIf(String::isNotBlank) }
            ?: error.javaClass.simpleName
        return message
            .lineSequence()
            .firstOrNull(String::isNotBlank)
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .take(MAX_MESSAGE_LENGTH)
            .ifBlank { "Unknown MediaInfo error" }
    }

    private const val LIBRARY_UNAVAILABLE = "MediaInfo library is not available"
    private const val MAX_CAUSE_DEPTH = 8
    private const val MAX_MESSAGE_LENGTH = 320
}
