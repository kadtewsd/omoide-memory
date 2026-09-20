package com.kasakaid.omoidememory.service.query.shared.memoryfeed

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoideVideo
import java.time.OffsetDateTime

object MemoryFeedDtoConverter {
    fun convert(data: Triple<List<SyncedOmoidePhoto>, List<SyncedOmoideVideo>, List<CommentOmoide>>): List<MemoryFeedDto> {
        val (photos, videos, comments) = data
        val commentsByFileName = comments.groupBy { it.fileName }

        val photoDtos =
            photos.map { photo ->
                transformPhotoToDto(photo, commentsByFileName[photo.fileName] ?: emptyList())
            }

        val videoDtos =
            videos.map { video ->
                transformVideoToDto(video, commentsByFileName[video.fileName] ?: emptyList())
            }

        val mediaFileNames = (photos.map { it.fileName } + videos.map { it.fileName }).toSet()
        val orphanCommentsByFileName =
            comments
                .filter { !mediaFileNames.contains(it.fileName) }
                .map { comment ->
                    comment.fileName to comment
                }.groupBy({ it.first }, { it.second })

        val commentOnlyDtos =
            orphanCommentsByFileName.map { (_, commentList) ->
                MemoryFeedDto(
                    id = commentList.firstOrNull()?.feedId,
                    type = null,
                    commentedAt = commentList.mapNotNull { it.commentedAt }.minOrNull() ?: OffsetDateTime.now(),
                    captureTime = null,
                    commentCount = commentList.size,
                )
            }

        return (photoDtos + videoDtos + commentOnlyDtos).sortedWith(
            compareByDescending<MemoryFeedDto, OffsetDateTime?>(nullsLast()) { it.captureTime ?: it.commentedAt }
                .thenByDescending(nullsLast()) { it.id },
        )
    }

    fun transformPhotoToDto(
        photo: SyncedOmoidePhoto,
        comments: List<CommentOmoide>,
    ): MemoryFeedDto =
        MemoryFeedDto(
            id = photo.id,
            type = "PHOTO",
            commentedAt = comments.mapNotNull { it.commentedAt }.minOrNull() ?: photo.captureTime ?: OffsetDateTime.now(),
            captureTime = photo.captureTime,
            commentCount = comments.size,
        )

    private fun transformVideoToDto(
        video: SyncedOmoideVideo,
        comments: List<CommentOmoide>,
    ): MemoryFeedDto {
        val commentedAt = comments.mapNotNull { it.commentedAt }.minOrNull() ?: video.captureTime ?: OffsetDateTime.now()

        return MemoryFeedDto(
            id = video.id,
            type = "VIDEO",
            commentedAt = commentedAt,
            captureTime = video.captureTime,
            commentCount = comments.size,
        )
    }
}
