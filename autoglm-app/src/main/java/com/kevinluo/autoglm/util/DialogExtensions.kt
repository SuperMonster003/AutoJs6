package com.kevinluo.autoglm.util

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kevinluo.autoglm.R

/**
 * Shows the dialog and applies the primary color to all action buttons.
 *
 * Material3's MaterialAlertDialogBuilder doesn't always respect theme overrides
 * for button colors, so we programmatically set them after the dialog is shown.
 *
 * @return The shown AlertDialog instance
 */
fun MaterialAlertDialogBuilder.showWithPrimaryButtons(): AlertDialog {
    val dialog = this.create()
    dialog.setOnShowListener {
        val primaryColor = ContextCompat.getColor(context, R.color.primary)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(primaryColor)
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(primaryColor)
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(primaryColor)
    }
    dialog.show()
    return dialog
}

/**
 * Applies the primary color to all action buttons of an existing AlertDialog.
 *
 * Call this after dialog.show() if you need to show the dialog manually.
 *
 * @return The AlertDialog instance for chaining
 */
fun AlertDialog.applyPrimaryButtonColors(): AlertDialog {
    val primaryColor = ContextCompat.getColor(context, R.color.primary)
    getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(primaryColor)
    getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(primaryColor)
    getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(primaryColor)
    return this
}
