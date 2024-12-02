package org.autojs.autojs.runtime.api

import android.webkit.MimeTypeMap
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.autojs.autojs.pio.PFiles
import eu.medsea.mimeutil.MimeUtil2

@Suppress("MayBeConstant", "unused", "SpellCheckingInspection", "GrazieInspection")
object Mime : MimeUtil2() {

    @JvmStatic
    fun getMediaType(mediaType: String): MediaType = mediaType.toMediaType()

    @JvmStatic
    fun parseMediaType(mediaType: String): MediaType? = mediaType.toMediaTypeOrNull()

    @JvmStatic
    fun fromFile(path: String): String? = PFiles.getExtension(path).let { ext ->
        if (ext.isEmpty()) WILDCARD else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    @JvmStatic
    fun fromFileOr(path: String, defaultType: String?): String = fromFile(path) ?: defaultType ?: WILDCARD

    @JvmStatic
    fun fromFileOrWildcard(path: String): String = fromFileOr(path, WILDCARD)

    /** Constant: "application/atom+xml" */
    @JvmField
    val APPLICATION_ATOM_XML = "application/atom+xml"

    /** Constant: "application/x-www-form-urlencoded" */
    @JvmField
    val APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded"

    /** Constant: "application/x-www-form-urlencoded" */
    @JvmField
    val APPLICATION_FORM_URLENCODED = APPLICATION_X_WWW_FORM_URLENCODED

    /** Constant: "application/json-patch+json" */
    @JvmField
    val APPLICATION_JSON_PATCH_JSON = "application/json-patch+json"

    /** Constant: "application/svg+xml" */
    @JvmField
    val APPLICATION_SVG_XML = "application/svg+xml"

    /** Constant: "application/xhtml+xml" */
    @JvmField
    val APPLICATION_XHTML_XML = "application/xhtml+xml"

    /** Constant: "*" */
    @JvmField
    val MEDIA_TYPE_WILDCARD = "*"

    /** Constant: "multipart/form-data" */
    @JvmField
    val MULTIPART_FORM_DATA = "multipart/form-data"

    /** Constant: "text/event-stream" */
    @JvmField
    val SERVER_SENT_EVENTS = "text/event-stream"

    /** Constant: "text/event-stream" */
    @JvmField
    val TEXT_EVENT_STREAM = "text/event-stream"

    /** Constant: "text/xml" */
    @JvmField
    val TEXT_XML = "text/xml"

    /** Constant: "&#42;/&#42;" */
    @JvmField
    val WILDCARD = "*/*"

    /** Constant: "application/bz2" */
    @JvmField
    val APPLICATION_BZ2 = "application/bz2"

    /** Constant: "application/gzip" */
    @JvmField
    val APPLICATION_GZIP = "application/gzip"

    /** Constant: "application/java-archive" */
    @JvmField
    val APPLICATION_JAVA_ARCHIVE = "application/java-archive"

    /** Constant: "application/json" */
    @JvmField
    val APPLICATION_JSON = "application/json"

    /** Constant: "application/msword" */
    @JvmField
    val APPLICATION_MSWORD = "application/msword"

    /** Constant: "application/msword" */
    @JvmField
    val APPLICATION_MS_WORD = APPLICATION_MSWORD

    /** Constant: "application/octet-stream" */
    @JvmField
    val APPLICATION_OCTET_STREAM = "application/octet-stream"

    /** Constant: "application/oda" */
    @JvmField
    val APPLICATION_ODA = "application/oda"

    /** Constant: "application/pdf" */
    @JvmField
    val APPLICATION_PDF = "application/pdf"

    /** Constant: "application/postscript" */
    @JvmField
    val APPLICATION_POSTSCRIPT = "application/postscript"

    /** Constant: "application/rtf" */
    @JvmField
    val APPLICATION_RTF = "application/rtf"

    /** Constant: "application/vnd.ms-excel" */
    @JvmField
    val APPLICATION_VND_MS_EXCEL = "application/vnd.ms-excel"

    /** Constant: "application/vnd.ms-excel" */
    @JvmField
    val APPLICATION_MS_EXCEL = APPLICATION_VND_MS_EXCEL

    /** Constant: "application/vnd.ms-excel" */
    @JvmField
    val APPLICATION_MSEXCEL = APPLICATION_VND_MS_EXCEL

    /** Constant: "application/vnd.ms-powerpoint" */
    @JvmField
    val APPLICATION_VND_MS_POWERPOINT = "application/vnd.ms-powerpoint"

    /** Constant: "application/vnd.ms-powerpoint" */
    @JvmField
    val APPLICATION_MS_POWERPOINT = APPLICATION_VND_MS_POWERPOINT

    /** Constant: "application/vnd.ms-powerpoint" */
    @JvmField
    val APPLICATION_MSPOWERPOINT = APPLICATION_VND_MS_POWERPOINT

    /** Constant: "application/vnd.oasis.opendocument.presentation" */
    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_PRESENTATION = "application/vnd.oasis.opendocument.presentation"

    /** Constant: "application/vnd.oasis.opendocument.presentation" */
    @JvmField
    val APPLICATION_OASIS_OPENDOCUMENT_PRESENTATION = APPLICATION_VND_OASIS_OPENDOCUMENT_PRESENTATION

    /** Constant: "application/vnd.oasis.opendocument.presentation" */
    @JvmField
    val APPLICATION_VND_OASIS_PRESENTATION = APPLICATION_VND_OASIS_OPENDOCUMENT_PRESENTATION

    /** Constant: "application/vnd.oasis.opendocument.presentation" */
    @JvmField
    val APPLICATION_OASIS_PRESENTATION = APPLICATION_VND_OASIS_PRESENTATION

    /** Constant: "application/vnd.oasis.opendocument.spreadsheet" */
    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_SPREADSHEET = "application/vnd.oasis.opendocument.spreadsheet"

    /** Constant: "application/vnd.oasis.opendocument.spreadsheet" */
    @JvmField
    val APPLICATION_OASIS_OPENDOCUMENT_SPREADSHEET = APPLICATION_VND_OASIS_OPENDOCUMENT_SPREADSHEET

    /** Constant: "application/vnd.oasis.opendocument.spreadsheet" */
    @JvmField
    val APPLICATION_VND_OASIS_SPREADSHEET = APPLICATION_VND_OASIS_OPENDOCUMENT_SPREADSHEET

    /** Constant: "application/vnd.oasis.opendocument.spreadsheet" */
    @JvmField
    val APPLICATION_OASIS_SPREADSHEET = APPLICATION_VND_OASIS_SPREADSHEET

    /** Constant: "application/vnd.oasis.opendocument.text" */
    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT = "application/vnd.oasis.opendocument.text"

    /** Constant: "application/vnd.oasis.opendocument.text" */
    @JvmField
    val APPLICATION_OASIS_OPENDOCUMENT_TEXT = APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT

    /** Constant: "application/vnd.oasis.opendocument.text" */
    @JvmField
    val APPLICATION_VND_OASIS_TEXT = APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT

    /** Constant: "application/vnd.oasis.opendocument.text" */
    @JvmField
    val APPLICATION_OASIS_TEXT = APPLICATION_VND_OASIS_TEXT

    /** Constant: "application/vnd.openxmlformats-officedocument.presentationml.presentation" */
    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    /** Constant: "application/vnd.openxmlformats-officedocument.presentationml.presentation" */
    @JvmField
    val APPLICATION_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION = APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION

    /** Constant: "application/vnd.openxmlformats-officedocument.presentationml.presentation" */
    @JvmField
    val APPLICATION_VND_OPENXML_PRESENTATION = APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION

    /** Constant: "application/vnd.openxmlformats-officedocument.presentationml.presentation" */
    @JvmField
    val APPLICATION_OPENXML_PRESENTATION = APPLICATION_VND_OPENXML_PRESENTATION

    /** Constant: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" */
    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Constant: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" */
    @JvmField
    val APPLICATION_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET = APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET

    /** Constant: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" */
    @JvmField
    val APPLICATION_VND_OPENXML_SHEET = APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET

    /** Constant: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" */
    @JvmField
    val APPLICATION_OPENXML_SHEET = APPLICATION_VND_OPENXML_SHEET

    /** Constant: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" */
    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** Constant: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" */
    @JvmField
    val APPLICATION_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT = APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT

    /** Constant: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" */
    @JvmField
    val APPLICATION_VND_OPENXML_DOCUMENT = APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT

    /** Constant: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" */
    @JvmField
    val APPLICATION_OPENXML_DOCUMENT = APPLICATION_VND_OPENXML_DOCUMENT

    /** Constant: "application/vnd.rar" */
    @JvmField
    val APPLICATION_VND_RAR = "application/vnd.rar"

    /** Constant: "application/x-7z-compressed" */
    @JvmField
    val APPLICATION_X_7Z_COMPRESSED = "application/x-7z-compressed"

    /** Constant: "application/x-bcpio" */
    @JvmField
    val APPLICATION_X_BCPIO = "application/x-bcpio"

    /** Constant: "application/x-cpio" */
    @JvmField
    val APPLICATION_X_CPIO = "application/x-cpio"

    /** Constant: "application/x-dvi" */
    @JvmField
    val APPLICATION_X_DVI = "application/x-dvi"

    /** Constant: "application/x-gtar" */
    @JvmField
    val APPLICATION_X_GTAR = "application/x-gtar"

    /** Constant: "application/x-hdf" */
    @JvmField
    val APPLICATION_X_HDF = "application/x-hdf"

    /** Constant: "application/x-latex" */
    @JvmField
    val APPLICATION_X_LATEX = "application/x-latex"

    /** Constant: "application/x-netcdf" */
    @JvmField
    val APPLICATION_X_NETCDF = "application/x-netcdf"

    /** Constant: "application/x-shar" */
    @JvmField
    val APPLICATION_X_SHAR = "application/x-shar"

    /** Constant: "application/x-sv4cpio" */
    @JvmField
    val APPLICATION_X_SV4CPIO = "application/x-sv4cpio"

    /** Constant: "application/x-sv4crc" */
    @JvmField
    val APPLICATION_X_SV4CRC = "application/x-sv4crc"

    /** Constant: "application/x-tar" */
    @JvmField
    val APPLICATION_X_TAR = "application/x-tar"

    /** Constant: "application/x-tex" */
    @JvmField
    val APPLICATION_X_TEX = "application/x-tex"

    /** Constant: "application/x-texinfo" */
    @JvmField
    val APPLICATION_X_TEXINFO = "application/x-texinfo"

    /** Constant: "application/x-troff" */
    @JvmField
    val APPLICATION_X_TROFF = "application/x-troff"

    /** Constant: "application/x-troff-man" */
    @JvmField
    val APPLICATION_X_TROFF_MAN = "application/x-troff-man"

    /** Constant: "application/x-troff-me" */
    @JvmField
    val APPLICATION_X_TROFF_ME = "application/x-troff-me"

    /** Constant: "application/x-troff-ms" */
    @JvmField
    val APPLICATION_X_TROFF_MS = "application/x-troff-ms"

    /** Constant: "application/x-troff-msvideo" */
    @JvmField
    val APPLICATION_X_TROFF_MSVIDEO = "application/x-troff-msvideo"

    /** Constant: "application/x-ustar" */
    @JvmField
    val APPLICATION_X_USTAR = "application/x-ustar"

    /** Constant: "application/x-wais-source" */
    @JvmField
    val APPLICATION_X_WAIS_SOURCE = "application/x-wais-source"

    /** Constant: "application/xml" */
    @JvmField
    val APPLICATION_XML = "application/xml"

    /** Constant: "application/zip" */
    @JvmField
    val APPLICATION_ZIP = "application/zip"

    /** Constant: "audio/aac" */
    @JvmField
    val AUDIO_AAC = "audio/aac"

    /** Constant: "audio/basic" */
    @JvmField
    val AUDIO_BASIC = "audio/basic"

    /** Constant: "audio/flac" */
    @JvmField
    val AUDIO_FLAC = "audio/flac"

    /** Constant: "audio/mp4" */
    @JvmField
    val AUDIO_MP4 = "audio/mp4"

    /** Constant: "audio/mpeg" */
    @JvmField
    val AUDIO_MPEG = "audio/mpeg"

    /** Constant: "audio/ogg" */
    @JvmField
    val AUDIO_OGG = "audio/ogg"

    /** Constant: "audio/x-aiff" */
    @JvmField
    val AUDIO_X_AIFF = "audio/x-aiff"

    /** Constant: "audio/aiff" */
    @JvmField
    val AUDIO_AIFF = "audio/aiff"

    /** Constant: "audio/x-wav" */
    @JvmField
    val AUDIO_X_WAV = "audio/x-wav"

    /** Constant: "audio/wav" */
    @JvmField
    val AUDIO_WAV = "audio/wav"

    /** Constant: "image/bmp" */
    @JvmField
    val IMAGE_BMP = "image/bmp"

    /** Constant: "image/gif" */
    @JvmField
    val IMAGE_GIF = "image/gif"

    /** Constant: "image/ief" */
    @JvmField
    val IMAGE_IEF = "image/ief"

    /** Constant: "image/jpeg" */
    @JvmField
    val IMAGE_JPEG = "image/jpeg"

    /** Constant: "image/png" */
    @JvmField
    val IMAGE_PNG = "image/png"

    /** Constant: "image/svg+xml" */
    @JvmField
    val IMAGE_SVG_XML = "image/svg+xml"

    /** Constant: "image/tiff" */
    @JvmField
    val IMAGE_TIFF = "image/tiff"

    /** Constant: "image/vnd.fpx" */
    @JvmField
    val IMAGE_VND_FPX = "image/vnd.fpx"

    /** Constant: "image/webp" */
    @JvmField
    val IMAGE_WEBP = "image/webp"

    /** Constant: "image/x-cmu-rast" */
    @JvmField
    val IMAGE_X_CMU_RAST = "image/x-cmu-rast"

    /** Constant: "image/x-portable-anymap" */
    @JvmField
    val IMAGE_X_PORTABLE_ANYMAP = "image/x-portable-anymap"

    /** Constant: "image/x-portable-bitmap" */
    @JvmField
    val IMAGE_X_PORTABLE_BITMAP = "image/x-portable-bitmap"

    /** Constant: "image/x-portable-graymap" */
    @JvmField
    val IMAGE_X_PORTABLE_GRAYMAP = "image/x-portable-graymap"

    /** Constant: "image/x-portable-pixmap" */
    @JvmField
    val IMAGE_X_PORTABLE_PIXMAP = "image/x-portable-pixmap"

    /** Constant: "image/x-rgb" */
    @JvmField
    val IMAGE_X_RGB = "image/x-rgb"

    /** Constant: "image/x-xbitmap" */
    @JvmField
    val IMAGE_X_XBITMAP = "image/x-xbitmap"

    /** Constant: "image/x-xwindowdump" */
    @JvmField
    val IMAGE_X_XWINDOWDUMP = "image/x-xwindowdump"

    /** Constant: "message/rfc822" */
    @JvmField
    val MESSAGE_RFC822 = "message/rfc822"

    /** Constant: "text/css" */
    @JvmField
    val TEXT_CSS = "text/css"

    /** Constant: "text/csv" */
    @JvmField
    val TEXT_CSV = "text/csv"

    /** Constant: "text/html" */
    @JvmField
    val TEXT_HTML = "text/html"

    /** Constant: "text/javascript" */
    @JvmField
    val TEXT_JAVASCRIPT = "text/javascript"

    /** Constant: "text/markdown" */
    @JvmField
    val TEXT_MARKDOWN = "text/markdown"

    /** Constant: "text/plain" */
    @JvmField
    val TEXT_PLAIN = "text/plain"

    /** Constant: "text/tab-separated-values" */
    @JvmField
    val TEXT_TAB_SEPARATED_VALUES = "text/tab-separated-values"

    /** Constant: "text/x-setext" */
    @JvmField
    val TEXT_X_SETEXT = "text/x-setext"

    /** Constant: "video/mp4" */
    @JvmField
    val VIDEO_MP4 = "video/mp4"

    /** Constant: "video/mpeg" */
    @JvmField
    val VIDEO_MPEG = "video/mpeg"

    /** Constant: "video/ogg" */
    @JvmField
    val VIDEO_OGG = "video/ogg"

    /** Constant: "video/quicktime" */
    @JvmField
    val VIDEO_QUICKTIME = "video/quicktime"

    /** Constant: "video/webm" */
    @JvmField
    val VIDEO_WEBM = "video/webm"

    /** Constant: "video/sgi-movie" */
    @JvmField
    val VIDEO_SGI_MOVIE = "video/sgi-movie"

    /** Constant: "video/x-sgi-movie" */
    @JvmField
    val VIDEO_X_SGI_MOVIE = "video/x-sgi-movie"

    @JvmField
    val APPLICATION_1D_INTERLEAVED_PARITYFEC = "application/1d-interleaved-parityfec"

    @JvmField
    val APPLICATION_3GPDASH_QOE_REPORT_XML = "application/3gpdash-qoe-report+xml"

    @JvmField
    val APPLICATION_3GPPHALFORMS_JSON = "application/3gppHalForms+json"

    @JvmField
    val APPLICATION_3GPPHAL_JSON = "application/3gppHal+json"

    @JvmField
    val APPLICATION_3GPP_IMS_XML = "application/3gpp-ims+xml"

    @JvmField
    val APPLICATION_A2L = "application/A2L"

    @JvmField
    val APPLICATION_ACE_CBOR = "application/ace+cbor"

    @JvmField
    val APPLICATION_ACE_JSON = "application/ace+json"

    @JvmField
    val APPLICATION_ACTIVEMESSAGE = "application/activemessage"

    @JvmField
    val APPLICATION_ACTIVITY_JSON = "application/activity+json"

    @JvmField
    val APPLICATION_AIF_CBOR = "application/aif+cbor"

    @JvmField
    val APPLICATION_AIF_JSON = "application/aif+json"

    @JvmField
    val APPLICATION_ALTO_CDNIFILTER_JSON = "application/alto-cdnifilter+json"

    @JvmField
    val APPLICATION_ALTO_CDNI_JSON = "application/alto-cdni+json"

    @JvmField
    val APPLICATION_ALTO_COSTMAPFILTER_JSON = "application/alto-costmapfilter+json"

    @JvmField
    val APPLICATION_ALTO_COSTMAP_JSON = "application/alto-costmap+json"

    @JvmField
    val APPLICATION_ALTO_DIRECTORY_JSON = "application/alto-directory+json"

    @JvmField
    val APPLICATION_ALTO_ENDPOINTCOSTPARAMS_JSON = "application/alto-endpointcostparams+json"

    @JvmField
    val APPLICATION_ALTO_ENDPOINTCOST_JSON = "application/alto-endpointcost+json"

    @JvmField
    val APPLICATION_ALTO_ENDPOINTPROPPARAMS_JSON = "application/alto-endpointpropparams+json"

    @JvmField
    val APPLICATION_ALTO_ENDPOINTPROP_JSON = "application/alto-endpointprop+json"

    @JvmField
    val APPLICATION_ALTO_ERROR_JSON = "application/alto-error+json"

    @JvmField
    val APPLICATION_ALTO_NETWORKMAPFILTER_JSON = "application/alto-networkmapfilter+json"

    @JvmField
    val APPLICATION_ALTO_NETWORKMAP_JSON = "application/alto-networkmap+json"

    @JvmField
    val APPLICATION_ALTO_PROPMAPPARAMS_JSON = "application/alto-propmapparams+json"

    @JvmField
    val APPLICATION_ALTO_PROPMAP_JSON = "application/alto-propmap+json"

    @JvmField
    val APPLICATION_ALTO_TIPSPARAMS_JSON = "application/alto-tipsparams+json"

    @JvmField
    val APPLICATION_ALTO_TIPS_JSON = "application/alto-tips+json"

    @JvmField
    val APPLICATION_ALTO_UPDATESTREAMCONTROL_JSON = "application/alto-updatestreamcontrol+json"

    @JvmField
    val APPLICATION_ALTO_UPDATESTREAMPARAMS_JSON = "application/alto-updatestreamparams+json"

    @JvmField
    val APPLICATION_AML = "application/AML"

    @JvmField
    val APPLICATION_ANDREW_INSET = "application/andrew-inset"

    @JvmField
    val APPLICATION_APPLEFILE = "application/applefile"

    @JvmField
    val APPLICATION_ATF = "application/ATF"

    @JvmField
    val APPLICATION_ATFX = "application/ATFX"

    @JvmField
    val APPLICATION_ATOMCAT_XML = "application/atomcat+xml"

    @JvmField
    val APPLICATION_ATOMDELETED_XML = "application/atomdeleted+xml"

    @JvmField
    val APPLICATION_ATOMICMAIL = "application/atomicmail"

    @JvmField
    val APPLICATION_ATOMSVC_XML = "application/atomsvc+xml"

    @JvmField
    val APPLICATION_ATSC_DWD_XML = "application/atsc-dwd+xml"

    @JvmField
    val APPLICATION_ATSC_DYNAMIC_EVENT_MESSAGE = "application/atsc-dynamic-event-message"

    @JvmField
    val APPLICATION_ATSC_HELD_XML = "application/atsc-held+xml"

    @JvmField
    val APPLICATION_ATSC_RDT_JSON = "application/atsc-rdt+json"

    @JvmField
    val APPLICATION_ATSC_RSAT_XML = "application/atsc-rsat+xml"

    @JvmField
    val APPLICATION_ATXML = "application/ATXML"

    @JvmField
    val APPLICATION_AT_JWT = "application/at+jwt"

    @JvmField
    val APPLICATION_AUTH_POLICY_XML = "application/auth-policy+xml"

    @JvmField
    val APPLICATION_AUTOMATIONML_AMLX_ZIP = "application/automationml-amlx+zip"

    @JvmField
    val APPLICATION_AUTOMATIONML_AML_XML = "application/automationml-aml+xml"

    @JvmField
    val APPLICATION_BACNET_XDD_ZIP = "application/bacnet-xdd+zip"

    @JvmField
    val APPLICATION_BATCH_SMTP = "application/batch-SMTP"

    @JvmField
    val APPLICATION_BEEP_XML = "application/beep+xml"

    @JvmField
    val APPLICATION_C2PA = "application/c2pa"

    @JvmField
    val APPLICATION_CALENDAR_JSON = "application/calendar+json"

    @JvmField
    val APPLICATION_CALENDAR_XML = "application/calendar+xml"

    @JvmField
    val APPLICATION_CALL_COMPLETION = "application/call-completion"

    @JvmField
    val APPLICATION_CALS_1840 = "application/CALS-1840"

    @JvmField
    val APPLICATION_CAPTIVE_JSON = "application/captive+json"

    @JvmField
    val APPLICATION_CBOR = "application/cbor"

    @JvmField
    val APPLICATION_CBOR_SEQ = "application/cbor-seq"

    @JvmField
    val APPLICATION_CCCEX = "application/cccex"

    @JvmField
    val APPLICATION_CCMP_XML = "application/ccmp+xml"

    @JvmField
    val APPLICATION_CCXML_XML = "application/ccxml+xml"

    @JvmField
    val APPLICATION_CDA_XML = "application/cda+xml"

    @JvmField
    val APPLICATION_CDFX_XML = "application/CDFX+XML"

    @JvmField
    val APPLICATION_CDMI_CAPABILITY = "application/cdmi-capability"

    @JvmField
    val APPLICATION_CDMI_CONTAINER = "application/cdmi-container"

    @JvmField
    val APPLICATION_CDMI_DOMAIN = "application/cdmi-domain"

    @JvmField
    val APPLICATION_CDMI_OBJECT = "application/cdmi-object"

    @JvmField
    val APPLICATION_CDMI_QUEUE = "application/cdmi-queue"

    @JvmField
    val APPLICATION_CDNI = "application/cdni"

    @JvmField
    val APPLICATION_CEA = "application/CEA"

    @JvmField
    val APPLICATION_CEA_2018_XML = "application/cea-2018+xml"

    @JvmField
    val APPLICATION_CELLML_XML = "application/cellml+xml"

    @JvmField
    val APPLICATION_CFW = "application/cfw"

    @JvmField
    val APPLICATION_CID_EDHOC_CBOR_SEQ = "application/cid-edhoc+cbor-seq"

    @JvmField
    val APPLICATION_CITY_JSON = "application/city+json"

    @JvmField
    val APPLICATION_CLR = "application/clr"

    @JvmField
    val APPLICATION_CLUE_INFO_XML = "application/clue_info+xml"

    @JvmField
    val APPLICATION_CLUE_XML = "application/clue+xml"

    @JvmField
    val APPLICATION_CMS = "application/cms"

    @JvmField
    val APPLICATION_CNRP_XML = "application/cnrp+xml"

    @JvmField
    val APPLICATION_COAP_GROUP_JSON = "application/coap-group+json"

    @JvmField
    val APPLICATION_COAP_PAYLOAD = "application/coap-payload"

    @JvmField
    val APPLICATION_COMMONGROUND = "application/commonground"

    @JvmField
    val APPLICATION_CONCISE_PROBLEM_DETAILS_CBOR = "application/concise-problem-details+cbor"

    @JvmField
    val APPLICATION_CONFERENCE_INFO_XML = "application/conference-info+xml"

    @JvmField
    val APPLICATION_COSE = "application/cose"

    @JvmField
    val APPLICATION_COSE_KEY = "application/cose-key"

    @JvmField
    val APPLICATION_COSE_KEY_SET = "application/cose-key-set"

    @JvmField
    val APPLICATION_COSE_X509 = "application/cose-x509"

    @JvmField
    val APPLICATION_CPL_XML = "application/cpl+xml"

    @JvmField
    val APPLICATION_CSRATTRS = "application/csrattrs"

    @JvmField
    val APPLICATION_CSTADATA_XML = "application/CSTAdata+xml"

    @JvmField
    val APPLICATION_CSTA_XML = "application/csta+xml"

    @JvmField
    val APPLICATION_CSVM_JSON = "application/csvm+json"

    @JvmField
    val APPLICATION_CWL = "application/cwl"

    @JvmField
    val APPLICATION_CWL_JSON = "application/cwl+json"

    @JvmField
    val APPLICATION_CWT = "application/cwt"

    @JvmField
    val APPLICATION_CYBERCASH = "application/cybercash"

    @JvmField
    val APPLICATION_DASHDELTA = "application/dashdelta"

    @JvmField
    val APPLICATION_DASH_PATCH_XML = "application/dash-patch+xml"

    @JvmField
    val APPLICATION_DASH_XML = "application/dash+xml"

    @JvmField
    val APPLICATION_DAVMOUNT_XML = "application/davmount+xml"

    @JvmField
    val APPLICATION_DCA_RFT = "application/dca-rft"

    @JvmField
    val APPLICATION_DCD = "application/DCD"

    @JvmField
    val APPLICATION_DEC_DX = "application/dec-dx"

    @JvmField
    val APPLICATION_DIALOG_INFO_XML = "application/dialog-info+xml"

    @JvmField
    val APPLICATION_DICOM = "application/dicom"

    @JvmField
    val APPLICATION_DICOM_JSON = "application/dicom+json"

    @JvmField
    val APPLICATION_DICOM_XML = "application/dicom+xml"

    @JvmField
    val APPLICATION_DII = "application/DII"

    @JvmField
    val APPLICATION_DIT = "application/DIT"

    @JvmField
    val APPLICATION_DNS = "application/dns"

    @JvmField
    val APPLICATION_DNS_JSON = "application/dns+json"

    @JvmField
    val APPLICATION_DNS_MESSAGE = "application/dns-message"

    @JvmField
    val APPLICATION_DOTS_CBOR = "application/dots+cbor"

    @JvmField
    val APPLICATION_DPOP_JWT = "application/dpop+jwt"

    @JvmField
    val APPLICATION_DSKPP_XML = "application/dskpp+xml"

    @JvmField
    val APPLICATION_DSSC_DER = "application/dssc+der"

    @JvmField
    val APPLICATION_DSSC_XML = "application/dssc+xml"

    @JvmField
    val APPLICATION_DVCS = "application/dvcs"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("TEXT_JAVASCRIPT"), DeprecationLevel.ERROR)
    val APPLICATION_ECMASCRIPT = "application/ecmascript"

    @JvmField
    val APPLICATION_EDHOC_CBOR_SEQ = "application/edhoc+cbor-seq"

    @JvmField
    val APPLICATION_EDIFACT = "application/EDIFACT"

    @JvmField
    val APPLICATION_EDI_CONSENT = "application/EDI-consent"

    @JvmField
    val APPLICATION_EDI_X12 = "application/EDI-X12"

    @JvmField
    val APPLICATION_EFI = "application/efi"

    @JvmField
    val APPLICATION_ELM_JSON = "application/elm+json"

    @JvmField
    val APPLICATION_ELM_XML = "application/elm+xml"

    @JvmField
    val APPLICATION_EMERGENCYCALLDATA_CAP_XML = "application/EmergencyCallData.cap+xml"

    @JvmField
    val APPLICATION_EMERGENCYCALLDATA_COMMENT_XML = "application/EmergencyCallData.Comment+xml"

    @JvmField
    val APPLICATION_EMERGENCYCALLDATA_CONTROL_XML = "application/EmergencyCallData.Control+xml"

    @JvmField
    val APPLICATION_EMERGENCYCALLDATA_DEVICEINFO_XML = "application/EmergencyCallData.DeviceInfo+xml"

    @JvmField
    val APPLICATION_EMERGENCYCALLDATA_ECALL_MSD = "application/EmergencyCallData.eCall.MSD"

    @JvmField
    val APPLICATION_EMERGENCYCALLDATA_LEGACYESN_JSON = "application/EmergencyCallData.LegacyESN+json"

    @JvmField
    val APPLICATION_EMERGENCYCALLDATA_PROVIDERINFO_XML = "application/EmergencyCallData.ProviderInfo+xml"

    @JvmField
    val APPLICATION_EMERGENCYCALLDATA_SERVICEINFO_XML = "application/EmergencyCallData.ServiceInfo+xml"

    @JvmField
    val APPLICATION_EMERGENCYCALLDATA_SUBSCRIBERINFO_XML = "application/EmergencyCallData.SubscriberInfo+xml"

    @JvmField
    val APPLICATION_EMERGENCYCALLDATA_VEDS_XML = "application/EmergencyCallData.VEDS+xml"

    @JvmField
    val APPLICATION_EMMA_XML = "application/emma+xml"

    @JvmField
    val APPLICATION_EMOTIONML_XML = "application/emotionml+xml"

    @JvmField
    val APPLICATION_ENCAPRTP = "application/encaprtp"

    @JvmField
    val APPLICATION_EPP_XML = "application/epp+xml"

    @JvmField
    val APPLICATION_EPUB_ZIP = "application/epub+zip"

    @JvmField
    val APPLICATION_ESHOP = "application/eshop"

    @JvmField
    val APPLICATION_EXAMPLE = "application/example"

    @JvmField
    val APPLICATION_EXI = "application/exi"

    @JvmField
    val APPLICATION_EXPECT_CT_REPORT_JSON = "application/expect-ct-report+json"

    @JvmField
    val APPLICATION_EXPRESS = "application/express"

    @JvmField
    val APPLICATION_FASTINFOSET = "application/fastinfoset"

    @JvmField
    val APPLICATION_FASTSOAP = "application/fastsoap"

    @JvmField
    val APPLICATION_FDF = "application/fdf"

    @JvmField
    val APPLICATION_FDT_XML = "application/fdt+xml"

    @JvmField
    val APPLICATION_FHIR_JSON = "application/fhir+json"

    @JvmField
    val APPLICATION_FHIR_XML = "application/fhir+xml"

    @JvmField
    val APPLICATION_FITS = "application/fits"

    @JvmField
    val APPLICATION_FLEXFEC = "application/flexfec"

    @JvmField
    @Deprecated("Deprecated", ReplaceWith("FONT_SFNT"))
    val APPLICATION_FONT_SFNT = "application/font-sfnt"

    @JvmField
    val APPLICATION_FONT_TDPFR = "application/font-tdpfr"

    @JvmField
    @Deprecated("Deprecated", ReplaceWith("FONT_WOFF"))
    val APPLICATION_FONT_WOFF = "application/font-woff"

    @JvmField
    val APPLICATION_FRAMEWORK_ATTRIBUTES_XML = "application/framework-attributes+xml"

    @JvmField
    val APPLICATION_GEOPACKAGE_SQLITE3 = "application/geopackage+sqlite3"

    @JvmField
    val APPLICATION_GEOXACML_JSON = "application/geoxacml+json"

    @JvmField
    val APPLICATION_GEOXACML_XML = "application/geoxacml+xml"

    @JvmField
    val APPLICATION_GEO_JSON = "application/geo+json"

    @JvmField
    val APPLICATION_GEO_JSON_SEQ = "application/geo+json-seq"

    @JvmField
    val APPLICATION_GLTF_BUFFER = "application/gltf-buffer"

    @JvmField
    val APPLICATION_GML_XML = "application/gml+xml"

    @JvmField
    val APPLICATION_GNAP_BINDING_JWS = "application/gnap-binding-jws"

    @JvmField
    val APPLICATION_GNAP_BINDING_JWSD = "application/gnap-binding-jwsd"

    @JvmField
    val APPLICATION_GNAP_BINDING_ROTATION_JWS = "application/gnap-binding-rotation-jws"

    @JvmField
    val APPLICATION_GNAP_BINDING_ROTATION_JWSD = "application/gnap-binding-rotation-jwsd"

    @JvmField
    val APPLICATION_H224 = "application/H224"

    @JvmField
    val APPLICATION_HELD_XML = "application/held+xml"

    @JvmField
    val APPLICATION_HL7V2_XML = "application/hl7v2+xml"

    @JvmField
    val APPLICATION_HTTP = "application/http"

    @JvmField
    val APPLICATION_HYPERSTUDIO = "application/hyperstudio"

    @JvmField
    val APPLICATION_IBE_KEY_REQUEST_XML = "application/ibe-key-request+xml"

    @JvmField
    val APPLICATION_IBE_PKG_REPLY_XML = "application/ibe-pkg-reply+xml"

    @JvmField
    val APPLICATION_IBE_PP_DATA = "application/ibe-pp-data"

    @JvmField
    val APPLICATION_IGES = "application/iges"

    @JvmField
    val APPLICATION_IM_ISCOMPOSING_XML = "application/im-iscomposing+xml"

    @JvmField
    val APPLICATION_INDEX = "application/index"

    @JvmField
    val APPLICATION_INDEX_CMD = "application/index.cmd"

    @JvmField
    val APPLICATION_INDEX_OBJ = "application/index.obj"

    @JvmField
    val APPLICATION_INDEX_RESPONSE = "application/index.response"

    @JvmField
    val APPLICATION_INDEX_VND = "application/index.vnd"

    @JvmField
    val APPLICATION_INKML_XML = "application/inkml+xml"

    @JvmField
    val APPLICATION_IOTP = "application/IOTP"

    @JvmField
    val APPLICATION_IPFIX = "application/ipfix"

    @JvmField
    val APPLICATION_IPP = "application/ipp"

    @JvmField
    val APPLICATION_ISUP = "application/ISUP"

    @JvmField
    val APPLICATION_ITS_XML = "application/its+xml"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("TEXT_JAVASCRIPT"), DeprecationLevel.ERROR)
    val APPLICATION_JAVASCRIPT = "application/javascript"

    @JvmField
    val APPLICATION_JF2FEED_JSON = "application/jf2feed+json"

    @JvmField
    val APPLICATION_JOSE = "application/jose"

    @JvmField
    val APPLICATION_JOSE_JSON = "application/jose+json"

    @JvmField
    val APPLICATION_JRD_JSON = "application/jrd+json"

    @JvmField
    val APPLICATION_JSCALENDAR_JSON = "application/jscalendar+json"

    @JvmField
    val APPLICATION_JSCONTACT_JSON = "application/jscontact+json"

    @JvmField
    val APPLICATION_JSONPATH = "application/jsonpath"

    @JvmField
    val APPLICATION_JSON_SEQ = "application/json-seq"

    @JvmField
    val APPLICATION_JWK_JSON = "application/jwk+json"

    @JvmField
    val APPLICATION_JWK_SET_JSON = "application/jwk-set+json"

    @JvmField
    val APPLICATION_JWT = "application/jwt"

    @JvmField
    val APPLICATION_KPML_REQUEST_XML = "application/kpml-request+xml"

    @JvmField
    val APPLICATION_KPML_RESPONSE_XML = "application/kpml-response+xml"

    @JvmField
    val APPLICATION_LD_JSON = "application/ld+json"

    @JvmField
    val APPLICATION_LGR_XML = "application/lgr+xml"

    @JvmField
    val APPLICATION_LINKSET = "application/linkset"

    @JvmField
    val APPLICATION_LINKSET_JSON = "application/linkset+json"

    @JvmField
    val APPLICATION_LINK_FORMAT = "application/link-format"

    @JvmField
    val APPLICATION_LOAD_CONTROL_XML = "application/load-control+xml"

    @JvmField
    val APPLICATION_LOGOUT_JWT = "application/logout+jwt"

    @JvmField
    val APPLICATION_LOSTSYNC_XML = "application/lostsync+xml"

    @JvmField
    val APPLICATION_LOST_XML = "application/lost+xml"

    @JvmField
    val APPLICATION_LPF_ZIP = "application/lpf+zip"

    @JvmField
    val APPLICATION_LXF = "application/LXF"

    @JvmField
    val APPLICATION_MACWRITEII = "application/macwriteii"

    @JvmField
    val APPLICATION_MAC_BINHEX40 = "application/mac-binhex40"

    @JvmField
    val APPLICATION_MADS_XML = "application/mads+xml"

    @JvmField
    val APPLICATION_MANIFEST_JSON = "application/manifest+json"

    @JvmField
    val APPLICATION_MARC = "application/marc"

    @JvmField
    val APPLICATION_MARCXML_XML = "application/marcxml+xml"

    @JvmField
    val APPLICATION_MATHEMATICA = "application/mathematica"

    @JvmField
    val APPLICATION_MATHML_CONTENT_XML = "application/mathml-content+xml"

    @JvmField
    val APPLICATION_MATHML_PRESENTATION_XML = "application/mathml-presentation+xml"

    @JvmField
    val APPLICATION_MATHML_XML = "application/mathml+xml"

    @JvmField
    val APPLICATION_MBMS_ASSOCIATED_PROCEDURE_DESCRIPTION_XML = "application/mbms-associated-procedure-description+xml"

    @JvmField
    val APPLICATION_MBMS_DEREGISTER_XML = "application/mbms-deregister+xml"

    @JvmField
    val APPLICATION_MBMS_ENVELOPE_XML = "application/mbms-envelope+xml"

    @JvmField
    val APPLICATION_MBMS_MSK_RESPONSE_XML = "application/mbms-msk-response+xml"

    @JvmField
    val APPLICATION_MBMS_MSK_XML = "application/mbms-msk+xml"

    @JvmField
    val APPLICATION_MBMS_PROTECTION_DESCRIPTION_XML = "application/mbms-protection-description+xml"

    @JvmField
    val APPLICATION_MBMS_RECEPTION_REPORT_XML = "application/mbms-reception-report+xml"

    @JvmField
    val APPLICATION_MBMS_REGISTER_RESPONSE_XML = "application/mbms-register-response+xml"

    @JvmField
    val APPLICATION_MBMS_REGISTER_XML = "application/mbms-register+xml"

    @JvmField
    val APPLICATION_MBMS_SCHEDULE_XML = "application/mbms-schedule+xml"

    @JvmField
    val APPLICATION_MBMS_USER_SERVICE_DESCRIPTION_XML = "application/mbms-user-service-description+xml"

    @JvmField
    val APPLICATION_MBOX = "application/mbox"

    @JvmField
    val APPLICATION_MEDIASERVERCONTROL_XML = "application/mediaservercontrol+xml"

    @JvmField
    val APPLICATION_MEDIA_CONTROL_XML = "application/media_control+xml"

    @JvmField
    val APPLICATION_MEDIA_POLICY_DATASET_XML = "application/media-policy-dataset+xml"

    @JvmField
    val APPLICATION_MERGE_PATCH_JSON = "application/merge-patch+json"

    @JvmField
    val APPLICATION_METALINK4_XML = "application/metalink4+xml"

    @JvmField
    val APPLICATION_METS_XML = "application/mets+xml"

    @JvmField
    val APPLICATION_MF4 = "application/MF4"

    @JvmField
    val APPLICATION_MIKEY = "application/mikey"

    @JvmField
    val APPLICATION_MIPC = "application/mipc"

    @JvmField
    val APPLICATION_MISSING_BLOCKS_CBOR_SEQ = "application/missing-blocks+cbor-seq"

    @JvmField
    val APPLICATION_MMT_AEI_XML = "application/mmt-aei+xml"

    @JvmField
    val APPLICATION_MMT_USD_XML = "application/mmt-usd+xml"

    @JvmField
    val APPLICATION_MODS_XML = "application/mods+xml"

    @JvmField
    val APPLICATION_MOSSKEY_DATA = "application/mosskey-data"

    @JvmField
    val APPLICATION_MOSSKEY_REQUEST = "application/mosskey-request"

    @JvmField
    val APPLICATION_MOSS_KEYS = "application/moss-keys"

    @JvmField
    val APPLICATION_MOSS_SIGNATURE = "application/moss-signature"

    @JvmField
    val APPLICATION_MP21 = "application/mp21"

    @JvmField
    val APPLICATION_MP4 = "application/mp4"

    @JvmField
    val APPLICATION_MPEG4_GENERIC = "application/mpeg4-generic"

    @JvmField
    val APPLICATION_MPEG4_IOD = "application/mpeg4-iod"

    @JvmField
    val APPLICATION_MPEG4_IOD_XMT = "application/mpeg4-iod-xmt"

    @JvmField
    val APPLICATION_MRB_CONSUMER_XML = "application/mrb-consumer+xml"

    @JvmField
    val APPLICATION_MRB_PUBLISH_XML = "application/mrb-publish+xml"

    @JvmField
    val APPLICATION_MSC_IVR_XML = "application/msc-ivr+xml"

    @JvmField
    val APPLICATION_MSC_MIXER_XML = "application/msc-mixer+xml"

    @JvmField
    val APPLICATION_MUD_JSON = "application/mud+json"

    @JvmField
    val APPLICATION_MULTIPART_CORE = "application/multipart-core"

    @JvmField
    val APPLICATION_MXF = "application/mxf"

    @JvmField
    val APPLICATION_NASDATA = "application/nasdata"

    @JvmField
    val APPLICATION_NEWS_CHECKGROUPS = "application/news-checkgroups"

    @JvmField
    val APPLICATION_NEWS_GROUPINFO = "application/news-groupinfo"

    @JvmField
    val APPLICATION_NEWS_TRANSMISSION = "application/news-transmission"

    @JvmField
    val APPLICATION_NLSML_XML = "application/nlsml+xml"

    @JvmField
    val APPLICATION_NODE = "application/node"

    @JvmField
    val APPLICATION_NSS = "application/nss"

    @JvmField
    val APPLICATION_N_QUADS = "application/n-quads"

    @JvmField
    val APPLICATION_N_TRIPLES = "application/n-triples"

    @JvmField
    val APPLICATION_OAUTH_AUTHZ_REQ_JWT = "application/oauth-authz-req+jwt"

    @JvmField
    val APPLICATION_OBLIVIOUS_DNS_MESSAGE = "application/oblivious-dns-message"

    @JvmField
    val APPLICATION_OCSP_REQUEST = "application/ocsp-request"

    @JvmField
    val APPLICATION_OCSP_RESPONSE = "application/ocsp-response"

    @JvmField
    val APPLICATION_ODM_XML = "application/odm+xml"

    @JvmField
    val APPLICATION_ODX = "application/ODX"

    @JvmField
    val APPLICATION_OEBPS_PACKAGE_XML = "application/oebps-package+xml"

    @JvmField
    val APPLICATION_OGG = "application/ogg"

    @JvmField
    val APPLICATION_OHTTP_KEYS = "application/ohttp-keys"

    @JvmField
    val APPLICATION_OPC_NODESET_XML = "application/opc-nodeset+xml"

    @JvmField
    val APPLICATION_OSCORE = "application/oscore"

    @JvmField
    val APPLICATION_OXPS = "application/oxps"

    @JvmField
    val APPLICATION_P21 = "application/p21"

    @JvmField
    val APPLICATION_P21_ZIP = "application/p21+zip"

    @JvmField
    val APPLICATION_P2P_OVERLAY_XML = "application/p2p-overlay+xml"

    @JvmField
    val APPLICATION_PARITYFEC = "application/parityfec"

    @JvmField
    val APPLICATION_PASSPORT = "application/passport"

    @JvmField
    val APPLICATION_PATCH_OPS_ERROR_XML = "application/patch-ops-error+xml"

    @JvmField
    val APPLICATION_PDX = "application/PDX"

    @JvmField
    val APPLICATION_PEM_CERTIFICATE_CHAIN = "application/pem-certificate-chain"

    @JvmField
    val APPLICATION_PGP_ENCRYPTED = "application/pgp-encrypted"

    @JvmField
    val APPLICATION_PGP_KEYS = "application/pgp-keys"

    @JvmField
    val APPLICATION_PGP_SIGNATURE = "application/pgp-signature"

    @JvmField
    val APPLICATION_PIDF_DIFF_XML = "application/pidf-diff+xml"

    @JvmField
    val APPLICATION_PIDF_XML = "application/pidf+xml"

    @JvmField
    val APPLICATION_PKCS10 = "application/pkcs10"

    @JvmField
    val APPLICATION_PKCS12 = "application/pkcs12"

    @JvmField
    val APPLICATION_PKCS7_MIME = "application/pkcs7-mime"

    @JvmField
    val APPLICATION_PKCS7_SIGNATURE = "application/pkcs7-signature"

    @JvmField
    val APPLICATION_PKCS8 = "application/pkcs8"

    @JvmField
    val APPLICATION_PKCS8_ENCRYPTED = "application/pkcs8-encrypted"

    @JvmField
    val APPLICATION_PKIXCMP = "application/pkixcmp"

    @JvmField
    val APPLICATION_PKIX_ATTR_CERT = "application/pkix-attr-cert"

    @JvmField
    val APPLICATION_PKIX_CERT = "application/pkix-cert"

    @JvmField
    val APPLICATION_PKIX_CRL = "application/pkix-crl"

    @JvmField
    val APPLICATION_PKIX_PKIPATH = "application/pkix-pkipath"

    @JvmField
    val APPLICATION_PLS_XML = "application/pls+xml"

    @JvmField
    val APPLICATION_POC_SETTINGS_XML = "application/poc-settings+xml"

    @JvmField
    val APPLICATION_PPSP_TRACKER_JSON = "application/ppsp-tracker+json"

    @JvmField
    val APPLICATION_PRIVATE_TOKEN_ISSUER_DIRECTORY = "application/private-token-issuer-directory"

    @JvmField
    val APPLICATION_PRIVATE_TOKEN_REQUEST = "application/private-token-request"

    @JvmField
    val APPLICATION_PRIVATE_TOKEN_RESPONSE = "application/private-token-response"

    @JvmField
    val APPLICATION_PROBLEM_JSON = "application/problem+json"

    @JvmField
    val APPLICATION_PROBLEM_XML = "application/problem+xml"

    @JvmField
    val APPLICATION_PROVENANCE_XML = "application/provenance+xml"

    @JvmField
    val APPLICATION_PRS_ALVESTRAND_TITRAX_SHEET = "application/prs.alvestrand.titrax-sheet"

    @JvmField
    val APPLICATION_PRS_CWW = "application/prs.cww"

    @JvmField
    val APPLICATION_PRS_CYN = "application/prs.cyn"

    @JvmField
    val APPLICATION_PRS_HPUB_ZIP = "application/prs.hpub+zip"

    @JvmField
    val APPLICATION_PRS_IMPLIED_DOCUMENT_XML = "application/prs.implied-document+xml"

    @JvmField
    val APPLICATION_PRS_IMPLIED_EXECUTABLE = "application/prs.implied-executable"

    @JvmField
    val APPLICATION_PRS_IMPLIED_OBJECT_JSON = "application/prs.implied-object+json"

    @JvmField
    val APPLICATION_PRS_IMPLIED_OBJECT_JSON_SEQ = "application/prs.implied-object+json-seq"

    @JvmField
    val APPLICATION_PRS_IMPLIED_OBJECT_YAML = "application/prs.implied-object+yaml"

    @JvmField
    val APPLICATION_PRS_IMPLIED_STRUCTURE = "application/prs.implied-structure"

    @JvmField
    val APPLICATION_PRS_NPREND = "application/prs.nprend"

    @JvmField
    val APPLICATION_PRS_PLUCKER = "application/prs.plucker"

    @JvmField
    val APPLICATION_PRS_RDF_XML_CRYPT = "application/prs.rdf-xml-crypt"

    @JvmField
    val APPLICATION_PRS_VCFBZIP2 = "application/prs.vcfbzip2"

    @JvmField
    val APPLICATION_PRS_XSF_XML = "application/prs.xsf+xml"

    @JvmField
    val APPLICATION_PSKC_XML = "application/pskc+xml"

    @JvmField
    val APPLICATION_PVD_JSON = "application/pvd+json"

    @JvmField
    val APPLICATION_QSIG = "application/QSIG"

    @JvmField
    val APPLICATION_RAPTORFEC = "application/raptorfec"

    @JvmField
    val APPLICATION_RDAP_JSON = "application/rdap+json"

    @JvmField
    val APPLICATION_RDF_XML = "application/rdf+xml"

    @JvmField
    val APPLICATION_REGINFO_XML = "application/reginfo+xml"

    @JvmField
    val APPLICATION_RELAX_NG_COMPACT_SYNTAX = "application/relax-ng-compact-syntax"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("NULL"), DeprecationLevel.ERROR)
    val APPLICATION_REMOTE_PRINTING = "application/remote-printing"

    @JvmField
    val APPLICATION_REPUTON_JSON = "application/reputon+json"

    @JvmField
    val APPLICATION_RESOURCE_LISTS_DIFF_XML = "application/resource-lists-diff+xml"

    @JvmField
    val APPLICATION_RESOURCE_LISTS_XML = "application/resource-lists+xml"

    @JvmField
    val APPLICATION_RFC_XML = "application/rfc+xml"

    @JvmField
    val APPLICATION_RISCOS = "application/riscos"

    @JvmField
    val APPLICATION_RLMI_XML = "application/rlmi+xml"

    @JvmField
    val APPLICATION_RLS_SERVICES_XML = "application/rls-services+xml"

    @JvmField
    val APPLICATION_ROUTE_APD_XML = "application/route-apd+xml"

    @JvmField
    val APPLICATION_ROUTE_S_TSID_XML = "application/route-s-tsid+xml"

    @JvmField
    val APPLICATION_ROUTE_USD_XML = "application/route-usd+xml"

    @JvmField
    val APPLICATION_RPKI_CHECKLIST = "application/rpki-checklist"

    @JvmField
    val APPLICATION_RPKI_GHOSTBUSTERS = "application/rpki-ghostbusters"

    @JvmField
    val APPLICATION_RPKI_MANIFEST = "application/rpki-manifest"

    @JvmField
    val APPLICATION_RPKI_PUBLICATION = "application/rpki-publication"

    @JvmField
    val APPLICATION_RPKI_ROA = "application/rpki-roa"

    @JvmField
    val APPLICATION_RPKI_UPDOWN = "application/rpki-updown"

    @JvmField
    val APPLICATION_RTPLOOPBACK = "application/rtploopback"

    @JvmField
    val APPLICATION_RTX = "application/rtx"

    @JvmField
    val APPLICATION_SAMLASSERTION_XML = "application/samlassertion+xml"

    @JvmField
    val APPLICATION_SAMLMETADATA_XML = "application/samlmetadata+xml"

    @JvmField
    val APPLICATION_SARIF_EXTERNAL_PROPERTIES_JSON = "application/sarif-external-properties+json"

    @JvmField
    val APPLICATION_SARIF_JSON = "application/sarif+json"

    @JvmField
    val APPLICATION_SBE = "application/sbe"

    @JvmField
    val APPLICATION_SBML_XML = "application/sbml+xml"

    @JvmField
    val APPLICATION_SCAIP_XML = "application/scaip+xml"

    @JvmField
    val APPLICATION_SCIM_JSON = "application/scim+json"

    @JvmField
    val APPLICATION_SCVP_CV_REQUEST = "application/scvp-cv-request"

    @JvmField
    val APPLICATION_SCVP_CV_RESPONSE = "application/scvp-cv-response"

    @JvmField
    val APPLICATION_SCVP_VP_REQUEST = "application/scvp-vp-request"

    @JvmField
    val APPLICATION_SCVP_VP_RESPONSE = "application/scvp-vp-response"

    @JvmField
    val APPLICATION_SDP = "application/sdp"

    @JvmField
    val APPLICATION_SECEVENT_JWT = "application/secevent+jwt"

    @JvmField
    val APPLICATION_SENML_CBOR = "application/senml+cbor"

    @JvmField
    val APPLICATION_SENML_ETCH_CBOR = "application/senml-etch+cbor"

    @JvmField
    val APPLICATION_SENML_ETCH_JSON = "application/senml-etch+json"

    @JvmField
    val APPLICATION_SENML_EXI = "application/senml-exi"

    @JvmField
    val APPLICATION_SENML_JSON = "application/senml+json"

    @JvmField
    val APPLICATION_SENML_XML = "application/senml+xml"

    @JvmField
    val APPLICATION_SENSML_CBOR = "application/sensml+cbor"

    @JvmField
    val APPLICATION_SENSML_EXI = "application/sensml-exi"

    @JvmField
    val APPLICATION_SENSML_JSON = "application/sensml+json"

    @JvmField
    val APPLICATION_SENSML_XML = "application/sensml+xml"

    @JvmField
    val APPLICATION_SEP_EXI = "application/sep-exi"

    @JvmField
    val APPLICATION_SEP_XML = "application/sep+xml"

    @JvmField
    val APPLICATION_SESSION_INFO = "application/session-info"

    @JvmField
    val APPLICATION_SET_PAYMENT = "application/set-payment"

    @JvmField
    val APPLICATION_SET_PAYMENT_INITIATION = "application/set-payment-initiation"

    @JvmField
    val APPLICATION_SET_REGISTRATION = "application/set-registration"

    @JvmField
    val APPLICATION_SET_REGISTRATION_INITIATION = "application/set-registration-initiation"

    @JvmField
    val APPLICATION_SGML = "application/SGML"

    @JvmField
    val APPLICATION_SGML_OPEN_CATALOG = "application/sgml-open-catalog"

    @JvmField
    val APPLICATION_SHF_XML = "application/shf+xml"

    @JvmField
    val APPLICATION_SIEVE = "application/sieve"

    @JvmField
    val APPLICATION_SIMPLESYMBOLCONTAINER = "application/simpleSymbolContainer"

    @JvmField
    val APPLICATION_SIMPLE_FILTER_XML = "application/simple-filter+xml"

    @JvmField
    val APPLICATION_SIMPLE_MESSAGE_SUMMARY = "application/simple-message-summary"

    @JvmField
    val APPLICATION_SIPC = "application/sipc"

    @JvmField
    val APPLICATION_SLATE = "application/slate"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("APPLICATION_SMIL_XML"), DeprecationLevel.ERROR)
    val APPLICATION_SMIL = "application/smil"

    @JvmField
    val APPLICATION_SMIL_XML = "application/smil+xml"

    @JvmField
    val APPLICATION_SMPTE336M = "application/smpte336m"

    @JvmField
    val APPLICATION_SOAP_FASTINFOSET = "application/soap+fastinfoset"

    @JvmField
    val APPLICATION_SOAP_XML = "application/soap+xml"

    @JvmField
    val APPLICATION_SPARQL_QUERY = "application/sparql-query"

    @JvmField
    val APPLICATION_SPARQL_RESULTS_XML = "application/sparql-results+xml"

    @JvmField
    val APPLICATION_SPDX_JSON = "application/spdx+json"

    @JvmField
    val APPLICATION_SPIRITS_EVENT_XML = "application/spirits-event+xml"

    @JvmField
    val APPLICATION_SQL = "application/sql"

    @JvmField
    val APPLICATION_SRGS = "application/srgs"

    @JvmField
    val APPLICATION_SRGS_XML = "application/srgs+xml"

    @JvmField
    val APPLICATION_SRU_XML = "application/sru+xml"

    @JvmField
    val APPLICATION_SSML_XML = "application/ssml+xml"

    @JvmField
    val APPLICATION_STIX_JSON = "application/stix+json"

    @JvmField
    val APPLICATION_SWID_CBOR = "application/swid+cbor"

    @JvmField
    val APPLICATION_SWID_XML = "application/swid+xml"

    @JvmField
    val APPLICATION_TAMP_APEX_UPDATE = "application/tamp-apex-update"

    @JvmField
    val APPLICATION_TAMP_APEX_UPDATE_CONFIRM = "application/tamp-apex-update-confirm"

    @JvmField
    val APPLICATION_TAMP_COMMUNITY_UPDATE = "application/tamp-community-update"

    @JvmField
    val APPLICATION_TAMP_COMMUNITY_UPDATE_CONFIRM = "application/tamp-community-update-confirm"

    @JvmField
    val APPLICATION_TAMP_ERROR = "application/tamp-error"

    @JvmField
    val APPLICATION_TAMP_SEQUENCE_ADJUST = "application/tamp-sequence-adjust"

    @JvmField
    val APPLICATION_TAMP_SEQUENCE_ADJUST_CONFIRM = "application/tamp-sequence-adjust-confirm"

    @JvmField
    val APPLICATION_TAMP_STATUS_QUERY = "application/tamp-status-query"

    @JvmField
    val APPLICATION_TAMP_STATUS_RESPONSE = "application/tamp-status-response"

    @JvmField
    val APPLICATION_TAMP_UPDATE = "application/tamp-update"

    @JvmField
    val APPLICATION_TAMP_UPDATE_CONFIRM = "application/tamp-update-confirm"

    @JvmField
    val APPLICATION_TAXII_JSON = "application/taxii+json"

    @JvmField
    val APPLICATION_TD_JSON = "application/td+json"

    @JvmField
    val APPLICATION_TEI_XML = "application/tei+xml"

    @JvmField
    val APPLICATION_TETRA_ISI = "application/TETRA_ISI"

    @JvmField
    val APPLICATION_THRAUD_XML = "application/thraud+xml"

    @JvmField
    val APPLICATION_TIMESTAMPED_DATA = "application/timestamped-data"

    @JvmField
    val APPLICATION_TIMESTAMP_QUERY = "application/timestamp-query"

    @JvmField
    val APPLICATION_TIMESTAMP_REPLY = "application/timestamp-reply"

    @JvmField
    val APPLICATION_TLSRPT_GZIP = "application/tlsrpt+gzip"

    @JvmField
    val APPLICATION_TLSRPT_JSON = "application/tlsrpt+json"

    @JvmField
    val APPLICATION_TM_JSON = "application/tm+json"

    @JvmField
    val APPLICATION_TNAUTHLIST = "application/tnauthlist"

    @JvmField
    val APPLICATION_TOKEN_INTROSPECTION_JWT = "application/token-introspection+jwt"

    @JvmField
    val APPLICATION_TRICKLE_ICE_SDPFRAG = "application/trickle-ice-sdpfrag"

    @JvmField
    val APPLICATION_TRIG = "application/trig"

    @JvmField
    val APPLICATION_TTML_XML = "application/ttml+xml"

    @JvmField
    val APPLICATION_TVE_TRIGGER = "application/tve-trigger"

    @JvmField
    val APPLICATION_TZIF = "application/tzif"

    @JvmField
    val APPLICATION_TZIF_LEAP = "application/tzif-leap"

    @JvmField
    val APPLICATION_ULPFEC = "application/ulpfec"

    @JvmField
    val APPLICATION_URC_GRPSHEET_XML = "application/urc-grpsheet+xml"

    @JvmField
    val APPLICATION_URC_RESSHEET_XML = "application/urc-ressheet+xml"

    @JvmField
    val APPLICATION_URC_TARGETDESC_XML = "application/urc-targetdesc+xml"

    @JvmField
    val APPLICATION_URC_UISOCKETDESC_XML = "application/urc-uisocketdesc+xml"

    @JvmField
    val APPLICATION_VCARD_JSON = "application/vcard+json"

    @JvmField
    val APPLICATION_VCARD_XML = "application/vcard+xml"

    @JvmField
    val APPLICATION_VEMMI = "application/vemmi"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("APPLICATION_VND_ARISTANETWORKS_SWI"), DeprecationLevel.ERROR)
    val APPLICATION_VND_ARASTRA_SWI = "application/vnd.arastra.swi"

    @JvmField
    val APPLICATION_VND_CNCF_HELM_CHART_CONTENT_V1_TAR_GZIP = "application/vnd.cncf.helm.chart.content.v1.tar+gzip"

    @JvmField
    val APPLICATION_VND_CNCF_HELM_CHART_PROVENANCE_V1_PROV = "application/vnd.cncf.helm.chart.provenance.v1.prov"

    @JvmField
    val APPLICATION_VND_CNCF_HELM_CONFIG_V1_JSON = "application/vnd.cncf.helm.config.v1+json"

    @JvmField
    @Deprecated("Obsoleted by RFC7946", ReplaceWith("APPLICATION_GEO_JSON"), DeprecationLevel.ERROR)
    val APPLICATION_VND_GEO_JSON = "application/vnd.geo+json"

    @JvmField
    @Deprecated("Obsoleted by request", ReplaceWith("NULL"), DeprecationLevel.ERROR)
    val APPLICATION_VND_GOV_SK_E_FORM_XML = "application/vnd.gov.sk.e-form+xml"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("VND_AFPC_AFPLINEDATA"), DeprecationLevel.ERROR)
    val APPLICATION_VND_IBM_AFPLINEDATA = "application/vnd.ibm.afplinedata"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("APPLICATION_VND_AFPC_MODCA"), DeprecationLevel.ERROR)
    val APPLICATION_VND_IBM_MODCAP = "application/vnd.ibm.modcap"

    @JvmField
    val APPLICATION_VND_IMS_LIS_V2_RESULT_JSON = "application/vnd.ims.lis.v2.result+json"

    @JvmField
    val APPLICATION_VND_IMS_LTI_V2_TOOLCONSUMERPROFILE_JSON = "application/vnd.ims.lti.v2.toolconsumerprofile+json"

    @JvmField
    val APPLICATION_VND_IMS_LTI_V2_TOOLPROXY_JSON = "application/vnd.ims.lti.v2.toolproxy+json"

    @JvmField
    val APPLICATION_VND_IMS_LTI_V2_TOOLPROXY_ID_JSON = "application/vnd.ims.lti.v2.toolproxy.id+json"

    @JvmField
    val APPLICATION_VND_IMS_LTI_V2_TOOLSETTINGS_JSON = "application/vnd.ims.lti.v2.toolsettings+json"

    @JvmField
    val APPLICATION_VND_IMS_LTI_V2_TOOLSETTINGS_SIMPLE_JSON = "application/vnd.ims.lti.v2.toolsettings.simple+json"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("APPLICATION_VND_VISIONARY"), DeprecationLevel.ERROR)
    val APPLICATION_VND_INFORMIX_VISIONARY = "application/vnd.informix-visionary"

    @JvmField
    val APPLICATION_VND_1000MINDS_DECISION_MODEL_XML = "application/vnd.1000minds.decision-model+xml"

    @JvmField
    val APPLICATION_VND_1OB = "application/vnd.1ob"

    @JvmField
    val APPLICATION_VND_3GPP2_BCMCSINFO_XML = "application/vnd.3gpp2.bcmcsinfo+xml"

    @JvmField
    val APPLICATION_VND_3GPP2_SMS = "application/vnd.3gpp2.sms"

    @JvmField
    val APPLICATION_VND_3GPP2_TCAP = "application/vnd.3gpp2.tcap"

    @JvmField
    val APPLICATION_VND_3GPP_5GNAS = "application/vnd.3gpp.5gnas"

    @JvmField
    val APPLICATION_VND_3GPP_5GSA2X = "application/vnd.3gpp.5gsa2x"

    @JvmField
    val APPLICATION_VND_3GPP_5GSA2X_LOCAL_SERVICE_INFORMATION = "application/vnd.3gpp.5gsa2x-local-service-information"

    @JvmField
    val APPLICATION_VND_3GPP_ACCESS_TRANSFER_EVENTS_XML = "application/vnd.3gpp.access-transfer-events+xml"

    @JvmField
    val APPLICATION_VND_3GPP_BSF_XML = "application/vnd.3gpp.bsf+xml"

    @JvmField
    val APPLICATION_VND_3GPP_CRS_XML = "application/vnd.3gpp.crs+xml"

    @JvmField
    val APPLICATION_VND_3GPP_CURRENT_LOCATION_DISCOVERY_XML = "application/vnd.3gpp.current-location-discovery+xml"

    @JvmField
    val APPLICATION_VND_3GPP_GMOP_XML = "application/vnd.3gpp.GMOP+xml"

    @JvmField
    val APPLICATION_VND_3GPP_GTPC = "application/vnd.3gpp.gtpc"

    @JvmField
    val APPLICATION_VND_3GPP_INTERWORKING_DATA = "application/vnd.3gpp.interworking-data"

    @JvmField
    val APPLICATION_VND_3GPP_LPP = "application/vnd.3gpp.lpp"

    @JvmField
    val APPLICATION_VND_3GPP_MCDATA_AFFILIATION_COMMAND_XML = "application/vnd.3gpp.mcdata-affiliation-command+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCDATA_INFO_XML = "application/vnd.3gpp.mcdata-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCDATA_MSGSTORE_CTRL_REQUEST_XML = "application/vnd.3gpp.mcdata-msgstore-ctrl-request+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCDATA_PAYLOAD = "application/vnd.3gpp.mcdata-payload"

    @JvmField
    val APPLICATION_VND_3GPP_MCDATA_REGROUP_XML = "application/vnd.3gpp.mcdata-regroup+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCDATA_SERVICE_CONFIG_XML = "application/vnd.3gpp.mcdata-service-config+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCDATA_SIGNALLING = "application/vnd.3gpp.mcdata-signalling"

    @JvmField
    val APPLICATION_VND_3GPP_MCDATA_UE_CONFIG_XML = "application/vnd.3gpp.mcdata-ue-config+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCDATA_USER_PROFILE_XML = "application/vnd.3gpp.mcdata-user-profile+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_AFFILIATION_COMMAND_XML = "application/vnd.3gpp.mcptt-affiliation-command+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_FLOOR_REQUEST_XML = "application/vnd.3gpp.mcptt-floor-request+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_INFO_XML = "application/vnd.3gpp.mcptt-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_LOCATION_INFO_XML = "application/vnd.3gpp.mcptt-location-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_MBMS_USAGE_INFO_XML = "application/vnd.3gpp.mcptt-mbms-usage-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_REGROUP_XML = "application/vnd.3gpp.mcptt-regroup+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_SERVICE_CONFIG_XML = "application/vnd.3gpp.mcptt-service-config+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_SIGNED_XML = "application/vnd.3gpp.mcptt-signed+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_UE_CONFIG_XML = "application/vnd.3gpp.mcptt-ue-config+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_UE_INIT_CONFIG_XML = "application/vnd.3gpp.mcptt-ue-init-config+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCPTT_USER_PROFILE_XML = "application/vnd.3gpp.mcptt-user-profile+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCVIDEO_AFFILIATION_COMMAND_XML = "application/vnd.3gpp.mcvideo-affiliation-command+xml"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("APPLICATION_VND_3GPP_MCVIDEO_INFO_XML"), DeprecationLevel.ERROR)
    val APPLICATION_VND_3GPP_MCVIDEO_AFFILIATION_INFO_XML = "application/vnd.3gpp.mcvideo-affiliation-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCVIDEO_INFO_XML = "application/vnd.3gpp.mcvideo-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCVIDEO_LOCATION_INFO_XML = "application/vnd.3gpp.mcvideo-location-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCVIDEO_MBMS_USAGE_INFO_XML = "application/vnd.3gpp.mcvideo-mbms-usage-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCVIDEO_REGROUP_XML = "application/vnd.3gpp.mcvideo-regroup+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCVIDEO_SERVICE_CONFIG_XML = "application/vnd.3gpp.mcvideo-service-config+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCVIDEO_TRANSMISSION_REQUEST_XML = "application/vnd.3gpp.mcvideo-transmission-request+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCVIDEO_UE_CONFIG_XML = "application/vnd.3gpp.mcvideo-ue-config+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MCVIDEO_USER_PROFILE_XML = "application/vnd.3gpp.mcvideo-user-profile+xml"

    @JvmField
    val APPLICATION_VND_3GPP_MC_SIGNALLING_EAR = "application/vnd.3gpp.mc-signalling-ear"

    @JvmField
    val APPLICATION_VND_3GPP_MID_CALL_XML = "application/vnd.3gpp.mid-call+xml"

    @JvmField
    val APPLICATION_VND_3GPP_NGAP = "application/vnd.3gpp.ngap"

    @JvmField
    val APPLICATION_VND_3GPP_PFCP = "application/vnd.3gpp.pfcp"

    @JvmField
    val APPLICATION_VND_3GPP_PIC_BW_LARGE = "application/vnd.3gpp.pic-bw-large"

    @JvmField
    val APPLICATION_VND_3GPP_PIC_BW_SMALL = "application/vnd.3gpp.pic-bw-small"

    @JvmField
    val APPLICATION_VND_3GPP_PIC_BW_VAR = "application/vnd.3gpp.pic-bw-var"

    @JvmField
    val APPLICATION_VND_3GPP_PROSE_PC3ACH_XML = "application/vnd.3gpp-prose-pc3ach+xml"

    @JvmField
    val APPLICATION_VND_3GPP_PROSE_PC3A_XML = "application/vnd.3gpp-prose-pc3a+xml"

    @JvmField
    val APPLICATION_VND_3GPP_PROSE_PC3CH_XML = "application/vnd.3gpp-prose-pc3ch+xml"

    @JvmField
    val APPLICATION_VND_3GPP_PROSE_PC8_XML = "application/vnd.3gpp-prose-pc8+xml"

    @JvmField
    val APPLICATION_VND_3GPP_PROSE_XML = "application/vnd.3gpp-prose+xml"

    @JvmField
    val APPLICATION_VND_3GPP_S1AP = "application/vnd.3gpp.s1ap"

    @JvmField
    val APPLICATION_VND_3GPP_SEAL_GROUP_DOC_XML = "application/vnd.3gpp.seal-group-doc+xml"

    @JvmField
    val APPLICATION_VND_3GPP_SEAL_INFO_XML = "application/vnd.3gpp.seal-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_SEAL_LOCATION_INFO_XML = "application/vnd.3gpp.seal-location-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_SEAL_MBMS_USAGE_INFO_XML = "application/vnd.3gpp.seal-mbms-usage-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_SEAL_NETWORK_QOS_MANAGEMENT_INFO_XML = "application/vnd.3gpp.seal-network-QoS-management-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_SEAL_UE_CONFIG_INFO_XML = "application/vnd.3gpp.seal-ue-config-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_SEAL_UNICAST_INFO_XML = "application/vnd.3gpp.seal-unicast-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_SEAL_USER_PROFILE_INFO_XML = "application/vnd.3gpp.seal-user-profile-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_SMS = "application/vnd.3gpp.sms"

    @JvmField
    val APPLICATION_VND_3GPP_SMS_XML = "application/vnd.3gpp.sms+xml"

    @JvmField
    val APPLICATION_VND_3GPP_SRVCC_EXT_XML = "application/vnd.3gpp.srvcc-ext+xml"

    @JvmField
    val APPLICATION_VND_3GPP_SRVCC_INFO_XML = "application/vnd.3gpp.SRVCC-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_STATE_AND_EVENT_INFO_XML = "application/vnd.3gpp.state-and-event-info+xml"

    @JvmField
    val APPLICATION_VND_3GPP_USSD_XML = "application/vnd.3gpp.ussd+xml"

    @JvmField
    val APPLICATION_VND_3GPP_V2X = "application/vnd.3gpp.v2x"

    @JvmField
    val APPLICATION_VND_3GPP_V2X_LOCAL_SERVICE_INFORMATION = "application/vnd.3gpp-v2x-local-service-information"

    @JvmField
    val APPLICATION_VND_3GPP_VAE_INFO_XML = "application/vnd.3gpp.vae-info+xml"

    @JvmField
    val APPLICATION_VND_3LIGHTSSOFTWARE_IMAGESCAL = "application/vnd.3lightssoftware.imagescal"

    @JvmField
    val APPLICATION_VND_3M_POST_IT_NOTES = "application/vnd.3M.Post-it-Notes"

    @JvmField
    val APPLICATION_VND_ACCPAC_SIMPLY_ASO = "application/vnd.accpac.simply.aso"

    @JvmField
    val APPLICATION_VND_ACCPAC_SIMPLY_IMP = "application/vnd.accpac.simply.imp"

    @JvmField
    val APPLICATION_VND_ACM_ADDRESSXFER_JSON = "application/vnd.acm.addressxfer+json"

    @JvmField
    val APPLICATION_VND_ACM_CHATBOT_JSON = "application/vnd.acm.chatbot+json"

    @JvmField
    val APPLICATION_VND_ACUCOBOL = "application/vnd.acucobol"

    @JvmField
    val APPLICATION_VND_ACUCORP = "application/vnd.acucorp"

    @JvmField
    val APPLICATION_VND_ADOBE_FLASH_MOVIE = "application/vnd.adobe.flash.movie"

    @JvmField
    val APPLICATION_VND_ADOBE_FORMSCENTRAL_FCDT = "application/vnd.adobe.formscentral.fcdt"

    @JvmField
    val APPLICATION_VND_ADOBE_FXP = "application/vnd.adobe.fxp"

    @JvmField
    val APPLICATION_VND_ADOBE_PARTIAL_UPLOAD = "application/vnd.adobe.partial-upload"

    @JvmField
    val APPLICATION_VND_ADOBE_XDP_XML = "application/vnd.adobe.xdp+xml"

    @JvmField
    val APPLICATION_VND_AETHER_IMP = "application/vnd.aether.imp"

    @JvmField
    val APPLICATION_VND_AFPC_AFPLINEDATA = "application/vnd.afpc.afplinedata"

    @JvmField
    val APPLICATION_VND_AFPC_AFPLINEDATA_PAGEDEF = "application/vnd.afpc.afplinedata-pagedef"

    @JvmField
    val APPLICATION_VND_AFPC_CMOCA_CMRESOURCE = "application/vnd.afpc.cmoca-cmresource"

    @JvmField
    val APPLICATION_VND_AFPC_FOCA_CHARSET = "application/vnd.afpc.foca-charset"

    @JvmField
    val APPLICATION_VND_AFPC_FOCA_CODEDFONT = "application/vnd.afpc.foca-codedfont"

    @JvmField
    val APPLICATION_VND_AFPC_FOCA_CODEPAGE = "application/vnd.afpc.foca-codepage"

    @JvmField
    val APPLICATION_VND_AFPC_MODCA = "application/vnd.afpc.modca"

    @JvmField
    val APPLICATION_VND_AFPC_MODCA_CMTABLE = "application/vnd.afpc.modca-cmtable"

    @JvmField
    val APPLICATION_VND_AFPC_MODCA_FORMDEF = "application/vnd.afpc.modca-formdef"

    @JvmField
    val APPLICATION_VND_AFPC_MODCA_MEDIUMMAP = "application/vnd.afpc.modca-mediummap"

    @JvmField
    val APPLICATION_VND_AFPC_MODCA_OBJECTCONTAINER = "application/vnd.afpc.modca-objectcontainer"

    @JvmField
    val APPLICATION_VND_AFPC_MODCA_OVERLAY = "application/vnd.afpc.modca-overlay"

    @JvmField
    val APPLICATION_VND_AFPC_MODCA_PAGESEGMENT = "application/vnd.afpc.modca-pagesegment"

    @JvmField
    val APPLICATION_VND_AGE = "application/vnd.age"

    @JvmField
    val APPLICATION_VND_AHEAD_SPACE = "application/vnd.ahead.space"

    @JvmField
    val APPLICATION_VND_AH_BARCODE = "application/vnd.ah-barcode"

    @JvmField
    val APPLICATION_VND_AIRZIP_FILESECURE_AZF = "application/vnd.airzip.filesecure.azf"

    @JvmField
    val APPLICATION_VND_AIRZIP_FILESECURE_AZS = "application/vnd.airzip.filesecure.azs"

    @JvmField
    val APPLICATION_VND_AMADEUS_JSON = "application/vnd.amadeus+json"

    @JvmField
    val APPLICATION_VND_AMAZON_MOBI8_EBOOK = "application/vnd.amazon.mobi8-ebook"

    @JvmField
    val APPLICATION_VND_AMERICANDYNAMICS_ACC = "application/vnd.americandynamics.acc"

    @JvmField
    val APPLICATION_VND_AMIGA_AMI = "application/vnd.amiga.ami"

    @JvmField
    val APPLICATION_VND_AMUNDSEN_MAZE_XML = "application/vnd.amundsen.maze+xml"

    @JvmField
    val APPLICATION_VND_ANDROID_OTA = "application/vnd.android.ota"

    @JvmField
    val APPLICATION_VND_ANKI = "application/vnd.anki"

    @JvmField
    val APPLICATION_VND_ANSER_WEB_CERTIFICATE_ISSUE_INITIATION = "application/vnd.anser-web-certificate-issue-initiation"

    @JvmField
    val APPLICATION_VND_ANTIX_GAME_COMPONENT = "application/vnd.antix.game-component"

    @JvmField
    val APPLICATION_VND_APACHE_ARROW_FILE = "application/vnd.apache.arrow.file"

    @JvmField
    val APPLICATION_VND_APACHE_ARROW_STREAM = "application/vnd.apache.arrow.stream"

    @JvmField
    val APPLICATION_VND_APACHE_PARQUET = "application/vnd.apache.parquet"

    @JvmField
    val APPLICATION_VND_APACHE_THRIFT_BINARY = "application/vnd.apache.thrift.binary"

    @JvmField
    val APPLICATION_VND_APACHE_THRIFT_COMPACT = "application/vnd.apache.thrift.compact"

    @JvmField
    val APPLICATION_VND_APACHE_THRIFT_JSON = "application/vnd.apache.thrift.json"

    @JvmField
    val APPLICATION_VND_APEXLANG = "application/vnd.apexlang"

    @JvmField
    val APPLICATION_VND_API_JSON = "application/vnd.api+json"

    @JvmField
    val APPLICATION_VND_APLEXTOR_WARRP_JSON = "application/vnd.aplextor.warrp+json"

    @JvmField
    val APPLICATION_VND_APOTHEKENDE_RESERVATION_JSON = "application/vnd.apothekende.reservation+json"

    @JvmField
    val APPLICATION_VND_APPLE_INSTALLER_XML = "application/vnd.apple.installer+xml"

    @JvmField
    val APPLICATION_VND_APPLE_KEYNOTE = "application/vnd.apple.keynote"

    @JvmField
    val APPLICATION_VND_APPLE_MPEGURL = "application/vnd.apple.mpegurl"

    @JvmField
    val APPLICATION_VND_APPLE_NUMBERS = "application/vnd.apple.numbers"

    @JvmField
    val APPLICATION_VND_APPLE_PAGES = "application/vnd.apple.pages"

    @JvmField
    val APPLICATION_VND_ARISTANETWORKS_SWI = "application/vnd.aristanetworks.swi"

    @JvmField
    val APPLICATION_VND_ARTISAN_JSON = "application/vnd.artisan+json"

    @JvmField
    val APPLICATION_VND_ARTSQUARE = "application/vnd.artsquare"

    @JvmField
    val APPLICATION_VND_ASTRAEA_SOFTWARE_IOTA = "application/vnd.astraea-software.iota"

    @JvmField
    val APPLICATION_VND_AUDIOGRAPH = "application/vnd.audiograph"

    @JvmField
    val APPLICATION_VND_AUTOPACKAGE = "application/vnd.autopackage"

    @JvmField
    val APPLICATION_VND_AVALON_JSON = "application/vnd.avalon+json"

    @JvmField
    val APPLICATION_VND_AVISTAR_XML = "application/vnd.avistar+xml"

    @JvmField
    val APPLICATION_VND_BALSAMIQ_BMML_XML = "application/vnd.balsamiq.bmml+xml"

    @JvmField
    val APPLICATION_VND_BALSAMIQ_BMPR = "application/vnd.balsamiq.bmpr"

    @JvmField
    val APPLICATION_VND_BANANA_ACCOUNTING = "application/vnd.banana-accounting"

    @JvmField
    val APPLICATION_VND_BBF_USP_ERROR = "application/vnd.bbf.usp.error"

    @JvmField
    val APPLICATION_VND_BBF_USP_MSG = "application/vnd.bbf.usp.msg"

    @JvmField
    val APPLICATION_VND_BBF_USP_MSG_JSON = "application/vnd.bbf.usp.msg+json"

    @JvmField
    val APPLICATION_VND_BEKITZUR_STECH_JSON = "application/vnd.bekitzur-stech+json"

    @JvmField
    val APPLICATION_VND_BELIGHTSOFT_LHZD_ZIP = "application/vnd.belightsoft.lhzd+zip"

    @JvmField
    val APPLICATION_VND_BELIGHTSOFT_LHZL_ZIP = "application/vnd.belightsoft.lhzl+zip"

    @JvmField
    val APPLICATION_VND_BINT_MED_CONTENT = "application/vnd.bint.med-content"

    @JvmField
    val APPLICATION_VND_BIOPAX_RDF_XML = "application/vnd.biopax.rdf+xml"

    @JvmField
    val APPLICATION_VND_BLINK_IDB_VALUE_WRAPPER = "application/vnd.blink-idb-value-wrapper"

    @JvmField
    val APPLICATION_VND_BLUEICE_MULTIPASS = "application/vnd.blueice.multipass"

    @JvmField
    val APPLICATION_VND_BLUETOOTH_EP_OOB = "application/vnd.bluetooth.ep.oob"

    @JvmField
    val APPLICATION_VND_BLUETOOTH_LE_OOB = "application/vnd.bluetooth.le.oob"

    @JvmField
    val APPLICATION_VND_BMI = "application/vnd.bmi"

    @JvmField
    val APPLICATION_VND_BPF = "application/vnd.bpf"

    @JvmField
    val APPLICATION_VND_BPF3 = "application/vnd.bpf3"

    @JvmField
    val APPLICATION_VND_BUSINESSOBJECTS = "application/vnd.businessobjects"

    @JvmField
    val APPLICATION_VND_BYU_UAPI_JSON = "application/vnd.byu.uapi+json"

    @JvmField
    val APPLICATION_VND_BZIP3 = "application/vnd.bzip3"

    @JvmField
    val APPLICATION_VND_C3VOC_SCHEDULE_XML = "application/vnd.c3voc.schedule+xml"

    @JvmField
    val APPLICATION_VND_CAB_JSCRIPT = "application/vnd.cab-jscript"

    @JvmField
    val APPLICATION_VND_CANON_CPDL = "application/vnd.canon-cpdl"

    @JvmField
    val APPLICATION_VND_CANON_LIPS = "application/vnd.canon-lips"

    @JvmField
    val APPLICATION_VND_CAPASYSTEMS_PG_JSON = "application/vnd.capasystems-pg+json"

    @JvmField
    val APPLICATION_VND_CENDIO_THINLINC_CLIENTCONF = "application/vnd.cendio.thinlinc.clientconf"

    @JvmField
    val APPLICATION_VND_CENTURY_SYSTEMS_TCP_STREAM = "application/vnd.century-systems.tcp_stream"

    @JvmField
    val APPLICATION_VND_CHEMDRAW_XML = "application/vnd.chemdraw+xml"

    @JvmField
    val APPLICATION_VND_CHESS_PGN = "application/vnd.chess-pgn"

    @JvmField
    val APPLICATION_VND_CHIPNUTS_KARAOKE_MMD = "application/vnd.chipnuts.karaoke-mmd"

    @JvmField
    val APPLICATION_VND_CIEDI = "application/vnd.ciedi"

    @JvmField
    val APPLICATION_VND_CINDERELLA = "application/vnd.cinderella"

    @JvmField
    val APPLICATION_VND_CIRPACK_ISDN_EXT = "application/vnd.cirpack.isdn-ext"

    @JvmField
    val APPLICATION_VND_CITATIONSTYLES_STYLE_XML = "application/vnd.citationstyles.style+xml"

    @JvmField
    val APPLICATION_VND_CLAYMORE = "application/vnd.claymore"

    @JvmField
    val APPLICATION_VND_CLOANTO_RP9 = "application/vnd.cloanto.rp9"

    @JvmField
    val APPLICATION_VND_CLONK_C4GROUP = "application/vnd.clonk.c4group"

    @JvmField
    val APPLICATION_VND_CLUETRUST_CARTOMOBILE_CONFIG = "application/vnd.cluetrust.cartomobile-config"

    @JvmField
    val APPLICATION_VND_CLUETRUST_CARTOMOBILE_CONFIG_PKG = "application/vnd.cluetrust.cartomobile-config-pkg"

    @JvmField
    val APPLICATION_VND_COFFEESCRIPT = "application/vnd.coffeescript"

    @JvmField
    val APPLICATION_VND_COLLABIO_XODOCUMENTS_DOCUMENT = "application/vnd.collabio.xodocuments.document"

    @JvmField
    val APPLICATION_VND_COLLABIO_XODOCUMENTS_DOCUMENT_TEMPLATE = "application/vnd.collabio.xodocuments.document-template"

    @JvmField
    val APPLICATION_VND_COLLABIO_XODOCUMENTS_PRESENTATION = "application/vnd.collabio.xodocuments.presentation"

    @JvmField
    val APPLICATION_VND_COLLABIO_XODOCUMENTS_PRESENTATION_TEMPLATE = "application/vnd.collabio.xodocuments.presentation-template"

    @JvmField
    val APPLICATION_VND_COLLABIO_XODOCUMENTS_SPREADSHEET = "application/vnd.collabio.xodocuments.spreadsheet"

    @JvmField
    val APPLICATION_VND_COLLABIO_XODOCUMENTS_SPREADSHEET_TEMPLATE = "application/vnd.collabio.xodocuments.spreadsheet-template"

    @JvmField
    val APPLICATION_VND_COLLECTION_DOC_JSON = "application/vnd.collection.doc+json"

    @JvmField
    val APPLICATION_VND_COLLECTION_JSON = "application/vnd.collection+json"

    @JvmField
    val APPLICATION_VND_COLLECTION_NEXT_JSON = "application/vnd.collection.next+json"

    @JvmField
    val APPLICATION_VND_COMICBOOK_RAR = "application/vnd.comicbook-rar"

    @JvmField
    val APPLICATION_VND_COMICBOOK_ZIP = "application/vnd.comicbook+zip"

    @JvmField
    val APPLICATION_VND_COMMERCE_BATTELLE = "application/vnd.commerce-battelle"

    @JvmField
    val APPLICATION_VND_COMMONSPACE = "application/vnd.commonspace"

    @JvmField
    val APPLICATION_VND_CONTACT_CMSG = "application/vnd.contact.cmsg"

    @JvmField
    val APPLICATION_VND_COREOS_IGNITION_JSON = "application/vnd.coreos.ignition+json"

    @JvmField
    val APPLICATION_VND_COSMOCALLER = "application/vnd.cosmocaller"

    @JvmField
    val APPLICATION_VND_CRICK_CLICKER = "application/vnd.crick.clicker"

    @JvmField
    val APPLICATION_VND_CRICK_CLICKER_KEYBOARD = "application/vnd.crick.clicker.keyboard"

    @JvmField
    val APPLICATION_VND_CRICK_CLICKER_PALETTE = "application/vnd.crick.clicker.palette"

    @JvmField
    val APPLICATION_VND_CRICK_CLICKER_TEMPLATE = "application/vnd.crick.clicker.template"

    @JvmField
    val APPLICATION_VND_CRICK_CLICKER_WORDBANK = "application/vnd.crick.clicker.wordbank"

    @JvmField
    val APPLICATION_VND_CRITICALTOOLS_WBS_XML = "application/vnd.criticaltools.wbs+xml"

    @JvmField
    val APPLICATION_VND_CRYPTII_PIPE_JSON = "application/vnd.cryptii.pipe+json"

    @JvmField
    val APPLICATION_VND_CRYPTOMATOR_ENCRYPTED = "application/vnd.cryptomator.encrypted"

    @JvmField
    val APPLICATION_VND_CRYPTOMATOR_VAULT = "application/vnd.cryptomator.vault"

    @JvmField
    val APPLICATION_VND_CRYPTO_SHADE_FILE = "application/vnd.crypto-shade-file"

    @JvmField
    val APPLICATION_VND_CTCT_WS_XML = "application/vnd.ctct.ws+xml"

    @JvmField
    val APPLICATION_VND_CTC_POSML = "application/vnd.ctc-posml"

    @JvmField
    val APPLICATION_VND_CUPS_PDF = "application/vnd.cups-pdf"

    @JvmField
    val APPLICATION_VND_CUPS_POSTSCRIPT = "application/vnd.cups-postscript"

    @JvmField
    val APPLICATION_VND_CUPS_PPD = "application/vnd.cups-ppd"

    @JvmField
    val APPLICATION_VND_CUPS_RASTER = "application/vnd.cups-raster"

    @JvmField
    val APPLICATION_VND_CUPS_RAW = "application/vnd.cups-raw"

    @JvmField
    val APPLICATION_VND_CURL = "application/vnd.curl"

    @JvmField
    val APPLICATION_VND_CYAN_DEAN_ROOT_XML = "application/vnd.cyan.dean.root+xml"

    @JvmField
    val APPLICATION_VND_CYBANK = "application/vnd.cybank"

    @JvmField
    val APPLICATION_VND_CYCLONEDX_JSON = "application/vnd.cyclonedx+json"

    @JvmField
    val APPLICATION_VND_CYCLONEDX_XML = "application/vnd.cyclonedx+xml"

    @JvmField
    val APPLICATION_VND_D2L_COURSEPACKAGE1P0_ZIP = "application/vnd.d2l.coursepackage1p0+zip"

    @JvmField
    val APPLICATION_VND_D3M_DATASET = "application/vnd.d3m-dataset"

    @JvmField
    val APPLICATION_VND_D3M_PROBLEM = "application/vnd.d3m-problem"

    @JvmField
    val APPLICATION_VND_DART = "application/vnd.dart"

    @JvmField
    val APPLICATION_VND_DATALOG = "application/vnd.datalog"

    @JvmField
    val APPLICATION_VND_DATAPACKAGE_JSON = "application/vnd.datapackage+json"

    @JvmField
    val APPLICATION_VND_DATARESOURCE_JSON = "application/vnd.dataresource+json"

    @JvmField
    val APPLICATION_VND_DATA_VISION_RDZ = "application/vnd.data-vision.rdz"

    @JvmField
    val APPLICATION_VND_DBF = "application/vnd.dbf"

    @JvmField
    val APPLICATION_VND_DEBIAN_BINARY_PACKAGE = "application/vnd.debian.binary-package"

    @JvmField
    val APPLICATION_VND_DECE_DATA = "application/vnd.dece.data"

    @JvmField
    val APPLICATION_VND_DECE_TTML_XML = "application/vnd.dece.ttml+xml"

    @JvmField
    val APPLICATION_VND_DECE_UNSPECIFIED = "application/vnd.dece.unspecified"

    @JvmField
    val APPLICATION_VND_DECE_ZIP = "application/vnd.dece.zip"

    @JvmField
    val APPLICATION_VND_DENOVO_FCSELAYOUT_LINK = "application/vnd.denovo.fcselayout-link"

    @JvmField
    val APPLICATION_VND_DESMUME_MOVIE = "application/vnd.desmume.movie"

    @JvmField
    val APPLICATION_VND_DIR_BI_PLATE_DL_NOSUFFIX = "application/vnd.dir-bi.plate-dl-nosuffix"

    @JvmField
    val APPLICATION_VND_DM_DELEGATION_XML = "application/vnd.dm.delegation+xml"

    @JvmField
    val APPLICATION_VND_DNA = "application/vnd.dna"

    @JvmField
    val APPLICATION_VND_DOCUMENT_JSON = "application/vnd.document+json"

    @JvmField
    val APPLICATION_VND_DOLBY_MOBILE_1 = "application/vnd.dolby.mobile.1"

    @JvmField
    val APPLICATION_VND_DOLBY_MOBILE_2 = "application/vnd.dolby.mobile.2"

    @JvmField
    val APPLICATION_VND_DOREMIR_SCORECLOUD_BINARY_DOCUMENT = "application/vnd.doremir.scorecloud-binary-document"

    @JvmField
    val APPLICATION_VND_DPGRAPH = "application/vnd.dpgraph"

    @JvmField
    val APPLICATION_VND_DREAMFACTORY = "application/vnd.dreamfactory"

    @JvmField
    val APPLICATION_VND_DRIVE_JSON = "application/vnd.drive+json"

    @JvmField
    val APPLICATION_VND_DTG_LOCAL = "application/vnd.dtg.local"

    @JvmField
    val APPLICATION_VND_DTG_LOCAL_FLASH = "application/vnd.dtg.local.flash"

    @JvmField
    val APPLICATION_VND_DTG_LOCAL_HTML = "application/vnd.dtg.local.html"

    @JvmField
    val APPLICATION_VND_DVB_AIT = "application/vnd.dvb.ait"

    @JvmField
    val APPLICATION_VND_DVB_DVBISL_XML = "application/vnd.dvb.dvbisl+xml"

    @JvmField
    val APPLICATION_VND_DVB_DVBJ = "application/vnd.dvb.dvbj"

    @JvmField
    val APPLICATION_VND_DVB_ESGCONTAINER = "application/vnd.dvb.esgcontainer"

    @JvmField
    val APPLICATION_VND_DVB_IPDCDFTNOTIFACCESS = "application/vnd.dvb.ipdcdftnotifaccess"

    @JvmField
    val APPLICATION_VND_DVB_IPDCESGACCESS = "application/vnd.dvb.ipdcesgaccess"

    @JvmField
    val APPLICATION_VND_DVB_IPDCESGACCESS2 = "application/vnd.dvb.ipdcesgaccess2"

    @JvmField
    val APPLICATION_VND_DVB_IPDCESGPDD = "application/vnd.dvb.ipdcesgpdd"

    @JvmField
    val APPLICATION_VND_DVB_IPDCROAMING = "application/vnd.dvb.ipdcroaming"

    @JvmField
    val APPLICATION_VND_DVB_IPTV_ALFEC_BASE = "application/vnd.dvb.iptv.alfec-base"

    @JvmField
    val APPLICATION_VND_DVB_IPTV_ALFEC_ENHANCEMENT = "application/vnd.dvb.iptv.alfec-enhancement"

    @JvmField
    val APPLICATION_VND_DVB_NOTIF_AGGREGATE_ROOT_XML = "application/vnd.dvb.notif-aggregate-root+xml"

    @JvmField
    val APPLICATION_VND_DVB_NOTIF_CONTAINER_XML = "application/vnd.dvb.notif-container+xml"

    @JvmField
    val APPLICATION_VND_DVB_NOTIF_GENERIC_XML = "application/vnd.dvb.notif-generic+xml"

    @JvmField
    val APPLICATION_VND_DVB_NOTIF_IA_MSGLIST_XML = "application/vnd.dvb.notif-ia-msglist+xml"

    @JvmField
    val APPLICATION_VND_DVB_NOTIF_IA_REGISTRATION_REQUEST_XML = "application/vnd.dvb.notif-ia-registration-request+xml"

    @JvmField
    val APPLICATION_VND_DVB_NOTIF_IA_REGISTRATION_RESPONSE_XML = "application/vnd.dvb.notif-ia-registration-response+xml"

    @JvmField
    val APPLICATION_VND_DVB_NOTIF_INIT_XML = "application/vnd.dvb.notif-init+xml"

    @JvmField
    val APPLICATION_VND_DVB_PFR = "application/vnd.dvb.pfr"

    @JvmField
    val APPLICATION_VND_DVB_SERVICE = "application/vnd.dvb.service"

    @JvmField
    val APPLICATION_VND_DXR = "application/vnd.dxr"

    @JvmField
    val APPLICATION_VND_DYNAGEO = "application/vnd.dynageo"

    @JvmField
    val APPLICATION_VND_DZR = "application/vnd.dzr"

    @JvmField
    val APPLICATION_VND_EASYKARAOKE_CDGDOWNLOAD = "application/vnd.easykaraoke.cdgdownload"

    @JvmField
    val APPLICATION_VND_ECDIS_UPDATE = "application/vnd.ecdis-update"

    @JvmField
    val APPLICATION_VND_ECIP_RLP = "application/vnd.ecip.rlp"

    @JvmField
    val APPLICATION_VND_ECLIPSE_DITTO_JSON = "application/vnd.eclipse.ditto+json"

    @JvmField
    val APPLICATION_VND_ECOWIN_CHART = "application/vnd.ecowin.chart"

    @JvmField
    val APPLICATION_VND_ECOWIN_FILEREQUEST = "application/vnd.ecowin.filerequest"

    @JvmField
    val APPLICATION_VND_ECOWIN_FILEUPDATE = "application/vnd.ecowin.fileupdate"

    @JvmField
    val APPLICATION_VND_ECOWIN_SERIES = "application/vnd.ecowin.series"

    @JvmField
    val APPLICATION_VND_ECOWIN_SERIESREQUEST = "application/vnd.ecowin.seriesrequest"

    @JvmField
    val APPLICATION_VND_ECOWIN_SERIESUPDATE = "application/vnd.ecowin.seriesupdate"

    @JvmField
    val APPLICATION_VND_EFI_IMG = "application/vnd.efi.img"

    @JvmField
    val APPLICATION_VND_EFI_ISO = "application/vnd.efi.iso"

    @JvmField
    val APPLICATION_VND_ELN_ZIP = "application/vnd.eln+zip"

    @JvmField
    val APPLICATION_VND_EMCLIENT_ACCESSREQUEST_XML = "application/vnd.emclient.accessrequest+xml"

    @JvmField
    val APPLICATION_VND_ENLIVEN = "application/vnd.enliven"

    @JvmField
    val APPLICATION_VND_ENPHASE_ENVOY = "application/vnd.enphase.envoy"

    @JvmField
    val APPLICATION_VND_EPRINTS_DATA_XML = "application/vnd.eprints.data+xml"

    @JvmField
    val APPLICATION_VND_EPSON_ESF = "application/vnd.epson.esf"

    @JvmField
    val APPLICATION_VND_EPSON_MSF = "application/vnd.epson.msf"

    @JvmField
    val APPLICATION_VND_EPSON_QUICKANIME = "application/vnd.epson.quickanime"

    @JvmField
    val APPLICATION_VND_EPSON_SALT = "application/vnd.epson.salt"

    @JvmField
    val APPLICATION_VND_EPSON_SSF = "application/vnd.epson.ssf"

    @JvmField
    val APPLICATION_VND_ERICSSON_QUICKCALL = "application/vnd.ericsson.quickcall"

    @JvmField
    val APPLICATION_VND_EROFS = "application/vnd.erofs"

    @JvmField
    val APPLICATION_VND_ESPASS_ESPASS_ZIP = "application/vnd.espass-espass+zip"

    @JvmField
    val APPLICATION_VND_ESZIGNO3_XML = "application/vnd.eszigno3+xml"

    @JvmField
    val APPLICATION_VND_ETSI_AOC_XML = "application/vnd.etsi.aoc+xml"

    @JvmField
    val APPLICATION_VND_ETSI_ASIC_E_ZIP = "application/vnd.etsi.asic-e+zip"

    @JvmField
    val APPLICATION_VND_ETSI_ASIC_S_ZIP = "application/vnd.etsi.asic-s+zip"

    @JvmField
    val APPLICATION_VND_ETSI_CUG_XML = "application/vnd.etsi.cug+xml"

    @JvmField
    val APPLICATION_VND_ETSI_IPTVCOMMAND_XML = "application/vnd.etsi.iptvcommand+xml"

    @JvmField
    val APPLICATION_VND_ETSI_IPTVDISCOVERY_XML = "application/vnd.etsi.iptvdiscovery+xml"

    @JvmField
    val APPLICATION_VND_ETSI_IPTVPROFILE_XML = "application/vnd.etsi.iptvprofile+xml"

    @JvmField
    val APPLICATION_VND_ETSI_IPTVSAD_BC_XML = "application/vnd.etsi.iptvsad-bc+xml"

    @JvmField
    val APPLICATION_VND_ETSI_IPTVSAD_COD_XML = "application/vnd.etsi.iptvsad-cod+xml"

    @JvmField
    val APPLICATION_VND_ETSI_IPTVSAD_NPVR_XML = "application/vnd.etsi.iptvsad-npvr+xml"

    @JvmField
    val APPLICATION_VND_ETSI_IPTVSERVICE_XML = "application/vnd.etsi.iptvservice+xml"

    @JvmField
    val APPLICATION_VND_ETSI_IPTVSYNC_XML = "application/vnd.etsi.iptvsync+xml"

    @JvmField
    val APPLICATION_VND_ETSI_IPTVUEPROFILE_XML = "application/vnd.etsi.iptvueprofile+xml"

    @JvmField
    val APPLICATION_VND_ETSI_MCID_XML = "application/vnd.etsi.mcid+xml"

    @JvmField
    val APPLICATION_VND_ETSI_MHEG5 = "application/vnd.etsi.mheg5"

    @JvmField
    val APPLICATION_VND_ETSI_OVERLOAD_CONTROL_POLICY_DATASET_XML = "application/vnd.etsi.overload-control-policy-dataset+xml"

    @JvmField
    val APPLICATION_VND_ETSI_PSTN_XML = "application/vnd.etsi.pstn+xml"

    @JvmField
    val APPLICATION_VND_ETSI_SCI_XML = "application/vnd.etsi.sci+xml"

    @JvmField
    val APPLICATION_VND_ETSI_SIMSERVS_XML = "application/vnd.etsi.simservs+xml"

    @JvmField
    val APPLICATION_VND_ETSI_TIMESTAMP_TOKEN = "application/vnd.etsi.timestamp-token"

    @JvmField
    val APPLICATION_VND_ETSI_TSL_DER = "application/vnd.etsi.tsl.der"

    @JvmField
    val APPLICATION_VND_ETSI_TSL_XML = "application/vnd.etsi.tsl+xml"

    @JvmField
    val APPLICATION_VND_EUDORA_DATA = "application/vnd.eudora.data"

    @JvmField
    val APPLICATION_VND_EU_KASPARIAN_CAR_JSON = "application/vnd.eu.kasparian.car+json"

    @JvmField
    val APPLICATION_VND_EVOLV_ECIG_PROFILE = "application/vnd.evolv.ecig.profile"

    @JvmField
    val APPLICATION_VND_EVOLV_ECIG_SETTINGS = "application/vnd.evolv.ecig.settings"

    @JvmField
    val APPLICATION_VND_EVOLV_ECIG_THEME = "application/vnd.evolv.ecig.theme"

    @JvmField
    val APPLICATION_VND_EXSTREAM_EMPOWER_ZIP = "application/vnd.exstream-empower+zip"

    @JvmField
    val APPLICATION_VND_EXSTREAM_PACKAGE = "application/vnd.exstream-package"

    @JvmField
    val APPLICATION_VND_EZPIX_ALBUM = "application/vnd.ezpix-album"

    @JvmField
    val APPLICATION_VND_EZPIX_PACKAGE = "application/vnd.ezpix-package"

    @JvmField
    val APPLICATION_VND_FAMILYSEARCH_GEDCOM_ZIP = "application/vnd.familysearch.gedcom+zip"

    @JvmField
    val APPLICATION_VND_FASTCOPY_DISK_IMAGE = "application/vnd.fastcopy-disk-image"

    @JvmField
    val APPLICATION_VND_FDSN_MSEED = "application/vnd.fdsn.mseed"

    @JvmField
    val APPLICATION_VND_FDSN_SEED = "application/vnd.fdsn.seed"

    @JvmField
    val APPLICATION_VND_FFSNS = "application/vnd.ffsns"

    @JvmField
    val APPLICATION_VND_FICLAB_FLB_ZIP = "application/vnd.ficlab.flb+zip"

    @JvmField
    val APPLICATION_VND_FILMIT_ZFC = "application/vnd.filmit.zfc"

    @JvmField
    val APPLICATION_VND_FINTS = "application/vnd.fints"

    @JvmField
    val APPLICATION_VND_FIREMONKEYS_CLOUDCELL = "application/vnd.firemonkeys.cloudcell"

    @JvmField
    val APPLICATION_VND_FLOGRAPHIT = "application/vnd.FloGraphIt"

    @JvmField
    val APPLICATION_VND_FLUXTIME_CLIP = "application/vnd.fluxtime.clip"

    @JvmField
    val APPLICATION_VND_FONT_FONTFORGE_SFD = "application/vnd.font-fontforge-sfd"

    @JvmField
    val APPLICATION_VND_FRAMEMAKER = "application/vnd.framemaker"

    @JvmField
    val APPLICATION_VND_FREELOG_COMIC = "application/vnd.freelog.comic"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("NULL"), DeprecationLevel.ERROR)
    val APPLICATION_VND_FROGANS_FNC = "application/vnd.frogans.fnc"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("NULL"), DeprecationLevel.ERROR)
    val APPLICATION_VND_FROGANS_LTF = "application/vnd.frogans.ltf"

    @JvmField
    val APPLICATION_VND_FSC_WEBLAUNCH = "application/vnd.fsc.weblaunch"

    @JvmField
    val APPLICATION_VND_FUJIFILM_FB_DOCUWORKS = "application/vnd.fujifilm.fb.docuworks"

    @JvmField
    val APPLICATION_VND_FUJIFILM_FB_DOCUWORKS_BINDER = "application/vnd.fujifilm.fb.docuworks.binder"

    @JvmField
    val APPLICATION_VND_FUJIFILM_FB_DOCUWORKS_CONTAINER = "application/vnd.fujifilm.fb.docuworks.container"

    @JvmField
    val APPLICATION_VND_FUJIFILM_FB_JFI_XML = "application/vnd.fujifilm.fb.jfi+xml"

    @JvmField
    val APPLICATION_VND_FUJITSU_OASYS = "application/vnd.fujitsu.oasys"

    @JvmField
    val APPLICATION_VND_FUJITSU_OASYS2 = "application/vnd.fujitsu.oasys2"

    @JvmField
    val APPLICATION_VND_FUJITSU_OASYS3 = "application/vnd.fujitsu.oasys3"

    @JvmField
    val APPLICATION_VND_FUJITSU_OASYSGP = "application/vnd.fujitsu.oasysgp"

    @JvmField
    val APPLICATION_VND_FUJITSU_OASYSPRS = "application/vnd.fujitsu.oasysprs"

    @JvmField
    val APPLICATION_VND_FUJIXEROX_ART4 = "application/vnd.fujixerox.ART4"

    @JvmField
    val APPLICATION_VND_FUJIXEROX_ART_EX = "application/vnd.fujixerox.ART-EX"

    @JvmField
    val APPLICATION_VND_FUJIXEROX_DDD = "application/vnd.fujixerox.ddd"

    @JvmField
    val APPLICATION_VND_FUJIXEROX_DOCUWORKS = "application/vnd.fujixerox.docuworks"

    @JvmField
    val APPLICATION_VND_FUJIXEROX_DOCUWORKS_BINDER = "application/vnd.fujixerox.docuworks.binder"

    @JvmField
    val APPLICATION_VND_FUJIXEROX_DOCUWORKS_CONTAINER = "application/vnd.fujixerox.docuworks.container"

    @JvmField
    val APPLICATION_VND_FUJIXEROX_HBPL = "application/vnd.fujixerox.HBPL"

    @JvmField
    val APPLICATION_VND_FUTOIN_CBOR = "application/vnd.futoin+cbor"

    @JvmField
    val APPLICATION_VND_FUTOIN_JSON = "application/vnd.futoin+json"

    @JvmField
    val APPLICATION_VND_FUT_MISNET = "application/vnd.fut-misnet"

    @JvmField
    val APPLICATION_VND_FUZZYSHEET = "application/vnd.fuzzysheet"

    @JvmField
    val APPLICATION_VND_F_SECURE_MOBILE = "application/vnd.f-secure.mobile"

    @JvmField
    val APPLICATION_VND_GENOMATIX_TUXEDO = "application/vnd.genomatix.tuxedo"

    @JvmField
    val APPLICATION_VND_GENOZIP = "application/vnd.genozip"

    @JvmField
    val APPLICATION_VND_GENTICS_GRD_JSON = "application/vnd.gentics.grd+json"

    @JvmField
    val APPLICATION_VND_GENTOO_CATMETADATA_XML = "application/vnd.gentoo.catmetadata+xml"

    @JvmField
    val APPLICATION_VND_GENTOO_EBUILD = "application/vnd.gentoo.ebuild"

    @JvmField
    val APPLICATION_VND_GENTOO_ECLASS = "application/vnd.gentoo.eclass"

    @JvmField
    val APPLICATION_VND_GENTOO_GPKG = "application/vnd.gentoo.gpkg"

    @JvmField
    val APPLICATION_VND_GENTOO_MANIFEST = "application/vnd.gentoo.manifest"

    @JvmField
    val APPLICATION_VND_GENTOO_PKGMETADATA_XML = "application/vnd.gentoo.pkgmetadata+xml"

    @JvmField
    val APPLICATION_VND_GENTOO_XPAK = "application/vnd.gentoo.xpak"

    @JvmField
    @Deprecated("Obsoleted by request", ReplaceWith("NULL"), DeprecationLevel.ERROR)
    val APPLICATION_VND_GEOCUBE_XML = "application/vnd.geocube+xml"

    @JvmField
    val APPLICATION_VND_GEOGEBRA_FILE = "application/vnd.geogebra.file"

    @JvmField
    val APPLICATION_VND_GEOGEBRA_SLIDES = "application/vnd.geogebra.slides"

    @JvmField
    val APPLICATION_VND_GEOGEBRA_TOOL = "application/vnd.geogebra.tool"

    @JvmField
    val APPLICATION_VND_GEOMETRY_EXPLORER = "application/vnd.geometry-explorer"

    @JvmField
    val APPLICATION_VND_GEONEXT = "application/vnd.geonext"

    @JvmField
    val APPLICATION_VND_GEOPLAN = "application/vnd.geoplan"

    @JvmField
    val APPLICATION_VND_GEOSPACE = "application/vnd.geospace"

    @JvmField
    val APPLICATION_VND_GERBER = "application/vnd.gerber"

    @JvmField
    val APPLICATION_VND_GLOBALPLATFORM_CARD_CONTENT_MGT = "application/vnd.globalplatform.card-content-mgt"

    @JvmField
    val APPLICATION_VND_GLOBALPLATFORM_CARD_CONTENT_MGT_RESPONSE = "application/vnd.globalplatform.card-content-mgt-response"

    @JvmField
    @Deprecated("Deprecated")
    val APPLICATION_VND_GMX = "application/vnd.gmx"

    @JvmField
    val APPLICATION_VND_GNU_TALER_EXCHANGE_JSON = "application/vnd.gnu.taler.exchange+json"

    @JvmField
    val APPLICATION_VND_GNU_TALER_MERCHANT_JSON = "application/vnd.gnu.taler.merchant+json"

    @JvmField
    val APPLICATION_VND_GOOGLE_EARTH_KML_XML = "application/vnd.google-earth.kml+xml"

    @JvmField
    val APPLICATION_VND_GOOGLE_EARTH_KMZ = "application/vnd.google-earth.kmz"

    @JvmField
    val APPLICATION_VND_GOV_SK_E_FORM_ZIP = "application/vnd.gov.sk.e-form+zip"

    @JvmField
    val APPLICATION_VND_GOV_SK_XMLDATACONTAINER_XML = "application/vnd.gov.sk.xmldatacontainer+xml"

    @JvmField
    val APPLICATION_VND_GPXSEE_MAP_XML = "application/vnd.gpxsee.map+xml"

    @JvmField
    val APPLICATION_VND_GRAFEQ = "application/vnd.grafeq"

    @JvmField
    val APPLICATION_VND_GRIDMP = "application/vnd.gridmp"

    @JvmField
    val APPLICATION_VND_GROOVE_ACCOUNT = "application/vnd.groove-account"

    @JvmField
    val APPLICATION_VND_GROOVE_HELP = "application/vnd.groove-help"

    @JvmField
    val APPLICATION_VND_GROOVE_IDENTITY_MESSAGE = "application/vnd.groove-identity-message"

    @JvmField
    val APPLICATION_VND_GROOVE_INJECTOR = "application/vnd.groove-injector"

    @JvmField
    val APPLICATION_VND_GROOVE_TOOL_MESSAGE = "application/vnd.groove-tool-message"

    @JvmField
    val APPLICATION_VND_GROOVE_TOOL_TEMPLATE = "application/vnd.groove-tool-template"

    @JvmField
    val APPLICATION_VND_GROOVE_VCARD = "application/vnd.groove-vcard"

    @JvmField
    val APPLICATION_VND_HAL_JSON = "application/vnd.hal+json"

    @JvmField
    val APPLICATION_VND_HAL_XML = "application/vnd.hal+xml"

    @JvmField
    val APPLICATION_VND_HANDHELD_ENTERTAINMENT_XML = "application/vnd.HandHeld-Entertainment+xml"

    @JvmField
    val APPLICATION_VND_HBCI = "application/vnd.hbci"

    @JvmField
    val APPLICATION_VND_HCL_BIREPORTS = "application/vnd.hcl-bireports"

    @JvmField
    val APPLICATION_VND_HC_JSON = "application/vnd.hc+json"

    @JvmField
    val APPLICATION_VND_HDT = "application/vnd.hdt"

    @JvmField
    val APPLICATION_VND_HEROKU_JSON = "application/vnd.heroku+json"

    @JvmField
    val APPLICATION_VND_HHE_LESSON_PLAYER = "application/vnd.hhe.lesson-player"

    @JvmField
    val APPLICATION_VND_HP_HPGL = "application/vnd.hp-HPGL"

    @JvmField
    val APPLICATION_VND_HP_HPID = "application/vnd.hp-hpid"

    @JvmField
    val APPLICATION_VND_HP_HPS = "application/vnd.hp-hps"

    @JvmField
    val APPLICATION_VND_HP_JLYT = "application/vnd.hp-jlyt"

    @JvmField
    val APPLICATION_VND_HP_PCL = "application/vnd.hp-PCL"

    @JvmField
    val APPLICATION_VND_HP_PCLXL = "application/vnd.hp-PCLXL"

    @JvmField
    val APPLICATION_VND_HSL = "application/vnd.hsl"

    @JvmField
    val APPLICATION_VND_HTTPHONE = "application/vnd.httphone"

    @JvmField
    val APPLICATION_VND_HYDROSTATIX_SOF_DATA = "application/vnd.hydrostatix.sof-data"

    @JvmField
    val APPLICATION_VND_HYPERDRIVE_JSON = "application/vnd.hyperdrive+json"

    @JvmField
    val APPLICATION_VND_HYPER_ITEM_JSON = "application/vnd.hyper-item+json"

    @JvmField
    val APPLICATION_VND_HYPER_JSON = "application/vnd.hyper+json"

    @JvmField
    val APPLICATION_VND_HZN_3D_CROSSWORD = "application/vnd.hzn-3d-crossword"

    @JvmField
    val APPLICATION_VND_IBM_ELECTRONIC_MEDIA = "application/vnd.ibm.electronic-media"

    @JvmField
    val APPLICATION_VND_IBM_MINIPAY = "application/vnd.ibm.MiniPay"

    @JvmField
    val APPLICATION_VND_IBM_RIGHTS_MANAGEMENT = "application/vnd.ibm.rights-management"

    @JvmField
    val APPLICATION_VND_IBM_SECURE_CONTAINER = "application/vnd.ibm.secure-container"

    @JvmField
    val APPLICATION_VND_ICCPROFILE = "application/vnd.iccprofile"

    @JvmField
    val APPLICATION_VND_IEEE_1905 = "application/vnd.ieee.1905"

    @JvmField
    val APPLICATION_VND_IGLOADER = "application/vnd.igloader"

    @JvmField
    val APPLICATION_VND_IMAGEMETER_FOLDER_ZIP = "application/vnd.imagemeter.folder+zip"

    @JvmField
    val APPLICATION_VND_IMAGEMETER_IMAGE_ZIP = "application/vnd.imagemeter.image+zip"

    @JvmField
    val APPLICATION_VND_IMMERVISION_IVP = "application/vnd.immervision-ivp"

    @JvmField
    val APPLICATION_VND_IMMERVISION_IVU = "application/vnd.immervision-ivu"

    @JvmField
    val APPLICATION_VND_IMS_IMSCCV1P1 = "application/vnd.ims.imsccv1p1"

    @JvmField
    val APPLICATION_VND_IMS_IMSCCV1P2 = "application/vnd.ims.imsccv1p2"

    @JvmField
    val APPLICATION_VND_IMS_IMSCCV1P3 = "application/vnd.ims.imsccv1p3"

    @JvmField
    val APPLICATION_VND_INFORMEDCONTROL_RMS_XML = "application/vnd.informedcontrol.rms+xml"

    @JvmField
    val APPLICATION_VND_INFOTECH_PROJECT = "application/vnd.infotech.project"

    @JvmField
    val APPLICATION_VND_INFOTECH_PROJECT_XML = "application/vnd.infotech.project+xml"

    @JvmField
    val APPLICATION_VND_INNOPATH_WAMP_NOTIFICATION = "application/vnd.innopath.wamp.notification"

    @JvmField
    val APPLICATION_VND_INSORS_IGM = "application/vnd.insors.igm"

    @JvmField
    val APPLICATION_VND_INTERCON_FORMNET = "application/vnd.intercon.formnet"

    @JvmField
    val APPLICATION_VND_INTERGEO = "application/vnd.intergeo"

    @JvmField
    val APPLICATION_VND_INTERTRUST_DIGIBOX = "application/vnd.intertrust.digibox"

    @JvmField
    val APPLICATION_VND_INTERTRUST_NNCP = "application/vnd.intertrust.nncp"

    @JvmField
    val APPLICATION_VND_INTU_QBO = "application/vnd.intu.qbo"

    @JvmField
    val APPLICATION_VND_INTU_QFX = "application/vnd.intu.qfx"

    @JvmField
    val APPLICATION_VND_IPFS_IPNS_RECORD = "application/vnd.ipfs.ipns-record"

    @JvmField
    val APPLICATION_VND_IPLD_CAR = "application/vnd.ipld.car"

    @JvmField
    val APPLICATION_VND_IPLD_DAG_CBOR = "application/vnd.ipld.dag-cbor"

    @JvmField
    val APPLICATION_VND_IPLD_DAG_JSON = "application/vnd.ipld.dag-json"

    @JvmField
    val APPLICATION_VND_IPLD_RAW = "application/vnd.ipld.raw"

    @JvmField
    val APPLICATION_VND_IPTC_G2_CATALOGITEM_XML = "application/vnd.iptc.g2.catalogitem+xml"

    @JvmField
    val APPLICATION_VND_IPTC_G2_CONCEPTITEM_XML = "application/vnd.iptc.g2.conceptitem+xml"

    @JvmField
    val APPLICATION_VND_IPTC_G2_KNOWLEDGEITEM_XML = "application/vnd.iptc.g2.knowledgeitem+xml"

    @JvmField
    val APPLICATION_VND_IPTC_G2_NEWSITEM_XML = "application/vnd.iptc.g2.newsitem+xml"

    @JvmField
    val APPLICATION_VND_IPTC_G2_NEWSMESSAGE_XML = "application/vnd.iptc.g2.newsmessage+xml"

    @JvmField
    val APPLICATION_VND_IPTC_G2_PACKAGEITEM_XML = "application/vnd.iptc.g2.packageitem+xml"

    @JvmField
    val APPLICATION_VND_IPTC_G2_PLANNINGITEM_XML = "application/vnd.iptc.g2.planningitem+xml"

    @JvmField
    val APPLICATION_VND_IPUNPLUGGED_RCPROFILE = "application/vnd.ipunplugged.rcprofile"

    @JvmField
    val APPLICATION_VND_IREPOSITORY_PACKAGE_XML = "application/vnd.irepository.package+xml"

    @JvmField
    val APPLICATION_VND_ISAC_FCS = "application/vnd.isac.fcs"

    @JvmField
    val APPLICATION_VND_ISO11783_10_ZIP = "application/vnd.iso11783-10+zip"

    @JvmField
    val APPLICATION_VND_IS_XPR = "application/vnd.is-xpr"

    @JvmField
    val APPLICATION_VND_JAM = "application/vnd.jam"

    @JvmField
    val APPLICATION_VND_JAPANNET_DIRECTORY_SERVICE = "application/vnd.japannet-directory-service"

    @JvmField
    val APPLICATION_VND_JAPANNET_JPNSTORE_WAKEUP = "application/vnd.japannet-jpnstore-wakeup"

    @JvmField
    val APPLICATION_VND_JAPANNET_PAYMENT_WAKEUP = "application/vnd.japannet-payment-wakeup"

    @JvmField
    val APPLICATION_VND_JAPANNET_REGISTRATION = "application/vnd.japannet-registration"

    @JvmField
    val APPLICATION_VND_JAPANNET_REGISTRATION_WAKEUP = "application/vnd.japannet-registration-wakeup"

    @JvmField
    val APPLICATION_VND_JAPANNET_SETSTORE_WAKEUP = "application/vnd.japannet-setstore-wakeup"

    @JvmField
    val APPLICATION_VND_JAPANNET_VERIFICATION = "application/vnd.japannet-verification"

    @JvmField
    val APPLICATION_VND_JAPANNET_VERIFICATION_WAKEUP = "application/vnd.japannet-verification-wakeup"

    @JvmField
    val APPLICATION_VND_JCP_JAVAME_MIDLET_RMS = "application/vnd.jcp.javame.midlet-rms"

    @JvmField
    val APPLICATION_VND_JISP = "application/vnd.jisp"

    @JvmField
    val APPLICATION_VND_JOOST_JODA_ARCHIVE = "application/vnd.joost.joda-archive"

    @JvmField
    val APPLICATION_VND_JSK_ISDN_NGN = "application/vnd.jsk.isdn-ngn"

    @JvmField
    val APPLICATION_VND_KAHOOTZ = "application/vnd.kahootz"

    @JvmField
    val APPLICATION_VND_KDE_KARBON = "application/vnd.kde.karbon"

    @JvmField
    val APPLICATION_VND_KDE_KCHART = "application/vnd.kde.kchart"

    @JvmField
    val APPLICATION_VND_KDE_KFORMULA = "application/vnd.kde.kformula"

    @JvmField
    val APPLICATION_VND_KDE_KIVIO = "application/vnd.kde.kivio"

    @JvmField
    val APPLICATION_VND_KDE_KONTOUR = "application/vnd.kde.kontour"

    @JvmField
    val APPLICATION_VND_KDE_KPRESENTER = "application/vnd.kde.kpresenter"

    @JvmField
    val APPLICATION_VND_KDE_KSPREAD = "application/vnd.kde.kspread"

    @JvmField
    val APPLICATION_VND_KDE_KWORD = "application/vnd.kde.kword"

    @JvmField
    val APPLICATION_VND_KENAMEAAPP = "application/vnd.kenameaapp"

    @JvmField
    val APPLICATION_VND_KIDSPIRATION = "application/vnd.kidspiration"

    @JvmField
    val APPLICATION_VND_KINAR = "application/vnd.Kinar"

    @JvmField
    val APPLICATION_VND_KOAN = "application/vnd.koan"

    @JvmField
    val APPLICATION_VND_KODAK_DESCRIPTOR = "application/vnd.kodak-descriptor"

    @JvmField
    val APPLICATION_VND_LAS = "application/vnd.las"

    @JvmField
    val APPLICATION_VND_LASZIP = "application/vnd.laszip"

    @JvmField
    val APPLICATION_VND_LAS_LAS_JSON = "application/vnd.las.las+json"

    @JvmField
    val APPLICATION_VND_LAS_LAS_XML = "application/vnd.las.las+xml"

    @JvmField
    val APPLICATION_VND_LDEV_PRODUCTLICENSING = "application/vnd.ldev.productlicensing"

    @JvmField
    val APPLICATION_VND_LEAP_JSON = "application/vnd.leap+json"

    @JvmField
    val APPLICATION_VND_LIBERTY_REQUEST_XML = "application/vnd.liberty-request+xml"

    @JvmField
    val APPLICATION_VND_LLAMAGRAPHICS_LIFE_BALANCE_DESKTOP = "application/vnd.llamagraphics.life-balance.desktop"

    @JvmField
    val APPLICATION_VND_LLAMAGRAPHICS_LIFE_BALANCE_EXCHANGE_XML = "application/vnd.llamagraphics.life-balance.exchange+xml"

    @JvmField
    val APPLICATION_VND_LOGIPIPE_CIRCUIT_ZIP = "application/vnd.logipipe.circuit+zip"

    @JvmField
    val APPLICATION_VND_LOOM = "application/vnd.loom"

    @JvmField
    val APPLICATION_VND_LOTUS_1_2_3 = "application/vnd.lotus-1-2-3"

    @JvmField
    val APPLICATION_VND_LOTUS_APPROACH = "application/vnd.lotus-approach"

    @JvmField
    val APPLICATION_VND_LOTUS_FREELANCE = "application/vnd.lotus-freelance"

    @JvmField
    val APPLICATION_VND_LOTUS_NOTES = "application/vnd.lotus-notes"

    @JvmField
    val APPLICATION_VND_LOTUS_ORGANIZER = "application/vnd.lotus-organizer"

    @JvmField
    val APPLICATION_VND_LOTUS_SCREENCAM = "application/vnd.lotus-screencam"

    @JvmField
    val APPLICATION_VND_LOTUS_WORDPRO = "application/vnd.lotus-wordpro"

    @JvmField
    val APPLICATION_VND_MACPORTS_PORTPKG = "application/vnd.macports.portpkg"

    @JvmField
    val APPLICATION_VND_MAPBOX_VECTOR_TILE = "application/vnd.mapbox-vector-tile"

    @JvmField
    val APPLICATION_VND_MARLIN_DRM_ACTIONTOKEN_XML = "application/vnd.marlin.drm.actiontoken+xml"

    @JvmField
    val APPLICATION_VND_MARLIN_DRM_CONFTOKEN_XML = "application/vnd.marlin.drm.conftoken+xml"

    @JvmField
    val APPLICATION_VND_MARLIN_DRM_LICENSE_XML = "application/vnd.marlin.drm.license+xml"

    @JvmField
    val APPLICATION_VND_MARLIN_DRM_MDCF = "application/vnd.marlin.drm.mdcf"

    @JvmField
    val APPLICATION_VND_MASON_JSON = "application/vnd.mason+json"

    @JvmField
    val APPLICATION_VND_MAXAR_ARCHIVE_3TZ_ZIP = "application/vnd.maxar.archive.3tz+zip"

    @JvmField
    val APPLICATION_VND_MAXMIND_MAXMIND_DB = "application/vnd.maxmind.maxmind-db"

    @JvmField
    val APPLICATION_VND_MCD = "application/vnd.mcd"

    @JvmField
    val APPLICATION_VND_MDL = "application/vnd.mdl"

    @JvmField
    val APPLICATION_VND_MDL_MBSDF = "application/vnd.mdl-mbsdf"

    @JvmField
    val APPLICATION_VND_MEDCALCDATA = "application/vnd.medcalcdata"

    @JvmField
    val APPLICATION_VND_MEDIASTATION_CDKEY = "application/vnd.mediastation.cdkey"

    @JvmField
    val APPLICATION_VND_MEDICALHOLODECK_RECORDXR = "application/vnd.medicalholodeck.recordxr"

    @JvmField
    val APPLICATION_VND_MERIDIAN_SLINGSHOT = "application/vnd.meridian-slingshot"

    @JvmField
    val APPLICATION_VND_MERMAID = "application/vnd.mermaid"

    @JvmField
    val APPLICATION_VND_MFER = "application/vnd.MFER"

    @JvmField
    val APPLICATION_VND_MFMP = "application/vnd.mfmp"

    @JvmField
    val APPLICATION_VND_MICROGRAFX_FLO = "application/vnd.micrografx.flo"

    @JvmField
    val APPLICATION_VND_MICROGRAFX_IGX = "application/vnd.micrografx.igx"

    @JvmField
    val APPLICATION_VND_MICROSOFT_PORTABLE_EXECUTABLE = "application/vnd.microsoft.portable-executable"

    @JvmField
    val APPLICATION_VND_MICROSOFT_WINDOWS_THUMBNAIL_CACHE = "application/vnd.microsoft.windows.thumbnail-cache"

    @JvmField
    val APPLICATION_VND_MICRO_JSON = "application/vnd.micro+json"

    @JvmField
    val APPLICATION_VND_MIELE_JSON = "application/vnd.miele+json"

    @JvmField
    val APPLICATION_VND_MIF = "application/vnd.mif"

    @JvmField
    val APPLICATION_VND_MINISOFT_HP3000_SAVE = "application/vnd.minisoft-hp3000-save"

    @JvmField
    val APPLICATION_VND_MITSUBISHI_MISTY_GUARD_TRUSTWEB = "application/vnd.mitsubishi.misty-guard.trustweb"

    @JvmField
    val APPLICATION_VND_MOBIUS_DAF = "application/vnd.Mobius.DAF"

    @JvmField
    val APPLICATION_VND_MOBIUS_DIS = "application/vnd.Mobius.DIS"

    @JvmField
    val APPLICATION_VND_MOBIUS_MBK = "application/vnd.Mobius.MBK"

    @JvmField
    val APPLICATION_VND_MOBIUS_MQY = "application/vnd.Mobius.MQY"

    @JvmField
    val APPLICATION_VND_MOBIUS_MSL = "application/vnd.Mobius.MSL"

    @JvmField
    val APPLICATION_VND_MOBIUS_PLC = "application/vnd.Mobius.PLC"

    @JvmField
    val APPLICATION_VND_MOBIUS_TXF = "application/vnd.Mobius.TXF"

    @JvmField
    val APPLICATION_VND_MODL = "application/vnd.modl"

    @JvmField
    val APPLICATION_VND_MOPHUN_APPLICATION = "application/vnd.mophun.application"

    @JvmField
    val APPLICATION_VND_MOPHUN_CERTIFICATE = "application/vnd.mophun.certificate"

    @JvmField
    val APPLICATION_VND_MOTOROLA_FLEXSUITE = "application/vnd.motorola.flexsuite"

    @JvmField
    val APPLICATION_VND_MOTOROLA_FLEXSUITE_ADSI = "application/vnd.motorola.flexsuite.adsi"

    @JvmField
    val APPLICATION_VND_MOTOROLA_FLEXSUITE_FIS = "application/vnd.motorola.flexsuite.fis"

    @JvmField
    val APPLICATION_VND_MOTOROLA_FLEXSUITE_GOTAP = "application/vnd.motorola.flexsuite.gotap"

    @JvmField
    val APPLICATION_VND_MOTOROLA_FLEXSUITE_KMR = "application/vnd.motorola.flexsuite.kmr"

    @JvmField
    val APPLICATION_VND_MOTOROLA_FLEXSUITE_TTC = "application/vnd.motorola.flexsuite.ttc"

    @JvmField
    val APPLICATION_VND_MOTOROLA_FLEXSUITE_WEM = "application/vnd.motorola.flexsuite.wem"

    @JvmField
    val APPLICATION_VND_MOTOROLA_IPRM = "application/vnd.motorola.iprm"

    @JvmField
    val APPLICATION_VND_MOZILLA_XUL_XML = "application/vnd.mozilla.xul+xml"

    @JvmField
    val APPLICATION_VND_MSA_DISK_IMAGE = "application/vnd.msa-disk-image"

    @JvmField
    val APPLICATION_VND_MSEQ = "application/vnd.mseq"

    @JvmField
    val APPLICATION_VND_MSIGN = "application/vnd.msign"

    @JvmField
    val APPLICATION_VND_MS_3MFDOCUMENT = "application/vnd.ms-3mfdocument"

    @JvmField
    val APPLICATION_VND_MS_ARTGALRY = "application/vnd.ms-artgalry"

    @JvmField
    val APPLICATION_VND_MS_ASF = "application/vnd.ms-asf"

    @JvmField
    val APPLICATION_VND_MS_CAB_COMPRESSED = "application/vnd.ms-cab-compressed"

    @JvmField
    val APPLICATION_VND_MS_EXCEL_ADDIN_MACROENABLED_12 = "application/vnd.ms-excel.addin.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_EXCEL_SHEET_BINARY_MACROENABLED_12 = "application/vnd.ms-excel.sheet.binary.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_EXCEL_SHEET_MACROENABLED_12 = "application/vnd.ms-excel.sheet.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_EXCEL_TEMPLATE_MACROENABLED_12 = "application/vnd.ms-excel.template.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_FONTOBJECT = "application/vnd.ms-fontobject"

    @JvmField
    val APPLICATION_VND_MS_HTMLHELP = "application/vnd.ms-htmlhelp"

    @JvmField
    val APPLICATION_VND_MS_IMS = "application/vnd.ms-ims"

    @JvmField
    val APPLICATION_VND_MS_LRM = "application/vnd.ms-lrm"

    @JvmField
    val APPLICATION_VND_MS_OFFICETHEME = "application/vnd.ms-officetheme"

    @JvmField
    val APPLICATION_VND_MS_OFFICE_ACTIVEX_XML = "application/vnd.ms-office.activeX+xml"

    @JvmField
    val APPLICATION_VND_MS_PLAYREADY_INITIATOR_XML = "application/vnd.ms-playready.initiator+xml"

    @JvmField
    val APPLICATION_VND_MS_POWERPOINT_ADDIN_MACROENABLED_12 = "application/vnd.ms-powerpoint.addin.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_POWERPOINT_PRESENTATION_MACROENABLED_12 = "application/vnd.ms-powerpoint.presentation.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_POWERPOINT_SLIDESHOW_MACROENABLED_12 = "application/vnd.ms-powerpoint.slideshow.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_POWERPOINT_SLIDE_MACROENABLED_12 = "application/vnd.ms-powerpoint.slide.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_POWERPOINT_TEMPLATE_MACROENABLED_12 = "application/vnd.ms-powerpoint.template.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_PRINTDEVICECAPABILITIES_XML = "application/vnd.ms-PrintDeviceCapabilities+xml"

    @JvmField
    val APPLICATION_VND_MS_PRINTSCHEMATICKET_XML = "application/vnd.ms-PrintSchemaTicket+xml"

    @JvmField
    val APPLICATION_VND_MS_PROJECT = "application/vnd.ms-project"

    @JvmField
    val APPLICATION_VND_MS_TNEF = "application/vnd.ms-tnef"

    @JvmField
    val APPLICATION_VND_MS_WINDOWS_DEVICEPAIRING = "application/vnd.ms-windows.devicepairing"

    @JvmField
    val APPLICATION_VND_MS_WINDOWS_NWPRINTING_OOB = "application/vnd.ms-windows.nwprinting.oob"

    @JvmField
    val APPLICATION_VND_MS_WINDOWS_PRINTERPAIRING = "application/vnd.ms-windows.printerpairing"

    @JvmField
    val APPLICATION_VND_MS_WINDOWS_WSD_OOB = "application/vnd.ms-windows.wsd.oob"

    @JvmField
    val APPLICATION_VND_MS_WMDRM_LIC_CHLG_REQ = "application/vnd.ms-wmdrm.lic-chlg-req"

    @JvmField
    val APPLICATION_VND_MS_WMDRM_LIC_RESP = "application/vnd.ms-wmdrm.lic-resp"

    @JvmField
    val APPLICATION_VND_MS_WMDRM_METER_CHLG_REQ = "application/vnd.ms-wmdrm.meter-chlg-req"

    @JvmField
    val APPLICATION_VND_MS_WMDRM_METER_RESP = "application/vnd.ms-wmdrm.meter-resp"

    @JvmField
    val APPLICATION_VND_MS_WORD_DOCUMENT_MACROENABLED_12 = "application/vnd.ms-word.document.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_WORD_TEMPLATE_MACROENABLED_12 = "application/vnd.ms-word.template.macroEnabled.12"

    @JvmField
    val APPLICATION_VND_MS_WORKS = "application/vnd.ms-works"

    @JvmField
    val APPLICATION_VND_MS_WPL = "application/vnd.ms-wpl"

    @JvmField
    val APPLICATION_VND_MS_XPSDOCUMENT = "application/vnd.ms-xpsdocument"

    @JvmField
    val APPLICATION_VND_MULTIAD_CREATOR = "application/vnd.multiad.creator"

    @JvmField
    val APPLICATION_VND_MULTIAD_CREATOR_CIF = "application/vnd.multiad.creator.cif"

    @JvmField
    val APPLICATION_VND_MUSICIAN = "application/vnd.musician"

    @JvmField
    val APPLICATION_VND_MUSIC_NIFF = "application/vnd.music-niff"

    @JvmField
    val APPLICATION_VND_MUVEE_STYLE = "application/vnd.muvee.style"

    @JvmField
    val APPLICATION_VND_MYNFC = "application/vnd.mynfc"

    @JvmField
    val APPLICATION_VND_NACAMAR_YBRID_JSON = "application/vnd.nacamar.ybrid+json"

    @JvmField
    val APPLICATION_VND_NATO_BINDINGDATAOBJECT_CBOR = "application/vnd.nato.bindingdataobject+cbor"

    @JvmField
    val APPLICATION_VND_NATO_BINDINGDATAOBJECT_JSON = "application/vnd.nato.bindingdataobject+json"

    @JvmField
    val APPLICATION_VND_NATO_BINDINGDATAOBJECT_XML = "application/vnd.nato.bindingdataobject+xml"

    @JvmField
    val APPLICATION_VND_NATO_OPENXMLFORMATS_PACKAGE_IEPD_ZIP = "application/vnd.nato.openxmlformats-package.iepd+zip"

    @JvmField
    val APPLICATION_VND_NCD_CONTROL = "application/vnd.ncd.control"

    @JvmField
    val APPLICATION_VND_NCD_REFERENCE = "application/vnd.ncd.reference"

    @JvmField
    val APPLICATION_VND_NEARST_INV_JSON = "application/vnd.nearst.inv+json"

    @JvmField
    val APPLICATION_VND_NEBUMIND_LINE = "application/vnd.nebumind.line"

    @JvmField
    val APPLICATION_VND_NERVANA = "application/vnd.nervana"

    @JvmField
    val APPLICATION_VND_NETFPX = "application/vnd.netfpx"

    @JvmField
    val APPLICATION_VND_NEUROLANGUAGE_NLU = "application/vnd.neurolanguage.nlu"

    @JvmField
    val APPLICATION_VND_NIMN = "application/vnd.nimn"

    @JvmField
    val APPLICATION_VND_NINTENDO_NITRO_ROM = "application/vnd.nintendo.nitro.rom"

    @JvmField
    val APPLICATION_VND_NINTENDO_SNES_ROM = "application/vnd.nintendo.snes.rom"

    @JvmField
    val APPLICATION_VND_NITF = "application/vnd.nitf"

    @JvmField
    val APPLICATION_VND_NOBLENET_DIRECTORY = "application/vnd.noblenet-directory"

    @JvmField
    val APPLICATION_VND_NOBLENET_SEALER = "application/vnd.noblenet-sealer"

    @JvmField
    val APPLICATION_VND_NOBLENET_WEB = "application/vnd.noblenet-web"

    @JvmField
    val APPLICATION_VND_NOKIA_CATALOGS = "application/vnd.nokia.catalogs"

    @JvmField
    val APPLICATION_VND_NOKIA_CONML_WBXML = "application/vnd.nokia.conml+wbxml"

    @JvmField
    val APPLICATION_VND_NOKIA_CONML_XML = "application/vnd.nokia.conml+xml"

    @JvmField
    val APPLICATION_VND_NOKIA_IPTV_CONFIG_XML = "application/vnd.nokia.iptv.config+xml"

    @JvmField
    val APPLICATION_VND_NOKIA_ISDS_RADIO_PRESETS = "application/vnd.nokia.iSDS-radio-presets"

    @JvmField
    val APPLICATION_VND_NOKIA_LANDMARKCOLLECTION_XML = "application/vnd.nokia.landmarkcollection+xml"

    @JvmField
    val APPLICATION_VND_NOKIA_LANDMARK_WBXML = "application/vnd.nokia.landmark+wbxml"

    @JvmField
    val APPLICATION_VND_NOKIA_LANDMARK_XML = "application/vnd.nokia.landmark+xml"

    @JvmField
    val APPLICATION_VND_NOKIA_NCD = "application/vnd.nokia.ncd"

    @JvmField
    val APPLICATION_VND_NOKIA_N_GAGE_AC_XML = "application/vnd.nokia.n-gage.ac+xml"

    @JvmField
    val APPLICATION_VND_NOKIA_N_GAGE_DATA = "application/vnd.nokia.n-gage.data"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("NULL"), DeprecationLevel.ERROR)
    val APPLICATION_VND_NOKIA_N_GAGE_SYMBIAN_INSTALL = "application/vnd.nokia.n-gage.symbian.install"

    @JvmField
    val APPLICATION_VND_NOKIA_PCD_WBXML = "application/vnd.nokia.pcd+wbxml"

    @JvmField
    val APPLICATION_VND_NOKIA_PCD_XML = "application/vnd.nokia.pcd+xml"

    @JvmField
    val APPLICATION_VND_NOKIA_RADIO_PRESET = "application/vnd.nokia.radio-preset"

    @JvmField
    val APPLICATION_VND_NOKIA_RADIO_PRESETS = "application/vnd.nokia.radio-presets"

    @JvmField
    val APPLICATION_VND_NOVADIGM_EDM = "application/vnd.novadigm.EDM"

    @JvmField
    val APPLICATION_VND_NOVADIGM_EDX = "application/vnd.novadigm.EDX"

    @JvmField
    val APPLICATION_VND_NOVADIGM_EXT = "application/vnd.novadigm.EXT"

    @JvmField
    val APPLICATION_VND_NTT_LOCAL_CONTENT_SHARE = "application/vnd.ntt-local.content-share"

    @JvmField
    val APPLICATION_VND_NTT_LOCAL_FILE_TRANSFER = "application/vnd.ntt-local.file-transfer"

    @JvmField
    val APPLICATION_VND_NTT_LOCAL_OGW_REMOTE_ACCESS = "application/vnd.ntt-local.ogw_remote-access"

    @JvmField
    val APPLICATION_VND_NTT_LOCAL_SIP_TA_REMOTE = "application/vnd.ntt-local.sip-ta_remote"

    @JvmField
    val APPLICATION_VND_NTT_LOCAL_SIP_TA_TCP_STREAM = "application/vnd.ntt-local.sip-ta_tcp_stream"

    @JvmField
    val APPLICATION_VND_OAI_WORKFLOWS = "application/vnd.oai.workflows"

    @JvmField
    val APPLICATION_VND_OAI_WORKFLOWS_JSON = "application/vnd.oai.workflows+json"

    @JvmField
    val APPLICATION_VND_OAI_WORKFLOWS_YAML = "application/vnd.oai.workflows+yaml"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_BASE = "application/vnd.oasis.opendocument.base"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_CHART = "application/vnd.oasis.opendocument.chart"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_CHART_TEMPLATE = "application/vnd.oasis.opendocument.chart-template"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("APPLICATION_VND_OASIS_OPENDOCUMENT_BASE"), DeprecationLevel.ERROR)
    val APPLICATION_VND_OASIS_OPENDOCUMENT_DATABASE = "application/vnd.oasis.opendocument.database"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_FORMULA = "application/vnd.oasis.opendocument.formula"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_FORMULA_TEMPLATE = "application/vnd.oasis.opendocument.formula-template"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_GRAPHICS = "application/vnd.oasis.opendocument.graphics"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_GRAPHICS_TEMPLATE = "application/vnd.oasis.opendocument.graphics-template"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_IMAGE = "application/vnd.oasis.opendocument.image"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_IMAGE_TEMPLATE = "application/vnd.oasis.opendocument.image-template"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_PRESENTATION_TEMPLATE = "application/vnd.oasis.opendocument.presentation-template"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_SPREADSHEET_TEMPLATE = "application/vnd.oasis.opendocument.spreadsheet-template"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_MASTER = "application/vnd.oasis.opendocument.text-master"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_MASTER_TEMPLATE = "application/vnd.oasis.opendocument.text-master-template"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_TEMPLATE = "application/vnd.oasis.opendocument.text-template"

    @JvmField
    val APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_WEB = "application/vnd.oasis.opendocument.text-web"

    @JvmField
    val APPLICATION_VND_OBN = "application/vnd.obn"

    @JvmField
    val APPLICATION_VND_OCF_CBOR = "application/vnd.ocf+cbor"

    @JvmField
    val APPLICATION_VND_OCI_IMAGE_MANIFEST_V1_JSON = "application/vnd.oci.image.manifest.v1+json"

    @JvmField
    val APPLICATION_VND_OFTN_L10N_JSON = "application/vnd.oftn.l10n+json"

    @JvmField
    val APPLICATION_VND_OIPF_CONTENTACCESSDOWNLOAD_XML = "application/vnd.oipf.contentaccessdownload+xml"

    @JvmField
    val APPLICATION_VND_OIPF_CONTENTACCESSSTREAMING_XML = "application/vnd.oipf.contentaccessstreaming+xml"

    @JvmField
    val APPLICATION_VND_OIPF_CSPG_HEXBINARY = "application/vnd.oipf.cspg-hexbinary"

    @JvmField
    val APPLICATION_VND_OIPF_DAE_SVG_XML = "application/vnd.oipf.dae.svg+xml"

    @JvmField
    val APPLICATION_VND_OIPF_DAE_XHTML_XML = "application/vnd.oipf.dae.xhtml+xml"

    @JvmField
    val APPLICATION_VND_OIPF_MIPPVCONTROLMESSAGE_XML = "application/vnd.oipf.mippvcontrolmessage+xml"

    @JvmField
    val APPLICATION_VND_OIPF_PAE_GEM = "application/vnd.oipf.pae.gem"

    @JvmField
    val APPLICATION_VND_OIPF_SPDISCOVERY_XML = "application/vnd.oipf.spdiscovery+xml"

    @JvmField
    val APPLICATION_VND_OIPF_SPDLIST_XML = "application/vnd.oipf.spdlist+xml"

    @JvmField
    val APPLICATION_VND_OIPF_UEPROFILE_XML = "application/vnd.oipf.ueprofile+xml"

    @JvmField
    val APPLICATION_VND_OIPF_USERPROFILE_XML = "application/vnd.oipf.userprofile+xml"

    @JvmField
    val APPLICATION_VND_OLPC_SUGAR = "application/vnd.olpc-sugar"

    @JvmField
    val APPLICATION_VND_OMADS_EMAIL_XML = "application/vnd.omads-email+xml"

    @JvmField
    val APPLICATION_VND_OMADS_FILE_XML = "application/vnd.omads-file+xml"

    @JvmField
    val APPLICATION_VND_OMADS_FOLDER_XML = "application/vnd.omads-folder+xml"

    @JvmField
    val APPLICATION_VND_OMALOC_SUPL_INIT = "application/vnd.omaloc-supl-init"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_ASSOCIATED_PROCEDURE_PARAMETER_XML = "application/vnd.oma.bcast.associated-procedure-parameter+xml"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_DRM_TRIGGER_XML = "application/vnd.oma.bcast.drm-trigger+xml"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_IMD_XML = "application/vnd.oma.bcast.imd+xml"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_LTKM = "application/vnd.oma.bcast.ltkm"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_NOTIFICATION_XML = "application/vnd.oma.bcast.notification+xml"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_PROVISIONINGTRIGGER = "application/vnd.oma.bcast.provisioningtrigger"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_SGBOOT = "application/vnd.oma.bcast.sgboot"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_SGDD_XML = "application/vnd.oma.bcast.sgdd+xml"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_SGDU = "application/vnd.oma.bcast.sgdu"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_SIMPLE_SYMBOL_CONTAINER = "application/vnd.oma.bcast.simple-symbol-container"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_SMARTCARD_TRIGGER_XML = "application/vnd.oma.bcast.smartcard-trigger+xml"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_SPROV_XML = "application/vnd.oma.bcast.sprov+xml"

    @JvmField
    val APPLICATION_VND_OMA_BCAST_STKM = "application/vnd.oma.bcast.stkm"

    @JvmField
    val APPLICATION_VND_OMA_CAB_ADDRESS_BOOK_XML = "application/vnd.oma.cab-address-book+xml"

    @JvmField
    val APPLICATION_VND_OMA_CAB_FEATURE_HANDLER_XML = "application/vnd.oma.cab-feature-handler+xml"

    @JvmField
    val APPLICATION_VND_OMA_CAB_PCC_XML = "application/vnd.oma.cab-pcc+xml"

    @JvmField
    val APPLICATION_VND_OMA_CAB_SUBS_INVITE_XML = "application/vnd.oma.cab-subs-invite+xml"

    @JvmField
    val APPLICATION_VND_OMA_CAB_USER_PREFS_XML = "application/vnd.oma.cab-user-prefs+xml"

    @JvmField
    val APPLICATION_VND_OMA_DCD = "application/vnd.oma.dcd"

    @JvmField
    val APPLICATION_VND_OMA_DCDC = "application/vnd.oma.dcdc"

    @JvmField
    val APPLICATION_VND_OMA_DD2_XML = "application/vnd.oma.dd2+xml"

    @JvmField
    val APPLICATION_VND_OMA_DRM_RISD_XML = "application/vnd.oma.drm.risd+xml"

    @JvmField
    val APPLICATION_VND_OMA_GROUP_USAGE_LIST_XML = "application/vnd.oma.group-usage-list+xml"

    @JvmField
    val APPLICATION_VND_OMA_LWM2M_CBOR = "application/vnd.oma.lwm2m+cbor"

    @JvmField
    val APPLICATION_VND_OMA_LWM2M_JSON = "application/vnd.oma.lwm2m+json"

    @JvmField
    val APPLICATION_VND_OMA_LWM2M_TLV = "application/vnd.oma.lwm2m+tlv"

    @JvmField
    val APPLICATION_VND_OMA_PAL_XML = "application/vnd.oma.pal+xml"

    @JvmField
    val APPLICATION_VND_OMA_POC_DETAILED_PROGRESS_REPORT_XML = "application/vnd.oma.poc.detailed-progress-report+xml"

    @JvmField
    val APPLICATION_VND_OMA_POC_FINAL_REPORT_XML = "application/vnd.oma.poc.final-report+xml"

    @JvmField
    val APPLICATION_VND_OMA_POC_GROUPS_XML = "application/vnd.oma.poc.groups+xml"

    @JvmField
    val APPLICATION_VND_OMA_POC_INVOCATION_DESCRIPTOR_XML = "application/vnd.oma.poc.invocation-descriptor+xml"

    @JvmField
    val APPLICATION_VND_OMA_POC_OPTIMIZED_PROGRESS_REPORT_XML = "application/vnd.oma.poc.optimized-progress-report+xml"

    @JvmField
    val APPLICATION_VND_OMA_PUSH = "application/vnd.oma.push"

    @JvmField
    val APPLICATION_VND_OMA_SCIDM_MESSAGES_XML = "application/vnd.oma.scidm.messages+xml"

    @JvmField
    val APPLICATION_VND_OMA_SCWS_CONFIG = "application/vnd.oma-scws-config"

    @JvmField
    val APPLICATION_VND_OMA_SCWS_HTTP_REQUEST = "application/vnd.oma-scws-http-request"

    @JvmField
    val APPLICATION_VND_OMA_SCWS_HTTP_RESPONSE = "application/vnd.oma-scws-http-response"

    @JvmField
    val APPLICATION_VND_OMA_XCAP_DIRECTORY_XML = "application/vnd.oma.xcap-directory+xml"

    @JvmField
    val APPLICATION_VND_ONEPAGER = "application/vnd.onepager"

    @JvmField
    val APPLICATION_VND_ONEPAGERTAMP = "application/vnd.onepagertamp"

    @JvmField
    val APPLICATION_VND_ONEPAGERTAMX = "application/vnd.onepagertamx"

    @JvmField
    val APPLICATION_VND_ONEPAGERTAT = "application/vnd.onepagertat"

    @JvmField
    val APPLICATION_VND_ONEPAGERTATP = "application/vnd.onepagertatp"

    @JvmField
    val APPLICATION_VND_ONEPAGERTATX = "application/vnd.onepagertatx"

    @JvmField
    val APPLICATION_VND_ONVIF_METADATA = "application/vnd.onvif.metadata"

    @JvmField
    val APPLICATION_VND_OPENBLOX_GAME_BINARY = "application/vnd.openblox.game-binary"

    @JvmField
    val APPLICATION_VND_OPENBLOX_GAME_XML = "application/vnd.openblox.game+xml"

    @JvmField
    val APPLICATION_VND_OPENEYE_OEB = "application/vnd.openeye.oeb"

    @JvmField
    val APPLICATION_VND_OPENSTREETMAP_DATA_XML = "application/vnd.openstreetmap.data+xml"

    @JvmField
    val APPLICATION_VND_OPENTIMESTAMPS_OTS = "application/vnd.opentimestamps.ots"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_CUSTOMXMLPROPERTIES_XML = "application/vnd.openxmlformats-officedocument.customXmlProperties+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_CUSTOM_PROPERTIES_XML = "application/vnd.openxmlformats-officedocument.custom-properties+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_CHARTSHAPES_XML = "application/vnd.openxmlformats-officedocument.drawingml.chartshapes+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_CHART_XML = "application/vnd.openxmlformats-officedocument.drawingml.chart+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMCOLORS_XML = "application/vnd.openxmlformats-officedocument.drawingml.diagramColors+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMDATA_XML = "application/vnd.openxmlformats-officedocument.drawingml.diagramData+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMLAYOUT_XML = "application/vnd.openxmlformats-officedocument.drawingml.diagramLayout+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMSTYLE_XML = "application/vnd.openxmlformats-officedocument.drawingml.diagramStyle+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWING_XML = "application/vnd.openxmlformats-officedocument.drawing+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_EXTENDED_PROPERTIES_XML = "application/vnd.openxmlformats-officedocument.extended-properties+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_COMMENTAUTHORS_XML = "application/vnd.openxmlformats-officedocument.presentationml.commentAuthors+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_COMMENTS_XML = "application/vnd.openxmlformats-officedocument.presentationml.comments+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_HANDOUTMASTER_XML = "application/vnd.openxmlformats-officedocument.presentationml.handoutMaster+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_NOTESMASTER_XML = "application/vnd.openxmlformats-officedocument.presentationml.notesMaster+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_NOTESSLIDE_XML = "application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION_MAIN_XML = "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESPROPS_XML = "application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.slide"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDELAYOUT_XML = "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDEMASTER_XML = "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDESHOW = "application/vnd.openxmlformats-officedocument.presentationml.slideshow"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDESHOW_MAIN_XML = "application/vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDEUPDATEINFO_XML = "application/vnd.openxmlformats-officedocument.presentationml.slideUpdateInfo+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDE_XML = "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TABLESTYLES_XML = "application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TAGS_XML = "application/vnd.openxmlformats-officedocument.presentationml.tags+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TEMPLATE = "application/vnd.openxmlformats-officedocument.presentationml.template"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TEMPLATE_MAIN_XML = "application/vnd.openxmlformats-officedocument.presentationml.template.main+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_VIEWPROPS_XML = "application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_CALCCHAIN_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.calcChain+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_CHARTSHEET_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_COMMENTS_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.comments+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_CONNECTIONS_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.connections+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_DIALOGSHEET_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_EXTERNALLINK_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.externalLink+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_PIVOTCACHEDEFINITION_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.pivotCacheDefinition+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_PIVOTCACHERECORDS_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.pivotCacheRecords+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_PIVOTTABLE_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.pivotTable+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_QUERYTABLE_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.queryTable+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_REVISIONHEADERS_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.revisionHeaders+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_REVISIONLOG_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.revisionLog+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHAREDSTRINGS_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEETMETADATA_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheetMetadata+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET_MAIN_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_STYLES_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TABLESINGLECELLS_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.tableSingleCells+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TABLE_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TEMPLATE = "application/vnd.openxmlformats-officedocument.spreadsheetml.template"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TEMPLATE_MAIN_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_USERNAMES_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.userNames+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_VOLATILEDEPENDENCIES_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.volatileDependencies+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_WORKSHEET_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_THEMEOVERRIDE_XML = "application/vnd.openxmlformats-officedocument.themeOverride+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_THEME_XML = "application/vnd.openxmlformats-officedocument.theme+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_VMLDRAWING = "application/vnd.openxmlformats-officedocument.vmlDrawing"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_COMMENTS_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT_GLOSSARY_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT_MAIN_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_ENDNOTES_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_FONTTABLE_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_FOOTER_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_FOOTNOTES_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_NUMBERING_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_SETTINGS_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_STYLES_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_TEMPLATE = "application/vnd.openxmlformats-officedocument.wordprocessingml.template"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_TEMPLATE_MAIN_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_WEBSETTINGS_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.webSettings+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_PACKAGE_CORE_PROPERTIES_XML = "application/vnd.openxmlformats-package.core-properties+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_PACKAGE_DIGITAL_SIGNATURE_XMLSIGNATURE_XML = "application/vnd.openxmlformats-package.digital-signature-xmlsignature+xml"

    @JvmField
    val APPLICATION_VND_OPENXMLFORMATS_PACKAGE_RELATIONSHIPS_XML = "application/vnd.openxmlformats-package.relationships+xml"

    @JvmField
    val APPLICATION_VND_ORACLE_RESOURCE_JSON = "application/vnd.oracle.resource+json"

    @JvmField
    val APPLICATION_VND_ORANGE_INDATA = "application/vnd.orange.indata"

    @JvmField
    val APPLICATION_VND_OSA_NETDEPLOY = "application/vnd.osa.netdeploy"

    @JvmField
    val APPLICATION_VND_OSGEO_MAPGUIDE_PACKAGE = "application/vnd.osgeo.mapguide.package"

    @JvmField
    val APPLICATION_VND_OSGI_BUNDLE = "application/vnd.osgi.bundle"

    @JvmField
    val APPLICATION_VND_OSGI_DP = "application/vnd.osgi.dp"

    @JvmField
    val APPLICATION_VND_OSGI_SUBSYSTEM = "application/vnd.osgi.subsystem"

    @JvmField
    val APPLICATION_VND_OTPS_CT_KIP_XML = "application/vnd.otps.ct-kip+xml"

    @JvmField
    val APPLICATION_VND_OXLI_COUNTGRAPH = "application/vnd.oxli.countgraph"

    @JvmField
    val APPLICATION_VND_PAGERDUTY_JSON = "application/vnd.pagerduty+json"

    @JvmField
    val APPLICATION_VND_PALM = "application/vnd.palm"

    @JvmField
    val APPLICATION_VND_PANOPLY = "application/vnd.panoply"

    @JvmField
    val APPLICATION_VND_PAOS_XML = "application/vnd.paos.xml"

    @JvmField
    val APPLICATION_VND_PATENTDIVE = "application/vnd.patentdive"

    @JvmField
    val APPLICATION_VND_PATIENTECOMMSDOC = "application/vnd.patientecommsdoc"

    @JvmField
    val APPLICATION_VND_PAWAAFILE = "application/vnd.pawaafile"

    @JvmField
    val APPLICATION_VND_PCOS = "application/vnd.pcos"

    @JvmField
    val APPLICATION_VND_PG_FORMAT = "application/vnd.pg.format"

    @JvmField
    val APPLICATION_VND_PG_OSASLI = "application/vnd.pg.osasli"

    @JvmField
    val APPLICATION_VND_PIACCESS_APPLICATION_LICENCE = "application/vnd.piaccess.application-licence"

    @JvmField
    val APPLICATION_VND_PICSEL = "application/vnd.picsel"

    @JvmField
    val APPLICATION_VND_PMI_WIDGET = "application/vnd.pmi.widget"

    @JvmField
    val APPLICATION_VND_POCKETLEARN = "application/vnd.pocketlearn"

    @JvmField
    val APPLICATION_VND_POC_GROUP_ADVERTISEMENT_XML = "application/vnd.poc.group-advertisement+xml"

    @JvmField
    val APPLICATION_VND_POWERBUILDER6 = "application/vnd.powerbuilder6"

    @JvmField
    val APPLICATION_VND_POWERBUILDER6_S = "application/vnd.powerbuilder6-s"

    @JvmField
    val APPLICATION_VND_POWERBUILDER7 = "application/vnd.powerbuilder7"

    @JvmField
    val APPLICATION_VND_POWERBUILDER75 = "application/vnd.powerbuilder75"

    @JvmField
    val APPLICATION_VND_POWERBUILDER75_S = "application/vnd.powerbuilder75-s"

    @JvmField
    val APPLICATION_VND_POWERBUILDER7_S = "application/vnd.powerbuilder7-s"

    @JvmField
    val APPLICATION_VND_PREMINET = "application/vnd.preminet"

    @JvmField
    val APPLICATION_VND_PREVIEWSYSTEMS_BOX = "application/vnd.previewsystems.box"

    @JvmField
    val APPLICATION_VND_PROTEUS_MAGAZINE = "application/vnd.proteus.magazine"

    @JvmField
    val APPLICATION_VND_PSFS = "application/vnd.psfs"

    @JvmField
    val APPLICATION_VND_PT_MUNDUSMUNDI = "application/vnd.pt.mundusmundi"

    @JvmField
    val APPLICATION_VND_PUBLISHARE_DELTA_TREE = "application/vnd.publishare-delta-tree"

    @JvmField
    val APPLICATION_VND_PVI_PTID1 = "application/vnd.pvi.ptid1"

    @JvmField
    val APPLICATION_VND_PWG_MULTIPLEXED = "application/vnd.pwg-multiplexed"

    @JvmField
    val APPLICATION_VND_PWG_XHTML_PRINT_XML = "application/vnd.pwg-xhtml-print+xml"

    @JvmField
    val APPLICATION_VND_QUALCOMM_BREW_APP_RES = "application/vnd.qualcomm.brew-app-res"

    @JvmField
    val APPLICATION_VND_QUARANTAINENET = "application/vnd.quarantainenet"

    @JvmField
    val APPLICATION_VND_QUARK_QUARKXPRESS = "application/vnd.Quark.QuarkXPress"

    @JvmField
    val APPLICATION_VND_QUOBJECT_QUOXDOCUMENT = "application/vnd.quobject-quoxdocument"

    @JvmField
    val APPLICATION_VND_RADISYS_MOML_XML = "application/vnd.radisys.moml+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_AUDIT_CONF_XML = "application/vnd.radisys.msml-audit-conf+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_AUDIT_CONN_XML = "application/vnd.radisys.msml-audit-conn+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_AUDIT_DIALOG_XML = "application/vnd.radisys.msml-audit-dialog+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_AUDIT_STREAM_XML = "application/vnd.radisys.msml-audit-stream+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_AUDIT_XML = "application/vnd.radisys.msml-audit+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_CONF_XML = "application/vnd.radisys.msml-conf+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_DIALOG_BASE_XML = "application/vnd.radisys.msml-dialog-base+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_DIALOG_FAX_DETECT_XML = "application/vnd.radisys.msml-dialog-fax-detect+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_DIALOG_FAX_SENDRECV_XML = "application/vnd.radisys.msml-dialog-fax-sendrecv+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_DIALOG_GROUP_XML = "application/vnd.radisys.msml-dialog-group+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_DIALOG_SPEECH_XML = "application/vnd.radisys.msml-dialog-speech+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_DIALOG_TRANSFORM_XML = "application/vnd.radisys.msml-dialog-transform+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_DIALOG_XML = "application/vnd.radisys.msml-dialog+xml"

    @JvmField
    val APPLICATION_VND_RADISYS_MSML_XML = "application/vnd.radisys.msml+xml"

    @JvmField
    val APPLICATION_VND_RAINSTOR_DATA = "application/vnd.rainstor.data"

    @JvmField
    val APPLICATION_VND_RAPID = "application/vnd.rapid"

    @JvmField
    val APPLICATION_VND_REALVNC_BED = "application/vnd.realvnc.bed"

    @JvmField
    val APPLICATION_VND_RECORDARE_MUSICXML = "application/vnd.recordare.musicxml"

    @JvmField
    val APPLICATION_VND_RECORDARE_MUSICXML_XML = "application/vnd.recordare.musicxml+xml"

    @JvmField
    val APPLICATION_VND_RELPIPE = "application/vnd.relpipe"

    @JvmField
    val APPLICATION_VND_RENLEARN_RLPRINT = "application/vnd.RenLearn.rlprint"

    @JvmField
    val APPLICATION_VND_RESILIENT_LOGIC = "application/vnd.resilient.logic"

    @JvmField
    val APPLICATION_VND_RESTFUL_JSON = "application/vnd.restful+json"

    @JvmField
    val APPLICATION_VND_RIG_CRYPTONOTE = "application/vnd.rig.cryptonote"

    @JvmField
    val APPLICATION_VND_ROUTE66_LINK66_XML = "application/vnd.route66.link66+xml"

    @JvmField
    val APPLICATION_VND_RS_274X = "application/vnd.rs-274x"

    @JvmField
    val APPLICATION_VND_RUCKUS_DOWNLOAD = "application/vnd.ruckus.download"

    @JvmField
    val APPLICATION_VND_S3SMS = "application/vnd.s3sms"

    @JvmField
    val APPLICATION_VND_SAILINGTRACKER_TRACK = "application/vnd.sailingtracker.track"

    @JvmField
    val APPLICATION_VND_SAR = "application/vnd.sar"

    @JvmField
    val APPLICATION_VND_SBM_CID = "application/vnd.sbm.cid"

    @JvmField
    val APPLICATION_VND_SBM_MID2 = "application/vnd.sbm.mid2"

    @JvmField
    val APPLICATION_VND_SCRIBUS = "application/vnd.scribus"

    @JvmField
    val APPLICATION_VND_SEALEDMEDIA_SOFTSEAL_HTML = "application/vnd.sealedmedia.softseal.html"

    @JvmField
    val APPLICATION_VND_SEALEDMEDIA_SOFTSEAL_PDF = "application/vnd.sealedmedia.softseal.pdf"

    @JvmField
    val APPLICATION_VND_SEALED_3DF = "application/vnd.sealed.3df"

    @JvmField
    val APPLICATION_VND_SEALED_CSF = "application/vnd.sealed.csf"

    @JvmField
    val APPLICATION_VND_SEALED_DOC = "application/vnd.sealed.doc"

    @JvmField
    val APPLICATION_VND_SEALED_EML = "application/vnd.sealed.eml"

    @JvmField
    val APPLICATION_VND_SEALED_MHT = "application/vnd.sealed.mht"

    @JvmField
    val APPLICATION_VND_SEALED_NET = "application/vnd.sealed.net"

    @JvmField
    val APPLICATION_VND_SEALED_PPT = "application/vnd.sealed.ppt"

    @JvmField
    val APPLICATION_VND_SEALED_TIFF = "application/vnd.sealed.tiff"

    @JvmField
    val APPLICATION_VND_SEALED_XLS = "application/vnd.sealed.xls"

    @JvmField
    val APPLICATION_VND_SEEMAIL = "application/vnd.seemail"

    @JvmField
    val APPLICATION_VND_SEIS_JSON = "application/vnd.seis+json"

    @JvmField
    val APPLICATION_VND_SEMA = "application/vnd.sema"

    @JvmField
    val APPLICATION_VND_SEMD = "application/vnd.semd"

    @JvmField
    val APPLICATION_VND_SEMF = "application/vnd.semf"

    @JvmField
    val APPLICATION_VND_SHADE_SAVE_FILE = "application/vnd.shade-save-file"

    @JvmField
    val APPLICATION_VND_SHANA_INFORMED_FORMDATA = "application/vnd.shana.informed.formdata"

    @JvmField
    val APPLICATION_VND_SHANA_INFORMED_FORMTEMPLATE = "application/vnd.shana.informed.formtemplate"

    @JvmField
    val APPLICATION_VND_SHANA_INFORMED_INTERCHANGE = "application/vnd.shana.informed.interchange"

    @JvmField
    val APPLICATION_VND_SHANA_INFORMED_PACKAGE = "application/vnd.shana.informed.package"

    @JvmField
    val APPLICATION_VND_SHOOTPROOF_JSON = "application/vnd.shootproof+json"

    @JvmField
    val APPLICATION_VND_SHOPKICK_JSON = "application/vnd.shopkick+json"

    @JvmField
    val APPLICATION_VND_SHP = "application/vnd.shp"

    @JvmField
    val APPLICATION_VND_SHX = "application/vnd.shx"

    @JvmField
    val APPLICATION_VND_SIGROK_SESSION = "application/vnd.sigrok.session"

    @JvmField
    val APPLICATION_VND_SIMTECH_MINDMAPPER = "application/vnd.SimTech-MindMapper"

    @JvmField
    val APPLICATION_VND_SIREN_JSON = "application/vnd.siren+json"

    @JvmField
    val APPLICATION_VND_SMAF = "application/vnd.smaf"

    @JvmField
    val APPLICATION_VND_SMART_NOTEBOOK = "application/vnd.smart.notebook"

    @JvmField
    val APPLICATION_VND_SMART_TEACHER = "application/vnd.smart.teacher"

    @JvmField
    val APPLICATION_VND_SMINTIO_PORTALS_ARCHIVE = "application/vnd.smintio.portals.archive"

    @JvmField
    val APPLICATION_VND_SNESDEV_PAGE_TABLE = "application/vnd.snesdev-page-table"

    @JvmField
    val APPLICATION_VND_SOFTWARE602_FILLER_FORM_XML = "application/vnd.software602.filler.form+xml"

    @JvmField
    val APPLICATION_VND_SOFTWARE602_FILLER_FORM_XML_ZIP = "application/vnd.software602.filler.form-xml-zip"

    @JvmField
    val APPLICATION_VND_SOLENT_SDKM_XML = "application/vnd.solent.sdkm+xml"

    @JvmField
    val APPLICATION_VND_SPOTFIRE_DXP = "application/vnd.spotfire.dxp"

    @JvmField
    val APPLICATION_VND_SPOTFIRE_SFS = "application/vnd.spotfire.sfs"

    @JvmField
    val APPLICATION_VND_SQLITE3 = "application/vnd.sqlite3"

    @JvmField
    val APPLICATION_VND_SSS_COD = "application/vnd.sss-cod"

    @JvmField
    val APPLICATION_VND_SSS_DTF = "application/vnd.sss-dtf"

    @JvmField
    val APPLICATION_VND_SSS_NTF = "application/vnd.sss-ntf"

    @JvmField
    val APPLICATION_VND_STEPMANIA_PACKAGE = "application/vnd.stepmania.package"

    @JvmField
    val APPLICATION_VND_STEPMANIA_STEPCHART = "application/vnd.stepmania.stepchart"

    @JvmField
    val APPLICATION_VND_STREET_STREAM = "application/vnd.street-stream"

    @JvmField
    val APPLICATION_VND_SUN_WADL_XML = "application/vnd.sun.wadl+xml"

    @JvmField
    val APPLICATION_VND_SUS_CALENDAR = "application/vnd.sus-calendar"

    @JvmField
    val APPLICATION_VND_SVD = "application/vnd.svd"

    @JvmField
    val APPLICATION_VND_SWIFTVIEW_ICS = "application/vnd.swiftview-ics"

    @JvmField
    val APPLICATION_VND_SYBYL_MOL2 = "application/vnd.sybyl.mol2"

    @JvmField
    val APPLICATION_VND_SYCLE_XML = "application/vnd.sycle+xml"

    @JvmField
    val APPLICATION_VND_SYFT_JSON = "application/vnd.syft+json"

    @JvmField
    val APPLICATION_VND_SYNCML_DMDDF_WBXML = "application/vnd.syncml.dmddf+wbxml"

    @JvmField
    val APPLICATION_VND_SYNCML_DMDDF_XML = "application/vnd.syncml.dmddf+xml"

    @JvmField
    val APPLICATION_VND_SYNCML_DMTNDS_WBXML = "application/vnd.syncml.dmtnds+wbxml"

    @JvmField
    val APPLICATION_VND_SYNCML_DMTNDS_XML = "application/vnd.syncml.dmtnds+xml"

    @JvmField
    val APPLICATION_VND_SYNCML_DM_NOTIFICATION = "application/vnd.syncml.dm.notification"

    @JvmField
    val APPLICATION_VND_SYNCML_DM_WBXML = "application/vnd.syncml.dm+wbxml"

    @JvmField
    val APPLICATION_VND_SYNCML_DM_XML = "application/vnd.syncml.dm+xml"

    @JvmField
    val APPLICATION_VND_SYNCML_DS_NOTIFICATION = "application/vnd.syncml.ds.notification"

    @JvmField
    val APPLICATION_VND_SYNCML_XML = "application/vnd.syncml+xml"

    @JvmField
    val APPLICATION_VND_TABLESCHEMA_JSON = "application/vnd.tableschema+json"

    @JvmField
    val APPLICATION_VND_TAO_INTENT_MODULE_ARCHIVE = "application/vnd.tao.intent-module-archive"

    @JvmField
    val APPLICATION_VND_TCPDUMP_PCAP = "application/vnd.tcpdump.pcap"

    @JvmField
    val APPLICATION_VND_THINK_CELL_PPTTC_JSON = "application/vnd.think-cell.ppttc+json"

    @JvmField
    val APPLICATION_VND_TMD_MEDIAFLEX_API_XML = "application/vnd.tmd.mediaflex.api+xml"

    @JvmField
    val APPLICATION_VND_TML = "application/vnd.tml"

    @JvmField
    val APPLICATION_VND_TMOBILE_LIVETV = "application/vnd.tmobile-livetv"

    @JvmField
    val APPLICATION_VND_TRID_TPT = "application/vnd.trid.tpt"

    @JvmField
    val APPLICATION_VND_TRISCAPE_MXS = "application/vnd.triscape.mxs"

    @JvmField
    val APPLICATION_VND_TRI_ONESOURCE = "application/vnd.tri.onesource"

    @JvmField
    val APPLICATION_VND_TRUEAPP = "application/vnd.trueapp"

    @JvmField
    val APPLICATION_VND_TRUEDOC = "application/vnd.truedoc"

    @JvmField
    val APPLICATION_VND_UBISOFT_WEBPLAYER = "application/vnd.ubisoft.webplayer"

    @JvmField
    val APPLICATION_VND_UFDL = "application/vnd.ufdl"

    @JvmField
    val APPLICATION_VND_UIQ_THEME = "application/vnd.uiq.theme"

    @JvmField
    val APPLICATION_VND_UMAJIN = "application/vnd.umajin"

    @JvmField
    val APPLICATION_VND_UNITY = "application/vnd.unity"

    @JvmField
    val APPLICATION_VND_UOML_XML = "application/vnd.uoml+xml"

    @JvmField
    val APPLICATION_VND_UPLANET_ALERT = "application/vnd.uplanet.alert"

    @JvmField
    val APPLICATION_VND_UPLANET_ALERT_WBXML = "application/vnd.uplanet.alert-wbxml"

    @JvmField
    val APPLICATION_VND_UPLANET_BEARER_CHOICE = "application/vnd.uplanet.bearer-choice"

    @JvmField
    val APPLICATION_VND_UPLANET_BEARER_CHOICE_WBXML = "application/vnd.uplanet.bearer-choice-wbxml"

    @JvmField
    val APPLICATION_VND_UPLANET_CACHEOP = "application/vnd.uplanet.cacheop"

    @JvmField
    val APPLICATION_VND_UPLANET_CACHEOP_WBXML = "application/vnd.uplanet.cacheop-wbxml"

    @JvmField
    val APPLICATION_VND_UPLANET_CHANNEL = "application/vnd.uplanet.channel"

    @JvmField
    val APPLICATION_VND_UPLANET_CHANNEL_WBXML = "application/vnd.uplanet.channel-wbxml"

    @JvmField
    val APPLICATION_VND_UPLANET_LIST = "application/vnd.uplanet.list"

    @JvmField
    val APPLICATION_VND_UPLANET_LISTCMD = "application/vnd.uplanet.listcmd"

    @JvmField
    val APPLICATION_VND_UPLANET_LISTCMD_WBXML = "application/vnd.uplanet.listcmd-wbxml"

    @JvmField
    val APPLICATION_VND_UPLANET_LIST_WBXML = "application/vnd.uplanet.list-wbxml"

    @JvmField
    val APPLICATION_VND_UPLANET_SIGNAL = "application/vnd.uplanet.signal"

    @JvmField
    val APPLICATION_VND_URI_MAP = "application/vnd.uri-map"

    @JvmField
    val APPLICATION_VND_VALVE_SOURCE_MATERIAL = "application/vnd.valve.source.material"

    @JvmField
    val APPLICATION_VND_VCX = "application/vnd.vcx"

    @JvmField
    val APPLICATION_VND_VD_STUDY = "application/vnd.vd-study"

    @JvmField
    val APPLICATION_VND_VECTORWORKS = "application/vnd.vectorworks"

    @JvmField
    val APPLICATION_VND_VEL_JSON = "application/vnd.vel+json"

    @JvmField
    val APPLICATION_VND_VERIMATRIX_VCAS = "application/vnd.verimatrix.vcas"

    @JvmField
    val APPLICATION_VND_VERITONE_AION_JSON = "application/vnd.veritone.aion+json"

    @JvmField
    val APPLICATION_VND_VERYANT_THIN = "application/vnd.veryant.thin"

    @JvmField
    val APPLICATION_VND_VES_ENCRYPTED = "application/vnd.ves.encrypted"

    @JvmField
    val APPLICATION_VND_VIDSOFT_VIDCONFERENCE = "application/vnd.vidsoft.vidconference"

    @JvmField
    val APPLICATION_VND_VISIO = "application/vnd.visio"

    @JvmField
    val APPLICATION_VND_VISIONARY = "application/vnd.visionary"

    @JvmField
    val APPLICATION_VND_VIVIDENCE_SCRIPTFILE = "application/vnd.vividence.scriptfile"

    @JvmField
    val APPLICATION_VND_VSF = "application/vnd.vsf"

    @JvmField
    val APPLICATION_VND_WAP_SIC = "application/vnd.wap.sic"

    @JvmField
    val APPLICATION_VND_WAP_SLC = "application/vnd.wap.slc"

    @JvmField
    val APPLICATION_VND_WAP_WBXML = "application/vnd.wap.wbxml"

    @JvmField
    val APPLICATION_VND_WAP_WMLC = "application/vnd.wap.wmlc"

    @JvmField
    val APPLICATION_VND_WAP_WMLSCRIPTC = "application/vnd.wap.wmlscriptc"

    @JvmField
    val APPLICATION_VND_WASMFLOW_WAFL = "application/vnd.wasmflow.wafl"

    @JvmField
    val APPLICATION_VND_WEBTURBO = "application/vnd.webturbo"

    @JvmField
    val APPLICATION_VND_WFA_DPP = "application/vnd.wfa.dpp"

    @JvmField
    val APPLICATION_VND_WFA_P2P = "application/vnd.wfa.p2p"

    @JvmField
    val APPLICATION_VND_WFA_WSC = "application/vnd.wfa.wsc"

    @JvmField
    val APPLICATION_VND_WINDOWS_DEVICEPAIRING = "application/vnd.windows.devicepairing"

    @JvmField
    val APPLICATION_VND_WMC = "application/vnd.wmc"

    @JvmField
    val APPLICATION_VND_WMF_BOOTSTRAP = "application/vnd.wmf.bootstrap"

    @JvmField
    val APPLICATION_VND_WOLFRAM_MATHEMATICA = "application/vnd.wolfram.mathematica"

    @JvmField
    val APPLICATION_VND_WOLFRAM_MATHEMATICA_PACKAGE = "application/vnd.wolfram.mathematica.package"

    @JvmField
    val APPLICATION_VND_WOLFRAM_PLAYER = "application/vnd.wolfram.player"

    @JvmField
    val APPLICATION_VND_WORDLIFT = "application/vnd.wordlift"

    @JvmField
    val APPLICATION_VND_WORDPERFECT = "application/vnd.wordperfect"

    @JvmField
    val APPLICATION_VND_WQD = "application/vnd.wqd"

    @JvmField
    val APPLICATION_VND_WRQ_HP3000_LABELLED = "application/vnd.wrq-hp3000-labelled"

    @JvmField
    val APPLICATION_VND_WT_STF = "application/vnd.wt.stf"

    @JvmField
    val APPLICATION_VND_WV_CSP_WBXML = "application/vnd.wv.csp+wbxml"

    @JvmField
    val APPLICATION_VND_WV_CSP_XML = "application/vnd.wv.csp+xml"

    @JvmField
    val APPLICATION_VND_WV_SSP_XML = "application/vnd.wv.ssp+xml"

    @JvmField
    val APPLICATION_VND_XACML_JSON = "application/vnd.xacml+json"

    @JvmField
    val APPLICATION_VND_XARA = "application/vnd.xara"

    @JvmField
    val APPLICATION_VND_XECRETS_ENCRYPTED = "application/vnd.xecrets-encrypted"

    @JvmField
    val APPLICATION_VND_XFDL = "application/vnd.xfdl"

    @JvmField
    val APPLICATION_VND_XFDL_WEBFORM = "application/vnd.xfdl.webform"

    @JvmField
    val APPLICATION_VND_XMI_XML = "application/vnd.xmi+xml"

    @JvmField
    val APPLICATION_VND_XMPIE_CPKG = "application/vnd.xmpie.cpkg"

    @JvmField
    val APPLICATION_VND_XMPIE_DPKG = "application/vnd.xmpie.dpkg"

    @JvmField
    val APPLICATION_VND_XMPIE_PLAN = "application/vnd.xmpie.plan"

    @JvmField
    val APPLICATION_VND_XMPIE_PPKG = "application/vnd.xmpie.ppkg"

    @JvmField
    val APPLICATION_VND_XMPIE_XLIM = "application/vnd.xmpie.xlim"

    @JvmField
    val APPLICATION_VND_YAMAHA_HV_DIC = "application/vnd.yamaha.hv-dic"

    @JvmField
    val APPLICATION_VND_YAMAHA_HV_SCRIPT = "application/vnd.yamaha.hv-script"

    @JvmField
    val APPLICATION_VND_YAMAHA_HV_VOICE = "application/vnd.yamaha.hv-voice"

    @JvmField
    val APPLICATION_VND_YAMAHA_OPENSCOREFORMAT = "application/vnd.yamaha.openscoreformat"

    @JvmField
    val APPLICATION_VND_YAMAHA_OPENSCOREFORMAT_OSFPVG_XML = "application/vnd.yamaha.openscoreformat.osfpvg+xml"

    @JvmField
    val APPLICATION_VND_YAMAHA_REMOTE_SETUP = "application/vnd.yamaha.remote-setup"

    @JvmField
    val APPLICATION_VND_YAMAHA_SMAF_AUDIO = "application/vnd.yamaha.smaf-audio"

    @JvmField
    val APPLICATION_VND_YAMAHA_SMAF_PHRASE = "application/vnd.yamaha.smaf-phrase"

    @JvmField
    val APPLICATION_VND_YAMAHA_THROUGH_NGN = "application/vnd.yamaha.through-ngn"

    @JvmField
    val APPLICATION_VND_YAMAHA_TUNNEL_UDPENCAP = "application/vnd.yamaha.tunnel-udpencap"

    @JvmField
    val APPLICATION_VND_YAOWEME = "application/vnd.yaoweme"

    @JvmField
    val APPLICATION_VND_YELLOWRIVER_CUSTOM_MENU = "application/vnd.yellowriver-custom-menu"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("VIDEO_VND_YOUTUBE_YT"), DeprecationLevel.ERROR)
    val APPLICATION_VND_YOUTUBE_YT = "application/vnd.youtube.yt"

    @JvmField
    val APPLICATION_VND_ZUL = "application/vnd.zul"

    @JvmField
    val APPLICATION_VND_ZZAZZ_DECK_XML = "application/vnd.zzazz.deck+xml"

    @JvmField
    val APPLICATION_VOICEXML_XML = "application/voicexml+xml"

    @JvmField
    val APPLICATION_VOUCHER_CMS_JSON = "application/voucher-cms+json"

    @JvmField
    val APPLICATION_VQ_RTCPXR = "application/vq-rtcpxr"

    @JvmField
    val APPLICATION_WASM = "application/wasm"

    @JvmField
    val APPLICATION_WATCHERINFO_XML = "application/watcherinfo+xml"

    @JvmField
    val APPLICATION_WEBPUSH_OPTIONS_JSON = "application/webpush-options+json"

    @JvmField
    val APPLICATION_WHOISPP_QUERY = "application/whoispp-query"

    @JvmField
    val APPLICATION_WHOISPP_RESPONSE = "application/whoispp-response"

    @JvmField
    val APPLICATION_WIDGET = "application/widget"

    @JvmField
    val APPLICATION_WITA = "application/wita"

    @JvmField
    val APPLICATION_WORDPERFECT5_1 = "application/wordperfect5.1"

    @JvmField
    val APPLICATION_WSDL_XML = "application/wsdl+xml"

    @JvmField
    val APPLICATION_WSPOLICY_XML = "application/wspolicy+xml"

    @JvmField
    val APPLICATION_X400_BP = "application/x400-bp"

    @JvmField
    val APPLICATION_XACML_XML = "application/xacml+xml"

    @JvmField
    val APPLICATION_XCAP_ATT_XML = "application/xcap-att+xml"

    @JvmField
    val APPLICATION_XCAP_CAPS_XML = "application/xcap-caps+xml"

    @JvmField
    val APPLICATION_XCAP_DIFF_XML = "application/xcap-diff+xml"

    @JvmField
    val APPLICATION_XCAP_EL_XML = "application/xcap-el+xml"

    @JvmField
    val APPLICATION_XCAP_ERROR_XML = "application/xcap-error+xml"

    @JvmField
    val APPLICATION_XCAP_NS_XML = "application/xcap-ns+xml"

    @JvmField
    val APPLICATION_XCON_CONFERENCE_INFO_DIFF_XML = "application/xcon-conference-info-diff+xml"

    @JvmField
    val APPLICATION_XCON_CONFERENCE_INFO_XML = "application/xcon-conference-info+xml"

    @JvmField
    val APPLICATION_XENC_XML = "application/xenc+xml"

    @JvmField
    val APPLICATION_XFDF = "application/xfdf"

    @JvmField
    val APPLICATION_XLIFF_XML = "application/xliff+xml"

    @JvmField
    val APPLICATION_XML_DTD = "application/xml-dtd"

    @JvmField
    val APPLICATION_XML_EXTERNAL_PARSED_ENTITY = "application/xml-external-parsed-entity"

    @JvmField
    val APPLICATION_XML_PATCH_XML = "application/xml-patch+xml"

    @JvmField
    val APPLICATION_XMPP_XML = "application/xmpp+xml"

    @JvmField
    val APPLICATION_XOP_XML = "application/xop+xml"

    @JvmField
    val APPLICATION_XSLT_XML = "application/xslt+xml"

    @JvmField
    val APPLICATION_XV_XML = "application/xv+xml"

    @JvmField
    val APPLICATION_X_PKI_MESSAGE = "application/x-pki-message"

    @JvmField
    val APPLICATION_X_X509_CA_CERT = "application/x-x509-ca-cert"

    @JvmField
    val APPLICATION_X_X509_CA_RA_CERT = "application/x-x509-ca-ra-cert"

    @JvmField
    val APPLICATION_X_X509_NEXT_CA_CERT = "application/x-x509-next-ca-cert"

    @JvmField
    val APPLICATION_YAML = "application/yaml"

    @JvmField
    val APPLICATION_YANG = "application/yang"

    @JvmField
    val APPLICATION_YANG_DATA_CBOR = "application/yang-data+cbor"

    @JvmField
    val APPLICATION_YANG_DATA_JSON = "application/yang-data+json"

    @JvmField
    val APPLICATION_YANG_DATA_XML = "application/yang-data+xml"

    @JvmField
    val APPLICATION_YANG_PATCH_JSON = "application/yang-patch+json"

    @JvmField
    val APPLICATION_YANG_PATCH_XML = "application/yang-patch+xml"

    @JvmField
    val APPLICATION_YANG_SID_JSON = "application/yang-sid+json"

    @JvmField
    val APPLICATION_YIN_XML = "application/yin+xml"

    @JvmField
    val APPLICATION_ZLIB = "application/zlib"

    @JvmField
    val APPLICATION_ZSTD = "application/zstd"

    @JvmField
    val AUDIO_1D_INTERLEAVED_PARITYFEC = "audio/1d-interleaved-parityfec"

    @JvmField
    val AUDIO_32KADPCM = "audio/32kadpcm"

    @JvmField
    val AUDIO_3GPP = "audio/3gpp"

    @JvmField
    val AUDIO_3GPP2 = "audio/3gpp2"

    @JvmField
    val AUDIO_AC3 = "audio/ac3"

    @JvmField
    val AUDIO_AMR_WB_PLUS = "audio/amr-wb+"

    @JvmField
    val AUDIO_AMR_WB = "audio/AMR-WB"

    @JvmField
    val AUDIO_APTX = "audio/aptx"

    @JvmField
    val AUDIO_ASC = "audio/asc"

    @JvmField
    val AUDIO_ATRAC3 = "audio/ATRAC3"

    @JvmField
    val AUDIO_ATRAC_ADVANCED_LOSSLESS = "audio/ATRAC-ADVANCED-LOSSLESS"

    @JvmField
    val AUDIO_ATRAC_X = "audio/ATRAC-X"

    @JvmField
    val AUDIO_BV16 = "audio/BV16"

    @JvmField
    val AUDIO_BV32 = "audio/BV32"

    @JvmField
    val AUDIO_CLEARMODE = "audio/clearmode"

    @JvmField
    val AUDIO_CN = "audio/CN"

    @JvmField
    val AUDIO_DAT12 = "audio/DAT12"

    @JvmField
    val AUDIO_DLS = "audio/dls"

    @JvmField
    val AUDIO_DSR_ES201108 = "audio/dsr-es201108"

    @JvmField
    val AUDIO_DSR_ES202050 = "audio/dsr-es202050"

    @JvmField
    val AUDIO_DSR_ES202211 = "audio/dsr-es202211"

    @JvmField
    val AUDIO_DSR_ES202212 = "audio/dsr-es202212"

    @JvmField
    val AUDIO_DV = "audio/DV"

    @JvmField
    val AUDIO_DVI4 = "audio/DVI4"

    @JvmField
    val AUDIO_EAC3 = "audio/eac3"

    @JvmField
    val AUDIO_ENCAPRTP = "audio/encaprtp"

    @JvmField
    val AUDIO_EVRC = "audio/EVRC"

    @JvmField
    val AUDIO_EVRC0 = "audio/EVRC0"

    @JvmField
    val AUDIO_EVRC1 = "audio/EVRC1"

    @JvmField
    val AUDIO_EVRCB = "audio/EVRCB"

    @JvmField
    val AUDIO_EVRCB0 = "audio/EVRCB0"

    @JvmField
    val AUDIO_EVRCB1 = "audio/EVRCB1"

    @JvmField
    val AUDIO_EVRCNW = "audio/EVRCNW"

    @JvmField
    val AUDIO_EVRCNW0 = "audio/EVRCNW0"

    @JvmField
    val AUDIO_EVRCNW1 = "audio/EVRCNW1"

    @JvmField
    val AUDIO_EVRCWB = "audio/EVRCWB"

    @JvmField
    val AUDIO_EVRCWB0 = "audio/EVRCWB0"

    @JvmField
    val AUDIO_EVRCWB1 = "audio/EVRCWB1"

    @JvmField
    val AUDIO_EVRC_QCP = "audio/EVRC-QCP"

    @JvmField
    val AUDIO_EVS = "audio/EVS"

    @JvmField
    val AUDIO_EXAMPLE = "audio/example"

    @JvmField
    val AUDIO_FLEXFEC = "audio/flexfec"

    @JvmField
    val AUDIO_FWDRED = "audio/fwdred"

    @JvmField
    val AUDIO_G711_0 = "audio/G711-0"

    @JvmField
    val AUDIO_G719 = "audio/G719"

    @JvmField
    val AUDIO_G722 = "audio/G722"

    @JvmField
    val AUDIO_G7221 = "audio/G7221"

    @JvmField
    val AUDIO_G723 = "audio/G723"

    @JvmField
    val AUDIO_G726_16 = "audio/G726-16"

    @JvmField
    val AUDIO_G726_24 = "audio/G726-24"

    @JvmField
    val AUDIO_G726_32 = "audio/G726-32"

    @JvmField
    val AUDIO_G726_40 = "audio/G726-40"

    @JvmField
    val AUDIO_G728 = "audio/G728"

    @JvmField
    val AUDIO_G729 = "audio/G729"

    @JvmField
    val AUDIO_G7291 = "audio/G7291"

    @JvmField
    val AUDIO_G729D = "audio/G729D"

    @JvmField
    val AUDIO_G729E = "audio/G729E"

    @JvmField
    val AUDIO_GSM = "audio/GSM"

    @JvmField
    val AUDIO_GSM_EFR = "audio/GSM-EFR"

    @JvmField
    val AUDIO_GSM_HR_08 = "audio/GSM-HR-08"

    @JvmField
    val AUDIO_ILBC = "audio/iLBC"

    @JvmField
    val AUDIO_IP_MR_V2_5 = "audio/ip-mr_v2.5"

    @JvmField
    val AUDIO_L16 = "audio/L16"

    @JvmField
    val AUDIO_L20 = "audio/L20"

    @JvmField
    val AUDIO_L24 = "audio/L24"

    @JvmField
    val AUDIO_L8 = "audio/L8"

    @JvmField
    val AUDIO_LPC = "audio/LPC"

    @JvmField
    val AUDIO_MATROSKA = "audio/matroska"

    @JvmField
    val AUDIO_MELP = "audio/MELP"

    @JvmField
    val AUDIO_MELP1200 = "audio/MELP1200"

    @JvmField
    val AUDIO_MELP2400 = "audio/MELP2400"

    @JvmField
    val AUDIO_MELP600 = "audio/MELP600"

    @JvmField
    val AUDIO_MHAS = "audio/mhas"

    @JvmField
    val AUDIO_MIDI_CLIP = "audio/midi-clip"

    @JvmField
    val AUDIO_MOBILE_XMF = "audio/mobile-xmf"

    @JvmField
    val AUDIO_MP4A_LATM = "audio/MP4A-LATM"

    @JvmField
    val AUDIO_MPA = "audio/MPA"

    @JvmField
    val AUDIO_MPA_ROBUST = "audio/mpa-robust"

    @JvmField
    val AUDIO_MPEG4_GENERIC = "audio/mpeg4-generic"

    @JvmField
    val AUDIO_OPUS = "audio/opus"

    @JvmField
    val AUDIO_PARITYFEC = "audio/parityfec"

    @JvmField
    val AUDIO_PCMA = "audio/PCMA"

    @JvmField
    val AUDIO_PCMA_WB = "audio/PCMA-WB"

    @JvmField
    val AUDIO_PCMU = "audio/PCMU"

    @JvmField
    val AUDIO_PCMU_WB = "audio/PCMU-WB"

    @JvmField
    val AUDIO_PRS_SID = "audio/prs.sid"

    @JvmField
    val AUDIO_QCELP = "audio/QCELP"

    @JvmField
    val AUDIO_RAPTORFEC = "audio/raptorfec"

    @JvmField
    val AUDIO_RED = "audio/RED"

    @JvmField
    val AUDIO_RTPLOOPBACK = "audio/rtploopback"

    @JvmField
    val AUDIO_RTP_ENC_AESCM128 = "audio/rtp-enc-aescm128"

    @JvmField
    val AUDIO_RTP_MIDI = "audio/rtp-midi"

    @JvmField
    val AUDIO_RTX = "audio/rtx"

    @JvmField
    val AUDIO_SCIP = "audio/scip"

    @JvmField
    val AUDIO_SMV = "audio/SMV"

    @JvmField
    val AUDIO_SMV0 = "audio/SMV0"

    @JvmField
    val AUDIO_SMV_QCP = "audio/SMV-QCP"

    @JvmField
    val AUDIO_SOFA = "audio/sofa"

    @JvmField
    val AUDIO_SPEEX = "audio/speex"

    @JvmField
    val AUDIO_SP_MIDI = "audio/sp-midi"

    @JvmField
    val AUDIO_T140C = "audio/t140c"

    @JvmField
    val AUDIO_T38 = "audio/t38"

    @JvmField
    val AUDIO_TELEPHONE_EVENT = "audio/telephone-event"

    @JvmField
    val AUDIO_TETRA_ACELP = "audio/TETRA_ACELP"

    @JvmField
    val AUDIO_TETRA_ACELP_BB = "audio/TETRA_ACELP_BB"

    @JvmField
    val AUDIO_TONE = "audio/tone"

    @JvmField
    val AUDIO_TSVCIS = "audio/TSVCIS"

    @JvmField
    val AUDIO_UEMCLIP = "audio/UEMCLIP"

    @JvmField
    val AUDIO_ULPFEC = "audio/ulpfec"

    @JvmField
    val AUDIO_USAC = "audio/usac"

    @JvmField
    val AUDIO_VDVI = "audio/VDVI"

    @JvmField
    val AUDIO_VMR_WB = "audio/VMR-WB"

    @JvmField
    val AUDIO_VND_3GPP_IUFP = "audio/vnd.3gpp.iufp"

    @JvmField
    val AUDIO_VND_4SB = "audio/vnd.4SB"

    @JvmField
    val AUDIO_VND_AUDIOKOZ = "audio/vnd.audiokoz"

    @JvmField
    val AUDIO_VND_CELP = "audio/vnd.CELP"

    @JvmField
    val AUDIO_VND_CISCO_NSE = "audio/vnd.cisco.nse"

    @JvmField
    val AUDIO_VND_CMLES_RADIO_EVENTS = "audio/vnd.cmles.radio-events"

    @JvmField
    val AUDIO_VND_CNS_ANP1 = "audio/vnd.cns.anp1"

    @JvmField
    val AUDIO_VND_CNS_INF1 = "audio/vnd.cns.inf1"

    @JvmField
    val AUDIO_VND_DECE_AUDIO = "audio/vnd.dece.audio"

    @JvmField
    val AUDIO_VND_DIGITAL_WINDS = "audio/vnd.digital-winds"

    @JvmField
    val AUDIO_VND_DLNA_ADTS = "audio/vnd.dlna.adts"

    @JvmField
    val AUDIO_VND_DOLBY_HEAAC_1 = "audio/vnd.dolby.heaac.1"

    @JvmField
    val AUDIO_VND_DOLBY_HEAAC_2 = "audio/vnd.dolby.heaac.2"

    @JvmField
    val AUDIO_VND_DOLBY_MLP = "audio/vnd.dolby.mlp"

    @JvmField
    val AUDIO_VND_DOLBY_MPS = "audio/vnd.dolby.mps"

    @JvmField
    val AUDIO_VND_DOLBY_PL2 = "audio/vnd.dolby.pl2"

    @JvmField
    val AUDIO_VND_DOLBY_PL2X = "audio/vnd.dolby.pl2x"

    @JvmField
    val AUDIO_VND_DOLBY_PL2Z = "audio/vnd.dolby.pl2z"

    @JvmField
    val AUDIO_VND_DOLBY_PULSE_1 = "audio/vnd.dolby.pulse.1"

    @JvmField
    val AUDIO_VND_DRA = "audio/vnd.dra"

    @JvmField
    val AUDIO_VND_DTS = "audio/vnd.dts"

    @JvmField
    val AUDIO_VND_DTS_HD = "audio/vnd.dts.hd"

    @JvmField
    val AUDIO_VND_DTS_UHD = "audio/vnd.dts.uhd"

    @JvmField
    val AUDIO_VND_DVB_FILE = "audio/vnd.dvb.file"

    @JvmField
    val AUDIO_VND_EVERAD_PLJ = "audio/vnd.everad.plj"

    @JvmField
    val AUDIO_VND_HNS_AUDIO = "audio/vnd.hns.audio"

    @JvmField
    val AUDIO_VND_LUCENT_VOICE = "audio/vnd.lucent.voice"

    @JvmField
    val AUDIO_VND_MS_PLAYREADY_MEDIA_PYA = "audio/vnd.ms-playready.media.pya"

    @JvmField
    val AUDIO_VND_NOKIA_MOBILE_XMF = "audio/vnd.nokia.mobile-xmf"

    @JvmField
    val AUDIO_VND_NORTEL_VBK = "audio/vnd.nortel.vbk"

    @JvmField
    val AUDIO_VND_NUERA_ECELP4800 = "audio/vnd.nuera.ecelp4800"

    @JvmField
    val AUDIO_VND_NUERA_ECELP7470 = "audio/vnd.nuera.ecelp7470"

    @JvmField
    val AUDIO_VND_NUERA_ECELP9600 = "audio/vnd.nuera.ecelp9600"

    @JvmField
    val AUDIO_VND_OCTEL_SBC = "audio/vnd.octel.sbc"

    @JvmField
    val AUDIO_VND_PRESONUS_MULTITRACK = "audio/vnd.presonus.multitrack"

    @JvmField
    @Deprecated("Deprecated", ReplaceWith("AUDIO_QCELP"))
    val AUDIO_VND_QCELP = "audio/vnd.qcelp"

    @JvmField
    val AUDIO_VND_RHETOREX_32KADPCM = "audio/vnd.rhetorex.32kadpcm"

    @JvmField
    val AUDIO_VND_RIP = "audio/vnd.rip"

    @JvmField
    val AUDIO_VND_SEALEDMEDIA_SOFTSEAL_MPEG = "audio/vnd.sealedmedia.softseal.mpeg"

    @JvmField
    val AUDIO_VND_VMX_CVSD = "audio/vnd.vmx.cvsd"

    @JvmField
    val AUDIO_VORBIS = "audio/vorbis"

    @JvmField
    val AUDIO_VORBIS_CONFIG = "audio/vorbis-config"

    @JvmField
    val FONT_COLLECTION = "font/collection"

    @JvmField
    val FONT_OTF = "font/otf"

    @JvmField
    val FONT_SFNT = "font/sfnt"

    @JvmField
    val FONT_TTF = "font/ttf"

    @JvmField
    val FONT_WOFF = "font/woff"

    @JvmField
    val FONT_WOFF2 = "font/woff2"

    @JvmField
    val IMAGE_ACES = "image/aces"

    @JvmField
    val IMAGE_APNG = "image/apng"

    @JvmField
    val IMAGE_AVCI = "image/avci"

    @JvmField
    val IMAGE_AVCS = "image/avcs"

    @JvmField
    val IMAGE_AVIF = "image/avif"

    @JvmField
    val IMAGE_CGM = "image/cgm"

    @JvmField
    val IMAGE_DICOM_RLE = "image/dicom-rle"

    @JvmField
    val IMAGE_DPX = "image/dpx"

    @JvmField
    val IMAGE_EMF = "image/emf"

    @JvmField
    val IMAGE_EXAMPLE = "image/example"

    @JvmField
    val IMAGE_FITS = "image/fits"

    @JvmField
    val IMAGE_G3FAX = "image/g3fax"

    @JvmField
    val IMAGE_HEIC = "image/heic"

    @JvmField
    val IMAGE_HEIC_SEQUENCE = "image/heic-sequence"

    @JvmField
    val IMAGE_HEIF = "image/heif"

    @JvmField
    val IMAGE_HEIF_SEQUENCE = "image/heif-sequence"

    @JvmField
    val IMAGE_HEJ2K = "image/hej2k"

    @JvmField
    val IMAGE_HSJ2 = "image/hsj2"

    @JvmField
    val IMAGE_J2C = "image/j2c"

    @JvmField
    val IMAGE_JLS = "image/jls"

    @JvmField
    val IMAGE_JP2 = "image/jp2"

    @JvmField
    val IMAGE_JPH = "image/jph"

    @JvmField
    val IMAGE_JPHC = "image/jphc"

    @JvmField
    val IMAGE_JPM = "image/jpm"

    @JvmField
    val IMAGE_JPX = "image/jpx"

    @JvmField
    val IMAGE_JXL = "image/jxl"

    @JvmField
    val IMAGE_JXR = "image/jxr"

    @JvmField
    val IMAGE_JXRA = "image/jxrA"

    @JvmField
    val IMAGE_JXRS = "image/jxrS"

    @JvmField
    val IMAGE_JXS = "image/jxs"

    @JvmField
    val IMAGE_JXSC = "image/jxsc"

    @JvmField
    val IMAGE_JXSI = "image/jxsi"

    @JvmField
    val IMAGE_JXSS = "image/jxss"

    @JvmField
    val IMAGE_KTX = "image/ktx"

    @JvmField
    val IMAGE_KTX2 = "image/ktx2"

    @JvmField
    val IMAGE_NAPLPS = "image/naplps"

    @JvmField
    val IMAGE_PRS_BTIF = "image/prs.btif"

    @JvmField
    val IMAGE_PRS_PTI = "image/prs.pti"

    @JvmField
    val IMAGE_PWG_RASTER = "image/pwg-raster"

    @JvmField
    val IMAGE_T38 = "image/t38"

    @JvmField
    val IMAGE_TIFF_FX = "image/tiff-fx"

    @JvmField
    val IMAGE_VND_ADOBE_PHOTOSHOP = "image/vnd.adobe.photoshop"

    @JvmField
    val IMAGE_VND_AIRZIP_ACCELERATOR_AZV = "image/vnd.airzip.accelerator.azv"

    @JvmField
    val IMAGE_VND_CNS_INF2 = "image/vnd.cns.inf2"

    @JvmField
    val IMAGE_VND_DECE_GRAPHIC = "image/vnd.dece.graphic"

    @JvmField
    val IMAGE_VND_DJVU = "image/vnd.djvu"

    @JvmField
    val IMAGE_VND_DVB_SUBTITLE = "image/vnd.dvb.subtitle"

    @JvmField
    val IMAGE_VND_DWG = "image/vnd.dwg"

    @JvmField
    val IMAGE_VND_DXF = "image/vnd.dxf"

    @JvmField
    val IMAGE_VND_FASTBIDSHEET = "image/vnd.fastbidsheet"

    @JvmField
    val IMAGE_VND_FST = "image/vnd.fst"

    @JvmField
    val IMAGE_VND_FUJIXEROX_EDMICS_MMR = "image/vnd.fujixerox.edmics-mmr"

    @JvmField
    val IMAGE_VND_FUJIXEROX_EDMICS_RLC = "image/vnd.fujixerox.edmics-rlc"

    @JvmField
    val IMAGE_VND_GLOBALGRAPHICS_PGB = "image/vnd.globalgraphics.pgb"

    @JvmField
    val IMAGE_VND_MICROSOFT_ICON = "image/vnd.microsoft.icon"

    @JvmField
    val IMAGE_VND_MIX = "image/vnd.mix"

    @JvmField
    val IMAGE_VND_MOZILLA_APNG = "image/vnd.mozilla.apng"

    @JvmField
    val IMAGE_VND_MS_MODI = "image/vnd.ms-modi"

    @JvmField
    val IMAGE_VND_NET_FPX = "image/vnd.net-fpx"

    @JvmField
    val IMAGE_VND_PCO_B16 = "image/vnd.pco.b16"

    @JvmField
    val IMAGE_VND_RADIANCE = "image/vnd.radiance"

    @JvmField
    val IMAGE_VND_SEALEDMEDIA_SOFTSEAL_GIF = "image/vnd.sealedmedia.softseal.gif"

    @JvmField
    val IMAGE_VND_SEALEDMEDIA_SOFTSEAL_JPG = "image/vnd.sealedmedia.softseal.jpg"

    @JvmField
    val IMAGE_VND_SEALED_PNG = "image/vnd.sealed.png"

    @JvmField
    val IMAGE_VND_SVF = "image/vnd.svf"

    @JvmField
    val IMAGE_VND_TENCENT_TAP = "image/vnd.tencent.tap"

    @JvmField
    val IMAGE_VND_VALVE_SOURCE_TEXTURE = "image/vnd.valve.source.texture"

    @JvmField
    val IMAGE_VND_WAP_WBMP = "image/vnd.wap.wbmp"

    @JvmField
    val IMAGE_VND_XIFF = "image/vnd.xiff"

    @JvmField
    val IMAGE_VND_ZBRUSH_PCX = "image/vnd.zbrush.pcx"

    @JvmField
    val IMAGE_WMF = "image/wmf"

    @JvmField
    @Deprecated("Deprecated", ReplaceWith("IMAGE_EMF"))
    val IMAGE_X_EMF = "image/emf"

    @JvmField
    @Deprecated("Deprecated", ReplaceWith("IMAGE_WMF"))
    val IMAGE_X_WMF = "image/wmf"

    @JvmField
    val MESSAGE_BHTTP = "message/bhttp"

    @JvmField
    val MESSAGE_CPIM = "message/CPIM"

    @JvmField
    val MESSAGE_DELIVERY_STATUS = "message/delivery-status"

    @JvmField
    val MESSAGE_DISPOSITION_NOTIFICATION = "message/disposition-notification"

    @JvmField
    val MESSAGE_EXAMPLE = "message/example"

    @JvmField
    val MESSAGE_FEEDBACK_REPORT = "message/feedback-report"

    @JvmField
    val MESSAGE_GLOBAL = "message/global"

    @JvmField
    val MESSAGE_GLOBAL_DELIVERY_STATUS = "message/global-delivery-status"

    @JvmField
    val MESSAGE_GLOBAL_DISPOSITION_NOTIFICATION = "message/global-disposition-notification"

    @JvmField
    val MESSAGE_GLOBAL_HEADERS = "message/global-headers"

    @JvmField
    val MESSAGE_HTTP = "message/http"

    @JvmField
    val MESSAGE_IMDN_XML = "message/imdn+xml"

    @JvmField
    val MESSAGE_MLS = "message/mls"

    @JvmField
    @Deprecated("Obsoleted by RFC5537", ReplaceWith("NULL"), DeprecationLevel.ERROR)
    val MESSAGE_NEWS = "message/news"

    @JvmField
    val MESSAGE_OHTTP_REQ = "message/ohttp-req"

    @JvmField
    val MESSAGE_OHTTP_RES = "message/ohttp-res"

    @JvmField
    val MESSAGE_SIP = "message/sip"

    @JvmField
    val MESSAGE_SIPFRAG = "message/sipfrag"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("NULL"), DeprecationLevel.ERROR)
    val MESSAGE_S_HTTP = "message/s-http"

    @JvmField
    val MESSAGE_TRACKING_STATUS = "message/tracking-status"

    @JvmField
    @Deprecated("Obsoleted by request", ReplaceWith("NULL"), DeprecationLevel.ERROR)
    val MESSAGE_VND_SI_SIMP = "message/vnd.si.simp"

    @JvmField
    val MESSAGE_VND_WFA_WSC = "message/vnd.wfa.wsc"

    @JvmField
    val MODEL_3MF = "model/3mf"

    @JvmField
    val MODEL_E57 = "model/e57"

    @JvmField
    val MODEL_EXAMPLE = "model/example"

    @JvmField
    val MODEL_GLTF_BINARY = "model/gltf-binary"

    @JvmField
    val MODEL_GLTF_JSON = "model/gltf+json"

    @JvmField
    val MODEL_IGES = "model/iges"

    @JvmField
    val MODEL_JT = "model/JT"

    @JvmField
    val MODEL_MTL = "model/mtl"

    @JvmField
    val MODEL_OBJ = "model/obj"

    @JvmField
    val MODEL_PRC = "model/prc"

    @JvmField
    val MODEL_STEP = "model/step"

    @JvmField
    val MODEL_STEP_XML = "model/step+xml"

    @JvmField
    val MODEL_STEP_XML_ZIP = "model/step-xml+zip"

    @JvmField
    val MODEL_STEP_ZIP = "model/step+zip"

    @JvmField
    val MODEL_STL = "model/stl"

    @JvmField
    val MODEL_U3D = "model/u3d"

    @JvmField
    val MODEL_VND_BARY = "model/vnd.bary"

    @JvmField
    val MODEL_VND_CLD = "model/vnd.cld"

    @JvmField
    val MODEL_VND_COLLADA_XML = "model/vnd.collada+xml"

    @JvmField
    val MODEL_VND_DWF = "model/vnd.dwf"

    @JvmField
    val MODEL_VND_FLATLAND_3DML = "model/vnd.flatland.3dml"

    @JvmField
    val MODEL_VND_GDL = "model/vnd.gdl"

    @JvmField
    val MODEL_VND_GS_GDL = "model/vnd.gs-gdl"

    @JvmField
    val MODEL_VND_GTW = "model/vnd.gtw"

    @JvmField
    val MODEL_VND_MOML_XML = "model/vnd.moml+xml"

    @JvmField
    val MODEL_VND_MTS = "model/vnd.mts"

    @JvmField
    val MODEL_VND_OPENGEX = "model/vnd.opengex"

    @JvmField
    val MODEL_VND_PARASOLID_TRANSMIT_BINARY = "model/vnd.parasolid.transmit.binary"

    @JvmField
    val MODEL_VND_PARASOLID_TRANSMIT_TEXT = "model/vnd.parasolid.transmit.text"

    @JvmField
    val MODEL_VND_PYTHA_PYOX = "model/vnd.pytha.pyox"

    @JvmField
    val MODEL_VND_ROSETTE_ANNOTATED_DATA_MODEL = "model/vnd.rosette.annotated-data-model"

    @JvmField
    val MODEL_VND_SAP_VDS = "model/vnd.sap.vds"

    @JvmField
    val MODEL_VND_USDA = "model/vnd.usda"

    @JvmField
    val MODEL_VND_USDZ_ZIP = "model/vnd.usdz+zip"

    @JvmField
    val MODEL_VND_VALVE_SOURCE_COMPILED_MAP = "model/vnd.valve.source.compiled-map"

    @JvmField
    val MODEL_VND_VTU = "model/vnd.vtu"

    @JvmField
    val MODEL_X3D_FASTINFOSET = "model/x3d+fastinfoset"

    @JvmField
    val MODEL_X3D_VRML = "model/x3d-vrml"

    @JvmField
    val MODEL_X3D_XML = "model/x3d+xml"

    @JvmField
    val MULTIPART_APPLEDOUBLE = "multipart/appledouble"

    @JvmField
    val MULTIPART_BYTERANGES = "multipart/byteranges"

    @JvmField
    val MULTIPART_ENCRYPTED = "multipart/encrypted"

    @JvmField
    val MULTIPART_EXAMPLE = "multipart/example"

    @JvmField
    val MULTIPART_HEADER_SET = "multipart/header-set"

    @JvmField
    val MULTIPART_MULTILINGUAL = "multipart/multilingual"

    @JvmField
    val MULTIPART_RELATED = "multipart/related"

    @JvmField
    val MULTIPART_REPORT = "multipart/report"

    @JvmField
    val MULTIPART_SIGNED = "multipart/signed"

    @JvmField
    val MULTIPART_VND_BINT_MED_PLUS = "multipart/vnd.bint.med-plus"

    @JvmField
    val MULTIPART_VOICE_MESSAGE = "multipart/voice-message"

    @JvmField
    val MULTIPART_X_MIXED_REPLACE = "multipart/x-mixed-replace"

    @JvmField
    val TEXT_1D_INTERLEAVED_PARITYFEC = "text/1d-interleaved-parityfec"

    @JvmField
    val TEXT_CACHE_MANIFEST = "text/cache-manifest"

    @JvmField
    val TEXT_CALENDAR = "text/calendar"

    @JvmField
    val TEXT_CQL = "text/cql"

    @JvmField
    val TEXT_CQL_EXPRESSION = "text/cql-expression"

    @JvmField
    val TEXT_CQL_IDENTIFIER = "text/cql-identifier"

    @JvmField
    val TEXT_CSV_SCHEMA = "text/csv-schema"

    @JvmField
    @Deprecated("Deprecated by RFC6350")
    val TEXT_DIRECTORY = "text/directory"

    @JvmField
    val TEXT_DNS = "text/dns"

    @JvmField
    @Deprecated("Obsoleted", ReplaceWith("TEXT_JAVASCRIPT"), DeprecationLevel.ERROR)
    val TEXT_ECMASCRIPT = "text/ecmascript"

    @JvmField
    val TEXT_ENCAPRTP = "text/encaprtp"

    @JvmField
    val TEXT_EXAMPLE = "text/example"

    @JvmField
    val TEXT_FHIRPATH = "text/fhirpath"

    @JvmField
    val TEXT_FLEXFEC = "text/flexfec"

    @JvmField
    val TEXT_FWDRED = "text/fwdred"

    @JvmField
    val TEXT_GFF3 = "text/gff3"

    @JvmField
    val TEXT_GRAMMAR_REF_LIST = "text/grammar-ref-list"

    @JvmField
    val TEXT_HL7V2 = "text/hl7v2"

    @JvmField
    val TEXT_JCR_CND = "text/jcr-cnd"

    @JvmField
    val TEXT_MIZAR = "text/mizar"

    @JvmField
    val TEXT_N3 = "text/n3"

    @JvmField
    val TEXT_PARAMETERS = "text/parameters"

    @JvmField
    val TEXT_PARITYFEC = "text/parityfec"

    @JvmField
    val TEXT_PROVENANCE_NOTATION = "text/provenance-notation"

    @JvmField
    val TEXT_PRS_FALLENSTEIN_RST = "text/prs.fallenstein.rst"

    @JvmField
    val TEXT_PRS_LINES_TAG = "text/prs.lines.tag"

    @JvmField
    val TEXT_PRS_PROP_LOGIC = "text/prs.prop.logic"

    @JvmField
    val TEXT_PRS_TEXI = "text/prs.texi"

    @JvmField
    val TEXT_RAPTORFEC = "text/raptorfec"

    @JvmField
    val TEXT_RED = "text/RED"

    @JvmField
    val TEXT_RFC822_HEADERS = "text/rfc822-headers"

    @JvmField
    val TEXT_RTF = "text/rtf"

    @JvmField
    val TEXT_RTPLOOPBACK = "text/rtploopback"

    @JvmField
    val TEXT_RTP_ENC_AESCM128 = "text/rtp-enc-aescm128"

    @JvmField
    val TEXT_RTX = "text/rtx"

    @JvmField
    val TEXT_SGML = "text/SGML"

    @JvmField
    val TEXT_SHACLC = "text/shaclc"

    @JvmField
    val TEXT_SHEX = "text/shex"

    @JvmField
    val TEXT_SPDX = "text/spdx"

    @JvmField
    val TEXT_STRINGS = "text/strings"

    @JvmField
    val TEXT_T140 = "text/t140"

    @JvmField
    val TEXT_TROFF = "text/troff"

    @JvmField
    val TEXT_TURTLE = "text/turtle"

    @JvmField
    val TEXT_ULPFEC = "text/ulpfec"

    @JvmField
    val TEXT_URI_LIST = "text/uri-list"

    @JvmField
    val TEXT_VCARD = "text/vcard"

    @JvmField
    val TEXT_VND_A = "text/vnd.a"

    @JvmField
    val TEXT_VND_ABC = "text/vnd.abc"

    @JvmField
    val TEXT_VND_ASCII_ART = "text/vnd.ascii-art"

    @JvmField
    val TEXT_VND_CURL = "text/vnd.curl"

    @JvmField
    val TEXT_VND_DEBIAN_COPYRIGHT = "text/vnd.debian.copyright"

    @JvmField
    val TEXT_VND_DMCLIENTSCRIPT = "text/vnd.DMClientScript"

    @JvmField
    val TEXT_VND_DVB_SUBTITLE = "text/vnd.dvb.subtitle"

    @JvmField
    val TEXT_VND_ESMERTEC_THEME_DESCRIPTOR = "text/vnd.esmertec.theme-descriptor"

    @JvmField
    val TEXT_VND_EXCHANGEABLE = "text/vnd.exchangeable"

    @JvmField
    val TEXT_VND_FAMILYSEARCH_GEDCOM = "text/vnd.familysearch.gedcom"

    @JvmField
    val TEXT_VND_FICLAB_FLT = "text/vnd.ficlab.flt"

    @JvmField
    val TEXT_VND_FLY = "text/vnd.fly"

    @JvmField
    val TEXT_VND_FMI_FLEXSTOR = "text/vnd.fmi.flexstor"

    @JvmField
    val TEXT_VND_GML = "text/vnd.gml"

    @JvmField
    val TEXT_VND_GRAPHVIZ = "text/vnd.graphviz"

    @JvmField
    val TEXT_VND_HANS = "text/vnd.hans"

    @JvmField
    val TEXT_VND_HGL = "text/vnd.hgl"

    @JvmField
    val TEXT_VND_IN3D_3DML = "text/vnd.in3d.3dml"

    @JvmField
    val TEXT_VND_IN3D_SPOT = "text/vnd.in3d.spot"

    @JvmField
    val TEXT_VND_IPTC_NEWSML = "text/vnd.IPTC.NewsML"

    @JvmField
    val TEXT_VND_IPTC_NITF = "text/vnd.IPTC.NITF"

    @JvmField
    val TEXT_VND_LATEX_Z = "text/vnd.latex-z"

    @JvmField
    val TEXT_VND_MOTOROLA_REFLEX = "text/vnd.motorola.reflex"

    @JvmField
    val TEXT_VND_MS_MEDIAPACKAGE = "text/vnd.ms-mediapackage"

    @JvmField
    val TEXT_VND_NET2PHONE_COMMCENTER_COMMAND = "text/vnd.net2phone.commcenter.command"

    @JvmField
    val TEXT_VND_RADISYS_MSML_BASIC_LAYOUT = "text/vnd.radisys.msml-basic-layout"

    @JvmField
    val TEXT_VND_SENX_WARPSCRIPT = "text/vnd.senx.warpscript"

    @JvmField
    @Deprecated("Obsoleted by request", ReplaceWith("NULL"), DeprecationLevel.ERROR)
    val TEXT_VND_SI_URICATALOGUE = "text/vnd.si.uricatalogue"

    @JvmField
    val TEXT_VND_SOSI = "text/vnd.sosi"

    @JvmField
    val TEXT_VND_SUN_J2ME_APP_DESCRIPTOR = "text/vnd.sun.j2me.app-descriptor"

    @JvmField
    val TEXT_VND_TROLLTECH_LINGUIST = "text/vnd.trolltech.linguist"

    @JvmField
    val TEXT_VND_WAP_SI = "text/vnd.wap.si"

    @JvmField
    val TEXT_VND_WAP_SL = "text/vnd.wap.sl"

    @JvmField
    val TEXT_VND_WAP_WML = "text/vnd.wap.wml"

    @JvmField
    val TEXT_VND_WAP_WMLSCRIPT = "text/vnd.wap.wmlscript"

    @JvmField
    val TEXT_VTT = "text/vtt"

    @JvmField
    val TEXT_WGSL = "text/wgsl"

    @JvmField
    val TEXT_XML_EXTERNAL_PARSED_ENTITY = "text/xml-external-parsed-entity"

    @JvmField
    val VIDEO_1D_INTERLEAVED_PARITYFEC = "video/1d-interleaved-parityfec"

    @JvmField
    val VIDEO_3GPP = "video/3gpp"

    @JvmField
    val VIDEO_3GPP2 = "video/3gpp2"

    @JvmField
    val VIDEO_3GPP_TT = "video/3gpp-tt"

    @JvmField
    val VIDEO_AV1 = "video/AV1"

    @JvmField
    val VIDEO_BMPEG = "video/BMPEG"

    @JvmField
    val VIDEO_BT656 = "video/BT656"

    @JvmField
    val VIDEO_CELB = "video/CelB"

    @JvmField
    val VIDEO_DV = "video/DV"

    @JvmField
    val VIDEO_ENCAPRTP = "video/encaprtp"

    @JvmField
    val VIDEO_EVC = "video/evc"

    @JvmField
    val VIDEO_EXAMPLE = "video/example"

    @JvmField
    val VIDEO_FFV1 = "video/FFV1"

    @JvmField
    val VIDEO_FLEXFEC = "video/flexfec"

    @JvmField
    val VIDEO_H261 = "video/H261"

    @JvmField
    val VIDEO_H263 = "video/H263"

    @JvmField
    val VIDEO_H263_1998 = "video/H263-1998"

    @JvmField
    val VIDEO_H263_2000 = "video/H263-2000"

    @JvmField
    val VIDEO_H264 = "video/H264"

    @JvmField
    val VIDEO_H264_RCDO = "video/H264-RCDO"

    @JvmField
    val VIDEO_H264_SVC = "video/H264-SVC"

    @JvmField
    val VIDEO_H265 = "video/H265"

    @JvmField
    val VIDEO_H266 = "video/H266"

    @JvmField
    val VIDEO_ISO_SEGMENT = "video/iso.segment"

    @JvmField
    val VIDEO_JPEG = "video/JPEG"

    @JvmField
    val VIDEO_JPEG2000 = "video/jpeg2000"

    @JvmField
    val VIDEO_JXSV = "video/jxsv"

    @JvmField
    val VIDEO_MATROSKA = "video/matroska"

    @JvmField
    val VIDEO_MATROSKA_3D = "video/matroska-3d"

    @JvmField
    val VIDEO_MJ2 = "video/mj2"

    @JvmField
    val VIDEO_MP1S = "video/MP1S"

    @JvmField
    val VIDEO_MP2P = "video/MP2P"

    @JvmField
    val VIDEO_MP2T = "video/MP2T"

    @JvmField
    val VIDEO_MP4V_ES = "video/MP4V-ES"

    @JvmField
    val VIDEO_MPEG4_GENERIC = "video/mpeg4-generic"

    @JvmField
    val VIDEO_MPV = "video/MPV"

    @JvmField
    val VIDEO_NV = "video/nv"

    @JvmField
    val VIDEO_PARITYFEC = "video/parityfec"

    @JvmField
    val VIDEO_POINTER = "video/pointer"

    @JvmField
    val VIDEO_RAPTORFEC = "video/raptorfec"

    @JvmField
    val VIDEO_RAW = "video/raw"

    @JvmField
    val VIDEO_RTPLOOPBACK = "video/rtploopback"

    @JvmField
    val VIDEO_RTP_ENC_AESCM128 = "video/rtp-enc-aescm128"

    @JvmField
    val VIDEO_RTX = "video/rtx"

    @JvmField
    val VIDEO_SCIP = "video/scip"

    @JvmField
    val VIDEO_SMPTE291 = "video/smpte291"

    @JvmField
    val VIDEO_SMPTE292M = "video/SMPTE292M"

    @JvmField
    val VIDEO_ULPFEC = "video/ulpfec"

    @JvmField
    val VIDEO_VC1 = "video/vc1"

    @JvmField
    val VIDEO_VC2 = "video/vc2"

    @JvmField
    val VIDEO_VND_CCTV = "video/vnd.CCTV"

    @JvmField
    val VIDEO_VND_DECE_HD = "video/vnd.dece.hd"

    @JvmField
    val VIDEO_VND_DECE_MOBILE = "video/vnd.dece.mobile"

    @JvmField
    val VIDEO_VND_DECE_MP4 = "video/vnd.dece.mp4"

    @JvmField
    val VIDEO_VND_DECE_PD = "video/vnd.dece.pd"

    @JvmField
    val VIDEO_VND_DECE_SD = "video/vnd.dece.sd"

    @JvmField
    val VIDEO_VND_DECE_VIDEO = "video/vnd.dece.video"

    @JvmField
    val VIDEO_VND_DIRECTV_MPEG = "video/vnd.directv.mpeg"

    @JvmField
    val VIDEO_VND_DIRECTV_MPEG_TTS = "video/vnd.directv.mpeg-tts"

    @JvmField
    val VIDEO_VND_DLNA_MPEG_TTS = "video/vnd.dlna.mpeg-tts"

    @JvmField
    val VIDEO_VND_DVB_FILE = "video/vnd.dvb.file"

    @JvmField
    val VIDEO_VND_FVT = "video/vnd.fvt"

    @JvmField
    val VIDEO_VND_HNS_VIDEO = "video/vnd.hns.video"

    @JvmField
    val VIDEO_VND_IPTVFORUM_1DPARITYFEC_1010 = "video/vnd.iptvforum.1dparityfec-1010"

    @JvmField
    val VIDEO_VND_IPTVFORUM_1DPARITYFEC_2005 = "video/vnd.iptvforum.1dparityfec-2005"

    @JvmField
    val VIDEO_VND_IPTVFORUM_2DPARITYFEC_1010 = "video/vnd.iptvforum.2dparityfec-1010"

    @JvmField
    val VIDEO_VND_IPTVFORUM_2DPARITYFEC_2005 = "video/vnd.iptvforum.2dparityfec-2005"

    @JvmField
    val VIDEO_VND_IPTVFORUM_TTSAVC = "video/vnd.iptvforum.ttsavc"

    @JvmField
    val VIDEO_VND_IPTVFORUM_TTSMPEG2 = "video/vnd.iptvforum.ttsmpeg2"

    @JvmField
    val VIDEO_VND_MOTOROLA_VIDEO = "video/vnd.motorola.video"

    @JvmField
    val VIDEO_VND_MOTOROLA_VIDEOP = "video/vnd.motorola.videop"

    @JvmField
    val VIDEO_VND_MPEGURL = "video/vnd.mpegurl"

    @JvmField
    val VIDEO_VND_MS_PLAYREADY_MEDIA_PYV = "video/vnd.ms-playready.media.pyv"

    @JvmField
    val VIDEO_VND_NOKIA_INTERLEAVED_MULTIMEDIA = "video/vnd.nokia.interleaved-multimedia"

    @JvmField
    val VIDEO_VND_NOKIA_MP4VR = "video/vnd.nokia.mp4vr"

    @JvmField
    val VIDEO_VND_NOKIA_VIDEOVOIP = "video/vnd.nokia.videovoip"

    @JvmField
    val VIDEO_VND_OBJECTVIDEO = "video/vnd.objectvideo"

    @JvmField
    val VIDEO_VND_RADGAMETTOOLS_BINK = "video/vnd.radgamettools.bink"

    @JvmField
    val VIDEO_VND_RADGAMETTOOLS_SMACKER = "video/vnd.radgamettools.smacker"

    @JvmField
    val VIDEO_VND_SEALEDMEDIA_SOFTSEAL_MOV = "video/vnd.sealedmedia.softseal.mov"

    @JvmField
    val VIDEO_VND_SEALED_MPEG1 = "video/vnd.sealed.mpeg1"

    @JvmField
    val VIDEO_VND_SEALED_MPEG4 = "video/vnd.sealed.mpeg4"

    @JvmField
    val VIDEO_VND_SEALED_SWF = "video/vnd.sealed.swf"

    @JvmField
    val VIDEO_VND_UVVU_MP4 = "video/vnd.uvvu.mp4"

    @JvmField
    val VIDEO_VND_VIVO = "video/vnd.vivo"

    @JvmField
    val VIDEO_VND_YOUTUBE_YT = "video/vnd.youtube.yt"

    @JvmField
    val VIDEO_VP8 = "video/VP8"

    @JvmField
    val VIDEO_VP9 = "video/VP9"

    @JvmField
    val APPLICATION_WILDCARD = "application/*"

    @JvmField
    val APPLICATION_IOTA_MMC_WBXML = "application/iota.mmc-wbxml"

    @JvmField
    val APPLICATION_IOTA_MMC_XML = "application/iota.mmc-xml"

    @JvmField
    val APPLICATION_JAVA_VM = "application/java-vm"

    @JvmField
    val APPLICATION_OMA_DIRECTORY_XML = "application/oma-directory+xml"

    @JvmField
    val APPLICATION_RSS_XML = "application/rss+xml"

    @JvmField
    val APPLICATION_VNC_CMCC_DCD_XML = "application/vnc.cmcc.dcd+xml"

    @JvmField
    val APPLICATION_VND_ANDROID_PACKAGE_ARCHIVE = "application/vnd.android.package-archive"

    @JvmField
    val APPLICATION_VND_CMCC_BOMBING_WBXML = "application/vnd.cmcc.bombing+wbxml"

    @JvmField
    val APPLICATION_VND_CMCC_SETTING_WBXML = "application/vnd.cmcc.setting+wbxml"

    @JvmField
    val APPLICATION_VND_DOCOMO_PF = "application/vnd.docomo.pf"

    @JvmField
    val APPLICATION_VND_DOCOMO_PF2 = "application/vnd.docomo.pf2"

    @JvmField
    val APPLICATION_VND_DOCOMO_UB = "application/vnd.docomo.ub"

    @JvmField
    val APPLICATION_VND_GOOGLE_APPS_DOCUMENT = "application/vnd.google-apps.document"

    @JvmField
    val APPLICATION_VND_GOOGLE_APPS_DRAWING = "application/vnd.google-apps.drawing"

    @JvmField
    val APPLICATION_VND_GOOGLE_APPS_PRESENTATION = "application/vnd.google-apps.presentation"

    @JvmField
    val APPLICATION_VND_GOOGLE_APPS_SPREADSHEET = "application/vnd.google-apps.spreadsheet"

    @JvmField
    val APPLICATION_VND_MOTOROLA_SCREEN3_GZIP = "application/vnd.motorola.screen3+gzip"

    @JvmField
    val APPLICATION_VND_MOTOROLA_SCREEN3_XML = "application/vnd.motorola.screen3+xml"

    @JvmField
    val APPLICATION_VND_NOKIA_IPDC_PURCHASE_RESPONSE = "application/vnd.nokia.ipdc-purchase-response"

    @JvmField
    val APPLICATION_VND_NOKIA_SYNCSET_WBXML = "application/vnd.nokia.syncset+wbxml"

    @JvmField
    val APPLICATION_VND_OMA_DD_XML = "application/vnd.oma.dd+xml"

    @JvmField
    val APPLICATION_VND_OMA_DRM_CONTENT = "application/vnd.oma.drm.content"

    @JvmField
    val APPLICATION_VND_OMA_DRM_MESSAGE = "application/vnd.oma.drm.message"

    @JvmField
    val APPLICATION_VND_OMA_DRM_RIGHTS_WBXML = "application/vnd.oma.drm.rights+wbxml"

    @JvmField
    val APPLICATION_VND_OMA_DRM_RIGHTS_XML = "application/vnd.oma.drm.rights+xml"

    @JvmField
    val APPLICATION_VND_OMA_DRM_ROAP_TRIGGER_WBXML = "application/vnd.oma.drm.roap-trigger+wbxml"

    @JvmField
    val APPLICATION_VND_PHONECOM_MMC_WBXML = "application/vnd.phonecom.mmc-wbxml"

    @JvmField
    val APPLICATION_VND_STARDIVISION_CALC = "application/vnd.stardivision.calc"

    @JvmField
    val APPLICATION_VND_STARDIVISION_DRAW = "application/vnd.stardivision.draw"

    @JvmField
    val APPLICATION_VND_STARDIVISION_IMPRESS = "application/vnd.stardivision.impress"

    @JvmField
    val APPLICATION_VND_STARDIVISION_WRITER = "application/vnd.stardivision.writer"

    @JvmField
    val APPLICATION_VND_STARDIVISION_WRITER_GLOBAL = "application/vnd.stardivision.writer-global"

    @JvmField
    val APPLICATION_VND_SUN_XML_CALC = "application/vnd.sun.xml.calc"

    @JvmField
    val APPLICATION_VND_SUN_XML_CALC_TEMPLATE = "application/vnd.sun.xml.calc.template"

    @JvmField
    val APPLICATION_VND_SUN_XML_DRAW = "application/vnd.sun.xml.draw"

    @JvmField
    val APPLICATION_VND_SUN_XML_DRAW_TEMPLATE = "application/vnd.sun.xml.draw.template"

    @JvmField
    val APPLICATION_VND_SUN_XML_IMPRESS = "application/vnd.sun.xml.impress"

    @JvmField
    val APPLICATION_VND_SUN_XML_IMPRESS_TEMPLATE = "application/vnd.sun.xml.impress.template"

    @JvmField
    val APPLICATION_VND_SUN_XML_WRITER = "application/vnd.sun.xml.writer"

    @JvmField
    val APPLICATION_VND_SUN_XML_WRITER_GLOBAL = "application/vnd.sun.xml.writer.global"

    @JvmField
    val APPLICATION_VND_SUN_XML_WRITER_TEMPLATE = "application/vnd.sun.xml.writer.template"

    @JvmField
    val APPLICATION_VND_SYNCML_WBXML = "application/vnd.syncml+wbxml"

    @JvmField
    val APPLICATION_VND_SYNCML_NOTIFICATION = "application/vnd.syncml.notification"

    @JvmField
    val APPLICATION_VND_UPLANET_PROVISIONING_STATUS_URI = "application/vnd.uplanet.provisioning-status-uri"

    @JvmField
    val APPLICATION_VND_WAP_CERT_RESPONSE = "application/vnd.wap.cert-response"

    @JvmField
    val APPLICATION_VND_WAP_COC = "application/vnd.wap.coc"

    @JvmField
    val APPLICATION_VND_WAP_CONNECTIVITY_WBXML = "application/vnd.wap.connectivity-wbxml"

    @JvmField
    val APPLICATION_VND_WAP_EMN_WBXML = "application/vnd.wap.emn+wbxml"

    @JvmField
    val APPLICATION_VND_WAP_HASHED_CERTIFICATE = "application/vnd.wap.hashed-certificate"

    @JvmField
    val APPLICATION_VND_WAP_LOC_XML = "application/vnd.wap.loc+xml"

    @JvmField
    val APPLICATION_VND_WAP_LOCC_WBXML = "application/vnd.wap.locc+wbxml"

    @JvmField
    val APPLICATION_VND_WAP_MMS_MESSAGE = "application/vnd.wap.mms-message"

    @JvmField
    val APPLICATION_VND_WAP_MULTIPART_WILDCARD = "application/vnd.wap.multipart.*"

    @JvmField
    val APPLICATION_VND_WAP_MULTIPART_ALTERNATIVE = "application/vnd.wap.multipart.alternative"

    @JvmField
    val APPLICATION_VND_WAP_MULTIPART_BYTERANGES = "application/vnd.wap.multipart.byteranges"

    @JvmField
    val APPLICATION_VND_WAP_MULTIPART_FORM_DATA = "application/vnd.wap.multipart.form-data"

    @JvmField
    val APPLICATION_VND_WAP_MULTIPART_MIXED = "application/vnd.wap.multipart.mixed"

    @JvmField
    val APPLICATION_VND_WAP_MULTIPART_RELATED = "application/vnd.wap.multipart.related"

    @JvmField
    val APPLICATION_VND_WAP_ROLLOVER_CERTIFICATE = "application/vnd.wap.rollover-certificate"

    @JvmField
    val APPLICATION_VND_WAP_SIA = "application/vnd.wap.sia"

    @JvmField
    val APPLICATION_VND_WAP_SIGNED_CERTIFICATE = "application/vnd.wap.signed-certificate"

    @JvmField
    val APPLICATION_VND_WAP_UAPROF = "application/vnd.wap.uaprof"

    @JvmField
    val APPLICATION_VND_WAP_WTA_EVENTC = "application/vnd.wap.wta-eventc"

    @JvmField
    val APPLICATION_VND_WAP_WTLS_CA_CERTIFICATE = "application/vnd.wap.wtls-ca-certificate"

    @JvmField
    val APPLICATION_VND_WAP_WTLS_USER_CERTIFICATE = "application/vnd.wap.wtls-user-certificate"

    @JvmField
    val APPLICATION_VND_WAP_XHTML_XML = "application/vnd.wap.xhtml+xml"

    @JvmField
    val APPLICATION_VND_WV_CSP_CIR = "application/vnd.wv.csp.cir"

    @JvmField
    val APPLICATION_WML_XML = "application/wml+xml"

    @JvmField
    val APPLICATION_X_ABIWORD = "application/x-abiword"

    @JvmField
    val APPLICATION_X_APPLE_DISKIMAGE = "application/x-apple-diskimage"

    @JvmField
    val APPLICATION_X_DEB = "application/x-deb"

    @JvmField
    val APPLICATION_X_DEBIAN_PACKAGE = "application/x-debian-package"

    @JvmField
    val APPLICATION_X_FONT = "application/x-font"

    @JvmField
    val APPLICATION_X_FONT_TTF = "application/x-font-ttf"

    @JvmField
    val APPLICATION_X_FONT_WOFF = "application/x-font-woff"

    @JvmField
    val APPLICATION_X_HDMLC = "application/x-hdmlc"

    @JvmField
    val APPLICATION_X_ISO9660_IMAGE = "application/x-iso9660-image"

    @JvmField
    val APPLICATION_X_JAVASCRIPT = "application/x-javascript"

    @JvmField
    val APPLICATION_X_KPRESENTER = "application/x-kpresenter"

    @JvmField
    val APPLICATION_X_KSPREAD = "application/x-kspread"

    @JvmField
    val APPLICATION_X_KWORD = "application/x-kword"

    @JvmField
    val APPLICATION_X_LHA = "application/x-lha"

    @JvmField
    val APPLICATION_X_LZH = "application/x-lzh"

    @JvmField
    val APPLICATION_X_LZX = "application/x-lzx"

    @JvmField
    val APPLICATION_X_OBJECT = "application/x-object"

    @JvmField
    val APPLICATION_X_PKCS12 = "application/x-pkcs12"

    @JvmField
    val APPLICATION_X_PKCS7_CERTIFICATES = "application/x-pkcs7-certificates"

    @JvmField
    val APPLICATION_X_PKCS7_CERTREQRESP = "application/x-pkcs7-certreqresp"

    @JvmField
    val APPLICATION_X_PKCS7_CRL = "application/x-pkcs7-crl"

    @JvmField
    val APPLICATION_X_PKCS7_MIME = "application/x-pkcs7-mime"

    @JvmField
    val APPLICATION_X_PKCS7_SIGNATURE = "application/x-pkcs7-signature"

    @JvmField
    val APPLICATION_X_QUICKTIMEPLAYER = "application/x-quicktimeplayer"

    @JvmField
    val APPLICATION_X_RAR_COMPRESSED = "application/x-rar-compressed"

    @JvmField
    val APPLICATION_X_SHOCKWAVE_FLASH = "application/x-shockwave-flash"

    @JvmField
    val APPLICATION_X_STUFFIT = "application/x-stuffit"

    @JvmField
    val APPLICATION_X_WEBARCHIVE = "application/x-webarchive"

    @JvmField
    val APPLICATION_X_WEBARCHIVE_XML = "application/x-webarchive-xml"

    @JvmField
    val APPLICATION_X_X509_USER_CERT = "application/x-x509-user-cert"

    @JvmField
    val APPLICATION_X_X968_CA_CERT = "application/x-x968-ca-cert"

    @JvmField
    val APPLICATION_X_X968_CROSS_CERT = "application/x-x968-cross-cert"

    @JvmField
    val APPLICATION_X_X968_USER_CERT = "application/x-x968-user-cert"

    @JvmField
    val AUDIO_WILDCARD = "audio/*"

    @JvmField
    val IMAGE_WILDCARD = "image/*"

    @JvmField
    val IMAGE_X_UP_WPNG = "image/x-up-wpng"

    @JvmField
    val INODE_DIRECTORY = "inode/directory"

    @JvmField
    val MULTIPART_WILDCARD = "multipart/*"

    @JvmField
    val MULTIPART_ALTERNATIVE = "multipart/alternative"

    @JvmField
    val MULTIPART_BYTERANTES = "multipart/byterantes"

    @JvmField
    val MULTIPART_MIXED = "multipart/mixed"

    @JvmField
    val TEXT_WILDCARD = "text/*"

    @JvmField
    val TEXT_DIRECTORY_PROFILE_VCARD = "text/directory;profile=vCard"

    @JvmField
    val TEXT_VND_WAP_CO = "text/vnd.wap.co"

    @JvmField
    val TEXT_VND_WAP_CONNECTIVITY_XML = "text/vnd.wap.connectivity-xml"

    @JvmField
    val TEXT_VND_WAP_EMN_XML = "text/vnd.wap.emn+xml"

    @JvmField
    val TEXT_VND_WAP_WTA_EVENT = "text/vnd.wap.wta-event"

    @JvmField
    val TEXT_X_C_HDR = "text/x-c++hdr"

    @JvmField
    val TEXT_X_C_SRC = "text/x-c++src"

    @JvmField
    val TEXT_X_CHDR = "text/x-chdr"

    @JvmField
    val TEXT_X_CSH = "text/x-csh"

    @JvmField
    val TEXT_X_CSRC = "text/x-csrc"

    @JvmField
    val TEXT_X_DSRC = "text/x-dsrc"

    @JvmField
    val TEXT_X_HASKELL = "text/x-haskell"

    @JvmField
    val TEXT_X_HDML = "text/x-hdml"

    @JvmField
    val TEXT_X_JAVA = "text/x-java"

    @JvmField
    val TEXT_X_LITERATE_HASKELL = "text/x-literate-haskell"

    @JvmField
    val TEXT_X_PASCAL = "text/x-pascal"

    @JvmField
    val TEXT_X_TCL = "text/x-tcl"

    @JvmField
    val TEXT_X_TEX = "text/x-tex"

    @JvmField
    val TEXT_X_TTML = "text/x-ttml"

    @JvmField
    val VIDEO_WILDCARD = "video/*"

    @JvmField
    val VND_ANDROID_DOCUMENT_DIRECTORY = "vnd.android.document/directory"

    @JvmField
    val X_WAP_MULTIPART_VND_UPLANET_HEADER_SET = "x-wap.multipart/vnd.uplanet.header-set"

    @JvmField
    val APPLICATION_ANNODEX = "application/annodex"

    @JvmField
    val APPLICATION_ATOMSERV_XML = "application/atomserv+xml"

    @JvmField
    val APPLICATION_BBOLIN = "application/bbolin"

    @JvmField
    val APPLICATION_CU_SEEME = "application/cu-seeme"

    @JvmField
    val APPLICATION_DOCBOOK_XML = "application/docbook+xml"

    @JvmField
    val APPLICATION_DSPTYPE = "application/dsptype"

    @JvmField
    val APPLICATION_FUTURESPLASH = "application/futuresplash"

    @JvmField
    val APPLICATION_GHOSTVIEW = "application/ghostview"

    @JvmField
    val APPLICATION_HTA = "application/hta"

    @JvmField
    val APPLICATION_JAVA_SERIALIZED_OBJECT = "application/java-serialized-object"

    @JvmField
    val APPLICATION_M3G = "application/m3g"

    @JvmField
    val APPLICATION_MAC_COMPACTPRO = "application/mac-compactpro"

    @JvmField
    val APPLICATION_MS_TNEF = "application/ms-tnef"

    @JvmField
    val APPLICATION_MSACCESS = "application/msaccess"

    @JvmField
    val APPLICATION_NEWS_MESSAGE_ID = "application/news-message-id"

    @JvmField
    val APPLICATION_ONENOTE = "application/onenote"

    @JvmField
    val APPLICATION_PICS_RULES = "application/pics-rules"

    @JvmField
    val APPLICATION_SLA = "application/sla"

    @JvmField
    val APPLICATION_VND_ANSER_WEB_FUNDS_TRANSFER_INITIATION = "application/vnd.anser-web-funds-transfer-initiation"

    @JvmField
    val APPLICATION_VND_COMSOCALLER = "application/vnd.comsocaller"

    @JvmField
    val APPLICATION_VND_FDF = "application/vnd.fdf"

    @JvmField
    val APPLICATION_VND_MS_PKI_SECCAT = "application/vnd.ms-pki.seccat"

    @JvmField
    val APPLICATION_VND_RIM_COD = "application/vnd.rim.cod"

    @JvmField
    val APPLICATION_VND_STARDIVISION_CHART = "application/vnd.stardivision.chart"

    @JvmField
    val APPLICATION_VND_STARDIVISION_MATH = "application/vnd.stardivision.math"

    @JvmField
    val APPLICATION_VND_SYMBIAN_INSTALL = "application/vnd.symbian.install"

    @JvmField
    val APPLICATION_VND_TVE_TRIGGER = "application/vnd.tve-trigger"

    @JvmField
    val APPLICATION_VND_WORDPERFECT5_1 = "application/vnd.wordperfect5.1"

    @JvmField
    val APPLICATION_X_123 = "application/x-123"

    @JvmField
    val APPLICATION_X_BITTORRENT = "application/x-bittorrent"

    @JvmField
    val APPLICATION_X_CAB = "application/x-cab"

    @JvmField
    val APPLICATION_X_CBR = "application/x-cbr"

    @JvmField
    val APPLICATION_X_CBZ = "application/x-cbz"

    @JvmField
    val APPLICATION_X_CDF = "application/x-cdf"

    @JvmField
    val APPLICATION_X_CDLINK = "application/x-cdlink"

    @JvmField
    val APPLICATION_X_CHESS_PGN = "application/x-chess-pgn"

    @JvmField
    val APPLICATION_X_COMSOL = "application/x-comsol"

    @JvmField
    val APPLICATION_X_CORE = "application/x-core"

    @JvmField
    val APPLICATION_X_CSH = "application/x-csh"

    @JvmField
    val APPLICATION_X_DIRECTOR = "application/x-director"

    @JvmField
    val APPLICATION_X_DMS = "application/x-dms"

    @JvmField
    val APPLICATION_X_DOOM = "application/x-doom"

    @JvmField
    val APPLICATION_X_EXECUTABLE = "application/x-executable"

    @JvmField
    val APPLICATION_X_FONT_PCF = "application/x-font-pcf"

    @JvmField
    val APPLICATION_X_FREEMIND = "application/x-freemind"

    @JvmField
    val APPLICATION_X_FUTURESPLASH = "application/x-futuresplash"

    @JvmField
    val APPLICATION_X_GANTTPROJECT = "application/x-ganttproject"

    @JvmField
    val APPLICATION_X_GNUMERIC = "application/x-gnumeric"

    @JvmField
    val APPLICATION_X_GO_SGF = "application/x-go-sgf"

    @JvmField
    val APPLICATION_X_GRAPHING_CALCULATOR = "application/x-graphing-calculator"

    @JvmField
    val APPLICATION_X_GTAR_COMPRESSED = "application/x-gtar-compressed"

    @JvmField
    val APPLICATION_X_HWP = "application/x-hwp"

    @JvmField
    val APPLICATION_X_ICA = "application/x-ica"

    @JvmField
    val APPLICATION_X_INFO = "application/x-info"

    @JvmField
    val APPLICATION_X_INTERNET_SIGNUP = "application/x-internet-signup"

    @JvmField
    val APPLICATION_X_IPHONE = "application/x-iphone"

    @JvmField
    val APPLICATION_X_JAM = "application/x-jam"

    @JvmField
    val APPLICATION_X_JAVA_APPLET = "application/x-java-applet"

    @JvmField
    val APPLICATION_X_JAVA_BEAN = "application/x-java-bean"

    @JvmField
    val APPLICATION_X_JAVA_JNLP_FILE = "application/x-java-jnlp-file"

    @JvmField
    val APPLICATION_X_JMOL = "application/x-jmol"

    @JvmField
    val APPLICATION_X_KCHART = "application/x-kchart"

    @JvmField
    val APPLICATION_X_KDELNK = "application/x-kdelnk"

    @JvmField
    val APPLICATION_X_KILLUSTRATOR = "application/x-killustrator"

    @JvmField
    val APPLICATION_X_KOAN = "application/x-koan"

    @JvmField
    val APPLICATION_X_LYX = "application/x-lyx"

    @JvmField
    val APPLICATION_X_MAKER = "application/x-maker"

    @JvmField
    val APPLICATION_X_MIF = "application/x-mif"

    @JvmField
    val APPLICATION_X_MPEGURL = "application/x-mpegURL"

    @JvmField
    val APPLICATION_X_MS_APPLICATION = "application/x-ms-application"

    @JvmField
    val APPLICATION_X_MS_MANIFEST = "application/x-ms-manifest"

    @JvmField
    val APPLICATION_X_MS_WMD = "application/x-ms-wmd"

    @JvmField
    val APPLICATION_X_MS_WMZ = "application/x-ms-wmz"

    @JvmField
    val APPLICATION_X_MSDOS_PROGRAM = "application/x-msdos-program"

    @JvmField
    val APPLICATION_X_MSI = "application/x-msi"

    @JvmField
    val APPLICATION_X_NS_PROXY_AUTOCONFIG = "application/x-ns-proxy-autoconfig"

    @JvmField
    val APPLICATION_X_NWC = "application/x-nwc"

    @JvmField
    val APPLICATION_X_OZ_APPLICATION = "application/x-oz-application"

    @JvmField
    val APPLICATION_X_PYTHON_CODE = "application/x-python-code"

    @JvmField
    val APPLICATION_X_QGIS = "application/x-qgis"

    @JvmField
    val APPLICATION_X_RDP = "application/x-rdp"

    @JvmField
    val APPLICATION_X_REDHAT_PACKAGE_MANAGER = "application/x-redhat-package-manager"

    @JvmField
    val APPLICATION_X_RSS_XML = "application/x-rss+xml"

    @JvmField
    val APPLICATION_X_RUBY = "application/x-ruby"

    @JvmField
    val APPLICATION_X_RX = "application/x-rx"

    @JvmField
    val APPLICATION_X_SCILAB = "application/x-scilab"

    @JvmField
    val APPLICATION_X_SCILAB_XCOS = "application/x-scilab-xcos"

    @JvmField
    val APPLICATION_X_SH = "application/x-sh"

    @JvmField
    val APPLICATION_X_SHELLSCRIPT = "application/x-shellscript"

    @JvmField
    val APPLICATION_X_SILVERLIGHT = "application/x-silverlight"

    @JvmField
    val APPLICATION_X_SQL = "application/x-sql"

    @JvmField
    val APPLICATION_X_TCL = "application/x-tcl"

    @JvmField
    val APPLICATION_X_TEX_GF = "application/x-tex-gf"

    @JvmField
    val APPLICATION_X_TEX_PK = "application/x-tex-pk"

    @JvmField
    val APPLICATION_X_TRASH = "application/x-trash"

    @JvmField
    val APPLICATION_X_VIDEOLAN = "application/x-videolan"

    @JvmField
    val APPLICATION_X_WINGZ = "application/x-wingz"

    @JvmField
    val APPLICATION_X_XCF = "application/x-xcf"

    @JvmField
    val APPLICATION_X_XFIG = "application/x-xfig"

    @JvmField
    val APPLICATION_X_XPINSTALL = "application/x-xpinstall"

    @JvmField
    val APPLICATION_X_XZ = "application/x-xz"

    @JvmField
    val APPLICATION_XSPF_XML = "application/xspf+xml"

    @JvmField
    val AUDIO_AMR = "audio/amr"

    @JvmField
    val AUDIO_ANNODEX = "audio/annodex"

    @JvmField
    val AUDIO_CSOUND = "audio/csound"

    @JvmField
    val AUDIO_G_722_1 = "audio/g.722.1"

    @JvmField
    val AUDIO_MIDI = "audio/midi"

    @JvmField
    val AUDIO_MPEGURL = "audio/mpegurl"

    @JvmField
    val AUDIO_X_GSM = "audio/x-gsm"

    @JvmField
    val AUDIO_X_MPEGURL = "audio/x-mpegurl"

    @JvmField
    val AUDIO_X_MS_WAX = "audio/x-ms-wax"

    @JvmField
    val AUDIO_X_MS_WMA = "audio/x-ms-wma"

    @JvmField
    val AUDIO_X_PN_REALAUDIO = "audio/x-pn-realaudio"

    @JvmField
    val AUDIO_X_PN_REALAUDIO_PLUGIN = "audio/x-pn-realaudio-plugin"

    @JvmField
    val AUDIO_X_REALAUDIO = "audio/x-realaudio"

    @JvmField
    val AUDIO_X_SCPLS = "audio/x-scpls"

    @JvmField
    val AUDIO_X_SD2 = "audio/x-sd2"

    @JvmField
    val CHEMICAL_X_ALCHEMY = "chemical/x-alchemy"

    @JvmField
    val CHEMICAL_X_CACHE = "chemical/x-cache"

    @JvmField
    val CHEMICAL_X_CACHE_CSF = "chemical/x-cache-csf"

    @JvmField
    val CHEMICAL_X_CACTVS_BINARY = "chemical/x-cactvs-binary"

    @JvmField
    val CHEMICAL_X_CDX = "chemical/x-cdx"

    @JvmField
    val CHEMICAL_X_CERIUS = "chemical/x-cerius"

    @JvmField
    val CHEMICAL_X_CHEM3D = "chemical/x-chem3d"

    @JvmField
    val CHEMICAL_X_CHEMDRAW = "chemical/x-chemdraw"

    @JvmField
    val CHEMICAL_X_CIF = "chemical/x-cif"

    @JvmField
    val CHEMICAL_X_CMDF = "chemical/x-cmdf"

    @JvmField
    val CHEMICAL_X_CML = "chemical/x-cml"

    @JvmField
    val CHEMICAL_X_COMPASS = "chemical/x-compass"

    @JvmField
    val CHEMICAL_X_CROSSFIRE = "chemical/x-crossfire"

    @JvmField
    val CHEMICAL_X_CSML = "chemical/x-csml"

    @JvmField
    val CHEMICAL_X_CTX = "chemical/x-ctx"

    @JvmField
    val CHEMICAL_X_CXF = "chemical/x-cxf"

    @JvmField
    val CHEMICAL_X_EMBL_DL_NUCLEOTIDE = "chemical/x-embl-dl-nucleotide"

    @JvmField
    val CHEMICAL_X_GALACTIC_SPC = "chemical/x-galactic-spc"

    @JvmField
    val CHEMICAL_X_GAMESS_INPUT = "chemical/x-gamess-input"

    @JvmField
    val CHEMICAL_X_GAUSSIAN_CHECKPOINT = "chemical/x-gaussian-checkpoint"

    @JvmField
    val CHEMICAL_X_GAUSSIAN_CUBE = "chemical/x-gaussian-cube"

    @JvmField
    val CHEMICAL_X_GAUSSIAN_INPUT = "chemical/x-gaussian-input"

    @JvmField
    val CHEMICAL_X_GAUSSIAN_LOG = "chemical/x-gaussian-log"

    @JvmField
    val CHEMICAL_X_GCG8_SEQUENCE = "chemical/x-gcg8-sequence"

    @JvmField
    val CHEMICAL_X_GENBANK = "chemical/x-genbank"

    @JvmField
    val CHEMICAL_X_HIN = "chemical/x-hin"

    @JvmField
    val CHEMICAL_X_ISOSTAR = "chemical/x-isostar"

    @JvmField
    val CHEMICAL_X_JCAMP_DX = "chemical/x-jcamp-dx"

    @JvmField
    val CHEMICAL_X_KINEMAGE = "chemical/x-kinemage"

    @JvmField
    val CHEMICAL_X_MACMOLECULE = "chemical/x-macmolecule"

    @JvmField
    val CHEMICAL_X_MACROMODEL_INPUT = "chemical/x-macromodel-input"

    @JvmField
    val CHEMICAL_X_MDL_MOLFILE = "chemical/x-mdl-molfile"

    @JvmField
    val CHEMICAL_X_MDL_RDFILE = "chemical/x-mdl-rdfile"

    @JvmField
    val CHEMICAL_X_MDL_RXNFILE = "chemical/x-mdl-rxnfile"

    @JvmField
    val CHEMICAL_X_MDL_SDFILE = "chemical/x-mdl-sdfile"

    @JvmField
    val CHEMICAL_X_MDL_TGF = "chemical/x-mdl-tgf"

    @JvmField
    val CHEMICAL_X_MMCIF = "chemical/x-mmcif"

    @JvmField
    val CHEMICAL_X_MOL2 = "chemical/x-mol2"

    @JvmField
    val CHEMICAL_X_MOLCONN_Z = "chemical/x-molconn-Z"

    @JvmField
    val CHEMICAL_X_MOPAC_GRAPH = "chemical/x-mopac-graph"

    @JvmField
    val CHEMICAL_X_MOPAC_INPUT = "chemical/x-mopac-input"

    @JvmField
    val CHEMICAL_X_MOPAC_OUT = "chemical/x-mopac-out"

    @JvmField
    val CHEMICAL_X_MOPAC_VIB = "chemical/x-mopac-vib"

    @JvmField
    val CHEMICAL_X_NCBI_ASN1 = "chemical/x-ncbi-asn1"

    @JvmField
    val CHEMICAL_X_NCBI_ASN1_ASCII = "chemical/x-ncbi-asn1-ascii"

    @JvmField
    val CHEMICAL_X_NCBI_ASN1_BINARY = "chemical/x-ncbi-asn1-binary"

    @JvmField
    val CHEMICAL_X_NCBI_ASN1_SPEC = "chemical/x-ncbi-asn1-spec"

    @JvmField
    val CHEMICAL_X_PDB = "chemical/x-pdb"

    @JvmField
    val CHEMICAL_X_ROSDAL = "chemical/x-rosdal"

    @JvmField
    val CHEMICAL_X_SWISSPROT = "chemical/x-swissprot"

    @JvmField
    val CHEMICAL_X_VAMAS_ISO14976 = "chemical/x-vamas-iso14976"

    @JvmField
    val CHEMICAL_X_VMD = "chemical/x-vmd"

    @JvmField
    val CHEMICAL_X_XTEL = "chemical/x-xtel"

    @JvmField
    val CHEMICAL_X_XYZ = "chemical/x-xyz"

    @JvmField
    val IMAGE_PCX = "image/pcx"

    @JvmField
    val IMAGE_X_CANON_CR2 = "image/x-canon-cr2"

    @JvmField
    val IMAGE_X_CANON_CRW = "image/x-canon-crw"

    @JvmField
    val IMAGE_X_CMU_RASTER = "image/x-cmu-raster"

    @JvmField
    val IMAGE_X_CORELDRAW = "image/x-coreldraw"

    @JvmField
    val IMAGE_X_CORELDRAWPATTERN = "image/x-coreldrawpattern"

    @JvmField
    val IMAGE_X_CORELDRAWTEMPLATE = "image/x-coreldrawtemplate"

    @JvmField
    val IMAGE_X_CORELPHOTOPAINT = "image/x-corelphotopaint"

    @JvmField
    val IMAGE_X_EPSON_ERF = "image/x-epson-erf"

    @JvmField
    val IMAGE_X_ICON = "image/x-icon"

    @JvmField
    val IMAGE_X_JG = "image/x-jg"

    @JvmField
    val IMAGE_X_JNG = "image/x-jng"

    @JvmField
    val IMAGE_X_MS_BMP = "image/x-ms-bmp"

    @JvmField
    val IMAGE_X_NIKON_NEF = "image/x-nikon-nef"

    @JvmField
    val IMAGE_X_OLYMPUS_ORF = "image/x-olympus-orf"

    @JvmField
    val IMAGE_X_PHOTOSHOP = "image/x-photoshop"

    @JvmField
    val IMAGE_X_XPIXMAP = "image/x-xpixmap"

    @JvmField
    val INODE_BLOCKDEVICE = "inode/blockdevice"

    @JvmField
    val INODE_CHARDEVICE = "inode/chardevice"

    @JvmField
    val INODE_DIRECTORY_LOCKED = "inode/directory-locked"

    @JvmField
    val INODE_FIFO = "inode/fifo"

    @JvmField
    val INODE_SOCKET = "inode/socket"

    @JvmField
    val MESSAGE_EXTERNAL_BODY = "message/external-body"

    @JvmField
    val MODEL_MESH = "model/mesh"

    @JvmField
    val MODEL_VRML = "model/vrml"

    @JvmField
    val MODEL_X3D_BINARY = "model/x3d+binary"

    @JvmField
    val MULTIPART_DIGEST = "multipart/digest"

    @JvmField
    val MULTIPART_PARALLEL = "multipart/parallel"

    @JvmField
    val TEXT_ENGLISH = "text/english"

    @JvmField
    val TEXT_ENRICHED = "text/enriched"

    @JvmField
    val TEXT_H323 = "text/h323"

    @JvmField
    val TEXT_IULS = "text/iuls"

    @JvmField
    val TEXT_MATHML = "text/mathml"

    @JvmField
    val TEXT_RICHTEXT = "text/richtext"

    @JvmField
    val TEXT_SCRIPTLET = "text/scriptlet"

    @JvmField
    val TEXT_TEXMACS = "text/texmacs"

    @JvmField
    val TEXT_VND_FLATLAND_3DML = "text/vnd.flatland.3dml"

    @JvmField
    val TEXT_X_BIBTEX = "text/x-bibtex"

    @JvmField
    val TEXT_X_BOO = "text/x-boo"

    @JvmField
    val TEXT_X_COMPONENT = "text/x-component"

    @JvmField
    val TEXT_X_CRONTAB = "text/x-crontab"

    @JvmField
    val TEXT_X_DIFF = "text/x-diff"

    @JvmField
    val TEXT_X_LILYPOND = "text/x-lilypond"

    @JvmField
    val TEXT_X_MAKEFILE = "text/x-makefile"

    @JvmField
    val TEXT_X_MOC = "text/x-moc"

    @JvmField
    val TEXT_X_PCS_GCD = "text/x-pcs-gcd"

    @JvmField
    val TEXT_X_PERL = "text/x-perl"

    @JvmField
    val TEXT_X_PYTHON = "text/x-python"

    @JvmField
    val TEXT_X_SCALA = "text/x-scala"

    @JvmField
    val TEXT_X_SERVER_PARSED_HTML = "text/x-server-parsed-html"

    @JvmField
    val TEXT_X_SFV = "text/x-sfv"

    @JvmField
    val TEXT_X_SH = "text/x-sh"

    @JvmField
    val TEXT_X_VCALENDAR = "text/x-vcalendar"

    @JvmField
    val VIDEO_ANNODEX = "video/annodex"

    @JvmField
    val VIDEO_DL = "video/dl"

    @JvmField
    val VIDEO_FLI = "video/fli"

    @JvmField
    val VIDEO_GL = "video/gl"

    @JvmField
    val VIDEO_VND_MTS = "video/vnd.mts"

    @JvmField
    val VIDEO_X_FLV = "video/x-flv"

    @JvmField
    val VIDEO_X_LA_ASF = "video/x-la-asf"

    @JvmField
    val VIDEO_X_MATROSKA = "video/x-matroska"

    @JvmField
    val VIDEO_X_MNG = "video/x-mng"

    @JvmField
    val VIDEO_X_MS_ASF = "video/x-ms-asf"

    @JvmField
    val VIDEO_X_MS_WM = "video/x-ms-wm"

    @JvmField
    val VIDEO_X_MS_WMV = "video/x-ms-wmv"

    @JvmField
    val VIDEO_X_MS_WMX = "video/x-ms-wmx"

    @JvmField
    val VIDEO_X_MS_WVX = "video/x-ms-wvx"

    @JvmField
    val VIDEO_X_MSVIDEO = "video/x-msvideo"

    @JvmField
    val X_CONFERENCE_X_COOLTALK = "x-conference/x-cooltalk"

    @JvmField
    val X_EPOC_X_SISX_APP = "x-epoc/x-sisx-app"

    @JvmField
    val X_WORLD_X_VRML = "x-world/x-vrml"
}