package com.retally.shared.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.presentation.onboarding.OnboardingContract
import com.retally.shared.resources.Res
import com.retally.shared.resources.onboarding_page1
import com.retally.shared.resources.onboarding_page2
import com.retally.shared.resources.onboarding_page3
import com.retally.shared.ui.theme.CappedTextScale
import com.retally.shared.ui.theme.ReTallyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * First-launch onboarding. Only the content region (illustration, headline, body) lives inside
 * the [HorizontalPager] — the page dots and the CTA footer are outside it, so "Get Started"
 * and "Already have an account? Sign In" never move.
 *
 * Auto-advance: every [OnboardingContract.AUTO_ADVANCE_MS] the pager moves to the next page
 * and stops on the last one. The countdown pauses while the user is touching the pager and
 * restarts afterwards, so it never fights a swipe.
 */
@Composable
fun OnboardingScreen(dispatch: (OnboardingContract.Action) -> Unit) {
    val colors = ReTallyTheme.colors
    val pagerState = rememberPagerState(pageCount = { OnboardingContract.PAGE_COUNT })
    val scope = rememberCoroutineScope()

    // Auto-advance loop: waits for the pager to be fully settled and untouched before every
    // tick, so it can never fight a swipe. The countdown restarts after any interaction,
    // and the loop ends on the last page.
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { pagerState.isScrollInProgress }.first { !it }
            delay(OnboardingContract.AUTO_ADVANCE_MS)
            if (pagerState.isScrollInProgress) continue
            val next = pagerState.currentPage + 1
            if (next >= OnboardingContract.PAGE_COUNT) break
            pagerState.animateScrollToPage(next)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding(),
    ) {
        // --- Rotating content: only this moves ---
        HorizontalPager(
            state = pagerState,
            // Compose the neighbours up front so their illustrations decode before the swipe.
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            OnboardingPage(page = page)
        }
        // --- Dots: outside the pager, only the highlight changes ---
        PageDots(
            pageCount = OnboardingContract.PAGE_COUNT,
            currentPage = pagerState.currentPage,
            onDotClick = { page ->
                scope.launch { pagerState.animateScrollToPage(page) }
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 28.dp),
        )
        // --- Pinned footer: completely static ---
        OnboardingFooter(
            onGetStarted = { dispatch(OnboardingContract.Action.GetStarted) },
            onSignIn = { dispatch(OnboardingContract.Action.SignInClicked) },
        )
    }
}

@Composable
private fun OnboardingPage(page: Int) {
    val colors = ReTallyTheme.colors
    val art: DrawableResource
    val titleId: String
    val bodyId: String
    when (page) {
        0 -> {
            art = Res.drawable.onboarding_page1
            titleId = S.ONBOARDING_PAGE1_TITLE
            bodyId = S.ONBOARDING_PAGE1_BODY
        }
        1 -> {
            art = Res.drawable.onboarding_page2
            titleId = S.ONBOARDING_PAGE2_TITLE
            bodyId = S.ONBOARDING_PAGE2_BODY
        }
        else -> {
            art = Res.drawable.onboarding_page3
            titleId = S.ONBOARDING_PAGE3_TITLE
            bodyId = S.ONBOARDING_PAGE3_BODY
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(art),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(28.dp))
        CappedTextScale(maxFontScale = 1.15f) {
            Text(
                text = str(titleId),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = str(bodyId),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageDots(
    pageCount: Int,
    currentPage: Int,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReTallyTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            // 28dp touch target with the 10dp dot centered inside.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onDotClick(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier.background(colors.primary)
                            } else {
                                Modifier.border(1.5.dp, colors.muted.copy(alpha = 0.5f), CircleShape)
                            },
                        ),
                )
            }
            if (index < pageCount - 1) Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun OnboardingFooter(onGetStarted: () -> Unit, onSignIn: () -> Unit) {
    val colors = ReTallyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Text(
                text = str(S.ONBOARDING_GET_STARTED),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(20.dp))
        // "Already have an account? Sign In" - one tap target; only "Sign In"
        // is styled as a link (bold, underlined, blue). str() is @Composable,
        // so both parts are resolved before buildAnnotatedString runs.
        val haveAccountPrefix = str(S.ONBOARDING_HAVE_ACCOUNT_PREFIX)
        val signInLabel = str(S.ONBOARDING_SIGN_IN)
        Text(
            text = buildAnnotatedString {
                append(haveAccountPrefix)
                withStyle(
                    SpanStyle(
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append(signInLabel)
                }
            },
            fontSize = 16.sp,
            color = colors.ink,
            modifier = Modifier.clickable(onClick = onSignIn),
        )
    }
}
