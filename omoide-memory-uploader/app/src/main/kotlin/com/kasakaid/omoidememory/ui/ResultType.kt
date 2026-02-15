package com.kasakaid.omoidememory.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


sealed interface ResultType {
    object NotStill : ResultType
    object Failure : ResultType
    object Success : ResultType
}

@Composable
fun colorOf(resultType: ResultType): Color {
    return when (resultType) {
        ResultType.NotStill -> MaterialTheme.colorScheme.onSurfaceVariant
        ResultType.Success -> Color.Green
        ResultType.Failure -> MaterialTheme.colorScheme.error
    }
}
