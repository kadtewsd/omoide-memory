package com.kasakaid.omoidememory.commentimport.domain.model

import org.springframework.util.MimeType
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.String
import kotlin.collections.List

typealias FileName = String

/**
 * CSV の1行と、そこから得られるコメント情報の組。
 */
class OmoideCommentFile(
    val parsedLines: List<String>,
    val omoideComment: OmoideComment,
)

/**
 * CSV の1行から生まれるコメント情報を保持するドメインオブジェクト。
 * 変換・パースの責務は持たず、値を保持するだけ。
 */
class OmoideComment(
    val feedId: UUID,
    val fileName: String,
    val mediaType: String, // Extension.of(fileName).mimeType の型に合わせてください
    val commentBody: String,
    val commenterName: String,
    val commentedAt: OffsetDateTime,
)
