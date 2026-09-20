package com.kasakaid.omoidememory.service.query.shared.memoryfeed

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.SyncedOmoidePhoto.Companion.SYNCED_OMOIDE_PHOTO
import org.jooq.Record
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import kotlin.collections.dropLast

@Service
class OmoideMemoryFeedQueryService(
    private val all: AllContentQueryService,
    private val photo: PhotoFeedQueryService,
    private val video: VideoFeedQueryService,
) {
    suspend fun fetchFeedPage(
        condition: OmoideCondition,
        limit: Int,
    ): FeedPageResponse {
        val (commentMap, rawRecords) =
            when (condition.contentType) {
                ContentType.ALL -> all.fetchPage(condition = condition, limit = limit)
                ContentType.PHOTO -> photo.fetchPage(condition = condition, limit = limit)
                ContentType.VIDEO -> video.fetchPage(condition = condition, limit = limit)
            }

        return OmoideUnionRecordList(
            records = rawRecords,
            commentCounts = commentMap,
        ).toFeedPageResponse(limit = limit)
    }
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
