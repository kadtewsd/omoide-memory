package com.kasakaid.omoidememory.service.query

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.file.Files
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*

@Service
class MemoryQueryService(
    private val dslContext: DSLContext,
) {
    fun getFeed(
        cursor: UUID?,
        limit: Int,
    ): Flux<MemoryFeedDto> {
        val conditions: MutableList<org.jooq.Condition> = mutableListOf()
        if (cursor != null) {
            conditions.add(COMMENT_OMOIDE.FEED_ID.lt(cursor))
        }

        return Flux
            .from(
                dslContext
                    .select(
                        COMMENT_OMOIDE.FEED_ID,
                        COMMENT_OMOIDE.MEDIA_TYPE,
                        DSL.coalesce(SYNCED_OMOIDE_PHOTO.SERVER_PATH, SYNCED_OMOIDE_VIDEO.SERVER_PATH).`as`("SERVER_PATH"),
                        DSL.min(COMMENT_OMOIDE.COMMENTED_AT).`as`(COMMENT_OMOIDE.COMMENTED_AT),
                        SYNCED_OMOIDE_VIDEO.THUMBNAIL_IMAGE,
                        SYNCED_OMOIDE_VIDEO.THUMBNAIL_MIME_TYPE,
                        COMMENT_OMOIDE.FILE_NAME,
                    ).from(COMMENT_OMOIDE)
                    .leftJoin(SYNCED_OMOIDE_PHOTO)
                    .on(COMMENT_OMOIDE.FILE_NAME.eq(SYNCED_OMOIDE_PHOTO.FILE_NAME))
                    .leftJoin(SYNCED_OMOIDE_VIDEO)
                    .on(COMMENT_OMOIDE.FILE_NAME.eq(SYNCED_OMOIDE_VIDEO.FILE_NAME))
                    .where(conditions)
                    .groupBy(
                        COMMENT_OMOIDE.FEED_ID,
                        COMMENT_OMOIDE.MEDIA_TYPE,
                        SYNCED_OMOIDE_PHOTO.ID,
                        SYNCED_OMOIDE_PHOTO.SERVER_PATH,
                        SYNCED_OMOIDE_VIDEO.ID,
                        SYNCED_OMOIDE_VIDEO.SERVER_PATH,
                        SYNCED_OMOIDE_VIDEO.THUMBNAIL_IMAGE,
                        SYNCED_OMOIDE_VIDEO.THUMBNAIL_MIME_TYPE,
                        COMMENT_OMOIDE.FILE_NAME,
                    ).orderBy(COMMENT_OMOIDE.FEED_ID.desc())
                    .limit(limit),
            ).concatMap { row: org.jooq.Record ->
                val type = row.get(COMMENT_OMOIDE.MEDIA_TYPE)
                val serverPath = row.get("SERVER_PATH", String::class.java)
                val thumbnailBytes = row.get(SYNCED_OMOIDE_VIDEO.THUMBNAIL_IMAGE)
                val thumbnailMimeType = row.get(SYNCED_OMOIDE_VIDEO.THUMBNAIL_MIME_TYPE) ?: "image/jpeg"

                val contentBase64Mono =
                    if (type == "PHOTO" && serverPath != null) {
                        Mono
                            .fromCallable {
                                val path = Paths.get(serverPath)
                                if (Files.exists(path)) {
                                    val bytes = Files.readAllBytes(path)
                                    val mimeType = Files.probeContentType(path) ?: "image/jpeg"
                                    "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
                                } else {
                                    null
                                }
                            }.subscribeOn(Schedulers.boundedElastic())
                    } else if (type == "VIDEO" && thumbnailBytes != null) {
                        Mono.just("data:$thumbnailMimeType;base64,${Base64.getEncoder().encodeToString(thumbnailBytes)}")
                    } else {
                        Mono.empty<String>()
                    }

                contentBase64Mono.defaultIfEmpty("").map { contentBase64 ->
                    MemoryFeedDto(
                        id = row.getValue(COMMENT_OMOIDE.FEED_ID)!!,
                        type = type,
                        contentBase64 = contentBase64.ifEmpty { null },
                        commentedAt = row.getValue(COMMENT_OMOIDE.COMMENTED_AT)!!,
                        thumbnailBase64 = if (type == "VIDEO") contentBase64.ifEmpty { null } else null,
                        thumbnailMimeType = if (type == "VIDEO") thumbnailMimeType else null,
                    )
                }
            }
    }

    suspend fun getPhotoComments(feedId: UUID): Flux<CommentOmoide> =
        Flux
            .from(
                dslContext
                    .select(COMMENT_OMOIDE.asterisk())
                    .from(COMMENT_OMOIDE)
                    .where(COMMENT_OMOIDE.FEED_ID.eq(feedId))
                    .orderBy(COMMENT_OMOIDE.COMMENTED_AT.asc()),
            ).map { row: org.jooq.Record -> row.into(CommentOmoide::class.java) }

    suspend fun getVideoComments(feedId: UUID): Flux<CommentOmoide> =
        Flux
            .from(
                dslContext
                    .select(COMMENT_OMOIDE.asterisk())
                    .from(COMMENT_OMOIDE)
                    .where(COMMENT_OMOIDE.FEED_ID.eq(feedId))
                    .orderBy(COMMENT_OMOIDE.COMMENTED_AT.asc()),
            ).map { row: org.jooq.Record -> row.into(CommentOmoide::class.java) }
}
