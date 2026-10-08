package com.retally.shared.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retally.shared.resources.Res
import com.retally.shared.resources.weev_media_logo
import org.jetbrains.compose.resources.painterResource

/** Small weev Media logo for the More menu footer. */
@Composable
fun WeevMediaLogo(markSize: Dp = 40.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.weev_media_logo),
        contentDescription = "Weev Media logo",
        modifier = modifier.size(markSize),
    )
}
