package com.kasakaid.omoidememory.adapter

import com.kasakaid.omoidememory.service.query.MemoryFeedDto
import com.kasakaid.omoidememory.service.query.photobook.PhotoRandomFillQueryService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

/**
 * フォトブック用写真選択 API コントローラー。
 *
 * GET /photos/random-fill エンドポイントを提供し、
 * 指定期間内の未選択写真をランダムに N 件返す。
 * フロントエンドの自動補完（200枚まで補充）および差し替え機能から呼び出される。
 */
@RestController
@CrossOrigin
class PhotoSelectionController(
    private val photoRandomFillQueryService: PhotoRandomFillQueryService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 指定期間の未選択写真をランダムに [count] 件返す。
     *
     * @param startInclusive 対象月の開始日時（ISO 8601、JST 00:00 相当）
     * @param endExclusive 対象月の翌月開始日時（ISO 8601）
     * @param excludeIds すでに選択済みの写真 ID（カンマ区切り UUID、省略可）
     * @param count 取得したい件数（= 200 − 選択済み件数）
     */
    @GetMapping("/photos/random-fill")
    suspend fun getRandomFillPhotos(
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startInclusive: OffsetDateTime,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        endExclusive: OffsetDateTime,
        @RequestParam(required = false) excludeIds: List<UUID>?,
        @RequestParam count: Int,
    ): List<MemoryFeedDto> {
        logger.info {
            "GET /photos/random-fill: startInclusive=$startInclusive, endExclusive=$endExclusive, excludeIds.size=${excludeIds?.size ?: 0}, count=$count"
        }

        return photoRandomFillQueryService
            .fetchRandomFill(
                startInclusive = startInclusive,
                endExclusive = endExclusive,
                excludeIds = excludeIds ?: emptyList(),
                count = count,
            ).also { result ->
                logger.info { "GET /photos/random-fill: 返却件数=${result.size}" }
            }
    }
}
