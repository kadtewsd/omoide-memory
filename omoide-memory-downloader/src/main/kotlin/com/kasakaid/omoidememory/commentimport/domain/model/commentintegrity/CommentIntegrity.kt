package com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity

sealed interface CommentIntegrity {
    val fileName: String

    // コンテンツの状態
    val contentState: String

    fun csvLine(commentBody: String): String =
        "$fileName,$commentBody," +
            when (this) {
                is ExactlyMatched, is Missed -> ",,"
                is MatchedFile -> "$likePattern,$actualFileName,"
            } + contentState
}

/**
 * 完全一致
 */
class ExactlyMatched(
    override val fileName: String,
) : CommentIntegrity {
    override val contentState: String = "コメントのコンテンツがある"
}

class MatchedFile(
    override val fileName: String,
    val likePattern: String,
    val actualFileName: String,
) : CommentIntegrity {
    override val contentState: String = "部分一致する (likePatter) ファイル名でコンテンツがある。ただ近い時期にコメントされただけで違うファイルである可能性が高い"
}

class Missed(
    override val fileName: String,
) : CommentIntegrity {
    override val contentState: String = "コンテンツ存在せず(要取得)"
}
