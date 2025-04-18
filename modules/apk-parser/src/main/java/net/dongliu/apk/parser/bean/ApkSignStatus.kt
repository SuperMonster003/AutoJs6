package net.dongliu.apk.parser.bean

/**
 * Apk sign status.
 *
 * @author dongliu
 */
enum class ApkSignStatus {
    NotSigned,

    /** invalid signing */
    Incorrect,
    Signed
}
