/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.howitworks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.presentation.howitworks.HowItWorksContract
import com.retally.shared.ui.components.CircleIconButton
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.scan.DoubleChargeVerdict
import com.retally.shared.ui.scan.LineItemCard
import com.retally.shared.ui.scan.ScanCookingAnimation
import com.retally.shared.ui.theme.ReTallyTheme
import kotlinx.coroutines.delay

/**
 * "How it works": a looping demo made of the real receipt-review pieces — the scan loader,
 * then each line-item card arriving (the double charge in amber with its DUPLICATE pill), then
 * the "Double charge found" verdict — followed by the four steps, scan tips and a scan button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowItWorksScreen(
    state: HowItWorksContract.State,
    dispatch: (HowItWorksContract.Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.HOW_IT_WORKS_TITLE), style = ReTallyTheme.typography.display, color = c.ink) },
                navigationIcon = {
                    CircleIconButton(onClick = { dispatch(HowItWorksContract.Action.CloseClicked) }) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = str(S.ACTION_BACK),
                            tint = c.ink,
                            // Same back chevron as Budget, Notifications and Recalls.
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = c.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = d.screen, end = d.screen,
                top = padding.calculateTopPadding() + d.xs,
                bottom = padding.calculateBottomPadding() + d.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(d.lg),
        ) {
            item {
                Text(str(S.HOW_IT_WORKS_INTRO), style = ReTallyTheme.typography.body, color = c.muted)
            }
            item { ScanDemo(state.sample) }
            item { Steps() }
            item { Tips() }
            item {
                PrimaryButton(
                    text = str(S.HOW_IT_WORKS_SCAN_CTA),
                    onClick = { dispatch(HowItWorksContract.Action.ScanClicked) },
                    icon = Icons.Rounded.DocumentScanner,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private enum class DemoPhase { READING, ITEMS, VERDICT }

/** The demo loop: scan loader → line items one by one → verdict, then again. */
@Composable
private fun ScanDemo(receipt: Receipt) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    var phase by remember { mutableStateOf(DemoPhase.READING) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(receipt) {
        while (true) {
            phase = DemoPhase.READING
            shown = 0
            delay(READING_MS)
            phase = DemoPhase.ITEMS
            repeat(receipt.items.size) {
                shown = it + 1
                delay(ITEM_STEP_MS)
            }
            phase = DemoPhase.VERDICT
            delay(VERDICT_HOLD_MS)
        }
    }
    SurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = c.surface) {
        Box {
            Column(Modifier.padding(d.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (phase) {
                            DemoPhase.READING -> str(S.HOW_IT_WORKS_DEMO_READING)
                            DemoPhase.ITEMS -> str(S.HOW_IT_WORKS_DEMO_CHECKING)
                            DemoPhase.VERDICT -> str(S.HOW_IT_WORKS_DEMO_DONE)
                        },
                        style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        str(S.HOW_IT_WORKS_DEMO_SAMPLE),
                        style = ReTallyTheme.typography.caption,
                        color = c.muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.background)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(d.md))
                Crossfade(targetState = phase == DemoPhase.READING, label = "demo-phase") { reading ->
                    if (reading) {
                        // The same loader the app shows while it reads a real receipt.
                        Box(Modifier.fillMaxWidth().heightIn(min = DEMO_MIN_HEIGHT), contentAlignment = Alignment.Center) {
                            ScanCookingAnimation(Modifier.size(LOADER_SIZE))
                        }
                    } else {
                        Column(
                            Modifier.heightIn(min = DEMO_MIN_HEIGHT),
                            verticalArrangement = Arrangement.spacedBy(d.sm),
                        ) {
                            AnimatedVisibility(
                                visible = phase == DemoPhase.VERDICT,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                DoubleChargeVerdict(receipt)
                            }
                            receipt.items.forEachIndexed { index, item ->
                                AnimatedVisibility(
                                    visible = index < shown,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically(),
                                ) {
                                    LineItemCard(
                                        item = item,
                                        categoryName = str(S.HOME_BUDGET_SCOPE_GROCERY),
                                        onKeepBoth = {},
                                        onRemoveOne = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // A demo, not a real receipt: swallow taps so "Keep both" / "Remove one" do nothing.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            )
        }
    }
}

@Composable
private fun Steps() {
    val d = ReTallyTheme.dimensions
    val steps = listOf(
        S.HOW_IT_WORKS_STEP_SCAN_TITLE to S.HOW_IT_WORKS_STEP_SCAN_BODY,
        S.HOW_IT_WORKS_STEP_READ_TITLE to S.HOW_IT_WORKS_STEP_READ_BODY,
        S.HOW_IT_WORKS_STEP_FLAG_TITLE to S.HOW_IT_WORKS_STEP_FLAG_BODY,
        S.HOW_IT_WORKS_STEP_BUDGET_TITLE to S.HOW_IT_WORKS_STEP_BUDGET_BODY,
    )
    Column(verticalArrangement = Arrangement.spacedBy(d.sm)) {
        steps.forEachIndexed { index, (title, body) -> Step(index + 1, str(title), str(body)) }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(d.lg)) {
            Box(
                Modifier.size(STEP_DISC).clip(CircleShape).background(c.primaryTint),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.Bold), color = c.primaryDark)
            }
            Spacer(Modifier.width(d.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(body, style = ReTallyTheme.typography.caption, color = c.muted)
            }
        }
    }
}

@Composable
private fun Tips() {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardRadius))
            .background(c.primaryTint)
            .padding(d.lg),
        verticalArrangement = Arrangement.spacedBy(d.xs),
    ) {
        Text(str(S.HOW_IT_WORKS_TIPS_TITLE), style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = c.primaryDark)
        listOf(S.HOW_IT_WORKS_TIP_FLAT, S.HOW_IT_WORKS_TIP_LIGHT, S.HOW_IT_WORKS_TIP_FRAME).forEach { tip ->
            Text("•  " + str(tip), style = ReTallyTheme.typography.caption, color = c.primaryDark)
        }
    }
}

private const val READING_MS = 2_200L
private const val ITEM_STEP_MS = 450L
private const val VERDICT_HOLD_MS = 4_000L
private val DEMO_MIN_HEIGHT = 220.dp
private val LOADER_SIZE = 140.dp
private val STEP_DISC = 32.dp
