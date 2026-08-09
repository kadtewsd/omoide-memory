package com.kasakaid.omoidememory.adapter

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourceRegion
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
     * 指定されたサーバー上のファイルパスから動画ストリームの [ResourceRegion] を生成します。
     *
     * @param serverPath 動画ファイルの絶対パス（null や 空文字、存在しないファイルの場合は null を返却）
     * @return [ResourceRegion]
     */
    fun toResourceRegion(serverPath: String?): ResourceRegion? {
        if (serverPath.isNullOrBlank()) {
            return null
        }

        val file = File(serverPath)
        if (!file.exists()) {
            return null
        }

        val resource: Resource = FileSystemResource(file)
        return ResourceRegion(resource, 0, minOf(CHUNK_SIZE_1MB, file.length()))
    }
}
