package com.example.dancetrainer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var youtubePlayerView: YouTubePlayerView
    private lateinit var playerView: PlayerView
    private lateinit var videoContainer: FrameLayout
    private lateinit var controlsScrollView: ScrollView
    private lateinit var btnCloseFullscreen: ImageButton
    private lateinit var resizeHandle: View
    private lateinit var ivPlaceholderThumbnail: ImageView
    private lateinit var tvYoutubeFallback: TextView
    private lateinit var controlBlock: LinearLayout
    
    private lateinit var btnExternalLink: Button
    private lateinit var etVideoTitle: EditText
    private lateinit var etNotes: EditText
    private lateinit var cbStar: CheckBox
    
    private var exoPlayer: ExoPlayer? = null
    private var activeYouTubePlayer: YouTubePlayer? = null
    private var currentVideoUrl: String? = null
    private var internalVideoUri: Uri? = null
    private var existingVideoId: Int = 0
    private var isMirrored = false
    private var isFullscreen = false
    
    private var currentYoutubeTime: Float = 0f
    private var isYoutubePlaying: Boolean = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        youtubePlayerView = findViewById(R.id.youtubePlayerView)
        playerView = findViewById(R.id.playerView)
        videoContainer = findViewById(R.id.videoContainer)
        controlsScrollView = findViewById(R.id.controlsScrollView)
        btnCloseFullscreen = findViewById(R.id.btnCloseFullscreen)
        resizeHandle = findViewById(R.id.resizeHandle)
        ivPlaceholderThumbnail = findViewById(R.id.ivPlaceholderThumbnail)
        tvYoutubeFallback = findViewById(R.id.tvYoutubeFallback)
        controlBlock = findViewById(R.id.controlBlock)
        
        btnExternalLink = findViewById(R.id.btnExternalLink)
        etVideoTitle = findViewById(R.id.etVideoTitle)
        etNotes = findViewById(R.id.etNotes)
        cbStar = findViewById(R.id.cbStar)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedSize = prefs.getFloat("font_size", 16f)
        etNotes.textSize = savedSize
        etVideoTitle.textSize = savedSize

        lifecycle.addObserver(youtubePlayerView)
        handleIntent(intent)

        setupResizeHandle()
        setupFocusListeners()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { 
            if (etVideoTitle.hasFocus() || etNotes.hasFocus()) {
                clearFocusAndHideKeyboard()
            } else {
                finish() 
            }
        }
        
        findViewById<Button>(R.id.btnSave).setOnClickListener { 
            saveVideoData() 
            clearFocusAndHideKeyboard()
        }
        
        findViewById<Button>(R.id.btnDelete).setOnClickListener { deleteVideo() }
        findViewById<Button>(R.id.btnMirror).setOnClickListener { toggleMirror() }
        findViewById<Button>(R.id.btnFullscreen).setOnClickListener { enterFullscreen() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareVideoData() }
        btnCloseFullscreen.setOnClickListener { exitFullscreen() }

        btnExternalLink.setOnClickListener {
            currentVideoUrl?.let { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }

        findViewById<Button>(R.id.btnSpeed025).setOnClickListener { setPlaybackSpeed(0.25f) }
        findViewById<Button>(R.id.btnSpeed050).setOnClickListener { setPlaybackSpeed(0.50f) }
        findViewById<Button>(R.id.btnSpeed075).setOnClickListener { setPlaybackSpeed(0.75f) }
        findViewById<Button>(R.id.btnSpeed100).setOnClickListener { setPlaybackSpeed(1.0f) }
        
        findViewById<Button>(R.id.btnRewind).setOnClickListener { seekRelative(-10) }
        findViewById<Button>(R.id.btnForward).setOnClickListener { seekRelative(10) }
        findViewById<Button>(R.id.btnPlayPause).setOnClickListener { togglePlayPause() }
    }

    private fun setupFocusListeners() {
        val focusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                videoContainer.visibility = View.GONE
                resizeHandle.visibility = View.GONE
                findViewById<View>(R.id.ivResizeIndicator).visibility = View.GONE
                controlBlock.visibility = View.GONE
            } else if (!etVideoTitle.hasFocus() && !etNotes.hasFocus()) {
                videoContainer.visibility = View.VISIBLE
                resizeHandle.visibility = View.VISIBLE
                findViewById<View>(R.id.ivResizeIndicator).visibility = View.VISIBLE
                controlBlock.visibility = View.VISIBLE
            }
        }
        
        etVideoTitle.onFocusChangeListener = focusListener
        etNotes.onFocusChangeListener = focusListener
    }

    private fun clearFocusAndHideKeyboard() {
        etVideoTitle.clearFocus()
        etNotes.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeHandle() {
        var lastY = 0f
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - lastY
                    val params = videoContainer.layoutParams
                    val newHeight = (videoContainer.height + deltaY).toInt()
                    
                    val minHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150f, resources.displayMetrics).toInt()
                    val maxHeight = resources.displayMetrics.heightPixels - 500
                    
                    if (newHeight in minHeight..maxHeight) {
                        params.height = newHeight
                        videoContainer.layoutParams = params
                    }
                    
                    lastY = event.rawY
                    true
                }
                else -> false
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val sharedUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        
        currentVideoUrl = sharedText ?: sharedUri?.toString()
        if (currentVideoUrl == null) return

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@VideoPlayerActivity)
            val existing = db.danceVideoDao().getAllVideos().find { it.videoUrl == currentVideoUrl }
            existing?.let {
                existingVideoId = it.id
                etVideoTitle.setText(it.title)
                etNotes.setText(it.notes)
                cbStar.isChecked = it.isStarred
            }

            val youtubeId = sharedText?.let { extractYoutubeId(it) }
            if (youtubeId != null) {
                playYouTubeVideo(youtubeId)
            } else if (sharedUri != null) {
                processLocalVideo(sharedUri)
            } else if (sharedText != null) {
                playLocalVideo(Uri.parse(sharedText))
            }
        }
    }

    private suspend fun processLocalVideo(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val file = File(filesDir, "video_${System.currentTimeMillis()}.mp4")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                
                withContext(Dispatchers.Main) {
                    internalVideoUri = Uri.fromFile(file)
                    currentVideoUrl = internalVideoUri.toString()
                    playLocalVideo(internalVideoUri!!)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoPlayerActivity, "Error saving video copy", Toast.LENGTH_SHORT).show()
                    playLocalVideo(uri)
                }
            }
        }
    }

    private fun playYouTubeVideo(videoId: String) {
        youtubePlayerView.visibility = View.VISIBLE
        playerView.visibility = View.GONE
        
        ivPlaceholderThumbnail.visibility = View.VISIBLE
        Glide.with(this)
            .load("https://img.youtube.com/vi/$videoId/maxresdefault.jpg")
            .into(ivPlaceholderThumbnail)

        youtubePlayerView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                activeYouTubePlayer = youTubePlayer
                youTubePlayer.loadVideo(videoId, 0f)
                ivPlaceholderThumbnail.visibility = View.GONE
            }
            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                currentYoutubeTime = second
            }
            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                isYoutubePlaying = state == PlayerConstants.PlayerState.PLAYING
            }
            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                ivPlaceholderThumbnail.visibility = View.VISIBLE
                tvYoutubeFallback.visibility = View.VISIBLE
                btnExternalLink.visibility = View.VISIBLE
            }
        })
    }

    private fun playLocalVideo(uri: Uri) {
        youtubePlayerView.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        ivPlaceholderThumbnail.visibility = View.GONE
        tvYoutubeFallback.visibility = View.GONE
        
        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Toast.makeText(this@VideoPlayerActivity, "Cannot play video: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
        playerView.player = exoPlayer
    }

    private fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
        val speedString = when(speed) {
            0.25f -> "0_25"
            0.50f -> "0_5"
            0.75f -> "0_75"
            else -> "1"
        }
        val ytRate = PlayerConstants.PlaybackRate.entries.find { it.name.contains(speedString) } ?: PlayerConstants.PlaybackRate.UNKNOWN
        if (ytRate != PlayerConstants.PlaybackRate.UNKNOWN) {
            activeYouTubePlayer?.setPlaybackRate(ytRate)
        }
    }

    private fun togglePlayPause() {
        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
        activeYouTubePlayer?.let { if (isYoutubePlaying) it.pause() else it.play() }
    }

    private fun seekRelative(seconds: Int) {
        exoPlayer?.let {
            val newPos = it.currentPosition + (seconds * 1000)
            it.seekTo(newPos.coerceAtLeast(0))
        }
        activeYouTubePlayer?.let {
            val newTime = currentYoutubeTime + seconds
            it.seekTo(newTime.coerceAtLeast(0f))
        }
    }

    private fun toggleMirror() {
        isMirrored = !isMirrored
        val scale = if (isMirrored) -1f else 1f
        playerView.scaleX = scale
        youtubePlayerView.scaleX = scale
    }

    private fun enterFullscreen() {
        isFullscreen = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        controlsScrollView.visibility = View.GONE
        btnCloseFullscreen.visibility = View.VISIBLE
        resizeHandle.visibility = View.GONE
        findViewById<View>(R.id.ivResizeIndicator).visibility = View.GONE
        
        val params = videoContainer.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.dimensionRatio = null
        videoContainer.layoutParams = params
        
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN 
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION 
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun exitFullscreen() {
        isFullscreen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        controlsScrollView.visibility = View.VISIBLE
        btnCloseFullscreen.visibility = View.GONE
        resizeHandle.visibility = View.VISIBLE
        findViewById<View>(R.id.ivResizeIndicator).visibility = View.VISIBLE
        
        val params = videoContainer.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 250f, resources.displayMetrics).toInt()
        params.dimensionRatio = null
        videoContainer.layoutParams = params
        
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (isFullscreen) {
            exitFullscreen()
        } else if (etVideoTitle.hasFocus() || etNotes.hasFocus()) {
            clearFocusAndHideKeyboard()
        } else {
            super.onBackPressed()
        }
    }

    private fun shareVideoData() {
        val title = etVideoTitle.text.toString()
        val notes = etNotes.text.toString()
        val url = currentVideoUrl ?: ""
        
        val shareBody = buildString {
            append("Move: $title\n\n")
            if (url.startsWith("http")) {
                append("Link: $url\n\n")
            }
            if (notes.isNotEmpty()) {
                append("Notes: $notes")
            }
        }
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Dance Trainer Move: $title")
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Move Using"))
    }

    private fun extractYoutubeId(url: String): String? {
        val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/shorts/)[^#&?]*"
        val matcher = Pattern.compile(pattern).matcher(url)
        return if (matcher.find()) matcher.group() else null
    }

    private fun saveVideoData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@VideoPlayerActivity)
            val notes = etNotes.text.toString()
            val extractedHashtags = Pattern.compile("#(\\w+)").matcher(notes)
            val hashtagsList = mutableListOf<String>()
            while (extractedHashtags.find()) {
                extractedHashtags.group(1)?.let { hashtagsList.add(it.lowercase()) }
            }
            val hashtagsString = hashtagsList.distinct().joinToString(" ")
            
            db.danceVideoDao().insertVideo(DanceVideo(
                id = existingVideoId,
                title = etVideoTitle.text.toString(),
                videoUrl = currentVideoUrl ?: "",
                hashtags = hashtagsString,
                notes = notes,
                isStarred = cbStar.isChecked
            ))
            Toast.makeText(this@VideoPlayerActivity, "Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteVideo() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@VideoPlayerActivity)
            val existing = db.danceVideoDao().getAllVideos().find { it.videoUrl == currentVideoUrl }
            existing?.let {
                if (it.videoUrl.startsWith("file://")) {
                    Uri.parse(it.videoUrl).path?.let { path -> File(path).delete() }
                }
                db.danceVideoDao().deleteVideo(it)
                Toast.makeText(this@VideoPlayerActivity, "Deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}