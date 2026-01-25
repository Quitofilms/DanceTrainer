package com.example.dancetrainer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dance_videos")
data class DanceVideo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val videoUrl: String,
    val hashtags: String,
    val notes: String,
    val isStarred: Boolean = false
)