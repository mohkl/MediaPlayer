@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mediaplayer.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mediaplayer.app.player.MAX_BOOST_PERCENT
import com.mediaplayer.app.player.PlayerManager
import com.mediaplayer.app.player.PlayerUi
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
private fun LtrSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier = Modifier, onFinish: (() -> Unit)? = null) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, onValueChangeFinished = onFinish, modifier = modifier)
    }
}

@Composable
fun PlayerScreen(pm: PlayerManager, onBack: () -> Unit) {
    val s by pm.state.collectAsState()
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    var visible by remember { mutableStateOf(true) }
    var sheet by remember { mutableIntStateOf(0) } // 1 صوت، 2 EQ، 3 سرعة، 4 ترجمة

    LaunchedEffect(visible, s.isPlaying, s.isVideo, sheet) {
        if (visible && s.isPlaying && s.isVideo && sheet == 0) { delay(4000); visible = false }
    }
    DisposableEffect(s.isVideo) {
        val w = activity?.window
        val ctrl = w?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (s.isVideo) {
            ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (pm.currentIsVideo) pm.player.pause()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (pm.currentIsVideo) pm.player.pause() }

    val bg = if (s.isVideo) Brush.verticalGradient(listOf(Color.Black, Color.Black))
    else Brush.verticalGradient(listOf(Color(0xFF2A1B6E), Color(0xFF0C0B16)))

    Box(
        Modifier.fillMaxSize().background(bg).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { visible = !visible }
    ) {
        if (s.isVideo) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = pm.player
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AudioArt(s)
        }
        if (s.buffering) CircularProgressIndicator(Modifier.align(Alignment.Center))
        s.error?.let {
            Card(Modifier.align(Alignment.Center).padding(24.dp)) {
                Text(it, Modifier.padding(20.dp), textAlign = TextAlign.Center)
            }
        }
        AnimatedVisibility(visible || !s.isVideo, enter = fadeIn(), exit = fadeOut()) {
            Controls(pm, s, activity, onBack) { sheet = it }
        }
    }

    if (sheet != 0) {
        ModalBottomSheet(onDismissRequest = { sheet = 0 }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (sheet) {
                    1 -> BoostSheet(pm, s)
                    2 -> EqSheet(pm, s)
                    3 -> SpeedSheet(pm, s)
                    4 -> SubsSheet(pm, s)
                }
            }
        }
    }
}

@Composable
private fun AudioArt(s: PlayerUi) {
    val scale by animateFloatAsState(if (s.isPlaying) 1f else 0.9f, label = "art")
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Box(
            Modifier.size(240.dp).scale(scale).background(
                Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF3B1FA8))), CircleShape
            ),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.MusicNote, null, Modifier.size(110.dp), tint = Color(0xFFFFC24D)) }
    }
}

@Composable
private fun Controls(pm: PlayerManager, s: PlayerUi, activity: Activity?, onBack: () -> Unit, openSheet: (Int) -> Unit) {
    val ctx = LocalContext.current
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    val dur = s.durationMs.coerceAtLeast(1L).toFloat()
    val white = Color.White

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // الشريط العلوي
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", tint = white) }
            Column(Modifier.weight(1f)) {
                Text(s.title, color = white, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.artist.isNotBlank()) Text(s.artist, color = white.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            TextButton(onClick = { openSheet(3) }) { Text("${s.speed}x", color = white, fontWeight = FontWeight.Bold) }
            if (s.isVideo) {
                IconButton(onClick = { openSheet(4) }) { Icon(Icons.Rounded.Subtitles, "الترجمة", tint = if (s.selectedText >= 0) Color(0xFFFFC24D) else white) }
                IconButton(onClick = {
                    activity?.requestedOrientation =
                        if (ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }) { Icon(Icons.Rounded.ScreenRotation, "تدوير", tint = white) }
            }
        }

        // أزرار التحكم الرئيسية
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            IconButton(onClick = { pm.player.seekToPreviousMediaItem() }, enabled = s.hasPrev) { Icon(Icons.Rounded.SkipPrevious, "السابق", tint = white.copy(alpha = if (s.hasPrev) 1f else 0.35f), modifier = Modifier.size(36.dp)) }
            IconButton(onClick = { pm.seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, "تأخير 10 ثوانٍ", tint = white, modifier = Modifier.size(36.dp)) }
            FilledIconButton(onClick = { pm.togglePlay() }, modifier = Modifier.size(72.dp)) {
                Icon(if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "تشغيل/إيقاف", modifier = Modifier.size(42.dp))
            }
            IconButton(onClick = { pm.seekBy(10_000) }) { Icon(Icons.Rounded.Forward10, "تقديم 10 ثوانٍ", tint = white, modifier = Modifier.size(36.dp)) }
            IconButton(onClick = { pm.player.seekToNextMediaItem() }, enabled = s.hasNext) { Icon(Icons.Rounded.SkipNext, "التالي", tint = white.copy(alpha = if (s.hasNext) 1f else 0.35f), modifier = Modifier.size(36.dp)) }
        }

        // الشريط السفلي
        Column {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(if (dragging) dragPos.toLong() else s.positionMs), color = white, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = (if (dragging) dragPos else s.positionMs.toFloat()).coerceIn(0f, dur),
                        onValueChange = { dragging = true; dragPos = it },
                        onValueChangeFinished = { pm.player.seekTo(dragPos.toLong()); dragging = false },
                        valueRange = 0f..dur, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(formatTime(s.durationMs), color = white, style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                IconButton(onClick = { pm.cycleRepeat() }) {
                    Icon(
                        if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "تكرار",
                        tint = if (s.repeatMode == Player.REPEAT_MODE_OFF) white else Color(0xFFFFC24D)
                    )
                }
                IconButton(onClick = { pm.toggleShuffle() }) { Icon(Icons.Rounded.Shuffle, "عشوائي", tint = if (s.shuffle) Color(0xFFFFC24D) else white) }
                TextButton(onClick = { openSheet(1) }) {
                    Icon(Icons.Rounded.VolumeUp, null, tint = white); Spacer(Modifier.width(4.dp))
                    Text("${s.boostPercent}%", color = if (s.boostPercent > 100) Color(0xFFFFC24D) else white, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { openSheet(2) }) { Icon(Icons.Rounded.Equalizer, "المعادل", tint = if (s.eqEnabled) Color(0xFFFFC24D) else white) }
            }
        }
    }
}

@Composable
private fun ColumnScope.BoostSheet(pm: PlayerManager, s: PlayerUi) {
    val maxV = if (s.boostSupported) MAX_BOOST_PERCENT.toFloat() else 100f
    val color = when { s.boostPercent > 200 -> Color(0xFFE5484D); s.boostPercent > 100 -> Color(0xFFE08A00); else -> MaterialTheme.colorScheme.primary }
    Text("مستوى الصوت", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("${s.boostPercent}%", style = MaterialTheme.typography.displaySmall, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
    LtrSlider(s.boostPercent.toFloat(), 0f..maxV, { pm.setBoost(it.roundToInt()) }, Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(50, 100, 150, 200, 300).filter { it <= maxV }.forEach { v ->
            FilterChip(selected = s.boostPercent == v, onClick = { pm.setBoost(v) }, label = { Text("$v%") })
        }
    }
    if (!s.boostSupported) Text("الرفع فوق 100% غير مدعوم على هذا الجهاز.", color = MaterialTheme.colorScheme.error)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = Color(0xFFE08A00)); Spacer(Modifier.width(8.dp))
                Text("تنبيه التشويه وحماية السمع", fontWeight = FontWeight.Bold)
            }
            Text("• 100% هو المستوى الطبيعي بلا تشويه.", style = MaterialTheme.typography.bodySmall)
            Text("• من 100% إلى 200%: مناسب للملفات الهادئة، وقد يظهر تشويه خفيف في الملفات المرتفعة أصلًا.", style = MaterialTheme.typography.bodySmall)
            Text("• من 200% إلى 300%: احتمال كبير لتشويه واضح (Clipping) وقد يُتلف مكبّر الصوت في بعض الأجهزة على مستوى مرتفع.", style = MaterialTheme.typography.bodySmall)
            Text("• الاستماع الطويل لصوت عالٍ يضر السمع: خفّض المستوى فورًا إذا شعرت بطنين أو ألم، وتجنّب السماعات الداخلية عند الرفع العالي، وابدأ دائمًا بمستوى منخفض.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ColumnScope.EqSheet(pm: PlayerManager, s: PlayerUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("المعادل وتحسين الصوت", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(checked = s.eqEnabled, onCheckedChange = { pm.setEqEnabled(it) }, enabled = s.eqSupported)
    }
    if (!s.eqSupported) { Text("المعادل غير متاح على هذا الجهاز أو لم يبدأ التشغيل بعد."); return }
    if (s.presets.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.presets.forEachIndexed { i, name -> AssistChip(onClick = { pm.setEqEnabled(true); pm.usePreset(i) }, label = { Text(name) }) }
        }
    }
    s.bands.forEachIndexed { i, b ->
        val label = if (b.hz >= 1000) "${b.hz / 1000} kHz" else "${b.hz} Hz"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(70.dp), style = MaterialTheme.typography.labelMedium)
            LtrSlider(b.level.toFloat(), b.min.toFloat()..b.max.toFloat(), { pm.setEqEnabled(true); pm.setBand(i, it.roundToInt()) }, Modifier.weight(1f))
            Text("${b.level / 100} dB", Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
        }
    }
    Text("تعزيز الجهير (Bass Boost)", fontWeight = FontWeight.SemiBold)
    LtrSlider(s.bass.toFloat(), 0f..1000f, { pm.setBass(it.roundToInt()) }, Modifier.fillMaxWidth())
    OutlinedButton(onClick = { pm.resetEq() }) { Text("إعادة الضبط") }
}

@Composable
private fun ColumnScope.SpeedSheet(pm: PlayerManager, s: PlayerUi) {
    Text("سرعة التشغيل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f).forEach { v ->
            FilterChip(selected = abs(s.speed - v) < 0.01f, onClick = { pm.setSpeed(v) }, label = { Text("${v}x") })
        }
    }
}

@Composable
private fun ColumnScope.SubsSheet(pm: PlayerManager, s: PlayerUi) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pm.addSubtitle(uri) }
    Text("الترجمة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    FilterChip(selected = s.selectedText < 0, onClick = { pm.selectText(-1) }, label = { Text("إيقاف الترجمة") })
    s.textTracks.forEachIndexed { i, n ->
        FilterChip(selected = s.selectedText == i, onClick = { pm.selectText(i) }, label = { Text(n) })
    }
    Button(onClick = { picker.launch(arrayOf("*/*")) }) {
        Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("إضافة ملف ترجمة (SRT / VTT / ASS)")
    }
}
