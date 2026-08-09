package com.kasakaid.omoidememory.adapter

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono
import java.io.File

/**
 * 動画ファイルを WebFlux のストリーミング用レスポンス（[ResourceRegion]）へ変換する Translator。
 */
object VideoStreamTranslator {
    /**
     * 1回のレスポンスでクライアントへ返す動画ストリームの最大チャンクサイズ（1 MB = 1024 * 1024 バイト）。
     * 動画全体を一括でメモリに読み込まず、1MB ごとの部分領域（ResourceRegion）に分割して配信することで
     * メモリ消費を抑え、スムーズな初期再生開始を実現します。
     */
    private const val CHUNK_SIZE_1MB: Long = 1024 * 1024L

    /**
     * 指定されたサーバー上のファイルパスから動画ストリームの [ResponseEntity] を生成します。
     *
     * @param serverPath 動画ファイルの絶対パス（null や 空文字、存在しないファイルの場合は 404 Not Found を返却）
     * @return [ResourceRegion] をボディに持つ非同期レスポンス [Mono]
     */
    fun toResponseEntity(serverPath: String?): Mono<ResponseEntity<ResourceRegion>> {
        if (serverPath.isNullOrBlank()) {
            return Mono.just(ResponseEntity.notFound().build())
        }

        val file = File(serverPath)
        if (!file.exists()) {
            return Mono.just(ResponseEntity.notFound().build())
        }

        val resource: Resource = FileSystemResource(file)
        val response =
            ResponseEntity
                .status(HttpStatus.OK)
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.parseMediaType("video/mp4")))
                // 1MB Chunk またはファイル全体の小さい方を初回配信範囲として読み込む
                .body(ResourceRegion(resource, 0, minOf(CHUNK_SIZE_1MB, file.length())))

        return Mono.just(response)
    }
}
