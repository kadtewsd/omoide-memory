package com.kasakaid.omoidememory.ui.snakbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * なにかを表示するためのスナックバー
 */
@Composable
fun StandardSnakbar(
    message: String?,
    onDismiss: () -> Unit,
    delayTime: DelayAndStep =
        DelayAndStep(
            durationMillis = 3000,
            steps = 2000,
        ),
    displayPlace: Alignment = Alignment.TopCenter,
    modifier: Modifier = Modifier,
) {
    var showMessage by remember { mutableStateOf(false) }
    var alpha by remember { mutableStateOf(1f) }

    LaunchedEffect(message) {
        if (message != null) {
            showMessage = true
            alpha = 1f
            try {
//                delay(delayTime)
                val anim = Animatable(1f)
                anim.animateTo(
                    targetValue = 0f,
//                    animationSpec = tween(durationMillis = 2000),
                    animationSpec = createFadeOutSpec(delayTime),
                ) {
                    alpha = value
                }
            } finally {
                showMessage = false
                onDismiss()
            }
        }
    }

    if (showMessage && message != null) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp),
            contentAlignment = displayPlace,
        ) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = alpha),
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier =
                    Modifier
                        .padding(horizontal = 24.dp)
                        .graphicsLayer(alpha = alpha),
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

class DelayAndStep(
    val durationMillis: Int,
    val steps: Int,
)

fun createFadeOutSpec(delayAndStep: DelayAndStep): AnimationSpec<Float> =
    keyframes {
        durationMillis = delayAndStep.durationMillis
        // 0..steps の範囲で分割を作成
        for (i in 0..delayAndStep.steps) {
            // 現在の進捗率 (0.0f ~ 1.0f)
            // N ステップに対して、何個、というので何割進んだかをみる
            val progress = i.toFloat() / delayAndStep.steps

            // 左側
            // 1.0f から 0.0f へ変化させる計算 (1.0 - progress)
            // ちょっとづつ進捗を増やしていく
            // 初めの方は i が少ないので、最後の進捗も少しの値。段々と 0 に向かっていく

            // 右側
            // 時間 (0ms から duration まで)
            // 全体の表示時間の何割か？という考え方で時間として出す
            // だんだんと durationMillis に収束する
            1.0f - progress at (durationMillis * progress).toInt()
        }
    }
