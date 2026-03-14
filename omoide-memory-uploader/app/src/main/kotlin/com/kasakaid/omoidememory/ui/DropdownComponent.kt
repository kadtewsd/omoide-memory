package com.kasakaid.omoidememory.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ラベルを持つアイテムを一覧表示するためのドロップダウンコンポーネント。
 */
@Composable
fun <T : EnumWithLabel> EnumDropdown(
    modifier: Modifier = Modifier,
    label: String,
    items: Array<T>,
    selectedItem: T?,
    onItemSelected: (T?) -> Unit,
    allLabel: String? = null,
    itemLabel: (T) -> String = { it.label },
    trigger: @Composable (onClick: () -> Unit, currentLabel: String) -> Unit = { onClick, currentLabel ->
        DefaultTrigger(onClick, currentLabel)
    },
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = selectedItem?.let { itemLabel(it) } ?: allLabel ?: label

    Box(modifier = modifier) {
        trigger({ expanded = true }, currentLabel)

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (allLabel != null) {
                DropdownMenuItem(
                    text = { Text(allLabel) },
                    onClick = {
                        onItemSelected(null)
                        expanded = false
                    },
                )
            }
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DefaultTrigger(
    onClick: () -> Unit,
    label: String,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
    }
}
