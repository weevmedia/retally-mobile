/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.scan

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.retally.shared.ui.components.CircleIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.presentation.scan.ScanReviewContract
import com.retally.shared.presentation.scan.ScanReviewContract.Action
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.theme.ReTallyTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.retally.shared.ui.format.formatShortDate
import com.retally.shared.ui.format.formatTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * "Edit receipt details": fix a misread store name, address or purchase date/time on one
 * receipt. Opened from the pencil beside the store name; Back (top-left) discards the draft,
 * Save writes it to the receipt. Date and time open the platform-styled Material pickers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditStorePage(
    edit: ScanReviewContract.StoreEdit,
    dispatch: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    val zone = TimeZone.currentSystemDefault()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Clearly visible outlines on every field (the default outline is faint on our background).
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = c.muted.copy(alpha = 0.7f),
        focusedBorderColor = c.primary,
        focusedLabelColor = c.primary,
    )
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.RECEIPT_EDIT_STORE_TITLE), style = ReTallyTheme.typography.display, color = c.ink) },
                navigationIcon = {
                    CircleIconButton(onClick = { dispatch(Action.StoreEditDismissed) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = str(S.ACTION_BACK),
                            tint = c.ink,
                            modifier = Modifier.padding(start = 6.dp).size(20.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d.screen, vertical = d.md),
            verticalArrangement = Arrangement.spacedBy(d.lg),
        ) {
            Text(str(S.RECEIPT_EDIT_STORE_HINT), style = ReTallyTheme.typography.secondaryBody, color = c.muted)
            OutlinedTextField(
                value = edit.name,
                onValueChange = { dispatch(Action.StoreNameChanged(it)) },
                label = { Text(str(S.RECEIPT_STORE_NAME_LABEL)) },
                singleLine = true,
                isError = !edit.canSave,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = edit.street,
                onValueChange = { dispatch(Action.StoreStreetChanged(it)) },
                label = { Text(str(S.RECEIPT_STORE_STREET_LABEL)) },
                maxLines = 3,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = edit.city,
                onValueChange = { dispatch(Action.StoreCityChanged(it)) },
                label = { Text(str(S.RECEIPT_STORE_CITY_LABEL)) },
                singleLine = true,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(d.md)) {
                OutlinedTextField(
                    value = edit.state,
                    onValueChange = { dispatch(Action.StoreStateChanged(it)) },
                    label = { Text(str(S.RECEIPT_STORE_STATE_LABEL)) },
                    placeholder = { Text("NJ") },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = edit.zip,
                    onValueChange = { dispatch(Action.StoreZipChanged(it)) },
                    label = { Text(str(S.RECEIPT_STORE_ZIP_LABEL)) },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1.4f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(d.md)) {
                PickerField(
                    label = str(S.RECEIPT_DATE_LABEL),
                    value = formatShortDate(edit.purchaseDate, zone),
                    colors = fieldColors,
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1.4f),
                )
                PickerField(
                    label = str(S.RECEIPT_TIME_LABEL),
                    value = formatTime(edit.purchaseDate, zone),
                    colors = fieldColors,
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryButton(
                text = str(S.RECEIPT_STORE_SAVE),
                icon = Icons.Rounded.Check,
                onClick = { dispatch(Action.StoreEditSaveClicked) },
                enabled = edit.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDatePicker) {
        PurchaseDatePicker(
            current = edit.purchaseDate,
            zone = zone,
            onPicked = { dispatch(Action.PurchaseDateChanged(it)) },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        PurchaseTimePicker(
            current = edit.purchaseDate,
            zone = zone,
            onPicked = { dispatch(Action.PurchaseDateChanged(it)) },
            onDismiss = { showTimePicker = false },
        )
    }
}

/** Read-only field that opens a picker when tapped (a text field can't take taps itself). */
@Composable
private fun PickerField(
    label: String,
    value: String,
    colors: TextFieldColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

/**
 * Material date picker, future days disabled. Keeps the receipt's time of day; the picker
 * works in UTC midnights, so dates are converted through the device zone both ways.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseDatePicker(
    current: Instant,
    zone: TimeZone,
    onPicked: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val local = current.toLocalDateTime(zone)
    val todayUtcMillis = Clock.System.now().toLocalDateTime(zone).date
        .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = local.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                    onPicked(LocalDateTime(date, local.time).toInstant(zone))
                }
                onDismiss()
            }) { Text(str(S.ACTION_OK)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(S.ACTION_CANCEL)) } },
    ) {
        DatePicker(state = state)
    }
}

/** Material time picker in a dialog; keeps the receipt's date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseTimePicker(
    current: Instant,
    zone: TimeZone,
    onPicked: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val local = current.toLocalDateTime(zone)
    val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPicked(LocalDateTime(local.date, LocalTime(state.hour, state.minute)).toInstant(zone))
                onDismiss()
            }) { Text(str(S.ACTION_OK)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(S.ACTION_CANCEL)) } },
        text = { TimePicker(state = state) },
    )
}
