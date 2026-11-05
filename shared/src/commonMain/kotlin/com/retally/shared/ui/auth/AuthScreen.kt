/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.auth

import androidx.compose.foundation.layout.aspectRatio
import com.retally.shared.domain.auth.SignInProvider
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import com.retally.shared.ui.components.CircleIconButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.outlined.Email
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.presentation.auth.AuthContract
import com.retally.shared.resources.Res
import com.retally.shared.resources.onboarding_header_logo
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.theme.CappedTextScale
import com.retally.shared.ui.theme.ReTallyTheme
import org.jetbrains.compose.resources.painterResource

/**
 * Email-first sign-in screen — stateless; the host feeds [state] and forwards intents
 * via [dispatch]. Pre-OTP there is only the email step: email-only prompt +
 * Apple/Google row + guest pill + legal footer. The exists/firstName lookup result
 * never changes this screen (enumeration-safe); the post-verification branch lives
 * in the OTP flow.
 */
@Composable
fun AuthScreen(
    state: AuthContract.State,
    dispatch: (AuthContract.Action) -> Unit,
    snackbarHostState: SnackbarHostState? = null,
) {
    val colors = ReTallyTheme.colors

    @Composable
    fun Content() {
        when (state.step) {
            AuthContract.AuthStep.Email -> EmailStep(state, dispatch)
            AuthContract.AuthStep.NameForm -> NameFormStep(state, dispatch)
        }
    }

    if (snackbarHostState != null) {
        Scaffold(
            snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
            containerColor = colors.surface,
            // The page pads for the status/navigation bars itself; letting the Scaffold add them
            // too pushed everything (the back button included) a status-bar height too low.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Box(Modifier.padding(padding)) { Content() }
        }
    } else {
        Content()
    }
}

/** Step 1: email-only. Apple/Google, guest and legal stay on this step. */
@Composable
private fun EmailStep(
    state: AuthContract.State,
    dispatch: (AuthContract.Action) -> Unit,
) {
    val colors = ReTallyTheme.colors
    AuthColumn {
        // Back to the onboarding screen.
        Row(
            // Same spot as the back button on other screens (top-app-bar height, 8dp from the edge):
            // the column has 24dp side padding, so pull the row out by 16dp.
            modifier = Modifier.fillMaxWidth().height(BackBarHeight).offset(x = (-16).dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(onClick = { dispatch(AuthContract.Action.BackClicked) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = str(S.ACTION_BACK),
                    tint = colors.ink,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp),
                )
            }
        }
        LogoTitleBlock(title = str(S.AUTH_TITLE), subtitle = str(S.AUTH_SUBTITLE))

        Spacer(Modifier.height(16.dp))
        EmailFieldBlock(
            state = state,
            dispatch = dispatch,
            onDone = { dispatch(AuthContract.Action.EmailContinueClicked) },
        )

        Spacer(Modifier.height(20.dp))
        ContinueButton(
            onClick = { dispatch(AuthContract.Action.EmailContinueClicked) },
            enabled = !state.isCheckingAccount && !state.isSendingCode,
            loading = state.isCheckingAccount || state.isSendingCode,
        )

        Spacer(Modifier.height(16.dp))
        ProviderSection(state, dispatch)

        if (state.showGuestOption) {
            Spacer(Modifier.height(16.dp))
            GuestSection(state, dispatch)
        }

        Spacer(Modifier.height(24.dp))
        LegalFooter(
            onTermsClick = { dispatch(AuthContract.Action.TermsClicked) },
            onPrivacyClick = { dispatch(AuthContract.Action.PrivacyClicked) },
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Step 2 (new users only): first/last name + the email greyed out (not editable).
 * CONTINUE sends the code via requestCode and goes to the OTP screen. No code is
 * sent until this form is completed — abandoned signups burn no Resend sends.
 */
@Composable
private fun NameFormStep(
    state: AuthContract.State,
    dispatch: (AuthContract.Action) -> Unit,
) {
    val colors = ReTallyTheme.colors
    AuthColumn {
        // Back to the email step to fix the email.
        Row(
            // Same spot as the back button on other screens (top-app-bar height, 8dp from the edge):
            // the column has 24dp side padding, so pull the row out by 16dp.
            modifier = Modifier.fillMaxWidth().height(BackBarHeight).offset(x = (-16).dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(onClick = { dispatch(AuthContract.Action.BackClicked) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = str(S.ACTION_BACK),
                    tint = colors.ink,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp),
                )
            }
        }
        LogoTitleBlock(title = str(S.AUTH_NAME_TITLE), subtitle = str(S.AUTH_NAME_SUBTITLE))

        Spacer(Modifier.height(16.dp))
        FieldLabel(str(S.AUTH_FIRST_NAME))
        AuthField(
            value = state.firstName,
            onValueChange = { dispatch(AuthContract.Action.FirstNameChanged(it)) },
            hint = str(S.AUTH_FIRST_NAME_HINT),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                capitalization = KeyboardCapitalization.Words,
            ),
            isError = state.showNameError && state.firstName.isBlank(),
        )

        Spacer(Modifier.height(10.dp))
        FieldLabel(str(S.AUTH_LAST_NAME))
        AuthField(
            value = state.lastName,
            onValueChange = { dispatch(AuthContract.Action.LastNameChanged(it)) },
            hint = str(S.AUTH_LAST_NAME_HINT),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Words,
            ),
            isError = state.showNameError && state.lastName.isBlank(),
            onNext = { dispatch(AuthContract.Action.NameContinueClicked) },
        )

        Spacer(Modifier.height(10.dp))
        FieldLabel(str(S.AUTH_EMAIL))
        AuthField(
            value = state.email,
            onValueChange = {},
            hint = "",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = false,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Email,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp),
                )
            },
        )

        if (state.showNameError) {
            Text(
                text = str(S.AUTH_ERROR_REQUIRED),
                color = colors.danger,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
        if (state.showLookupError) {
            Text(
                text = str(S.AUTH_ERROR_LOOKUP),
                color = colors.danger,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        ContinueButton(
            onClick = { dispatch(AuthContract.Action.NameContinueClicked) },
            enabled = !state.isSendingCode,
            loading = state.isSendingCode,
        )
        Spacer(Modifier.height(12.dp))
    }
}

/** Shared scrollable column shell for every auth step. */
@Composable
private fun AuthColumn(content: @Composable ColumnScope.() -> Unit) {
    val colors = ReTallyTheme.colors
    CappedTextScale(maxFontScale = 1.15f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

@Composable
private fun LogoTitleBlock(title: String, subtitle: String?) {
    val colors = ReTallyTheme.colors
    // Logo mark.
    Image(
        painter = painterResource(Res.drawable.onboarding_header_logo),
        contentDescription = null,
        modifier = Modifier.size(104.dp),
    )

    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = colors.ink,
        textAlign = TextAlign.Center,
    )
    if (subtitle != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = subtitle,
            fontSize = 14.5.sp,
            color = colors.muted,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun EmailFieldBlock(
    state: AuthContract.State,
    dispatch: (AuthContract.Action) -> Unit,
    focusModifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    val colors = ReTallyTheme.colors
    FieldLabel(str(S.AUTH_EMAIL))
    AuthField(
        value = state.email,
        onValueChange = { dispatch(AuthContract.Action.EmailChanged(it)) },
        hint = str(S.AUTH_EMAIL_HINT),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        modifier = focusModifier,
        isError = state.showEmailError || state.showLookupError,
        onNext = onDone,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(20.dp),
            )
        },
    )
    if (state.showEmailError) {
        Text(
            text = str(S.AUTH_ERROR_EMAIL),
            color = colors.danger,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
    if (state.showLookupError) {
        Text(
            text = str(S.AUTH_ERROR_LOOKUP),
            color = colors.danger,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

@Composable
private fun ContinueButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = ReTallyTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = colors.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(
                text = str(S.AUTH_CONTINUE),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

@Composable
private fun ProviderSection(state: AuthContract.State, dispatch: (AuthContract.Action) -> Unit) {
    if (state.providers.isEmpty()) return
    DividerRow(str(S.AUTH_OR_CONTINUE_WITH))
    Spacer(Modifier.height(16.dp))
    // iOS: Apple, then Google. Android: Google. White with an outline, logo + label — a style both
    // Apple's and Google's branding guidelines allow.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.providers.forEach { provider ->
            ProviderButton(
                provider = provider,
                loading = state.providerInProgress == provider,
                enabled = !state.isProviderSigningIn && !state.isSubmitting,
                onClick = {
                    dispatch(
                        when (provider) {
                            SignInProvider.APPLE -> AuthContract.Action.AppleClicked
                            SignInProvider.GOOGLE -> AuthContract.Action.GoogleClicked
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ProviderButton(provider: SignInProvider, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = ReTallyTheme.colors
    val isApple = provider == SignInProvider.APPLE
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, colors.divider),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.ink),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.ink)
        } else {
            Icon(
                imageVector = if (isApple) AuthIcons.apple() else AuthIcons.google(),
                contentDescription = null,
                tint = if (isApple) colors.ink else Color.Unspecified,
                // The Apple logo is taller than wide (3:4); a square box squashed it sideways.
                modifier = if (isApple) Modifier.height(22.dp).aspectRatio(APPLE_LOGO_ASPECT) else Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                str(if (isApple) S.AUTH_APPLE else S.AUTH_GOOGLE),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
            )
        }
    }
}

@Composable
private fun GuestSection(
    state: AuthContract.State,
    dispatch: (AuthContract.Action) -> Unit,
) {
    val colors = ReTallyTheme.colors
    DividerRow(str(S.AUTH_OR))
    Spacer(Modifier.height(16.dp))

    OutlinedButton(
        onClick = { dispatch(AuthContract.Action.GuestClicked) },
        enabled = !state.isSubmitting,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, colors.divider),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surface,
            contentColor = colors.ink,
            disabledContainerColor = colors.surface,
        ),
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 0.dp),
    ) {
        Text(
            text = str(S.AUTH_GUEST),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = str(S.AUTH_GUEST_CAPTION),
        fontSize = 12.5.sp,
        color = colors.muted,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun FieldLabel(text: String) {
    val colors = ReTallyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
        )
        Text(
            text = " *",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.danger,
        )
    }
}

@Composable
internal fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    onNext: (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val colors = ReTallyTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        placeholder = { Text(hint, color = colors.muted, fontSize = 15.sp) },
        leadingIcon = leadingIcon,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onNext?.invoke() },
        ),
        shape = RoundedCornerShape(12.dp),
        isError = isError,
        textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.ink),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.background,
            unfocusedContainerColor = colors.background,
            errorContainerColor = colors.background,
            disabledContainerColor = colors.background,
            disabledTextColor = colors.muted,
            disabledBorderColor = Color.Transparent,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = Color.Transparent,
            errorBorderColor = colors.danger,
            cursorColor = colors.primary,
        ),
    )
}

@Composable
private fun DividerRow(text: String) {
    val colors = ReTallyTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider)
        Text(
            text = text,
            fontSize = 13.sp,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider)
    }
}

@Composable
private fun LegalFooter(onTermsClick: () -> Unit, onPrivacyClick: () -> Unit) {
    val colors = ReTallyTheme.colors
    val linkStyle = SpanStyle(
        color = colors.ink,
        textDecoration = TextDecoration.Underline,
    )
    // Spaces are added here, not taken from the strings: Android trims leading/trailing spaces
    // in strings.xml, which ran the words together ("agree to ourTerms & ConditionsandPrivacy").
    val annotated = buildAnnotatedString {
        append(str(S.AUTH_LEGAL_PREFIX).trim())
        append(' ')
        pushStringAnnotation(tag = "legal", annotation = "terms")
        withStyle(linkStyle) { append(str(S.AUTH_LEGAL_TERMS).trim()) }
        pop()
        append(' ')
        append(str(S.AUTH_LEGAL_AND).trim())
        append(' ')
        pushStringAnnotation(tag = "legal", annotation = "privacy")
        withStyle(linkStyle) { append(str(S.AUTH_LEGAL_PRIVACY).trim()) }
        pop()
    }
    ClickableText(
        text = annotated,
        style = TextStyle(
            fontSize = 12.5.sp,
            color = colors.muted,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        ),
        modifier = Modifier.padding(horizontal = 24.dp),
        onClick = { offset ->
            when (annotated.getStringAnnotations(tag = "legal", start = offset, end = offset).firstOrNull()?.item) {
                "terms" -> onTermsClick()
                "privacy" -> onPrivacyClick()
            }
        },
    )
}

/** Height of the back-button row: the same as a top app bar, so Back sits where it does elsewhere. */
internal val BackBarHeight = 64.dp

/** Width ÷ height of the Apple logo artwork (384×512). */
private const val APPLE_LOGO_ASPECT = 384f / 512f
