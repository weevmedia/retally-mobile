package com.retally.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import coil3.compose.AsyncImage
import com.retally.shared.data.store.StoreIconRepository
import com.retally.shared.domain.store.iconKeyOf
import org.koin.compose.koinInject

/**
 * The logo link for [storeName]: the device cache first, and only on a miss a one-store
 * lookup on the server (which is then cached). Null while looking up, or when the store
 * has no logo.
 */
@Composable
fun rememberStoreIconUrl(
    storeName: String,
    repository: StoreIconRepository = koinInject(),
): String? {
    val cached by repository.cachedIcons.collectAsState()
    LaunchedEffect(storeName) {
        if (storeName.isNotBlank() && !repository.isCached(storeName)) repository.resolve(storeName)
    }
    return cached[iconKeyOf(storeName)]
}

/**
 * The weekly-ad page for [storeName], set per store in the admin console. It arrives with
 * the logo lookup ([rememberStoreIconUrl]), so call that too. Null when the store has none.
 */
@Composable
fun rememberStoreWeeklyAdUrl(
    storeName: String,
    repository: StoreIconRepository = koinInject(),
): String? {
    val ads by repository.weeklyAds.collectAsState()
    return ads[iconKeyOf(storeName)]
}

/**
 * Store badge for receipt rows and the Smart List: the store's server logo on a white disc,
 * otherwise a generic storefront glyph on the tinted disc. Same slot either way (44dp unless
 * [modifier] sets a size).
 *
 * The logo image is fetched by Coil the first time it's shown and kept in its disk cache, so
 * later screens and launches show it without the network. Until it arrives — or if it fails
 * — the storefront glyph stays.
 */
@Composable
fun StoreIcon(storeName: String, modifier: Modifier = Modifier) {
    val iconUrl = rememberStoreIconUrl(storeName)
    var loaded by remember(iconUrl) { mutableStateOf(false) }
    val showLogo = iconUrl != null && loaded
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (showLogo) Color.White else MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!showLogo) {
            Icon(
                Icons.Rounded.Store,
                contentDescription = storeName,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxSize(0.55f),
            )
        }
        if (iconUrl != null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = if (showLogo) storeName else null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                // Fills the badge, 1dp inside its 1dp border.
                modifier = Modifier.circleLogo(inset = 2.dp),
            )
        }
    }
}

/**
 * Makes a logo fill its round badge edge to edge, leaving [inset] free around it
 * (the badge's border width + 1dp). Use with `ContentScale.Crop`.
 */
fun Modifier.circleLogo(inset: Dp): Modifier =
    this.fillMaxSize().padding(inset).clip(CircleShape)
