@file:OptIn(ExperimentalMaterial3Api::class)

package com.mediaplayer.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import com.mediaplayer.app.domain.MediaFile
import com.mediaplayer.app.player.PlayerManager

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun hasMediaPermission(ctx: Context): Boolean {
    fun ok(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= 33) ok(Manifest.permission.READ_MEDIA_VIDEO) || ok(Manifest.permission.READ_MEDIA_AUDIO)
    else ok(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
fun HomeScreen(vm: MainViewModel, pm: PlayerManager, nav: NavHostController) {
    val ctx = LocalContext.current
    val lib by vm.library.collectAsState()
    val query by vm.query.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(hasMediaPermission(ctx)) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaPermission(ctx)
        if (granted) vm.refresh()
    }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(requiredPermissions()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasMediaPermission(ctx)
        if (granted) vm.refresh()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            pm.playExternal(uri)
            nav.navigate("player")
        }
    }

    val q = query.trim().lowercase()
    val videos = remember(lib.videos, q) { if (q.isEmpty()) lib.videos else lib.videos.filter { it.title.lowercase().contains(q) } }
    val audios = remember(lib.audios, q) { if (q.isEmpty()) lib.audios else lib.audios.filter { it.title.lowercase().contains(q) } }
    val folders = remember(lib.folders, q) { if (q.isEmpty()) lib.folders else lib.folders.filter { it.name.lowercase().contains(q) } }

    val tabs = listOf<Pair<String, ImageVector>>(
        "الفيديو" to Icons.Rounded.VideoLibrary,
        "الصوت" to Icons.Rounded.LibraryMusic,
        "المجلدات" to Icons.Rounded.Folder,
        "القوائم" to Icons.Rounded.QueueMusic
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query, onValueChange = { vm.query.value = it },
                            placeholder = { Text("ابحث...") }, singleLine = true,
                            keyboardOptions = KeyboardOptions.Default,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppLogo(size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Media Player", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) vm.query.value = "" }) {
                        Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "بحث")
                    }
                    if (!searching) {
                        IconButton(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }) { Icon(Icons.Rounded.FileOpen, "فتح ملف") }
                        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Rounded.Settings, "الإعدادات") }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !granted -> PermissionCard(
                    onGrant = { permLauncher.launch(requiredPermissions()) },
                    onSettings = {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    })
                lib.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> Crossfade(tab, label = "tabs") { t ->
                    when (t) {
                        0 -> MediaList(videos, vm) { i -> pm.play(videos, i); nav.navigate("player") }
                        1 -> MediaList(audios, vm) { i -> pm.play(audios, i); nav.navigate("player") }
                        2 -> FoldersTab(folders.map { Triple(it.path, it.name, it.items.size) }) { path ->
                            vm.selectedFolder = path; nav.navigate("folder")
                        }
                        else -> PlaylistsTab(vm, nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("نحتاج إذن الوصول إلى ملفات الوسائط على جهازك لعرضها وتشغيلها. كل شيء يعمل محليًا بدون إنترنت.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("منح الإذن") }
        TextButton(onClick = onSettings) { Text("فتح إعدادات التطبيق") }
    }
}

@Composable
fun EmptyState(text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Movie, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FoldersTab(folders: List<Triple<String, String, Int>>, onOpen: (String) -> Unit) {
    if (folders.isEmpty()) { EmptyState("لا توجد مجلدات"); return }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(folders, key = { it.first }) { (path, name, count) ->
            Card(onClick = { onOpen(path) }, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$count ملف", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(vm: MainViewModel, nav: NavHostController) {
    val lists by vm.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("قائمة تشغيل جديدة")
        }
        if (lists.isEmpty()) EmptyState("لا توجد قوائم تشغيل بعد") else
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lists, key = { it.id }) { p ->
                    Card(onClick = { vm.selectedPlaylist = p.id; nav.navigate("playlist") }, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.items.size} ملف", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
    }
    if (creating) NameDialog("قائمة تشغيل جديدة", { creating = false }) { vm.createPlaylist(it); creating = false }
}

@Composable
fun NameDialog(title: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("الاسم") }) },
        confirmButton = { TextButton(onClick = { onOk(name) }) { Text("إنشاء") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun rememberDeleter(onDone: () -> Unit): (MediaFile) -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onDone()
    }
    return { f: MediaFile ->
        try {
            val uri = Uri.parse(f.uri)
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(uri))
                launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                ctx.contentResolver.delete(uri, null, null)
                onDone()
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "تعذّر حذف الملف", Toast.LENGTH_SHORT).show()
        }
    }
}

/** قائمة ملفات قابلة لإعادة الاستخدام مع قائمة خيارات لكل ملف (تشغيل/قائمة/مشاركة/معلومات/حذف) */
@Composable
fun MediaList(items: List<MediaFile>, vm: MainViewModel, onRemove: ((MediaFile) -> Unit)? = null, onPlay: (Int) -> Unit) {
    val ctx = LocalContext.current
    val playlists by vm.playlists.collectAsState()
    var addFor by remember { mutableStateOf<MediaFile?>(null) }
    var infoFor by remember { mutableStateOf<MediaFile?>(null) }
    var deleteFor by remember { mutableStateOf<MediaFile?>(null) }
    val deleter = rememberDeleter { vm.refresh() }

    if (items.isEmpty()) { EmptyState("لا توجد ملفات هنا"); return }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(items, key = { i, f -> "${f.uri}#$i" }) { i, f ->
            MediaRow(
                f, onClick = { onPlay(i) },
                onAdd = { addFor = f },
                onShare = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = if (f.isVideo) "video/*" else "audio/*"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(f.uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, "مشاركة"))
                },
                onInfo = { infoFor = f },
                onDelete = { deleteFor = f },
                onRemove = onRemove?.let { r -> { r(f) } }
            )
        }
    }

    addFor?.let { f ->
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addFor = null }, title = { Text("إضافة إلى قائمة تشغيل") },
            text = {
                Column {
                    playlists.forEach { p ->
                        TextButton(onClick = {
                            vm.addToPlaylist(p.id, f); addFor = null
                            Toast.makeText(ctx, "تمت الإضافة", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.fillMaxWidth()) { Text(p.name) }
                    }
                    if (playlists.isNotEmpty()) HorizontalDivider()
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("قائمة جديدة") })
                    TextButton(onClick = {
                        val id = vm.createPlaylist(newName); vm.addToPlaylist(id, f); addFor = null
                        Toast.makeText(ctx, "تمت الإضافة", Toast.LENGTH_SHORT).show()
                    }) { Text("إنشاء وإضافة") }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { addFor = null }) { Text("إلغاء") } }
        )
    }
    infoFor?.let { f ->
        AlertDialog(
            onDismissRequest = { infoFor = null }, title = { Text(f.title, maxLines = 2) },
            text = {
                Text(
                    "النوع: ${if (f.isVideo) "فيديو" else "صوت"}\nالمدة: ${formatTime(f.durationMs)}\n" +
                        "الحجم: ${Formatter.formatShortFileSize(ctx, f.sizeBytes)}\nالمجلد: ${f.folderPath}" +
                        (if (f.artist.isNotBlank()) "\nالفنان: ${f.artist}" else "")
                )
            },
            confirmButton = { TextButton(onClick = { infoFor = null }) { Text("حسنًا") } }
        )
    }
    deleteFor?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteFor = null }, title = { Text("حذف الملف") },
            text = { Text("سيتم حذف «${f.title}» نهائيًا من الجهاز. هل أنت متأكد؟") },
            confirmButton = { TextButton(onClick = { deleter(f); deleteFor = null }) { Text("حذف", color = Color(0xFFE5484D)) } },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun MediaRow(
    f: MediaFile, onClick: () -> Unit, onAdd: () -> Unit, onShare: () -> Unit,
    onInfo: () -> Unit, onDelete: () -> Unit, onRemove: (() -> Unit)?
) {
    var open by remember { mutableStateOf(false) }
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (f.isVideo) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatTime(f.durationMs)} • ${f.folder}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "خيارات") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text("إضافة إلى قائمة تشغيل") }, leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) }, onClick = { open = false; onAdd() })
                    DropdownMenuItem(text = { Text("مشاركة") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { open = false; onShare() })
                    DropdownMenuItem(text = { Text("معلومات") }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { open = false; onInfo() })
                    if (onRemove != null) DropdownMenuItem(text = { Text("إزالة من القائمة") }, leadingIcon = { Icon(Icons.Rounded.Close, null) }, onClick = { open = false; onRemove() })
                    DropdownMenuItem(text = { Text("حذف من الجهاز") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { open = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun FolderScreen(vm: MainViewModel, pm: PlayerManager, nav: NavHostController) {
    val lib by vm.library.collectAsState()
    val group = lib.folders.firstOrNull { it.path == vm.selectedFolder }
    val items = group?.items ?: emptyList()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(group?.name ?: "المجلد", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
            actions = {
                if (items.isNotEmpty()) IconButton(onClick = { pm.play(items, 0); nav.navigate("player") }) { Icon(Icons.Rounded.PlayArrow, "تشغيل الكل") }
            }
        )
    }) { pad -> Box(Modifier.padding(pad)) { MediaList(items, vm) { i -> pm.play(items, i); nav.navigate("player") } } }
}

@Composable
fun PlaylistScreen(vm: MainViewModel, pm: PlayerManager, nav: NavHostController) {
    val lists by vm.playlists.collectAsState()
    val p = lists.firstOrNull { it.id == vm.selectedPlaylist }
    var confirm by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(p?.name ?: "قائمة التشغيل", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
            actions = {
                if (p != null && p.items.isNotEmpty()) IconButton(onClick = { pm.play(p.items, 0); nav.navigate("player") }) { Icon(Icons.Rounded.PlayArrow, "تشغيل الكل") }
                if (p != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, "حذف القائمة") }
            }
        )
    }) { pad ->
        Box(Modifier.padding(pad)) {
            if (p != null) MediaList(p.items, vm, onRemove = { vm.removeFromPlaylist(p.id, it) }) { i -> pm.play(p.items, i); nav.navigate("player") }
        }
    }
    if (confirm && p != null) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("حذف القائمة") },
        text = { Text("حذف قائمة «${p.name}»؟ (لن تُحذف الملفات نفسها)") },
        confirmButton = { TextButton(onClick = { vm.deletePlaylist(p.id); confirm = false; nav.popBackStack() }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } }
    )
}
