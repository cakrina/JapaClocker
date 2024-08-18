package com.cak.japaclocker

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.AudioManager
import android.os.IBinder
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class ForegroundService : Service() {

    private lateinit var silentPlayer: MediaPlayer
    private lateinit var clickPlayer: MediaPlayer
    private lateinit var vReceiver: BroadcastReceiver
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var defaultVolume: Int = 0
    private lateinit var audioManager: AudioManager
    private var clickCount = 0
    private var roundCount = 0
    private var mantraCount = 0
    private var lastClickTime: Long = 0
    private var lastClickTimestamp: Long = 0
    private val logList = mutableListOf<String>()
    private lateinit var logFileName: File

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        silentPlayer = MediaPlayer.create(this, R.raw.silent)
        clickPlayer = MediaPlayer.create(this, R.raw.click)
        // Get the current volume
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val minVolume = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }

        // Adjust volume if it's at max or min
        when {
            currentVolume == maxVolume -> {
                defaultVolume = (maxVolume * 0.9).toInt() // 10% less than max
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume, 0)
            }
            currentVolume == minVolume -> {
                defaultVolume = (minVolume + (maxVolume * 0.1)).toInt() // 10% more than min
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume, 0)
            }
            else -> {
                defaultVolume = currentVolume
            }
        }
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("JapaClockPrefs", MODE_PRIVATE)
        editor = sharedPreferences.edit()
        logFileName = File(filesDir, "log_list.csv")

        // restore state
        clickCount = sharedPreferences.getInt("clickCount", 0)
        lastClickTime = sharedPreferences.getLong("lastClickTime", 0)
        restoreLogListFromCSV()


        // Prepare the silent player to loop indefinitely
        silentPlayer.isLooping = true

        // BroadcastReceiver to listen for volume changes
        vReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    // Reset volume to default after the click sound
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume, 0)
                    incrementMantra()

                }
            }
        }

        // Register the BroadcastReceiver for volume changes
        registerReceiver(vReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))

        // Start playing the silent sound to keep media session active
        silentPlayer.start()
    }
    private fun incrementMantra() {
        val currentClickTime = System.currentTimeMillis()

        if (currentClickTime - lastClickTimestamp >= 1000) { // Limit to 1 click per second
            clickCount++
            roundCount = clickCount / 108
            mantraCount = clickCount % 108

            val clickTimeStr: String

            if (clickCount == 1) {
                lastClickTime = currentClickTime
                clickTimeStr = "-.-"
            } else {
                val clickTime = (currentClickTime - lastClickTime) / 100 / 10f
                lastClickTime = currentClickTime

                clickTimeStr = if (clickTime > 99.9) {
                    "XXXX"
                } else {
                    clickTime.toString()
                }
            }
            logList.add(0, "$roundCount:$mantraCount - $clickTimeStr")

            // Update last click timestamp and play click sound
            lastClickTimestamp = currentClickTime
            saveState()
            clickPlayer.start()
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun saveState() {
        editor.putLong("lastClickTime", lastClickTime)
        editor.putInt("clickCount", clickCount)
        editor.apply()
        saveLogListToCSV()
    }
    private fun saveLogListToCSV() {
        try {
            val fileWriter = FileWriter(logFileName)
            logList.forEach { logItem ->
                fileWriter.append(logItem).append("\n")
            }
            fileWriter.flush()
            fileWriter.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun restoreLogListFromCSV() {
        logList.clear()
        try {
            if (logFileName.exists()) {
                val bufferedReader = BufferedReader(FileReader(logFileName))
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    line?.let {
                        logList.add(it)
                    }
                }
                bufferedReader.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop and release MediaPlayer resources
        silentPlayer.stop()
        silentPlayer.release()
        clickPlayer.release()
        unregisterReceiver(vReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
