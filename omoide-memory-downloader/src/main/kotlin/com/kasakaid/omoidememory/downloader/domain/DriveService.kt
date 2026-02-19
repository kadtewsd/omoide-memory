package com.kasakaid.omoidememory.downloader.domain

import com.kasakaid.omoidememory.domain.OmoideMemory

/**
 * DIP にしたがってドライブのアクセスの実装は切り離す
 */
interface DriveService {
    /**
     * ドライブからファイルを持ってきます。
     * 実装は Google や OneDrive などいずれかのドライブのアクセスになります。
     * まずはメタデータを取得します。
     */
    suspend fun listFiles(): List<OmoideMemory>

    /**
     * 取得されたファイルのメタデータから実体を取得してメモリをローカル PC のストレージに書き込みます。
     */
    suspend fun writeOmoideMemoryToTargetPath(omoideMemory: OmoideMemory)
}