package com.kasakaid.sharing.query

import arrow.core.Either
import com.kasakaid.sharing.query.dto.CommentDto
import com.kasakaid.sharing.query.dto.MemoryFeedDto
import java.time.OffsetDateTime

interface MemoryQueryService {
    suspend fun getFeed(
        cursor: OffsetDateTime?,
        limit: Int,
    ): Either<Throwable, List<MemoryFeedDto>>

    suspend fun getPhotoComments(photoId: Long): Either<Throwable, List<CommentDto>>

    suspend fun getVideoComments(videoId: Long): Either<Throwable, List<CommentDto>>
}
