package com.retally.shared.ui.budget

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.retally.shared.ui.components.CircleIconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.retally.shared.presentation.budget.centsEntry
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.retally.shared.presentation.budget.BudgetContract
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.format.formatCurrency
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * Monthly grocery budget, opened from Home's Spent tile. Pace-based 3-color bar:
 * green (on track), orange (heading over), red (over pace or over budget).
 * The budget resets on the 1st — a new month starts empty; history stays in the
 * `budget` table for future spending-habit analysis and savings predictions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    state: BudgetContract.State,
    dispatch: (BudgetContract.Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.BUDGET_TITLE), style = ReTallyTheme.typography.display, color = c.ink) },
                navigationIcon = {
                    CircleIconButton(onClick = { dispatch(BudgetContract.Action.CloseClicked) }) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = str(S.ACTION_BACK),
                            tint = c.ink,
                            // The chevron glyph has wide built-in padding; 32dp matches the other screens.
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
        containerColor = c.background,
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary)
            }
            return@Scaffold
        }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        // Keyboard Done: hide the keyboard, and save when there's a new valid amount.
        val onKeyboardDone = {
            keyboardController?.hide()
            focusManager.clearFocus()
            if (state.canSave) dispatch(BudgetContract.Action.SaveClicked)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d.screen),
            verticalArrangement = Arrangement.spacedBy(d.lg),
        ) {
            Spacer(Modifier.height(d.xs))
            // Month + resets date.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.monthLabel,
                    style = ReTallyTheme.typography.display.copy(fontWeight = FontWeight.ExtraBold),
                    color = c.ink,
                )
                Text(
                    "Resets ${state.nextMonthLabel} 1",
                    style = ReTallyTheme.typography.caption,
                    color = c.muted,
                )
            }

            // Main "Spent so far" card — matches the prototype layout.
            val barColor = when (state.progressColor) {
                BudgetContract.ProgressColor.GREEN -> c.primary
                BudgetContract.ProgressColor.ORANGE -> c.warning
                BudgetContract.ProgressColor.RED -> c.danger
                null -> c.muted.copy(alpha = 0.25f)
            }
            val badgeText = when (state.progressColor) {
                BudgetContract.ProgressColor.GREEN -> str(S.BUDGET_BADGE_ON_TRACK)
                BudgetContract.ProgressColor.ORANGE -> str(S.BUDGET_BADGE_NEAR_LIMIT)
                BudgetContract.ProgressColor.RED -> str(S.BUDGET_BADGE_OVER_PACE)
                null -> null
            }
            val badgeColor = when (state.progressColor) {
                BudgetContract.ProgressColor.GREEN -> c.primary
                BudgetContract.ProgressColor.ORANGE -> c.warning
                BudgetContract.ProgressColor.RED -> c.danger
                null -> c.muted
            }
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(d.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(d.sm),
                ) {
                    // "Spent so far" + status badge.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(str(S.BUDGET_SPENT_SO_FAR), style = ReTallyTheme.typography.body, color = c.muted)
                        if (badgeText != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(badgeText, style = ReTallyTheme.typography.caption.copy(fontWeight = FontWeight.Bold), color = badgeColor)
                            }
                        }
                    }
                    // "$1,270 / $800" — one line that shrinks to fit large amounts.
                    val big = ReTallyTheme.typography.display
                    BasicText(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.ExtraBold)) {
                                append(formatCurrency(state.spentThisMonth))
                            }
                            state.budgetAmount?.let { budget ->
                                withStyle(SpanStyle(color = c.muted, fontWeight = FontWeight.Normal, fontSize = big.fontSize * 0.7f)) {
                                    append(" / ${formatCurrency(budget)}")
                                }
                            }
                        },
                        style = big.copy(color = c.ink),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = big.fontSize, stepSize = 0.5.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Progress bar.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(c.muted.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(state.progressFraction)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(barColor),
                        )
                    }
                    // "$0" + "$800" labels.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatCurrency(0.0), style = ReTallyTheme.typography.caption, color = c.muted)
                        if (state.budgetAmount != null) {
                            Text(formatCurrency(state.budgetAmount!!), style = ReTallyTheme.typography.caption, color = c.muted)
                        }
                    }
                    // Divider + projected.
                    if (state.budgetAmount != null && state.projected != null) {
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.muted.copy(alpha = 0.15f)))
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(str(S.BUDGET_PROJECTED), style = ReTallyTheme.typography.caption, color = c.muted)
                                Text(
                                    formatCurrency(state.projected!!),
                                    style = ReTallyTheme.typography.title.copy(fontWeight = FontWeight.ExtraBold),
                                    color = c.ink,
                                )
                            }
                            val overBy = state.projected!! - state.budgetAmount!!
                            if (overBy > 0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(str(S.BUDGET_OVER_PACE_BY), style = ReTallyTheme.typography.caption, color = c.muted)
                                    Text(
                                        formatCurrency(overBy),
                                        style = ReTallyTheme.typography.title.copy(fontWeight = FontWeight.ExtraBold),
                                        color = c.danger,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Remaining + days left.
            Row(horizontalArrangement = Arrangement.spacedBy(d.md)) {
                StatCard(
                    value = state.remaining?.let { formatCurrency(it) } ?: "—",
                    label = str(S.BUDGET_REMAINING),
                    valueColor = when {
                        state.remaining == null -> c.ink
                        state.remaining!! < 0 -> c.danger
                        state.budgetAmount != null && state.remaining!! < state.budgetAmount!! * 0.2 -> c.warning
                        else -> c.primary
                    },
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = "${state.daysLeft} ${str(S.BUDGET_DAYS_SUFFIX)}",
                    label = str(S.BUDGET_DAYS_LEFT),
                    valueColor = c.ink,
                    modifier = Modifier.weight(1f),
                )
            }

            // What the budget counts: all spending, or chosen categories.
            TrackScopeCard(state, dispatch)

            // Prominent budget amount entry.
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(d.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(d.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(str(S.BUDGET_MONTHLY_BUDGET), style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.Bold), color = c.ink)
                    // Cash-register entry: digits push in from the right (00.00 -> 00.01 -> 00.12)
                    // and the cursor is pinned to the end, so taps can't move it.
                    var amountField by remember { mutableStateOf(TextFieldValue(state.draftAmount, TextRange(state.draftAmount.length)), neverEqualPolicy()) }
                    LaunchedEffect(state.draftAmount) {
                        if (amountField.text != state.draftAmount) {
                            amountField = TextFieldValue(state.draftAmount, TextRange(state.draftAmount.length))
                        }
                    }
                    OutlinedTextField(
                        value = amountField,
                        onValueChange = { edited ->
                            val formatted = centsEntry(edited.text)
                            amountField = TextFieldValue(formatted, TextRange(formatted.length))
                            if (formatted != state.draftAmount) dispatch(BudgetContract.Action.AmountChanged(formatted))
                        },
                        prefix = { Text("$", style = ReTallyTheme.typography.display.copy(fontWeight = FontWeight.ExtraBold), color = c.muted) },
                        placeholder = { Text("00.00", style = ReTallyTheme.typography.display, color = c.muted.copy(alpha = 0.5f)) },
                        textStyle = ReTallyTheme.typography.display.copy(fontWeight = FontWeight.ExtraBold, color = c.ink, textAlign = TextAlign.Center),
                        // The decimal point is placed automatically; a typed "." is ignored.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onKeyboardDone() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    )
                    PrimaryButton(
                        text = str(S.BUDGET_SAVE),
                        onClick = {
                            keyboardController?.hide()
                            dispatch(BudgetContract.Action.SaveClicked)
                        },
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SpendingHistoryCard(state)

            Text(
                str(S.BUDGET_RESET_NOTE),
                style = ReTallyTheme.typography.caption,
                color = c.muted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(d.md))
        }
    }
}

/**
 * "What to track": All spending or Grocery only.
 * Changing it re-totals this month at once; past months keep their own choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackScopeCard(state: BudgetContract.State, dispatch: (BudgetContract.Action) -> Unit) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    val scope = state.scope
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(d.cardPadding), verticalArrangement = Arrangement.spacedBy(d.sm)) {
            Text(str(S.BUDGET_TRACK_TITLE), style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.Bold), color = c.ink)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = scope.allSpending,
                    onClick = { dispatch(BudgetContract.Action.TrackAllSpending) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = c.primary.copy(alpha = 0.16f)),
                ) { Text(str(S.BUDGET_TRACK_ALL)) }
                SegmentedButton(
                    selected = !scope.allSpending,
                    onClick = { dispatch(BudgetContract.Action.TrackCategories) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = c.primary.copy(alpha = 0.16f)),
                ) { Text(str(S.BUDGET_TRACK_CATEGORIES)) }
            }
            Text(
                str(if (scope.allSpending) S.BUDGET_TRACK_NOTE_ALL else S.BUDGET_TRACK_NOTE_CATEGORIES),
                style = ReTallyTheme.typography.caption,
                color = c.muted,
            )
            if (state.uncategorizedCount > 0) {
                Text(
                    str(S.BUDGET_UNCATEGORIZED, state.uncategorizedCount),
                    style = ReTallyTheme.typography.caption,
                    color = c.warning,
                )
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    SurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(d.cardPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Shrinks for big amounts so the whole value fits the half-width card.
            BasicText(
                value,
                style = ReTallyTheme.typography.title.copy(fontWeight = FontWeight.Bold, color = valueColor),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 11.sp,
                    maxFontSize = ReTallyTheme.typography.title.fontSize,
                    stepSize = 0.5.sp,
                ),
            )
            Text(label, style = ReTallyTheme.typography.caption, color = c.muted)
        }
    }
}

/**
 * Last 12 months of receipt totals as a bar chart (oldest → this month). Each bar is coloured
 * against that month's budget — green under, red over, soft green when no budget was set —
 * and a dark tick marks the budget. Tapping a bar shows its month, total and budget above.
 */
@Composable
private fun SpendingHistoryCard(state: BudgetContract.State) {
    val history = state.history
    if (history.isEmpty()) return
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selected = history.firstOrNull { it.key == selectedKey } ?: history.last()
    val scaleMax = history.maxOf { maxOf(it.total, it.budget ?: 0.0) }.takeIf { it > 0 }
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(d.cardPadding),
            verticalArrangement = Arrangement.spacedBy(d.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    str(S.BUDGET_HISTORY_TITLE),
                    style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                state.historyAverage?.let {
                    Text(str(S.BUDGET_HISTORY_AVG, formatCurrency(it)), style = ReTallyTheme.typography.caption, color = c.muted)
                }
            }
            if (scaleMax == null) {
                Text(str(S.BUDGET_HISTORY_EMPTY), style = ReTallyTheme.typography.secondaryBody, color = c.muted)
                return@Column
            }
            // The tapped month (this month by default).
            Column {
                Text(selected.label, style = ReTallyTheme.typography.caption, color = c.muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatCurrency(selected.total),
                        style = ReTallyTheme.typography.title,
                        color = c.ink,
                    )
                    Spacer(Modifier.width(d.sm))
                    Text(
                        selected.budget?.let { str(S.BUDGET_HISTORY_BUDGET, formatCurrency(it)) }
                            ?: str(S.BUDGET_HISTORY_NO_BUDGET),
                        style = ReTallyTheme.typography.caption,
                        color = if (selected.budget != null && selected.total > selected.budget) c.danger else c.muted,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                history.forEach { month ->
                    val isSelected = month.key == selected.key
                    val over = month.budget != null && month.total > month.budget
                    val barColor = when {
                        over -> c.danger
                        month.budget != null -> c.primary
                        else -> c.primary.copy(alpha = 0.55f)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { selectedKey = month.key },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val full = maxHeight
                            val barHeight = (full * (month.total / scaleMax).toFloat()).coerceAtLeast(3.dp)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        if (month.total <= 0) c.divider
                                        else barColor.copy(alpha = if (isSelected) barColor.alpha else barColor.alpha * 0.6f),
                                    ),
                            )
                            // Budget tick for months that had one.
                            month.budget?.let { budget ->
                                val y = full * (budget / scaleMax).toFloat()
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(y = -y)
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(c.ink.copy(alpha = 0.7f)),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            month.shortLabel.take(1),
                            style = ReTallyTheme.typography.caption.copy(
                                fontWeight = if (isSelected || month.isCurrent) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (isSelected) c.ink else c.muted,
                        )
                    }
                }
            }
        }
    }
}
