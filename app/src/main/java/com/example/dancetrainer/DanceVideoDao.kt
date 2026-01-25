package com.example.dancetrainer

import androidx.room.*

@Dao
interface DanceVideoDao {
    @Query("SELECT * FROM dance_videos WHERE hashtags LIKE '%' || :query || '%'")
    suspend fun searchByHashtag(query: String): List<DanceVideo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: DanceVideo)

    @Delete
    suspend fun deleteVideo(video: DanceVideo) // New delete method

    @Query("UPDATE dance_videos SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarredStatus(id: Int, isStarred: Boolean) // New star toggle

    @Query("SELECT * FROM dance_videos ORDER BY id DESC")
    suspend fun getAllVideos(): List<DanceVideo>
}