package com.example.beatplayer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beatplayer.ItemAdapters.AdapterDataModels.SongData
import com.example.beatplayer.Bluetooth.BluetoothDeviceData
import com.example.beatplayer.Bluetooth.BluetoothDevicesListAdapter
import com.example.beatplayer.ItemAdapters.SongsListAdapter
import com.example.beatplayer.Modules.AverageImageColorFinder
import com.example.beatplayer.Modules.DataFormatter
import com.example.beatplayer.Modules.RGBColorGenerator
import com.example.beatplayer.Modules.audioVisualizer.audioVisualizerTemplates.SongIconAudioVisualizer
import com.example.beatplayer.databinding.ActivityMainBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

class MainActivity : AppCompatActivity(), SongsListAdapter.OnItemClickListener, BluetoothDevicesListAdapter.OnItemClickListener {

    companion object {
        private const val executeMediaTypeCondition = MediaStore.Audio.Media.IS_MUSIC
        private const val SPP = "00001101-0000-1000-8000-00805f9b34fb"
        //private const val A2DP = "0000110a-0000-1000-8000-00805f9b34fb"
    }

    private lateinit var notFoundBleDevicesPanel: LinearLayout

    private lateinit var binding: ActivityMainBinding
    private lateinit var clickAnimation: Animation
    private var musicPlayer: MediaPlayer = MediaPlayer()
    private var RGBColorGenerator: RGBColorGenerator = RGBColorGenerator()
    private var dataFormatter: DataFormatter = DataFormatter()
    private lateinit var devicesList: MutableList<BluetoothDevice>
    private lateinit var songIconAudioVisualizer: SongIconAudioVisualizer
    private lateinit var averageImageColorFinder: AverageImageColorFinder

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var searchLabel: LinearLayout? = null
    private var bluetoothDialog: AlertDialog? = null
    private var runnableTimer: Runnable? = null
    private var mainHandler: Handler? = null
    private var isTimerRunning = false
    private var averageVisualizerColor = 0
    private var timeMark = 0
    private var songIndex = 0

    private var isRecordAudioPermissionGranted = false
    private var isReadAudioPermissionGranted = false
    private var isBluetoothConnectGranted = false
    private var isBluetoothScanGranted = false
    private var isBluetoothGranted = false
    private var isBluetoothAdminGranted = false
    private var isFineLocationGranted = false
    private var isCoarseLocationGranted = false
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var valuesList: ArrayList<SongData>
    private lateinit var bleList: ArrayList<BluetoothDeviceData>
    private lateinit var songsAdapter: SongsListAdapter
    private lateinit var bleDevicesAdapter: BluetoothDevicesListAdapter

    private val getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            setupDialog()
            getPairedDevices()
            startBluetoothScan()
            binding.bluetoothSwitch.setImageResource(R.drawable.bluetooth_searching)
        }
    }

    private val bluetoothDeviceReceiver = object : BroadcastReceiver() {
        @SuppressLint("NotifyDataSetChanged")
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String = intent!!.action!!

            when(action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!

                    if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        val bluetoothClass = device.bluetoothClass
                        val deviceClass = bluetoothClass.deviceClass

                        val name = device.name
                        val address = device.address
                        var deviceType = "OtherDevice"

                        when (deviceClass) {
                            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> {
                                deviceType = "HeadPhones"
                            }

                            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> {
                                deviceType = "AudioVideoWearable"
                            }

                            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> {
                                deviceType = "AudioSpeaker"
                            }
                        }

                        bleList.add(BluetoothDeviceData(name, address, false, deviceType))
                        bleDevicesAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private val bluetoothConnectionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String = intent!!.action!!

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    binding.bluetoothSwitch.setImageResource(R.drawable.bluetooth_connected)
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (musicPlayer.isPlaying) {
                        musicPlayer.pause()
                        stopTimer()
                    }

                    binding.playSong.setImageResource(android.R.drawable.ic_media_play)

                    binding.pairedDevice.text = ""
                    binding.pairedDevice.isSelected = false
                    binding.pairedDevice.visibility = View.GONE
                    binding.bluetoothSwitch.setImageResource(R.drawable.bluetooth)
                }
            }
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String = intent!!.action!!

            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            bluetoothDialog?.dismiss()

                            if (musicPlayer.isPlaying) {
                                musicPlayer.pause()
                                stopTimer()
                            }

                            binding.playSong.setImageResource(android.R.drawable.ic_media_play)

                            binding.pairedDevice.text = ""
                            binding.pairedDevice.isSelected = false
                            binding.pairedDevice.visibility = View.GONE
                            binding.bluetoothSwitch.setImageResource(R.drawable.bluetooth)
                        }

                        BluetoothAdapter.STATE_ON -> {
                            if (musicPlayer.isPlaying) {
                                musicPlayer.pause()
                                stopTimer()
                            }

                            binding.playSong.setImageResource(android.R.drawable.ic_media_play)
                            binding.bluetoothSwitch.setImageResource(R.drawable.bluetooth_searching)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged", "MissingInflatedId", "ClickableViewAccessibility", "SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        musicPlayer = MediaPlayer()
        songIconAudioVisualizer = findViewById(R.id.songIconAudioVisualizer)
        averageImageColorFinder = AverageImageColorFinder()

        clickAnimation = AnimationUtils.loadAnimation(this, R.anim.click_button)

        RGBColorGenerator = RGBColorGenerator()
        binding.timeProgress.progressDrawable.colorFilter = PorterDuffColorFilter(RGBColorGenerator.randomColor(256, 256, 256), PorterDuff.Mode.SRC_IN)

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            isRecordAudioPermissionGranted = permissions[Manifest.permission.RECORD_AUDIO]?:isRecordAudioPermissionGranted
            isReadAudioPermissionGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO]?:isReadAudioPermissionGranted
            isBluetoothConnectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT]?:isBluetoothConnectGranted
            isBluetoothScanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN]?:isBluetoothScanGranted
            isBluetoothGranted = permissions[Manifest.permission.BLUETOOTH]?:isBluetoothGranted
            isBluetoothAdminGranted = permissions[Manifest.permission.BLUETOOTH_ADMIN]?:isBluetoothAdminGranted
            isFineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION]?:isFineLocationGranted
            isCoarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION]?:isCoarseLocationGranted
        }

        requestPermissions()

        valuesList = ArrayList()
        bleList = ArrayList()
        songsAdapter = SongsListAdapter(this,this, valuesList)
        binding.songsList.layoutManager = LinearLayoutManager(this)
        binding.songsList.adapter = songsAdapter

        /*if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 1)
        } else {
            fetchSongsFiles()
        }*/

        binding.pairedDevice.visibility = View.GONE
        binding.filesLoadProgress.visibility = View.VISIBLE
        binding.songLoadProgress.visibility = View.VISIBLE

        binding.songsList.visibility = View.GONE
        binding.songTxtData.visibility = View.GONE
        binding.actionButtonsPanel.visibility = View.GONE
        binding.timeProgress.visibility = View.GONE
        binding.songIcon.visibility = View.GONE

        fetchSongsFiles()

        binding.bluetoothSwitch.setOnClickListener {
            binding.bluetoothSwitch.startAnimation(clickAnimation)

            bleList.clear()

            val bluetoothManager = getSystemService(BluetoothManager::class.java)

            bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter != null) {
                if (!bluetoothAdapter?.isEnabled!!) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        getResult.launch(intent)
                    }
                } else {
                    setupDialog()
                    getPairedDevices()
                    startBluetoothScan()
                    binding.bluetoothSwitch.setImageResource(R.drawable.bluetooth_searching)
                }
            } else {
                Toast.makeText(this, "Bluetooth not working on your device!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.playSong.apply {
            setOnClickListener {
                if (!musicPlayer.isPlaying) {
                    musicPlayer.start()
                    binding.playSong.setImageResource(android.R.drawable.ic_media_pause)
                    binding.playSong.startAnimation(clickAnimation)
                    startTimer()
                    songIconAudioVisualizer.launchAudioVisualizer()
                } else {
                    musicPlayer.pause()
                    binding.playSong.setImageResource(android.R.drawable.ic_media_play)
                    binding.playSong.startAnimation(clickAnimation)
                    stopTimer()
                    songIconAudioVisualizer.stopAudioVisualizer()
                }
            }

            setOnLongClickListener {
                if (musicPlayer.isPlaying) {
                    musicPlayer.pause()
                    stopTimer()
                    binding.playSong.setImageResource(R.drawable.brand_awareness)
                }

                binding.barPanel.visibility = View.VISIBLE
                binding.actionMessage.visibility = View.VISIBLE
                binding.actionMessage.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in))
                binding.barPanel.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in))

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                binding.valueBar.max = maxVolume
                binding.valueBar.progress = currentVolume

                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, AudioManager.FLAG_SHOW_UI)
                binding.actionDescription.text = "${ceil((currentVolume.toFloat() / maxVolume) * 100)}%"

                if (currentVolume > maxVolume / 2) {
                    binding.actionIcon.setImageResource(R.drawable.volume_up)
                } else if (currentVolume < maxVolume / 2) {
                    binding.actionIcon.setImageResource(R.drawable.volume_down)
                }

                binding.valueBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val percentage = ceil((progress.toFloat() / maxVolume) * 100)
                        binding.actionDescription.text = "$percentage%"

                        if (progress > maxVolume / 2) {
                            binding.actionIcon.setImageResource(R.drawable.volume_up)
                        } else if (progress < maxVolume / 2) {
                            binding.actionIcon.setImageResource(R.drawable.volume_down)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        binding.actionMessage.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_out))
                        binding.barPanel.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_out))
                        binding.actionMessage.visibility = View.GONE
                        binding.barPanel.visibility = View.GONE
                        binding.actionDescription.text = ""
                        musicPlayer.start()
                        startTimer()
                        binding.playSong.setImageResource(android.R.drawable.ic_media_pause)

                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, seekBar?.progress ?: 0, AudioManager.FLAG_SHOW_UI)
                    }

                })

                true
            }
        }

        binding.nextSong.apply {
            setOnClickListener {
                startAnimation(clickAnimation)
                switchNextSong(this@MainActivity)
            }

            setOnLongClickListener {
                if (musicPlayer.isPlaying) {
                    musicPlayer.pause()
                    stopTimer()
                    binding.playSong.setImageResource(android.R.drawable.ic_media_play)
                }

                binding.barPanel.visibility = View.VISIBLE
                binding.actionMessage.visibility = View.VISIBLE
                binding.actionMessage.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in))
                binding.barPanel.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in))

                binding.actionIcon.setImageResource(R.drawable.music_note)

                val data = valuesList[songIndex]

                binding.valueBar.max = data.length.toInt()
                binding.valueBar.progress = timeMark
                binding.actionDescription.text = dataFormatter.formatData(timeMark.toLong(), "mm:ss")

                binding.valueBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        musicPlayer.seekTo(progress)
                        timeMark = progress
                        binding.actionDescription.text = dataFormatter.formatData(timeMark.toLong(), "mm:ss")
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        binding.actionMessage.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_out))
                        binding.barPanel.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_out))
                        binding.actionMessage.visibility = View.GONE
                        binding.barPanel.visibility = View.GONE
                        binding.actionDescription.text = ""

                        startTimer()
                        musicPlayer.start()
                        binding.playSong.setImageResource(android.R.drawable.ic_media_pause)
                    }
                })

                true
            }
        }

        binding.previousSong.apply {
            setOnClickListener {
                startAnimation(clickAnimation)
                switchPreviousSong(this@MainActivity)
            }

            setOnLongClickListener {
                if (musicPlayer.isPlaying) {
                    musicPlayer.pause()
                    stopTimer()
                    binding.playSong.setImageResource(android.R.drawable.ic_media_play)
                }

                binding.barPanel.visibility = View.VISIBLE
                binding.actionMessage.visibility = View.VISIBLE
                binding.actionMessage.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in))
                binding.barPanel.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in))

                binding.actionIcon.setImageResource(R.drawable.music_note)

                val data = valuesList[songIndex]

                binding.valueBar.max = data.length.toInt()
                binding.valueBar.progress = timeMark
                binding.actionDescription.text = dataFormatter.formatData(timeMark.toLong(), "mm:ss")

                binding.valueBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        musicPlayer.seekTo(progress)
                        timeMark = progress
                        binding.actionDescription.text = dataFormatter.formatData(timeMark.toLong(), "mm:ss")
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        binding.actionMessage.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_out))
                        binding.barPanel.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_out))
                        binding.actionMessage.visibility = View.GONE
                        binding.barPanel.visibility = View.GONE
                        binding.actionDescription.text = ""

                        startTimer()
                        musicPlayer.start()
                        binding.playSong.setImageResource(android.R.drawable.ic_media_pause)
                    }
                })

                true
            }
        }
    }
    //interfaces listeners area---------------------------------------------------------------------------------------------------------------------------------------
    @SuppressLint("NotifyDataSetChanged")
    override fun onMusicPlay(path: String, icon: Bitmap?, name: String, data: String, length: Int, position: Int, card: MaterialCardView) {
        selectSong(path, position, card)
    }

    override fun onBleDeviceSelected(address: String, name: String, position: Int, progressBar: ProgressBar, deviceIcon: ImageView, deviceCard: MaterialCardView) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            //val device = bluetoothAdapter?.getRemoteDevice(address)
            var device: BluetoothDevice? = null
            val uuid = UUID.fromString(SPP)
            var isDeviceFounded = false

            for (d: BluetoothDevice in devicesList) {
                if (d.name == name) {
                    device = d
                    isDeviceFounded = true
                    break
                }
            }

            if (!isDeviceFounded) {
                //write here a warning message, this message must contains like: WARNING! device is not bonded...
                device = bluetoothAdapter?.getRemoteDevice(address)
            }

            if (device != null) {
                connectToDevice(this, name, progressBar, device, uuid, position, deviceIcon, deviceCard)
            }
        }
    }

    //interfaces listeners area---------------------------------------------------------------------------------------------------------------------------------------

    //coroutines actions area-----------------------------------------------------------------------------------------------------------------------------------------
    private fun selectSong(path: String, position: Int, card: MaterialCardView) {
        card.isClickable = false
        val previousSongIndex = songIndex
        var songName: String

        songIconAudioVisualizer.stopAudioVisualizer()

        try {
            CoroutineScope(Dispatchers.IO).launch {
                if (musicPlayer.isPlaying) {
                    musicPlayer.stop()
                }

                musicPlayer.reset()
                stopTimer()

                timeMark = 0
                songIndex = position

                /*valuesList.forEachIndexed { index, songData ->
                    songData.isSelected = false
                    songData.isVisualizerActivated = false
                }*/
                valuesList[previousSongIndex].isSelected = false
                valuesList[previousSongIndex].isVisualizerActivated = false
                valuesList[songIndex].isSelected = true
                valuesList[songIndex].isVisualizerActivated = true

                musicPlayer.setDataSource(path)
                musicPlayer.prepare()

                val data = valuesList[songIndex]

                averageVisualizerColor = if (data.icon != null) {
                    val image = data.icon
                    averageImageColorFinder.findAverageColorOnImage(image, true)
                } else {
                    Color.rgb(5, 127, 224)
                }

                songName = data.name ?: data.filename

                withContext(Dispatchers.Main) {
                    setSong(previousSongIndex, songIndex, songName, musicPlayer.audioSessionId, false)
                    binding.playSong.setImageResource(android.R.drawable.ic_media_pause)
                    card.isClickable = true

                    songIconAudioVisualizer.launchAudioVisualizer()
                    musicPlayer.start()
                    startTimer()

                    delay(250)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show()
            card.isClickable = true
        }
    }

    private fun switchNextSong(context: Context) {
        binding.nextSong.isClickable = false
        binding.previousSong.isClickable = false
        val previousSongIndex = songIndex
        var songName: String

        songIconAudioVisualizer.stopAudioVisualizer()

        try {
            CoroutineScope(Dispatchers.IO).launch {
                songIndex += 1

                if (musicPlayer.isPlaying) {
                    musicPlayer.stop()
                }
                musicPlayer.reset()
                stopTimer()

                if (songIndex >= valuesList.size) {
                    songIndex = 0
                }

                val data = valuesList[songIndex]

                /*valuesList.forEachIndexed { index, songData ->
                    songData.isSelected = false
                    songData.isVisualizerActivated = false
                }*/
                valuesList[previousSongIndex].isSelected = false
                valuesList[previousSongIndex].isVisualizerActivated = false
                valuesList[songIndex].isSelected = true
                valuesList[songIndex].isVisualizerActivated = true

                musicPlayer.setDataSource(data.path)
                musicPlayer.prepare()

                val dataNow = valuesList[songIndex]

                averageVisualizerColor = if (dataNow.icon != null) {
                    val image = dataNow.icon
                    averageImageColorFinder.findAverageColorOnImage(image, true)
                } else {
                    Color.rgb(5, 127, 224)
                }

                //Log.d("index", songIndex.toString())

                songName = dataNow.name ?: dataNow.filename

                withContext(Dispatchers.Main) {
                    setSong(previousSongIndex, songIndex, songName, musicPlayer.audioSessionId, false)
                    binding.songsList.scrollToPosition(songIndex)
                    binding.playSong.setImageResource(android.R.drawable.ic_media_pause)

                    songIconAudioVisualizer.launchAudioVisualizer()
                    musicPlayer.start()
                    startTimer()

                    delay(250)

                    binding.nextSong.isClickable = true
                    binding.previousSong.isClickable = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "$e $songIndex", Toast.LENGTH_SHORT).show()
            binding.nextSong.isClickable = true
            binding.previousSong.isClickable = true
        }
    }

    private fun switchPreviousSong(context: Context) {
        binding.previousSong.isClickable = false
        binding.nextSong.isClickable = false
        val previousSongIndex = songIndex
        var songName: String

        songIconAudioVisualizer.stopAudioVisualizer()

        try {
            CoroutineScope(Dispatchers.IO).launch {
                songIndex -= 1

                if (musicPlayer.isPlaying) {
                    musicPlayer.stop()
                }
                musicPlayer.reset()
                stopTimer()

                if (songIndex < 0) {
                    songIndex = valuesList.size - 1
                }

                val data = valuesList[songIndex]

                /*valuesList.forEachIndexed { index, songData ->
                    songData.isSelected = false
                    songData.isVisualizerActivated = false
                }*/
                valuesList[previousSongIndex].isSelected = false
                valuesList[previousSongIndex].isVisualizerActivated = false
                valuesList[songIndex].isSelected = true
                valuesList[songIndex].isVisualizerActivated = true

                musicPlayer.setDataSource(data.path)
                musicPlayer.prepare()

                val dataNow = valuesList[songIndex]

                averageVisualizerColor = if (dataNow.icon != null) {
                    val image = dataNow.icon
                    averageImageColorFinder.findAverageColorOnImage(image, true)
                } else {
                    Color.rgb(5, 127, 224)
                }

                //Log.d("index", songIndex.toString())

                songName = dataNow.name ?: dataNow.filename

                withContext(Dispatchers.Main) {
                    setSong(previousSongIndex, songIndex, songName, musicPlayer.audioSessionId, false)
                    binding.songsList.scrollToPosition(songIndex)
                    binding.playSong.setImageResource(android.R.drawable.ic_media_pause)

                    songIconAudioVisualizer.launchAudioVisualizer()
                    musicPlayer.start()
                    startTimer()

                    delay(250)

                    binding.previousSong.isClickable = true
                    binding.nextSong.isClickable = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "$e $songIndex", Toast.LENGTH_SHORT).show()
            binding.previousSong.isClickable = true
            binding.nextSong.isClickable = true
        }
    }

    private fun connectToDevice(context: Context, name: String, progressBar: ProgressBar, device: BluetoothDevice, uuid: UUID, position: Int, deviceIcon: ImageView, deviceCard: MaterialCardView) {
        var isConnected = false
        var outputStream: OutputStream? = null
        var inputStream: InputStream? = null

        fun checkSocketConnection(socket: BluetoothSocket?): Boolean {
            return try {
                outputStream = socket?.outputStream
                outputStream?.write("Hello $name !".toByteArray())
                outputStream?.flush()

                inputStream = socket?.inputStream
                val buffer = ByteArray(1024)
                val bytes = inputStream?.read(buffer)

                bytes != null && bytes > 0
            } catch (e: IOException) {
                false
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.cancelDiscovery()

                try {
                    //bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(uuid)
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)

                    withTimeout(5000) {
                        bluetoothSocket?.connect()
                    }
                    //isConnected = bluetoothSocket?.isConnected!!
                    isConnected = checkSocketConnection(bluetoothSocket)
                } catch (e: TimeoutCancellationException) {
                    e.printStackTrace()
                    isConnected = false
                    withContext(Dispatchers.Main) {
                        Snackbar.make(findViewById(android.R.id.content), "Connection timeout: ${e.message}", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    isConnected = false
                    withContext(Dispatchers.Main) {
                        Snackbar.make(findViewById(android.R.id.content), "Connection error: ${e.message}", Snackbar.LENGTH_SHORT).show()
                    }
                } finally {
                    try {
                        outputStream?.close()
                        inputStream?.close()
                        bluetoothSocket?.close()
                    } catch (closeException: IOException) {
                        closeException.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Snackbar.make(findViewById(android.R.id.content), "Close socket error: ${closeException.message}", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                    bluetoothSocket = null
                }
            }

            withContext(Dispatchers.Main) {
                if (isConnected) {
                    binding.pairedDevice.visibility = View.VISIBLE
                    binding.pairedDevice.isSelected = true
                    binding.pairedDevice.text = name
                    progressBar.visibility = View.GONE
                    deviceIcon.visibility = View.VISIBLE
                    binding.bluetoothSwitch.setImageResource(R.drawable.bluetooth_connected)
                    Toast.makeText(context, "Connected to $name !", Toast.LENGTH_LONG).show()
                } else {
                    binding.pairedDevice.visibility = View.GONE
                    binding.pairedDevice.isSelected = false
                    binding.pairedDevice.text = ""
                    progressBar.visibility = View.GONE
                    deviceIcon.visibility = View.VISIBLE
                    Toast.makeText(context, "Not connected to $name !", Toast.LENGTH_LONG).show()
                }

                deviceCard.isClickable = true

                bleList[position].connected = isConnected
                bleDevicesAdapter.updateList(bleList)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchSongsFiles() {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                val dataList = getAllSongsFiles()

                withContext(Dispatchers.Main) {
                    setSongsDataFromList(dataList)

                    binding.filesLoadProgress.visibility = View.GONE
                    binding.songLoadProgress.visibility = View.GONE

                    binding.songsList.visibility = View.VISIBLE
                    binding.songTxtData.visibility = View.VISIBLE
                    binding.actionButtonsPanel.visibility = View.VISIBLE
                    binding.timeProgress.visibility = View.VISIBLE
                    binding.songIcon.visibility = View.VISIBLE

                    if (valuesList.isNotEmpty()) {
                        val songName: String
                        val rndIndex = setRndIndex(0, valuesList.count() - 1)
                        val data = valuesList[rndIndex]

                        averageVisualizerColor = if (data.icon != null) {
                            val image = data.icon
                            averageImageColorFinder.findAverageColorOnImage(image, true)
                        } else {
                            Color.rgb(5, 127, 224)
                        }

                        songName = data.name ?: data.filename

                        setSong(0, rndIndex, songName, musicPlayer.audioSessionId, true)
                    } else {
                        Toast.makeText(applicationContext, "no songs!", Toast.LENGTH_SHORT).show()
                    }

                    binding.songTrackPanel.isClickable = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("Recycle", "SetTextI18n")
    private fun getAllSongsFiles(): ArrayList<SongData> {
        val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DATE_ADDED)

        val selection = "$executeMediaTypeCondition != 0"
        val cursor: Cursor? = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null)

        val dataList: ArrayList<SongData> = arrayListOf()

        cursor?.use {
            val filenameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            var itemPosition = 0

            while (it.moveToNext()) {
                val filename = it.getString(filenameColumn)
                val artist = it.getString(artistColumn)
                val duration = it.getString(durationColumn)
                val path = it.getString(dataColumn)
                val albumArt = getAlbumArt(path)
                val name = getSongData(path)
                val dateCreation = it.getLong(dateColumn) * 1000
                itemPosition += 1

                dataList.add(SongData(name, filename, artist, path, duration, albumArt, itemPosition, dateCreation, false, false))
            }
        }

        return dataList
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setSongsDataFromList(list: ArrayList<SongData>) {
        valuesList.addAll(list)
        songsAdapter.notifyDataSetChanged()
    }

    private fun setRndIndex(min: Int, max: Int): Int {
        var index = (min..max).random()

        if (index < 0) {
            index = 0
        }

        return index
    }
    //coroutines actions area-----------------------------------------------------------------------------------------------------------------------------

    @SuppressLint("SetTextI18n")
    private fun setSong(previousIndex: Int, index: Int, songName: String, sessionId: Int, initializeSong: Boolean) {
        try {
            val data = valuesList[index]
            songIndex = index

            timeMark = 0
            binding.timeProgress.progress = 0

            binding.songName.text = songName

            binding.songAuthor.text = "${data.author} - ${convertToMinutes(data.length.toLong())}"
            binding.timeProgress.max = data.length.toInt()


            if (data.icon != null) {
                binding.songIcon.setImageBitmap(data.icon)
            } else {
                binding.songIcon.setImageResource(R.drawable.not_found_icon)
            }

            songIconAudioVisualizer.setColor(averageVisualizerColor)

            //Log.d("length", data.length)
            //Log.d("max time", binding.timeProgress.max.toString())

            if (initializeSong) {
                musicPlayer.reset()
                musicPlayer.setDataSource(data.path)
                musicPlayer.prepare()
                data.isSelected = true
                songsAdapter.notifyItemChanged(songIndex)
                binding.songsList.scrollToPosition(songIndex)
            } else {
                songsAdapter.notifyItemChanged(songIndex)
                if (previousIndex >= 0) {
                    songsAdapter.notifyItemChanged(previousIndex)
                }
            }

            songIconAudioVisualizer.configureAudioVisualizer(sessionId)

            binding.songName.isSelected = true
            binding.songAuthor.isSelected = true
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            Toast.makeText(this, "$e $index", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getAlbumArt(path: String): Bitmap? {
        val mmr = MediaMetadataRetriever()

        return try {
            mmr.setDataSource(path)
            val art = mmr.embeddedPicture
            if (art != null) {
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            mmr.release()
        }
    }

    private fun getSongData(path: String): String? {
        val mmr = MediaMetadataRetriever()

        return try {
            mmr.setDataSource(path)

            val name = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

            if (album != null && name != null) {
                "$album - $name"
            } else if (name != null) {
                "Unknown album - $name"
            } else if (album != null) {
                "$album - Unknown name"
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            mmr.release()
        }
    }

    private fun convertToMinutes(millis: Long): String {
        val date = Date(millis)
        val sdf = SimpleDateFormat("mm:ss", Locale.getDefault())
        return sdf.format(date)
    }

    private fun startTimer() {
        if (isTimerRunning) return

        isTimerRunning = true

        var elapsedTime = 0

        if (timeMark > 0) {
            elapsedTime = timeMark
        }

        mainHandler = Handler(Looper.getMainLooper())
        val maxTime = binding.timeProgress.max

        runnableTimer = object : Runnable {
            override fun run() {
                //Log.d("timer", "timer working!")
                if (elapsedTime < maxTime) {
                    //Log.d("timer", "timer working!")
                    elapsedTime += 1000
                    timeMark = elapsedTime
                    binding.timeProgress.progress = elapsedTime

                    if (binding.barPanel.visibility == View.VISIBLE) {
                        binding.valueBar.progress = elapsedTime
                    }

                    mainHandler?.postDelayed(runnableTimer!!, 1000)
                } else if (elapsedTime >= maxTime) {
                    switchNextSong(this@MainActivity)
                }
            }
        }
        mainHandler?.post(runnableTimer!!)
    }

    private fun stopTimer() {
        if (!isTimerRunning) return

        isTimerRunning = false
        mainHandler?.removeCallbacks(runnableTimer!!)
        runnableTimer = null
        mainHandler = null
    }

    private fun startBluetoothScan() {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)

        val filter1 = IntentFilter(BluetoothDevice.ACTION_FOUND)
        val filter2 = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        val filter3 = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        bluetoothAdapter = bluetoothManager.adapter
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.startDiscovery()
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            registerReceiver(bluetoothDeviceReceiver, filter1)
            registerReceiver(bluetoothStateReceiver, filter2)
            registerReceiver(bluetoothConnectionsReceiver, filter3)
            searchLabel?.visibility = View.VISIBLE
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun setupDialog() {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialog = inflater.inflate(R.layout.bluetooth_devices_view, null)
        val bleDevicesView = dialog.findViewById<RecyclerView>(R.id.scannedDevicesView)
        searchLabel = dialog.findViewById(R.id.searchLabel)
        notFoundBleDevicesPanel = dialog.findViewById(R.id.pairedDevicesNotFoundPanel)

        builder.setView(dialog)
        builder.setCancelable(false)
        builder.setTitle("Bluetooth devices")
        builder.setIcon(R.drawable.bluetooth)

        builder.setPositiveButton("Close") { dialog, which ->
            dialog.cancel()
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                binding.bluetoothSwitch.setImageResource(R.drawable.bluetooth)
            }
        }

        builder.setNeutralButton("Manage Bluetooth") { dialog, which ->
            if (bluetoothAdapter?.isEnabled!! && bluetoothAdapter != null) {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                startActivity(intent)
            }
        }

        bluetoothDialog = builder.create()

        bluetoothDialog?.show()

        val win = bluetoothDialog?.window
        win?.setBackgroundDrawable(ContextCompat.getDrawable(this, R.color.light_dark_sky))
        //solve problem...
        bluetoothDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
        bluetoothDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.WHITE)

        bleDevicesAdapter = BluetoothDevicesListAdapter(this, this, bleList)
        bleDevicesView.layoutManager = LinearLayoutManager(this)
        bleDevicesView.adapter = bleDevicesAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun getPairedDevices() {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        devicesList = mutableListOf()
        devicesList.clear()

        pairedDevices?.forEach { device ->
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                val bluetoothClass = device.bluetoothClass
                val deviceClass = bluetoothClass.deviceClass

                val name = device.name
                val address = device.address
                var deviceType = "OtherDevice"

                when (deviceClass) {
                    BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> {
                        deviceType = "HeadPhones"
                    }

                    BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> {
                        deviceType = "AudioVideoWearable"
                    }

                    BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> {
                        deviceType = "AudioSpeaker"
                    }
                }

                devicesList.add(device)
                bleList.add(BluetoothDeviceData(name, address, false, deviceType))
                bleDevicesAdapter.notifyDataSetChanged()
            }
        }

        if (bleList.isEmpty()) {
            notFoundBleDevicesPanel.visibility = View.VISIBLE
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestPermissions() {
        isReadAudioPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        isRecordAudioPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        isBluetoothConnectGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        isBluetoothScanGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        isBluetoothGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        isBluetoothAdminGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        isFineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        isCoarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val permissionRequest: MutableList<String> = ArrayList()

        if (!isReadAudioPermissionGranted) {
            permissionRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (!isRecordAudioPermissionGranted) {
            permissionRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        if (!isBluetoothConnectGranted) {
            permissionRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (!isBluetoothScanGranted) {
            permissionRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (!isBluetoothGranted) {
            permissionRequest.add(Manifest.permission.BLUETOOTH)
        }

        if (!isBluetoothAdminGranted) {
            permissionRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (!isFineLocationGranted) {
            permissionRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (!isCoarseLocationGranted) {
            permissionRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (musicPlayer.isPlaying) {
            musicPlayer.stop()
        }
        musicPlayer.release()
        songIconAudioVisualizer.destroyAudioVisualizer()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            unregisterReceiver(bluetoothDeviceReceiver)
            binding.bluetoothSwitch.setImageResource(R.drawable.bluetooth)
        }
    }
}
