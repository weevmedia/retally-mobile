package com.retally.shared.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.retally.shared.resources.Res
import com.retally.shared.resources.logo_7eleven
import com.retally.shared.resources.logo_aldi
import com.retally.shared.resources.logo_costco
import com.retally.shared.resources.logo_cvs
import com.retally.shared.resources.logo_target
import com.retally.shared.resources.logo_trader_joes
import com.retally.shared.resources.logo_walgreens
import com.retally.shared.resources.logo_walmart
import com.retally.shared.resources.logo_whole_foods
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Bundled store logos, matched by fragments of the parsed store name. The first fragment
 * contained in the lowercased name wins, so longer/more-specific fragments go first.
 *
 * Kroger has no bundled logo (its current mark isn't available as a freely reusable SVG)
 * and falls back to the generic storefront icon, like any unknown store.
 */
private val STORE_LOGOS: List<Pair<String, DrawableResource>> = listOf(
    "whole foods" to Res.drawable.logo_whole_foods,
    "trader joe" to Res.drawable.logo_trader_joes,
    "7-eleven" to Res.drawable.logo_7eleven,
    "7 eleven" to Res.drawable.logo_7eleven,
    "walgreens" to Res.drawable.logo_walgreens,
    "costco" to Res.drawable.logo_costco,
    "walmart" to Res.drawable.logo_walmart,
    "wal-mart" to Res.drawable.logo_walmart,
    "target" to Res.drawable.logo_target,
    "aldi" to Res.drawable.logo_aldi,
    "cvs" to Res.drawable.logo_cvs,
)

/** The bundled logo for a parsed store name, or null when the store has no bundled logo. */
fun storeLogoFor(storeName: String): DrawableResource? {
    val normalized = storeName.lowercase()
    return STORE_LOGOS.firstOrNull { (fragment, _) -> fragment in normalized }?.second
}

/**
 * Store badge for receipt rows: the store's logo on a white disc when one is bundled,
 * otherwise a generic storefront glyph on the tinted disc. Same 44dp slot either way.
 */
@Composable
fun StoreIcon(storeName: String, modifier: Modifier = Modifier) {
    val logo = storeLogoFor(storeName)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (logo != null) Color.White else MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = storeName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(30.dp),
            )
        } else {
            Icon(
                Icons.Rounded.Store,
                contentDescription = storeName,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
