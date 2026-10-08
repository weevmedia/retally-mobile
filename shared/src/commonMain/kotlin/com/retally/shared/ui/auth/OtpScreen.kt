package com.retally.shared.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.presentation.auth.OtpContract
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.theme.CappedTextScale
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * Email OTP verification — stateless; the host feeds [state] and forwards intents via
 * [dispatch]. One hidden [BasicTextField] captures the numeric keyboard while six visual
 * boxes render the digits, so auto-advance and backspace behave natively on both platforms.
 */
@Composable
fun OtpScreen(
    state: OtpContract.State,
    dispatch: (OtpContract.Action) -> Unit,
    snackbarHostState: SnackbarHostState? = null,
) {
    val colors = ReTallyTheme.colors
    val codeFocus = remember { FocusRequester() }

    // Pop the keyboard as soon as the screen appears.
    LaunchedEffect(Unit) { codeFocus.requestFocus() }

    @Composable
    fun Content() {
    CappedTextScale(maxFontScale = 1.15f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Back to the sign-in screen (e.g. to fix the email).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                IconButton(onClick = { dispatch(OtpContract.Action.BackClicked) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = str(S.ACTION_BACK),
                        tint = colors.ink,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = str(S.OTP_TITLE),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = str(S.OTP_SUBTITLE, state.email),
                fontSize = 14.5.sp,
                color = colors.muted,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(32.dp))

            // Hidden input: owns the keyboard; the boxes below only render.
            BasicTextField(
                value = state.code,
                onValueChange = { dispatch(OtpContract.Action.CodeChanged(it)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                textStyle = TextStyle(color = colors.ink),
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(codeFocus),
            )
            OtpBoxes(
                code = state.code,
                hasError = state.showError,
                onTap = { codeFocus.requestFocus() },
            )

            if (state.showError) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = str(S.OTP_ERROR_MISMATCH),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.danger,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { dispatch(OtpContract.Action.VerifyClicked) },
                enabled = state.canVerify,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.primary.copy(alpha = 0.35f),
                    disabledContentColor = colors.onPrimary.copy(alpha = 0.7f),
                ),
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 0.dp),
            ) {
                if (state.isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = colors.onPrimary,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(
                        text = str(S.OTP_VERIFY),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            if (state.canResend) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = str(S.OTP_RESEND_PROMPT),
                        fontSize = 13.5.sp,
                        color = colors.muted,
                    )
                    TextButton(
                        onClick = { dispatch(OtpContract.Action.ResendClicked) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = str(S.OTP_RESEND),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
            } else {
                Text(
                    text = str(S.OTP_RESEND_WAIT, formatCooldown(state.resendCooldownSeconds)),
                    fontSize = 13.5.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    }

    if (snackbarHostState != null) {
        Scaffold(
            snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
            containerColor = colors.surface,
        ) { padding ->
            Box(Modifier.padding(padding)) { Content() }
        }
    } else {
        Content()
    }
}

/** "Resend in 1 minute 59 secs" style countdown — words, not digits. */
private fun formatCooldown(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 && seconds > 0 ->
            "$minutes ${if (minutes == 1) "minute" else "minutes"} " +
                "$seconds ${if (seconds == 1) "sec" else "secs"}"
        minutes > 0 -> "$minutes ${if (minutes == 1) "minute" else "minutes"}"
        else -> "$totalSeconds ${if (totalSeconds == 1) "sec" else "secs"}"
    }
}

@Composable
private fun OtpBoxes(
    code: String,
    hasError: Boolean,
    onTap: () -> Unit,
) {
    val colors = ReTallyTheme.colors
    val codeDescription = str(S.OTP_ACCESSIBILITY)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .semantics { contentDescription = codeDescription },
    ) {
        for (i in 0 until OtpContract.CODE_LENGTH) {
            val digit = code.getOrNull(i)?.toString() ?: ""
            val filled = digit.isNotEmpty()
            val isNext = i == code.length
            val borderColor = when {
                hasError -> colors.danger
                filled || isNext -> colors.primary
                else -> colors.divider
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 48.dp, height = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.background)
                    .border(
                        width = if (filled || isNext) 1.5.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) {
                Text(
                    text = digit,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                )
            }
        }
    }
}
