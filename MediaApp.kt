package com.mediaplayer.app

import android.app.Application
import android.content.Context
import com.mediaplayer.app.data.*
import com.mediaplayer.app.domain.*
import com.mediaplayer.app.player.PlayerManager

class AppContainer(ctx: Context) {
    val mediaRepo: MediaRepository = MediaStoreRepository(ctx)
    val playlistRepo: PlaylistRepository = PrefsPlaylistRepository(ctx)
    val settings: SettingsRepository = PrefsSettingsRepository(ctx)
    val loadLibrary = LoadLibraryUseCase(mediaRepo)
    val groupByFolder = GroupByFolderUseCase()
    val playlistOps = PlaylistUseCases()
    val player: PlayerManager by lazy { PlayerManager(ctx) }
}

class MediaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
