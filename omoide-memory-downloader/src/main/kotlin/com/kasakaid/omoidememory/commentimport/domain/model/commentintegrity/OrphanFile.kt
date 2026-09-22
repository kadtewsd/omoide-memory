package com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity

import com.ibm.icu.text.Normalizer2
import com.kasakaid.omoidememory.commentimport.domain.model.FileName

/**
 * 1 件の orphan ファイル名に対して「試せる候補文字列」を保持する。
 *
 * likePatterns は % を含まない純粋な文字列。
 * 呼び出し元（リポジトリ）が %candidate% で囲んで中間一致 LIKE 検索する。
 */
class OrphanFile(
    val orphanFileName: String,
    val likePatterns: List<String>,
)

object OrphanFileFactory {
    /**
     * LIKE のワイルドカード文字（\, %）を ESCAPE '\' でエスケープする。
     * '_' は検索パターン自体に含まれるファイル名の区切り文字として必要なためエスケープする。
     */
    private fun escapeLike(raw: String): String =
        raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    /** 先頭の英字列＋アンダースコアによる接頭辞（PXL_, IMG_, VID_ 等）にマッチする。 */
    private val leadingPrefixRegex = Regex("""^[A-Za-z]+_""")

    /**
     * 入力ファイル名から、中間一致 LIKE 検索に使う候補文字列一覧を生成する（DB 非依存の純粋関数）。
     *
     * 処理手順:
     * 1. 複合拡張子を完全除去（最初の '.' 以降を切り捨て）
     * 2. 先頭の英字接頭辞を除去（PXL_, IMG_ 等）
     * 3. 末尾から 1 文字ずつ削りながら、'_' が消えるまでパターンを列挙（最大 10 件）
     *
     * 例: PXL_20260405_002855758.TS.mp4
     *   → 接頭辞除去後: 20260405_002855758
     *   → パターン: 20260405_002855758, 20260405_00285575, 20260405_0028557, ...（最大10件）
     *
     * @param rawFileName CSV 上のファイル名（未加工）
     * @return orphan ファイル名に紐づく候補文字列（絞り込みが強い順）
     */
    fun create(rawFileName: FileName): OrphanFile {
        val normalized = Normalizer2.getNFCInstance().normalize(rawFileName.trim())

        // 拡張子をすべて除去: 最初の '.' 以降を切り捨て（.TS.mp4 等の複合拡張子対応）
        val dotIndex = normalized.indexOf('.')
        val fullBase = if (dotIndex >= 0) normalized.substring(0, dotIndex) else normalized

        // 接頭辞除去: PXL_, IMG_, VID_ などの英字列 + '_' を除去
        val baseWithoutPrefix = leadingPrefixRegex.replace(fullBase, "")

        // 末尾から 1 文字ずつ削り、'_' が消えるまでパターンを列挙（最大 10 件）
        val likePatterns =
            buildList {
                var current = baseWithoutPrefix
                while (size < 10 && current.contains('_')) {
                    add(escapeLike(current))
                    current = current.dropLast(1)
                }
            }

        return OrphanFile(
            orphanFileName = rawFileName,
            likePatterns = likePatterns,
        )
    }
}
