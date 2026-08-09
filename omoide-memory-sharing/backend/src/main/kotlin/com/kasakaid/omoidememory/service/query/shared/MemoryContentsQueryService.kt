package com.kasakaid.omoidememory.service.query.shared

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoideVideo
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Service
class MemoryContentsQueryService(
    private val dslContext: DSLContext,
) {
    fun fetchMemoryData(
        startInclusive: OffsetDateTime?,
        endExclusive: OffsetDateTime?,
    ): Mono<Triple<List<SyncedOmoidePhoto>, List<SyncedOmoideVideo>, List<CommentOmoide>>> {
        val photoConditions = mutableListOf<Condition>()
        if (startInclusive != null && endExclusive != null) {
            photoConditions.add(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.ge(startInclusive).and(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.lt(endExclusive)))
        }

        val videoConditions = mutableListOf<Condition>()
        if (startInclusive != null && endExclusive != null) {
            videoConditions.add(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME.ge(startInclusive).and(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME.lt(endExclusive)))
        }

        val photosMono =
            Flux
                .from(
                    dslContext
                        .selectFrom(SYNCED_OMOIDE_PHOTO)
                        .where(photoConditions),
                ).map { record -> record.into(SyncedOmoidePhoto::class.java) }
                .collectList()

        val videosMono =
            Flux
                .from(
                    dslContext
                        .selectFrom(SYNCED_OMOIDE_VIDEO)
                        .where(videoConditions),
                ).map { record -> record.into(SyncedOmoideVideo::class.java) }
                .collectList()

        val commentsMono =
            Flux
                .from(
                    dslContext
                        .selectFrom(COMMENT_OMOIDE),
                ).map { record -> record.into(CommentOmoide::class.java) }
                .collectList()

        return Mono
            .zip(photosMono, videosMono, commentsMono)
            .map { tuple -> Triple(tuple.t1, tuple.t2, tuple.t3) }
    }

    fun getCapturedYearMonths(): Mono<List<OffsetDateTime>> {
        val photoCaptureTimes =
            Flux
                .from(
                    dslContext
                        .select(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME)
                        .from(SYNCED_OMOIDE_PHOTO)
                        .where(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.isNotNull),
                ).mapNotNull { record -> record.value1() }

        val videoCaptureTimes =
            Flux
                .from(
                    dslContext
                        .select(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME)
                        .from(SYNCED_OMOIDE_VIDEO)
                        .where(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME.isNotNull),
                ).mapNotNull { record -> record.value1() }

        return Flux
            .merge(photoCaptureTimes, videoCaptureTimes)
            .collectList()
            .map { times ->
                times
                    .sortedDescending()
            }
    }
}
