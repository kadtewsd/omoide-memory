package com.kasakaid.omoidememory.service.query.shared.memoryfeed

import com.kasakaid.omoidememory.domain.model.CommentCount
import com.kasakaid.omoidememory.domain.model.ContentName
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Service
import com.kasakaid.omoidememory.service.query.shared.SyncedOmoideMemoryPhoto as photoMemory

@Service
class PhotoFeedQueryService(
    private val dslContext: DSLContext,
    private val commentCountQuery: CommentCountQuery,
) {
    suspend fun fetchPage(
        condition: OmoideCondition,
        limit: Int,
    ): Pair<Map<ContentName, CommentCount>, List<Record>> {
        val rawRecords: List<Record> = dslContext.executeWithContentOrder(omoideMemoryTable = photoMemory, condition = condition, limit = limit)
        return commentCountQuery.fetch(
            rawRecords.mapNotNull { it.get(SYNCED_OMOIDE_PHOTO.FILE_NAME) }.distinct().toSet(),
        ) to rawRecords
    }
}
