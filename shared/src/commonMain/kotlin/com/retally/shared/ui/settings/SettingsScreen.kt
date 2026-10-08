package com.retally.shared.ui.settings

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FlagCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.retally.shared.data.store.StoreIconRepository
import com.retally.shared.domain.store.iconKeyOf
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retally.shared.domain.auth.Account
import com.retally.shared.domain.favorites.FavoriteStore
import com.retally.shared.domain.favorites.FavoriteStoresRepository
import com.retally.shared.presentation.settings.SettingsContract
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.components.SectionHeader
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.format.formatDateTime
import com.retally.shared.ui.theme.ReTallyTheme

private val Emerald = Color(0xFF10B981)
private val TileGray = Color(0xFFF1F3F4)
private val TileInk = Color(0xFF3C4043)
private val MutedGray = Color(0xFF80868B)
private val FavoriteRed = Color(0xFFEF4444)

/** Menu row titles: 18sp Bold, matching the reference. */
private val MenuTitleStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)

/**
 * The More menu: profile card, favorite stores (Instagram-style circles), and the grouped
 * settings — ACCOUNT, SETTINGS, DATA, LEGAL. "Account settings" opens the full-screen Account page
 * (login for guests, logout + delete for members); the favorites EDIT/+ opens the
 * full-screen store picker (search, 3-column grid with hearts, Save). Side effects
 * (URLs, messages, auth routing) are the caller's job.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsContract.State,
    dispatch: (SettingsContract.Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val page = when {
        state.showStorePickerPage -> MorePage.STORE_PICKER
        state.showAccountPage -> MorePage.ACCOUNT
        else -> MorePage.MAIN
    }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            // Push: new page slides in from the right; pop: it slides back out.
            if (targetState != MorePage.MAIN && initialState == MorePage.MAIN) {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 }
            } else {
                slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it }
            }
        },
        label = "more-page",
        modifier = modifier,
    ) { current ->
        when (current) {
            MorePage.ACCOUNT -> AccountScreen(
                state = state,
                dispatch = dispatch,
                snackbarHostState = snackbarHostState,
                onBack = { dispatch(SettingsContract.Action.AccountDismissed) },
            )
            MorePage.STORE_PICKER -> StorePickerScreen(
                state = state,
                dispatch = dispatch,
                onBack = { dispatch(SettingsContract.Action.StorePickerDismissed) },
            )
            MorePage.MAIN -> Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(str(S.MORE_TITLE), style = ReTallyTheme.typography.display, color = ReTallyTheme.colors.ink) },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
                snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                MoreContent(state = state, contentPadding = padding, dispatch = dispatch)
            }
        }
    }

    if (state.showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { dispatch(SettingsContract.Action.DeleteAllCancelled) },
            title = { Text(str(S.SETTINGS_DELETE_ALL_TITLE)) },
            text = { Text(str(S.SETTINGS_DELETE_ALL_BODY)) },
            confirmButton = {
                TextButton(onClick = { dispatch(SettingsContract.Action.DeleteAllConfirmed) }) {
                    Text(str(S.SETTINGS_DELETE_ALL_CONFIRM), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dispatch(SettingsContract.Action.DeleteAllCancelled) }) { Text(str(S.ACTION_CANCEL)) }
            },
        )
    }

    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { dispatch(SettingsContract.Action.LogoutCancelled) },
            title = { Text(str(S.MORE_ACCOUNT_LOGOUT_TITLE)) },
            text = { Text(str(S.MORE_ACCOUNT_LOGOUT_BODY)) },
            confirmButton = {
                TextButton(onClick = { dispatch(SettingsContract.Action.LogoutConfirmed) }) {
                    Text(str(S.MORE_ACCOUNT_LOGOUT))
                }
            },
            dismissButton = {
                TextButton(onClick = { dispatch(SettingsContract.Action.LogoutCancelled) }) { Text(str(S.ACTION_CANCEL)) }
            },
        )
    }

    if (state.showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { dispatch(SettingsContract.Action.DeleteAccountCancelled) },
            title = { Text(str(S.MORE_ACCOUNT_DELETE_TITLE)) },
            text = { Text(str(S.MORE_ACCOUNT_DELETE_BODY)) },
            confirmButton = {
                TextButton(onClick = { dispatch(SettingsContract.Action.DeleteAccountConfirmed) }) {
                    Text(str(S.MORE_ACCOUNT_DELETE), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dispatch(SettingsContract.Action.DeleteAccountCancelled) }) { Text(str(S.ACTION_CANCEL)) }
            },
        )
    }
}

@Composable
private fun MoreContent(
    state: SettingsContract.State,
    contentPadding: PaddingValues,
    dispatch: (SettingsContract.Action) -> Unit,
) {
    val d = ReTallyTheme.dimensions
    val account = state.account
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = d.lg,
                end = d.lg,
                top = contentPadding.calculateTopPadding() + d.sm,
                bottom = contentPadding.calculateBottomPadding() + d.xxl,
            ),
        verticalArrangement = Arrangement.spacedBy(d.md),
    ) {
        ProfileCard(account = account, dispatch = dispatch)

        FavoritesSection(state = state, dispatch = dispatch)

        SectionHeader(str(S.MORE_SECTION_ACCOUNT), modifier = Modifier.padding(top = d.sm))
        SurfaceCard {
            Column {
                MenuRow(
                    icon = Icons.Rounded.Settings,
                    title = str(S.MORE_ACCOUNT_SETTINGS),
                    subtitle = account.email ?: str(S.MORE_ACCOUNT_GUEST_SUB),
                    onClick = { dispatch(SettingsContract.Action.AccountClicked) },
                )
            }
        }

        SectionHeader(str(S.MORE_SECTION_SETTINGS), modifier = Modifier.padding(top = d.sm))
        SurfaceCard {
            Column {
                ToggleRow(
                    icon = Icons.Rounded.LocationOn,
                    title = str(S.SETTINGS_NEARBY_TITLE),
                    subtitle = str(S.SETTINGS_NEARBY_SUB),
                    checked = state.nearbyNotificationsEnabled,
                    onChange = { dispatch(SettingsContract.Action.SetNearbyNotificationsEnabled(it)) },
                )
                HorizontalDivider(Modifier.padding(horizontal = d.lg))
                ToggleRow(
                    icon = Icons.Rounded.FlagCircle,
                    title = str(S.SETTINGS_HIGHLIGHT_DUPLICATES),
                    subtitle = str(S.SETTINGS_HIGHLIGHT_DUPLICATES_SUB),
                    checked = state.preferences.highlightDuplicates,
                    onChange = { dispatch(SettingsContract.Action.SetHighlightDuplicates(it)) },
                )
                HorizontalDivider(Modifier.padding(horizontal = d.lg))
                ToggleRow(
                    icon = Icons.Rounded.Vibration,
                    title = str(S.SETTINGS_HAPTICS),
                    subtitle = str(S.SETTINGS_HAPTICS_SUB),
                    checked = state.preferences.hapticsEnabled,
                    onChange = { dispatch(SettingsContract.Action.SetHapticsEnabled(it)) },
                )
                // DEBUG-ONLY (remove before release): retest geofences without waiting 24h.
                HorizontalDivider(Modifier.padding(horizontal = d.lg))
                MenuRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Reset nearby cooldowns (debug)",
                    showChevron = false,
                    onClick = { dispatch(SettingsContract.Action.ResetNearbyCooldownsClicked) },
                )
                HorizontalDivider(Modifier.padding(horizontal = d.lg))
                MenuRow(
                    icon = Icons.AutoMirrored.Rounded.HelpOutline,
                    title = str(S.SETTINGS_HELP),
                    onClick = { dispatch(SettingsContract.Action.SupportClicked) },
                )
            }
        }

        SectionHeader(str(S.MORE_SECTION_DATA), modifier = Modifier.padding(top = d.sm))
        SurfaceCard {
            Column {
                ToggleRow(
                    icon = Icons.Rounded.CloudUpload,
                    title = str(S.SETTINGS_BACKUP),
                    subtitle = if (state.cloudConfigured) str(S.SETTINGS_BACKUP_SUB_ON) else str(S.SETTINGS_BACKUP_SUB_OFF),
                    checked = state.preferences.cloudBackupEnabled,
                    enabled = state.cloudConfigured,
                    onChange = { dispatch(SettingsContract.Action.SetCloudBackupEnabled(it)) },
                )
                if (state.cloudConfigured) {
                    HorizontalDivider(Modifier.padding(horizontal = d.lg))
                    BackupStatusRow(state = state, dispatch = dispatch)
                }
                HorizontalDivider(Modifier.padding(horizontal = d.lg))
                MenuRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = str(S.SETTINGS_DELETE_ALL),
                    destructive = true,
                    showChevron = false,
                    onClick = { dispatch(SettingsContract.Action.DeleteAllRequested) },
                )
            }
        }

        SectionHeader(str(S.MORE_SECTION_LEGAL), modifier = Modifier.padding(top = d.sm))
        SurfaceCard {
            Column {
                MenuRow(
                    icon = Icons.Rounded.Lock,
                    title = str(S.SETTINGS_PRIVACY),
                    onClick = { dispatch(SettingsContract.Action.PrivacyPolicyClicked) },
                )
                HorizontalDivider(Modifier.padding(horizontal = d.lg))
                MenuRow(
                    icon = Icons.Rounded.Description,
                    title = str(S.SETTINGS_TERMS),
                    onClick = { dispatch(SettingsContract.Action.TermsClicked) },
                )
            }
        }

        // Footer: weev Media mark, clean app version, copyright.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = d.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WeevMediaLogo(markSize = 40.dp)
            Text(
                str(S.SETTINGS_VERSION, state.appVersion),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                str(S.MORE_COPYRIGHT),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The Account page: identity card plus the current account options — login for guests,
 * logout and delete-account for members. Opened from "Account settings"; back returns
 * to the More menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountScreen(
    state: SettingsContract.State,
    dispatch: (SettingsContract.Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val d = ReTallyTheme.dimensions
    val account = state.account
    val name = account.displayName?.takeIf { it.isNotBlank() } ?: str(S.HOME_GUEST_NAME)
    val sub = account.email ?: str(S.MORE_ACCOUNT_GUEST_SUB)
    val initial = account.firstName?.firstOrNull()?.uppercaseChar()?.toString()
        ?: name.firstOrNull()?.uppercaseChar()?.toString()
        ?: "G"
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        str(S.MORE_ACCOUNT_TITLE),
                        style = ReTallyTheme.typography.title,
                        color = ReTallyTheme.colors.ink,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = str(S.ACTION_BACK),
                            tint = ReTallyTheme.colors.ink,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = d.lg,
                    end = d.lg,
                    top = padding.calculateTopPadding() + d.sm,
                    bottom = padding.calculateBottomPadding() + d.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(d.md),
        ) {
            SurfaceCard {
                Row(
                    Modifier.fillMaxWidth().padding(d.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(letter = initial, size = 56.dp)
                    Spacer(Modifier.width(d.md))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (account.isGuest) {
                Text(
                    str(S.MORE_ACCOUNT_GUEST_BODY),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGray,
                )
                SurfaceCard {
                    CenteredActionRow(
                        label = str(S.MORE_LOG_IN),
                        color = Emerald,
                        onClick = { dispatch(SettingsContract.Action.LoginClicked) },
                    )
                }
            } else {
                SurfaceCard {
                    CenteredActionRow(
                        label = str(S.MORE_SIGN_OUT),
                        color = MaterialTheme.colorScheme.error,
                        onClick = { dispatch(SettingsContract.Action.LogoutRequested) },
                    )
                }
                SurfaceCard {
                    CenteredActionRow(
                        label = str(S.MORE_DELETE_ACCOUNT),
                        color = MaterialTheme.colorScheme.error,
                        onClick = { dispatch(SettingsContract.Action.DeleteAccountRequested) },
                    )
                }
            }
        }
    }
}

/** Profile card: avatar initial, name, email (or guest), Edit opens the Account page. */
@Composable
private fun ProfileCard(
    account: Account,
    dispatch: (SettingsContract.Action) -> Unit,
) {
    val d = ReTallyTheme.dimensions
    val name = account.displayName?.takeIf { it.isNotBlank() } ?: str(S.HOME_GUEST_NAME)
    val sub = account.email ?: str(S.MORE_ACCOUNT_GUEST_SUB)
    val initial = account.firstName?.firstOrNull()?.uppercaseChar()?.toString()
        ?: name.firstOrNull()?.uppercaseChar()?.toString()
        ?: "G"
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth().padding(d.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(letter = initial, size = 52.dp)
            Spacer(Modifier.width(d.md))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { dispatch(SettingsContract.Action.AccountClicked) }) {
                Text(str(S.MORE_EDIT), color = Emerald, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Avatar(letter: String, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(TileGray),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = MaterialTheme.typography.titleLarge, color = TileInk)
    }
}

/**
 * Favorite stores: Instagram-style circles with an emerald ring, EDIT toggles remove mode.
 * The content area keeps a fixed height so the section never jumps between the empty
 * state and the populated circles.
 */
private val FavoritesContentHeight = 120.dp

@Composable
private fun FavoritesSection(
    state: SettingsContract.State,
    dispatch: (SettingsContract.Action) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(str(S.MORE_FAVORITE_STORES), modifier = Modifier.weight(1f))
        TextButton(onClick = { dispatch(SettingsContract.Action.EditFavoritesToggled) }) {
            Text(
                if (state.favoritesEditMode) str(S.MORE_DONE) else str(S.MORE_EDIT),
                color = Emerald,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(FavoritesContentHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (state.favoriteStores.isEmpty()) {
            Text(
                str(S.MORE_FAVORITES_EMPTY),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = ReTallyTheme.dimensions.lg),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.favoriteStores, key = { it.key }) { store ->
                    FavoriteCircle(
                        store = store,
                        editMode = state.favoritesEditMode,
                        onRemove = { dispatch(SettingsContract.Action.RemoveFavorite(store.key)) },
                    )
                }
                item {
                    AddFavoriteCircle(onClick = { dispatch(SettingsContract.Action.AddFavoritesClicked) })
                }
            }
        }
    }
}

@Composable
private fun FavoriteCircle(
    store: FavoriteStore,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(favoriteTint(store.key))
                    .border(2.dp, Emerald, CircleShape)
                    .clickable(enabled = editMode, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    monogram(store.name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
            if (editMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = str(S.ACTION_REMOVE),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            store.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddFavoriteCircle(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(1.5.dp, MutedGray, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MutedGray)
        }
        Spacer(Modifier.height(4.dp))
        Text("", style = MaterialTheme.typography.labelSmall)
    }
}

private val FavoriteTints = listOf(
    Color(0xFF0EA5A5),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFFF59E0B),
    Color(0xFF3B82F6),
    Color(0xFFEF4444),
    Color(0xFF10B981),
    Color(0xFFF97316),
)

private fun favoriteTint(key: String): Color =
    FavoriteTints[(key.hashCode() and Int.MAX_VALUE) % FavoriteTints.size]

private fun monogram(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"

/** Display form of a store name in Title Case. Only the first letter of each word is changed, so names like Trader Joe's and Stop & Shop keep their shape. */
private fun titleCaseName(name: String): String =
    name.lowercase().split(' ').joinToString(" ") { word ->
        val i = word.indexOfFirst { it.isLetter() }
        if (i < 0) word
        else word.substring(0, i) + word[i].uppercaseChar() + word.substring(i + 1)
    }

/** Which full-screen page the More menu is showing. */
private enum class MorePage { MAIN, ACCOUNT, STORE_PICKER }

/**
 * Full-screen store picker: a search box on top, a 3-column grid of store tiles each with a
 * corner heart to select it, and a Save button pinned at the bottom.
 */
@Composable
private fun StorePickerScreen(
    state: SettingsContract.State,
    dispatch: (SettingsContract.Action) -> Unit,
    onBack: () -> Unit,
) {
    val d = ReTallyTheme.dimensions
    val query = state.storeSearchQuery.trim()
    val visibleStores = remember(state.knownStores, query) {
        if (query.isEmpty()) state.knownStores
        else state.knownStores.filter { it.contains(query, ignoreCase = true) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Top bar: back arrow + title.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = str(S.ACTION_BACK),
                    tint = ReTallyTheme.colors.ink,
                )
            }
            Text(
                str(S.MORE_ADD_FAVORITES_TITLE),
                style = ReTallyTheme.typography.title,
                color = ReTallyTheme.colors.ink,
            )
        }
        // Search box.
        OutlinedTextField(
            value = state.storeSearchQuery,
            onValueChange = { dispatch(SettingsContract.Action.StoreSearchQueryChanged(it)) },
            placeholder = { Text(str(S.MORE_SEARCH_STORES)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MutedGray) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.screen, vertical = d.sm),
        )
        if (visibleStores.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    str(if (state.knownStores.isEmpty()) S.MORE_NO_STORES_YET else S.MORE_NO_STORES_MATCH),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = d.screen),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = d.screen,
                    end = d.screen,
                    top = d.sm,
                    bottom = d.md,
                ),
                horizontalArrangement = Arrangement.spacedBy(d.sm),
                verticalArrangement = Arrangement.spacedBy(d.md),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(visibleStores, key = { FavoriteStoresRepository.keyOf(it) }) { name ->
                    StoreGridTile(
                        name = name,
                        selected = state.storePickerSelection.contains(FavoriteStoresRepository.keyOf(name)),
                        onToggle = { dispatch(SettingsContract.Action.StorePickerSelectionToggled(name)) },
                    )
                }
            }
        }
        // Save, pinned at the bottom.
        PrimaryButton(
            text = str(S.MORE_SAVE),
            onClick = { dispatch(SettingsContract.Action.StorePickerSaved) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.screen, vertical = d.md),
        )
    }
}

/**
 * One store tile in the picker grid: a white card with the store's logo (or its
 * monogram until a logo is pushed from the server), its name, and a bare corner
 * heart. Tapping the tile selects it — the heart alone shows selection
 * (red filled when selected, gray outline when not).
 */
@Composable
private fun StoreGridTile(
    name: String,
    selected: Boolean,
    onToggle: () -> Unit,
    storeIcons: StoreIconRepository = koinInject(),
) {
    val icons by storeIcons.icons.collectAsState()
    val iconUrl = icons[iconKeyOf(name)]?.iconUrl
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onToggle)
            .padding(8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (iconUrl != null) {
                // Server-pushed logo on a white disc; padding keeps it off the edge.
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(52.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(favoriteTint(FavoriteStoresRepository.keyOf(name))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        monogram(name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                titleCaseName(name),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ReTallyTheme.colors.ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Heart pinned to the card's top-right corner, so it sits in the identical spot on
        // every card whether the name runs to one line or two.
        Icon(
            if (selected) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = null,
            tint = if (selected) FavoriteRed else MutedGray,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp),
        )
    }
}

/** The cloud-backup status block under the backup toggle (kept from the old screen). */
@Composable
private fun BackupStatusRow(
    state: SettingsContract.State,
    dispatch: (SettingsContract.Action) -> Unit,
) {
    val d = ReTallyTheme.dimensions
    Row(
        Modifier.fillMaxWidth().padding(horizontal = d.lg, vertical = d.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    state.syncStatus == SettingsContract.SyncStatus.SYNCING -> str(S.SETTINGS_BACKING_UP)
                    state.unsyncedCount == 0 -> str(S.SETTINGS_EVERYTHING_BACKED_UP)
                    state.unsyncedCount == 1 -> str(S.SETTINGS_PENDING_ONE)
                    else -> str(S.SETTINGS_PENDING_MANY, state.unsyncedCount)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            val status = state.syncMessage ?: state.lastSyncedAt?.let { str(S.SETTINGS_LAST_BACKUP, formatDateTime(it)) }
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.syncStatus == SettingsContract.SyncStatus.FAILURE)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(d.md))
        if (state.syncStatus == SettingsContract.SyncStatus.SYNCING) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Button(
                onClick = { dispatch(SettingsContract.Action.SyncNowClicked) },
                enabled = state.preferences.cloudBackupEnabled,
            ) { Text(str(S.SETTINGS_BACK_UP_NOW)) }
        }
    }
}

/** Uniform menu row: 34dp gray tile, 18sp Bold title, optional subtitle, chevron. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    destructive: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val d = ReTallyTheme.dimensions
    val titleColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = d.lg, vertical = d.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TileIcon(icon = icon, destructive = destructive)
        Spacer(Modifier.width(d.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MenuTitleStyle, color = titleColor)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showChevron) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MutedGray,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val d = ReTallyTheme.dimensions
    Row(
        Modifier.fillMaxWidth().padding(horizontal = d.lg, vertical = d.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TileIcon(icon = icon)
        Spacer(Modifier.width(d.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MenuTitleStyle)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(d.sm))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** The reference's gray icon tile: 34dp, 9dp radius, dark glyph. */
@Composable
private fun TileIcon(icon: ImageVector, destructive: Boolean = false) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (destructive) MaterialTheme.colorScheme.errorContainer else TileGray),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.onErrorContainer else TileInk,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Full-width centered red (or brand) action row, like the reference's SIGN OUT. */
@Composable
private fun CenteredActionRow(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            ),
        )
    }
}
