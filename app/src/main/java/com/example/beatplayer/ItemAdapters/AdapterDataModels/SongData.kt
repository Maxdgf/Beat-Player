package com.example.beatplayer.ItemAdapters.AdapterDataModels

import android.graphics.Bitmap

class SongData(val name: String?, val filename: String, val author: String, val path: String, val length: String, val icon: Bitmap?, val pos: Int, val date: Long, var isSelected: Boolean, var isVisualizerActivated: Boolean) {
}