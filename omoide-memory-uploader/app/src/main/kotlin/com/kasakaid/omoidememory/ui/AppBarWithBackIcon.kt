package com.kasakaid.omoidememory.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

/**
 * 画面ん左上の戻るを含んだ AppBar
 */
@Composable
fun AppBarWithBackIcon(
    title: String,
    onFinished: () -> Unit,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    TopAppBar(
        title = { Text(title) },
        // 🚀 左端にアイコンを置くスロット
        navigationIcon = {
            IconButton(onClick = onFinished) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "戻る",
                )
            }
        },
    )
}
