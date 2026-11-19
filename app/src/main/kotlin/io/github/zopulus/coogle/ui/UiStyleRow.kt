// Material selector adapted from tiann/KernelSU (GPL-3.0); see NOTICE.txt.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.zopulus.coogle.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Theme
import io.github.suqi8.coui.kmp.preference.WindowSpinnerPreference

private const val TITLE = "界面风格"
private const val SUMMARY = "选择应用的界面风格"

@Composable
internal fun UiStyleRow(index: Int, count: Int, onSelect: (UiStyle) -> Unit) {
    val style = LocalUiStyle.current
    val icon = Icons.Filled.DisplaySettings
    if (style == UiStyle.COUI) {
        val items = remember { UiStyle.entries.map { DropdownItem(text = it.title) } }
        WindowSpinnerPreference(
            items = items,
            selectedIndex = style.ordinal,
            title = TITLE,
            summary = SUMMARY,
            startAction = { CouiRowIcon(COUIIcons.Theme) },
            cardListPosition = cardListPositionOf(index, count),
            onSelectedIndexChange = { onSelect(UiStyle.entries[it]) }
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var anchorOffset by remember { mutableStateOf(IntOffset.Zero) }
    val haptics = LocalHapticFeedback.current
    Box(Modifier.pointerInput(Unit) {
        awaitEachGesture {
            anchorOffset = awaitFirstDown(requireUnconsumed = false).position.round()
        }
    }) {
        MaterialSettingRow(index, count, icon, TITLE, SUMMARY, onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            expanded = true
        }) {
            Text(style.title, Modifier.fillMaxWidth(0.3f),
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
        }
        // Position the menu at the press within the row, like KernelSU.
        Box(Modifier.layout { measurable, constraints ->
            val anchor = measurable.measure(Constraints())
            layout(constraints.maxWidth, 0) { anchor.place(anchorOffset.x, anchorOffset.y) }
        }) {
            DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuGroup(
                    shapes = MenuDefaults.groupShape(index = 0, count = 1),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    UiStyle.entries.forEachIndexed { index, choice ->
                        SelectableDropdownMenuItem(
                            text = { Text(choice.title) },
                            selected = choice == style,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                expanded = false
                                onSelect(choice)
                            },
                            shapes = MenuDefaults.itemShape(index, UiStyle.entries.size),
                            selectedLeadingIcon = { Icon(Icons.Filled.Check, null, Modifier.size(MenuDefaults.LeadingIconSize)) }
                        )
                    }
                }
            }
        }
    }
}
