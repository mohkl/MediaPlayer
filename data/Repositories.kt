package com.mediaplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.mediaplayer.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MediaStoreRepository(private val ctx: Context) : MediaRepository {

    override suspend fun scan(): List<MediaFile> = withContext(Dispatchers.IO) {
        query(true) + query(false)
    }

    private fun query(video: Boolean): List<MediaFile> {
        val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val durCol = if (video) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION
        val artCol = if (video) MediaStore.Video.Media.ARTIST else MediaStore.Audio.Media.ARTIST
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            durCol,
            artCol,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE
        )
        val out = ArrayList<MediaFile>()
        ctx.contentResolver.query(
            base, projection, "$durCol > 0", null,
            "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(durCol)
            val iArt = c.getColumnIndexOrThrow(artCol)
            val iData = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val name = c.getString(iName) ?: "ملف"
                val artist = c.getString(iArt)?.takeIf { it != "<unknown>" } ?: ""
                val path = c.getString(iData)
                val parent = path?.let { File(it).parentFile }
                out += MediaFile(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id).toString(),
                    title = name.substringBeforeLast('.'),
                    artist = artist,
                    durationMs = c.getLong(iDur),
                    folder = parent?.name ?: "غير معروف",
                    folderPath = parent?.absolutePath ?: "unknown",
                    isVideo = video,
                    sizeBytes = c.getLong(iSize)
                )
            }
        }
        return out
    }
}

private fun MediaFile.toJson(): JSONObject = JSONObject()
    .put("id", id).put("uri", uri).put("title", title).put("artist", artist)
    .put("dur", durationMs).put("folder", folder).put("path", folderPath)
    .put("video", isVideo).put("size", sizeBytes)

private fun JSONObject.toMediaFile() = MediaFile(
    getLong("id"), getString("uri"), getString("title"), optString("artist"),
    optLong("dur"), optString("folder"), optString("path"), optBoolean("video"), optLong("size")
)

class PrefsPlaylistRepository(ctx: Context) : PlaylistRepository {
    private val sp = ctx.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    override fun load(): List<Playlist> = try {
        val arr = JSONArray(sp.getString("data", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val items = o.getJSONArray("items")
            Playlist(o.getString("id"), o.getString("name"),
                (0 until items.length()).map { j -> items.getJSONObject(j).toMediaFile() })
        }
    } catch (e: Exception) {
        emptyList()
    }

    override fun save(playlists: List<Playlist>) {
        val arr = JSONArray()
        for (p in playlists) {
            val items = JSONArray()
            for (f in p.items) items.put(f.toJson())
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("items", items))
        }
        sp.edit().putString("data", arr.toString()).apply()
    }
}

class PrefsSettingsRepository(ctx: Context) : SettingsRepository {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    override fun themeMode(): Int = sp.getInt("theme", 0)
    override fun setThemeMode(mode: Int) { sp.edit().putInt("theme", mode).apply() }
    override fun welcomeShown(): Boolean = sp.getBoolean("welcome", false)
    override fun setWelcomeShown() { sp.edit().putBoolean("welcome", true).apply() }
    override fun rating(): Int = sp.getInt("rating", 0)
    override fun setRating(value: Int) { sp.edit().putInt("rating", value).apply() }
}
