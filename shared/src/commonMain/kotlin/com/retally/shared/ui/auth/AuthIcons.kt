package com.retally.shared.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Brand glyphs for the sign-in row (drawn from SVG path data so they work on both
 * platforms from shared code; no binary assets needed).
 */
internal object AuthIcons {

    /** Apple logo silhouette (single colour — tinted by the caller). */
    @Composable
    fun apple(): ImageVector = remember {
        ImageVector.Builder(
            name = "apple",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 384f,
            viewportHeight = 512f,
        ).addPath(
            pathData = PathParser().parsePathString(APPLE_PATH).toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        ).build()
    }

    /** Google "G" in the four brand colours. */
    @Composable
    fun google(): ImageVector = remember {
        ImageVector.Builder(
            name = "google",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 48f,
            viewportHeight = 48f,
        )
            .addPath(
                pathData = PathParser().parsePathString(GOOGLE_BLUE).toNodes(),
                fill = androidx.compose.ui.graphics.SolidColor(Color(0xFF4285F4)),
            )
            .addPath(
                pathData = PathParser().parsePathString(GOOGLE_GREEN).toNodes(),
                fill = androidx.compose.ui.graphics.SolidColor(Color(0xFF34A853)),
            )
            .addPath(
                pathData = PathParser().parsePathString(GOOGLE_YELLOW).toNodes(),
                fill = androidx.compose.ui.graphics.SolidColor(Color(0xFFFBBC05)),
            )
            .addPath(
                pathData = PathParser().parsePathString(GOOGLE_RED).toNodes(),
                fill = androidx.compose.ui.graphics.SolidColor(Color(0xFFEA4335)),
            )
            .build()
    }

    private const val APPLE_PATH =
        "M318.7 268.7c-.2-36.7 16.4-64.4 50-84.8-18.8-26.9-47.2-41.7-84.7-44.6-35.5-2.8-74.3 20.7-88.5 20.7-15 0-49.4-19.7-76.4-19.7C63.3 141.2 4 184.8 4 273.5q0 39.3 14.4 81.2c12.8 36.7 59 126.7 107.2 125.2 25.2-.6 43-17.9 75.8-17.9 31.8 0 48.3 17.9 76.4 17.9 48.6-.7 90.4-82.5 102.6-119.3-65.2-30.7-61.7-90-61.7-91.9zm-56.6-164.2c27.3-32.4 24.8-61.9 24-72.5-24.1 1.4-52 16.4-67.9 34.9-17.5 19.8-27.8 44.3-25.6 71.9 26.1 2 49.9-11.4 69.5-34.3z"

    private const val GOOGLE_BLUE =
        "M43.611,20.083H42V20H24v8h11.303c-1.649,4.657-6.08,8-11.303,8c-6.627,0-12-5.373-12-12c0-6.627,5.373-12,12-12c3.059,0,5.842,1.154,7.961,3.039l5.657-5.657C34.046,6.053,29.268,4,24,4C12.955,4,4,12.955,4,24c0,11.045,8.955,20,20,20c11.045,0,20-8.955,20-20C44,22.659,43.862,21.35,43.611,20.083z"

    private const val GOOGLE_GREEN =
        "M6.306,14.691l6.571,4.819C14.655,15.108,18.961,12,24,12c3.059,0,5.842,1.154,7.961,3.039l5.657-5.657C34.046,6.053,29.268,4,24,4C16.318,4,9.656,8.337,6.306,14.691z"

    private const val GOOGLE_YELLOW =
        "M24,44c5.166,0,9.86-1.977,13.409-5.192l-6.19-5.238C29.211,35.091,26.715,36,24,36c-5.202,0-9.619-3.317-11.283-7.946l-6.522,5.025C9.505,39.556,16.227,44,24,44z"

    private const val GOOGLE_RED =
        "M43.611,20.083H42V20H24v8h11.303c-0.792,2.237-2.231,4.166-4.087,5.571c0.001-0.001,0.002-0.001,0.003-0.002l6.19,5.238C36.971,39.205,44,34,44,24C44,22.659,43.862,21.35,43.611,20.083z"
}
