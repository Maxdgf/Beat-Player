package com.example.beatplayer.ItemAdapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.beatplayer.Modules.RGBColorGenerator
import com.example.beatplayer.R
import com.example.beatplayer.ItemAdapters.AdapterDataModels.SongData
import com.example.beatplayer.Modules.DataFormatter
import com.google.android.material.card.MaterialCardView
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Locale

class SongsListAdapter(private val context: Context, private val listener: OnItemClickListener, private var dataList:ArrayList<SongData>): RecyclerView.Adapter<SongsListAdapter.songDataViewHolder>() {

    private var dataFormatter: DataFormatter = DataFormatter()

    inner class songDataViewHolder(private val view: View): RecyclerView.ViewHolder(view) {
        val songName: TextView = view.findViewById(R.id.nameView)
        val songData: TextView = view.findViewById(R.id.dataView)
        val songIcon: ImageView = view.findViewById(R.id.iconView)
        val iconBorder:  MaterialCardView = view.findViewById(R.id.iconBorder)
        val songCard: MaterialCardView = view.findViewById(R.id.generalNoteViewCardWidget)
        val songCardBg: ConstraintLayout = view.findViewById(R.id.cardBg)
        val songNum: TextView = view.findViewById(R.id.numView)
        val songDate: TextView = view.findViewById(R.id.dateView)
        val songSelectedDetector: ImageView = view.findViewById(R.id.songSelectedDetector)
    }

    interface OnItemClickListener {
        fun onMusicPlay(path: String, icon: Bitmap?, name: String, data: String, length: Int, position: Int, card: MaterialCardView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): songDataViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.song_item_card, parent, false)
        return songDataViewHolder(view)
    }

    @SuppressLint("Recycle")
    override fun onBindViewHolder(holder: songDataViewHolder, position: Int) {
        val list = dataList[position]
        val RGBColorGenerator = RGBColorGenerator()
        val dotAnimation = AnimationUtils.loadAnimation(context, R.anim.dot_flash)

        when (list.isSelected) {
            true -> {
                holder.songCardBg.background = ContextCompat.getDrawable(context,
                    R.drawable.action_panel_selected
                )
                holder.songCard.strokeColor = RGBColorGenerator.randomColor(255, 255, 255)
                holder.iconBorder.strokeColor = ContextCompat.getColor(context, R.color.lemon)
                holder.songName.setTextColor(ContextCompat.getColor(context, R.color.lemon))
                holder.songSelectedDetector.visibility = View.VISIBLE
                holder.songSelectedDetector.startAnimation(dotAnimation)
            }

            else -> {
                holder.songCard.isClickable = true
                holder.iconBorder.strokeColor = ContextCompat.getColor(context, R.color.transparent)
                holder.songName.setTextColor(ContextCompat.getColor(context, R.color.white))
                holder.songCardBg.background = ContextCompat.getDrawable(context,
                    R.drawable.action_panel_background
                )
                holder.songSelectedDetector.visibility = View.GONE
                holder.songCard.strokeColor = ContextCompat.getColor(context, R.color.dark_sky)
                dotAnimation.cancel()
            }
        }

        val minutes = list.length.toLong()
        val timeMillis = list.date

        val songData = "${list.author} - ${dataFormatter.formatData(minutes, "mm:ss")}"

        val songDateCreation = dataFormatter.formatData(timeMillis, "dd.MM.yyyy HH:mm:ss")

        val songName: String

        if (list.name != null) {
            holder.songName.text = list.name
            songName = list.name
        } else {
            holder.songName.text = list.filename
            songName = list.filename
        }

        holder.songData.text = songData
        holder.songNum.text = list.pos.toString()
        holder.songDate.text = songDateCreation

        if (list.icon != null) {
            holder.songIcon.setImageBitmap(list.icon)
        } else {
            holder.songIcon.setImageResource(R.drawable.not_found_icon)
        }

        holder.songName.isSelected = true
        holder.songData.isSelected = true

        holder.songCard.setOnClickListener {
            if (!list.isSelected) {
                holder.songCard.isClickable = false
            }
            listener.onMusicPlay(list.path, list.icon, songName, songData, list.length.toInt(), position, holder.songCard)
        }
    }

    override fun getItemCount(): Int {
        return dataList.size
    }
}