package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.service.query.CommentDto
import com.kasakaid.omoidememory.service.query.MemoryFeedDto
import com.kasakaid.omoidememory.service.query.MemoryQueryService
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux

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
        val cursorUuid = cursor?.let { java.util.UUID.fromString(it) }
        return memoryQueryService.getFeed(cursorUuid, limit)
    }

    @GetMapping("/content/{id}/comments")
    suspend fun getComments(
        @PathVariable id: java.util.UUID,
    ): Flux<CommentDto> = memoryQueryService.getComments(id)
}
