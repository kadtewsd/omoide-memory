package com.kasakaid.sharing.infrastructure.query

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.CommentOmoidePhotoRecord
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.CommentOmoideVideoRecord
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE_VIDEO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.r2dbc.R2DBCDSLContext
import com.kasakaid.sharing.service.query.MemoryFeedDto
import org.jooq.impl.DSL.exists
import org.jooq.impl.DSL.selectOne
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.*

@Service
class MemoryQueryService(
    private val dslContext: R2DBCDSLContext,
) {
    suspend fun getFeed(
        cursor: OffsetDateTime?,
        limit: Int,
    ): List<MemoryFeedDto> {
        val photoQuery =
            dslContext
                .get()
                .selectFrom(SYNCED_OMOIDE_PHOTO)
                .whereExists(
                    selectOne()
                        .from(COMMENT_OMOIDE_PHOTO)
                        .where(COMMENT_OMOIDE_PHOTO.PHOTO_ID.eq(SYNCED_OMOIDE_PHOTO.ID)),
                ).let {
                    if (cursor != null) {
                        it.and(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.lt(cursor))
                    } else {
                        it
                    }
                }.orderBy(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.desc())
                .limit(limit)

        val photos =
            photoQuery.toList().map { row ->
                MemoryFeedDto(
                    id = row.id!!,
                    type = "PHOTO",
                    filePath = row.serverPath ?: "",
                    captureTime = row.captureTime,
                )
            }

        val videoQuery =
            dslContext
                .get()
                .selectFrom(SYNCED_OMOIDE_VIDEO)
                .where(
                    exists(
                        selectOne()
                            .from(COMMENT_OMOIDE_VIDEO)
                            .where(COMMENT_OMOIDE_VIDEO.VIDEO_ID.eq(SYNCED_OMOIDE_VIDEO.ID)),
                    ),
                ).let {
                    if (cursor != null) {
                        it.and(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME.lt(cursor))
                    } else {
                        it
                    }
                }.orderBy(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME.desc())
                .limit(limit)

        val videos =
            videoQuery.toList().map { row ->
                MemoryFeedDto(
                    id = row.id!!,
                    type = "VIDEO",
                    filePath = row.serverPath,
                    captureTime = row.captureTime,
                    thumbnailBase64 = row.thumbnailImage?.let { Base64.getEncoder().encodeToString(it) },
                    thumbnailMimeType = row.thumbnailMimeType,
                )
            }

        return (photos + videos)
            .sortedByDescending { it.captureTime }
            .take(limit)
    }

    suspend fun getPhotoComments(photoId: java.util.UUID): List<CommentOmoidePhotoRecord> =
        dslContext
            .get()
            .selectFrom(COMMENT_OMOIDE_PHOTO)
            .where(COMMENT_OMOIDE_PHOTO.PHOTO_ID.eq(photoId))
            .orderBy(COMMENT_OMOIDE_PHOTO.COMMENTED_AT.asc())
            .toList()

    suspend fun getVideoComments(videoId: java.util.UUID): List<CommentOmoideVideoRecord> =
        dslContext
            .get()
            .selectFrom(COMMENT_OMOIDE_VIDEO)
            .where(COMMENT_OMOIDE_VIDEO.VIDEO_ID.eq(videoId))
            .orderBy(COMMENT_OMOIDE_VIDEO.COMMENTED_AT.asc())
            .toList()
}
