package com.kasakaid.omoidememory.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

/**
 * 画面左上の戻るを含んだ AppBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBarWithBackIcon(
    title: String,
    onFinished: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        // 🚀 左端にアイコンを置くスロット
        navigationIcon = { NavigationIcon(onFinished) },
    )
}

/**
 * アクションボタン を持てる AppBarWithBackIcon
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRowBarWithBackIcon(
    title: String,
    onFinished: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { NavigationIcon(onFinished) },
        actions = actions,
    )
}

@Composable
private fun NavigationIcon(onFinished: () -> Unit) {
    IconButton(onClick = onFinished) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "戻る",
        )
    }
}
