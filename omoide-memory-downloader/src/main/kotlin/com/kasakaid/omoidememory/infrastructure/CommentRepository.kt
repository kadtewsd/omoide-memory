package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE_VIDEO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class CommentRepository(
    private val dslContext: DSLContext,
) {
    suspend fun findPhotoIdByFileName(fileName: String): UUID? =
        dslContext
            .selectFrom(SYNCED_OMOIDE_PHOTO)
            .where(SYNCED_OMOIDE_PHOTO.FILE_NAME.eq(fileName))
            .awaitFirstOrNull()
            ?.id

    suspend fun findVideoIdByFileName(fileName: String): UUID? =
        dslContext
            .selectFrom(SYNCED_OMOIDE_VIDEO)
            .where(SYNCED_OMOIDE_VIDEO.FILE_NAME.eq(fileName))
            .awaitFirstOrNull()
            ?.id

    suspend fun getNextPhotoCommentSeq(photoId: UUID): Int {
        val maxSeqRec =
            dslContext
                .selectFrom(COMMENT_OMOIDE_PHOTO)
                .where(COMMENT_OMOIDE_PHOTO.PHOTO_ID.eq(photoId))
                .orderBy(COMMENT_OMOIDE_PHOTO.COMMENT_SEQ.desc())
                .limit(1)
                .awaitFirstOrNull()
        return (maxSeqRec?.commentSeq ?: 0) + 1
    }

    suspend fun getNextVideoCommentSeq(videoId: UUID): Int {
        val maxSeqRec =
            dslContext
                .selectFrom(COMMENT_OMOIDE_VIDEO)
                .where(COMMENT_OMOIDE_VIDEO.VIDEO_ID.eq(videoId))
                .orderBy(COMMENT_OMOIDE_VIDEO.COMMENT_SEQ.desc())
                .limit(1)
                .awaitFirstOrNull()
        return (maxSeqRec?.commentSeq ?: 0) + 1
    }

    suspend fun insertPhotoComment(
        photoId: UUID,
        commenterId: Long?,
        commentSeq: Int,
        commentBody: String,
        createdBy: String = "comment-import",
    ) {
        dslContext
            .insertInto(COMMENT_OMOIDE_PHOTO)
            .set(COMMENT_OMOIDE_PHOTO.ID, MyUUIDGenerator.generateUUIDv7())
            .set(COMMENT_OMOIDE_PHOTO.PHOTO_ID, photoId)
            .set(COMMENT_OMOIDE_PHOTO.COMMENTER_ID, commenterId)
            .set(COMMENT_OMOIDE_PHOTO.COMMENT_SEQ, commentSeq)
            .set(COMMENT_OMOIDE_PHOTO.COMMENT_BODY, commentBody)
            .set(COMMENT_OMOIDE_PHOTO.COMMENTED_AT, OffsetDateTime.now())
            .set(COMMENT_OMOIDE_PHOTO.CREATED_AT, OffsetDateTime.now())
            .set(COMMENT_OMOIDE_PHOTO.CREATED_BY, createdBy)
            .awaitSingle()
    }

    suspend fun insertVideoComment(
        videoId: UUID,
        commenterId: Long?,
        commentSeq: Int,
        commentBody: String,
        createdBy: String = "comment-import",
    ) {
        dslContext
            .insertInto(COMMENT_OMOIDE_VIDEO)
            .set(COMMENT_OMOIDE_VIDEO.ID, MyUUIDGenerator.generateUUIDv7())
            .set(COMMENT_OMOIDE_VIDEO.VIDEO_ID, videoId)
            .set(COMMENT_OMOIDE_VIDEO.COMMENTER_ID, commenterId)
            .set(COMMENT_OMOIDE_VIDEO.COMMENT_SEQ, commentSeq)
            .set(COMMENT_OMOIDE_VIDEO.COMMENT_BODY, commentBody)
            .set(COMMENT_OMOIDE_VIDEO.COMMENTED_AT, OffsetDateTime.now())
            .set(COMMENT_OMOIDE_VIDEO.CREATED_AT, OffsetDateTime.now())
            .set(COMMENT_OMOIDE_VIDEO.CREATED_BY, createdBy)
            .awaitSingle()
    }
}
