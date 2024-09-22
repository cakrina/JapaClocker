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

class ForegroundService : Service() {

    private lateinit var silentPlayer: MediaPlayer
    private lateinit var clickPlayer: MediaPlayer
    private lateinit var vReceiver: BroadcastReceiver
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var defaultVolume: Int = 0
    private lateinit var audioManager: AudioManager
    private var mala = 108
    private var pauseTimeout = 30
    private var mantraTimeout: Long = 1000
    private var lastMantraIsPause: Boolean = false
    private var lastRoundIsPause: Boolean = false
    private var clickCount = 0
    private var roundCount = 0
    private var mantraCount = 0
    private var lastClickTime: Long = 0
    private var lastClickTimestamp: Long = 0
    private val mantraList = mutableListOf<Pair<String, Boolean>>()
    private val roundList = mutableListOf<Pair<String, Boolean>>()
    private var roundStartTime = 0L
    private var roundEndTime = 0L
    private var pauseTimeSum: Long = 0

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

        restoreState()



        // Prepare the silent player to loop indefinitely
        silentPlayer.isLooping = true

        // BroadcastReceiver to listen for volume changes
        vReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTimestamp >= mantraTimeout) { // Limit to 1 click per second
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
        lastClickTime = if (lastClickTime==0L) currentClickTime else lastClickTime
        val clickTime = (currentClickTime - lastClickTime) / 100 / 10f

        if (clickTime > pauseTimeout) { // Pause time > 30 seconds no click added only wake up
            pauseTimeSum +=  currentClickTime - lastClickTime // Add pause time to pauseTimeSum
            if (clickCount == 0) {lastClickTime = currentClickTime}
            else {
                lastMantraIsPause = true
            }
        } else if (clickTime == 0f) {
            lastClickTime = currentClickTime
        }
        else {
            clickCount++ // count click
            roundCount = (clickCount - 1) / mala
            mantraCount = clickCount - roundCount * mala
            val clickTimeStr = clickTime.toString()
            mantraList.add(0, Pair("$roundCount/$mantraCount - $clickTimeStr",lastMantraIsPause))
            Thread.sleep(50)
            lastMantraIsPause = false
        }

        if (mantraCount == 1 && !lastMantraIsPause) {
            if (roundCount == 0) {
                roundStartTime = currentClickTime
            } else {
                roundEndTime = currentClickTime
                val roundTime = roundEndTime - roundStartTime - pauseTimeSum
                lastRoundIsPause = pauseTimeSum != 0L
                val roundTimeStr = String.format("%.1f", roundTime / 60000f)
                roundList.add(0, Pair("$roundCount - $roundTimeStr",lastRoundIsPause))
                roundStartTime = roundEndTime
                pauseTimeSum = 0L  // Reset pauseTimeSum for the next round
            }
        }
        lastClickTime = currentClickTime
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun saveState() {
        editor.putInt("clickCount", clickCount)
        editor.putLong("lastClickTime", lastClickTime)
        editor.putLong("roundStartTime", roundStartTime)
        editor.putLong("pauseTimeSum", pauseTimeSum)

        val roundListString = roundList.joinToString(separator = ";") { "${it.first},${it.second}" }
        editor.putString("roundList", roundListString)

        val mantraListString = mantraList.joinToString(separator = ";") { "${it.first},${it.second}" }
        editor.putString("mantraList", mantraListString)
        editor.apply()
    }

    private fun restoreState() {
        clickCount = sharedPreferences.getInt("clickCount", 0)
        roundCount = clickCount / mala
        mantraCount = clickCount % mala
        lastClickTime = sharedPreferences.getLong("lastClickTime", 0)
        roundStartTime = sharedPreferences.getLong("roundStartTime", 0)
        pauseTimeSum = sharedPreferences.getLong("pauseTimeSum", 0)

        roundList.clear()
        sharedPreferences.getString("roundList", "")?.let {
            if (it.isNotEmpty()) {
                it.split(";").forEach { item ->
                    val parts = item.split(",")
                    if (parts.size == 2) {
                        roundList.add(Pair(parts[0], parts[1].toBoolean()))
                    }
                }
            }
        }

        mantraList.clear()
        sharedPreferences.getString("mantraList", "")?.let {
            if (it.isNotEmpty()) {
                it.split(";").forEach { item ->
                    val parts = item.split(",")
                    if (parts.size == 2) {
                        mantraList.add(Pair(parts[0], parts[1].toBoolean()))
                    }
                }
            }
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
