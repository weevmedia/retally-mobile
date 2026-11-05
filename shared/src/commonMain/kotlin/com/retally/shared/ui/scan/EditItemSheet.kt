/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.domain.category.ItemCategory
import com.retally.shared.presentation.scan.ScanReviewContract
import com.retally.shared.presentation.scan.ScanReviewContract.Action
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * "Edit item" bottom sheet, opened by swiping a line item and tapping Edit. Everything about
 * the line is here: name, item number, price, the spending category (chips) and the
 * "Mark as duplicate" switch. Save writes it to the receipt; swiping the sheet down or
 * Cancel discards the draft.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditItemSheet(
    edit: ScanReviewContract.ItemEdit,
    categories: List<ItemCategory>,
    dispatch: (Action) -> Unit,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = c.muted.copy(alpha = 0.7f),
        focusedBorderColor = c.primary,
        focusedLabelColor = c.primary,
    )
    // Larger, bolder than body copy: the shopper is checking what the scanner read against a
    // crumpled receipt, so the typed values need to be easy to read at a glance.
    val fieldTextStyle = ReTallyTheme.typography.body.copy(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        color = c.ink,
    )
    ModalBottomSheet(
        onDismissRequest = { dispatch(Action.ItemEditDismissed) },
        sheetState = sheetState,
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = d.cardRadius, topEnd = d.cardRadius),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = d.screen)
                .padding(bottom = d.lg),
            verticalArrangement = Arrangement.spacedBy(d.md),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    str(S.RECEIPT_EDIT_ITEM_TITLE),
                    style = ReTallyTheme.typography.title,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { dispatch(Action.ItemEditDismissed) }) {
                    Text(str(S.ACTION_CANCEL), style = ReTallyTheme.typography.button, color = c.muted)
                }
            }
            OutlinedTextField(
                value = edit.name,
                onValueChange = { dispatch(Action.ItemNameChanged(it)) },
                label = { Text(str(S.RECEIPT_ITEM_NAME_LABEL)) },
                singleLine = true,
                isError = edit.name.isBlank(),
                textStyle = fieldTextStyle,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(d.md)) {
                OutlinedTextField(
                    value = edit.itemNumber,
                    onValueChange = { dispatch(Action.ItemNumberChanged(it)) },
                    label = { Text(str(S.RECEIPT_ITEM_NUMBER_LABEL)) },
                    singleLine = true,
                    textStyle = fieldTextStyle,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1.4f),
                )
                OutlinedTextField(
                    value = edit.price,
                    onValueChange = { dispatch(Action.ItemPriceChanged(it)) },
                    label = { Text(str(S.RECEIPT_ITEM_PRICE_LABEL)) },
                    prefix = { Text("$", style = fieldTextStyle, color = c.muted) },
                    singleLine = true,
                    isError = edit.parsedPrice == null,
                    textStyle = fieldTextStyle,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
            }
            if (edit.parsedPrice == null) {
                Text(str(S.RECEIPT_ITEM_PRICE_ERROR), style = ReTallyTheme.typography.caption, color = c.danger)
            }

            Text(str(S.RECEIPT_ITEM_CATEGORY_LABEL), style = ReTallyTheme.typography.rowTitle, color = c.ink)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(d.sm),
                verticalArrangement = Arrangement.spacedBy(d.xs),
            ) {
                categories.forEach { category ->
                    val selected = category.id == edit.categoryId
                    FilterChip(
                        selected = selected,
                        onClick = { dispatch(Action.ItemCategoryPicked(category.id)) },
                        label = { Text(category.name, style = ReTallyTheme.typography.caption) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.width(16.dp)) }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.primaryTint,
                            selectedLabelColor = c.primaryDark,
                            selectedLeadingIconColor = c.primaryDark,
                            labelColor = c.ink,
                        ),
                    )
                }
            }

            // Manual duplicate flag — the parser is a heuristic; the shopper has the last word.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str(S.RECEIPT_ITEM_DUPLICATE_TOGGLE), style = ReTallyTheme.typography.rowTitle, color = c.ink)
                    Text(str(S.RECEIPT_ITEM_DUPLICATE_TOGGLE_SUB), style = ReTallyTheme.typography.caption, color = c.muted)
                }
                Spacer(Modifier.width(d.md))
                Switch(
                    checked = edit.isDuplicate,
                    onCheckedChange = { dispatch(Action.ItemDuplicateChanged(it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.warning, checkedThumbColor = c.surface),
                )
            }

            Spacer(Modifier.height(d.xs))
            PrimaryButton(
                text = str(S.RECEIPT_STORE_SAVE),
                onClick = { dispatch(Action.ItemEditSaveClicked) },
                enabled = edit.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
