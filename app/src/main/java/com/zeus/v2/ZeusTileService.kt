package com.zeus.v2

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ZeusTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val isActive = tile.state == Tile.STATE_ACTIVE

        val intent = Intent(this, AudioEngineService::class.java)

        if (isActive) {
            stopService(intent)
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Zeus EQ"
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Zeus ON"
        }
        tile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
    }
}
