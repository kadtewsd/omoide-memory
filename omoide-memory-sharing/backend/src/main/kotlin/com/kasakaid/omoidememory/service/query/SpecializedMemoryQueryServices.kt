package com.kasakaid.omoidememory.service.query

import com.kasakaid.omoidememory.domain.model.FilePathFinder
import com.kasakaid.omoidememory.infrastructure.LocalDiskFilePathFinder
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.CommentOmoide.Companion.COMMENT_OMOIDE
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.SyncedOmoidePhoto.Companion.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.SyncedOmoideVideo.Companion.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoideVideo
import com.kasakaid.omoidememory.service.query.shared.MemoryContentsQueryService
import org.jooq.Condition
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.time.OffsetDateTime
import java.util.Base64

@Service
class MemoryWithCommentQueryService(
    private val memoryContentsQueryService: MemoryContentsQueryService,
    private val memoryFeedDtoConverter: MemoryFeedDtoConverter,
) {
    suspend fun getFeed(
        startInclusive: OffsetDateTime?,
        endExclusive: OffsetDateTime?,
    ): List<MemoryFeedDto> {
        val (photoCondition, videoCondition, commentCondition) = buildDateCondition(startInclusive, endExclusive)
        val data = memoryContentsQueryService.fetchOmoideMemory(photoCondition, videoCondition, commentCondition)
        return memoryFeedDtoConverter
            .convert(data)
            .filter { it.commentCount > 0 }
    }
}

@Service
class OmoideMemoryQueryService(
    private val memoryContentsQueryService: MemoryContentsQueryService,
    private val memoryFeedDtoConverter: MemoryFeedDtoConverter,
) {
    suspend fun getFeed(
        startInclusive: OffsetDateTime?,
        endExclusive: OffsetDateTime?,
    ): List<MemoryFeedDto> {
        val (photoCondition, videoCondition, commentCondition) = buildDateCondition(startInclusive, endExclusive)
        val data = memoryContentsQueryService.fetchOmoideMemory(photoCondition, videoCondition, commentCondition)
        return memoryFeedDtoConverter.convert(data)
    }
}

private fun buildDateCondition(
    startInclusive: OffsetDateTime?,
    endExclusive: OffsetDateTime?,
): Triple<Condition, Condition, Condition> {
    if (startInclusive == null || endExclusive == null) {
        return Triple(
            DSL
                .noCondition(),
            DSL
                .noCondition(),
            DSL
                .noCondition(),
        )
    }

    val photoCondition =
        SYNCED_OMOIDE_PHOTO.run {
            CAPTURE_TIME
                .ge(
                    startInclusive,
                ).and(
                    CAPTURE_TIME
                        .lt(endExclusive),
                )
        }
    val videoCondition =
        SYNCED_OMOIDE_VIDEO.run {
            CAPTURE_TIME
                .ge(
                    startInclusive,
                ).and(
                    CAPTURE_TIME
                        .lt(endExclusive),
                )
        }
    val commentCondition =
        COMMENT_OMOIDE.run {
            COMMENTED_AT
                .ge(
                    startInclusive,
                ).and(
                    COMMENTED_AT
                        .lt(endExclusive),
                )
        }

    return Triple(photoCondition, videoCondition, commentCondition)
}

@Component
class MemoryFeedDtoConverter(
    private val filePathFinder: FilePathFinder,
) {
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
            orphanCommentsByFileName.map { (fileName, commentList) ->
                MemoryFeedDto(
                    id = commentList.firstOrNull()?.feedId,
                    type = null,
                    contentBase64 = null,
                    commentedAt = commentList.mapNotNull { it.commentedAt }.minOrNull() ?: OffsetDateTime.now(),
                    captureTime = null,
                    thumbnailBase64 = null,
                    thumbnailMimeType = null,
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
    ): MemoryFeedDto {
        val contentBase64 =
            filePathFinder.findPath(photo.serverPath)?.let { path ->
                try {
                    val bytes = Files.readAllBytes(path)
                    val mimeType = Files.probeContentType(path) ?: "image/jpeg"
                    "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
                } catch (_: Exception) {
                    null
                }
            }

        return MemoryFeedDto(
            id = photo.id,
            type = "PHOTO",
            contentBase64 = contentBase64,
            commentedAt = comments.mapNotNull { it.commentedAt }.minOrNull() ?: photo.captureTime ?: OffsetDateTime.now(),
            captureTime = photo.captureTime,
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
