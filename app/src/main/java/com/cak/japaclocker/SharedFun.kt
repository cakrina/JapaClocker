package com.cak.japaclocker

import android.content.SharedPreferences

class SharedFun(
    private var clickCount: Int,
    private var roundCount: Int,
    private var mantraCount: Int,
    private var lastClickTime: Long,
    private var lastClickTimestamp: Long,
    private val logList: MutableList<String>,
    private val sharedPreferences: SharedPreferences
) {

    fun incrementMantra() {
        val currentClickTime = System.currentTimeMillis()
        clickCount++
        roundCount = clickCount / 108
        mantraCount = clickCount % 108

        val clickTimeStr: String

        if (clickCount == 1) {
            lastClickTime = currentClickTime
            clickTimeStr = "--.-"
        } else {
            val clickTime = (currentClickTime - lastClickTime) / 100 / 10f
            lastClickTime = currentClickTime

            clickTimeStr = if (clickTime > 99.9) {
                "break"
            } else {
                clickTime.toString()
            }
        }

        logList.add(0, "$roundCount/$mantraCount - $clickTimeStr")

        lastClickTimestamp = currentClickTime
    }

    // Other functions such as saveState, restoreState, etc.
}
