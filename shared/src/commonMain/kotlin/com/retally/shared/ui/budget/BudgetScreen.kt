package com.retally.shared.ui.budget

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
                    IconButton(onClick = { dispatch(BudgetContract.Action.CloseClicked) }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = str(S.ACTION_BACK), tint = c.ink)
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
                    // "$1,270 / $800"
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatCurrency(state.spentThisMonth),
                            style = ReTallyTheme.typography.display.copy(fontWeight = FontWeight.ExtraBold),
                            color = c.ink,
                        )
                        if (state.budgetAmount != null) {
                            Text(
                                " / ${formatCurrency(state.budgetAmount!!)}",
                                style = ReTallyTheme.typography.title,
                                color = c.muted,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
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

            // Prominent budget amount entry.
            val keyboardController = LocalSoftwareKeyboardController.current
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(d.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(d.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(str(S.BUDGET_MONTHLY_BUDGET), style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.Bold), color = c.ink)
                    OutlinedTextField(
                        value = state.draftAmount.ifEmpty { "00.00" },
                        onValueChange = { dispatch(BudgetContract.Action.AmountChanged(it)) },
                        prefix = { Text("$", style = ReTallyTheme.typography.display.copy(fontWeight = FontWeight.ExtraBold), color = c.muted) },
                        placeholder = { Text("00.00", style = ReTallyTheme.typography.display, color = c.muted.copy(alpha = 0.5f)) },
                        textStyle = ReTallyTheme.typography.display.copy(fontWeight = FontWeight.ExtraBold, color = c.ink, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide()
                            dispatch(BudgetContract.Action.SaveClicked)
                        }),
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

@Composable
private fun StatCard(value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    SurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(d.cardPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, style = ReTallyTheme.typography.title.copy(fontWeight = FontWeight.Bold), color = valueColor)
            Text(label, style = ReTallyTheme.typography.caption, color = c.muted)
        }
    }
}
