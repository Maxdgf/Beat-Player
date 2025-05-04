package com.example.beatplayer.Bluetooth

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.beatplayer.R
import com.google.android.material.card.MaterialCardView

class BluetoothDevicesListAdapter(private val context: Context, private val listener: OnItemClickListener, private var dataList:ArrayList<BluetoothDeviceData>): RecyclerView.Adapter<BluetoothDevicesListAdapter.deviceDataHolder>() {

    inner class deviceDataHolder(private val view: View): RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.deviceName)
        val deviceAddress: TextView = view.findViewById(R.id.deviceAddress)
        val deviceCard: MaterialCardView = view.findViewById(R.id.bleGeneralDeviceCard)
        val deviceCardBg: ConstraintLayout = view.findViewById(R.id.deviceBg)
        val connectDeviceProgress: ProgressBar = view.findViewById(R.id.deviceConnectProgress)
        val deviceType: ImageView = view.findViewById(R.id.deviceType)
    }

    interface OnItemClickListener {
        fun onBleDeviceSelected(address: String, name: String, position: Int, progressBar: ProgressBar, deviceIcon: ImageView, deviceCard: MaterialCardView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): deviceDataHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.bluetooth_device_item, parent, false)
        return deviceDataHolder(view)
    }

    override fun onBindViewHolder(holder: deviceDataHolder, position: Int) {
        val list = dataList[position]

        holder.deviceName.text = list.name
        holder.deviceAddress.text = list.address

        if (list.connected) {
            holder.deviceCardBg.background = ContextCompat.getDrawable(context,
                R.drawable.action_panel_selected
            )
        } else {
            holder.deviceCardBg.background = ContextCompat.getDrawable(context,
                R.drawable.action_panel_background
            )
        }

        when (list.type) {
            "HeadPhones" -> {
                holder.deviceType.setImageResource(R.drawable.headphones)
            }

            "AudioVideoWearable" -> {
                holder.deviceType.setImageResource(R.drawable.media_output)
            }

            "AudioSpeaker" -> {
                holder.deviceType.setImageResource(R.drawable.speaker)
            }

            "OtherDevice" -> {
                holder.deviceType.setImageResource(R.drawable.devices)
            }
        }

        holder.deviceCard.setOnClickListener {
            holder.connectDeviceProgress.visibility = View.VISIBLE
            holder.deviceType.visibility = View.GONE
            holder.deviceCard.isClickable = false

            listener.onBleDeviceSelected(list.address, list.name, position, holder.connectDeviceProgress, holder.deviceType, holder.deviceCard)
        }
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: ArrayList<BluetoothDeviceData>) {
        dataList = newList
        notifyDataSetChanged()
    }
}