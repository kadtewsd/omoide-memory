package com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity

import com.ibm.icu.text.Normalizer

/**
 * 1 件の orphan ファイル名に対して「試せる LIKE パターン文字列」を保持する。
 *
 * このオブジェクト自体は候補パターンの入れ物であり、
 * どの候補が実際に DB とマッチしたかは呼び出し元（サービス層）が
 * `likePatterns` を順に LIKE 検索して決定する。
 */
class OrphanFile(
    val orphanFileName: String,
    val likePatterns: List<String>,
    val mediaType: String,
)

object OrphanFileFactory {
    /**
     * LIKE のワイルドカード文字（\, %, _）を ESCAPE '\' でエスケープする。
     */
    private fun escapeLike(raw: String): String =
        raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    /**
     * 末尾サフィックス除去パターンの Regex。
     * (1)、-edited、-COLLAGE、-ANIMATION、~数字、 - コピー を対象とする。
     */
    private val trailingSuffixRegex = Regex("""(\(\d+\)|-edited|-COLLAGE|-ANIMATION|~\d+| - コピー)+$""")

    private const val MIN_BASE_LENGTH = 12
    private const val MAX_TRIM_CHARS = 3

    /**
     * 入力ファイル名から、マッチング試行順の LIKE パターン一覧を生成する（DB 非依存の純粋関数）。
     *
     * @param rawFileName CSV 上のファイル名（未加工）
     * @return 1件の orphan ファイル名に紐づく LIKE パターン文字列（優先順）
     */
    fun create(
        rawFileName: String,
        mediaType: String,
    ): OrphanFile {
        val normalized = Normalizer.normalize(rawFileName.trim(), Normalizer.NFC)
        val dotIndex = normalized.lastIndexOf('.')
        val base = if (dotIndex >= 0) normalized.substring(0, dotIndex) else normalized

        val patterns = mutableListOf<String>()

        // パターン 1: 大文字小文字無視の完全一致
        patterns.add(escapeLike(normalized))

        // パターン 2: base の前方一致
        patterns.add("${escapeLike(base)}%")

        // パターン 3: 末尾サフィックス除去後の前方一致
        val strippedBase = trailingSuffixRegex.replace(base, "")
        if (strippedBase != base) {
            patterns.add("${escapeLike(strippedBase)}%")
        }

        // パターン 4〜6: base の末尾を 1〜MAX_TRIM_CHARS 文字削った前方一致
        for (trimCount in 1..MAX_TRIM_CHARS) {
            val trimmedBase = base.dropLast(trimCount)
            if (trimmedBase.length >= MIN_BASE_LENGTH) {
                patterns.add("${escapeLike(trimmedBase)}%")
            }
        }

        return OrphanFile(
            orphanFileName = rawFileName,
            likePatterns = patterns,
            mediaType = mediaType,
        )
    }
}
