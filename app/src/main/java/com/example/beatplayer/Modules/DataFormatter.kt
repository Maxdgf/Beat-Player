package com.example.beatplayer.Modules

import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Locale

class DataFormatter {

    fun formatData(millis: Long, pattern: String): String {
        val date = Date(millis)
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(date)
    }
}
