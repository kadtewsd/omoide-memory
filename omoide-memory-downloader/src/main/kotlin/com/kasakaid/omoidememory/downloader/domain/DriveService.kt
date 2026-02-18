package com.kasakaid.omoidememory.downloader.domain

import com.kasakaid.omoidememory.domain.OmoideMemory
import java.nio.file.Path

/**
 * DIP にしたがってドライブのアクセスの実装は切り離す
 */
interface DriveService {
    /**
     * ドライブからファイルを持ってきます。
     * 実装は Google や OneDrive などいずれかのドライブのアクセスになります。
     */
    suspend fun listFiles(): List<OmoideMemory>

    /**
     * 指定されたメモリをローカルにダウンロードします。
     */
    suspend fun downloadFile(omoideMemory: OmoideMemory): Path
}