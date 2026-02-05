package com.kevinluo.autoglm.ui

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.kevinluo.autoglm.util.Logger

/**
 * Quick Settings Tile for toggling the floating window.
 * Users can add this tile to their quick settings panel for easy access.
 */
@RequiresApi(Build.VERSION_CODES.N)
class FloatingWindowTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    /**
     * Called when the tile is clicked.
     * Toggles the floating window visibility.
     */
    override fun onClick() {
        super.onClick()
        Logger.d(TAG, "Tile clicked")

        // Check overlay permission first
        if (!FloatingWindowService.canDrawOverlays(this)) {
            Logger.w(TAG, "No overlay permission, opening settings")
            val intent =
                Intent(this, com.kevinluo.autoglm.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            startActivityAndCollapseCompat(intent)
            return
        }

        // Use transparent activity to toggle and collapse panel
        // Note: Don't use FLAG_ACTIVITY_CLEAR_TASK as it destroys MainActivity and causes
        // the floating window service to be destroyed when the process is cleaned up
        val intent =
            Intent(this, FloatingWindowToggleActivity::class.java).apply {
                action = FloatingWindowToggleActivity.ACTION_TOGGLE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
        startActivityAndCollapseCompat(intent)
    }

    /**
     * Compatibility wrapper for startActivityAndCollapse.
     * API 34+ requires PendingIntent, older versions use Intent directly.
     */
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            startActivityAndCollapse(pendingIntent)
        } else {
            // API < 34: Use deprecated Intent version (required for backward compatibility)
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    /**
     * Called when the tile is added to quick settings.
     */
    override fun onTileAdded() {
        super.onTileAdded()
        Logger.d(TAG, "Tile added")
        updateTileState()
    }

    /**
     * Called when the tile is removed from quick settings.
     */
    override fun onTileRemoved() {
        super.onTileRemoved()
        Logger.d(TAG, "Tile removed")
    }

    private fun updateTileState() {
        val tile = qsTile ?: return

        val isEnabled = FloatingWindowStateManager.isEnabled()

        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(com.kevinluo.autoglm.R.string.tile_floating_window)
        tile.contentDescription = getString(com.kevinluo.autoglm.R.string.tile_floating_window_desc)

        tile.updateTile()
    }

    companion object {
        private const val TAG = "FloatingWindowTile"
    }
}
