package com.kasakaid.omoidememory.service.query.photobook

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.service.query.MemoryFeedDto
import com.kasakaid.omoidememory.service.query.MemoryFeedDtoConverter
import com.kasakaid.omoidememory.service.query.shared.MemoryContentsQueryService
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

/**
 * フォトブック用・写真ランダム補充クエリサービス。
 *
 * 指定期間内の写真を取得し、すでに選択済みの写真（excludeIds）を除外した上で
 * Kotlin 側でシャッフルして指定件数を返す。
 * DB に ORDER BY RANDOM() を使わずアプリケーション側でシャッフルすることで
 * クエリをシンプルに保ちつつ柔軟な除外処理を実現する。
 *
 * CQS の方針に従い、このサービスはデータ取得のみを行う（更新・副作用なし）。
 */
@Service
class PhotoRandomFillQueryService(
    private val memoryContentsQueryService: MemoryContentsQueryService,
    private val memoryFeedDtoConverter: MemoryFeedDtoConverter,
) {
    /**
     * 指定期間内の写真から [excludeIds] を除いたものをランダムに [count] 件返す。
     *
     * @param startInclusive 対象期間の開始日時（JST 00:00 相当の UTC オフセット付き日時）
     * @param endExclusive 対象期間の終了日時（翌月 JST 00:00 相当）
     * @param excludeIds すでに選択済みで除外すべき写真 ID のリスト
     * @param count 補充したい枚数
     * @return ランダムに選ばれた写真の DTO リスト（[count] 件以下）
     */
    suspend fun fetchRandomFill(
        startInclusive: OffsetDateTime,
        endExclusive: OffsetDateTime,
        excludeIds: List<UUID>,
        count: Int,
    ): List<MemoryFeedDto> {
        val photoCondition =
            SYNCED_OMOIDE_PHOTO.run {
                CAPTURE_TIME
                    .ge(startInclusive)
                    .and(CAPTURE_TIME.lt(endExclusive))
                    .and(
                        if (excludeIds.isEmpty()) {
                            DSL.noCondition()
                        } else {
                            ID.notIn(excludeIds)
                        },
                    )
            }

        val photos = memoryContentsQueryService.fetchPhoto(condition = photoCondition)

        return photos
            .shuffled()
            .take(count)
            .map { photo ->
                memoryFeedDtoConverter.transformPhotoToDto(
                    photo = photo,
                    comments = emptyList(),
                )
            }
    }
}
