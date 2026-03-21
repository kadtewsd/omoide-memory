package com.kasakaid.omoidememory.service.query

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE
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
                                .from(COMMENT_OMOIDE)
                                .where(COMMENT_OMOIDE.FILE_NAME.eq(SYNCED_OMOIDE_PHOTO.FILE_NAME)),
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
                                .from(COMMENT_OMOIDE)
                                .where(COMMENT_OMOIDE.FILE_NAME.eq(SYNCED_OMOIDE_VIDEO.FILE_NAME)),
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

    suspend fun getPhotoComments(photoId: UUID): Flux<CommentOmoide> =
        Flux
            .from(
                dslContext
                    .select(COMMENT_OMOIDE.asterisk())
                    .from(COMMENT_OMOIDE)
                    .join(SYNCED_OMOIDE_PHOTO)
                    .on(COMMENT_OMOIDE.FILE_NAME.eq(SYNCED_OMOIDE_PHOTO.FILE_NAME))
                    .where(SYNCED_OMOIDE_PHOTO.ID.eq(photoId))
                    .orderBy(COMMENT_OMOIDE.COMMENTED_AT.asc()),
            ).map { it.into(CommentOmoide::class.java) }

    suspend fun getVideoComments(videoId: UUID): Flux<CommentOmoide> =
        Flux
            .from(
                dslContext
                    .select(COMMENT_OMOIDE.asterisk())
                    .from(COMMENT_OMOIDE)
                    .join(SYNCED_OMOIDE_VIDEO)
                    .on(COMMENT_OMOIDE.FILE_NAME.eq(SYNCED_OMOIDE_VIDEO.FILE_NAME))
                    .where(SYNCED_OMOIDE_VIDEO.ID.eq(videoId))
                    .orderBy(COMMENT_OMOIDE.COMMENTED_AT.asc()),
            ).map { it.into(CommentOmoide::class.java) }
}
