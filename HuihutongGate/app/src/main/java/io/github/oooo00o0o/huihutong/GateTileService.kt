package io.github.oooo00o0o.huihutong

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class GateTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_gate)
        tile.label = getString(R.string.app_name)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val launchAction = Runnable {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }

        if (isLocked) {
            unlockAndRun(launchAction)
        } else {
            launchAction.run()
        }
    }
}
