package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.domain.model.FilePathFinder
import com.kasakaid.omoidememory.service.query.CommentDto
import com.kasakaid.omoidememory.service.query.FeedCursor
import com.kasakaid.omoidememory.service.query.FeedPageResponse
import com.kasakaid.omoidememory.service.query.FilterMode
import com.kasakaid.omoidememory.service.query.OmoideCondition
import com.kasakaid.omoidememory.service.query.shared.MemoryCommentsQueryService
import com.kasakaid.omoidememory.service.query.shared.MemoryContentsQueryService
import com.kasakaid.omoidememory.service.query.shared.PhotoQueryService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.file.Files
import java.time.OffsetDateTime
import java.util.UUID

@RestController
@CrossOrigin
class MemorySharingController(
    private val memoryContentsQueryService: MemoryContentsQueryService,
    private val memoryCommentsQueryService: MemoryCommentsQueryService,
    private val photoQueryService: PhotoQueryService,
    private val filePathFinder: FilePathFinder,
) {
    private val logger = KotlinLogging.logger {}

    @GetMapping("/feed")
    suspend fun getFeed(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startInclusive: OffsetDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        endExclusive: OffsetDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        cursorCaptureTime: OffsetDateTime?,
        @RequestParam(required = false) cursorId: UUID?,
        @RequestParam(required = false) mode: FilterMode?,
        @RequestParam(required = false) limit: Int?,
    ): FeedPageResponse {
        val cursor =
            if (cursorCaptureTime != null && cursorId != null) {
                FeedCursor(captureTime = cursorCaptureTime, id = cursorId)
            } else {
                null
            }
        val condition =
            OmoideCondition(
                startInclusive = startInclusive,
                endExclusive = endExclusive,
                cursor = cursor,
                filterMode = mode ?: FilterMode.ALL,
            )
        val pageSize = (limit ?: 25).coerceIn(1, 1000)
        return memoryContentsQueryService.fetchFeedPage(
            condition = condition,
            limit = pageSize + 1,
        )
    }

    @GetMapping("/content/{id}/image", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    suspend fun getImage(
        @PathVariable id: UUID,
    ): ResponseEntity<ByteArray> {
        val photos = photoQueryService.findPhotosByIds(listOf(id))
        val photo =
            photos.firstOrNull()
                ?: return ResponseEntity.notFound().build()

        val path =
            filePathFinder.findPath(photo.serverPath)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val bytes =
            try {
                Files.readAllBytes(path)
            } catch (_: Exception) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }

        val mimeType = Files.probeContentType(path) ?: "image/jpeg"
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(mimeType))
            .body(bytes)
    }

    @GetMapping("/content/{id}/comments")
    suspend fun getComments(
        @PathVariable id: java.util.UUID,
    ): List<CommentDto> =
        memoryCommentsQueryService
            .getComments(id) { commentPojo, commenterName, commenterIconBase64 ->
                CommentDto(
                    id = commentPojo.id,
                    commenterName = commenterName,
                    commenterIconBase64 = commenterIconBase64,
                    commentBody = commentPojo.commentBody ?: "",
                    commentedAt = commentPojo.commentedAt ?: OffsetDateTime.now(),
                )
            }.collectList()
            .awaitSingle()

    @GetMapping("/contents-captured-ym")
    suspend fun getCapturedYearMonths(): List<OffsetDateTime> {
        logger.info { "年月を取得" }
        return memoryContentsQueryService.getCapturedYearMonths().also {
            logger.info { "$it 年月を取得完了" }
        }
    }

    @GetMapping("/comment-created-ym")
    suspend fun getCommentCreatedYearMonths(): List<OffsetDateTime> {
        logger.info { "コメント日時年月を取得" }
        return memoryCommentsQueryService.getCommentCreatedYearMonths().awaitSingle().also {
            logger.info { "$it コメント日時年月を取得完了" }
        }
    }
}
