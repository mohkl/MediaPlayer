package com.mediaplayer.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mediaplayer.app.AppContainer
import com.mediaplayer.app.domain.FolderGroup
import com.mediaplayer.app.domain.MediaFile
import com.mediaplayer.app.domain.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryState(
    val loading: Boolean = false,
    val videos: List<MediaFile> = emptyList(),
    val audios: List<MediaFile> = emptyList(),
    val folders: List<FolderGroup> = emptyList(),
    val error: String? = null
)

class MainViewModel(private val c: AppContainer) : ViewModel() {

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _playlists = MutableStateFlow(c.playlistRepo.load())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    val query = MutableStateFlow("")

    private val _theme = MutableStateFlow(c.settings.themeMode())
    val theme: StateFlow<Int> = _theme.asStateFlow()

    private val _rating = MutableStateFlow(c.settings.rating())
    val rating: StateFlow<Int> = _rating.asStateFlow()

    val welcomeShown: Boolean = c.settings.welcomeShown()

    var selectedFolder by mutableStateOf<String?>(null)
    var selectedPlaylist by mutableStateOf<String?>(null)

    fun refresh() {
        viewModelScope.launch {
            val cur = _library.value
            if (cur.videos.isEmpty() && cur.audios.isEmpty()) _library.update { it.copy(loading = true, error = null) }
            try {
                val all = c.loadLibrary()
                _library.value = LibraryState(
                    loading = false,
                    videos = all.filter { it.isVideo },
                    audios = all.filter { !it.isVideo },
                    folders = c.groupByFolder(all)
                )
            } catch (e: Exception) {
                _library.update { it.copy(loading = false, error = "تعذّر قراءة الملفات") }
            }
        }
    }

    fun setTheme(mode: Int) { c.settings.setThemeMode(mode); _theme.value = mode }
    fun markWelcomeShown() = c.settings.setWelcomeShown()
    fun setRating(v: Int) { c.settings.setRating(v); _rating.value = v }

    private fun mutate(f: (List<Playlist>) -> List<Playlist>) {
        val next = f(_playlists.value)
        _playlists.value = next
        viewModelScope.launch(Dispatchers.IO) { c.playlistRepo.save(next) }
    }

    fun createPlaylist(name: String): String {
        val (list, id) = c.playlistOps.create(_playlists.value, name)
        mutate { list }
        return id
    }
    fun addToPlaylist(id: String, f: MediaFile) = mutate { c.playlistOps.add(it, id, f) }
    fun removeFromPlaylist(id: String, f: MediaFile) = mutate { c.playlistOps.remove(it, id, f) }
    fun deletePlaylist(id: String) = mutate { c.playlistOps.delete(it, id) }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { MainViewModel(c) } }
    }
}
