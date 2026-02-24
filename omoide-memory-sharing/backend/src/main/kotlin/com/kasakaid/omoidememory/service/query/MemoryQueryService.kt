package com.kasakaid.omoidememory.service.query

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoideVideo
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.CommentOmoidePhotoRecord
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.CommentOmoideVideoRecord
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE_VIDEO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import org.jooq.DSLContext
import org.jooq.impl.DSL.selectOne
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.time.OffsetDateTime
import java.util.*

@Service
class MemoryQueryService(
    private val dslContext: DSLContext,
) {
    fun getFeed(
        cursor: OffsetDateTime?,
        limit: Int,
    ): Flux<MemoryFeedDto> {
        // 1. Photo のクエリ（Publisher化）
        val photoFlux =
            Flux
                .from(
                    dslContext
                        .selectFrom(SYNCED_OMOIDE_PHOTO)
                        .whereExists(
                            selectOne()
                                .from(COMMENT_OMOIDE_PHOTO)
                                .where(COMMENT_OMOIDE_PHOTO.PHOTO_ID.eq(SYNCED_OMOIDE_PHOTO.ID)),
                        ).let { if (cursor != null) it.and(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.lt(cursor)) else it }
                        .orderBy(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.desc())
                        .limit(limit),
                ).map { row ->
                    MemoryFeedDto(
                        id = row.id,
                        type = "PHOTO",
                        filePath = row.serverPath,
                        captureTime = row.captureTime!!,
                    )
                }

        // 2. Video のクエリ（Publisher化）
        val videoFlux =
            Flux
                .from(
                    dslContext
                        .selectFrom(SYNCED_OMOIDE_VIDEO)
                        .whereExists(
                            selectOne()
                                .from(COMMENT_OMOIDE_VIDEO)
                                .where(COMMENT_OMOIDE_VIDEO.VIDEO_ID.eq(SYNCED_OMOIDE_VIDEO.ID)),
                        ).let { if (cursor != null) it.and(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME.lt(cursor)) else it }
                        .orderBy(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME.desc())
                        .limit(limit),
                ).map { row ->
                    MemoryFeedDto(
                        id = row.id,
                        type = "VIDEO",
                        filePath = row.serverPath,
                        captureTime = row.captureTime!!,
                        thumbnailBase64 = row.thumbnailImage?.let { Base64.getEncoder().encodeToString(it) },
                        thumbnailMimeType = row.thumbnailMimeType,
                    )
                }

        // 3. 両方をマージしてストリーム上でソート
        return Flux
            .merge(photoFlux, videoFlux)
            .sort { a, b -> b.captureTime.compareTo(a.captureTime) } // 降順ソート
            .take(limit.toLong()) // 最終的に必要な件数だけ取得
    }

    suspend fun getPhotoComments(photoId: UUID): Flux<CommentOmoidePhoto> =
        dslContext
            .selectFrom(COMMENT_OMOIDE_PHOTO)
            .where(COMMENT_OMOIDE_PHOTO.PHOTO_ID.eq(photoId))
            .orderBy(COMMENT_OMOIDE_PHOTO.COMMENTED_AT.asc())
            .let {
                Flux.from(it).map {
                    it.into(CommentOmoidePhoto::class.java)
                }
            }

    suspend fun getVideoComments(videoId: UUID): Flux<CommentOmoideVideo> =
        dslContext
            .selectFrom(COMMENT_OMOIDE_VIDEO)
            .where(COMMENT_OMOIDE_VIDEO.VIDEO_ID.eq(videoId))
            .orderBy(COMMENT_OMOIDE_VIDEO.COMMENTED_AT.asc())
            .let {
                Flux.from(it).map {
                    it.into(CommentOmoideVideo::class.java)
                }
            }
}
