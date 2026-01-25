package com.example.dancetrainer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.regex.Pattern

class VideoAdapter(
    private var videos: List<DanceVideo>,
    private val onClick: (DanceVideo) -> Unit
) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        holder.tvTitle.text = if (video.isStarred) "⭐ ${video.title}" else video.title
        
        val youtubeId = extractYoutubeId(video.videoUrl)
        if (youtubeId != null) {
            val thumbUrl = "https://img.youtube.com/vi/$youtubeId/mqdefault.jpg"
            Glide.with(holder.itemView.context)
                .load(thumbUrl)
                .placeholder(android.R.drawable.ic_menu_slideshow)
                .into(holder.ivThumbnail)
        } else {
            // Local video thumbnail
            Glide.with(holder.itemView.context)
                .load(video.videoUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivThumbnail)
        }

        holder.itemView.setOnClickListener { onClick(video) }
    }

    private fun extractYoutubeId(url: String): String? {
        val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/shorts/)[^#&?]*"
        val matcher = Pattern.compile(pattern).matcher(url)
        return if (matcher.find()) matcher.group() else null
    }

    override fun getItemCount() = videos.size

    fun updateList(newVideos: List<DanceVideo>) {
        videos = newVideos
        notifyDataSetChanged()
    }
}