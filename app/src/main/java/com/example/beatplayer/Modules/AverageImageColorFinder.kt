package com.example.beatplayer.Modules

import android.graphics.Bitmap
import android.graphics.Color

/*This is a app module for calculating average color of bitmap image of song and
setting this color in song album icon audio visualizer.
-------------------------------------------------------------------------------------
*                          |Module functions: |
-------------------------------------------------------------------------------------
* >> findAverageColorOnImage() - scanning image RGB pixels and calculating average color.
  >> makeBrightColor() - makes color bright
  >> makeDarkColor() - makes color dark
  >> checkColorLightness() - checking color lightness.*/

class AverageImageColorFinder {

    private var imageCompressor: ImageCompressor = ImageCompressor()

    companion object {
        private const val brightness = 3.1f
        private const val darkness = 3.1f
        private const val compressWidth = 100
        private const val compressHeight = 100
    }

    fun findAverageColorOnImage(image: Bitmap, correctColorWithLightness: Boolean): Int {
        //compress scale of bitmap image for optimization
        val compressedImage = imageCompressor.compressImageScale(image, compressWidth, compressHeight)

        var redCount = 0
        var greenCount = 0
        var blueCount= 0

        val width = compressedImage.width
        val height = compressedImage.height
        val pixels = width * height

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = compressedImage.getPixel(x, y)

                redCount += Color.red(pixel)
                greenCount += Color.green(pixel)
                blueCount += Color.blue(pixel)
            }
        }

        val averageRed = redCount / pixels
        val averageGreen = greenCount / pixels
        val averageBlue = blueCount / pixels

        val result = Color.rgb(averageRed, averageGreen, averageBlue)

        //checking color lightness for the audio visualizer to be visible on top song image
        return if (correctColorWithLightness) {
            if (!checkColorLightness(result)) {
                makeBrightColor(result, brightness)
            } else {
                makeDarkColor(result, darkness)
            }
        } else {
            result
        }
    }

    private fun makeBrightColor(color: Int, brightness: Float): Int {
        val r = (Color.red(color) * brightness).coerceAtMost(255f).toInt()
        val g = (Color.green(color) * brightness).coerceAtMost(255f).toInt()
        val b = (Color.blue(color) * brightness).coerceAtMost(255f).toInt()

        return Color.rgb(r, g, b)
    }

    private fun makeDarkColor(color: Int, darkness: Float): Int {
        val r = (Color.red(color) * darkness).coerceAtMost(0f).toInt()
        val g = (Color.green(color) * darkness).coerceAtMost(0f).toInt()
        val b = (Color.blue(color) * darkness).coerceAtMost(0f).toInt()

        return Color.rgb(r, g, b)
    }

    private fun checkColorLightness(color: Int): Boolean {
        val threshold = 200

        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        return r > threshold && g > threshold && b > threshold
    }
}