package com.mediaplayer.app.player

import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.mediaplayer.app.domain.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10

const val MAX_BOOST_PERCENT = 300

/** تحويل نسبة الرفع إلى كسب بالـ millibel: 300% ≈ +9.54dB ≈ 954mB */
fun boostToGainMb(percent: Int): Int =
    if (percent <= 100) 0 else (2000.0 * log10(percent / 100.0)).toInt()

data class EqBand(val hz: Int, val level: Int, val min: Int, val max: Int)

data class PlayerUi(
    val hasMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val isVideo: Boolean = false,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    val boostPercent: Int = 100,
    val boostSupported: Boolean = false,
    val eqSupported: Boolean = false,
    val eqEnabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val presets: List<String> = emptyList(),
    val bass: Int = 0,
    val textTracks: List<String> = emptyList(),
    val selectedText: Int = -1,
    val error: String? = null
)

class PlayerManager(private val app: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(
        app, DefaultRenderersFactory(app).setEnableDecoderFallback(true)
    )
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state.asStateFlow()

    private var queue: List<MediaFile> = emptyList()
    private var loudness: LoudnessEnhancer? = null
    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var boost = 100
    private var eqOn = false
    private var bassStrength = 0
    private val eqLevels = mutableMapOf<Int, Int>()

    val currentIsVideo: Boolean
        get() = queue.getOrNull(player.currentMediaItemIndex)?.isVideo == true

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = refresh()
            override fun onAudioSessionIdChanged(audioSessionId: Int) = setupEffects(audioSessionId)
            override fun onPlayerError(error: PlaybackException) {
                _state.update {
                    it.copy(error = "تعذّر تشغيل هذا الملف: الصيغة أو الترميز غير مدعوم على هذا الجهاز")
                }
            }
        })
        setupEffects(player.audioSessionId)
        scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _state.update {
                        it.copy(positionMs = player.currentPosition.coerceAtLeast(0), durationMs = safeDuration())
                    }
                }
                delay(if (player.isPlaying) 400 else 1000)
            }
        }
    }

    private fun safeDuration(): Long = player.duration.let { if (it == C.TIME_UNSET) 0L else it }

    // ---------------- التشغيل ----------------

    fun play(items: List<MediaFile>, start: Int) {
        if (items.isEmpty()) return
        queue = items
        val mediaItems = items.map { f ->
            MediaItem.Builder()
                .setMediaId(f.id.toString())
                .setUri(f.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(f.title)
                        .setArtist(f.artist.takeIf { it.isNotBlank() })
                        .build()
                )
                .build()
        }
        _state.update { it.copy(error = null) }
        player.setMediaItems(mediaItems, start.coerceIn(0, mediaItems.lastIndex), 0L)
        player.prepare()
        player.play()
        try {
            app.startService(Intent(app, PlaybackService::class.java))
        } catch (e: Exception) { /* الإشعار اختياري */ }
    }

    fun playExternal(uri: Uri) {
        var name = uri.lastPathSegment ?: "ملف"
        try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: name
            }
        } catch (e: Exception) { }
        val mime = try { app.contentResolver.getType(uri) } catch (e: Exception) { null }
        val ext = name.substringAfterLast('.', "").lowercase()
        val isVideo = mime?.startsWith("video") == true ||
            (mime?.startsWith("audio") != true && ext in VIDEO_EXT)
        play(listOf(MediaFile(-1, uri.toString(), name.substringBeforeLast('.'), "", 0, "", "", isVideo, 0)), 0)
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        val d = safeDuration()
        val target = player.currentPosition + deltaMs
        player.seekTo(if (d > 0) target.coerceIn(0, d) else target.coerceAtLeast(0))
    }

    fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled }

    // ---------------- الترجمة ----------------

    fun addSubtitle(uri: Uri) {
        val cur = player.currentMediaItem ?: return
        val idx = player.currentMediaItemIndex
        val pos = player.currentPosition
        val wasPlaying = player.isPlaying
        var name = uri.lastPathSegment ?: ""
        try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: name
            }
        } catch (e: Exception) { }
        val lower = name.lowercase()
        val mime = when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".ttml") || lower.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val sub = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setLabel(name.ifBlank { "ترجمة" })
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        player.replaceMediaItem(idx, cur.buildUpon().setSubtitleConfigurations(listOf(sub)).build())
        player.seekTo(idx, pos)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        if (wasPlaying) player.play()
    }

    fun selectText(index: Int) {
        val b = player.trackSelectionParameters.buildUpon()
        if (index < 0) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val g = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.getOrNull(index) ?: return
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = b.build()
    }

    // ---------------- الصوت: رفع حتى 300% + EQ ----------------

    fun setBoost(percent: Int) {
        boost = percent.coerceIn(0, MAX_BOOST_PERCENT)
        applyBoost()
        refresh()
    }

    private fun applyBoost() {
        player.volume = if (boost <= 100) boost / 100f else 1f
        try { loudness?.setTargetGain(boostToGainMb(boost)) } catch (e: Exception) { }
    }

    fun setEqEnabled(on: Boolean) {
        eqOn = on
        try { eq?.enabled = on } catch (e: Exception) { }
        refresh()
    }

    fun setBand(band: Int, level: Int) {
        eqLevels[band] = level
        try { eq?.setBandLevel(band.toShort(), level.toShort()) } catch (e: Exception) { }
        refresh()
    }

    fun usePreset(index: Int) {
        try {
            val e = eq ?: return
            e.usePreset(index.toShort())
            for (b in 0 until e.numberOfBands.toInt()) eqLevels[b] = e.getBandLevel(b.toShort()).toInt()
        } catch (e: Exception) { }
        refresh()
    }

    fun resetEq() {
        try {
            val e = eq ?: return
            for (b in 0 until e.numberOfBands.toInt()) { e.setBandLevel(b.toShort(), 0); eqLevels[b] = 0 }
        } catch (e: Exception) { }
        setBass(0)
    }

    fun setBass(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        try {
            bassBoost?.let { it.enabled = bassStrength > 0; if (it.strengthSupported) it.setStrength(bassStrength.toShort()) }
        } catch (e: Exception) { }
        refresh()
    }

    private fun setupEffects(session: Int) {
        releaseEffects()
        if (session == C.AUDIO_SESSION_ID_UNSET || session == 0) { refresh(); return }
        loudness = try { LoudnessEnhancer(session).also { it.enabled = true } } catch (e: Throwable) { null }
        eq = try {
            Equalizer(0, session).also { e ->
                e.enabled = eqOn
                eqLevels.forEach { (b, l) -> if (b < e.numberOfBands) e.setBandLevel(b.toShort(), l.toShort()) }
            }
        } catch (e: Throwable) { null }
        bassBoost = try {
            BassBoost(0, session).also {
                it.enabled = bassStrength > 0
                if (it.strengthSupported) it.setStrength(bassStrength.toShort())
            }
        } catch (e: Throwable) { null }
        applyBoost()
        refresh()
    }

    private fun releaseEffects() {
        try { loudness?.release() } catch (e: Exception) { }
        try { eq?.release() } catch (e: Exception) { }
        try { bassBoost?.release() } catch (e: Exception) { }
        loudness = null; eq = null; bassBoost = null
    }

    // ---------------- الحالة ----------------

    private fun refresh() {
        val cur = queue.getOrNull(player.currentMediaItemIndex)
        val md = player.mediaMetadata
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val names = groups.mapIndexed { i, g ->
            val f = g.getTrackFormat(0)
            f.label ?: f.language ?: "ترجمة ${i + 1}"
        }
        val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val selected = if (textDisabled) -1 else groups.indexOfFirst { it.isSelected }

        var bands: List<EqBand> = emptyList()
        var presets: List<String> = emptyList()
        try {
            eq?.let { e ->
                val r = e.bandLevelRange
                bands = (0 until e.numberOfBands.toInt()).map { b ->
                    EqBand(e.getCenterFreq(b.toShort()) / 1000, e.getBandLevel(b.toShort()).toInt(), r[0].toInt(), r[1].toInt())
                }
                presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
            }
        } catch (e: Exception) { }

        _state.update {
            it.copy(
                hasMedia = player.mediaItemCount > 0,
                title = cur?.title ?: md.title?.toString().orEmpty(),
                artist = cur?.artist ?: md.artist?.toString().orEmpty(),
                isVideo = cur?.isVideo == true,
                isPlaying = player.isPlaying,
                buffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = safeDuration(),
                speed = player.playbackParameters.speed,
                repeatMode = player.repeatMode,
                shuffle = player.shuffleModeEnabled,
                hasNext = player.hasNextMediaItem(),
                hasPrev = player.hasPreviousMediaItem(),
                boostPercent = boost,
                boostSupported = loudness != null,
                eqSupported = eq != null,
                eqEnabled = eqOn,
                bands = bands,
                presets = presets,
                bass = bassStrength,
                textTracks = names,
                selectedText = selected,
                error = if (player.playbackState == Player.STATE_READY) null else it.error
            )
        }
    }

    companion object {
        val VIDEO_EXT = setOf("mp4", "mkv", "webm", "3gp", "mov", "m4v", "ts", "flv", "avi", "wmv", "mpg", "mpeg")
    }
}
