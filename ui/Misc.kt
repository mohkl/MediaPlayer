@file:OptIn(ExperimentalMaterial3Api::class)

package com.mediaplayer.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mediaplayer.app.BuildConfig

const val DEV_NAME = "2"
const val DEV_PHONE_DISPLAY = "+201027814323"
const val DEV_WA_LINK = "https://wa.me/201027814323"
val DOWNLOAD_URL: String get() = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest/download/MediaPlayer.apk"

fun makeQr(text: String, size: Int = 768): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
private fun SubScreen(title: String, nav: NavHostController, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavHostController) {
    val theme by vm.theme.collectAsState()
    SubScreen("الإعدادات", nav) {
        Text("المظهر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(16.dp)) {
            listOf(0 to "حسب النظام", 1 to "فاتح", 2 to "داكن").forEach { (mode, label) ->
                Row(Modifier.fillMaxWidth().clickable { vm.setTheme(mode) }.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = theme == mode, onClick = { vm.setTheme(mode) })
                    Text(label)
                }
            }
        }
        Text("المزيد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        NavRow(Icons.Rounded.Info, "حول التطبيق") { nav.navigate("about") }
        NavRow(Icons.Rounded.Share, "مشاركة التطبيق") { nav.navigate("share") }
        NavRow(Icons.Rounded.Star, "تقييم التطبيق") { nav.navigate("rate") }
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AboutScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    SubScreen("حول التطبيق", nav) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(size = 100.dp)
            Spacer(Modifier.height(10.dp))
            Text("Media Player", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("الإصدار ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "مشغل فيديو وصوت يعمل بالكامل دون إنترنت، يدعم رفع الصوت حتى 300%، ومعادل صوت، وقوائم تشغيل، وترجمة الفيديو.",
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("معلومات المطوّر", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("اسم المطوّر: $DEV_NAME")
                Text("واتساب: $DEV_PHONE_DISPLAY")
                Button(onClick = {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEV_WA_LINK))) }
                    catch (e: Exception) { Toast.makeText(ctx, "تعذّر فتح واتساب", Toast.LENGTH_SHORT).show() }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Message, null); Spacer(Modifier.width(8.dp)); Text("تواصل عبر WhatsApp")
                }
            }
        }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                "حماية السمع: رفع الصوت فوق 100% قد يسبب تشويهًا وقد يضر السمع عند الاستماع الطويل. ابدأ بمستوى منخفض وارفع تدريجيًا.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun ShareScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val url = DOWNLOAD_URL
    val qr = remember(url) { makeQr(url).asImageBitmap() }
    SubScreen("مشاركة التطبيق", nav) {
        Text("امسح رمز QR لتحميل التطبيق مباشرة من GitHub Releases", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth(), Alignment.Center) {
            Image(qr, "QR Code", Modifier.size(260.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp))
        }
        Text(url, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("link", url))
                Toast.makeText(ctx, "تم نسخ الرابط", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("نسخ") }
            Button(onClick = {
                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "حمّل تطبيق Media Player:\n$url") }
                ctx.startActivity(Intent.createChooser(i, "مشاركة التطبيق"))
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("مشاركة") }
        }
    }
}

@Composable
fun RateScreen(vm: MainViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val saved by vm.rating.collectAsState()
    var stars by remember { mutableIntStateOf(saved) }
    val labels = listOf("", "سيئ", "مقبول", "جيد", "جيد جدًا", "ممتاز!")
    SubScreen("تقييم التطبيق", nav) {
        Spacer(Modifier.height(24.dp))
        Text("ما رأيك في Media Player؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), Arrangement.Center) {
            for (i in 1..5) {
                val sc by animateFloatAsState(if (i <= stars) 1.25f else 1f, spring(dampingRatio = 0.4f), label = "star$i")
                IconButton(onClick = { stars = i }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        if (i <= stars) Icons.Rounded.Star else Icons.Rounded.StarBorder, "$i",
                        tint = if (i <= stars) Color(0xFFFFB020) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp).scale(sc)
                    )
                }
            }
        }
        Text(labels[stars], style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            vm.setRating(stars)
            Toast.makeText(ctx, "شكرًا لتقييمك!", Toast.LENGTH_SHORT).show()
            nav.popBackStack()
        }, enabled = stars > 0, modifier = Modifier.fillMaxWidth()) { Text("حفظ التقييم") }
        OutlinedButton(onClick = {
            val msg = Uri.encode("تقييمي لتطبيق Media Player: $stars/5")
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$DEV_WA_LINK?text=$msg"))) } catch (e: Exception) { }
        }, enabled = stars > 0, modifier = Modifier.fillMaxWidth()) { Text("إرسال التقييم للمطوّر عبر واتساب") }
    }
}
