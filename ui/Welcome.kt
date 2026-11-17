package com.mediaplayer.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(250); step = 1; delay(600); step = 2; delay(800); step = 3 }
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale"
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1B1048), Color(0xFF0C0B16), Color(0xFF2A1366)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(340.dp).scale(glow)
                .background(Brush.radialGradient(listOf(Color(0x558B5CF6), Color.Transparent)), CircleShape)
        )
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(step >= 1, enter = fadeIn(tween(800)) + scaleIn(tween(800), initialScale = 0.5f)) {
                AppLogo(size = 130.dp)
            }
            Spacer(Modifier.height(28.dp))
            AnimatedVisibility(step >= 2, enter = fadeIn(tween(900)) + slideInVertically(tween(900)) { it / 2 }) {
                Text("Media Player", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(step >= 2, enter = fadeIn(tween(1200)) + slideInVertically(tween(1200)) { it / 2 }) {
                Text(
                    "مرحبًا بك في عالم الترفيه، استمتع بتجربة تشغيل استثنائية!",
                    fontFamily = FontFamily.Serif, fontSize = 22.sp, lineHeight = 36.sp,
                    textAlign = TextAlign.Center, color = Color(0xFFE6DEFF)
                )
            }
            Spacer(Modifier.height(40.dp))
            AnimatedVisibility(step >= 3, enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it }) {
                Button(onClick = onStart, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 44.dp, vertical = 14.dp)) {
                    Text("ابدأ الآن", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
