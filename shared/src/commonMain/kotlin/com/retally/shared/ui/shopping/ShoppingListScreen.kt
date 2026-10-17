package com.retally.shared.ui.shopping

import androidx.compose.animation.Crossfade
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.retally.shared.ui.components.StoreIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.retally.shared.ui.components.AdFormat
import com.retally.shared.ui.components.AdBannerSlot
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.domain.shopping.SuggestedItem
import com.retally.shared.presentation.shopping.ShoppingListContract
import com.retally.shared.presentation.shopping.ShoppingListContract.Action
import com.retally.shared.ui.components.EmptyState
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.components.SectionHeader
import com.retally.shared.ui.components.ShoppingBasketIcon
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.theme.ReTallyDimensions
import com.retally.shared.ui.theme.ReTallyTheme
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * AI shopping list tab. States, in priority order: guest → preparing (nothing cached yet) →
 * failed → no history → the list. A background refresh never hides a list that is showing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    state: ShoppingListContract.State,
    dispatch: (Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    adsEnabled: Boolean = false,
    bannerAdUnitId: String = "",
) {
    val c = ReTallyTheme.colors
    LaunchedEffect(Unit) { dispatch(Action.Appeared) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.TAB_AI_LIST), style = ReTallyTheme.typography.display, color = c.ink) },
                actions = {
                    if (state.selected != null && !state.isGuest) {
                        IconButton(onClick = { dispatch(Action.ShareClicked) }) {
                            Icon(Icons.Rounded.Share, contentDescription = str(S.SHOPPING_SHARE), tint = c.ink)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
        containerColor = c.background,
    ) { padding ->
        // A 320×50 banner floats 12dp above the bottom nav; content reserves its height so the
        // last row and the Done button are never hidden behind it.
        val showBanner = adsEnabled && bannerAdUnitId.isNotBlank()
        val bannerGap = 12.dp
        val bannerReserve = if (showBanner) AdFormat.BANNER.heightDp.dp + bannerGap * 2 else 0.dp
        Box(Modifier.fillMaxSize()) {
            val content = Modifier.fillMaxSize().padding(padding).padding(bottom = bannerReserve)
            when {
                state.isGuest -> Centered(content) {
                    EmptyState(
                        icon = ShoppingBasketIcon,
                        title = str(S.SHOPPING_GUEST_TITLE),
                        body = str(S.SHOPPING_GUEST_BODY),
                        iconSize = ReTallyDimensions.emptyStateIconLarge,
                        showIconDisc = false,
                        action = { PrimaryButton(str(S.SHOPPING_SIGN_IN), onClick = { dispatch(Action.SignInClicked) }) },
                    )
                }
                state.isPreparing -> PreparingView(state.preparingStep, content)
                state.loadFailed -> Centered(content) {
                    EmptyState(
                        icon = ShoppingBasketIcon,
                        title = str(S.SHOPPING_FAILED_TITLE),
                        body = str(S.SHOPPING_FAILED_BODY),
                        showIconDisc = false,
                        action = { PrimaryButton(str(S.SHOPPING_RETRY), onClick = { dispatch(Action.Refresh) }) },
                    )
                }
                state.selected == null -> Centered(content) {
                    EmptyState(
                        icon = ShoppingBasketIcon,
                        title = str(S.SHOPPING_NO_HISTORY_TITLE),
                        body = str(S.SHOPPING_NO_HISTORY_BODY),
                        iconSize = ReTallyDimensions.emptyStateIconLarge,
                        showIconDisc = false,
                    )
                }
                // Pull down to regenerate the list (replaces the old refresh icon).
                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { dispatch(Action.Refresh) },
                    modifier = content,
                ) {
                    ListContent(state, dispatch, Modifier.fillMaxSize())
                }
            }
            AdBannerSlot(
                adUnitId = bannerAdUnitId,
                enabled = showBanner,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = ReTallyTheme.dimensions.lg)
                    .padding(bottom = padding.calculateBottomPadding() + bannerGap),
            )
        }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.padding(horizontal = ReTallyTheme.dimensions.screen), contentAlignment = Alignment.Center) { content() }
}

// ---------- preparing ----------

/**
 * "Preparing your list": colourful groceries drop from a small receipt into the SmartList
 * basket, which bobs as each one lands. Status text and step dots sit underneath.
 */
@Composable
private fun PreparingView(step: Int, modifier: Modifier) {
    val c = ReTallyTheme.colors
    val steps = listOf(S.SHOPPING_STEP_1, S.SHOPPING_STEP_2, S.SHOPPING_STEP_3, S.SHOPPING_STEP_4)
    Column(
        modifier.padding(horizontal = ReTallyTheme.dimensions.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasketDropAnimation()
        Spacer(Modifier.height(ReTallyTheme.dimensions.xl))
        Text(str(S.SHOPPING_PREPARING_TITLE), style = ReTallyTheme.typography.title, color = c.ink)
        Spacer(Modifier.height(ReTallyTheme.dimensions.sm))
        Crossfade(targetState = step % steps.size, label = "step") { i ->
            Text(
                str(steps[i]), style = ReTallyTheme.typography.body, color = c.muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(ReTallyTheme.dimensions.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(steps.size) { i ->
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (i <= step % steps.size) c.primary else c.divider),
                )
            }
        }
    }
}

/** Grocery colours for the falling items (yellow, red, blue, green). */
private val DropColors = listOf(Color(0xFFFBBF24), Color(0xFFF87171), Color(0xFF60A5FA), Color(0xFF34D399))

@Composable
private fun BasketDropAnimation() {
    val c = ReTallyTheme.colors
    val transition = rememberInfiniteTransition(label = "basket")
    // One loop = every item dropping once, staggered by a quarter cycle.
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DROP_CYCLE_MS, easing = LinearEasing)),
        label = "cycle",
    )
    val itemCount = DropColors.size
    // Phase of each item within its own drop, 0..1.
    val phases = List(itemCount) { i -> ((t - i.toFloat() / itemCount) % 1f + 1f) % 1f }
    // Basket bob: a short dip whenever an item lands (phase ≈ LAND).
    val bob = phases.maxOf { p -> (1f - kotlin.math.abs(p - LAND) / 0.08f).coerceIn(0f, 1f) }

    Box(Modifier.size(width = 150.dp, height = 170.dp), contentAlignment = Alignment.TopCenter) {
        // Mini receipt the items come from.
        Column(
            Modifier.padding(top = 4.dp).size(width = 56.dp, height = 46.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(1f, 0.6f, 1f).forEach { w ->
                Box(Modifier.fillMaxWidth(w).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE5E7EB)))
            }
        }
        // Falling items.
        phases.forEachIndexed { i, p ->
            val fall = (p / LAND).coerceIn(0f, 1f)
            val eased = fall * fall // accelerate like a real drop
            val alpha = when {
                p < 0.1f -> p / 0.1f
                p > 0.85f -> ((1f - p) / 0.15f).coerceAtLeast(0f)
                else -> 1f
            }
            Box(
                Modifier
                    .padding(top = 40.dp)
                    .graphicsLayer {
                        translationX = ((i % 2) * 2 - 1) * (6 + 5 * i).dp.toPx() * 0.5f
                        translationY = (eased * 78).dp.toPx()
                        rotationZ = eased * 25f * if (i % 2 == 0) 1f else -1f
                        this.alpha = alpha
                    }
                    .size(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(DropColors[i]),
            )
        }
        // The SmartList basket, bobbing as items land.
        Icon(
            ShoppingBasketIcon,
            contentDescription = null,
            tint = c.primary,
            modifier = Modifier
                .padding(top = 78.dp)
                .graphicsLayer { translationY = (bob * 4).dp.toPx() }
                .size(92.dp),
        )
    }
}

private const val DROP_CYCLE_MS = 2_400
/** Point in an item's drop where it reaches the basket. */
private const val LAND = 0.7f

// ---------- list ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListContent(state: ShoppingListContract.State, dispatch: (Action) -> Unit, modifier: Modifier) {
    val d = ReTallyTheme.dimensions
    val list = state.selected ?: return
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = d.screen, vertical = d.sm),
        verticalArrangement = Arrangement.spacedBy(d.md),
    ) {
        item { StoreHeaderCard(state, dispatch) }
        item { AddItemCard(state.draftItem, dispatch) }

        if (state.isEmptyList) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Check,
                    title = str(S.SHOPPING_ALL_SET_TITLE),
                    body = str(S.SHOPPING_ALL_SET_BODY, list.storeName),
                )
            }
        }
        if (state.dueItems.isNotEmpty()) {
            item { SectionHeader(str(S.SHOPPING_SECTION_DUE, list.storeName), Modifier.padding(top = d.xs)) }
            item { ItemsCard(state.dueItems, state.checked, dispatch) }
        }
        if (state.alsoHereItems.isNotEmpty()) {
            item { SectionHeader(str(S.SHOPPING_SECTION_ALSO), Modifier.padding(top = d.xs)) }
            item { ItemsCard(state.alsoHereItems, state.checked, dispatch) }
        }
        if (state.added.isNotEmpty()) {
            item { SectionHeader(str(S.SHOPPING_SECTION_ADDED), Modifier.padding(top = d.xs)) }
            item { ItemsCard(state.added.map { SuggestedItem(item = it) }, state.checked, dispatch) }
        }
        if (state.stapleItems.isNotEmpty()) {
            item { SectionHeader(str(S.SHOPPING_SECTION_STAPLES), Modifier.padding(top = d.xs)) }
            item {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(d.sm),
                    verticalArrangement = Arrangement.spacedBy(d.sm),
                ) {
                    state.stapleItems.forEach { staple ->
                        StapleChip(staple.item) { dispatch(Action.AddStaple(staple.item)) }
                    }
                }
            }
        }
        if (!state.isEmptyList || state.added.isNotEmpty()) {
            item {
                PrimaryButton(
                    str(S.SHOPPING_DONE),
                    onClick = { dispatch(Action.DoneShopping) },
                    enabled = state.checked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = d.xs),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) } // breathing room above the banner / tab bar
    }
}

/** White card: store logo, name (tap to switch store), usual day and freshness on one line. */
@Composable
private fun StoreHeaderCard(state: ShoppingListContract.State, dispatch: (Action) -> Unit) {
    val c = ReTallyTheme.colors
    val d = ReTallyTheme.dimensions
    val list = state.selected ?: return
    val canSwitch = state.lists.size > 1
    var open by remember { mutableStateOf(false) }
    val subtitle = listOfNotNull(
        list.usualShoppingDay?.let { str(S.SHOPPING_USUAL_DAY, it) },
        updatedLabel(list.generatedAt),
    ).joinToString(" · ")
    Box {
        SurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = if (canSwitch) ({ open = true }) else null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = d.cardPadding, vertical = d.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoreIcon(list.storeName)
                Spacer(Modifier.width(d.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        list.storeName,
                        style = ReTallyTheme.typography.title.copy(fontWeight = FontWeight.Bold),
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = ReTallyTheme.typography.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (canSwitch) Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = c.muted)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.lists.forEach { other ->
                DropdownMenuItem(
                    text = { Text(other.storeName) },
                    leadingIcon = { StoreIcon(other.storeName, Modifier.size(28.dp)) },
                    onClick = { open = false; dispatch(Action.SelectStore(other.storeKey)) },
                )
            }
        }
    }
}

@Composable
private fun updatedLabel(generatedAt: String): String? {
    val at = runCatching { Instant.parse(generatedAt) }.getOrNull() ?: return null
    val minutes = (Clock.System.now() - at).inWholeMinutes
    return when {
        minutes < 2 -> str(S.SHOPPING_UPDATED_JUST_NOW)
        minutes < 60 -> str(S.SHOPPING_UPDATED_MINUTES, minutes.toInt())
        minutes < 48 * 60 -> str(S.SHOPPING_UPDATED_HOURS, (minutes / 60).toInt())
        else -> str(S.SHOPPING_UPDATED_DAYS, (minutes / (24 * 60)).toInt())
    }
}

/** White card: a filled input on the app background colour plus a compact primary Add button. */
@Composable
private fun AddItemCard(draft: String, dispatch: (Action) -> Unit) {
    val c = ReTallyTheme.colors
    val d = ReTallyTheme.dimensions
    val fieldShape = RoundedCornerShape(ReTallyDimensions.buttonRadius)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Add the item, then drop focus so the keyboard closes (Done key and Add button alike).
    val submit = {
        dispatch(Action.AddDraft)
        keyboard?.hide()
        focusManager.clearFocus()
    }
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(d.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f)
                    .height(ReTallyDimensions.buttonHeightCompact)
                    .clip(fieldShape)
                    .background(c.background)
                    .border(1.dp, c.divider, fieldShape)
                    .padding(horizontal = d.md),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) Text(str(S.SHOPPING_ADD_HINT), style = ReTallyTheme.typography.body, color = c.muted)
                BasicTextField(
                    value = draft,
                    onValueChange = { dispatch(Action.DraftChanged(it)) },
                    singleLine = true,
                    textStyle = ReTallyTheme.typography.body.copy(color = c.ink),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(d.sm))
            Box(
                Modifier.height(ReTallyDimensions.buttonHeightCompact)
                    .clip(fieldShape)
                    .background(if (draft.isNotBlank()) c.primary else c.primary.copy(alpha = 0.5f))
                    .clickable(enabled = draft.isNotBlank()) { submit() }
                    .padding(horizontal = d.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(str(S.SHOPPING_ADD), style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = c.onPrimary)
            }
        }
    }
}

@Composable
private fun ItemsCard(items: List<SuggestedItem>, checked: Set<String>, dispatch: (Action) -> Unit) {
    // Unchecked first, checked sink to the bottom.
    val ordered = items.sortedBy { it.item in checked }
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            ordered.forEachIndexed { index, item ->
                ItemRow(item, item.item in checked, dispatch)
                if (index < ordered.lastIndex) {
                    Box(
                        Modifier.fillMaxWidth()
                            .padding(start = ReTallyTheme.dimensions.cardPadding + 24.dp + ReTallyTheme.dimensions.md)
                            .height(1.dp)
                            .background(ReTallyTheme.colors.divider),
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: SuggestedItem, isChecked: Boolean, dispatch: (Action) -> Unit) {
    val c = ReTallyTheme.colors
    val d = ReTallyTheme.dimensions
    Row(
        Modifier.fillMaxWidth()
            .clickable { dispatch(Action.ToggleItem(item.item)) }
            .padding(start = d.cardPadding, top = d.sm, bottom = d.sm, end = d.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(isChecked)
        Spacer(Modifier.width(d.md))
        Column(Modifier.weight(1f)) {
            Text(
                item.item,
                style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = if (isChecked) c.muted else c.ink,
                textDecoration = if (isChecked) TextDecoration.LineThrough else null,
            )
            val sub = listOfNotNull(
                item.reason.takeIf { it.isNotBlank() },
                item.usualStore?.takeIf { item.reason.isBlank() }?.let { str(S.SHOPPING_USUALLY_FROM, it) },
            ).firstOrNull()
            if (sub != null && !isChecked) Text(sub, style = ReTallyTheme.typography.caption, color = c.muted)
        }
        if (item.confidence > 0 && !isChecked) ConfidenceDots(item.confidence)
        IconButton(onClick = { dispatch(Action.DismissItem(item.item)) }) {
            Icon(Icons.Rounded.Close, contentDescription = str(S.SHOPPING_NOT_NEEDED), tint = c.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CheckCircle(isChecked: Boolean) {
    val c = ReTallyTheme.colors
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .background(if (isChecked) c.primary else Color.Transparent)
            .border(2.dp, if (isChecked) c.primary else c.muted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isChecked) Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/** Three dots: how sure the AI is the shopper needs this on this trip. */
@Composable
private fun ConfidenceDots(confidence: Double) {
    val c = ReTallyTheme.colors
    val filled = when {
        confidence >= 0.75 -> 3
        confidence >= 0.45 -> 2
        else -> 1
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        repeat(3) { i ->
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (i < filled) c.primary else c.divider))
        }
    }
}

/** Outlined white pill: "+ Spinach". Tapping adds it to this trip's list. */
@Composable
private fun StapleChip(text: String, onClick: () -> Unit) {
    val c = ReTallyTheme.colors
    val shape = RoundedCornerShape(ReTallyDimensions.pillRadius)
    Row(
        Modifier.clip(shape)
            .background(c.surface)
            .border(1.dp, c.divider, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = ReTallyTheme.typography.caption, color = c.ink)
    }
}
