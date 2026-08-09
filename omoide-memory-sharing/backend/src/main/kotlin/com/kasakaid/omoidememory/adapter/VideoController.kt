package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import org.jooq.DSLContext
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@CrossOrigin
class VideoController(
    private val dslContext: DSLContext,
) {
    @GetMapping("/video/{id}/stream")
    fun streamVideo(
        @PathVariable id: UUID,
    ): Mono<ResponseEntity<ResourceRegion>> =
        Flux
            .from(
                dslContext
                    .selectFrom(SYNCED_OMOIDE_VIDEO)
                    .where(SYNCED_OMOIDE_VIDEO.ID.eq(id)),
            ).next()
            .flatMap { record ->
                VideoStreamTranslator.toResponseEntity(record.serverPath)
            }.defaultIfEmpty(ResponseEntity.notFound().build())
}
