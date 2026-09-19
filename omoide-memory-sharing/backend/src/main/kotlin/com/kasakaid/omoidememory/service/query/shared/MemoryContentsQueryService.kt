package com.kasakaid.omoidememory.service.query.shared

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoidePhoto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoideVideo
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.service.query.FeedCursor
import com.kasakaid.omoidememory.service.query.FeedPageResponse
import com.kasakaid.omoidememory.service.query.FilterMode
import com.kasakaid.omoidememory.service.query.MemoryFeedDto
import com.kasakaid.omoidememory.service.query.OmoideCondition
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class MemoryContentsQueryService(
    private val dslContext: DSLContext,
) {
    suspend fun fetchFeedPage(
        condition: OmoideCondition,
        limit: Int,
    ): FeedPageResponse {
        val photoMemory = SyncedOmoideMemoryPhoto()
        val videoMemory = SyncedOmoideMemoryVideo()

        val photoQuery = dslContext.createUnionQuery(omoideMemory = photoMemory, condition = condition)
        val videoQuery = dslContext.createUnionQuery(omoideMemory = videoMemory, condition = condition)

        val unionSelect = photoQuery.unionAll(videoQuery)
        val captureTimeField = DSL.field(DSL.name(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.name), OffsetDateTime::class.java)
        val idField = DSL.field(DSL.name(SYNCED_OMOIDE_PHOTO.ID.name), UUID::class.java)

        val rawRecords =
            dslContext
                .selectFrom(unionSelect.asTable("feed_union"))
                .orderBy(captureTimeField.desc(), idField.desc())
                .limit(limit)
                .asFlow()
                .toList()

        val fileNames = rawRecords.mapNotNull { it.get(SYNCED_OMOIDE_PHOTO.FILE_NAME) }.distinct()
        val commentCounts =
            if (fileNames.isNotEmpty()) {
                dslContext
                    .select(COMMENT_OMOIDE.FILE_NAME, DSL.count())
                    .from(COMMENT_OMOIDE)
                    .where(COMMENT_OMOIDE.FILE_NAME.`in`(fileNames))
                    .groupBy(COMMENT_OMOIDE.FILE_NAME)
                    .asFlow()
                    .toList()
                    .associate { (it.value1() ?: "") to (it.value2() ?: 0) }
            } else {
                emptyMap()
            }

        return OmoideUnionRecordList(
            records = rawRecords,
            commentCounts = commentCounts,
        ).toFeedPageResponse(limit = limit)
    }

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
            .selectFrom(SYNCED_OMOIDE_PHOTO)
            .where(condition)
            .asFlow()
            .map { record -> record.into(SyncedOmoidePhoto::class.java) }
            .toList()

    suspend fun fetchVideo(condition: Condition): List<SyncedOmoideVideo> =
        dslContext
            .selectFrom(SYNCED_OMOIDE_VIDEO)
            .where(condition)
            .asFlow()
            .map { record -> record.into(SyncedOmoideVideo::class.java) }
            .toList()

    suspend fun fetchComment(condition: Condition): List<CommentOmoide> =
        dslContext
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
                .selectDistinct(photoYearMonthField)
                .from(SYNCED_OMOIDE_PHOTO)
                .where(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME.isNotNull)
                .asFlow()
                .mapNotNull { record -> record.value1() }
                .toList()

        val videoYearMonths =
            dslContext
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

fun DSLContext.createUnionQuery(
    omoideMemory: OmoideMemory,
    condition: OmoideCondition,
): SelectConditionStep<Record> {
    val dateCondition =
        when {
            condition.startInclusive != null && condition.endExclusive != null -> {
                omoideMemory.captureTime
                    .ge(condition.startInclusive)
                    .and(omoideMemory.captureTime.lt(condition.endExclusive))
            }

            else -> {
                DSL.noCondition()
            }
        }

    val cursorCondition =
        condition.cursor?.let { c ->
            omoideMemory.captureTime
                .lt(c.captureTime)
                .or(
                    omoideMemory.captureTime
                        .eq(c.captureTime)
                        .and(omoideMemory.id.lt(c.id)),
                )
        } ?: DSL.noCondition()

    val commentCondition =
        when (condition.filterMode) {
            FilterMode.COMMENT_ONLY -> {
                DSL.exists(
                    selectOne()
                        .from(COMMENT_OMOIDE)
                        .where(COMMENT_OMOIDE.FILE_NAME.eq(omoideMemory.fileName)),
                )
            }

            FilterMode.ALL -> {
                DSL.noCondition()
            }
        }

    return select(
        listOf(
            omoideMemory.id,
            omoideMemory.type,
            omoideMemory.fileName,
            omoideMemory.captureTime,
        ),
    ).from(omoideMemory.table)
        .where(
            dateCondition
                .and(cursorCondition)
                .and(commentCondition),
        )
}

/**
 * 写真と動画を `UNION ALL` で一括取得した生レコード（[Record]）群を保持し、
 * ドメイン DTO（[MemoryFeedDto]）へのマッピングおよび Keyset ページネーションの
 * 次ページ判定・カーソル生成を担当するファーストクラスコレクション。
 *
 * @property omoideMemories 生レコードから変換された [MemoryFeedDto] の一覧
 */
class OmoideUnionRecordList(
    records: List<Record>,
    commentCounts: Map<String, Int>,
) {
    val omoideMemories: List<MemoryFeedDto> =
        records.map { record ->
            MemoryFeedDto(
                id = record.get(SYNCED_OMOIDE_PHOTO.ID),
                type = record.get("type", String::class.java),
                commentedAt = record.get(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME) ?: OffsetDateTime.now(),
                captureTime = record.get(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME),
                commentCount = commentCounts[record.get(SYNCED_OMOIDE_PHOTO.FILE_NAME) ?: ""] ?: 0,
            )
        }

    /**
     * 指定された取得上限（[limit] = 要求ページサイズ + 先読み 1 件）に基づき、
     * 次ページの有無（[FeedPageResponse.hasNext]）および次ページ牽引用のカーソル（[FeedPageResponse.nextCursor]）
     * を算出して [FeedPageResponse] を生成します。
     *
     * 取得件数が [limit] 件に達している場合、次ページが存在すると判定し、
     * クライアントへ返却するアイテム一覧からは先読み用の末尾 1 件を除外（[List.dropLast]）します。
     *
     * @param limit DB クエリ時に指定した取得件数（pageSize + 1）
     * @return ページネーションメタデータを含むフィードレスポンス
     */
    fun toFeedPageResponse(limit: Int): FeedPageResponse {
        val hasNext = omoideMemories.size == limit
        val pageItems = if (hasNext) omoideMemories.dropLast(1) else omoideMemories
        return FeedPageResponse(
            items = pageItems,
            nextCursor = calculateNextCursor(hasNext = hasNext, pageItems = pageItems),
            hasNext = hasNext,
        )
    }

    /**
     * 次回リクエストで指定すべき Keyset カーソル（[FeedCursor]）を算出します。
     *
     * 【Keyset ページネーションにおけるカーソル算出の設計意図】:
     * 次回リクエストでは `(capture_time, id) < (cursor_capture_time, cursor_id)`（厳密に小さい）
     * という条件で次ページを検索するため、カーソルには「クライアントへ返却したページの最後のアイテム」
     * の [FeedCursor.captureTime] と [FeedCursor.id] を採用します。
     * これにより、先読みした N+1 件目のアイテムが次ページの先頭 1 件目としてスキップ（欠落）することなく
     * 正確に取得され、データの重複も発生しません。
     *
     * 次ページが存在しない場合（[hasNext] が false）、または返却対象アイテムが空の場合は null を早期リターンします。
     *
     * @param hasNext 次ページが存在するかどうか
     * @param pageItems クライアントへ返却する当ページのアイテム一覧
     * @return 次ページ取得用のカーソル。次ページがない場合は null
     */
    private fun calculateNextCursor(
        hasNext: Boolean,
        pageItems: List<MemoryFeedDto>,
    ): FeedCursor? {
        if (!hasNext) return null
        if (pageItems.isEmpty()) return null
        val lastItem = pageItems.last()
        val captureTime = lastItem.captureTime ?: return null
        val id = lastItem.id ?: return null
        return FeedCursor(captureTime = captureTime, id = id)
    }
}
