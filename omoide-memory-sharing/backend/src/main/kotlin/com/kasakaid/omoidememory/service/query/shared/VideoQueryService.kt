package com.kasakaid.omoidememory.service.query.shared

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import java.util.UUID

class VideoThumbnail(
    val bytes: ByteArray,
    val mimeType: String,
)

@Service
class VideoQueryService(
    private val dslContext: DSLContext,
) {
    suspend fun findThumbnailById(id: UUID): VideoThumbnail? =
        dslContext
            .select(SYNCED_OMOIDE_VIDEO.THUMBNAIL_IMAGE, SYNCED_OMOIDE_VIDEO.THUMBNAIL_MIME_TYPE)
            .from(SYNCED_OMOIDE_VIDEO)
            .where(SYNCED_OMOIDE_VIDEO.ID.eq(id))
            .asFlow()
            .mapNotNull { record ->
                val imageBytes = record.get(SYNCED_OMOIDE_VIDEO.THUMBNAIL_IMAGE)
                if (imageBytes != null) {
                    VideoThumbnail(
                        bytes = imageBytes,
                        mimeType = record.get(SYNCED_OMOIDE_VIDEO.THUMBNAIL_MIME_TYPE) ?: "image/jpeg",
                    )
                } else {
                    null
                }
            }.toList()
            .firstOrNull()
}
