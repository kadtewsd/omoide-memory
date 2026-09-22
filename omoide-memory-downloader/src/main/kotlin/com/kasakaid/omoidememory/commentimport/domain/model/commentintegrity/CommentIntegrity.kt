package com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity

sealed interface CommentIntegrity {
    val fileName: String
}

/**
 * 完全一致
 */
class ExactlyMatched(
    override val fileName: String,
) : CommentIntegrity

/**
 * 1 件のマッチ
 */
class MatchedFile(
    override val fileName: String,
    val likePattern: String,
    val actualFileName: String,
) : CommentIntegrity

/**
 * 1 件のマッチ
 */
class Missed(
    override val fileName: String,
) : CommentIntegrity
