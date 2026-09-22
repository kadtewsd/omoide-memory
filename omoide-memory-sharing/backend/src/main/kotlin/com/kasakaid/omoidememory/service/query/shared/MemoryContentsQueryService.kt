package com.kasakaid.omoidememory.service.query.shared

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoideVideo
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.Condition
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class MemoryContentsQueryService(
    private val dslContext: DSLGenerator,
) {
    suspend fun fetchOmoideMemory(
        photoCondition: Condition,
        videoCondition: Condition,
        commentCondition: Condition,
    ): Triple<List<SyncedOmoidePhoto>, List<SyncedOmoideVideo>, List<CommentOmoide>> =
        coroutineScope {
            val photosDeferred = async { fetchPhoto(photoCondition) }
            val videosDeferred = async { fetchVideo(videoCondition) }
            val commentsDeferred = async { fetchComment(commentCondition) }

            Triple(photosDeferred.await(), videosDeferred.await(), commentsDeferred.await())
        }

    suspend fun fetchPhoto(condition: Condition): List<SyncedOmoidePhoto> =
        dslContext
            .invoke()
            .selectFrom(SYNCED_OMOIDE_PHOTO)
            .where(condition)
            .asFlow()
            .map { record -> record.into(SyncedOmoidePhoto::class.java) }
            .toList()

    suspend fun fetchVideo(condition: Condition): List<SyncedOmoideVideo> =
        dslContext
            .invoke()
            .selectFrom(SYNCED_OMOIDE_VIDEO)
            .where(condition)
            .asFlow()
            .map { record -> record.into(SyncedOmoideVideo::class.java) }
            .toList()

    suspend fun fetchComment(condition: Condition): List<CommentOmoide> =
        dslContext
            .invoke()
            .selectFrom(COMMENT_OMOIDE)
            .where(condition)
            .asFlow()
            .map { record -> record.into(CommentOmoide::class.java) }
            .toList()

    suspend fun getCapturedYearMonths(): List<OffsetDateTime> {
        val photoYearMonthField = DSL.trunc(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME, org.jooq.DatePart.MONTH)
        val videoYearMonthField = DSL.trunc(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME, org.jooq.DatePart.MONTH)

        val photoYearMonths =
            dslContext
                .invoke()
                .selectDistinct(photoYearMonthField)
                .from(SYNCED_OMOIDE_PHOTO)
                .where(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.isNotNull)
                .asFlow()
                .mapNotNull { record -> record.value1() }
                .toList()

        val videoYearMonths =
            dslContext
                .invoke()
                .selectDistinct(videoYearMonthField)
                .from(SYNCED_OMOIDE_VIDEO)
                .where(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME.isNotNull)
                .asFlow()
                .mapNotNull { record -> record.value1() }
                .toList()

        return (photoYearMonths + videoYearMonths)
            .distinct()
            .sortedDescending()
    }
}
