package com.kasakaid.omoidememory.service.query.shared

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PhotoQueryService(
    private val dslContext: DSLGenerator,
) {
    suspend fun findPhotosByIds(photoIds: List<UUID>): List<SyncedOmoidePhoto> =
        dslContext
            .invoke()
            .selectFrom(SYNCED_OMOIDE_PHOTO)
            .where(SYNCED_OMOIDE_PHOTO.ID.`in`(photoIds))
            .asFlow()
            .map { record -> record.into(SyncedOmoidePhoto::class.java) }
            .toList()
}
