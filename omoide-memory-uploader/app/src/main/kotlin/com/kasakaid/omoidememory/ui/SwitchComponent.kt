package com.kasakaid.omoidememory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


/**
 * チェックボックスの On/Off の状態を表現するもの
 */
sealed interface OnOff {
    val label: String
    val forOppositeLabel: String // 切り替え後のアクション（"解除" など）
    val isChecked: Boolean // Switch の見た目と同期する値

    data object On : OnOff {
        override val label = "選択中"
        override val forOppositeLabel = "解除"
        override val isChecked = true
    }

    data object Off : OnOff {
        override val label = "未選択"
        override val forOppositeLabel = "すべて選択"
        override val isChecked = false
    }
}

@Composable
fun MySwitch(
    onOff: OnOff,
    onSwitchChanged: (OnOff) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                Text(
                    text = onOff.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Blue,
                )
                Text(
                    text = "をすべて",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = onOff.forOppositeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Red,
                )
            }
            Switch(
                checked = onOff.isChecked,
                onCheckedChange = { clicked ->
                    when (clicked) {
                        true -> OnOff.On
                        false -> OnOff.Off
                    }.let {
                        onSwitchChanged(it)
                    }
                }
            )
        }
    }
}