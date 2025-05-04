package com.example.beatplayer.Modules.audioVisualizer.audioVisualizerTemplates

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.ceil

import com.example.beatplayer.Modules.audioVisualizer.AudioVisualizer
import com.example.beatplayer.R

/* <-Audio Visualizer template-> */

class SongIconAudioVisualizer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AudioVisualizer(context, attrs, defStyleAttr) {

    //lines top points
    private var topPointsList = FloatArray(density.toInt()) { height.toFloat() }

    companion object {
        private const val density = 10f
        private const val space = 4
        private const val steps = 10
        private const val interpolationFactor = 0.5f
    }

    init {
        paint?.isAntiAlias = true
        paint?.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        //filling canvas a transparent color for clear and smoothness drawing
        canvas.drawColor(ContextCompat.getColor(context, R.color.transparent))

        if (waveformByteArray != null) {
            val barWidth = width / density
            val division = waveformByteArray?.size?.div(density)
            paint?.strokeWidth = barWidth - space

            for (i in 0 until density.toInt()) {
                val bytePosition = ceil(i * division!!.toDouble()).toInt()
                val top = ((abs(waveformByteArray!![bytePosition].toInt()) + 128) * height / 256).toFloat()
                val barX = (i * barWidth) + (barWidth / 2)

                //giving smoothness to audio visualizer

                //audio visualizer line drawing step size
                val stepSize = (top - topPointsList[i]) / steps

                //drawing lines in steps followed by linear interpolation of each step
                for (j in 0 until steps) {
                    if (topPointsList[i] < top) {
                        topPointsList[i] += stepSize
                        topPointsList[i] = topPointsList[i] + (top - topPointsList[i]) * interpolationFactor
                        canvas.drawLine(barX, height.toFloat(), barX, topPointsList[i], paint!!)
                    } else {
                        topPointsList[i] -= stepSize
                        topPointsList[i] = topPointsList[i] + (top - topPointsList[i]) * interpolationFactor
                        canvas.drawLine(barX, height.toFloat(), barX, topPointsList[i], paint!!)
                    }
                }
            }
        }
    }
}