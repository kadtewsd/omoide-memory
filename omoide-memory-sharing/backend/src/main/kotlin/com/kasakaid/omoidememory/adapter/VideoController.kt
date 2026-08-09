package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.shared.adapter.NotFoundException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.DSLContext
import org.springframework.core.io.support.ResourceRegion
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@CrossOrigin
class VideoController(
    private val dslContext: DSLContext,
) {
    @GetMapping("/video/{id}/stream")
    suspend fun streamVideo(
        @PathVariable id: UUID,
    ): ResourceRegion {
        val record =
            dslContext
                .selectFrom(SYNCED_OMOIDE_VIDEO)
                .where(SYNCED_OMOIDE_VIDEO.ID.eq(id))
                .asFlow()
                .toList()
                .firstOrNull()
                ?: throw NotFoundException("Video record not found for id: $id")

        return VideoStreamTranslator.toResourceRegion(record.serverPath)
            ?: throw NotFoundException("Video file not found for path: ${record.serverPath}")
    }
}
