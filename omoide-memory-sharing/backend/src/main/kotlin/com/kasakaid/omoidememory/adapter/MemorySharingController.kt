package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoideVideo
import com.kasakaid.omoidememory.service.query.MemoryFeedDto
import com.kasakaid.omoidememory.service.query.MemoryQueryService
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api")
class MemorySharingController(
    private val memoryQueryService: MemoryQueryService,
) {
    @GetMapping("/feed")
    suspend fun getFeed(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): Flux<MemoryFeedDto> {
        val cursorTime = cursor?.let { OffsetDateTime.parse(it) }
        return memoryQueryService.getFeed(cursorTime, limit)
    }

    @GetMapping("/content/photo/{id}/comments")
    suspend fun getPhotoComments(
        @PathVariable id: java.util.UUID,
    ): Flux<CommentOmoidePhoto> = memoryQueryService.getPhotoComments(id)

    @GetMapping("/content/video/{id}/comments")
    suspend fun getVideoComments(
        @PathVariable id: java.util.UUID,
    ): Flux<CommentOmoideVideo> = memoryQueryService.getVideoComments(id)
}
