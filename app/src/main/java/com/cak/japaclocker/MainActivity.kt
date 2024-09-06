package com.cak.japaclocker

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileWriter
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

@SuppressLint("NotifyDataSetChanged")
class MainActivity : AppCompatActivity() {

    private lateinit var tvRounds: TextView
    private lateinit var tvMantras: TextView
    private lateinit var rvMantras: RecyclerView
    private lateinit var rvRounds: RecyclerView
    private var clickCount = 0
    private var roundCount = 0
    private var mantraCount = 0
    private val mantraList = mutableListOf<Pair<String, Boolean>>()
    private val roundList = mutableListOf<Pair<String, Boolean>>()
    private val mala = 108
    private val timeout = 30
    private var lastMantraIsPause: Boolean = false
    private var lastRoundIsPause: Boolean = false
    private var lastClickTime: Long = 0
    private var roundStartTime: Long = 0
    private var roundEndTime: Long = 0
    private var pauseTimeSum: Long = 0

    private lateinit var clickSound: MediaPlayer
    private lateinit var resetSound: MediaPlayer

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var screenLockReceiver: ScreenLockReceiver
    private lateinit var logFileName: File
    private lateinit var mantraAdapter: LogAdapter
    private lateinit var roundAdapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logFileName = File(filesDir, "log_list.csv")

        screenLockReceiver = ScreenLockReceiver()

        // Initialize TextViews and RecyclerViews
        tvRounds = findViewById(R.id.tvRounds)
        tvMantras = findViewById(R.id.tvMantras)
        rvMantras = findViewById(R.id.rvMantras)
        rvRounds = findViewById(R.id.rvRounds)

        // Set layout managers for RecyclerViews
        rvMantras.layoutManager = LinearLayoutManager(this)
        rvRounds.layoutManager = LinearLayoutManager(this)

        // Initialize adapters
        mantraAdapter = LogAdapter(mantraList)
        roundAdapter = LogAdapter(roundList)

        // Set adapters to RecyclerViews
        rvMantras.adapter = mantraAdapter
        rvRounds.adapter = roundAdapter


        // Register the ScreenLockReceiver to listen for screen on/off actions
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenLockReceiver, filter)


        // Initialize MediaPlayer
        clickSound = MediaPlayer.create(this, R.raw.click) // Ensure click.mp3 is in res/raw folder
        resetSound =
            MediaPlayer.create(this, R.raw.whoosh) // Ensure whoosh.mp3 is in res/raw folder

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("JapaClockPrefs", MODE_PRIVATE)
        editor = sharedPreferences.edit()

        // Restore saved state
        restoreState()
        updateDisplay()

        lastClickTime = if (lastClickTime == 0L) System.currentTimeMillis() else lastClickTime

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
                clickCount = rounds * mala
                roundCount = clickCount / mala
                mantraCount = 0
                updateDisplay()
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
        builder.setPositiveButton("Yes") { _, _ ->
            resetCounts()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss() // Close the dialog
        }
        builder.create().show()
    }

    private fun resetCounts() {
        clickCount = 0
        roundCount = 0
        mantraCount = 0
        roundStartTime = 0
        pauseTimeSum = 0
        lastClickTime = System.currentTimeMillis()
        mantraList.clear()
        roundList.clear()
        updateDisplay()
        mantraAdapter.notifyDataSetChanged()
        roundAdapter.notifyDataSetChanged()
        saveState()

        // Play reset sound
        resetSound.start()
    }

    @SuppressLint("DefaultLocale")
    private fun incrementMantra() {
        val currentClickTime = System.currentTimeMillis()
        lastClickTime = if (lastClickTime==0L) currentClickTime else lastClickTime
        val clickTime = (currentClickTime - lastClickTime) / 100 / 10f

        if (clickTime > timeout) { // Pause time > 30 seconds no click added only wake up
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
            mantraAdapter.notifyItemInserted(0)
            rvMantras.scrollToPosition(0)
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
                roundAdapter.notifyItemInserted(0)
                rvRounds.scrollToPosition(0)
                roundStartTime = roundEndTime
                pauseTimeSum = 0L  // Reset pauseTimeSum for the next round
            }
        }
        lastClickTime = currentClickTime
    }


    private fun clickEvent() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= 1000) { // Limit to 1 click per second
            incrementMantra()
            updateDisplay()
            //mantraAdapter.notifyItemInserted(0)
            //rvMantras.scrollToPosition(0)

            clickSound.start()
        }
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
    @SuppressLint("SetTextI18n")
    private fun updateDisplay() {

        tvRounds.text = "$roundCount"
        tvMantras.text = "$mantraCount"
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

    private fun saveLogListToCSV() {
        try {
            val fileWriter = FileWriter(logFileName)
            mantraList.forEach { logItem ->
                fileWriter.append("${logItem.first},${logItem.second}\n")
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
                while (true) {
                    line = bufferedReader.readLine()
                    if (line == null) break
                    val parts = line.split(",")
                    if (parts.size == 2) {
                        mantraList.add(parts[0] to parts[1].toBoolean())
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
        restoreState()
        updateDisplay()
        mantraAdapter.notifyDataSetChanged()
        roundAdapter.notifyDataSetChanged()
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
        mantraAdapter.notifyDataSetChanged()
        roundAdapter.notifyDataSetChanged()

    }

    override fun onDestroy() {
        super.onDestroy()
        saveState()
        clickSound.release()
        resetSound.release()
        // Unregister the ScreenLockReceiver when the activity is paused
        unregisterReceiver(screenLockReceiver)
    }

    private inner class LogAdapter(private val logItems: MutableList<Pair<String, Boolean>>) :
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
            val textView: TextView = itemView.findViewById(android.R.id.text1)
            val currentColor = textView.currentTextColor

            fun bind(logItem: Pair<String, Boolean>) {
                textView.text = logItem.first
                textView.setTextColor(if (logItem.second) android.graphics.Color.RED else currentColor)

                itemView.post {
                    val recyclerViewWidth = itemView.width
                    val calculatedTextSize = when {
                        recyclerViewWidth > 300 -> 14f
                        recyclerViewWidth > 200 -> 10f
                        else -> 8f
                    }
                    textView.textSize = calculatedTextSize
                    textView.gravity = Gravity.END
                }

                itemView.setOnLongClickListener {
                    copyToClipboard(itemView.context, logItems)
                    true
                }
            }
        }

        private fun copyToClipboard(context: Context, logItems: List<Pair<String, Boolean>>) {
            val entireText = logItems.joinToString(separator = "\n") { it.first }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", entireText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}