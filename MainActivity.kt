package com.mediaplayer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.mediaplayer.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val openPlayer = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MediaApp).container
        if (savedInstanceState == null) handleIntent(intent, container)
        setContent { AppRoot(container, openPlayer) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, (application as MediaApp).container)
    }

    private fun handleIntent(i: Intent?, container: AppContainer) {
        val data = i?.data
        if (i?.action == Intent.ACTION_VIEW && data != null) {
            container.player.playExternal(data)
            openPlayer.intValue++
        }
    }
}
