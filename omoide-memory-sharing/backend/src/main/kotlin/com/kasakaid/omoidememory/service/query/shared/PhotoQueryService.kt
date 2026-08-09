package com.kasakaid.omoidememory.service.query.shared

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.util.UUID

@Service
class PhotoQueryService(
    private val dslContext: DSLContext,
) {
    fun findPhotosByIds(photoIds: List<UUID>): Flux<SyncedOmoidePhoto> =
        Flux
            .from(
                dslContext
                    .selectFrom(SYNCED_OMOIDE_PHOTO)
                    .where(SYNCED_OMOIDE_PHOTO.ID.`in`(photoIds)),
            ).map { record -> record.into(SyncedOmoidePhoto::class.java) }
}
