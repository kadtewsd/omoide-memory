package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.infrastructure.query.MemoryQueryService
import com.kasakaid.omoidememory.service.query.MemoryFeedDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
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
    ): ResponseEntity<List<MemoryFeedDto>> {
        val cursorTime = cursor?.let { OffsetDateTime.parse(it) }
        return ResponseEntity.ok(memoryQueryService.getFeed(cursorTime, limit))
    }

    @GetMapping("/content/photo/{id}/comments")
    suspend fun getPhotoComments(
        @PathVariable id: java.util.UUID,
    ): ResponseEntity<List<com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.CommentOmoidePhotoRecord>> =
        ResponseEntity.ok(memoryQueryService.getPhotoComments(id))

    @GetMapping("/content/video/{id}/comments")
    suspend fun getVideoComments(
        @PathVariable id: java.util.UUID,
    ): ResponseEntity<List<com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.CommentOmoideVideoRecord>> =
        ResponseEntity.ok(memoryQueryService.getVideoComments(id))
}
