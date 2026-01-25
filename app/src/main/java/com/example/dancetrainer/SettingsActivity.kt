package com.example.dancetrainer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsActivity : AppCompatActivity() {

    // File picker for Import
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importJsonData(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Font Size Controls
        findViewById<Button>(R.id.btnIncreaseFont).setOnClickListener {
            val currentSize = prefs.getFloat("font_size", 16f)
            prefs.edit().putFloat("font_size", currentSize + 2f).apply()
            Toast.makeText(this, "Font increased to ${currentSize + 2f}sp", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnDecreaseFont).setOnClickListener {
            val currentSize = prefs.getFloat("font_size", 16f)
            if (currentSize > 10f) {
                prefs.edit().putFloat("font_size", currentSize - 2f).apply()
                Toast.makeText(this, "Font decreased to ${currentSize - 2f}sp", Toast.LENGTH_SHORT).show()
            }
        }

        // Data Management
        findViewById<Button>(R.id.btnExport).setOnClickListener { exportDatabaseToJson() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { importLauncher.launch("application/json") }
    }

    private fun exportDatabaseToJson() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@SettingsActivity)
            val videos = db.danceVideoDao().getAllVideos()
            val jsonArray = JSONArray()

            videos.forEach { video ->
                val jsonObj = JSONObject().apply {
                    put("title", video.title)
                    put("url", video.videoUrl)
                    put("notes", video.notes)
                    put("hashtags", video.hashtags)
                    put("isStarred", video.isStarred)
                }
                jsonArray.put(jsonObj)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, jsonArray.toString(4))
                putExtra(Intent.EXTRA_SUBJECT, "DanceTrainer_Backup.json")
            }
            startActivity(Intent.createChooser(shareIntent, "Export Backup via..."))
        }
    }

    private fun importJsonData(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.use { it.readText() }
                val jsonArray = JSONArray(jsonString)

                val db = AppDatabase.getDatabase(this@SettingsActivity)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val video = DanceVideo(
                        title = obj.getString("title"),
                        videoUrl = obj.getString("url"),
                        notes = obj.getString("notes"),
                        hashtags = obj.getString("hashtags"),
                        isStarred = obj.optBoolean("isStarred", false)
                    )
                    db.danceVideoDao().insertVideo(video)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Import Successful!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Error importing file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}