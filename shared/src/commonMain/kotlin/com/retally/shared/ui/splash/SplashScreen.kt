package com.retally.shared.ui.splash

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.retally.shared.ui.theme.CappedTextScale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retally.shared.resources.Res
import com.retally.shared.resources.splash_logo
import com.retally.shared.resources.splash_scan_art
import com.retally.shared.ui.components.AdBannerSlot
import com.retally.shared.ui.components.AdFormat
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/**
 * Brand splash (design: "Splash Screen" mock, light + dark). Logo mark, `retally` wordmark,
 * the SCAN · TRACK · SAVE tagline, the scan illustration, soft waves along the bottom and a thin
 * progress bar above the navigation bar. Colors follow the active [MaterialTheme] so the
 * in-app theme choice (system / light / dark) decides which variant renders.
 *
 * Stateless: [progress] is cosmetic and comes from the splash store's warm-up.
 *
 * @param adsEnabled live ad eligibility from the shell (remote config + consent).
 * @param bannerAdUnitId AdMob unit id for the 320×50 banner shown 20dp above the
 *   progress bar. The banner is the first thing to load: the bar holds at zero until
 *   the ad request settles.
 */
@Composable
fun SplashScreen(
    progress: Float,
    adsEnabled: Boolean = false,
    bannerAdUnitId: String = "",
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = if (dark) SplashPalette.Dark else SplashPalette.Light

    // Animate in from the first frame (not gated on store progress) so the splash is
    // distinguishable from the native launch screen even if the store never ticks.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val logoScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.88f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "logoScale",
    )
    val fadeIn by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 550),
        label = "fadeIn",
    )
    // The ad loads first: the progress bar holds at zero until the ad request settles
    // (creative shown or failed) or the timeout elapses, so the banner is on screen
    // before the bar starts moving.
    var adSettled by remember { mutableStateOf(!adsEnabled) }
    LaunchedEffect(adsEnabled, adSettled) {
        if (adsEnabled && !adSettled) {
            delay(SplashDefaults.AD_SETTLE_TIMEOUT_MS)
            adSettled = true
        }
    }
    val bar by animateFloatAsState(
        targetValue = if (adSettled) progress else 0f,
        animationSpec = tween(220),
        label = "bar",
    )

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        SplashWaves(palette = palette, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(SplashDefaults.LOGO_SIZE)
                    .scale(logoScale)
                    .alpha(fadeIn),
            )
            Spacer(Modifier.height(8.dp))
            CappedTextScale(maxFontScale = 1.15f) {
            Text(
                text = str(S.APP_NAME),
                fontSize = 58.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.5).sp,
                lineHeight = 62.sp,
                color = palette.wordmark,
                modifier = Modifier.alpha(fadeIn),
            )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = str(S.SPLASH_TAGLINE),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
                color = palette.tagline,
                modifier = Modifier.alpha(fadeIn),
            )
            Spacer(Modifier.height(72.dp))
            Image(
                painter = painterResource(Res.drawable.splash_scan_art),
                contentDescription = null,
                modifier = Modifier
                    .width(200.dp)
                    .aspectRatio(640f / 504f)
                    .scale(logoScale)
                    .alpha(fadeIn),
            )
            // Keep the group visually centred against the bar + waves below.
            Spacer(Modifier.height(48.dp))
        }

        // 320×50 banner 20dp above the progress bar; the bar keeps its original spot
        // at the bottom and starts only after the ad request settles. The nav-bar inset
        // is applied here too, so the 20dp gap holds on devices with a system nav bar
        // or home indicator — otherwise the inset eats the gap and the banner lands on
        // the bar.
        AdBannerSlot(
            adUnitId = bannerAdUnitId,
            enabled = adsEnabled,
            format = AdFormat.BANNER,
            onAdSettled = { adSettled = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp + 4.dp + 20.dp),
        )

        LinearProgressIndicator(
            progress = { bar },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp)
                .fillMaxWidth(0.45f)
                .height(4.dp)
                .clip(RoundedCornerShape(50)),
            color = palette.progress,
            trackColor = palette.progressTrack,
            strokeCap = StrokeCap.Round,
        )
    }
}

/** Colour set for one splash variant; the design's dark screen is deep green, not the app's near-black. */
internal data class SplashPalette(
    val background: Color,
    val wordmark: Color,
    val tagline: Color,
    val glyph: Color,
    val progress: Color,
    val progressTrack: Color,
    val waveBack: Color,
    val waveFront: Color,
) {
    companion object {
        val Light = SplashPalette(
            // Brand primary (ReTallyLightPalette.primary) as the full-bleed field; every
            // foreground is white or a white alpha so it reads on the green.
            background = Color(0xFF10B981),
            wordmark = Color(0xFFFFFFFF),
            tagline = Color(0xD9FFFFFF),
            glyph = Color(0xE6FFFFFF),
            progress = Color(0xFFFFFFFF),
            progressTrack = Color(0x59FFFFFF),
            waveBack = Color(0x1AFFFFFF),
            waveFront = Color(0x2EFFFFFF),
        )
        val Dark = SplashPalette(
            background = Color(0xFF0F2E29),
            wordmark = Color(0xFFFFFFFF),
            tagline = Color(0xFF6FD8B8),
            glyph = Color(0xFF6FD8B8),
            progress = Color(0xFF45D4AF),
            progressTrack = Color(0x3345D4AF),
            waveBack = Color(0x2610B981),
            waveFront = Color(0x4010B981),
        )
    }
}

internal object SplashDefaults {
    val LOGO_SIZE = 150.dp
    /** Max wait for the splash ad request before the progress bar starts anyway. */
    const val AD_SETTLE_TIMEOUT_MS = 2000L
}

/** Two overlapping soft waves anchored to the bottom edge (design: layered mint swells). */
@Composable
private fun SplashWaves(palette: SplashPalette, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Back wave: rises from the left, crests right of centre.
        drawPath(
            Path().apply {
                moveTo(0f, h * 0.80f)
                cubicTo(w * 0.25f, h * 0.70f, w * 0.45f, h * 0.90f, w * 0.70f, h * 0.82f)
                cubicTo(w * 0.85f, h * 0.77f, w * 0.95f, h * 0.80f, w, h * 0.78f)
                lineTo(w, h); lineTo(0f, h); close()
            },
            color = palette.waveBack,
        )
        // Front wave: lower and flatter, crests left of centre.
        drawPath(
            Path().apply {
                moveTo(0f, h * 0.90f)
                cubicTo(w * 0.20f, h * 0.84f, w * 0.40f, h * 0.96f, w * 0.62f, h * 0.91f)
                cubicTo(w * 0.80f, h * 0.87f, w * 0.92f, h * 0.93f, w, h * 0.90f)
                lineTo(w, h); lineTo(0f, h); close()
            },
            color = palette.waveFront,
        )
    }
}


