package com.example.dancetrainer

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var videoAdapter: VideoAdapter
    private var allVideosList = mutableListOf<DanceVideo>()
    private var isFilterStarred = false
    private var activeTagFilter: String? = null

    private val TEST_VIDEO_URL = "https://www.youtube.com/watch?v=atlkqWeTiok"
    private val baselineTags = listOf("lindy", "charleston", "swing", "jive", "shag", "collegiate shag", "style", "clothes", "music")

    private val selectVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, it)
            }
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecentVideosList()
        handleIncomingFile(intent) // Check for shared .dance files

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Chip>(R.id.chipStarred).setOnCheckedChangeListener { _, isChecked ->
            isFilterStarred = isChecked
            applyFilters()
        }

        findViewById<Button>(R.id.btnAddLocalVideo).setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        findViewById<SearchView>(R.id.searchView).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                applyFilters(newText)
                return true
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingFile(intent)
    }

    private fun handleIncomingFile(intent: Intent) {
        val uri: Uri? = intent.data
        if (intent.action == Intent.ACTION_VIEW && uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val data = reader.readLine() ?: return
                    val parts = data.split("|") // Title|URL|Notes
                    if (parts.size >= 2) {
                        lifecycleScope.launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.danceVideoDao().insertVideo(DanceVideo(
                                title = parts[0],
                                videoUrl = parts[1],
                                hashtags = "",
                                notes = parts.getOrElse(2) { "" }
                            ))
                            loadVideosFromDb()
                            Toast.makeText(this@MainActivity, "Imported: ${parts[0]}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to import move", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadVideosFromDb()
    }

    private fun setupDynamicHashtags() {
        val chipGroup = findViewById<ChipGroup>(R.id.cgHashtagSuggestions)
        chipGroup.removeAllViews()

        val discoveredTags = allVideosList.flatMap { video ->
            video.hashtags.split(" ").filter { it.isNotBlank() }
        }
        val allUniqueTags = (baselineTags + discoveredTags).distinct().sorted()

        val typedValue = TypedValue()
        val textColor = if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
            typedValue.data
        } else {
            Color.BLACK
        }

        allUniqueTags.forEach { tag ->
            val count = allVideosList.count { it.hashtags.contains(tag, ignoreCase = true) }
            val chip = Chip(this).apply {
                text = "$tag ($count)"
                isCheckable = true
                isChecked = (activeTagFilter == tag)
                setTextColor(ColorStateList.valueOf(textColor))
                setOnClickListener {
                    activeTagFilter = if (this.isChecked) tag else null
                    applyFilters()
                    for (i in 0 until chipGroup.childCount) {
                        (chipGroup.getChildAt(i) as? Chip)?.let { if (it != this) it.isChecked = false }
                    }
                }
                setOnLongClickListener {
                    if (!baselineTags.contains(tag)) showDeleteTagDialog(tag)
                    else Toast.makeText(context, "Cannot delete baseline tags", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun showDeleteTagDialog(tag: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Tag")
            .setMessage("Remove #$tag from all videos?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val dao = db.danceVideoDao()
                    allVideosList.forEach { video ->
                        if (video.hashtags.contains(tag)) {
                            val updatedTags = video.hashtags.split(" ").filter { it != tag }.joinToString(" ")
                            dao.insertVideo(video.copy(hashtags = updatedTags))
                        }
                    }
                    loadVideosFromDb()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadVideosFromDb() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.danceVideoDao()
            if (dao.getAllVideos().none { it.videoUrl == TEST_VIDEO_URL }) {
                dao.insertVideo(DanceVideo(title = "Seed: Lindy Hop Practice", videoUrl = TEST_VIDEO_URL, hashtags = "lindy swing", notes = "Hardcoded test link #lindy #swing", isStarred = true))
            }
            allVideosList = dao.getAllVideos().toMutableList()
            setupDynamicHashtags()
            applyFilters()
        }
    }

    private fun applyFilters(query: String? = null) {
        if (!::videoAdapter.isInitialized) return
        var filteredList = if (isFilterStarred) allVideosList.filter { it.isStarred } else allVideosList
        activeTagFilter?.let { tag -> filteredList = filteredList.filter { it.hashtags.contains(tag, ignoreCase = true) } }
        if (!query.isNullOrEmpty()) { filteredList = filteredList.filter { it.title.contains(query, ignoreCase = true) } }
        videoAdapter.updateList(filteredList)
    }

    private fun setupRecentVideosList() {
        val rvRecentVideos = findViewById<RecyclerView>(R.id.rvRecentVideos)
        videoAdapter = VideoAdapter(mutableListOf()) { video ->
            val intent = Intent(this, VideoPlayerActivity::class.java).apply { putExtra(Intent.EXTRA_TEXT, video.videoUrl) }
            startActivity(intent)
        }
        rvRecentVideos.layoutManager = LinearLayoutManager(this)
        rvRecentVideos.adapter = videoAdapter
    }
}