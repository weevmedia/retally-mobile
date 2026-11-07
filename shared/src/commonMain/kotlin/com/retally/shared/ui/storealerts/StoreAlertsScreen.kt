/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.storealerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.presentation.storealerts.StoreAlertsContract
import com.retally.shared.ui.components.CircleIconButton
import com.retally.shared.ui.theme.CappedTextScale
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * Nearby-store alerts opt-in, shown once after the first sign-in / guest choice. Same visual
 * family as the onboarding pager (30sp ExtraBold headline, 60dp pill CTA): the sign-in screen's back chevron top-left, "Skip" top-right,
 * then top-aligned — a location pin, the pitch, the background-location disclosure — and
 * "Enable location" pinned at the bottom. The system prompts the host raises after "Enable location" appear
 * over this screen, so the disclosure is on screen while the user answers.
 */
@Composable
fun StoreAlertsScreen(
    dispatch: (StoreAlertsContract.Action) -> Unit,
    state: StoreAlertsContract.State = StoreAlertsContract.State(),
) {
    val colors = ReTallyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding(),
    ) {
        // --- Top bar: the sign-in screen's circle back chevron on the left, Skip on the right ---
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 24.dp),
        ) {
            CircleIconButton(
                onClick = { dispatch(StoreAlertsContract.Action.BackClicked) },
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = str(S.ACTION_BACK),
                    tint = colors.ink,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp),
                )
            }
            Text(
                text = str(S.ONBOARDING_ALERTS_SKIP),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryDark,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !state.isRequesting,
                        onClick = { dispatch(StoreAlertsContract.Action.SkipClicked) },
                    )
                    .padding(8.dp),
            )
        }

        // --- Pitch: starts just under the Skip row (there is no back button to make room
        // for), reads top-down like the onboarding pages, and leaves the slack above the CTA. ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(16.dp))
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(24.dp))
            CappedTextScale(maxFontScale = 1.15f) {
                Text(
                    text = str(S.ONBOARDING_ALERTS_TITLE),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = str(S.ONBOARDING_ALERTS_BODY),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = colors.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // Prominent disclosure: what background location is used for, before the prompt.
            Text(
                text = str(S.ONBOARDING_ALERTS_DISCLOSURE),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // Which option to pick: the OS prompt defaults to "while using", which can't wake the app.
            Text(
                text = str(S.ONBOARDING_ALERTS_HINT),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
        }

        // --- Pinned CTA, same shape as onboarding's "Get Started" ---
        Button(
            onClick = { dispatch(StoreAlertsContract.Action.EnableClicked) },
            enabled = !state.isRequesting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 28.dp)
                .height(60.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.primary,
                disabledContentColor = colors.onPrimary,
            ),
        ) {
            if (state.isRequesting) {
                CircularProgressIndicator(color = colors.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = str(S.ONBOARDING_ALERTS_ENABLE),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
