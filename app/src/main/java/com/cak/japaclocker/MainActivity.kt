package com.cak.japaclocker

import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileWriter
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var tvRounds: TextView
    private lateinit var tvMantras: TextView
    private lateinit var rvLog: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private var clickCount = 0
    private var roundCount = 0
    private var mantraCount = 0
    private val logList = mutableListOf<String>() // Use mutable list to add items at the beginning
    private var lastClickTime: Long = 0
    private var lastClickTimestamp: Long = 0

    private lateinit var clickSound: MediaPlayer
    private lateinit var resetSound: MediaPlayer

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var screenLockReceiver: ScreenLockReceiver
    private lateinit var logFileName: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logFileName = File(filesDir, "log_list.csv")

        screenLockReceiver = ScreenLockReceiver()

        tvRounds = findViewById(R.id.tvRounds)
        tvMantras = findViewById(R.id.tvMantras)
        rvLog = findViewById(R.id.rvLog)

        rvLog.layoutManager = LinearLayoutManager(this)
        logAdapter = LogAdapter(logList)
        rvLog.adapter = logAdapter

        // Register the ScreenLockReceiver to listen for screen on/off actions
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenLockReceiver, filter)


        // Initialize MediaPlayer
        clickSound = MediaPlayer.create(this, R.raw.click) // Ensure click.mp3 is in res/raw folder
        resetSound = MediaPlayer.create(this, R.raw.whoosh) // Ensure whoosh.mp3 is in res/raw folder

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("JapaClockPrefs", MODE_PRIVATE)
        editor = sharedPreferences.edit()

        // Restore saved state
        clickCount = sharedPreferences.getInt("clickCount", 0)
        roundCount = clickCount / 108
        mantraCount = clickCount % 108
        updateDisplay()
        restoreLogListFromCSV()

        tvMantras.setOnClickListener {
            clickEvent()
        }
        tvMantras.setOnLongClickListener {
            showConfirmationDialog()
            true
        }

        tvRounds.setOnLongClickListener {
            showEnterRoundsDialog()
            true
        }
    }

    private fun showEnterRoundsDialog() {
        // Create a NumberPicker view for user input
        val numberPicker = NumberPicker(this)
        numberPicker.minValue = 0
        numberPicker.maxValue = 128 // Set the maximum value as needed
        numberPicker.wrapSelectorWheel = false
        numberPicker.value = roundCount

        // Create and configure the AlertDialog
        AlertDialog.Builder(this)
            .setTitle("Enter Rounds")
            .setView(numberPicker)
            .setPositiveButton("Submit") { dialog, _ ->

                // Calculate total clicks and update display
                val rounds = numberPicker.value
                clickCount = rounds * 108
                roundCount = clickCount / 108
                mantraCount = clickCount % 108


                // Update display
                updateDisplay()

                // Update state and save
                saveState()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun showConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Reset")
        builder.setMessage("proceed? with reset")
        builder.setPositiveButton("Yes") { dialog, which ->
            resetCounts()
        }
        builder.setNegativeButton("No") { dialog, which ->
            dialog.dismiss() // Close the dialog
        }
        builder.create().show()
    }

    private fun resetCounts() {
        clickCount = 0
        roundCount = 0
        mantraCount = 0
        logList.clear()
        updateDisplay()
        logAdapter.notifyDataSetChanged()
        saveState()

        // Play reset sound
        resetSound.start()
    }

    private fun incrementMantra() {
        val currentClickTime = System.currentTimeMillis()

        if (System.currentTimeMillis() - lastClickTimestamp >= 1000) { // Limit to 1 click per second
            clickCount++
            roundCount = clickCount / 108
            mantraCount = clickCount % 108

            val clickTimeStr: String

            if (clickCount == 1) {
                lastClickTime = System.currentTimeMillis()
                clickTimeStr = "--.-"
            } else {
                val clickTime = (System.currentTimeMillis() - lastClickTime) / 100 / 10f
                lastClickTime = System.currentTimeMillis()

                clickTimeStr = if (clickTime > 99.9) {
                    "break"
                } else {
                    clickTime.toString()
                }
            }

            // Add the new log entry to the beginning of the list
            logList.add(0, "$roundCount/$mantraCount - $clickTimeStr")


            // Update last click timestamp and play click sound
            lastClickTimestamp = currentClickTime
            clickSound.start()
        }
    }

    private fun clickEvent() {
        incrementMantra()
        updateDisplay()
        logAdapter.notifyItemInserted(0)
        rvLog.scrollToPosition(0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                clickEvent()
                return true // Indicate that you've handled the key event
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateDisplay() {
        tvRounds.text = "$roundCount"
        tvMantras.text = "$mantraCount"
    }

    private fun saveState() {
        // Save click count and log list
        editor.putInt("clickCount", clickCount)
        editor.putLong("lastClickTime", lastClickTime)
        saveLogListToCSV()
        editor.apply()
    }

    private fun restoreState() {
        clickCount = sharedPreferences.getInt("clickCount", 0)
        roundCount = clickCount / 108
        mantraCount = clickCount % 108
        lastClickTime = sharedPreferences.getLong("lastClickTime", 0)
        restoreLogListFromCSV()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("lastClickTime", lastClickTime)
        outState.putInt("clickCount", clickCount)
        saveLogListToCSV()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        lastClickTime = savedInstanceState.getLong("lastClickTime")
        clickCount = savedInstanceState.getInt("clickCount")
        restoreLogListFromCSV()
        updateDisplay()
        logAdapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        // Save click count and log list
        saveState()
    }

    override fun onResume() {
        super.onResume()
        // Restore state
        restoreState()
        updateDisplay()
        logAdapter.notifyDataSetChanged()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        saveState()
        clickSound.release()
        resetSound.release()
        // Unregister the ScreenLockReceiver when the activity is paused
        unregisterReceiver(screenLockReceiver)
    }

    private inner class LogAdapter(private val logItems: MutableList<String>) :
        RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(logItems[position])
        }

        override fun getItemCount(): Int {
            return logItems.size
        }

        inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            private val textView: TextView = itemView.findViewById(android.R.id.text1)

            fun bind(logItem: String) {
                // Update the text of the TextView
                textView.text = logItem

                // Measure the width of the itemView and set the text size accordingly
                itemView.post {
                    val recyclerViewWidth = itemView.width
                    Log.d("viewsize is ", recyclerViewWidth.toString())
                    val calculatedTextSize = when {
                        recyclerViewWidth > 300 -> 14f // Larger size for wider RecyclerView items
                        recyclerViewWidth > 200 -> 10f // Medium size
                        else -> 8f // Smaller size for narrower RecyclerView items
                    }

                    // Set the calculated text size
                    textView.textSize = calculatedTextSize
                    textView.gravity = Gravity.END
                }
            }
        }

    }
}
