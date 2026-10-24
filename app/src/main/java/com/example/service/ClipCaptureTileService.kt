package com.example.service

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.ClipRepository
import com.example.data.repository.SaveResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClipCaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Save Clip"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "ClipNest"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                val db = AppDatabase.getDatabase(applicationContext)
                val pref = PreferenceManager(applicationContext)
                val repo = ClipRepository(db.clipDao(), pref)

                CoroutineScope(Dispatchers.IO).launch {
                    val result = repo.saveOrUpdateClip(text, "Captured via Quick Tile")
                    withContext(Dispatchers.Main) {
                        when (result) {
                            SaveResult.NEW -> {
                                Toast.makeText(
                                    this@ClipCaptureTileService,
                                    "Saved to ClipNest!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            SaveResult.UPDATED_EXISTING -> {
                                Toast.makeText(
                                    this@ClipCaptureTileService,
                                    "Clip updated in ClipNest",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            SaveResult.EMPTY_IGNORED -> {
                                Toast.makeText(
                                    this@ClipCaptureTileService,
                                    "Clipboard is empty",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Clipboard has no readable text", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
