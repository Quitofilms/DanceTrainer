package com.example.dancetrainer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HashtagAdapter(
    private var hashtags: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<HashtagAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTag: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag = hashtags[position]
        holder.tvTag.text = tag
        holder.itemView.setOnClickListener { onClick(tag) }
    }

    override fun getItemCount() = hashtags.size

    fun updateList(newList: List<String>) {
        hashtags = newList
        notifyDataSetChanged()
    }
}