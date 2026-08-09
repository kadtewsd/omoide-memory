package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.service.query.CommentDto
import com.kasakaid.omoidememory.service.query.MemoryFeedDto
import com.kasakaid.omoidememory.service.query.MemoryWithCommentQueryService
import com.kasakaid.omoidememory.service.query.OmoideMemoryQueryService
import com.kasakaid.omoidememory.service.query.shared.MemoryCommentsQueryService
import com.kasakaid.omoidememory.service.query.shared.MemoryContentsQueryService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

enum class FilterMode {
    COMMENT_ONLY,
    ALL,
}

@RestController
@CrossOrigin
class MemorySharingController(
    private val memoryWithCommentQueryService: MemoryWithCommentQueryService,
    private val omoideMemoryQueryService: OmoideMemoryQueryService,
    private val memoryContentsQueryService: MemoryContentsQueryService,
    private val memoryCommentsQueryService: MemoryCommentsQueryService,
) {
    private val log = LoggerFactory.getLogger(MemorySharingController::class.java)

    @GetMapping("/feed")
    suspend fun getFeed(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startInclusive: OffsetDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        endExclusive: OffsetDateTime?,
        @RequestParam(defaultValue = "ALL") mode: FilterMode,
    ): List<MemoryFeedDto> {
        val resultFlux =
            when (mode) {
                FilterMode.COMMENT_ONLY -> memoryWithCommentQueryService.getFeed(startInclusive, endExclusive)
                FilterMode.ALL -> omoideMemoryQueryService.getFeed(startInclusive, endExclusive)
            }

        val items = resultFlux.collectList().awaitSingle()
        log.info("[GET /feed Response] count=${items.size}, items=$items")
        return items
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

    private val logger = KotlinLogging.logger {}

    @GetMapping("/contents-captured-ym")
    suspend fun getCapturedYearMonths(): List<OffsetDateTime> {
        logger.info { "年月を取得" }
        return memoryContentsQueryService.getCapturedYearMonths().awaitSingle().also {
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
