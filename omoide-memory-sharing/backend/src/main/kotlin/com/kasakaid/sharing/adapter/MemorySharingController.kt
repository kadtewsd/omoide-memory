package com.kasakaid.sharing.adapter

import com.kasakaid.sharing.query.MemoryQueryService
import com.kasakaid.sharing.query.dto.CommentDto
import com.kasakaid.sharing.query.dto.MemoryFeedDto
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
        return memoryQueryService.getFeed(cursorTime, limit).fold(
            ifLeft = { ResponseEntity.internalServerError().build() },
            ifRight = { ResponseEntity.ok(it) },
        )
    }

    @GetMapping("/content/photo/{id}/comments")
    suspend fun getPhotoComments(
        @PathVariable id: Long,
    ): ResponseEntity<List<CommentDto>> =
        memoryQueryService.getPhotoComments(id).fold(
            ifLeft = { ResponseEntity.internalServerError().build() },
            ifRight = { ResponseEntity.ok(it) },
        )

    @GetMapping("/content/video/{id}/comments")
    suspend fun getVideoComments(
        @PathVariable id: Long,
    ): ResponseEntity<List<CommentDto>> =
        memoryQueryService.getVideoComments(id).fold(
            ifLeft = { ResponseEntity.internalServerError().build() },
            ifRight = { ResponseEntity.ok(it) },
        )
}
