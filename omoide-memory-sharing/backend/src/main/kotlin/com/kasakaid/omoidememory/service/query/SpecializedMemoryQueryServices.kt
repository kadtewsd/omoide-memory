package com.kasakaid.omoidememory.service.query

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoideVideo
import com.kasakaid.omoidememory.service.query.shared.MemoryContentsQueryService
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.Base64

@Service
class MemoryWithCommentQueryService(
    private val memoryContentsQueryService: MemoryContentsQueryService,
) {
    suspend fun getFeed(
        startInclusive: OffsetDateTime?,
        endExclusive: OffsetDateTime?,
    ): List<MemoryFeedDto> {
        val (photoCondition, videoCondition, commentCondition) = buildDateCondition(startInclusive, endExclusive)
        val data = memoryContentsQueryService.fetchOmoideMemory(photoCondition, videoCondition, commentCondition)
        return MemoryFeedDtoConverter
            .convert(data)
            .filter { it.commentCount > 0 }
    }
}

@Service
class OmoideMemoryQueryService(
    private val memoryContentsQueryService: MemoryContentsQueryService,
) {
    suspend fun getFeed(
        startInclusive: OffsetDateTime?,
        endExclusive: OffsetDateTime?,
    ): List<MemoryFeedDto> {
        val (photoCondition, videoCondition, commentCondition) = buildDateCondition(startInclusive, endExclusive)
        val data = memoryContentsQueryService.fetchOmoideMemory(photoCondition, videoCondition, commentCondition)
        return MemoryFeedDtoConverter.convert(data)
    }
}

private fun buildDateCondition(
    startInclusive: OffsetDateTime?,
    endExclusive: OffsetDateTime?,
): Triple<org.jooq.Condition, org.jooq.Condition, org.jooq.Condition> {
    if (startInclusive == null || endExclusive == null) {
        return Triple(
            org.jooq.impl.DSL
                .noCondition(),
            org.jooq.impl.DSL
                .noCondition(),
            org.jooq.impl.DSL
                .noCondition(),
        )
    }

    val photoCondition =
        com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO.CAPTURE_TIME
            .ge(
                startInclusive,
            ).and(
                com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO.CAPTURE_TIME
                    .lt(endExclusive),
            )
    val videoCondition =
        com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO.CAPTURE_TIME
            .ge(
                startInclusive,
            ).and(
                com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO.CAPTURE_TIME
                    .lt(endExclusive),
            )
    val commentCondition =
        com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE.COMMENTED_AT
            .ge(
                startInclusive,
            ).and(
                com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE.COMMENTED_AT
                    .lt(endExclusive),
            )

    return Triple(photoCondition, videoCondition, commentCondition)
}

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

        val mediaFileNames = (photos.map { it.fileName } + videos.map { it.fileName }).filterNotNull().toSet()
        val orphanCommentsByFileName =
            comments
                .filter { !mediaFileNames.contains(it.fileName) }
                .mapNotNull { comment ->
                    comment.fileName?.let { fName -> fName to comment }
                }.groupBy({ it.first }, { it.second })

        val commentOnlyDtos =
            orphanCommentsByFileName.map { (fileName, commentList) ->
                val earliestComment = commentList.mapNotNull { it.commentedAt }.minOrNull() ?: OffsetDateTime.now()
                MemoryFeedDto(
                    id = commentList.firstOrNull()?.feedId ?: java.util.UUID.randomUUID(),
                    type = null,
                    contentBase64 = null,
                    commentedAt = earliestComment,
                    captureTime = null,
                    thumbnailBase64 = null,
                    thumbnailMimeType = null,
                    commentCount = commentList.size,
                )
            }

        return (photoDtos + videoDtos + commentOnlyDtos).sortedWith(
            compareByDescending<MemoryFeedDto, OffsetDateTime?>(nullsLast()) { it.captureTime ?: it.commentedAt }
                .thenByDescending { it.id },
        )
    }

    fun transformPhotoToDto(
        photo: SyncedOmoidePhoto,
        comments: List<CommentOmoide>,
    ): MemoryFeedDto {
        val serverPath = photo.serverPath ?: ""
        val captureTime = photo.captureTime
        val commentedAt = comments.mapNotNull { it.commentedAt }.minOrNull() ?: captureTime ?: OffsetDateTime.now()

        val contentBase64 =
            if (serverPath.isNotEmpty()) {
                try {
                    val path = Paths.get(serverPath)
                    if (Files.exists(path)) {
                        val bytes = Files.readAllBytes(path)
                        val mimeType = Files.probeContentType(path) ?: "image/jpeg"
                        "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

        return MemoryFeedDto(
            id = photo.id,
            type = "PHOTO",
            contentBase64 = contentBase64,
            commentedAt = commentedAt,
            captureTime = captureTime,
            thumbnailBase64 = null,
            thumbnailMimeType = null,
            commentCount = comments.size,
        )
    }

    private fun transformVideoToDto(
        video: SyncedOmoideVideo,
        comments: List<CommentOmoide>,
    ): MemoryFeedDto {
        val thumbnailMimeType = video.thumbnailMimeType ?: "image/jpeg"
        val commentedAt = comments.mapNotNull { it.commentedAt }.minOrNull() ?: video.captureTime ?: OffsetDateTime.now()

        val thumbnailBase64 =
            if (video.thumbnailImage != null) {
                "data:$thumbnailMimeType;base64,${Base64.getEncoder().encodeToString(video.thumbnailImage)}"
            } else {
                null
            }

        return MemoryFeedDto(
            id = video.id,
            type = "VIDEO",
            contentBase64 = thumbnailBase64,
            commentedAt = commentedAt,
            captureTime = video.captureTime,
            thumbnailBase64 = thumbnailBase64,
            thumbnailMimeType = thumbnailMimeType,
            commentCount = comments.size,
        )
    }
}
