package org.autojs.autojs.runtime.api.augment.mime

import eu.medsea.mimeutil.MimeType
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.api.augment.util.Util
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeObject

class JsMime(mimeStr: String) : StringReadable {

    @JvmField
    val raw: String = mimeStr

    @JvmField
    val type: String

    @JvmField
    val subtype: String

    @JvmField
    val mimeType: String

    @JvmField
    val mimeTypeRefined: String

    @JvmField
    val parameters: NativeObject

    init {
        val mime = MimeType(mimeStr)
        type = mime.type
        subtype = mime.subtype
        val parsed = parseMimeExtra(type, subtype, mimeStr)
        mimeType = parsed.mimeType
        mimeTypeRefined = parsed.mimeTypeRefined
        parameters = parsed.parameters
    }

    override fun toStringReadable() = toString()

    override fun toString(): String = listOf(
        "${javaClass.simpleName} {",
        "  type: '$type',",
        "  subtype: '$subtype',",
        "  mimeType: '$mimeType',",
        "  mimeTypeRefined: '$mimeTypeRefined',",
        "  parameters: '${Util.formatRhino(parameters)}',",
        "  raw: '$raw',",
        "}",
    ).joinToString("\n")

    private fun parseMimeExtra(type: String, subtype: String, mimeType: String): MimeExtra {
        val paramObj = newNativeObject()
        val mimeRefined = "$type/$subtype"
        var mimeFull = mimeRefined
        mimeType.split("\\s*;\\s*".toRegex()).drop(1)
            .filter { str -> str.isNotEmpty() }
            .takeIf { list -> list.isNotEmpty() }
            ?.let { list ->
                mimeFull += "; ${list.joinToString("; ")}"
                list.forEach { s ->
                    if (s.contains('=')) {
                        val (k, v) = s.split("\\s*=\\s*".toRegex())
                        paramObj.put(k, paramObj, v)
                    }
                }
            }
        return MimeExtra(mimeFull, mimeRefined, paramObj)
    }

    private data class MimeExtra(val mimeType: String, val mimeTypeRefined: String, val parameters: NativeObject)

}
