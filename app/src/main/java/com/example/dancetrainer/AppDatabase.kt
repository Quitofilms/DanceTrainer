package com.example.dancetrainer

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(entities = [DanceVideo::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun danceVideoDao(): DanceVideoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dance_trainer_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}