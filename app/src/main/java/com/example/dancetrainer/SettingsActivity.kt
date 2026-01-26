package com.example.dancetrainer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var cbIncludeVideos: CheckBox

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        cbIncludeVideos = findViewById(R.id.cbIncludeVideos)
        displayAppVersion()

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        findViewById<Button>(R.id.btnIncreaseFont).setOnClickListener {
            val currentSize = prefs.getFloat("font_size", 16f)
            prefs.edit().putFloat("font_size", currentSize + 2f).apply()
            Toast.makeText(this, "Font increased", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnDecreaseFont).setOnClickListener {
            val currentSize = prefs.getFloat("font_size", 16f)
            if (currentSize > 10f) {
                prefs.edit().putFloat("font_size", currentSize - 2f).apply()
                Toast.makeText(this, "Font decreased", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener { exportBackup() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { importLauncher.launch("*/*") }
        
        findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener { checkForUpdatesManually() }
    }

    private fun displayAppVersion() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode.toLong()
            findViewById<TextView>(R.id.tvAppVersion).text = "VERSION: ${pInfo.versionName} (Code: $code)"
        } catch (e: Exception) {}
    }

    private fun checkForUpdatesManually() {
        val versionUrl = "http://www.swingdancent.com/DanceTrainer/version.txt"
        val apkUrl = "http://www.swingdancent.com/DanceTrainer/DanceTrainer.apk"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latestVersionStr = URL(versionUrl).readText().trim()
                val latestVersionCode = latestVersionStr.toLong()

                val pInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toLong()
                }

                withContext(Dispatchers.Main) {
                    if (latestVersionCode > currentVersionCode) {
                        showUpdateDialog(apkUrl)
                    } else {
                        Toast.makeText(this@SettingsActivity, "You are on the latest version!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Failed to check for updates. Check internet connection.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("UPDATE AVAILABLE")
            .setMessage("A new version of Dance Trainer is ready. Download the latest APK?")
            .setPositiveButton("DOWNLOAD") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                startActivity(intent)
            }
            .setNegativeButton("LATER", null)
            .show()
    }

    private fun exportBackup() {
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

            val jsonString = jsonArray.toString(4)

            if (cbIncludeVideos.isChecked) {
                createZipBackup(jsonString, videos)
            } else {
                shareJsonText(jsonString)
            }
        }
    }

    private fun shareJsonText(json: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
            putExtra(Intent.EXTRA_SUBJECT, "DanceTrainer_Backup.json")
        }
        startActivity(Intent.createChooser(intent, "Export Backup via..."))
    }

    private fun createZipBackup(json: String, videos: List<DanceVideo>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val zipFile = File(cacheDir, "DanceTrainer_Full_Backup.zip")
                ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                    zos.putNextEntry(ZipEntry("data.json"))
                    zos.write(json.toByteArray())
                    zos.closeEntry()

                    videos.forEach { video ->
                        if (video.videoUrl.startsWith("file://")) {
                            val path = Uri.parse(video.videoUrl).path
                            if (path != null) {
                                val videoFile = File(path)
                                if (videoFile.exists()) {
                                    zos.putNextEntry(ZipEntry(videoFile.name))
                                    videoFile.inputStream().copyTo(zos)
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(this@SettingsActivity, "$packageName.fileprovider", zipFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Export Full Backup..."))
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "Export failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun handleImport(uri: Uri) {
        val type = contentResolver.getType(uri)
        if (type == "application/zip" || uri.toString().endsWith(".zip")) {
            importZipBackup(uri)
        } else {
            importJsonData(uri)
        }
    }

    private fun importJsonData(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@launch
                processJsonImport(jsonString)
                withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "Import Successful!", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "Import failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun importZipBackup(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var jsonString: String? = null
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "data.json") {
                            jsonString = zis.bufferedReader().readText()
                        } else {
                            val destFile = File(filesDir, entry.name)
                            destFile.outputStream().use { zis.copyTo(it) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                jsonString?.let { processJsonImport(it) }
                withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "Full Import Successful!", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "ZIP Import failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private suspend fun processJsonImport(jsonString: String) {
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
    }
}