package com.cak.japaclocker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
    private val mantraList = mutableListOf<String>()
    private val roundList = mutableListOf<String>()
    private var roundStartTime = 0L
    private var roundEndTime = 0L
    private var pauseTimeSum: Long = 0
    private lateinit var logFileName: File

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        silentPlayer = MediaPlayer.create(this, R.raw.silent)
        clickPlayer = MediaPlayer.create(this, R.raw.click)
        // Get the current volume
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val minVolume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }

        // Adjust volume if it's at max or min
        when (currentVolume) {
            maxVolume -> {
                defaultVolume = (maxVolume * 0.9).toInt() // 10% less than max
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume, 0)
            }
            minVolume -> {
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

        restoreState()



        // Prepare the silent player to loop indefinitely
        silentPlayer.isLooping = true

        // BroadcastReceiver to listen for volume changes
        vReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTimestamp >= 1000) { // Limit to 1 click per second
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume, 0)
                        incrementMantra()
                        saveState()
                        clickPlayer.start()
                        lastClickTimestamp = currentTime

                    }
                }
            }
        }


        // Register the BroadcastReceiver for volume changes
        registerReceiver(vReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))

        // Start playing the silent sound to keep media session active
        silentPlayer.start()
    }
    @SuppressLint("DefaultLocale")
    private fun incrementMantra() {
        val currentClickTime = System.currentTimeMillis()
        val clickTime = (currentClickTime - lastClickTime) / 100 / 10f

        if (clickTime > 30) { // Pause time > 30 seconds no click added only wake up
            pauseTimeSum +=  currentClickTime - lastClickTime // Add pause time to pauseTimeSum
            if (clickCount == 0) {lastClickTime = currentClickTime}
            else {
                mantraList.add(0, "-pause-") // Add "-pause-" to the list
                }
        }
        else if (clickTime == 0f ){
            lastClickTime = currentClickTime
        }
        else {
            clickCount++
            roundCount = (clickCount - 1) / 108
            mantraCount = clickCount - roundCount * 108
            val clickTimeStr = clickTime.toString()
            mantraList.add(0, "$roundCount/$mantraCount - $clickTimeStr")
        }

        if (mantraCount == 1) {
            if (roundStartTime != 0L) { //normal round
                roundEndTime = currentClickTime
                var roundTime = roundEndTime - roundStartTime
                val roundTimeStr: String
                if (pauseTimeSum != 0L) {
                    roundList.add(0, "-pause-")
                    roundTime -= pauseTimeSum
                    roundTimeStr = String.format("%.1f", roundTime / 60000f)
                    if (roundCount != 0 ) {
                        roundList.add(0, "$roundCount - $roundTimeStr")
                    }
                } else {
                    roundTimeStr = String.format("%.1f", roundTime / 60000f)
                    roundList.add(0, "$roundCount - $roundTimeStr")
                    }


                roundStartTime = roundEndTime
                pauseTimeSum = 0L  // Reset pauseTimeSum for the next round
            } else {
                roundStartTime = currentClickTime
            }
        }
        lastClickTime = currentClickTime

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun saveState() {
        // Save click count and log list
        editor.putInt("clickCount", clickCount)
        editor.putLong("lastClickTime", lastClickTime)
        editor.putLong("roundStartTime", roundStartTime)
        editor.putLong("pauseTimeSum", pauseTimeSum)
        val roundListString = roundList.joinToString(separator = ",")
        editor.putString("roundList", roundListString)
        saveLogListToCSV()
        editor.apply()
    }

    private fun restoreState() {
        clickCount = sharedPreferences.getInt("clickCount", 0)
        roundCount = clickCount / 108
        mantraCount = clickCount % 108
        pauseTimeSum = sharedPreferences.getLong("pauseTimeSum", 0)
        lastClickTime = sharedPreferences.getLong("lastClickTime", 0)
        roundStartTime = sharedPreferences.getLong("roundStartTime", 0)
        val roundListString = sharedPreferences.getString("roundList", "")
        roundList.clear()
        roundListString?.let {
            if (it.isNotEmpty()) {
                roundList.addAll(it.split(","))
            }
        }
        restoreLogListFromCSV()
    }

    private fun saveLogListToCSV() {
        try {
            val fileWriter = FileWriter(logFileName)
            mantraList.forEach { logItem ->
                fileWriter.append(logItem).append("\n")
            }
            fileWriter.flush()
            fileWriter.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun restoreLogListFromCSV() {
        mantraList.clear()
        try {
            if (logFileName.exists()) {
                val bufferedReader = BufferedReader(FileReader(logFileName))
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    line?.let {
                        mantraList.add(it)
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
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "mantra_service_channel",
                "Mantra Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_MUTABLE)

        return NotificationCompat.Builder(this, "mantra_service_channel")
            .setContentTitle("Mantra Counter Running")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
            .build()
    }


}
