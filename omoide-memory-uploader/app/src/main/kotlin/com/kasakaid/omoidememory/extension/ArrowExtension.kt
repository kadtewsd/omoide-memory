package com.kasakaid.omoidememory.extension

import arrow.core.Option

/**
 * Iterable<Option<T>> から Some の値だけを抽出し、アンラップして List<T> を返します。
 * ArrowKt 2.x からレギュラーです
 */
fun <T> Iterable<Option<T>>.flattenOption(): List<T> {
    return this.mapNotNull { option ->
        option.getOrNull()
    }
}

/**
 * Sequence 版も用意しておくと、巨大な Cursor 処理でもメモリ効率が最大化されます。
 */
fun <T> Sequence<Option<T>>.flattenOption(): Sequence<T> {
    return this.mapNotNull { option ->
        option.getOrNull()
    }
}