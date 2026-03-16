package com.kasakaid.omoidememory.commentimport.domain.model

import java.time.OffsetDateTime

class OmoideComment(
    val fileName: String,
    val commentBody: String,
    val commenterName: String,
    val commentedAt: OffsetDateTime,
)
