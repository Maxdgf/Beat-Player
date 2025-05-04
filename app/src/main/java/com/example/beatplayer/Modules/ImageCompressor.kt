package com.example.beatplayer.Modules

import android.graphics.Bitmap

class ImageCompressor {

    fun compressImageScale(image: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(image, width, height, true)
    }
}