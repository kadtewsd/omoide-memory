package com.kasakaid.omoidememory.ui.indicator

/**
 * 処理の進捗状況を表現する値オブジェクト。
 *
 * Pair<Int, Int> などの汎用タプル型を廃止し、進捗率やパーセンテージ計算をカプセル化します。
 *
 * @property progressed 現在処理完了した件数
 * @property total 全体の処理対象件数
 */
data class Progress(
    val progressed: Int,
    val total: Int,
) {
    /**
     * 進捗割合 (0.0f 〜 1.0f)。total が 0 以下の場合は 0f を返します。
     */
    val fraction: Float
        get() = if (total > 0) (progressed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    /**
     * 進捗パーセンテージ (0 〜 100)。
     */
    val percent: Int
        get() = (fraction * 100).toInt()

    companion object {
        /**
         * 全体件数を元に、初期状態 (進捗 0 件) の Progress を生成します。
         */
        fun initialize(total: Int): Progress =
            Progress(
                progressed = 0,
                total = total,
            )
    }
}

fun Progress?.current(total: Int): Progress = this ?: Progress.initialize(total = total)
