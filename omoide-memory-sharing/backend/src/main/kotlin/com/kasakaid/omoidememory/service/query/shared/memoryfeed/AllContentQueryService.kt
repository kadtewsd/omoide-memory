package com.kasakaid.omoidememory.service.query.shared.memoryfeed

import com.kasakaid.omoidememory.domain.model.CommentCount
import com.kasakaid.omoidememory.domain.model.ContentName
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID
import com.kasakaid.omoidememory.service.query.shared.SyncedOmoideMemoryPhoto as photoMemory
import com.kasakaid.omoidememory.service.query.shared.SyncedOmoideMemoryVideo as videoMemory

@Service
class AllContentQueryService(
    private val dslContext: DSLGenerator,
    private val commentCountQuery: CommentCountQuery,
) {
    suspend fun fetchPage(
        condition: OmoideCondition,
        limit: Int,
    ): Pair<Map<ContentName, CommentCount>, List<Record>> {
        val photoQuery = dslContext.createMemoryQuery(omoideMemory = photoMemory, condition = condition)
        val videoQuery = dslContext.createMemoryQuery(omoideMemory = videoMemory, condition = condition)
        val unionSelect = photoQuery.unionAll(videoQuery)
        val captureTimeField = DSL.field(DSL.name(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.name), OffsetDateTime::class.java)
        val idField = DSL.field(DSL.name(SYNCED_OMOIDE_PHOTO.ID.name), UUID::class.java)

        val rawRecords: List<Record> =
            dslContext
                .invoke()
                .selectFrom(unionSelect.asTable("feed_union"))
                .orderBy(captureTimeField.desc(), idField.desc())
                .limit(limit)
                .asFlow()
                .toList()

        return commentCountQuery.fetch(
            rawRecords.mapNotNull { it.get(SYNCED_OMOIDE_PHOTO.FILE_NAME) }.distinct().toSet(),
        ) to rawRecords
    }
}
