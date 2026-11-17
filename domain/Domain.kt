package com.mediaplayer.app.domain

import java.util.UUID

data class MediaFile(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val folder: String,
    val folderPath: String,
    val isVideo: Boolean,
    val sizeBytes: Long
)

data class FolderGroup(val path: String, val name: String, val items: List<MediaFile>)

data class Playlist(val id: String, val name: String, val items: List<MediaFile>)

interface MediaRepository { suspend fun scan(): List<MediaFile> }

interface PlaylistRepository {
    fun load(): List<Playlist>
    fun save(playlists: List<Playlist>)
}

interface SettingsRepository {
    /** 0 = حسب النظام، 1 = فاتح، 2 = داكن */
    fun themeMode(): Int
    fun setThemeMode(mode: Int)
    fun welcomeShown(): Boolean
    fun setWelcomeShown()
    fun rating(): Int
    fun setRating(value: Int)
}

class LoadLibraryUseCase(private val repo: MediaRepository) {
    suspend operator fun invoke(): List<MediaFile> = repo.scan()
}

class GroupByFolderUseCase {
    operator fun invoke(items: List<MediaFile>): List<FolderGroup> =
        items.groupBy { it.folderPath }
            .map { (path, list) ->
                FolderGroup(path, list.first().folder, list.sortedBy { it.title.lowercase() })
            }
            .sortedBy { it.name.lowercase() }
}

class PlaylistUseCases {
    /** يعيد القائمة الجديدة ومعرّف القائمة المنشأة */
    fun create(all: List<Playlist>, name: String): Pair<List<Playlist>, String> {
        val id = UUID.randomUUID().toString()
        val clean = name.trim().ifBlank { "قائمة جديدة" }
        return (all + Playlist(id, clean, emptyList())) to id
    }

    fun add(all: List<Playlist>, id: String, file: MediaFile): List<Playlist> =
        all.map { p -> if (p.id == id && p.items.none { it.uri == file.uri }) p.copy(items = p.items + file) else p }

    fun remove(all: List<Playlist>, id: String, file: MediaFile): List<Playlist> =
        all.map { p -> if (p.id == id) p.copy(items = p.items.filterNot { it.uri == file.uri }) else p }

    fun delete(all: List<Playlist>, id: String): List<Playlist> = all.filterNot { it.id == id }
}
