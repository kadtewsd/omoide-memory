package com.kasakaid.sharing.infrastructure.query

import arrow.core.Either
import arrow.core.either
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.CommentOmoidePhoto.COMMENT_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.CommentOmoideVideo.COMMENT_OMOIDE_VIDEO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.Commenter.COMMENTER
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.SyncedOmoidePhoto.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.SyncedOmoideVideo.SYNCED_OMOIDE_VIDEO
import com.kasakaid.sharing.query.MemoryQueryService
import com.kasakaid.sharing.query.dto.CommentDto
import com.kasakaid.sharing.query.dto.MemoryFeedDto
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.OffsetDateTime
import java.util.Base64

@Repository
class MemoryQueryServiceImpl(
    private val dslContext: DSLContext,
) : MemoryQueryService {
    override suspend fun getFeed(
        cursor: OffsetDateTime?,
        limit: Int,
    ): Either<Throwable, List<MemoryFeedDto>> =
        either {
            val photoQuery =
                dslContext
                    .selectDistinct(
                        SYNCED_OMOIDE_PHOTO.ID,
                        SYNCED_OMOIDE_PHOTO.SERVER_PATH,
                        SYNCED_OMOIDE_PHOTO.CAPTURE_TIME,
                    ).from(SYNCED_OMOIDE_PHOTO)
                    .join(COMMENT_OMOIDE_PHOTO)
                    .on(SYNCED_OMOIDE_PHOTO.ID.eq(COMMENT_OMOIDE_PHOTO.PHOTO_ID))
                    .let {
                        if (cursor != null) {
                            it.where(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.lt(cursor))
                        } else {
                            it
                        }
                    }.orderBy(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.desc())
                    .limit(limit)

            val photos =
                Flux.from(photoQuery).asFlow().toList().map { row ->
                    MemoryFeedDto.Photo(
                        id = row[SYNCED_OMOIDE_PHOTO.ID] ?: 0L,
                        filePath = row[SYNCED_OMOIDE_PHOTO.SERVER_PATH] ?: "",
                        captureTime = row[SYNCED_OMOIDE_PHOTO.CAPTURE_TIME],
                    )
                }

            val videoQuery =
                dslContext
                    .selectDistinct(
                        SYNCED_OMOIDE_VIDEO.ID,
                        SYNCED_OMOIDE_VIDEO.SERVER_PATH,
                        SYNCED_OMOIDE_VIDEO.CAPTURE_TIME,
                        SYNCED_OMOIDE_VIDEO.THUMBNAIL_IMAGE,
                        SYNCED_OMOIDE_VIDEO.THUMBNAIL_MIME_TYPE,
                    ).from(SYNCED_OMOIDE_VIDEO)
                    .join(COMMENT_OMOIDE_VIDEO)
                    .on(SYNCED_OMOIDE_VIDEO.ID.eq(COMMENT_OMOIDE_VIDEO.VIDEO_ID))
                    .let {
                        if (cursor != null) {
                            it.where(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME.lt(cursor))
                        } else {
                            it
                        }
                    }.orderBy(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME.desc())
                    .limit(limit)

            val videos =
                Flux.from(videoQuery).asFlow().toList().map { row ->
                    MemoryFeedDto.Video(
                        id = row[SYNCED_OMOIDE_VIDEO.ID] ?: 0L,
                        filePath = row[SYNCED_OMOIDE_VIDEO.SERVER_PATH] ?: "",
                        captureTime = row[SYNCED_OMOIDE_VIDEO.CAPTURE_TIME],
                        thumbnailBase64 = row[SYNCED_OMOIDE_VIDEO.THUMBNAIL_IMAGE]?.let { Base64.getEncoder().encodeToString(it) },
                        thumbnailMimeType = row[SYNCED_OMOIDE_VIDEO.THUMBNAIL_MIME_TYPE],
                    )
                }

            (photos + videos)
                .sortedByDescending { it.captureTime }
                .take(limit)
        }

    override suspend fun getPhotoComments(photoId: Long): Either<Throwable, List<CommentDto>> =
        either {
            val query =
                dslContext
                    .select(
                        COMMENT_OMOIDE_PHOTO.ID,
                        COMMENTER.NAME,
                        COMMENTER.ICON,
                        COMMENT_OMOIDE_PHOTO.COMMENT_BODY,
                        COMMENT_OMOIDE_PHOTO.COMMENTED_AT,
                    ).from(COMMENT_OMOIDE_PHOTO)
                    .leftJoin(COMMENTER)
                    .on(COMMENT_OMOIDE_PHOTO.COMMENTER_ID.eq(COMMENTER.ID))
                    .where(COMMENT_OMOIDE_PHOTO.PHOTO_ID.eq(photoId))
                    .orderBy(COMMENT_OMOIDE_PHOTO.COMMENTED_AT.asc())

            Flux.from(query).asFlow().toList().map { row ->
                CommentDto(
                    id = row[COMMENT_OMOIDE_PHOTO.ID] ?: 0L,
                    commenterName = row[COMMENTER.NAME] ?: "Unknown",
                    commenterIconBase64 = row[COMMENTER.ICON],
                    commentBody = row[COMMENT_OMOIDE_PHOTO.COMMENT_BODY] ?: "",
                    commentedAt = row[COMMENT_OMOIDE_PHOTO.COMMENTED_AT] ?: OffsetDateTime.now(),
                )
            }
        }

    override suspend fun getVideoComments(videoId: Long): Either<Throwable, List<CommentDto>> =
        either {
            val query =
                dslContext
                    .select(
                        COMMENT_OMOIDE_VIDEO.ID,
                        COMMENTER.NAME,
                        COMMENTER.ICON,
                        COMMENT_OMOIDE_VIDEO.COMMENT_BODY,
                        COMMENT_OMOIDE_VIDEO.COMMENTED_AT,
                    ).from(COMMENT_OMOIDE_VIDEO)
                    .leftJoin(COMMENTER)
                    .on(COMMENT_OMOIDE_VIDEO.COMMENTER_ID.eq(COMMENTER.ID))
                    .where(COMMENT_OMOIDE_VIDEO.VIDEO_ID.eq(videoId))
                    .orderBy(COMMENT_OMOIDE_VIDEO.COMMENTED_AT.asc())

            Flux.from(query).asFlow().toList().map { row ->
                CommentDto(
                    id = row[COMMENT_OMOIDE_VIDEO.ID] ?: 0L,
                    commenterName = row[COMMENTER.NAME] ?: "Unknown",
                    commenterIconBase64 = row[COMMENTER.ICON],
                    commentBody = row[COMMENT_OMOIDE_VIDEO.COMMENT_BODY] ?: "",
                    commentedAt = row[COMMENT_OMOIDE_VIDEO.COMMENTED_AT] ?: OffsetDateTime.now(),
                )
            }
        }
}
