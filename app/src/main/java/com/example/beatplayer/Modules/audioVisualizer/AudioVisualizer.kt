package com.example.beatplayer.Modules.audioVisualizer

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.media.audiofx.Visualizer.OnDataCaptureListener
import android.util.AttributeSet
import android.view.View

/*This is a app module for to configure and control status of the audio visualizer.
-------------------------------------------------------------------------------------
*                          |Module functions: |
-------------------------------------------------------------------------------------
* >> configureAudioVisualizer() - configuring audio visualizer with audio session id.
  >> launchAudioVisualizer() - starts audio visualizer.
  >> stopAudioVisualizer() - stops audio visualizer.
  >> setColor() - setting audio visualizer color.
  >> destroyAudioVisualizer() - destroying audio visualizer.*/

abstract class AudioVisualizer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    protected var waveformByteArray: ByteArray? = null
    protected var paint: Paint? = null

    private var visualizer: Visualizer? = null
    private var visualizerColor: Int = Color.GREEN

    init {
        paint = Paint()
    }

    fun configureAudioVisualizer(audioSessionId: Int) {
        visualizer = Visualizer(audioSessionId).apply {
            enabled = false
            captureSize = Visualizer.getCaptureSizeRange()[1]

            setDataCaptureListener(object : OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer, waveform: ByteArray, samplingRate: Int) {
                    // data for visualization
                    // waveform - data about sound wave
                    waveformByteArray = waveform
                    invalidate()
                }

                override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) {
                    // data for FFT (frequency analysis)
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, false)
        }
    }

    fun launchAudioVisualizer() {
        visualizer?.enabled = true
        visibility = VISIBLE
    }

    fun stopAudioVisualizer() {
        visualizer?.enabled = false
        invalidate()
        visibility = GONE
    }

    fun setColor(color: Int) {
        visualizerColor = color
        paint?.color = color
    }

    fun destroyAudioVisualizer() {
        if (visualizer == null) {
            return
        }

        visualizer?.release()
        waveformByteArray = null
        invalidate()
    }
}