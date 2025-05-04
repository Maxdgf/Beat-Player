package com.example.beatplayer.Modules

import android.graphics.Color

/*This is a app module for random rgb color generation.
-------------------------------------------------------------------------------------
*                          |Module functions: |
-------------------------------------------------------------------------------------
* >> randomColor() - generates random rgb color.
  >> generateColor() - generates rgb color by arguments.*/

class RGBColorGenerator {

    fun randomColor(r: Int, g: Int, b: Int): Int {
        val red = (0..(if (r > 255) 255 else r)).random()
        val green = (0..(if (g > 255) 255 else g)).random()
        val blue = (0..(if (b > 255) 255 else b)).random()

        return Color.rgb(red, green, blue)
    }
}