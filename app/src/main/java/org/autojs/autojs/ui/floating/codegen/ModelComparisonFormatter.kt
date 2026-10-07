package org.autojs.autojs.ui.floating.codegen

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.text.BidiFormatter
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogCompatibility
import org.autojs.autojs.core.accessibility.codegen.store.ModelSource
import org.autojs.autojs.core.accessibility.codegen.store.ModelVersionStatus
import org.autojs.autojs.core.accessibility.codegen.store.VersionComparison
import org.autojs.autojs.core.accessibility.codegen.store.VersionComparison.Field
import org.autojs.autojs6.R
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Resource and locale formatting shared by the settings and floating model dialogs. */
class ModelComparisonFormatter(private val context: Context) : VersionComparison.Formatter {
    val locale: Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    private val bidi = BidiFormatter.getInstance(locale)
    private val zone = ZoneId.systemDefault()

    fun fieldLabel(field: Field): String = context.getString(when (field) {
        Field.NAME -> R.string.text_codegen_compare_name
        Field.VERSION -> R.string.text_codegen_compare_version
        Field.SOURCE -> R.string.text_codegen_compare_source
        Field.CONTENT -> R.string.text_codegen_compare_content
        Field.SIZE -> R.string.text_codegen_compare_size
        Field.COMPATIBILITY -> R.string.text_codegen_compare_compatibility
        Field.DATASET -> R.string.text_codegen_compare_dataset
        Field.METRICS -> R.string.text_codegen_compare_metrics
        Field.RELEASED_AT -> R.string.text_codegen_compare_released_at
        Field.INSTALLED_AT -> R.string.text_codegen_compare_installed_at
        Field.LICENSE -> R.string.text_codegen_compare_license
        Field.STATUS -> R.string.text_codegen_compare_status
    })

    override fun source(value: ModelSource): String = context.getString(when (value) {
        ModelSource.OFFICIAL -> R.string.text_codegen_model_source_official
        ModelSource.CUSTOM -> R.string.text_codegen_model_source_custom
    })

    override fun compatibility(value: ModelCatalogCompatibility): String = context.getString(when (value) {
        ModelCatalogCompatibility.COMPATIBLE -> R.string.text_codegen_model_compatible
        ModelCatalogCompatibility.INCOMPATIBLE -> R.string.text_codegen_model_incompatible
        ModelCatalogCompatibility.NEEDS_HOST_UPDATE -> R.string.text_codegen_model_needs_host_update
    })

    override fun status(value: ModelVersionStatus): String = context.getString(when (value) {
        ModelVersionStatus.CURRENT -> R.string.text_codegen_model_current
        ModelVersionStatus.INSTALLED -> R.string.text_codegen_model_installed
        ModelVersionStatus.REVOKED -> R.string.text_codegen_model_revoked
    })

    override fun size(bytes: Long): String = when {
        bytes < 1024L -> formatted(R.string.text_codegen_compare_size_bytes, NumberFormat.getIntegerInstance(locale).format(bytes))
        bytes < 1024L * 1024L -> formatted(R.string.text_codegen_compare_size_kib, decimal(bytes / 1024.0))
        else -> formatted(R.string.text_codegen_compare_size_mib, decimal(bytes / (1024.0 * 1024.0)))
    }

    override fun integer(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value)

    override fun percentage(value: Double): String = NumberFormat.getPercentInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(value)

    override fun percentagePoints(value: Double): String = formatted(
        R.string.text_codegen_compare_percentage_points,
        // Preserve the sign of the difference; the unit is localized separately from the number.
        String.format(locale, "%+.2f", if (kotlin.math.abs(value) < 0.005) 0.0 else value),
    )

    override fun date(value: Instant): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale).withZone(zone).format(value)

    override fun dateTime(value: Instant): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withZone(zone).format(value)

    override fun pair(field: Field, primary: String, secondary: String): String = context.getString(
        when (field) {
            Field.VERSION -> R.string.text_codegen_compare_version_value
            Field.COMPATIBILITY -> R.string.text_codegen_compare_compatibility_value
            Field.METRICS -> R.string.text_codegen_compare_metrics_value
            else -> R.string.text_codegen_compare_pair
        }, bidi.unicodeWrap(primary), bidi.unicodeWrap(secondary),
    )

    private fun decimal(value: Double): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(value)

    private fun formatted(@StringRes resource: Int, number: String): String = context.getString(resource, bidi.unicodeWrap(number))
}
