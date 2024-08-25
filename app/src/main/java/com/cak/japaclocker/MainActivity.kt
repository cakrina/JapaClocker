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
import android.util.Log
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
    private val mantraList =
        mutableListOf<String>() // Use mutable list to add items at the beginning
    private val roundList = mutableListOf<String>()
    //private var currentClickTime: Long = 0
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

        if (clickTime > 30) { // Pause time > 30 seconds no click added only wake up
            pauseTimeSum +=  currentClickTime - lastClickTime // Add pause time to pauseTimeSum
            if (clickCount == 0) {lastClickTime = currentClickTime}
            else {
                mantraList.add(0, "-pause-") // Add "-pause-" to the list
                mantraAdapter.notifyItemInserted(0)
                rvMantras.scrollToPosition(0)}
        } else if (clickTime == 0f) {
            lastClickTime = currentClickTime
        }
        else {
            clickCount++ // count click
            roundCount = (clickCount - 1) / 108
            mantraCount = clickCount - roundCount * 108
            val clickTimeStr = clickTime.toString()
            mantraList.add(0, "$roundCount/$mantraCount - $clickTimeStr")
            mantraAdapter.notifyItemInserted(0)
            rvMantras.scrollToPosition(0)
        }

        if (mantraCount == 1) {
            if (roundStartTime != 0L) { //normal round
                roundEndTime = currentClickTime
                var roundTime = roundEndTime - roundStartTime
                val roundTimeStr: String
                if (pauseTimeSum != 0L) {
                    roundList.add(0, "-pause-")
                    roundAdapter.notifyItemInserted(0)
                    rvRounds.scrollToPosition(0)
                    roundTime -= pauseTimeSum
                    roundTimeStr = String.format("%.1f", roundTime / 60000f)
                    if (roundCount != 0 ) {
                        roundList.add(0, "$roundCount - $roundTimeStr")
                        roundAdapter.notifyItemInserted(0)
                        rvRounds.scrollToPosition(0)
                    }
                } else {
                    roundTimeStr = String.format("%.1f", roundTime / 60000f)
                    roundList.add(0, "$roundCount - $roundTimeStr")
                    roundAdapter.notifyItemInserted(0)
                    rvRounds.scrollToPosition(0)}


                roundStartTime = roundEndTime
                pauseTimeSum = 0L  // Reset pauseTimeSum for the next round
            } else {
                roundStartTime = currentClickTime
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
        lastClickTime = sharedPreferences.getLong("lastClickTime", 0)
        roundStartTime = sharedPreferences.getLong("roundStartTime", 0)
        pauseTimeSum = sharedPreferences.getLong("pauseTimeSum", 0)
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
                textView.text = logItem

                itemView.post {
                    val recyclerViewWidth = itemView.width
                    Log.d("viewsize is ", recyclerViewWidth.toString())
                    val calculatedTextSize = when {
                        recyclerViewWidth > 300 -> 14f // Larger size for wider RecyclerView items
                        recyclerViewWidth > 200 -> 10f // Medium size
                        else -> 8f // Smaller size for narrower RecyclerView items
                    }
                    textView.textSize = calculatedTextSize
                    textView.gravity = Gravity.END
                }

                // Add long press listener for copying text to clipboard
                itemView.setOnLongClickListener {
                    copyToClipboard(itemView.context, logItems)
                    true // Return true to indicate the long press was handled
                }
            }
        }

        private fun copyToClipboard(context: Context, logItems: List<String>) {
            val entireText = logItems.joinToString(separator = "\n")
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", entireText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}