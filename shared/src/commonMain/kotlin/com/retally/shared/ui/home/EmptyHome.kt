package com.retally.shared.ui.home

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.retally.shared.domain.auth.Account
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.retally.shared.resources.Res
import com.retally.shared.resources.empty_receipts
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.theme.ReTallyTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource

/**
 * empty-state for Home (design: "No receipts yet"): time-of-day greeting, the receipt +
 * magnifier illustration, headline and body, the primary "Scan receipt" button, a
 * "See how it works" link, then the ad rectangle parked above the tab bar.
 */
@Composable
internal fun EmptyHome(
    account: Account,
    contentPadding: PaddingValues,
    onScan: () -> Unit,
    onHowItWorks: () -> Unit,
    onSignIn: () -> Unit,
    adsEnabled: Boolean,
    bannerAdUnitId: String,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d.screen),
        ) {
            Greeting(account)
            if (account.isGuest) {
                Spacer(Modifier.height(d.lg))
                GuestBanner(onSignIn = onSignIn)
            }
            Spacer(Modifier.height(d.lg))
            ReceiptSearchIllustration(
                modifier = Modifier.fillMaxWidth().height(d.emptyIllustration),
            )
            Spacer(Modifier.height(d.lg))
            Text(
                str(S.HOME_EMPTY_TITLE),
                style = ReTallyTheme.typography.display,
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(d.sm))
            Text(
                str(S.HOME_EMPTY_BODY),
                style = ReTallyTheme.typography.body,
                color = c.ink.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = d.sm),
            )
            Spacer(Modifier.height(d.lg))
            PrimaryButton(
                text = str(S.HOME_EMPTY_SCAN),
                onClick = onScan,
                icon = Icons.Rounded.DocumentScanner,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(d.md))
            Text(
                str(S.HOME_EMPTY_HOW_IT_WORKS),
                style = ReTallyTheme.typography.link,
                color = c.primaryDark,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onHowItWorks)
                    .padding(vertical = d.xs),
            )
            Spacer(Modifier.height(d.lg))
        }
        if (adsEnabled) {
            InlineRectangleAd(adUnitId = bannerAdUnitId)
            Spacer(Modifier.height(d.md))
        }
    }
}

/** "Good evening, Guest" / "Good evening, Mukesh" — Display 28 Bold like the mock. */
@Composable
internal fun Greeting(account: Account) {
    val name = account.firstName ?: str(S.HOME_GUEST_NAME)
    Text(
        str(greetingKey(), name),
        style = ReTallyTheme.typography.display,
        color = ReTallyTheme.colors.ink,
    )
}

/**
 * Guest banner: amber card with an info glyph, "You are browsing as a guest" (Title 20
 * SemiBold), "Receipts stay on this device only." (Body 16, Muted) and the
 * "Sign in to enable cloud sync" link (Body 16 SemiBold, Primary dark).
 */
@Composable
internal fun GuestBanner(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardRadius))
            .background(c.warning.copy(alpha = 0.45f))
            .padding(d.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .border(2.dp, c.warningInk.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = c.warningInk, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(d.md))
            Column {
                Text(str(S.HOME_GUEST_TITLE), style = ReTallyTheme.typography.title, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(str(S.HOME_GUEST_BODY), style = ReTallyTheme.typography.body, color = c.ink.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(d.md))
        Text(
            str(S.HOME_GUEST_SIGN_IN),
            style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = c.primaryDark,
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSignIn)
                .padding(vertical = d.xs),
        )
    }
}

/** Key of the "Good morning, %s" / afternoon / evening greeting for the device clock. */
internal fun greetingKey(clock: Clock = Clock.System, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val hour = clock.now().toLocalDateTime(timeZone).hour
    return when {
        hour < 12 -> S.HOME_GREETING_MORNING
        hour < 17 -> S.HOME_GREETING_AFTERNOON
        else -> S.HOME_GREETING_EVENING
    }
}

/**
 * Empty-state illustration: the receipt-and-magnifier artwork shipped as a Compose
 * Multiplatform drawable (shared/src/commonMain/composeResources/drawable/empty_receipts.png).
 *
 * Replaces the earlier Canvas line-art. The PNG has a transparent background so it sits on
 * whatever surface hosts it, but unlike the drawn version its colours are fixed and do not
 * follow the theme tokens.
 */
@Composable
internal fun ReceiptSearchIllustration(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.empty_receipts),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
