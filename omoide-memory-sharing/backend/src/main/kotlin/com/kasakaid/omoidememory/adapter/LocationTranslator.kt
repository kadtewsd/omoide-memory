package com.kasakaid.omoidememory.domain

/**
 * ACL (Anti-Corruption Layer) class for Location.
 */
object LocationTranslator {
    fun translateToJapaneseAddress(displayName: String?): String? {
        // 1. 基本的なガード句
        if (displayName.isNullOrBlank()) return displayName

        val elements = displayName.split(",").map { it.trim() }

        // 2. 日本のデータかどうかを判定（ACLの重要ポイント）
        // 末尾が "日本" または "Japan" でない場合は、変換せずにそのまま返す
        if (elements.lastOrNull() != "日本") {
            return displayName
        }

        // 3. 日本形式に変換（逆順にして結合）
        // elements: [りそな銀行, 2, 押上一丁目, ..., 日本]
        // reversed: [日本, 131-0045, 東京都, 墨田区, ...]
        return elements
            .reversed()
            .joinToString(",")
    }
}
